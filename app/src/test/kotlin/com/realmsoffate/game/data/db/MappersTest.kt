package com.realmsoffate.game.data.db

import com.realmsoffate.game.data.Faction
import com.realmsoffate.game.data.GovernmentInfo
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.LoreNpc
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.data.Quest
import com.realmsoffate.game.data.db.entities.NpcEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun `LogNpc roundtrips through NpcEntity`() {
        val npc = LogNpc(
            id = "mira-cole",
            name = "Mira Cole",
            race = "human", role = "innkeeper",
            relationship = "friendly",
            lastLocation = "Silent Swamp",
            metTurn = 3, lastSeenTurn = 12,
            dialogueHistory = mutableListOf("Hello, traveler."),
            memorableQuotes = mutableListOf("T3: \"You'll regret this.\"")
        )
        val entity = Mappers.toEntity(npc)
        val back = Mappers.toLogNpc(entity)!!
        assertEquals(npc.id, back.id)
        assertEquals(npc.name, back.name)
        assertEquals(npc.lastLocation, back.lastLocation)
        assertEquals(npc.dialogueHistory, back.dialogueHistory)
        assertEquals(npc.memorableQuotes, back.memorableQuotes)
    }

    @Test
    fun `LoreNpc roundtrips through NpcEntity`() {
        val lore = LoreNpc(
            id = "old-sage",
            name = "Old Sage",
            race = "elf",
            role = "historian",
            location = "Hightower"
        )
        val entity = Mappers.toEntity(lore)
        val back = Mappers.toLoreNpc(entity)
        assertEquals(lore.id, back.id)
        assertEquals(lore.location, back.location)
    }

    @Test
    fun `toLogNpc returns null when discovery is lore`() {
        val entity = NpcEntity(id = "x", name = "X", nameTokens = "x", discovery = "lore")
        assertNull(Mappers.toLogNpc(entity))
    }

    @Test
    fun `nameTokens is lowercased space-separated`() {
        val npc = LogNpc(id = "v", name = "Vesper Vance", metTurn = 1, lastSeenTurn = 1)
        val e = Mappers.toEntity(npc)
        assertEquals("vesper vance", e.nameTokens)
    }

    @Test
    fun `Quest roundtrips with objectives json`() {
        val q = Quest(
            id = "q1", title = "Find the key", desc = "...", giver = "Mira",
            location = "swamp",
            objectives = mutableListOf("Enter swamp", "Kill hydra"),
            completed = mutableListOf(true, false),
            reward = "50 gp", turnStarted = 5
        )
        val e = Mappers.toEntity(q)
        val back = Mappers.toQuest(e)
        assertEquals(q.objectives, back.objectives)
        assertEquals(q.completed, back.completed)
        assertEquals(q.status, back.status)
    }

    @Test
    fun `Faction roundtrips with government json`() {
        val f = Faction(
            id = "court", name = "Obsidian Court", type = "empire",
            description = "...", baseLoc = "north",
            government = GovernmentInfo(form = "monarchy", ruler = "Elenna")
        )
        val e = Mappers.toEntity(f)
        val back = Mappers.toFaction(e)
        assertEquals("Elenna", back.government?.ruler)
    }

    @Test
    fun `MapLocation roundtrips`() {
        val l = MapLocation(id = 3, name = "Hightower", type = "city", icon = "C", x = 10, y = 20, discovered = true)
        val back = Mappers.toMapLocation(Mappers.toEntity(l))
        assertEquals(l, back)
    }
}
