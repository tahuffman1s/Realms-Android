package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class InfiniteGoldClampTest {

    @After fun tearDown() { Cheats.reset() }

    @Test fun `clamp bumps gold to 999999 when infiniteGold is on`() {
        Cheats.infiniteGold = true
        val ch = Character(name = "T", race = "H", cls = "Fighter", gold = 100)
        val ui = MutableStateFlow(GameUiState(character = ch))
        clampInfiniteGold(ui)
        assertEquals(999_999, ui.value.character!!.gold)
    }

    @Test fun `clamp is a no-op when flag is off`() {
        Cheats.infiniteGold = false
        val ch = Character(name = "T", race = "H", cls = "Fighter", gold = 100)
        val ui = MutableStateFlow(GameUiState(character = ch))
        clampInfiniteGold(ui)
        assertEquals(100, ui.value.character!!.gold)
    }

    @Test fun `clamp is idempotent`() {
        Cheats.infiniteGold = true
        val ch = Character(name = "T", race = "H", cls = "Fighter", gold = 999_999)
        val ui = MutableStateFlow(GameUiState(character = ch))
        val before = ui.value
        clampInfiniteGold(ui)
        assertEquals("no state churn", before, ui.value)
    }
}
