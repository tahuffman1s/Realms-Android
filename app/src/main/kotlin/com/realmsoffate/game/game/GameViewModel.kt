package com.realmsoffate.game.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.realmsoffate.game.RealmsApp
import com.realmsoffate.game.data.AiProvider
import com.realmsoffate.game.data.AiRepository
import com.realmsoffate.game.data.ChatMsg
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.Choice
import com.realmsoffate.game.data.PartyCompanion
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.ParsedReply
import com.realmsoffate.game.data.PlayerPos
import com.realmsoffate.game.data.TravelState
import com.realmsoffate.game.data.PreferencesStore
import com.realmsoffate.game.data.Prompts
import com.realmsoffate.game.data.Quest
import com.realmsoffate.game.data.GraveyardEntry
import com.realmsoffate.game.data.DebugTurn
import com.realmsoffate.game.data.SaveData
import com.realmsoffate.game.data.SaveSlotMeta
import com.realmsoffate.game.data.SaveStore
import com.realmsoffate.game.data.SerializedBuyback
import com.realmsoffate.game.data.NarrationSegmentData
import com.realmsoffate.game.data.TagParser
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.data.WorldEvent
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.realmsoffate.game.game.reducers.NpcLogReducer
import com.realmsoffate.game.game.reducers.WorldReducer

enum class Screen { ApiSetup, Title, CharacterCreation, Game, Death }

data class GameUiState(
    val character: Character? = null,
    val worldMap: WorldMap? = null,
    val currentLoc: Int = 0,
    val playerPos: PlayerPos? = null,
    val worldLore: WorldLore? = null,
    val worldEvents: List<WorldEvent> = emptyList(),
    val lastEventTurn: Int = 0,
    val npcLog: List<LogNpc> = emptyList(),
    val party: List<PartyCompanion> = emptyList(),
    val quests: List<Quest> = emptyList(),
    val hotbar: List<String?> = List(6) { null },
    val turns: Int = 0,
    val morality: Int = 0,
    val factionRep: Map<String, Int> = emptyMap(),
    val history: List<ChatMsg> = emptyList(),
    val messages: List<DisplayMessage> = emptyList(),
    val currentScene: String = "default",
    val currentSceneDesc: String = "",
    val currentChoices: List<Choice> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val merchantStocks: Map<String, Map<String, Int>> = emptyMap(),
    /** Merchants available at current location — shown as tappable buttons in the chat. */
    val availableMerchants: List<String> = emptyList(),
    val lastCheck: CheckDisplay? = null,
    val lastDice: Int = 0,
    /** Non-null when the current scene is a battle. Round counter + ally initiative. */
    val combat: CombatState? = null,
    /** Non-null when the player is at 0 HP and rolling death saves. */
    val deathSave: DeathSaveState? = null,
    /**
     * The dice preview shown after the player commits an action but BEFORE the
     * narrator sees it. Tapping Continue on the preview clears this and dispatches
     * the action to the AI with the pre-rolled value.
     */
    val preRoll: PreRollDisplay? = null,
    /**
     * Index into `messages` of the player bubble that started the current turn.
     * The chat list scrolls to this row so each turn reads top-down rather than
     * jumping to the bottom of the narration.
     */
    val turnStartIndex: Int = 0,
    /** Bookmarked text snippets from individual bubbles. */
    val bookmarks: List<String> = emptyList(),
    /** Non-null when the player is actively traveling between locations. */
    val travelState: TravelState? = null,
    /**
     * Memoized per-game stable system-prompt suffix. Null until first AI call;
     * cleared on level-up, mutation change, character replacement, or load. See
     * Prompts.buildSessionSystem.
     */
    val cachedSessionSystem: String? = null
)

data class CheckDisplay(
    val skill: String, val ability: String, val dc: Int,
    val passed: Boolean, val total: Int, val roll: Int,
    val mod: Int, val prof: Int, val crit: Boolean
)

/**
 * Pre-roll preview shown to the player BEFORE the action is sent to the
 * narrator. They see the d20 result, the modifier breakdown, and the running
 * total; tapping Continue commits the action and the AI sees the same number.
 *
 *   skill / ability are null for "freeform" actions that don't trigger a check.
 *   crit is true on nat 20 / nat 1 — used for the visual flourish.
 */
data class PreRollDisplay(
    val action: String,
    val skill: String?,
    val ability: String,
    val roll: Int,
    val mod: Int,
    val prof: Int,
    val total: Int,
    val crit: Boolean
)

@kotlinx.serialization.Serializable
sealed interface DisplayMessage {
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("player")
    data class Player(val text: String) : DisplayMessage

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("narration")
    data class Narration(
        val text: String, val scene: String, val sceneDesc: String,
        val hpBefore: Int = 0, val hpAfter: Int = 0, val maxHp: Int = 0,
        val goldBefore: Int = 0, val goldAfter: Int = 0,
        val xpGained: Int = 0,
        val conditionsAdded: List<String> = emptyList(),
        val conditionsRemoved: List<String> = emptyList(),
        val itemsGained: List<String> = emptyList(),
        val itemsRemoved: List<String> = emptyList(),
        val moralDelta: Int = 0,
        val repDeltas: List<Pair<String, Int>> = emptyList(),
        val segments: List<NarrationSegmentData> = emptyList()
    ) : DisplayMessage

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("event")
    data class Event(val icon: String, val title: String, val text: String) : DisplayMessage

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("system")
    data class System(val text: String) : DisplayMessage
}

