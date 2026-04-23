package com.realmsoffate.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The single authoritative per-turn payload format. The AI returns exactly one
 * JSON object of this shape; EnvelopeParser (next task) decodes it into ParsedReply.
 *
 * IMPORTANT: every new per-turn datum goes HERE, not into a new bracket tag.
 * The envelope is the tag registry. One place to edit, one schema to maintain.
 */
@Serializable
data class TurnEnvelope(
    val scene: SceneInfo = SceneInfo(),
    val segments: List<Segment> = emptyList(),
    val choices: List<ChoiceSpec> = emptyList(),
    val metadata: TurnMetadata = TurnMetadata()
)

@Serializable
data class SceneInfo(
    val type: String = "default",
    val desc: String = ""
)

@Serializable
data class ChoiceSpec(
    val text: String = "",
    val skill: String = ""
)

/**
 * Ordered narration segments. Polymorphic on "kind". Rendered in order by the UI.
 * Every segment type this game supports is declared below — adding a new one means
 * adding a new subclass here, nowhere else.
 */
@Serializable
sealed class Segment {
    @Serializable @SerialName("prose")
    data class Prose(val text: String) : Segment()

    @Serializable @SerialName("aside")
    data class Aside(val text: String) : Segment()

    @Serializable @SerialName("player_action")
    data class PlayerAction(val text: String) : Segment()

    @Serializable @SerialName("player_dialog")
    data class PlayerDialog(val text: String) : Segment()

    @Serializable @SerialName("npc_action")
    data class NpcAction(val name: String, val text: String) : Segment()

    @Serializable @SerialName("npc_dialog")
    data class NpcDialog(val name: String, val text: String) : Segment()
}
