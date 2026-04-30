package com.realmsoffate.game.data

/**
 * Hand-curated style anchor pinned at the front of every turn's system message.
 *
 * Earlier the same slot was filled by the earliest scene summary, which is
 * past-tense historian voice (see [Prompts.SCENE_SUMMARY_SYS]) — the opposite
 * of the second-person, present-tense, sardonic narrator voice [Prompts.SYS]
 * asks the model to embody. The mismatch dragged generated prose toward bland
 * reportage; replacing the anchor with a hand-curated exemplar in the right
 * voice fixes it.
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
