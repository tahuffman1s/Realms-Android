package com.realmsoffate.game.data

/**
 * Phase 4 diagnostic — Spike A.
 *
 * Replaces the scene-summary-derived [StyleExemplar] sample (which was
 * historian-voice past-tense) with a hand-curated exemplar in the actual
 * narrator voice that [Prompts.SYS] asks the model to embody:
 * second-person, present-tense, sardonic, BG3-narrator.
 *
 * The string is fed verbatim through [StyleExemplar.render], which keeps the
 * first 3 sentence-terminated sentences. Keep this 3-4 sentences, voice-rich.
 */
object StyleExemplarConstants {
    const val NARRATOR_VOICE: String =
        "You push through the tavern door and the rain follows you in. " +
        "The barkeep looks up from her cloth like she's seen this exact entrance before — and she has, twice this week. " +
        "Between you and me, the careful ones don't come in dripping wet. " +
        "But here you are, and here I am, and this is the part where it gets interesting."
}
