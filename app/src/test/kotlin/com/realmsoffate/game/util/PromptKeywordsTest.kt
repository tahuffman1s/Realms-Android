package com.realmsoffate.game.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptKeywordsTest {
    @Test
    fun `empty input yields no tokens`() {
        assertEquals(emptyList<String>(), PromptKeywords.extract(""))
    }

    @Test
    fun `lowercases and splits on non-letters`() {
        val tokens = PromptKeywords.extract("Vesper's house at the Silent-Swamp")
        assertTrue("vesper" in tokens)
        assertTrue("silent" in tokens)
        assertTrue("swamp" in tokens)
    }

    @Test
    fun `drops short tokens under 3 chars`() {
        assertTrue("to" !in PromptKeywords.extract("to the swamp"))
    }

    @Test
    fun `drops common stopwords`() {
        val tokens = PromptKeywords.extract("the king and the court are hostile")
        assertTrue("the" !in tokens)
        assertTrue("and" !in tokens)
        assertTrue("king" in tokens)
        assertTrue("court" in tokens)
    }

    @Test
    fun `dedupes tokens`() {
        val tokens = PromptKeywords.extract("Mira met Mira again")
        assertEquals(1, tokens.count { it == "mira" })
    }
}
