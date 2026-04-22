package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleExemplarTest {
    @Test
    fun `render trims sample to at most 3 sentences`() {
        val long = "Sentence one. Sentence two. Sentence three. Sentence four. Sentence five."
        val out = StyleExemplar.render(long)
        assertEquals("Sentence one. Sentence two. Sentence three.", out)
    }

    @Test
    fun `render preserves short samples`() {
        val short = "Only one sentence here."
        assertEquals(short, StyleExemplar.render(short))
    }

    @Test
    fun `render handles question and exclamation sentence endings`() {
        val mixed = "What now? Run! Walk."
        assertEquals(mixed, StyleExemplar.render(mixed))
    }

    @Test
    fun `block wraps non-blank sample in a STYLE section`() {
        val block = StyleExemplar.block("Hello world.")
        assertTrue(block.contains("# STYLE"))
        assertTrue(block.contains("Hello world."))
        // Block ends with a newline so concat keeps formatting clean.
        assertTrue(block.endsWith("\n"))
    }

    @Test
    fun `block returns empty string for null or blank sample`() {
        assertEquals("", StyleExemplar.block(null))
        assertEquals("", StyleExemplar.block(""))
        assertEquals("", StyleExemplar.block("   "))
    }
}
