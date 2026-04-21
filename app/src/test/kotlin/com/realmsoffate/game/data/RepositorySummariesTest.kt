package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositorySummariesTest {
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

    private fun scene(turnStart: Int, turnEnd: Int, summary: String) = SceneSummary(
        turnStart = turnStart,
        turnEnd = turnEnd,
        sceneName = "scene-$turnStart",
        locationName = "somewhere",
        summary = summary
    )

    @Test
    fun `appendSceneSummary inserts and returns id`() = runTest {
        val id = repo.appendSceneSummary(scene(1, 5, "..."))
        assertTrue(id > 0)
        assertEquals(1, repo.countUnrolledScenes())
    }

    @Test
    fun `recentSceneSummaries excludes rolled-up scenes`() = runTest {
        val s1 = repo.appendSceneSummary(scene(1, 5, "a"))
        repo.appendSceneSummary(scene(6, 10, "b"))
        repo.rollupScenes(listOf(s1), ArcSummary(turnStart = 1, turnEnd = 5, summary = "arc"))
        val recent = repo.recentSceneSummaries(limit = 20)
        assertEquals(listOf("b"), recent.map { it.summary })
    }

    @Test
    fun `rollupScenes inserts arc and marks scenes transactionally`() = runTest {
        val id1 = repo.appendSceneSummary(scene(1, 5, "a"))
        val id2 = repo.appendSceneSummary(scene(6, 10, "b"))
        repo.rollupScenes(listOf(id1, id2), ArcSummary(turnStart = 1, turnEnd = 10, summary = "arc"))
        val arcs = repo.allArcSummaries()
        assertEquals(1, arcs.size)
        assertEquals("arc", arcs[0].summary)
        assertEquals(0, repo.countUnrolledScenes())
    }
}
