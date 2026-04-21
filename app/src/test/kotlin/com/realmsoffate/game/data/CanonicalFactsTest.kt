package com.realmsoffate.game.data

import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalFactsTest {
    @Test
    fun `render returns empty string when no facts`() {
        val out = CanonicalFacts(emptyList(), emptyList(), emptyList()).render()
        assertTrue(out.isEmpty())
    }

    @Test
    fun `render includes NPCs section when npcs non-empty`() {
        val facts = CanonicalFacts(
            npcs = listOf(
                LogNpc(
                    id = "v", name = "Vesper", race = "human", role = "sorcerer",
                    faction = "court", status = "alive", relationship = "allied",
                    lastLocation = "Silent Swamp", metTurn = 5, lastSeenTurn = 47,
                    thoughts = "Will help if paid in arcane lore."
                )
            ),
            factions = emptyList(),
            locations = emptyList()
        )
        val out = facts.render()
        assertTrue(out.contains("CANONICAL FACTS"))
        assertTrue(out.contains("Vesper"))
        assertTrue(out.contains("sorcerer"))
        assertTrue(out.contains("Silent Swamp"))
    }

    @Test
    fun `render includes factions and locations sections`() {
        val facts = CanonicalFacts(
            npcs = emptyList(),
            factions = listOf(
                Faction(
                    id = "court", name = "Obsidian Court", type = "empire",
                    description = "", baseLoc = "north",
                    ruler = "Elenna", disposition = "hostile"
                )
            ),
            locations = listOf(
                MapLocation(id = 1, name = "Silent Swamp", type = "marsh", icon = "M", x = 0, y = 0, discovered = true)
            )
        )
        val out = facts.render()
        assertTrue(out.contains("## Factions"))
        assertTrue(out.contains("Obsidian Court"))
        assertTrue(out.contains("Elenna"))
        assertTrue(out.contains("## Locations"))
        assertTrue(out.contains("Silent Swamp"))
    }
}
