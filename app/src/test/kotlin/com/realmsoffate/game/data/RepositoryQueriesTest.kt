package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.db.Mappers
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryQueriesTest {
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
    fun `sceneRelevantNpcs matches by current location`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "a", name = "Alpha", lastLocation = "Hightower", metTurn = 1, lastSeenTurn = 1)))
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "b", name = "Beta", lastLocation = "Lowvale", metTurn = 1, lastSeenTurn = 1)))
        val result = repo.sceneRelevantNpcs("Hightower", currentTurn = 50, withinTurns = 10)
        assertEquals(listOf("Alpha"), result.map { it.name })
    }

    @Test
    fun `sceneRelevantNpcs matches by recent lastSeenTurn`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "a", name = "Alpha", lastLocation = "Other", metTurn = 1, lastSeenTurn = 48)))
        val result = repo.sceneRelevantNpcs("Hightower", currentTurn = 50, withinTurns = 10)
        assertEquals(listOf("Alpha"), result.map { it.name })
    }

    @Test
    fun `keywordMatchedEntities finds by name tokens`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "v", name = "Vesper Vance", metTurn = 1, lastSeenTurn = 1)))
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "m", name = "Mira Cole", metTurn = 1, lastSeenTurn = 1)))
        val hits = repo.keywordMatchedEntities(listOf("vesper", "nothing"))
        assertEquals(1, hits.npcs.size)
        assertEquals("Vesper Vance", hits.npcs[0].name)
    }

    @Test
    fun `snapshotForReducers returns current lists`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "v", name = "V", metTurn = 1, lastSeenTurn = 1)))
        val snap = repo.snapshotForReducers()
        assertEquals(1, snap.npcs.size)
    }
}
