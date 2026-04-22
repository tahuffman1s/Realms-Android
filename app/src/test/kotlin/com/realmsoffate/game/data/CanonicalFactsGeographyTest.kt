package com.realmsoffate.game.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalFactsGeographyTest {
    private val greyhollow = MapLocation(id = 1, name = "Greyhollow", type = "village", icon = "🏘", x = 0, y = 0, discovered = true)
    private val mirewood = MapLocation(id = 2, name = "Mirewood", type = "forest", icon = "🌲", x = 10, y = 0, discovered = true)
    private val duskhollow = MapLocation(id = 3, name = "Duskhollow", type = "ruin", icon = "🏚", x = -10, y = 0, discovered = false)

    @Test
    fun `render includes current location and lists adjacents`() {
        val facts = CanonicalFacts(
            npcs = emptyList(),
            factions = emptyList(),
            locations = listOf(greyhollow, mirewood, duskhollow),
            currentLocationId = 1,
            adjacencies = mapOf(1 to listOf(2, 3))
        )
        val rendered = facts.render()
        assertTrue("header missing:\n$rendered", rendered.contains("# CANONICAL FACTS"))
        assertTrue("current section missing:\n$rendered", rendered.contains("## Current location"))
        assertTrue("current name missing:\n$rendered", rendered.contains("Greyhollow"))
        assertTrue("adjacents missing:\n$rendered", rendered.contains("Adjacent: Mirewood, Duskhollow"))
    }

    @Test
    fun `current location is not duplicated under Locations section`() {
        val facts = CanonicalFacts(
            npcs = emptyList(),
            factions = emptyList(),
            locations = listOf(greyhollow, mirewood),
            currentLocationId = 1,
            adjacencies = mapOf(1 to listOf(2))
        )
        val rendered = facts.render()
        val greyhollowOccurrences = Regex("Greyhollow").findAll(rendered).count()
        org.junit.Assert.assertEquals("Greyhollow should appear exactly once (in Current location block)", 1, greyhollowOccurrences)
        assertTrue("Mirewood still in Locations section", rendered.contains("## Locations"))
    }

    @Test
    fun `empty when no npcs factions locations and no current location`() {
        val facts = CanonicalFacts(emptyList(), emptyList(), emptyList())
        assertTrue(facts.isEmpty)
        assertTrue(facts.render().isEmpty())
    }

    @Test
    fun `not empty when only current location is set`() {
        val facts = CanonicalFacts(
            npcs = emptyList(), factions = emptyList(),
            locations = listOf(greyhollow),
            currentLocationId = 1
        )
        assertFalse(facts.isEmpty)
        assertTrue(facts.render().contains("Greyhollow"))
    }
}
