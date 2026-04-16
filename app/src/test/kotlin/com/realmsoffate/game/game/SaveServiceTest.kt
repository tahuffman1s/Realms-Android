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
}
