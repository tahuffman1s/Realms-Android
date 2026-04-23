package com.realmsoffate.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Abilities(
    var str: Int = 10, var dex: Int = 10, var con: Int = 10,
    var int: Int = 10, var wis: Int = 10, var cha: Int = 10
) {
    fun mod(score: Int) = (score - 10) / 2
    val strMod get() = mod(str)
    val dexMod get() = mod(dex)
    val conMod get() = mod(con)
    val intMod get() = mod(int)
    val wisMod get() = mod(wis)
    val chaMod get() = mod(cha)
    fun byName(n: String) = when (n.uppercase()) {
        "STR" -> str; "DEX" -> dex; "CON" -> con
        "INT" -> int; "WIS" -> wis; "CHA" -> cha
        else -> 10
    }
    fun modByName(n: String) = mod(byName(n))
}

@Serializable
data class Item(
    val name: String,
    val desc: String = "",
    val type: String = "item",
    val rarity: String = "common",
    var qty: Int = 1,
    var equipped: Boolean = false,
    val damage: String? = null,
    val ac: Int? = null
)

@Serializable
data class Backstory(
    val origin: String,
    val motivation: String,
    val flaw: String,
    val bond: String,
    val darkSecret: String,
    val lostItem: String,
    val personalEnemy: String,
    val prophecy: String? = null,
    val promptText: String
)

@Serializable
data class CharacterAppearance(
    /** Display name / hex of skin tone swatch (9 presets). */
    var skinTone: String = "#C49C7C",
    /** Hex of hair color (8 presets). */
    var hairColor: String = "#3A2A1A",
    /** Human-readable hair style token. */
    var hairStyle: String = "Short",
    /** Body build: Lean / Average / Muscular / Stocky / Hulking. */
    var build: String = "Average",
    /** Self-identified gender label. */
    var gender: String = "Unspecified",
    /** Life stage: Young / Adult / Mature / Elder — colors voice/skin/NPC assumptions. */
    var ageBand: String = "Adult"
)

@Serializable
data class Character(
    var name: String,
    var race: String,
    var cls: String,
    var level: Int = 1,
    var xp: Int = 0,
    var hp: Int = 10,
    var maxHp: Int = 10,
    var ac: Int = 10,
    var gold: Int = 25,
    val abilities: Abilities = Abilities(),
    val inventory: MutableList<Item> = mutableListOf(),
    val knownSpells: MutableList<String> = mutableListOf(),
    val spellSlots: MutableMap<Int, Int> = mutableMapOf(),
    val maxSpellSlots: MutableMap<Int, Int> = mutableMapOf(),
    var backstory: Backstory? = null,
    var racialPhysique: String = "",
    var appearance: CharacterAppearance = CharacterAppearance(),
    /** Active conditions — Poisoned, Blessed, Cursed, Charmed, Frightened, etc. */
    val conditions: MutableList<String> = mutableListOf(),
    /** Feats selected on level-up (every 4th level). */
    val feats: MutableList<String> = mutableListOf()
) {
    val proficiency: Int get() = when (level) {
        in 1..4 -> 2; in 5..8 -> 3; in 9..12 -> 4; in 13..16 -> 5; else -> 6
    }
}

/** Deep-copies all mutable state so mutations don't affect the original. */
fun Character.deepCopy(): Character = copy(
    inventory = inventory.toMutableList(),
    knownSpells = knownSpells.toMutableList(),
    spellSlots = spellSlots.toMutableMap(),
    maxSpellSlots = maxSpellSlots.toMutableMap(),
    conditions = conditions.toMutableList(),
    feats = feats.toMutableList()
)

@Serializable
data class MapLocation(
    val id: Int,
    val name: String,
    val type: String,
    val icon: String,
    val x: Int,
    val y: Int,
    var discovered: Boolean = false
)

@Serializable
data class MapRoad(val from: Int, val to: Int, val dist: Int)

