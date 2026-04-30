package com.realmsoffate.game.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleExemplarConstantsTest {

    @Test
    fun `narrator exemplar is non-blank`() {
        assertTrue(StyleExemplarConstants.NARRATOR_VOICE.isNotBlank())
    }

    @Test
    fun `narrator exemplar uses second-person present tense, not past-tense reportage`() {
        val text = StyleExemplarConstants.NARRATOR_VOICE.lowercase()
        // Second-person is the BG3 narrator's calling card.
        assertTrue("should address the player", text.contains(" you "))
        // Past-tense reportage signals (the historian voice we are running from).
        assertFalse("avoid past-tense -ed verbs typical of scene summaries",
            Regex("\\b(walked|killed|told|asked|leaned|nodded|said)\\b").containsMatchIn(text))
    }

    @Test
    fun `narrator exemplar contains at least 3 sentences for StyleExemplar render`() {
        val sentences = StyleExemplarConstants.NARRATOR_VOICE
            .split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        assertTrue("StyleExemplar.render takes the first 3 sentences; need ≥3",
            sentences.size >= 3)
    }
}
