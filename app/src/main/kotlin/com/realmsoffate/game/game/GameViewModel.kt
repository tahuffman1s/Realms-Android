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
import com.realmsoffate.game.data.PreferencesStore
import com.realmsoffate.game.data.Prompts
import com.realmsoffate.game.data.Quest
import com.realmsoffate.game.data.GraveyardEntry
import com.realmsoffate.game.data.SaveData
import com.realmsoffate.game.data.SaveSlotMeta
import com.realmsoffate.game.data.SaveStore
import com.realmsoffate.game.data.TagParser
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.data.WorldEvent
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val timeOfDay: String = "day",
    /** TimeSystem accumulator — advances phase when it crosses the threshold. */
    val timeAccumulator: Int = 0,
    /** Current weather id from WeatherSystem.table. Rolled on start + travel + long rest. */
    val weather: String = "clear",
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
    val lastCheck: CheckDisplay? = null,
    val lastDice: Int = 0,
    /** Non-null when the current scene is a battle. Round counter + ally initiative. */
    val combat: CombatState? = null,
    /** Non-null when the player is at 0 HP and rolling death saves. */
    val deathSave: DeathSaveState? = null
)

data class CheckDisplay(
    val skill: String, val ability: String, val dc: Int,
    val passed: Boolean, val total: Int, val roll: Int,
    val mod: Int, val prof: Int, val crit: Boolean
)

sealed interface DisplayMessage {
    data class Player(val text: String) : DisplayMessage
    data class Narration(val text: String, val scene: String, val sceneDesc: String) : DisplayMessage
    data class Event(val icon: String, val title: String, val text: String) : DisplayMessage
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

