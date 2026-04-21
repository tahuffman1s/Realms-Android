package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AiRepositorySummarizeTest {
    @Test
    fun `parseSummaryResponse handles clean JSON`() {
        val raw = """{"summary":"Met Mira.","keyFacts":["Owes 5g","Tavern burned"]}"""
        val parsed = AiRepository.parseSummaryResponse(raw)
        assertEquals("Met Mira.", parsed?.first)
        assertEquals(listOf("Owes 5g", "Tavern burned"), parsed?.second)
    }

    @Test
    fun `parseSummaryResponse handles fenced JSON`() {
        val raw = "```json\n{\"summary\":\"Short.\",\"keyFacts\":[]}\n```"
        val parsed = AiRepository.parseSummaryResponse(raw)
        assertEquals("Short.", parsed?.first)
        assertEquals(emptyList<String>(), parsed?.second)
    }

    @Test
    fun `parseSummaryResponse returns null on invalid JSON`() {
        assertEquals(null, AiRepository.parseSummaryResponse("not json"))
    }

    @Test
    fun `parseSummaryResponse extracts first JSON object from noisy output`() {
        val raw = "Sure! Here you go: {\"summary\":\"x\",\"keyFacts\":[\"y\"]} (hope that helps)"
        val parsed = AiRepository.parseSummaryResponse(raw)
        assertEquals("x", parsed?.first)
        assertEquals(listOf("y"), parsed?.second)
    }
}
