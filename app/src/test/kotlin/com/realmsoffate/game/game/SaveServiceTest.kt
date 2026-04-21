package com.realmsoffate.game.game

import com.realmsoffate.game.game.GameStateFixture.baseState
import com.realmsoffate.game.game.GameStateFixture.character
import com.realmsoffate.game.game.GameStateFixture.viewModelWithState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveServiceTest {

    @Test
    fun `exportCurrentJson returns null when no character`() {
        val vm = viewModelWithState(GameUiState())
        assertNull(vm.exportCurrentJson())
    }

    @Test
    fun `exportCurrentJson returns JSON containing character name`() {
        val vm = viewModelWithState(baseState(character = character(name = "Aryn")))
        val json = vm.exportCurrentJson()
        assertNotNull(json)
        assertTrue(json!!.contains("Aryn"))
    }

    @Test
    fun `exportFilename returns save dot json when no character`() {
        val vm = viewModelWithState(GameUiState())
        assertEquals("save.json", vm.exportFilename())
    }

    @Test
    fun `exportFilename encodes name level and turn`() {
        val vm = viewModelWithState(baseState(character = character(name = "Test Hero", level = 1), turns = 0))
        assertEquals("test_hero_L1_turn0.json", vm.exportFilename())
    }

    @Test
    fun `debugDumpFilename matches expected pattern`() {
        val before = System.currentTimeMillis() / 1000
        val vm = viewModelWithState(baseState(character = character(name = "Test Hero"), turns = 5))
        val filename = vm.debugDumpFilename()
        val after = System.currentTimeMillis() / 1000

        assertTrue(filename.startsWith("debug_test_hero_T5_"))
        assertTrue(filename.endsWith(".txt"))

        val epoch = filename.removeSuffix(".txt").substringAfterLast("_").toLong()
        assertTrue(epoch in before..after)
    }

    @Test
    fun `exportCurrentJson includes sceneSummaries`() {
        val summary = com.realmsoffate.game.data.SceneSummary(
            turnStart = 1, turnEnd = 5,
            sceneName = "ashford-tavern", locationName = "Ashford",
            summary = "Met Mira the rogue.",
            keyFacts = listOf("Owes 5g")
        )
        val state = baseState(character = character(name = "Roundtrip"))
            .copy(sceneSummaries = listOf(summary))
        val vm = viewModelWithState(state)
        val json = vm.exportCurrentJson()
        assertNotNull(json)
        assertTrue(json!!.contains("Met Mira the rogue"))
        assertTrue(json.contains("Owes 5g"))
        assertTrue(json.contains("ashford-tavern"))
    }

    @Test
    fun `sceneSummaries survive SaveData JSON roundtrip`() {
        val summaries = listOf(
            com.realmsoffate.game.data.SceneSummary(
                turnStart = 1, turnEnd = 4,
                sceneName = "s1", locationName = "L1",
                summary = "First scene", keyFacts = emptyList()
            ),
            com.realmsoffate.game.data.SceneSummary(
                turnStart = 5, turnEnd = 9,
                sceneName = "s2", locationName = "L2",
                summary = "Second scene", keyFacts = listOf("fact")
            )
        )
        val state = baseState(character = character(name = "Roundtrip2"))
            .copy(sceneSummaries = summaries)
        val vm = viewModelWithState(state)
        val json = vm.exportCurrentJson()!!
        val parsed = com.realmsoffate.game.data.SaveStore.fromJson(json)
        assertNotNull(parsed)
        assertEquals(summaries, parsed!!.sceneSummaries)
    }
}
