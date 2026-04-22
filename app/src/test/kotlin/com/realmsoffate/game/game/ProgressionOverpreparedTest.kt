package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.game.handlers.ProgressionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionOverpreparedTest {

    private fun freshHandler(ch: Character): Triple<ProgressionHandler, MutableStateFlow<GameUiState>, MutableStateFlow<Int?>> {
        val ui = MutableStateFlow(GameUiState(character = ch))
        val pendingLevelUp = MutableStateFlow<Int?>(null)
        val pendingStatPoints = MutableStateFlow(0)
        val pendingFeat = MutableStateFlow(false)
        return Triple(
            ProgressionHandler(ui, pendingLevelUp, pendingStatPoints, pendingFeat),
            ui,
            pendingLevelUp
        )
    }

    @Test fun `L1 character jumps to L20`() {
        val ch = Character(name = "Test", race = "Human", cls = "Fighter", level = 1, xp = 0,
                abilities = Abilities(str = 15, dex = 12, con = 14, int = 10, wis = 10, cha = 8))
        val (handler, ui, _) = freshHandler(ch)
        handler.applyOverprepared()
        val after = ui.value.character!!
        assertEquals(20, after.level)
        assertEquals(GameViewModel.levelThreshold(20), after.xp)
        assertTrue("hp must be > 0", after.hp > 0)
        assertEquals("hp should be full", after.maxHp, after.hp)
    }

    @Test fun `L20 character is a no-op`() {
        val ch = Character(name = "Max", race = "Elf", cls = "Wizard", level = 20, xp = 500_000)
        val (handler, ui, _) = freshHandler(ch)
        handler.applyOverprepared()
        val after = ui.value.character!!
        assertEquals(20, after.level)
    }

    @Test fun `L7 character reaches L20`() {
        val ch = Character(name = "Mid", race = "Dwarf", cls = "Cleric", level = 7, xp = 24_000)
        val (handler, ui, _) = freshHandler(ch)
        handler.applyOverprepared()
        assertEquals(20, ui.value.character!!.level)
    }

    @Test fun `pendingLevelUp cleared after apply`() {
        val ch = Character(name = "Test", race = "Human", cls = "Fighter", level = 1)
        val (handler, _, pendingLevelUp) = freshHandler(ch)
        pendingLevelUp.value = 2
        handler.applyOverprepared()
        assertNull(pendingLevelUp.value)
    }
}
