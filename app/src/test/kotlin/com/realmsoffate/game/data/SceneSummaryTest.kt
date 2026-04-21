package com.realmsoffate.game.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneSummaryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SceneSummary serializes and deserializes`() {
        val s = SceneSummary(
            turnStart = 5,
            turnEnd = 12,
            sceneName = "ashford-tavern",
            locationName = "Ashford",
            summary = "Met Mira the rogue; she offered a job.",
            keyFacts = listOf("Mira owes Garrick 5g", "Tavern burned down at T11")
        )
        val encoded = json.encodeToString(SceneSummary.serializer(), s)
        val decoded = json.decodeFromString(SceneSummary.serializer(), encoded)
        assertEquals(s, decoded)
    }

    @Test
    fun `SceneSummary has sensible defaults for optional fields`() {
        val s = SceneSummary(
            turnStart = 1, turnEnd = 2,
            sceneName = "", locationName = "",
            summary = "test"
        )
        assertEquals(emptyList<String>(), s.keyFacts)
    }
}