@Serializable
data class Terrain(val type: String, val x: Float, val y: Float, val size: Float, val rot: Float = 0f)

@Serializable
data class Lake(val x: Float, val y: Float, val rx: Float, val ry: Float, val rot: Float)

@Serializable
data class RiverPoint(val x: Float, val y: Float)

@Serializable
data class WorldMap(
    val locations: MutableList<MapLocation>,
    val roads: List<MapRoad>,
    val startId: Int,
    val terrain: List<Terrain>,
    val rivers: List<List<RiverPoint>>,
    val lakes: List<Lake>,
    val width: Int = 600,
    val height: Int = 500
)

@Serializable
data class PlayerPos(val x: Float, val y: Float)

@Serializable
data class PastRuler(
    val name: String,
    val yearsAgo: Int,
    val fate: String
)

@Serializable
data class GovernmentInfo(
    val form: String = "monarchy",
    val ruler: String = "",
    val capital: String = "",
    val rulerTrait: String = "",
    val yearsInPower: Int = 0,
    val dynasty: String = "",
    val succession: String = "",
    val pastRulers: List<PastRuler> = emptyList()
)

@Serializable
data class EconomyInfo(
    /** Level label: Thriving / Prosperous / Stable / Strained / Impoverished / Collapsed / Hoarded / War Economy / Black Market / Boom & Bust. */
    val level: String = "Stable",
    /** 0..5 "wealth bars" for the UI's 5-bar indicator. */
    val wealth: Int = 3,
    val description: String = "",
    val exports: List<String> = emptyList(),
    val imports: List<String> = emptyList(),
    val tax: String = ""
)

@Serializable
data class Faction(
    val id: String = "",   // stable identifier; empty = legacy entry awaiting migration
    val name: String,
    val type: String,
    val description: String,
    val baseLoc: String,
    val color: String? = null,
    val government: GovernmentInfo? = null,
    val economy: EconomyInfo? = null,
    val population: String = "",
    val mood: String = "",
    val disposition: String = "",
    val goal: String = "",
    var status: String = "active", // active, destroyed, player_controlled, subjugated
    var ruler: String = ""
)

@Serializable
data class LoreNpc(
    val id: String = "",   // stable identifier; empty = legacy entry awaiting migration
    val name: String,
    val race: String,
    val role: String,
    val age: String = "",
    val appearance: String = "",
    val personality: String = "",
    val location: String = "",
    val faction: String? = null
)

@Serializable
data class HistoryEntry(
    /** One of: primordial / ancient / medieval / dark_age / recent. */
    val era: String,
    /** In-world year (approximate), descending so recent events have the largest value. */
    val year: Int,
    /** Rendered markdown text. */
    val text: String
)

@Serializable
data class WorldLore(
    val factions: List<Faction>,
    val npcs: List<LoreNpc>,
    val primordial: List<String>,
    val mutations: List<String>,
    /** Mutation ids for AI prompt lookup via Mutations.find(id). */
    val mutationIds: List<String> = emptyList(),
    /** Pre-generated rumors surfaced in the Lore panel's Rumors tab. */
    val rumors: List<String> = emptyList(),
    /** World name shown in the Lore panel header and death screen. */
    val worldName: String = "",
    /** Current era label shown under the world name. */
    val era: String = "",
    /** Chronicle of notable events across all 5 eras. */
    val history: List<HistoryEntry> = emptyList()
)

@Serializable
data class WorldEvent(
    val icon: String,
    val title: String,
    val text: String,
    val prompt: String,
    val turn: Int
)

