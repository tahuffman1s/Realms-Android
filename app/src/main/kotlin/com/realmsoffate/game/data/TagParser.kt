package com.realmsoffate.game.data

/**
 * Indicates which parse path was taken for a given turn response.
 * Exposed on [ParsedReply] so downstream consumers (e.g. debug logging) can report it.
 */
enum class ParseSource {
    /** Envelope JSON decoded successfully. */
    JSON,
    /** Envelope was malformed or missing — zero effects applied this turn. */
    INVALID
}

/**
 * Ordered content segments extracted from the LLM response.
 * The UI renders each type as a distinct visual element (bubble, pill, etc.).
 */
@kotlinx.serialization.Serializable
sealed interface NarrationSegmentData {
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("prose")
    data class Prose(val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("aside")
    data class Aside(val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("npc_dialog")
    data class NpcDialog(val name: String, val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("player_dialog")
    data class PlayerDialog(val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("player_action")
    data class PlayerAction(val text: String) : NarrationSegmentData

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("npc_action")
    data class NpcAction(val name: String, val text: String) : NarrationSegmentData
}

/**
 * The parsed result of a single LLM turn response.
 *
 * All narration content lives in [segments]. The [narration] field is kept for
 * backwards-compatibility with callers that were written before envelope migration;
 * it is always empty post-migration.
 */
data class ParsedReply(
    val scene: String,
    val sceneDesc: String,
    val narration: String,
    val choices: List<Choice>,
    val damage: Int,
    val heal: Int,
    val xp: Int,
    val goldGained: Int,
    val goldLost: Int,
    val itemsGained: List<Item>,
    val checks: List<CheckResult>,
    val npcsMet: List<LogNpc>,
    val questStarts: List<Quest>,
    val questUpdates: List<Pair<String, String>>,
    val questComplete: List<String>,
    val questFails: List<String>,
    val shops: List<Pair<String, Map<String, Int>>>,
    val travelTo: String?,
    val partyJoins: List<PartyCompanion>,
    val timeOfDay: String?,
    val moralDelta: Int,
    val repDeltas: List<Pair<String, Int>>,
    val worldEventHook: String?,
    val conditionsAdded: List<String> = emptyList(),
    val conditionsRemoved: List<String> = emptyList(),
    val itemsRemoved: List<String> = emptyList(),
    val partyLeaves: List<String> = emptyList(),
    val enemies: List<Triple<String, Int, Int>> = emptyList(),
    val factionUpdates: List<Triple<String, String, String>> = emptyList(),
    val npcDeaths: List<String> = emptyList(),
    val npcUpdates: List<Triple<String, String, String>> = emptyList(),
    val loreEntries: List<String> = emptyList(),
    val npcQuotes: List<Pair<String, String>> = emptyList(),
    val narratorProse: List<String> = emptyList(),
    val narratorAsides: List<String> = emptyList(),
    val playerDialogs: List<String> = emptyList(),
    val npcDialogs: List<Pair<String, String>> = emptyList(),
    val playerActions: List<String> = emptyList(),
    val npcActions: List<Pair<String, String>> = emptyList(),
    val segments: List<NarrationSegmentData> = emptyList(),
    val source: ParseSource = ParseSource.INVALID
)

data class CheckResult(
    val skill: String, val ability: String, val dc: Int,
    val passed: Boolean, val total: Int
)

object TagParser {
    /**
     * Parses a DeepSeek turn response into a ParsedReply.
     *
     * Post-envelope-migration: every response is a single JSON envelope,
     * so this shim delegates to EnvelopeParser. Kept to avoid churning
     * every call site.
     */
    fun parse(raw: String, currentTurn: Int): ParsedReply =
        EnvelopeParser.parse(raw, currentTurn)
}
