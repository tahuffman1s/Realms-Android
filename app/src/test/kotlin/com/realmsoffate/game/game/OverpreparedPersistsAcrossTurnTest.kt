package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: applying Overprepared then running a turn must leave the character at L20.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverpreparedPersistsAcrossTurnTest {

    @Test
    fun `L20 persists after applyParsed runs on next turn`() {
        val ch = GameStateFixture.character(
            name = "Grungle", race = "Half-Orc", cls = "Bard",
            level = 1, hp = 10, maxHp = 10, xp = 0
        ).copy(
            abilities = Abilities(str = 10, dex = 12, con = 14, int = 10, wis = 10, cha = 16)
        )
        val state = GameStateFixture.baseState(character = ch, turns = 2)
        val vm = GameStateFixture.viewModelWithState(state)

        vm.applyOverprepared()

        val afterCheat = vm.ui.value.character!!
        assertEquals("character went to L20 via Overprepared", 20, afterCheat.level)

        val nextState = vm.ui.value
        val parsed = ParsedReplyBuilder()
            .narration("You walk down the road.")
            .xp(50)
            .build()

        val result = vm.applyParsed(
            nextState, nextState.character!!, parsed,
            playerAction = "walk", roll = 12, mod = 3, prof = 2
        )

        assertEquals("level must remain 20 after a normal turn", 20, result.character?.level)
        assertEquals("hp stays at maxHp", afterCheat.maxHp, result.character?.hp)
    }

    /**
     * Reproduces the dispatchToAi race: player hits Send → state snapshot captured with L1 char
     * → player applies Overprepared mid-generation → AI responds → applyParsed runs with STALE
     * L1 snapshot. The turn commit must still surface the L20 character, not the L1 snapshot.
     */
    @Test
    fun `overprepared applied during in-flight turn survives the commit`() {
        val preCheatChar = GameStateFixture.character(
            name = "Grungle", race = "Half-Orc", cls = "Bard",
            level = 1, hp = 10, maxHp = 10, xp = 0
        ).copy(
            abilities = Abilities(str = 10, dex = 12, con = 14, int = 10, wis = 10, cha = 16)
        )
        val preCheatState = GameStateFixture.baseState(character = preCheatChar, turns = 2)
        val vm = GameStateFixture.viewModelWithState(preCheatState)

        // Simulate the user applying Overprepared during AI generation — this mutates
        // _ui.value's character while the dispatcher still holds the pre-turn snapshot.
        vm.applyOverprepared()
        val liveChar = vm.ui.value.character!!
        assertEquals(20, liveChar.level)

        // Now dispatcher runs applyParsed with the STALE pre-cheat state.
        val parsed = ParsedReplyBuilder().narration("The road goes on.").xp(25).build()
        val result = vm.applyParsed(
            preCheatState, preCheatChar, parsed,
            playerAction = "walk", roll = 12, mod = 3, prof = 2
        )

        assertEquals("mid-turn Overprepared must survive the turn commit", 20, result.character?.level)
        assertTrue(
            "no duplicate player bubble in the message feed",
            result.messages.count { it is DisplayMessage.Player && it.text == "walk" } <= 1
        )
    }
}
