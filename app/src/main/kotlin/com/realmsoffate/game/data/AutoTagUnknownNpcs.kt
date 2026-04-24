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

    /** If the candidate's *last* word is an equipment/item category, it's an item, not an NPC. */
    private val ITEM_TAIL_WORDS = setOf(
        "sword", "shield", "armor", "armour", "helm", "helmet", "bow", "staff", "wand",
        "ring", "amulet", "necklace", "cloak", "robe", "boots", "gauntlets", "gloves",
        "dagger", "mace", "hammer", "spear", "axe", "blade", "knife", "lance", "pike",
        "arrow", "arrows", "bolt", "bolts", "potion", "elixir", "scroll", "tome",
        "book", "grimoire", "key", "gem", "stone", "crystal", "orb", "idol", "relic",
        "talisman", "charm", "rod", "sceptre", "scepter", "crown", "circlet", "belt",
        "buckler", "tunic", "mail", "plate", "greaves", "bracers", "pauldrons",
        "crossbow", "longbow", "shortbow", "flail", "whip", "chain", "club", "cudgel",
        "shuriken", "kunai", "javelin", "trident", "halberd", "glaive", "naginata"
    )

    /** Two or three capitalized words, each >= 3 letters. */
    private val PROPER_NOUN = Regex("\\b([A-Z][a-z]{2,}(?: [A-Z][a-z]{2,}){1,2})\\b")

    fun scan(
        parsed: ParsedReply,
        existingNpcs: List<LogNpc>,
        currentLoc: String,
        turn: Int,
        itemNames: Set<String> = emptySet(),
        spellNames: Set<String> = emptySet()
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
        val itemKeys = itemNames.map { IdGen.nameKey(it) }.toSet()
        val spellKeys = spellNames.map { IdGen.nameKey(it) }.toSet()
        val out = mutableListOf<LogNpc>()
        val seenKeys = mutableSetOf<String>()
        for (m in PROPER_NOUN.findAll(narrationText)) {
            val name = m.groupValues[1]
            if (name.substringBefore(' ') in COMMON) continue
            if (name.substringAfterLast(' ').lowercase() in ITEM_TAIL_WORDS) continue
            val key = IdGen.nameKey(name)
            if (key in existingKeys || key in seenKeys) continue
            if (key in itemKeys || key in spellKeys) continue
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