class GameViewModel(
    private val ai: AiRepository,
    private val prefs: PreferencesStore
) : ViewModel() {

    private val _screen = MutableStateFlow(Screen.ApiSetup)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _ui = MutableStateFlow(GameUiState())
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    private val _provider = MutableStateFlow(AiProvider.DEEPSEEK)
    val provider: StateFlow<AiProvider> = _provider.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _pendingCharacter = MutableStateFlow<Character?>(null)

    /** Save slot thumbnails for the title-screen Load menu. */
    private val _saveSlots = MutableStateFlow<List<SaveSlotMeta>>(emptyList())
    val saveSlots: StateFlow<List<SaveSlotMeta>> = _saveSlots.asStateFlow()

    /** Graveyard tombstones for the title-screen Graveyard menu. */
    private val _graveyard = MutableStateFlow<List<GraveyardEntry>>(emptyList())
    val graveyard: StateFlow<List<GraveyardEntry>> = _graveyard.asStateFlow()

    /** Most recent death — feeds the BitLife-style DeathScreen. */
    private val _lastDeath = MutableStateFlow<GraveyardEntry?>(null)
    val lastDeath: StateFlow<GraveyardEntry?> = _lastDeath.asStateFlow()

    /** Running timeline — appended to on major events, stored on death. */
    private val timeline = mutableListOf<TimelineEntry>()

    /** Set to a level number when the player just levelled up — game screen shows overlay. */
    private val _pendingLevelUp = MutableStateFlow<Int?>(null)
    val pendingLevelUpFlow: StateFlow<Int?> = _pendingLevelUp.asStateFlow()
    private var pendingLevelUp: Int?
        get() = _pendingLevelUp.value
        set(v) { _pendingLevelUp.value = v }

    fun dismissLevelUp() { _pendingLevelUp.value = null }

    /** Stat points available to assign from level-ups. */
    private val _pendingStatPoints = MutableStateFlow(0)
    val pendingStatPoints: StateFlow<Int> = _pendingStatPoints.asStateFlow()

    /** Non-null when the player should choose a feat (at levels 4, 8, 12, 16, 20). */
    private val _pendingFeat = MutableStateFlow(false)
    val pendingFeat: StateFlow<Boolean> = _pendingFeat.asStateFlow()

    fun assignStatPoint(stat: String) {
        val pts = _pendingStatPoints.value
        if (pts <= 0) return
        val s = _ui.value
        val ch = s.character ?: return
        when (stat.uppercase()) {
            "STR" -> ch.abilities.str += 1
            "DEX" -> ch.abilities.dex += 1
            "CON" -> { ch.abilities.con += 1; ch.maxHp += 1; ch.hp += 1 }
            "INT" -> ch.abilities.int += 1
            "WIS" -> ch.abilities.wis += 1
            "CHA" -> ch.abilities.cha += 1
            else -> return
        }
        _pendingStatPoints.value = pts - 1
        _ui.value = s.copy(character = ch)
    }

    fun selectFeat(featName: String) {
        val s = _ui.value
        val ch = s.character ?: return
        val feat = Feats.find(featName) ?: return
        feat.apply(ch)
        ch.feats.add(featName)
        _pendingFeat.value = false
        _ui.value = s.copy(character = ch, messages = s.messages + DisplayMessage.System("Feat acquired: $featName"))
    }

    fun dismissFeat() { _pendingFeat.value = false }

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    fun setFontScale(scale: Float) {
        _fontScale.value = scale.coerceIn(0.7f, 1.6f)
        viewModelScope.launch { prefs.saveFontScale(_fontScale.value) }
    }

    // ---- Debug log — records every AI exchange for diagnostics ----
    // DebugTurn is defined in Models.kt (com.realmsoffate.game.data) and is
    // @Serializable so it can be persisted in SaveData.debugLog.
    private val _debugLog = mutableListOf<DebugTurn>()

    private fun logDebugTurn(
        turn: Int, action: String, skill: String?, roll: Int,
        prompt: String, raw: String, parsed: com.realmsoffate.game.data.ParsedReply
    ) {
        val tags = buildString {
            if (parsed.damage > 0) appendLine("DAMAGE:${parsed.damage}")
            if (parsed.heal > 0) appendLine("HEAL:${parsed.heal}")
            if (parsed.xp > 0) appendLine("XP:${parsed.xp}")
            if (parsed.goldGained > 0) appendLine("GOLD:${parsed.goldGained}")
            if (parsed.goldLost > 0) appendLine("GOLD_LOST:${parsed.goldLost}")
            parsed.checks.forEach { appendLine("CHECK:${it.skill}|${it.ability}|DC${it.dc}|${if (it.passed) "PASS" else "FAIL"}|${it.total}") }
            parsed.npcsMet.forEach { appendLine("NPC_MET:${it.name}|${it.race}|${it.role}|${it.relationship}") }
            parsed.questStarts.forEach { appendLine("QUEST_START:${it.title}") }
            parsed.questComplete.forEach { appendLine("QUEST_COMPLETE:$it") }
            parsed.questFails.forEach { appendLine("QUEST_FAIL:$it") }
            parsed.shops.forEach { (m, _) -> appendLine("SHOP:$m") }
            parsed.travelTo?.let { appendLine("TRAVEL:$it") }
            parsed.partyJoins.forEach { appendLine("PARTY_JOIN:${it.name}") }
            parsed.partyLeaves.forEach { appendLine("PARTY_LEAVE:$it") }
            parsed.enemies.forEach { (n, hp, max) -> appendLine("ENEMY:$n|$hp/$max") }
            parsed.factionUpdates.forEach { (n, f, v) -> appendLine("FACTION_UPDATE:$n|$f|$v") }
            parsed.npcDeaths.forEach { appendLine("NPC_DIED:$it") }
            parsed.npcUpdates.forEach { (n, f, v) -> appendLine("NPC_UPDATE:$n|$f|$v") }
            parsed.conditionsAdded.forEach { appendLine("CONDITION:$it") }
            parsed.conditionsRemoved.forEach { appendLine("REMOVE_CONDITION:$it") }
            parsed.itemsGained.forEach { appendLine("ITEM:${it.name}|${it.type}|${it.rarity}") }
            parsed.itemsRemoved.forEach { appendLine("REMOVE_ITEM:$it") }
            parsed.loreEntries.forEach { appendLine("LORE:$it") }
            if (parsed.moralDelta != 0) appendLine("MORAL:${parsed.moralDelta}")
            parsed.repDeltas.forEach { (f, d) -> appendLine("REP:$f|$d") }
            parsed.dialogues.forEach { (name, lines) -> lines.forEach { (t, q) -> appendLine("DIALOGUE:$name(T$t):$q") } }
            parsed.narratorProse.forEach { appendLine("NARRATOR_PROSE:${it.take(80)}...") }
            parsed.narratorAsides.forEach { appendLine("NARRATOR_ASIDE:$it") }
            parsed.playerActions.forEach { appendLine("PLAYER_ACTION:${it.take(80)}") }
            parsed.npcActions.forEach { (n, a) -> appendLine("NPC_ACTION:$n:${a.take(80)}") }
            parsed.npcDialogs.forEach { (n, d) -> appendLine("NPC_DIALOG:$n:${d.take(80)}") }
            parsed.npcQuotes.forEach { (n, q) -> appendLine("NPC_QUOTE:$n:${q.take(80)}") }
            parsed.playerDialogs.forEach { appendLine("PLAYER_DIALOG:${it.take(80)}") }
            if (parsed.segments.isNotEmpty()) appendLine("SEGMENTS:${parsed.segments.size} blocks")
            appendLine("SOURCE:${parsed.source}")
            appendLine("CACHE_HIT:${ai.lastCacheHit}/${ai.lastPromptTokens} tokens")
        }
        _debugLog.add(DebugTurn(
            turn = turn, playerAction = action, classifiedSkill = skill,
            diceRoll = roll, userPromptSent = prompt, rawAiResponse = raw,
            parsedScene = "${parsed.scene}|${parsed.sceneDesc}",
            parsedNarration = parsed.narration.take(500),
            parsedTags = tags
        ))
        // Cap at 50 turns to avoid memory bloat
        if (_debugLog.size > 50) _debugLog.removeAt(0)
    }

    /** Generates a comprehensive debug dump as a plain-text string. */
    fun exportDebugDump(): String {
        val s = _ui.value
        val ch = s.character
        return buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("  REALMS OF FATE — DEBUG DUMP")
            appendLine("  ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine("═══════════════════════════════════════")
            appendLine()

            // ---- Character ----
            appendLine("── CHARACTER ──")
            if (ch != null) {
                appendLine("Name: ${ch.name}  Race: ${ch.race}  Class: ${ch.cls}  Level: ${ch.level}")
                appendLine("HP: ${ch.hp}/${ch.maxHp}  AC: ${ch.ac}  Gold: ${ch.gold}  XP: ${ch.xp}")
                appendLine("STR:${ch.abilities.str} DEX:${ch.abilities.dex} CON:${ch.abilities.con} INT:${ch.abilities.int} WIS:${ch.abilities.wis} CHA:${ch.abilities.cha}")
                appendLine("Proficiency: +${ch.proficiency}")
                appendLine("Conditions: ${ch.conditions.joinToString(", ").ifBlank { "none" }}")
                appendLine("Feats: ${ch.feats.joinToString(", ").ifBlank { "none" }}")
                appendLine("Equipped: ${ch.inventory.filter { it.equipped }.joinToString(", ") { it.name }.ifBlank { "nothing" }}")
                appendLine("Inventory: ${ch.inventory.joinToString(", ") { "${it.name}(x${it.qty})" }.ifBlank { "empty" }}")
                ch.backstory?.let {
                    appendLine("Backstory: origin=${it.origin}, flaw=${it.flaw}, bond=${it.bond}")
                    appendLine("  darkSecret=${it.darkSecret}")
                    appendLine("  personalEnemy=${it.personalEnemy}")
                    appendLine("  lostItem=${it.lostItem}")
                    appendLine("  prophecy=${it.prophecy ?: "none"}")
                }
            } else appendLine("(no character)")

            appendLine()
            appendLine("── WORLD STATE ──")
            appendLine("Turn: ${s.turns}  Morality: ${s.morality}")
            appendLine("Location: ${s.worldMap?.locations?.getOrNull(s.currentLoc)?.let { "${it.name} (${it.type})" } ?: "unknown"}")
            appendLine("Scene: ${s.currentScene} | ${s.currentSceneDesc}")
            appendLine("Faction Rep: ${s.factionRep.entries.joinToString(", ") { "${it.key}:${it.value}" }.ifBlank { "none" }}")
            s.combat?.let { appendLine("Combat: round ${it.round}, ${it.order.size} combatants") }

            appendLine()
            appendLine("── FACTIONS ──")
            s.worldLore?.factions?.forEach { f ->
                appendLine("  ${f.name} (${f.type}) status=${f.status} ruler=${f.ruler} currency=${f.currency}")
            }

            appendLine()
            appendLine("── NPC LOG (${s.npcLog.size} NPCs) ──")
            s.npcLog.forEach { n ->
                appendLine("  ${n.name} | ${n.race} ${n.role} | ${n.relationship} | status=${n.status} | lastSeen=T${n.lastSeenTurn} at ${n.lastLocation}")
                if (n.dialogueHistory.isNotEmpty()) {
                    n.dialogueHistory.takeLast(3).forEach { appendLine("    > $it") }
                }
            }

            appendLine()
            appendLine("── PARTY (${s.party.size}) ──")
            s.party.forEach { appendLine("  ${it.name} ${it.race} ${it.role} L${it.level} HP${it.hp}/${it.maxHp}") }

            appendLine()
            appendLine("── QUESTS ──")
            s.quests.forEach { q ->
                appendLine("  [${q.status}] ${q.title} (${q.type}) by ${q.giver}")
                q.objectives.forEachIndexed { i, o -> appendLine("    ${if (q.completed.getOrElse(i) { false }) "✓" else "○"} $o") }
            }

            appendLine()
            appendLine("── WORLD EVENTS ──")
            s.worldEvents.forEach { appendLine("  T${it.turn}: ${it.icon} ${it.title} — ${it.text}") }

            appendLine()
            appendLine("── DISPLAY MESSAGES (${s.messages.size}) ──")
            s.messages.forEachIndexed { i, msg ->
                when (msg) {
                    is DisplayMessage.Player -> appendLine("  [$i] PLAYER: ${msg.text}")
                    is DisplayMessage.Narration -> appendLine("  [$i] NARRATION (${msg.scene}): ${msg.text.take(200)}...")
                    is DisplayMessage.Event -> appendLine("  [$i] EVENT: ${msg.icon} ${msg.title}")
                    is DisplayMessage.System -> appendLine("  [$i] SYSTEM: ${msg.text}")
                }
            }

            appendLine()
            appendLine("── CHAT HISTORY (${s.history.size} messages) ──")
            s.history.forEach { m ->
                appendLine("  [${m.role}] ${m.content.take(300)}${if (m.content.length > 300) "..." else ""}")
                appendLine()
            }

            appendLine()
            appendLine("══════════════════════════════════════════")
            appendLine("  AI EXCHANGE LOG (${_debugLog.size} turns)")
            appendLine("══════════════════════════════════════════")
            _debugLog.forEach { d ->
                appendLine()
                appendLine("──── TURN ${d.turn} ────")
                appendLine("Action: ${d.playerAction}")
                appendLine("Classified Skill: ${d.classifiedSkill ?: "none (freeform)"}")
                appendLine("Dice: d20=${d.diceRoll}")
                appendLine("Scene: ${d.parsedScene}")
                appendLine()
                appendLine("USER PROMPT SENT:")
                appendLine(d.userPromptSent)
                appendLine()
                appendLine("RAW AI RESPONSE:")
                appendLine(d.rawAiResponse)
                appendLine()
                appendLine("PARSED TAGS:")
                appendLine(d.parsedTags.ifBlank { "(none)" })
                appendLine()
                appendLine("PARSED NARRATION (first 500 chars):")
                appendLine(d.parsedNarration)
            }

            appendLine()
            appendLine("── SYSTEM PROMPT ──")
            appendLine(Prompts.DS_PREFIX.take(500) + "...")
            appendLine("(...)")
            appendLine(Prompts.SYS.take(500) + "...")
            appendLine()
            appendLine("── PER_TURN_REMINDER ──")
            appendLine(Prompts.PER_TURN_REMINDER)
        }
    }

    fun debugDumpFilename(): String {
        val ch = _ui.value.character
        val name = ch?.let { SaveStore.slotKeyFor(it.name) } ?: "debug"
        return "debug_${name}_T${_ui.value.turns}_${System.currentTimeMillis() / 1000}.txt"
    }

    /** Tutorial step — null means tutorial is complete or dismissed. */
    private val _tutorialStep = MutableStateFlow<Int?>(null)
    val tutorialStep: StateFlow<Int?> = _tutorialStep.asStateFlow()

    fun advanceTutorial() {
        val current = _tutorialStep.value ?: return
        val next = current + 1
        _tutorialStep.value = if (next > 8) null else next
    }

    fun dismissTutorial() {
        _tutorialStep.value = null
        viewModelScope.launch { prefs.setTutorialComplete() }
    }

    fun resetTutorial() {
        viewModelScope.launch { prefs.resetTutorial() }
    }

    /** Set to a Rest type (short/long) when player initiates via slash command / overlay. */
    private val _restOverlay = MutableStateFlow<String?>(null)
    val restOverlay: StateFlow<String?> = _restOverlay.asStateFlow()

    fun shortRest() {
        val s = _ui.value
        val ch = s.character ?: return
        val hitDie = Classes.find(ch.cls)?.hitDie ?: 8
        val heal = Dice.d(hitDie) + ch.abilities.conMod.coerceAtLeast(0)
        ch.hp = (ch.hp + heal).coerceAtMost(ch.maxHp)
        SpellSlots.applyShortRest(ch)
        _ui.value = s.copy(character = ch, messages = s.messages + DisplayMessage.System("Short rest — recovered $heal HP."))
        logTimeline("event", "Short rest — +$heal HP")
        _restOverlay.value = "short:$heal"
    }

    fun longRest() {
        val s = _ui.value
        val ch = s.character ?: return
        SpellSlots.applyLongRest(ch)
        // Long rest clears most conditions (per D&D 5e). Keep narrative-permanent
        // markers like "Cursed" in place — the narrator can remove them explicitly.
        val permanent = setOf("cursed", "doomed", "marked", "branded")
        ch.conditions.removeAll { it.lowercase() !in permanent }
        _ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("Long rest — fully restored.")
        )
        logTimeline("event", "Long rest — full heal + slots restored")
        _restOverlay.value = "long"
    }

    fun dismissRest() { _restOverlay.value = null }

    /** Start traveling to a destination. Calculates road distance and begins turn-by-turn travel. */
    fun startTravel(destId: Int) {
        val s = _ui.value
        val wm = s.worldMap ?: return
        val dest = wm.locations.getOrNull(destId) ?: return
        if (destId == s.currentLoc) return

        // Find direct road
        val road = wm.roads.firstOrNull {
            (it.from == s.currentLoc && it.to == destId) ||
            (it.to == s.currentLoc && it.from == destId)
        } ?: return

        val travel = TravelState(
            destId = destId,
            totalLeagues = road.dist,
            leaguesTraveled = 0,
            roadPath = listOf(s.currentLoc, destId),
            destName = dest.name
        )
        _ui.value = s.copy(travelState = travel)

        // Submit the first travel turn
        submitAction("I begin traveling to ${dest.name}. The road stretches ${road.dist} leagues ahead.")
    }

    fun cancelTravel() {
        val s = _ui.value
        if (s.travelState != null) {
            _ui.value = s.copy(travelState = null)
            postSystemMessage("Travel cancelled. You stop on the road.")
        }
    }

    /** Current target-prompt spec — null when no picker is showing. */
    private val _targetPrompt = MutableStateFlow<com.realmsoffate.game.ui.overlays.TargetPromptSpec?>(null)
    val targetPrompt: StateFlow<com.realmsoffate.game.ui.overlays.TargetPromptSpec?> = _targetPrompt.asStateFlow()

    fun requestTargetPrompt(spec: com.realmsoffate.game.ui.overlays.TargetPromptSpec) {
        _targetPrompt.value = spec
    }
    fun dismissTargetPrompt() { _targetPrompt.value = null }

    /** Flashes the dramatic INITIATIVE overlay on the first round of a battle. */
    private val _showInitiative = MutableStateFlow(false)
    val showInitiative: StateFlow<Boolean> = _showInitiative.asStateFlow()
    fun dismissInitiative() { _showInitiative.value = false }

    /** Active shop merchant name — set when a [SHOP] tag arrives, cleared on close. */
    private val _activeShop = MutableStateFlow<String?>(null)
    val activeShop: StateFlow<String?> = _activeShop.asStateFlow()
    fun dismissShop() { _activeShop.value = null }
    fun openShop(merchantName: String) { _activeShop.value = merchantName }

    fun buyItem(merchant: String, itemName: String, price: Int) {
        val s = _ui.value
        val ch = s.character ?: return
        if (ch.gold < price) return
        ch.gold -= price
        ch.inventory.add(Item(name = itemName, desc = "Bought from $merchant", type = "item", rarity = "common"))
        _ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("Bought $itemName for ${price}g.")
        )
        logTimeline("event", "Bought $itemName for ${price}g from $merchant")
    }

    /** Buyback inventory — per merchant, last 8 items sold. */
    private val _buybackStocks = MutableStateFlow<Map<String, List<com.realmsoffate.game.ui.overlays.BuybackEntry>>>(emptyMap())
    val buybackStocks: StateFlow<Map<String, List<com.realmsoffate.game.ui.overlays.BuybackEntry>>> = _buybackStocks.asStateFlow()

    fun sellItem(merchant: String, item: Item, price: Int) {
        val s = _ui.value
        val ch = s.character ?: return
        val idx = ch.inventory.indexOfFirst { it.name == item.name && it.rarity == item.rarity }
        if (idx < 0) return
        val existing = ch.inventory[idx]
        if (existing.qty > 1) {
            ch.inventory[idx] = existing.copy(qty = existing.qty - 1)
        } else {
            ch.inventory.removeAt(idx)
        }
        ch.gold += price
        _ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("Sold ${item.name} for ${price}g.")
        )
        // Remember for buyback.
        val current = _buybackStocks.value.toMutableMap()
        val list = current[merchant].orEmpty().toMutableList()
        list.add(0, com.realmsoffate.game.ui.overlays.BuybackEntry(item = item.copy(qty = 1), price = price * 2))
        while (list.size > 8) list.removeAt(list.size - 1)
        current[merchant] = list
        _buybackStocks.value = current
        logTimeline("event", "Sold ${item.name} for ${price}g to $merchant")
    }

    fun buybackItem(merchant: String, item: Item, price: Int) {
        val s = _ui.value
        val ch = s.character ?: return
        if (ch.gold < price) return
        ch.gold -= price
        ch.inventory.add(item)
        _ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("Bought back ${item.name} for ${price}g.")
        )
        val current = _buybackStocks.value.toMutableMap()
        val list = current[merchant].orEmpty().toMutableList()
        list.removeAll { it.item.name == item.name && it.price == price }
        current[merchant] = list
        _buybackStocks.value = current
    }

    /**
     * Rolls a single death save. 3 successes = stabilise at 1 HP; 3 failures = die.
     * Nat 20 jumps straight to stable + 1 HP; nat 1 counts as two failures.
     */
    fun rollDeathSave() {
        val s = _ui.value
        val saves = s.deathSave ?: return
        val d = Dice.d20()
        val (updated, label) = DeathSaves.roll(saves, d)
        _ui.value = s.copy(
            deathSave = updated,
            messages = s.messages + DisplayMessage.System(label)
        )
        if (updated.dead) {
            logTimeline("death", "Failed third death save.")
            die("Failed the third death save.")
            return
        }
        if (updated.stable) {
            val ch = s.character ?: return
            ch.hp = 1
            _ui.value = _ui.value.copy(
                character = ch,
                deathSave = null,
                messages = _ui.value.messages + DisplayMessage.System("Stable — 1 HP. Get away.")
            )
            logTimeline("event", "Stabilised after death saves")
        }
    }

    /**
     * Exchange gold ↔ a faction's local currency at a rate derived from its
     * economy. 1 gold → `rate` local coins; `rate = 0.6 + 0.2 * (wealth - 3)`
     * clamped to [0.3, 1.6]. Directions: "to" = gold → local, "from" = local → gold.
     */
    fun exchange(factionName: String, direction: String, goldAmount: Int) {
        val s = _ui.value
        val ch = s.character ?: return
        val faction = s.worldLore?.factions?.firstOrNull { it.name == factionName } ?: return
        val wealth = faction.economy?.wealth ?: 3
        val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
        val localCurrency = faction.currency
        when (direction) {
            "to" -> {
                if (ch.gold < goldAmount) return
                val localGained = (goldAmount * rate).toInt()
                ch.gold -= goldAmount
                ch.currencyBalances[localCurrency] =
                    (ch.currencyBalances[localCurrency] ?: 0) + localGained
                _ui.value = s.copy(
                    character = ch,
                    messages = s.messages + DisplayMessage.System("Exchanged ${goldAmount}g → $localGained $localCurrency.")
                )
            }
            "from" -> {
                val currentLocal = ch.currencyBalances[localCurrency] ?: 0
                val localNeeded = goldAmount // caller passes local-amount-to-spend in this path
                if (currentLocal < localNeeded) return
                val goldGained = (localNeeded / rate).toInt()
                ch.currencyBalances[localCurrency] = currentLocal - localNeeded
                ch.gold += goldGained
                _ui.value = s.copy(
                    character = ch,
                    messages = s.messages + DisplayMessage.System("Exchanged $localNeeded $localCurrency → ${goldGained}g.")
                )
            }
        }
    }

    /** CHA check haggle — returns price multiplier (1.0 = no discount, down to 0.8). */
    fun haggle(chaMod: Int): Float {
        val roll = Dice.d20() + chaMod
        return when {
            roll >= 18 -> 0.8f // full 20%
            roll >= 14 -> 0.9f // 10%
            roll >= 10 -> 0.95f // 5%
            else -> 1f
        }
    }

    init {
        viewModelScope.launch {
            val p = prefs.provider.first()
            val k = prefs.apiKey.first()
            _provider.value = p
            _apiKey.value = k
            if (k.isNotBlank() && p.validate(k)) {
                _screen.value = Screen.Title
                refreshSlots()
            }
        }
        viewModelScope.launch { prefs.fontScale.collect { _fontScale.value = it } }
    }

    fun refreshSlots() {
        viewModelScope.launch {
            _saveSlots.value = SaveStore.listSlots()
            _graveyard.value = SaveStore.listGraves()
        }
    }

    /** Returns to the title screen without wiping state (for pause/menu). */
    fun returnToTitle() {
        // Drop any in-flight overlays so they don't bleed into the next load.
        _pendingLevelUp.value = null
        _restOverlay.value = null
        _activeShop.value = null
        _targetPrompt.value = null
        _showInitiative.value = false
        _screen.value = Screen.Title
        refreshSlots()
    }

    fun goToCharacterCreation() {
        // Fresh run — clear timeline and in-memory state so the new hero starts clean.
        timeline.clear()
        _ui.value = GameUiState()
        _screen.value = Screen.CharacterCreation
    }

    fun continueLatest() {
        viewModelScope.launch {
            val slots = SaveStore.listSlots()
            val mostRecent = slots.firstOrNull() ?: return@launch
            loadSlot(mostRecent.slot)
        }
    }

    fun deleteSlot(slot: String) {
        viewModelScope.launch {
            SaveStore.delete(slot)
            refreshSlots()
        }
    }

    fun exhumeGrave(entry: GraveyardEntry) {
        viewModelScope.launch {
            SaveStore.exhume(entry)
            refreshSlots()
        }
    }

    /** Triggered when the character's HP hits 0 — buries them and shows DeathScreen. */
    private fun die(cause: String) {
        val s = _ui.value
        val ch = s.character ?: return
        val entry = GraveyardEntry(
            characterName = ch.name,
            race = ch.race,
            cls = ch.cls,
            level = ch.level,
            turns = s.turns,
            xp = ch.xp,
            gold = ch.gold,
            morality = s.morality,
            worldName = s.worldMap?.locations?.firstOrNull()?.name.orEmpty(),
            mutations = s.worldLore?.mutations.orEmpty(),
            companions = s.party.map { it.name },
            backstoryText = ch.backstory?.promptText,
            causeOfDeath = cause,
            timeline = timeline.toList() + TimelineEntry(s.turns, "death", cause),
            diedAt = java.time.Instant.now().toString()
        )
        _lastDeath.value = entry
        viewModelScope.launch {
            SaveStore.bury(entry)
            SaveStore.delete("autosave")
            SaveStore.delete(SaveStore.slotKeyFor(ch.name))
            refreshSlots()
        }
        _screen.value = Screen.Death
    }

    private fun logTimeline(category: String, text: String) {
        timeline += TimelineEntry(_ui.value.turns, category, text)
    }

    fun setProvider(p: AiProvider) {
        _provider.value = p
    }

    /** Back-to-setup from title (user wants to change provider / key). */
    fun backToApiSetup() {
        _screen.value = Screen.ApiSetup
    }

    fun setApiKey(k: String) {
        _apiKey.value = k
    }

    fun confirmApiKey() {
        viewModelScope.launch {
            prefs.save(_provider.value, _apiKey.value)
            _screen.value = Screen.Title
            refreshSlots()
        }
    }

    fun startNewGame(char: Character) {
        viewModelScope.launch {
            val wm = WorldGen.generate()
            val lore = LoreGen.generate(wm)
            val clsDef = Classes.find(char.cls)
            if (clsDef != null) applyClassStart(char, clsDef)
            char.backstory = BackstoryGen.generate(char.race, char.cls, lore, wm)
            char.racialPhysique = Races.find(char.race)?.physiqueTemplate.orEmpty()
            val startLoc = wm.locations[wm.startId]
            startLoc.discovered = true

            // Pick an opening scenario.
            val scenario = Scenarios.random()
            scenario.modify?.invoke(char)

            _ui.value = GameUiState(
                character = char,
                worldMap = wm,
                currentLoc = wm.startId,
                playerPos = PlayerPos(startLoc.x.toFloat(), startLoc.y.toFloat()),
                worldLore = lore,
                history = emptyList(),
                currentScene = scenario.sceneHint,
                messages = listOf(
                    DisplayMessage.System("${scenario.name} — ${char.name} the ${char.race} ${char.cls} arrives in ${startLoc.name}.")
                )
            )
            _screen.value = Screen.Game

            // Start tutorial on first game.
            viewModelScope.launch {
                if (!prefs.isTutorialComplete()) {
                    _tutorialStep.value = 0
                }
            }

            // Seed opening narration with the scenario prompt template.
            val nearby = WorldGen.connected(wm, wm.startId).joinToString(", ") { (d, dist) -> "${d.icon} ${d.name} (${dist}lg)" }
            val loreBlurb = lore.factions.firstOrNull()?.let { "Local faction: ${it.name} (${it.type})." } ?: ""
            val seedPrompt = scenario.promptTemplate(char, startLoc, nearby, loreBlurb)
            submitAction(seedPrompt, seed = true)
        }
    }

    /**
     * Atomic guard against rapid taps. Compose-side click events can fire faster
     * than the StateFlow update propagates, so we synchronously claim the slot
     * by writing `isGenerating = true` before any further work.
     */
    @Synchronized
    private fun tryClaimSubmit(): GameUiState? {
        val state = _ui.value
        if (state.character == null) return null
        if (state.isGenerating || state.preRoll != null) return null
        // Reserve the slot immediately. Subsequent taps will see isGenerating=true
        // and bail before we even compute the roll.
        _ui.value = state.copy(isGenerating = true)
        return state
    }

    /**
     * Local keyword-based skill classifier — used as fallback when the API
     * classifier fails. Always returns a skill name (never null): dialogue → Persuasion,
     * unrecognised actions → Perception.
     */
    private fun localClassifyAction(action: String): String? {
        val a = action.lowercase()
        return when {
            // Combat
            a.contains("attack") || a.contains("strike") || a.contains("stab") ||
            a.contains("slash") || a.contains("shoot") || a.contains("kill") ||
            a.contains("punch") || a.contains("kick") || a.contains("fight") -> "Attack"
            // Stealth
            a.contains("sneak") || a.contains("hide") || a.contains("creep") ||
            a.contains("shadow") || a.contains("silent") || a.contains("stealthi") -> "Stealth"
            // Persuasion / social
            a.contains("persuade") || a.contains("convince") || a.contains("plead") ||
            a.contains("negotiate") || a.contains("charm") || a.contains("flatter") -> "Persuasion"
            // Deception
            a.contains("lie") || a.contains("deceive") || a.contains("bluff") ||
            a.contains("trick") || a.contains("pretend") || a.contains("disguise") -> "Deception"
            // Intimidation
            a.contains("intimidat") || a.contains("threaten") || a.contains("scare") ||
            a.contains("menac") || a.contains("growl") -> "Intimidation"
            // Perception
            a.contains("look") || a.contains("search") || a.contains("examine") ||
            a.contains("inspect") || a.contains("scan") || a.contains("watch") ||
            a.contains("listen") || a.contains("observe") -> "Perception"
            // Investigation
            a.contains("investigat") || a.contains("analyze") || a.contains("study") ||
            a.contains("deduc") || a.contains("clue") -> "Investigation"
            // Athletics
            a.contains("climb") || a.contains("jump") || a.contains("lift") ||
            a.contains("push") || a.contains("pull") || a.contains("swim") ||
            a.contains("grapple") || a.contains("shove") || a.contains("break down") -> "Athletics"
            // Acrobatics
            a.contains("dodge") || a.contains("flip") || a.contains("tumble") ||
            a.contains("balance") || a.contains("acrobat") || a.contains("leap") -> "Acrobatics"
            // Sleight of Hand
            a.contains("pick the lock") || a.contains("pickpocket") || a.contains("steal") ||
            a.contains("lockpick") || a.contains("sleight") || a.contains("pilfer") ||
            a.contains("pick lock") || a.contains("unlock") -> "Sleight Of Hand"
            // Arcana
            a.contains("cast") || a.contains("spell") || a.contains("magic") ||
            a.contains("arcane") || a.contains("enchant") || a.contains("ritual") -> "Arcana"
            // Medicine
            a.contains("heal") || a.contains("bandage") || a.contains("treat wound") ||
            a.contains("first aid") || a.contains("medicin") -> "Medicine"
            // Survival
            a.contains("track") || a.contains("forage") || a.contains("hunt") ||
            a.contains("navigate") || a.contains("camp") || a.contains("survive") -> "Survival"
            // Religion
            a.contains("pray") || a.contains("bless") || a.contains("divine") ||
            a.contains("holy") || a.contains("temple") || a.contains("worship") -> "Religion"
            // Nature
            a.contains("nature") || a.contains("animal") || a.contains("plant") ||
            a.contains("herb") || a.contains("beast") -> "Nature"
            // Insight
            a.contains("read") || a.contains("sense motive") || a.contains("insight") ||
            a.contains("tell if") || a.contains("lying") || a.contains("trust") -> "Insight"
            // Performance
            a.contains("sing") || a.contains("play music") || a.contains("perform") ||
            a.contains("dance") || a.contains("entertain") -> "Performance"
            // Pure dialogue — use Persuasion as the social check
            a.contains("say ") || a.contains("tell ") || a.contains("ask ") ||
            a.contains("i say") || a.contains("i tell") || a.contains("i ask") ||
            a.startsWith("\"") || a.startsWith("\u201c") -> "Persuasion"
            // Default: if it sounds like an action, use Perception as catch-all
            a.contains("try") || a.contains("attempt") -> "Perception"
            else -> "Perception"
        }
    }

    fun submitAction(action: String, skill: String? = null, seed: Boolean = false) {
        val state = tryClaimSubmit() ?: return
        val char = state.character ?: return  // re-checked for the type checker

        // Seed turn (opening narration) — skip the pre-roll preview entirely.
        if (seed) {
            _ui.value = _ui.value.copy(isGenerating = false)
            dispatchToAi(action, skill, seed = true, preRolled = Dice.d20())
            return
        }

        // No skill specified — fire a lightweight classifier to determine whether
        // this freeform action warrants a skill check. The user sees "thinking"
        // dots during the ~200ms call since isGenerating stays true.
        if (skill == null) {
            // Fire a lightweight classifier to determine if this action warrants a check.
            // Falls back to local keyword matching if the API call fails.
            viewModelScope.launch {
                var classified = try {
                    ai.classifyAction(_apiKey.value, action)
                } catch (_: Exception) { null }

                // Local fallback if the API classifier failed or returned null
                if (classified == null) {
                    classified = localClassifyAction(action)
                }

                // Every action gets a pre-roll — use the classified skill (Attack uses STR,
                // dialogue defaults to Persuasion, catch-all defaults to Perception).
                val effectiveSkill = classified ?: "Perception"
                val roll = Dice.d20()
                val ability = if (effectiveSkill == "Attack") "STR" else skillToAbility(effectiveSkill)
                val mod = char.abilities.modByName(ability)
                val prof = if (classProficient(char.cls, effectiveSkill)) char.proficiency else 0
                val total = roll + mod + prof
                _ui.value = _ui.value.copy(
                    isGenerating = false,
                    preRoll = PreRollDisplay(
                        action = action,
                        skill = effectiveSkill,
                        ability = ability,
                        roll = roll,
                        mod = mod,
                        prof = prof,
                        total = total,
                        crit = roll == 20 || roll == 1
                    )
                )
            }
            return
        }

        // Skill-tagged action: roll d20 + show preview. The actual AI call
        // is deferred until the player taps Send It on the dice breakdown.
        val roll = Dice.d20()
        val ability = skillToAbility(skill)
        val mod = char.abilities.modByName(ability)
        val prof = if (classProficient(char.cls, skill)) char.proficiency else 0
        val total = roll + mod + prof
        _ui.value = _ui.value.copy(
            isGenerating = false,  // release the claim; preRoll is the new gate
            preRoll = PreRollDisplay(
                action = action,
                skill = skill,
                ability = ability,
                roll = roll,
                mod = mod,
                prof = prof,
                total = total,
                crit = roll == 20 || roll == 1
            )
        )
    }

    /**
     * Called when the player taps Continue on the pre-roll preview. Posts the
     * optimistic player bubble (so the chat shows what they did immediately),
     * captures the turn-start scroll anchor, and dispatches to the AI with the
     * pre-rolled value.
     */
    fun confirmPreRoll() {
        val state = _ui.value
        val pre = state.preRoll ?: return
        // Drop the preview AND post the player bubble synchronously so the feed
        // updates the moment the player commits.
        val newMsgs = state.messages + DisplayMessage.Player(pre.action)
        _ui.value = state.copy(
            preRoll = null,
            messages = newMsgs,
            turnStartIndex = newMsgs.lastIndex
        )
        dispatchToAi(pre.action, pre.skill, seed = false, preRolled = pre.roll)
    }

    fun cancelPreRoll() {
        // Rolls are final — no take-backs
    }

    /**
     * Sends the action to the AI provider with the pre-rolled d20 value. Splits
     * the original submitAction's network + parse work out of the entry point so
     * the pre-roll dialog can defer the actual call.
     */
    private fun dispatchToAi(
        action: String,
        skill: String?,
        seed: Boolean,
        preRolled: Int
    ) {
        val state = _ui.value
        val char = state.character ?: return
        viewModelScope.launch {
            _ui.value = state.copy(isGenerating = true, error = null)

            val roll = preRolled
            val ability = skill?.let { skillToAbility(it) } ?: "STR"
            val mod = char.abilities.modByName(ability)
            val prof = if (skill != null && classProficient(char.cls, skill)) char.proficiency else 0
            val total = roll + mod + prof

            val sessionSystem = sessionSystemFor(state, char)
            val sys = Prompts.SYS + "\n\n" + sessionSystem
            val userPrompt = buildUserPrompt(state, char, action, skill, ability, roll, mod, prof, total, suppressDice = seed)
            val userMsg = ChatMsg(role = "user", content = userPrompt)
            val nh = state.history + userMsg

            val raw = try {
                ai.generate(_provider.value, _apiKey.value, sys, nh)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(isGenerating = false, error = "Network error: ${t.message}")
                return@launch
            }

            val parsed = TagParser.parse(raw, state.turns + 1)
            logDebugTurn(state.turns + 1, action, skill, roll, userPrompt, raw, parsed)
            // On seed turns we don't surface the dice reveal — the character hasn't acted yet.
            val updated = applyParsed(state, char, parsed, action, roll, mod, prof, suppressCheck = seed)
            // Decide whether the session system we just computed is still valid for the
            // next turn. If applyParsed advanced the character's level or replaced
            // worldLore, the string is stale — null out to force rebuild. Otherwise
            // persist it so next turn hits the DeepSeek prefix cache.
            val levelChanged = updated.character?.level != state.character?.level
            val loreChanged = updated.worldLore !== state.worldLore
            val sessionSystemToPersist = if (levelChanged || loreChanged) null else sessionSystem
            val withHistory = updated.copy(
                history = nh + ChatMsg(role = "assistant", content = raw),
                isGenerating = false,
                cachedSessionSystem = sessionSystemToPersist
            )
            _ui.value = withHistory

            // HP 0 — enter death saves instead of dying immediately (D&D 5e). 3 successes
            // stabilise; 3 failures and the run ends. If the saves overlay is already open
            // we leave it be; every player action counts as a "turn" in that state.
            val liveHp = withHistory.character?.hp ?: -1
            if (liveHp <= 0 && !seed) {
                if (_ui.value.deathSave == null) {
                    _ui.value = _ui.value.copy(
                        deathSave = DeathSaveState(),
                        messages = _ui.value.messages + DisplayMessage.System("You fall — rolling death saves.")
                    )
                    logTimeline("event", "Went down — death saves beginning")
                }
                // Persist the death-save state so a force-quit during this fragile
                // window restores the player at the brink rather than walking around at 0 HP.
                saveToSlot("autosave")
                withHistory.character?.let { c -> saveToSlot(SaveStore.slotKeyFor(c.name)) }
                return@launch
            } else if (liveHp > 0 && _ui.value.deathSave != null) {
                // Healed out of it.
                _ui.value = _ui.value.copy(
                    deathSave = null,
                    messages = _ui.value.messages + DisplayMessage.System("Stable — back from the brink.")
                )
            }

            // Combat state: on scene=battle, lazily start a round tracker; otherwise clear.
            // Case-insensitive in case the AI emits "Battle" / "BATTLE" — TagParser
            // already trims, but the source-of-truth example uses lowercase so we
            // defend rather than depend on it.
            val nowScene = withHistory.currentScene
            _ui.value = _ui.value.let { cur ->
                val ch2 = cur.character ?: return@let cur
                if (nowScene.equals("battle", ignoreCase = true)) {
                    val existing = cur.combat
                    var combat = if (existing == null) {
                        _showInitiative.value = true
                        CombatSystem.startCombat(ch2, cur.party)
                    } else {
                        CombatSystem.syncHp(existing.next(), ch2, cur.party)
                    }
                    // Merge enemy combatants from [ENEMY] tags into the initiative order
                    var updatedNpcLog = cur.npcLog
                    if (parsed.enemies.isNotEmpty()) {
                        val currentOrder = combat.order.toMutableList()
                        parsed.enemies.forEach { (name, hp, maxHp) ->
                            val idx = currentOrder.indexOfFirst { it.name.equals(name, true) && !it.isPlayer }
                            if (idx >= 0) {
                                // Update existing enemy HP
                                currentOrder[idx] = currentOrder[idx].copy(hp = hp, maxHp = maxHp)
                            } else {
                                // New enemy — add to the order
                                currentOrder.add(Combatant(name = name, hp = hp, maxHp = maxHp, initiative = Dice.d(20), isPlayer = false))
                            }
                        }
                        // Find enemies at 0 HP before removing them — mark them dead in npcLog
                        val killedByHp = currentOrder.filter { !it.isPlayer && it.hp <= 0 }.map { it.name }
                        if (killedByHp.isNotEmpty()) {
                            val mutableLog = updatedNpcLog.toMutableList()
                            killedByHp.forEach { deadName ->
                                val npcIdx = mutableLog.indexOfFirst { it.name.equals(deadName, true) }
                                if (npcIdx >= 0) {
                                    mutableLog[npcIdx] = mutableLog[npcIdx].copy(
                                        status = "dead",
                                        relationship = "dead",
                                        relationshipNote = "Killed turn ${cur.turns}"
                                    )
                                }
                            }
                            updatedNpcLog = mutableLog
                        }
                        // Remove enemies at 0 HP
                        currentOrder.removeAll { !it.isPlayer && it.hp <= 0 }
                        combat = combat.copy(order = currentOrder)
                    }
                    cur.copy(combat = combat, npcLog = updatedNpcLog)
                } else if (cur.combat != null) {
                    cur.copy(
                        combat = null,
                        messages = cur.messages + DisplayMessage.System("⚔️ Combat has ended.")
                    )
                } else cur
            }

            // Possibly generate a world event
            maybeRollWorldEvent()
            // Autosave every turn so crashes / background kills don't lose progress.
            saveToSlot("autosave")
            // Also write to a character-name-keyed slot for the Load menu.
            withHistory.character?.let { ch ->
                saveToSlot(SaveStore.slotKeyFor(ch.name))
            }
        }
    }

    fun pickChoice(c: Choice) = submitAction(c.text, skill = c.skill)

    fun toggleBookmark(text: String) {
        val s = _ui.value
        val current = s.bookmarks.toMutableList()
        val trimmed = text.take(300)
        if (current.any { it == trimmed }) current.remove(trimmed)
        else current.add(trimmed)
        _ui.value = s.copy(bookmarks = current)
    }

    fun removeBookmark(text: String) {
        val s = _ui.value
        _ui.value = s.copy(bookmarks = s.bookmarks.filter { it != text })
    }

    fun isBookmarked(text: String): Boolean = _ui.value.bookmarks.contains(text.take(300))

    /** Slash-command helper — inserts a system line into the message feed. */
    fun postSystemMessage(text: String) {
        val s = _ui.value
        _ui.value = s.copy(messages = s.messages + DisplayMessage.System(text))
    }

    /**
     * Returns the memoized per-game stable system suffix, building and caching it
     * on first call. The cache is invalidated (set to null) wherever level, mutations,
     * worldLore, or the character itself change. See GameUiState.cachedSessionSystem.
     */
    private fun sessionSystemFor(state: GameUiState, char: Character): String {
        // Pure: returns the cached string if present, otherwise builds a fresh
        // one. The caller is responsible for persisting the result into state
        // after applyParsed runs — writing to _ui.value here would get clobbered
        // when applyParsed's returned copy is committed.
        return state.cachedSessionSystem
            ?: com.realmsoffate.game.data.buildSessionSystem(char, state.worldLore)
    }

    /**
     * Per-turn volatile state briefing. Character sheet / backstory / mutations /
     * world palette have all moved to buildSessionSystem (now in the system message)
     * so DeepSeek's prefix cache can hit them across turns.
     */
    private fun buildUserPrompt(
        s: GameUiState, ch: Character, action: String, skill: String?, ability: String,
        roll: Int, mod: Int, prof: Int, total: Int, suppressDice: Boolean = false
    ): String {
        // Defensive: if worldMap is somehow null (shouldn't happen post-startNewGame
        // but a corrupt save or interrupted init could leave it that way), bail out
        // with a minimal action context rather than NPE'ing the whole turn.
        val wm = s.worldMap ?: return "ACTION: $action"
        val loc = wm.locations.getOrNull(s.currentLoc)
        val localFaction = LoreGen.findLocalFaction(wm, s.worldLore, s.currentLoc)
        val nearby = WorldGen.connected(wm, s.currentLoc)

        val eventCtx = if (s.worldEvents.isNotEmpty())
            "\nRECENT WORLD EVENTS (reference these):\n" +
                s.worldEvents.takeLast(3).joinToString("\n") { "${it.icon} ${it.prompt}" }
        else ""

        val partyCtx = if (s.party.isNotEmpty())
            "\nPARTY: " + s.party.joinToString(", ") { "${it.name} (${it.race} ${it.role} L${it.level} HP${it.hp}/${it.maxHp})" }
        else ""

        val questCtx = if (s.quests.any { it.status == "active" })
            "\nACTIVE QUESTS: " + s.quests.filter { it.status == "active" }.joinToString("; ") { "${it.title} — next: ${it.objectives.firstOrNull().orEmpty()}" }
        else ""

        val npcCtx = s.worldLore?.npcs.orEmpty()
            .filter { n -> n.location == (loc?.name ?: "") }
            .joinToString("\n") { "${it.name} (${it.race} ${it.role})" }
            .let { if (it.isNotBlank()) "\nNPCs HERE:\n$it" else "" }

        val inv = ch.inventory.filter { it.equipped }.joinToString(", ") { it.name }.ifBlank { "nothing" }
        val invAll = ch.inventory.joinToString(", ") { "${it.name} (x${it.qty})" }

        // Build a compact known-NPC roster for the LLM — stable IDs let it emit
        // correct ID-first tags without guessing. Include recently dead NPCs (within
        // 3 turns) so the narrator doesn't forget them; cap at 20 by recency.
        val currentTurnNum = s.turns + 1
        val rosterEntries = s.npcLog
            .filter { npc ->
                npc.status != "dead" || (currentTurnNum - npc.lastSeenTurn) <= 3
            }
            .sortedByDescending { it.lastSeenTurn }
            .take(20)
        val knownNpcsCtx = if (rosterEntries.isNotEmpty()) {
            buildString {
                append("\nKNOWN NPCS (reference these by id in your tags):")
                rosterEntries.forEach { npc ->
                    val racePart = npc.race.ifBlank { null }
                    val rolePart = npc.role.ifBlank { null }
                    val desc = listOfNotNull(racePart, rolePart).joinToString(" ")
                    val statusLabel = npc.status
                    val idPart = npc.id.ifBlank { "?" }
                    append("\n  $idPart — ${npc.name}")
                    if (desc.isNotBlank()) append(" ($desc, $statusLabel)")
                    else append(" ($statusLabel)")
                }
            }
        } else ""

        val cs = buildString {
            // Per-turn volatile state — HP/AC/Gold change every turn so stay here
            append("HP:${ch.hp}/${ch.maxHp}  AC:${ch.ac}  Gold:${ch.gold}")
            append("\nMORALITY: ${s.morality}   ")
            append("FACTION REP: ${s.factionRep.entries.joinToString { "${it.key}:${it.value}" }.ifBlank { "none" }}")
            loc?.let { append("\nLOCATION: ${it.name} (${it.type})") }
            localFaction?.let { append("\nLOCAL FACTION: ${it.name} (${it.type}); currency: ${it.currency}") }
            append("\nNEARBY: ${nearby.joinToString(", ") { "${it.first.name} (${it.second}lg)" }}")
            append("\nTURN: ${s.turns + 1}")
            s.travelState?.let { t ->
                append("\nTRAVELING: You are on the road to ${t.destName}. ${t.leaguesTraveled}/${t.totalLeagues} leagues traveled. ${t.totalLeagues - t.leaguesTraveled} leagues remaining.")
                append("\nNarrate the journey — describe the road, encounters, weather, terrain. The player is actively traveling, not at a settled location.")
            }
            append(partyCtx); append(questCtx); append(npcCtx); append(knownNpcsCtx); append(eventCtx)
            append("\nEquipped: $inv")
            append("\nInventory: ${invAll.ifBlank { "empty" }}")
            // Feed recent narration context so the AI doesn't lose the thread
            val recentNarration = s.messages
                .filterIsInstance<DisplayMessage.Narration>()
                .takeLast(2)
                .joinToString("\n---\n") { it.text.take(300) }
            if (recentNarration.isNotBlank()) {
                append("\n\nRECENT STORY (continue from here, do not reset or contradict):\n$recentNarration")
            }
        }

        val diceLine = if (suppressDice) "" else "\nDICE: d20=$roll" +
            (if (roll == 20) " CRITICAL SUCCESS!" else if (roll == 1) " CRITICAL FAILURE!" else "") +
            (skill?.let { "  SKILL:$it($ability) modifier:+$mod proficiency:+$prof total:$total" }.orEmpty())

        return "$cs\n\nACTION: $action$diceLine"
    }

    internal fun applyParsed(
        state: GameUiState, ch: Character, parsed: ParsedReply,
        playerAction: String, roll: Int, mod: Int, prof: Int,
        suppressCheck: Boolean = false
    ): GameUiState {
        val result = com.realmsoffate.game.game.reducers.CharacterReducer.apply(ch, parsed, currentTurn = state.turns + 1)
        val char = result.character
        val hpBefore = result.hpBefore
        val goldBefore = result.goldBefore

        // Apply level-up side effects
        result.levelUp?.let { signal ->
            pendingLevelUp = signal.newLevel
            if (signal.featPending) _pendingFeat.value = true
            else _pendingStatPoints.value += signal.statPointsGained
        }
        // Drain timeline entries from the reducer into the VM's timeline
        result.timelineEntries.forEach { entry -> timeline += entry }

        if (parsed.partyJoins.isNotEmpty()) Unit // handled at state level below

        // Display message list — the player bubble is already in state.messages
        // (posted optimistically by confirmPreRoll), so we just append narration.
        // Seed turns are the exception: they have no preRoll path so the player
        // bubble must be added here.
        val newMsgs = state.messages.toMutableList().apply {
            val alreadyHasPlayer = lastOrNull() is DisplayMessage.Player &&
                (lastOrNull() as? DisplayMessage.Player)?.text == playerAction
            if (!alreadyHasPlayer) add(DisplayMessage.Player(playerAction))
            add(DisplayMessage.Narration(
                parsed.narration, parsed.scene, parsed.sceneDesc,
                hpBefore = hpBefore, hpAfter = char.hp, maxHp = char.maxHp,
                goldBefore = goldBefore, goldAfter = char.gold,
                xpGained = parsed.xp,
                conditionsAdded = parsed.conditionsAdded,
                conditionsRemoved = parsed.conditionsRemoved,
                itemsGained = parsed.itemsGained.map { it.name },
                itemsRemoved = parsed.itemsRemoved,
                moralDelta = parsed.moralDelta,
                repDeltas = parsed.repDeltas,
                segments = parsed.segments
            ))
        }

        // Morality + faction rep
        val newMorality = (state.morality + parsed.moralDelta).coerceIn(-100, 100)
        val newRep = state.factionRep.toMutableMap().also {
            parsed.repDeltas.forEach { (f, d) -> it[f] = (it.getOrDefault(f, 0) + d).coerceIn(-100, 100) }
        }

        // Travel — turn-by-turn progression.
        var currentLoc = state.currentLoc
        var worldMap = state.worldMap
        var playerPos = state.playerPos
        var travelState = state.travelState

        if (travelState != null && worldMap != null) {
            // Progress travel by ~2-4 leagues per turn (road travel pace)
            val leaguesThisTurn = 2 + (roll % 3) // 2-4 leagues based on dice
            val newTraveled = (travelState.leaguesTraveled + leaguesThisTurn)
                .coerceAtMost(travelState.totalLeagues)

            if (newTraveled >= travelState.totalLeagues) {
                // Arrived at destination
                val dest = worldMap.locations.getOrNull(travelState.destId)
                if (dest != null) {
                    if (!dest.discovered) {
                        val newLocs = worldMap.locations.toMutableList().also { it[travelState.destId] = dest.copy(discovered = true) }
                        worldMap = worldMap.copy(locations = newLocs)
                    }
                    currentLoc = travelState.destId
                    playerPos = PlayerPos(dest.x.toFloat(), dest.y.toFloat())
                    newMsgs.add(DisplayMessage.System("You arrive at ${dest.icon} ${dest.name}"))
                    logTimeline("travel", "Arrived at ${dest.name}")
                }
                travelState = null // Travel complete
            } else {
                // Still traveling — interpolate position along the road
                val progress = newTraveled.toFloat() / travelState.totalLeagues.toFloat()
                val fromLoc = worldMap.locations.getOrNull(state.currentLoc)
                val toLoc = worldMap.locations.getOrNull(travelState.destId)
                if (fromLoc != null && toLoc != null) {
                    val interpX = fromLoc.x + (toLoc.x - fromLoc.x) * progress
                    val interpY = fromLoc.y + (toLoc.y - fromLoc.y) * progress
                    playerPos = PlayerPos(interpX, interpY)
                }
                travelState = travelState.copy(leaguesTraveled = newTraveled)
                val remaining = travelState.totalLeagues - newTraveled
                newMsgs.add(DisplayMessage.System("Traveled $leaguesThisTurn leagues. $remaining leagues remain to ${travelState.destName}."))
            }
        } else if (parsed.travelTo != null && worldMap != null) {
            // Legacy instant travel from AI [TRAVEL:] tag (when not using map travel)
            val idx = worldMap.locations.indexOfFirst { it.name.equals(parsed.travelTo, true) }
            if (idx >= 0) {
                val dest = worldMap.locations[idx]
                if (!dest.discovered) {
                    val newLocs = worldMap.locations.toMutableList().also { it[idx] = dest.copy(discovered = true) }
                    worldMap = worldMap.copy(locations = newLocs)
                }
                currentLoc = idx
                playerPos = PlayerPos(dest.x.toFloat(), dest.y.toFloat())
                newMsgs.add(DisplayMessage.System("You travel to ${dest.icon} ${dest.name}"))
                logTimeline("travel", "Arrived at ${dest.name}")
            }
        }

        val npcLogResult = NpcLogReducer.apply(
            npcLog = state.npcLog,
            combat = state.combat,
            parsed = parsed,
            currentTurn = state.turns + 1,
            currentLocName = worldMap?.locations?.getOrNull(currentLoc)?.name.orEmpty()
        )
        val npcLog = npcLogResult.npcLog
        var combatState = npcLogResult.combat
        newMsgs.addAll(npcLogResult.systemMessages)
        npcLogResult.timelineEntries.forEach { entry -> timeline += entry }

        // World lore — faction updates, dead-leader cascade, lore entries.
        val worldResult = WorldReducer.apply(
            worldLore = state.worldLore,
            npcLog = npcLog,
            parsed = parsed,
            currentTurn = state.turns + 1
        )
        var worldLoreUpdated = worldResult.worldLore
        newMsgs.addAll(worldResult.systemMessages)
        worldResult.timelineEntries.forEach { entry -> timeline += entry }

        // Quests
        val quests = state.quests.toMutableList()
        parsed.questStarts.forEach { q ->
            val withLoc = q.copy(location = worldMap?.locations?.getOrNull(currentLoc)?.name.orEmpty())
            quests.add(withLoc)
            logTimeline("quest", "Quest started: ${q.title}")
        }
        parsed.questUpdates.forEach { (title, obj) ->
            val idx = quests.indexOfFirst { it.title.equals(title, true) && it.status == "active" }
            if (idx >= 0) {
                val q = quests[idx]
                val oi = q.objectives.indexOfFirst { it.equals(obj, true) }
                if (oi >= 0) q.completed[oi] = true else {
                    q.objectives.add(obj)
                    q.completed.add(true)
                }
            }
        }
        parsed.questComplete.forEach { t ->
            val idx = quests.indexOfFirst { it.title.equals(t, true) }
            if (idx >= 0) quests[idx] = quests[idx].copy(status = "completed", turnCompleted = state.turns + 1)
        }
        parsed.questFails.forEach { t ->
            val idx = quests.indexOfFirst { it.title.equals(t, true) }
            if (idx >= 0) quests[idx] = quests[idx].copy(status = "failed", turnCompleted = state.turns + 1)
        }

        // Party joins + leaves
        val party = state.party.toMutableList().apply {
            parsed.partyJoins.forEach {
                add(it)
                logTimeline("birth", "${it.name} the ${it.race} ${it.role} joined the party.")
            }
            parsed.partyLeaves.forEach { name ->
                val idx = indexOfFirst { it.name.equals(name, ignoreCase = true) }
                if (idx >= 0) {
                    val gone = removeAt(idx)
                    logTimeline("event", "${gone.name} left the party.")
                }
            }
        }

        // Merchant stocks — save stock and expose button; do NOT auto-open the overlay.
        val merchants = state.merchantStocks.toMutableMap()
        val newMerchants = state.availableMerchants.toMutableList()
        parsed.shops.forEach { (name, items) ->
            merchants[name] = items
            if (!newMerchants.contains(name)) newMerchants.add(name)
            newMsgs.add(DisplayMessage.System("\uD83D\uDCB0 $name is open for business — tap to browse"))
        }

        // The pre-roll dialog already showed the breakdown to the player BEFORE
        // dispatching to the AI. Showing the DiceRollerDialog again on the response
        // would be a redundant second reveal — so we no longer populate `lastCheck`.
        // Instead we post a single inline result line in the chat feed with the
        // AUTHORITATIVE total (our d20 + mod + prof) and pass/fail vs the DC the
        // narrator picked. This catches the case where the AI hallucinates a wrong
        // total: we override and display the right one.
        val check: CheckDisplay? = null
        parsed.checks.firstOrNull()?.takeUnless { suppressCheck }?.let { c ->
            val authTotal = roll + mod + prof
            val passed = if (roll == 20) true
                         else if (roll == 1) false
                         else authTotal >= c.dc
            val verdict = if (passed) "PASSED" else "FAILED"
            val critLabel = when (roll) {
                20 -> " · NAT 20!"
                1 -> " · NAT 1!"
                else -> ""
            }
            val resultLine = "${if (passed) "✓" else "✗"} ${c.skill} (${c.ability}) DC ${c.dc} — $verdict ($authTotal)$critLabel"
            newMsgs.add(DisplayMessage.System(resultLine))
        }

        // Advance time contextually — [TIME:phase] tag forces, otherwise accumulator.
        // cachedSessionSystem is NOT set here — dispatchToAi decides after applyParsed
        // whether to persist the freshly-built string or force a rebuild next turn.
        return state.copy(
            character = char,
            worldMap = worldMap,
            worldLore = worldLoreUpdated ?: state.worldLore,
            currentLoc = currentLoc,
            playerPos = playerPos,
            morality = newMorality,
            factionRep = newRep,
            npcLog = npcLog,
            party = party,
            quests = quests,
            merchantStocks = merchants,
            availableMerchants = newMerchants,
            turns = state.turns + 1,
            currentScene = parsed.scene,
            currentSceneDesc = parsed.sceneDesc,
            currentChoices = parsed.choices,
            messages = newMsgs,
            lastCheck = check,
            lastDice = roll,
            combat = combatState,
            travelState = travelState
        )
    }

    private fun levelThreshold(level: Int): Int = Companion.levelThreshold(level)

    /**
     * World events used to interrupt the chat with a big EventCard announcement.
     * That broke immersion and pre-empted the narrator's reveal. Now they're
     * recorded silently — added to `worldEvents` (so the AI keeps weaving them
     * into prose via the per-turn context) and to the timeline (for the death
     * screen and Lore panel's history). The player discovers them through:
     *   1) the narrator dropping organic hints next turn,
     *   2) a small bell on the Lore button + the Lore panel's "Living World" feed.
     */
    private fun maybeRollWorldEvent() {
        val s = _ui.value
        val ev = WorldEvents.maybeGenerate(s.worldLore, s.worldMap, s.turns, s.lastEventTurn) ?: return
        _ui.value = s.copy(
            worldEvents = s.worldEvents + ev,
            lastEventTurn = s.turns,
            // One-line teaser only — no card, no spoilers. The narrator will
            // weave the actual event into prose; the full text lives in Lore.
            messages = s.messages + DisplayMessage.System("\uD83C\uDF10 The world shifts somewhere distant — see Lore.")
        )
        logTimeline("event", "${ev.title}: ${ev.text.take(60)}")
    }

    private fun skillToAbility(skill: String): String = when (skill.lowercase()) {
        "athletics" -> "STR"
        "acrobatics", "sleight of hand", "stealth" -> "DEX"
        "arcana", "history", "investigation", "nature", "religion" -> "INT"
        "animal handling", "insight", "medicine", "perception", "survival" -> "WIS"
        "deception", "intimidation", "performance", "persuasion" -> "CHA"
        else -> "STR"
    }

    /** Infer the most appropriate ability from free-form action text. */
    private fun inferAbilityFromAction(action: String): String {
        val a = action.lowercase()
        return when {
            // STR — physical force, breaking, lifting, grappling
            a.contains("attack") || a.contains("strike") || a.contains("hit") ||
            a.contains("punch") || a.contains("kick") || a.contains("grapple") ||
            a.contains("shove") || a.contains("push") || a.contains("lift") ||
            a.contains("break") || a.contains("smash") || a.contains("force") -> "STR"
            // DEX — stealth, agility, ranged, dodging, lockpicking
            a.contains("sneak") || a.contains("stealth") || a.contains("hide") ||
            a.contains("dodge") || a.contains("shoot") || a.contains("arrow") ||
            a.contains("bow") || a.contains("pick lock") || a.contains("lockpick") ||
            a.contains("climb") || a.contains("acrobat") || a.contains("leap") ||
            a.contains("jump") || a.contains("dart") || a.contains("dagger") -> "DEX"
            // INT — knowledge, investigation, magic, reading, studying
            a.contains("cast") || a.contains("spell") || a.contains("magic") ||
            a.contains("investigate") || a.contains("study") || a.contains("read") ||
            a.contains("arcana") || a.contains("recall") || a.contains("decipher") ||
            a.contains("analyze") || a.contains("examine") || a.contains("research") -> "INT"
            // WIS — perception, insight, survival, healing, sensing
            a.contains("look") || a.contains("listen") || a.contains("search") ||
            a.contains("perceive") || a.contains("sense") || a.contains("track") ||
            a.contains("heal") || a.contains("medicine") || a.contains("pray") ||
            a.contains("meditat") || a.contains("forage") || a.contains("surviv") -> "WIS"
            // CHA — persuasion, deception, intimidation, performance, social
            a.contains("persuad") || a.contains("convince") || a.contains("talk") ||
            a.contains("say") || a.contains("tell") || a.contains("ask") ||
            a.contains("lie") || a.contains("deceiv") || a.contains("bluff") ||
            a.contains("intimidat") || a.contains("threaten") || a.contains("charm") ||
            a.contains("flirt") || a.contains("sing") || a.contains("perform") ||
            a.contains("bribe") || a.contains("negotiate") || a.contains("barter") -> "CHA"
            // Default to STR for truly ambiguous actions
            else -> "STR"
        }
    }

    private fun classProficient(cls: String, skill: String): Boolean {
        val clsDef = Classes.find(cls) ?: return false
        return clsDef.proficiencies.any { it.equals(skill, true) }
    }

    fun dismissLastCheck() {
        _ui.value = _ui.value.copy(lastCheck = null)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun updateHotbar(index: Int, spellName: String?) {
        val s = _ui.value
        val hb = s.hotbar.toMutableList()
        if (index in hb.indices) hb[index] = spellName
        _ui.value = s.copy(hotbar = hb)
    }

    fun equipToggle(item: Item) {
        val s = _ui.value
        val ch = s.character ?: return
        val inv = ch.inventory.toMutableList()
        val idx = inv.indexOfFirst { it.name == item.name }
        if (idx < 0) return
        inv[idx] = inv[idx].copy(equipped = !inv[idx].equipped)
        _ui.value = s.copy(character = ch.copy(inventory = inv))
    }

    fun dismissCompanion(name: String) {
        val s = _ui.value
        _ui.value = s.copy(party = s.party.filterNot { it.name == name })
    }

    fun abandonQuest(id: String) {
        val s = _ui.value
        _ui.value = s.copy(quests = s.quests.map { if (it.id == id) it.copy(status = "failed", turnCompleted = s.turns) else it })
    }

    fun saveToSlot(slot: String = "autosave") {
        viewModelScope.launch {
            SaveStore.write(slot, snapshotSaveData() ?: return@launch)
        }
    }

    /**
     * Captures every piece of state the player should see when reloading:
     * character (with conditions + currency), world, NPC dialogue history,
     * timeline, conditions, in-flight shop/buyback, weather, accumulator, the
     * full chat-feed display messages, and the AI conversation history.
     */
    private fun snapshotSaveData(): SaveData? {
        val s = _ui.value
        val ch = s.character ?: return null
        val wm = s.worldMap ?: return null
        val buyback = _buybackStocks.value.mapValues { entry ->
            entry.value.map { SerializedBuyback(item = it.item, price = it.price) }
        }
        return SaveData(
            character = ch,
            morality = s.morality,
            factionRep = s.factionRep,
            worldMap = wm,
            currentLoc = s.currentLoc,
            playerPos = s.playerPos,
            worldLore = s.worldLore,
            worldEvents = s.worldEvents,
            lastEventTurn = s.lastEventTurn,
            npcLog = s.npcLog,
            party = s.party,
            quests = s.quests,
            hotbar = s.hotbar,
            history = s.history,
            turns = s.turns,
            scene = s.currentScene,
            savedAt = java.time.Instant.now().toString(),
            sceneDesc = s.currentSceneDesc,
            merchantStocks = s.merchantStocks,
            buybackStocks = buyback,
            currentChoices = s.currentChoices,
            timeline = timeline.toList(),
            displayMessages = s.messages,
            deathSave = s.deathSave,
            travelState = s.travelState,
            debugLog = _debugLog.takeLast(50)
        )
    }

    fun loadSlot(slot: String = "autosave") {
        viewModelScope.launch {
            val d = SaveStore.read(slot) ?: return@launch
            // Restore the in-memory timeline so death-screen reads the full life.
            timeline.clear()
            timeline.addAll(d.timeline)
            // Restore buyback stocks from the persisted form.
            _buybackStocks.value = d.buybackStocks.mapValues { e ->
                e.value.map { com.realmsoffate.game.ui.overlays.BuybackEntry(item = it.item, price = it.price) }
            }
            // Restore the debug log so reload → dump still shows historical cache/source data.
            _debugLog.clear()
            _debugLog.addAll(d.debugLog)
            _ui.value = GameUiState(
                character = d.character,
                worldMap = d.worldMap,
                currentLoc = d.currentLoc,
                playerPos = d.playerPos,
                worldLore = d.worldLore,
                worldEvents = d.worldEvents,
                lastEventTurn = d.lastEventTurn,
                npcLog = d.npcLog,
                party = d.party,
                quests = d.quests,
                hotbar = d.hotbar,
                morality = d.morality,
                factionRep = d.factionRep,
                history = d.history,
                turns = d.turns,
                currentScene = d.scene,
                currentSceneDesc = d.sceneDesc,
                merchantStocks = d.merchantStocks,
                currentChoices = d.currentChoices,
                // Restore the rendered chat feed if the save has it; otherwise show
                // a single "loaded" line so the player has context.
                messages = if (d.displayMessages.isNotEmpty()) {
                    d.displayMessages + DisplayMessage.System("Loaded — ${d.character.name}, turn ${d.turns}")
                } else {
                    listOf(DisplayMessage.System("Loaded — ${d.character.name}, turn ${d.turns}"))
                },
                turnStartIndex = if (d.displayMessages.isNotEmpty()) d.displayMessages.lastIndex else 0,
                // Restore in-flight death-save tracker so reload picks the player up
                // exactly where they fell.
                deathSave = d.deathSave,
                // Restore any in-progress road travel.
                travelState = d.travelState
            )
            // Clear any stale ephemeral overlays so they don't leak into the loaded run.
            _pendingLevelUp.value = null
            _restOverlay.value = null
            _activeShop.value = null
            _targetPrompt.value = null
            _showInitiative.value = false
            _lastDeath.value = null
            _screen.value = Screen.Game
        }
    }

    /** Import a SaveData that came from a shared JSON file. */
    fun importSave(json: String) {
        viewModelScope.launch {
            val data = SaveStore.fromJson(json) ?: return@launch
            val slot = SaveStore.slotKeyFor(data.character.name)
            SaveStore.write(slot, data)
            refreshSlots()
        }
    }

    /** Serialises the live in-memory state to a JSON string for export. */
    fun exportCurrentJson(): String? = snapshotSaveData()?.let { SaveStore.toJson(it) }

    /** Suggested filename for a save export, e.g. `Kaelis_L4_turn31.json`. */
    fun exportFilename(): String {
        val ch = _ui.value.character ?: return "save.json"
        val safe = SaveStore.slotKeyFor(ch.name)
        return "${safe}_L${ch.level}_turn${_ui.value.turns}.json"
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val ctx = RealmsApp.instance
                GameViewModel(
                    ai = AiRepository(),
                    prefs = PreferencesStore(ctx)
                )
            }
        }

        /** XP required to reach the given level (D&D 5e thresholds). */
        fun levelThreshold(level: Int): Int = when (level) {
            2 -> 300; 3 -> 900; 4 -> 2700; 5 -> 6500; 6 -> 14000; 7 -> 23000
            8 -> 34000; 9 -> 48000; 10 -> 64000; 11 -> 85000; 12 -> 100000
            else -> level * 12000
        }
    }
}
