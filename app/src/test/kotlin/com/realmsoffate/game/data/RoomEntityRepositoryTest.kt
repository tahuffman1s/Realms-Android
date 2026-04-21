package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.db.Mappers
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomEntityRepositoryTest {
    private lateinit var db: RealmsDb
    private lateinit var repo: RoomEntityRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = RealmsDb.inMemory(ctx)
        repo = RoomEntityRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeLoggedNpcs emits after upsert of met npc`() = runTest {
        val npc = LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1)
        db.npcDao().upsertOne(Mappers.toEntity(npc))
        val logged = repo.observeLoggedNpcs().first()
        assertEquals(1, logged.size)
        assertEquals("Vesper", logged[0].name)
    }

    @Test
    fun `observeLoreNpcs emits only lore discovery rows`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LoreNpc(id = "sage", name = "Old Sage", race = "elf", role = "historian")))
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1)))
        val lore = repo.observeLoreNpcs().first()
        assertEquals(1, lore.size)
        assertEquals("Old Sage", lore[0].name)
    }

    @Test
    fun `applyChanges insert npc writes to db`() = runTest {
        val npc = LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1)
        repo.applyChanges(EntityChanges(npcs = listOf(NpcChange.Insert(npc))))
        val rows = db.npcDao().getAllLogged()
        assertEquals(1, rows.size)
        assertEquals("Vesper", rows[0].name)
    }

    @Test
    fun `applyChanges update patches fields and appends dialogue`() = runTest {
        val base = LogNpc(
            id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1,
            dialogueHistory = mutableListOf("hi")
        )
        repo.applyChanges(EntityChanges(npcs = listOf(NpcChange.Insert(base))))
        repo.applyChanges(EntityChanges(npcs = listOf(
            NpcChange.Update("v", NpcPatch(
                lastLocation = "Hightower",
                lastSeenTurn = 10,
                relationship = "allied",
                appendDialogue = listOf("bye")
            ))
        )))
        val back = Mappers.toLogNpc(db.npcDao().getById("v")!!)!!
        assertEquals("Hightower", back.lastLocation)
        assertEquals(10, back.lastSeenTurn)
        assertEquals("allied", back.relationship)
        assertEquals(listOf("hi", "bye"), back.dialogueHistory)
    }

    @Test
    fun `applyChanges markDead sets status and discovery`() = runTest {
        repo.applyChanges(EntityChanges(npcs = listOf(
            NpcChange.Insert(LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1))
        )))
        repo.applyChanges(EntityChanges(npcs = listOf(NpcChange.MarkDead("v", 50))))
        val row = db.npcDao().getById("v")!!
        assertEquals("dead", row.status)
        assertEquals("dead", row.discovery)
        assertEquals(50, row.lastSeenTurn)
    }
}