@Serializable
data class LogNpc(
    val id: String = "",   // stable identifier; empty = legacy entry awaiting migration
    val name: String,
    val race: String = "",
    val role: String = "",
    val age: String = "",
    var relationship: String = "neutral",
    val appearance: String = "",
    val personality: String = "",
    val thoughts: String = "",
    val faction: String? = null,
    var lastLocation: String = "",
    val metTurn: Int,
    var lastSeenTurn: Int,
    val dialogueHistory: MutableList<String> = mutableListOf(),
    /**
     * Lines the narrator flagged as memorable via [NPC_QUOTE:Name|quote] — threats,
     * confessions, prophecies, revelations. Curated by the AI, distinct from the
     * rolling `dialogueHistory` which captures every recent line. Capped at 12.
     * Format: "T{turn}: \"quote\"".
     */
    val memorableQuotes: MutableList<String> = mutableListOf(),
    var relationshipNote: String = "",
    var status: String = "alive" // alive, dead, missing, imprisoned
)

@Serializable
data class PartyCompanion(
    val name: String,
    val race: String = "",
    val role: String = "",
    var level: Int = 1,
    var hp: Int = 10,
    var maxHp: Int = 10,
    val appearance: String = "",
    val personality: String = "",
    val faction: String? = null,
    val homeLocation: String = "",
    val joinedTurn: Int,
    val age: String = ""
)

@Serializable
data class Quest(
    val id: String,
    val title: String,
    val type: String = "side",
    val desc: String,
    val giver: String,
    val location: String,
    val objectives: MutableList<String>,
    val completed: MutableList<Boolean> = MutableList(objectives.size) { false },
    val reward: String,
    var status: String = "active",
    val turnStarted: Int,
    var turnCompleted: Int? = null
)

@Serializable
data class ChatMsg(val role: String, val content: String)

/**
 * One turn's worth of raw AI exchange data — captured for retrospective diagnostics.
 * Stored in SaveData so that reload → debug-dump still shows cache/source history.
 */
