package com.realmsoffate.game.data

/**
 * Scan AI narration segments for capitalized two-word proper nouns (likely NPC names)
 * that are not already known and are not common English words. Emit synthetic
 * [LogNpc] stubs so they enter the journal even when the model skips the
 * npcs_met metadata entry for minor or recurring characters.
 */
object AutoTagUnknownNpcs {
    private val COMMON = setOf(
        "The", "And", "But", "When", "Where", "What", "Why", "How", "Who", "Which",
        "You", "He", "She", "It", "They", "We",
        "Yes", "No", "Okay", "Well", "Still", "Then", "Now", "Here", "There",
        "After", "Before", "During", "Without", "Within", "Though", "Because"
    )

    /** Two or three capitalized words, each >= 3 letters. */
    private val PROPER_NOUN = Regex("\\b([A-Z][a-z]{2,}(?: [A-Z][a-z]{2,}){1,2})\\b")

    fun scan(
        parsed: ParsedReply,
        existingNpcs: List<LogNpc>,
        currentLoc: String,
        turn: Int
    ): List<LogNpc> {
        val narrationText = parsed.segments.joinToString("\n") {
            when (it) {
                is NarrationSegmentData.Prose -> it.text
                is NarrationSegmentData.Aside -> it.text
                is NarrationSegmentData.PlayerAction -> it.text
                is NarrationSegmentData.PlayerDialog -> it.text
                is NarrationSegmentData.NpcAction -> it.text
                is NarrationSegmentData.NpcDialog -> it.text
            }
        }
        if (narrationText.isBlank()) return emptyList()
        val existingKeys = existingNpcs.map { IdGen.nameKey(it.name) }.toSet()
        val out = mutableListOf<LogNpc>()
        val seenKeys = mutableSetOf<String>()
        for (m in PROPER_NOUN.findAll(narrationText)) {
            val name = m.groupValues[1]
            if (name.substringBefore(' ') in COMMON) continue
            val key = IdGen.nameKey(name)
            if (key in existingKeys || key in seenKeys) continue
            seenKeys += key
            out += LogNpc(
                id = "",
                name = name,
                relationship = "neutral",
                lastLocation = currentLoc,
                metTurn = turn,
                lastSeenTurn = turn
            )
        }
        return out
    }
}
