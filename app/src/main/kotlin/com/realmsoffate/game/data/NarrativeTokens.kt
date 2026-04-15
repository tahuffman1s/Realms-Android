package com.realmsoffate.game.data

/**
 * Narrative tags consumed by the segment parser. Opaque blocks (SCENE / CHOICES /
 * METADATA) are NOT in this enum — they're handled by dedicated regex passes in
 * TagParser and skipped by the tokenizer.
 */
enum class NarrativeTagType {
    NARRATOR_PROSE,
    NARRATOR_ASIDE,
    SNARK,           // legacy aside; maps to Aside segment
    PLAYER_ACTION,
    PLAYER_DIALOG,
    NPC_ACTION,      // requires arg (id or display name)
    NPC_DIALOG;      // requires arg (id or display name)

    companion object {
        /** Returns the enum for a raw tag name, or null if unknown. Case-insensitive. */
        fun fromRaw(name: String): NarrativeTagType? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

        /** Whether a tag of this type carries a colon-separated argument like NPC_DIALOG:vesper */
        fun requiresArg(type: NarrativeTagType): Boolean =
            type == NPC_ACTION || type == NPC_DIALOG
    }
}

/**
 * One token emitted by tokenizeNarrative. Tokens are consumed by buildSegments
 * which walks them with a stack-based state machine.
 */
sealed interface NarrativeToken {
    data class OpenTag(val type: NarrativeTagType, val arg: String?) : NarrativeToken
    data class CloseTag(val type: NarrativeTagType) : NarrativeToken
    data class Text(val content: String) : NarrativeToken
    /** A SCENE / CHOICES / METADATA block the tokenizer walks past without parsing. */
    data object OpaqueBlock : NarrativeToken
}