@Serializable
data class DebugTurn(
    val turn: Int,
    val playerAction: String,
    val classifiedSkill: String?,
    val diceRoll: Int,
    val userPromptSent: String,
    val rawAiResponse: String,
    val parsedScene: String,
    val parsedNarration: String,
    val parsedTags: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SaveData(
    val version: Int = 3,
    val character: Character,
    val morality: Int,
    val factionRep: Map<String, Int>,
    val worldMap: WorldMap,
    val currentLoc: Int,
    val playerPos: PlayerPos?,
    val worldLore: WorldLore?,
    val worldEvents: List<WorldEvent>,
    val lastEventTurn: Int,
    val npcLog: List<LogNpc>,
    val party: List<PartyCompanion>,
    val quests: List<Quest>,
    val hotbar: List<String?>,
    /** Legacy field — kept for v1/v2 save compatibility. No longer surfaced anywhere. */
    val timeOfDay: String = "",
    val history: List<ChatMsg>,
    val turns: Int,
    val scene: String,
    val savedAt: String,
    // ---- v2 additions: complete reload state ----
    val sceneDesc: String = "",
    /** Legacy weather field — preserved for save compat, ignored at load. */
    val weather: String = "",
    /** Legacy time accumulator — preserved for save compat, ignored at load. */
    val timeAccumulator: Int = 0,
    val merchantStocks: Map<String, Map<String, Int>> = emptyMap(),
    val buybackStocks: Map<String, List<SerializedBuyback>> = emptyMap(),
    val currentChoices: List<Choice> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    /** Rendered chat feed so reloads restore the entire visible history. */
    val displayMessages: List<com.realmsoffate.game.game.DisplayMessage> = emptyList(),
    /** In-flight death-save tracker — non-null if the player was bleeding out at save time. */
    val deathSave: com.realmsoffate.game.game.DeathSaveState? = null,
    // ---- diagnostic trail: last ~50 AI exchanges, preserved across reloads ----
    val debugLog: List<DebugTurn> = emptyList(),
    val availableMerchants: List<String> = emptyList(),
    /** Scene summaries persisted for reload. Empty on legacy saves; rebuilt forward from next scene boundary. */
    val sceneSummaries: List<SceneSummary> = emptyList(),
    /** Arc summaries — scene-rollup records from [ArcSummarizer]. Phase 2 addition. */
    val arcSummaries: List<ArcSummary> = emptyList()
)

/**
 * Persistable buyback row — the live UI uses ui.overlays.BuybackEntry but
 * that type is in the UI module. Save/load translates between them.
 */
@Serializable
data class SerializedBuyback(val item: Item, val price: Int)

@Serializable
data class Choice(val n: Int, val text: String, val skill: String)

/**
 * Generates stable slug-style IDs for NPCs and factions.
 * - slug(name) = lowercase ASCII, spaces -> dashes, punctuation stripped
 * - If the slug collides with an existing ID in `existingIds`, appends -2,
 *   -3, etc. until unique.
 * - If the name is blank, falls back to a short timestamp-based suffix.
 */
object IdGen {
    private val SLUG_REGEX = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    fun forName(name: String, existingIds: Set<String>): String {
        val base = slug(name).ifBlank { "entity-${(System.currentTimeMillis() % 100000)}" }
        if (base !in existingIds) return base
        var n = 2
        while ("$base-$n" in existingIds) n++
        return "$base-$n"
    }

    /** Dedup key that collapses separator and case variants so
     *  "Mira Cole" / "Mira-Cole" / "mira-cole" / "MIRA COLE" are equivalent. */
    fun nameKey(s: String): String =
        s.lowercase().replace(Regex("[\\s-]+"), " ").trim()

    /** Title-case reverse of a slug for display: "mira-cole" -> "Mira Cole". */
    fun slugToDisplay(slug: String): String =
        slug.split('-', '_', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

    /** True when `s` looks like a slug — lowercase alnum words joined by single dashes. */
    fun isSlug(s: String): Boolean = s.isNotBlank() && s.matches(SLUG_REGEX)

    private fun slug(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")     // drop non-alphanumeric except space/dash
            .replace(Regex("\\s+"), "-")              // spaces -> dashes
            .replace(Regex("-+"), "-")                // collapse multiple dashes
            .trim('-')
            .take(60)                                  // cap length
    }
}

/**
 * Fixes a LogNpc whose display name leaked from the slug ID (e.g., name="mira-cole").
 * Applied on save load so older games that accumulated broken entries read cleanly.
 */
fun LogNpc.sanitizeDisplayName(): LogNpc {
    if (!IdGen.isSlug(name)) return this
    val looksLikeSlug = id.isBlank() || name.equals(id, ignoreCase = true)
    return if (looksLikeSlug) copy(name = IdGen.slugToDisplay(name)) else this
}

// ============================================================================
//  Turn metadata JSON schema — Phase 3
// ============================================================================
// The AI emits a single [METADATA]{...}[/METADATA] block at the end of each
// response containing ALL mechanical side effects for the turn. Tagged prose
// ([NARRATOR_PROSE], [NPC_DIALOG:id], etc.) remains the narrative format; this
// schema is strictly for the state mutations that used to ride in inline tags
// like [DAMAGE:N], [ITEM:Name|...], [NPC_MET:...], etc.
//
// All fields have defaults so the AI can omit any section that doesn't apply
// this turn. snake_case JSON keys because DeepSeek generates them more
// reliably than camelCase (both work via @SerialName mapping).
//
// Missing / malformed / omitted JSON block -> parser falls back to the legacy
// inline-tag regex extraction. Old saves and ongoing games keep working.

@Serializable
data class TurnMetadata(
    @Serializable(with = LenientInt::class) val damage: Int = 0,
    @Serializable(with = LenientInt::class) val heal: Int = 0,
    @Serializable(with = LenientInt::class) val xp: Int = 0,
    @SerialName("gold_gained") @Serializable(with = LenientInt::class) val goldGained: Int = 0,
    @SerialName("gold_lost") @Serializable(with = LenientInt::class) val goldLost: Int = 0,
    @SerialName("moral_delta") @Serializable(with = LenientInt::class) val moralDelta: Int = 0,
    @SerialName("items_gained") val itemsGained: List<ItemSpec> = emptyList(),
    @SerialName("items_removed") val itemsRemoved: List<String> = emptyList(),
    @SerialName("conditions_added") val conditionsAdded: List<String> = emptyList(),
    @SerialName("conditions_removed") val conditionsRemoved: List<String> = emptyList(),
    @SerialName("npcs_met") val npcsMet: List<NpcMetSpec> = emptyList(),
    @SerialName("npc_updates") val npcUpdates: List<FieldUpdateSpec> = emptyList(),
    @SerialName("npc_deaths") val npcDeaths: List<String> = emptyList(),
    @SerialName("npc_quotes") val npcQuotes: List<NpcQuoteSpec> = emptyList(),
    @SerialName("quest_starts") val questStarts: List<QuestSpec> = emptyList(),
    @SerialName("quest_updates") val questUpdates: List<QuestUpdateSpec> = emptyList(),
    @SerialName("quest_completes") val questCompletes: List<String> = emptyList(),
    @SerialName("quest_fails") val questFails: List<String> = emptyList(),
    val enemies: List<EnemySpec> = emptyList(),
    @SerialName("faction_updates") val factionUpdates: List<FieldUpdateSpec> = emptyList(),
    @SerialName("rep_deltas") val repDeltas: List<RepDeltaSpec> = emptyList(),
    @SerialName("lore_entries") val loreEntries: List<String> = emptyList(),
    val check: CheckSpec? = null,
    @SerialName("travel_to") val travelTo: String? = null,
    @SerialName("time_of_day") val timeOfDay: String? = null,
    val shops: List<ShopSpec> = emptyList(),
    @SerialName("party_joins") val partyJoins: List<PartyJoinSpec> = emptyList(),
    @SerialName("party_leaves") val partyLeaves: List<String> = emptyList()
)

@Serializable
data class ItemSpec(
    val name: String = "",
    val desc: String = "",
    val type: String = "item",
    val rarity: String = "common"
)

@Serializable
data class NpcMetSpec(
    val id: String = "",
    val name: String = "",
    val race: String = "",
    val role: String = "",
    val age: String = "",
    val relationship: String = "neutral",
    val appearance: String = "",
    val personality: String = "",
    val thoughts: String = ""
)

/** Generic field update for [NPC_UPDATE] / [FACTION_UPDATE] style operations. */
@Serializable
data class FieldUpdateSpec(val id: String = "", val field: String = "", val value: String = "")

@Serializable
data class NpcQuoteSpec(val id: String = "", val quote: String = "")

@Serializable
data class QuestSpec(
    val title: String = "",
    val type: String = "side",
    val desc: String = "",
    val giver: String = "",
    val objectives: List<String> = emptyList(),
    val reward: String = ""
)

@Serializable
data class QuestUpdateSpec(val title: String = "", val objective: String = "")

@Serializable
data class EnemySpec(
    val name: String = "",
    @Serializable(with = LenientInt::class) val hp: Int = 0,
    @SerialName("max_hp") @Serializable(with = LenientInt::class) val maxHp: Int = 0
)

@Serializable
data class RepDeltaSpec(
    val faction: String = "",
    @Serializable(with = LenientInt::class) val delta: Int = 0
)

@Serializable
data class CheckSpec(
    val skill: String = "",
    val ability: String = "",
    @Serializable(with = LenientInt::class) val dc: Int = 10,
    @Serializable(with = LenientBool::class) val passed: Boolean = false,
    @Serializable(with = LenientInt::class) val total: Int = 0
)

@Serializable
data class ShopSpec(val merchant: String = "", val items: Map<String, Int> = emptyMap())

@Serializable
data class PartyJoinSpec(
    val name: String = "",
    val race: String = "",
    val role: String = "",
    @Serializable(with = LenientInt::class) val level: Int = 1,
    @SerialName("max_hp") @Serializable(with = LenientInt::class) val maxHp: Int = 10,
    val appearance: String = "",
    val personality: String = ""
)
