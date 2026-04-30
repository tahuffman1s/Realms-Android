package com.realmsoffate.game.data

/**
 * Canonical 5e skill list and freeform-verb-to-skill alias map. Use
 * [canonicalize] at every boundary where a free-text "skill hint" enters the
 * game state (envelope choice parse, classify-action fallback). Returns the
 * canonical name on match, or `""` when the input is a travel/dialogue
 * transition or genuinely unknown verb. Empty is the right answer for
 * "no skill check warranted" — the caller decides the default (the current
 * pre-roll path falls back to Perception when skill is null/blank).
 */
object SkillCanon {
    val CANONICAL_SKILLS: Set<String> = setOf(
        "Athletics", "Acrobatics", "Sleight of Hand", "Stealth",
        "Arcana", "History", "Investigation", "Nature", "Religion",
        "Animal Handling", "Insight", "Medicine", "Perception",
        "Survival", "Deception", "Intimidation", "Performance", "Persuasion",
        "Attack"
    )

    private val CANONICAL_BY_LOWER: Map<String, String> =
        CANONICAL_SKILLS.associateBy { it.lowercase() }

    private val VERB_ALIASES: List<Pair<String, String>> = listOf(
        // Investigation
        "investigate" to "Investigation", "search" to "Investigation",
        "examine" to "Investigation", "inspect" to "Investigation",
        "study" to "Investigation", "analyze" to "Investigation",
        "deduce" to "Investigation", "clue" to "Investigation",
        // Perception
        "look" to "Perception", "listen" to "Perception",
        "watch" to "Perception", "observe" to "Perception",
        "scan" to "Perception", "notice" to "Perception",
        "spot" to "Perception",
        // Intimidation
        "intimidate" to "Intimidation", "threaten" to "Intimidation",
        "scare" to "Intimidation", "menace" to "Intimidation",
        "growl" to "Intimidation",
        // Persuasion
        "persuade" to "Persuasion", "convince" to "Persuasion",
        "negotiate" to "Persuasion", "charm" to "Persuasion",
        "flatter" to "Persuasion", "plead" to "Persuasion",
        // Deception
        "lie" to "Deception", "deceive" to "Deception",
        "bluff" to "Deception", "trick" to "Deception",
        "pretend" to "Deception", "disguise" to "Deception",
        // Stealth
        "sneak" to "Stealth", "hide" to "Stealth",
        "creep" to "Stealth", "shadow" to "Stealth",
        // Acrobatics
        "dodge" to "Acrobatics", "flip" to "Acrobatics",
        "tumble" to "Acrobatics", "balance" to "Acrobatics",
        "leap" to "Acrobatics",
        // Athletics
        "climb" to "Athletics", "jump" to "Athletics",
        "lift" to "Athletics", "push" to "Athletics",
        "pull" to "Athletics", "swim" to "Athletics",
        "grapple" to "Athletics", "shove" to "Athletics",
        // Sleight of Hand
        "pickpocket" to "Sleight of Hand", "lockpick" to "Sleight of Hand",
        "pilfer" to "Sleight of Hand", "sleight" to "Sleight of Hand",
        // Arcana
        "cast" to "Arcana", "spell" to "Arcana",
        "arcane" to "Arcana", "enchant" to "Arcana",
        "ritual" to "Arcana",
        // Medicine
        "heal" to "Medicine", "bandage" to "Medicine",
        "treat" to "Medicine", "medicine" to "Medicine",
        // Survival
        "track" to "Survival", "forage" to "Survival",
        "hunt" to "Survival", "navigate" to "Survival",
        "camp" to "Survival", "survive" to "Survival",
        // Religion
        "pray" to "Religion", "bless" to "Religion",
        "divine" to "Religion", "worship" to "Religion",
        // Nature
        "nature" to "Nature", "plant" to "Nature",
        "herb" to "Nature", "beast" to "Nature",
        // Animal Handling
        "tame" to "Animal Handling", "soothe" to "Animal Handling",
        "calm" to "Animal Handling",
        // Insight
        "insight" to "Insight", "sense motive" to "Insight",
        // Performance
        "sing" to "Performance", "perform" to "Performance",
        "dance" to "Performance", "entertain" to "Performance",
        // History
        "history" to "History", "lore" to "History",
        "recall" to "History",
        // Attack
        "attack" to "Attack", "strike" to "Attack",
        "stab" to "Attack", "slash" to "Attack",
        "shoot" to "Attack", "punch" to "Attack",
        "kick" to "Attack", "fight" to "Attack",
        "kill" to "Attack"
    )

    private val TRANSITION_PREFIXES = listOf(
        "head to", "head toward", "head back", "go to", "go back", "go north",
        "go south", "go east", "go west", "travel to", "travel back",
        "walk to", "walk away", "walk back", "return to", "leave",
        "speak to", "speak with", "talk to", "talk with",
        "approach", "follow", "wait", "rest", "stay", "stand"
    )

    /**
     * Canonicalize a freeform skill label or action verb.
     *
     * Returns the canonical 5e skill name when [raw] matches a canonical name
     * (case-insensitive) or a known verb alias, `""` otherwise. Empty is
     * intentional — callers that want a default should pick one themselves
     * (e.g. Perception). It MUST NOT silently default to STR/Athletics.
     */
    fun canonicalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase()

        CANONICAL_BY_LOWER[lower]?.let { return it }

        if (TRANSITION_PREFIXES.any { lower == it || lower.startsWith("$it ") }) {
            return ""
        }

        for ((stem, canonical) in VERB_ALIASES) {
            if (lower == stem || lower.startsWith("$stem ") || lower.contains(" $stem ") ||
                lower.endsWith(" $stem")
            ) {
                return canonical
            }
        }

        return ""
    }
}
