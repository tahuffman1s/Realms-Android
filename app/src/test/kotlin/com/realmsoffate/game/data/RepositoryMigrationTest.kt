package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class RepositoryMigrationTest {
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
    fun `seed dedupes NPCs present in both worldLore and npcLog`() = runTest {
        val lore = LoreNpc(id = "mira-cole", name = "Mira Cole", race = "human", role = "innkeeper", location = "Hightower")
        val log = LogNpc(id = "mira-cole", name = "Mira Cole", race = "human", metTurn = 5, lastSeenTurn = 30, relationship = "allied")
        repo.seedFromSaveData(fixtureSave(loreNpcs = listOf(lore), logNpcs = listOf(log)))
        val loreCount = db.npcDao().observeLore().first().size
        val loggedCount = db.npcDao().observeLogged().first().size
        assertEquals(0, loreCount)
        assertEquals(1, loggedCount)
    }

    @Test
    fun `seed inserts quests factions locations and scene summaries`() = runTest {
        val save = fixtureSave(
            quests = listOf(
                Quest(id = "q1", title = "t", desc = "", giver = "g", location = "", objectives = mutableListOf("a"), reward = "", turnStarted = 1)
            ),
            factions = listOf(Faction(id = "f", name = "F", type = "", description = "", baseLoc = "")),
            scenes = listOf(SceneSummary(turnStart = 1, turnEnd = 5, sceneName = "s1", locationName = "L", summary = "s1"))
        )
        repo.seedFromSaveData(save)
        assertEquals(1, db.questDao().getAll().size)
        assertEquals(1, db.factionDao().getAll().size)
        assertEquals(1, db.sceneSummaryDao().getAll().size)
    }
}

private fun fixtureSave(
    loreNpcs: List<LoreNpc> = emptyList(),
    logNpcs: List<LogNpc> = emptyList(),
    quests: List<Quest> = emptyList(),
    factions: List<Faction> = emptyList(),
    scenes: List<SceneSummary> = emptyList()
): SaveData = SaveData(
    character = Character(name = "Test", race = "human", cls = "fighter"),
    morality = 0,
    factionRep = emptyMap(),
    worldMap = WorldMap(
        locations = mutableListOf(),
        roads = emptyList(),
        startId = 0,
        terrain = emptyList(),
        rivers = emptyList(),
        lakes = emptyList()
    ),
    currentLoc = 0,
    playerPos = null,
    worldLore = WorldLore(factions = factions, npcs = loreNpcs, primordial = emptyList(), mutations = emptyList()),
    worldEvents = emptyList(),
    lastEventTurn = 0,
    npcLog = logNpcs,
    party = emptyList(),
    quests = quests,
    hotbar = emptyList(),
    history = emptyList(),
    turns = 0,
    scene = "",
    savedAt = "2026-04-21",
    sceneSummaries = scenes
)