    private val _provider = MutableStateFlow(AiProvider.GEMINI)
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
            timeOfDay = "dawn",
            timeAccumulator = 0,
            messages = s.messages + DisplayMessage.System("Long rest — fully restored.")
        )
        logTimeline("event", "Long rest — full heal + slots restored")
        _restOverlay.value = "long"
    }

    fun dismissRest() { _restOverlay.value = null }

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
    }

    fun refreshSlots() {
        viewModelScope.launch {
            _saveSlots.value = SaveStore.listSlots()
            _graveyard.value = SaveStore.listGraves()
        }
    }

    /** Returns to the title screen without wiping state (for pause/menu). */
    fun returnToTitle() {
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

            // Roll an opening scenario + weather to set atmosphere.
            val scenario = Scenarios.random()
            scenario.modify?.invoke(char)
            val mutationIds = lore.mutationIds.toSet()
            val weather = WeatherSystem.applyMutations(
                WeatherSystem.roll(startLoc.type),
                mutationIds
            )

            _ui.value = GameUiState(
                character = char,
                worldMap = wm,
                currentLoc = wm.startId,
                playerPos = PlayerPos(startLoc.x.toFloat(), startLoc.y.toFloat()),
                worldLore = lore,
                weather = weather,
                timeOfDay = "day",
                history = emptyList(),
                currentScene = scenario.sceneHint,
                messages = listOf(
                    DisplayMessage.System("${scenario.name} — ${char.name} the ${char.race} ${char.cls} arrives in ${startLoc.name}.")
                )
            )
            _screen.value = Screen.Game

            // Seed opening narration with the scenario prompt template.
            val nearby = WorldGen.connected(wm, wm.startId).joinToString(", ") { (d, dist) -> "${d.icon} ${d.name} (${dist}lg)" }
            val loreBlurb = lore.factions.firstOrNull()?.let { "Local faction: ${it.name} (${it.type})." } ?: ""
            val seedPrompt = scenario.promptTemplate(char, startLoc, nearby, loreBlurb)
            submitAction(seedPrompt, seed = true)
        }
    }

    fun submitAction(action: String, skill: String? = null, seed: Boolean = false) {
        val state = _ui.value
        val char = state.character ?: return
        if (state.isGenerating) return
        viewModelScope.launch {
            _ui.value = state.copy(isGenerating = true, error = null)

            // Roll d20 for the action (always a real 1-20; seed turns just skip the dice line in the prompt)
            val roll = Dice.d20()
            val ability = skill?.let { skillToAbility(it) } ?: "STR"
            val mod = char.abilities.modByName(ability)
            val prof = if (skill != null && classProficient(char.cls, skill)) char.proficiency else 0
            val total = roll + mod + prof

            // Build per-turn user prompt (dynamic context)
            val sys = Prompts.SYS
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
            // On seed turns we don't surface the dice reveal — the character hasn't acted yet.
            val updated = applyParsed(state, char, parsed, action, roll, mod, prof, suppressCheck = seed)
            val withHistory = updated.copy(
                history = nh + ChatMsg(role = "assistant", content = raw),
                isGenerating = false
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
                return@launch
            } else if (liveHp > 0 && _ui.value.deathSave != null) {
                // Healed out of it.
                _ui.value = _ui.value.copy(
                    deathSave = null,
                    messages = _ui.value.messages + DisplayMessage.System("Stable — back from the brink.")
                )
            }

            // Combat state: on scene=battle, lazily start a round tracker; otherwise clear.
            val nowScene = withHistory.currentScene
            _ui.value = _ui.value.let { cur ->
                val ch2 = cur.character ?: return@let cur
                if (nowScene == "battle") {
                    val existing = cur.combat
                    val combat = if (existing == null) {
                        // First round of a new battle — flash the INITIATIVE overlay.
                        _showInitiative.value = true
                        CombatSystem.startCombat(ch2, cur.party)
                    } else {
                        CombatSystem.syncHp(existing.next(), ch2, cur.party)
                    }
                    cur.copy(combat = combat)
                } else if (cur.combat != null) {
                    cur.copy(combat = null)
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

    /** Slash-command helper — inserts a system line into the message feed. */
    fun postSystemMessage(text: String) {
        val s = _ui.value
        _ui.value = s.copy(messages = s.messages + DisplayMessage.System(text))
    }

    private fun buildUserPrompt(
        s: GameUiState, ch: Character, action: String, skill: String?, ability: String,
        roll: Int, mod: Int, prof: Int, total: Int, suppressDice: Boolean = false
    ): String {
        val loc = s.worldMap?.locations?.getOrNull(s.currentLoc)
        val localFaction = LoreGen.findLocalFaction(s.worldMap!!, s.worldLore, s.currentLoc)
        val nearby = WorldGen.connected(s.worldMap, s.currentLoc)
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

        val mutCtx = s.worldLore?.mutations.orEmpty()
            .joinToString("\n") { "- $it" }
            .let { if (it.isNotBlank()) "\nWORLD CONDITIONS:\n$it" else "" }

        val primordialCtx = s.worldLore?.primordial.orEmpty().take(1)
            .joinToString("\n") { "- $it" }
            .let { if (it.isNotBlank()) "\nWORLD LORE (refer obliquely):\n$it" else "" }

        val inv = ch.inventory.filter { it.equipped }.joinToString(", ") { it.name }.ifBlank { "nothing" }
        val invAll = ch.inventory.joinToString(", ") { "${it.name} (x${it.qty})" }

        // Expand mutation prompt strings for the narrator — each mutation's full
        // id-mapped AI prompt string is richer than the short description string.
        val mutationPrompts = s.worldLore?.mutationIds.orEmpty()
            .mapNotNull { Mutations.find(it)?.prompt }
            .joinToString("\n")
            .let { if (it.isNotBlank()) "\nACTIVE MUTATIONS:\n$it" else "" }
        val weatherLine = WeatherSystem.table[s.weather]?.let {
            "\nWEATHER: ${it.label} (${it.effects})"
        } ?: ""

        val cs = buildString {
            append("You are ${ch.name}, a L${ch.level} ${ch.race} ${ch.cls}.")
            append("\nHP:${ch.hp}/${ch.maxHp}  AC:${ch.ac}  Gold:${ch.gold}")
            append("\nABILITIES — STR:${ch.abilities.str} DEX:${ch.abilities.dex} CON:${ch.abilities.con} INT:${ch.abilities.int} WIS:${ch.abilities.wis} CHA:${ch.abilities.cha}")
            append("\nPROFICIENCY: +${ch.proficiency}")
            append("\nMORALITY: ${s.morality}   ")
            append("FACTION REP: ${s.factionRep.entries.joinToString { "${it.key}:${it.value}" }.ifBlank { "none" }}")
            append("\nRACIAL PHYSIQUE: ${ch.racialPhysique}")
            loc?.let { append("\nLOCATION: ${it.name} (${it.type})") }
            localFaction?.let { append("\nLOCAL FACTION: ${it.name} (${it.type}); currency: ${it.currency}") }
            append("\nNEARBY: ${nearby.joinToString(", ") { "${it.first.name} (${it.second}lg)" }}")
            append("\nTIME: ${s.timeOfDay}  TURN: ${s.turns + 1}")
            append(weatherLine)
            append(mutationPrompts)
            append(mutCtx); append(primordialCtx); append(partyCtx); append(questCtx); append(npcCtx); append(eventCtx)
            append("\nEquipped: $inv")
            append("\nInventory: ${invAll.ifBlank { "empty" }}")
            ch.backstory?.let { append("\nBACKSTORY: ${it.promptText}") }
        }

        val diceLine = if (suppressDice) "" else "\nDICE: d20=$roll" +
            (if (roll == 20) " CRITICAL SUCCESS!" else if (roll == 1) " CRITICAL FAILURE!" else "") +
            (skill?.let { "  SKILL:$it($ability) modifier:+$mod proficiency:+$prof total:$total" }.orEmpty())

        return "$cs\n\nACTION: $action$diceLine"
    }

    private fun applyParsed(
        state: GameUiState, ch: Character, parsed: ParsedReply,
        playerAction: String, roll: Int, mod: Int, prof: Int,
        suppressCheck: Boolean = false
    ): GameUiState {
        // Mutate a working copy of the character
        val char = ch.copy(
            abilities = ch.abilities.copy(),
            inventory = ch.inventory.toMutableList(),
            knownSpells = ch.knownSpells.toMutableList(),
            spellSlots = ch.spellSlots.toMutableMap(),
            maxSpellSlots = ch.maxSpellSlots.toMutableMap()
        )
        char.hp = (char.hp - parsed.damage + parsed.heal).coerceAtMost(char.maxHp).coerceAtLeast(0)
        char.xp += parsed.xp
        char.gold = (char.gold + parsed.goldGained - parsed.goldLost).coerceAtLeast(0)
        if (parsed.itemsGained.isNotEmpty()) char.inventory.addAll(parsed.itemsGained)
        // Consumed / dropped items — match by name (case-insensitive).
        parsed.itemsRemoved.forEach { name ->
            val idx = char.inventory.indexOfFirst { it.name.equals(name, true) }
            if (idx >= 0) {
                val e = char.inventory[idx]
                if (e.qty > 1) char.inventory[idx] = e.copy(qty = e.qty - 1)
                else char.inventory.removeAt(idx)
            }
        }
        // Conditions
        parsed.conditionsAdded.forEach { c ->
            if (char.conditions.none { it.equals(c, true) }) char.conditions += c
        }
        parsed.conditionsRemoved.forEach { c ->
            char.conditions.removeAll { it.equals(c, true) }
        }
        if (parsed.partyJoins.isNotEmpty()) Unit // handled at state level below

        // Level up if xp threshold reached (D&D 5e-ish milestones)
        val nextXp = levelThreshold(char.level + 1)
        if (char.xp >= nextXp && char.level < 20) {
            char.level += 1
            val clsDef = Classes.find(char.cls)
            char.maxHp += (clsDef?.hitDie ?: 8) + char.abilities.conMod
            char.hp = char.maxHp
            // Refresh slot table to the new level's allotment + surface a full-screen level up overlay.
            SpellSlots.slotsForLevel(char.cls, char.level).forEachIndexed { idx, n ->
                if (idx == 0 || n <= 0) return@forEachIndexed
                char.maxSpellSlots[idx] = n
                char.spellSlots[idx] = n
            }
            pendingLevelUp = char.level
            logTimeline("levelup", "Reached level ${char.level}.")
        }

        // Display message list
        val newMsgs = state.messages.toMutableList().apply {
            add(DisplayMessage.Player(playerAction))
            add(DisplayMessage.Narration(parsed.narration, parsed.scene, parsed.sceneDesc))
        }

        // Morality + faction rep
        val newMorality = (state.morality + parsed.moralDelta).coerceIn(-100, 100)
        val newRep = state.factionRep.toMutableMap().also {
            parsed.repDeltas.forEach { (f, d) -> it[f] = (it.getOrDefault(f, 0) + d).coerceIn(-100, 100) }
        }

        // Travel — rolls fresh weather for the new terrain type.
        var currentLoc = state.currentLoc
        var worldMap = state.worldMap
        var playerPos = state.playerPos
        var newWeather = state.weather
        if (parsed.travelTo != null && worldMap != null) {
            val idx = worldMap.locations.indexOfFirst { it.name.equals(parsed.travelTo, true) }
            if (idx >= 0) {
                val dest = worldMap.locations[idx]
                if (!dest.discovered) {
                    val newLocs = worldMap.locations.toMutableList().also { it[idx] = dest.copy(discovered = true) }
                    worldMap = worldMap.copy(locations = newLocs)
                }
                currentLoc = idx
                playerPos = PlayerPos(dest.x.toFloat(), dest.y.toFloat())
                val mutationIds = state.worldLore?.mutationIds.orEmpty().toSet()
                newWeather = WeatherSystem.applyMutations(WeatherSystem.roll(dest.type), mutationIds)
                newMsgs.add(DisplayMessage.System("You travel to ${dest.icon} ${dest.name}"))
                logTimeline("travel", "Arrived at ${dest.name}")
            }
        }

        // NPC log merge
        val npcLog = state.npcLog.toMutableList()
        parsed.npcsMet.forEach { n ->
            val existing = npcLog.indexOfFirst { it.name.equals(n.name, true) }
            if (existing >= 0) {
                val old = npcLog[existing]
                npcLog[existing] = old.copy(
                    relationship = n.relationship,
                    lastSeenTurn = state.turns + 1,
                    lastLocation = worldMap?.locations?.getOrNull(currentLoc)?.name.orEmpty()
                )
            } else {
                npcLog.add(n.copy(lastLocation = worldMap?.locations?.getOrNull(currentLoc)?.name.orEmpty()))
            }
        }
        // Attach extracted dialogue to any NPC currently in the log so the journal
        // can show quotes with turn numbers. "T12: \"dialogue…\"" is the stored format.
        parsed.dialogues.forEach { (name, quotes) ->
            val idx = npcLog.indexOfFirst { it.name.equals(name, true) }
            if (idx >= 0) {
                val old = npcLog[idx]
                val merged = (old.dialogueHistory + quotes.map { (turn, q) -> "T$turn: \"$q\"" })
                    .takeLast(20) // cap per NPC to keep save size small
                    .toMutableList()
                npcLog[idx] = old.copy(
                    dialogueHistory = merged,
                    lastSeenTurn = state.turns + 1
                )
            }
        }

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

        // Party joins
        val party = state.party.toMutableList().apply {
            parsed.partyJoins.forEach {
                add(it)
                logTimeline("birth", "${it.name} the ${it.race} ${it.role} joined the party.")
            }
        }

        // Merchant stocks — popping up the Shop overlay when a new merchant appears.
        val merchants = state.merchantStocks.toMutableMap()
        parsed.shops.forEach { (name, items) ->
            merchants[name] = items
            _activeShop.value = name
        }

        // Check display for animation (skipped on seed turns where no player action has occurred yet)
        val check = if (suppressCheck) null else parsed.checks.firstOrNull()?.let { c ->
            CheckDisplay(
                skill = c.skill, ability = c.ability, dc = c.dc,
                passed = c.passed, total = c.total, roll = roll,
                mod = mod, prof = prof, crit = roll == 20 || roll == 1
            )
        }

        // Advance time contextually — [TIME:phase] tag forces, otherwise accumulator.
        val (newTime, newAcc) = if (parsed.timeOfDay != null) {
            parsed.timeOfDay to 0
        } else {
            val tick = TimeSystem.advance(
                state.timeOfDay, state.timeAccumulator,
                TimeSystem.classifyAction(playerAction)
            )
            tick.phase to tick.accumulator
        }

        return state.copy(
            character = char,
            worldMap = worldMap,
            currentLoc = currentLoc,
            playerPos = playerPos,
            weather = newWeather,
            morality = newMorality,
            factionRep = newRep,
            npcLog = npcLog,
            party = party,
            quests = quests,
            merchantStocks = merchants,
            turns = state.turns + 1,
            timeOfDay = newTime,
            timeAccumulator = newAcc,
            currentScene = parsed.scene,
            currentSceneDesc = parsed.sceneDesc,
            currentChoices = parsed.choices,
            messages = newMsgs,
            lastCheck = check,
            lastDice = roll
        )
    }

    private fun advanceTimeOfDay(cur: String): String = when (cur) {
        "dawn" -> "day"; "day" -> "dusk"; "dusk" -> "night"; "night" -> "dawn"; else -> "day"
    }

    private fun levelThreshold(level: Int): Int = Companion.levelThreshold(level)

    private fun maybeRollWorldEvent() {
        val s = _ui.value
        val ev = WorldEvents.maybeGenerate(s.worldLore, s.worldMap, s.turns, s.lastEventTurn) ?: return
        _ui.value = s.copy(
            worldEvents = s.worldEvents + ev,
            lastEventTurn = s.turns,
            messages = s.messages + DisplayMessage.Event(ev.icon, ev.title, ev.text)
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

    private fun classProficient(cls: String, skill: String): Boolean {
        val clsDef = Classes.find(cls) ?: return false
        return clsDef.proficiencies.any { it.equals(skill, true) }
    }

    fun dismissLastCheck() {
        _ui.value = _ui.value.copy(lastCheck = null)
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
            val s = _ui.value
            val ch = s.character ?: return@launch
            val wm = s.worldMap ?: return@launch
            val data = SaveData(
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
                timeOfDay = s.timeOfDay,
                history = s.history,
                turns = s.turns,
                scene = s.currentScene,
                savedAt = java.time.Instant.now().toString()
            )
            SaveStore.write(slot, data)
        }
    }

    fun loadSlot(slot: String = "autosave") {
        viewModelScope.launch {
            val d = SaveStore.read(slot) ?: return@launch
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
                timeOfDay = d.timeOfDay,
                morality = d.morality,
                factionRep = d.factionRep,
                history = d.history,
                turns = d.turns,
                currentScene = d.scene,
                messages = listOf(DisplayMessage.System("Loaded — ${d.character.name}, turn ${d.turns}"))
            )
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
    fun exportCurrentJson(): String? {
        val s = _ui.value
        val ch = s.character ?: return null
        val wm = s.worldMap ?: return null
        val data = SaveData(
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
            timeOfDay = s.timeOfDay,
            history = s.history,
            turns = s.turns,
            scene = s.currentScene,
            savedAt = java.time.Instant.now().toString()
        )
        return SaveStore.toJson(data)
    }

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
