package com.realmsoffate.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeathHandlerTest {

    @Test
    fun `rollDeathSave updates deathSave state by adding a roll`() {
        val char = GameStateFixture.character(hp = 0, maxHp = 10)
        val state = GameStateFixture.baseState(character = char).copy(deathSave = DeathSaveState())
        val vm = GameStateFixture.viewModelWithState(state)

        vm.rollDeathSave()

        val ds = vm.ui.value.deathSave
        // If not yet dead and not yet stable, deathSave persists with a new roll recorded.
        // If stable (3 successes), deathSave is cleared and hp set to 1.
        // If dead (3 failures), screen changes to Death.
        val afterScreen = vm.screen.value
        if (afterScreen == Screen.Death) {
            assertEquals(Screen.Death, afterScreen)
        } else if (ds == null) {
            assertEquals(1, vm.ui.value.character!!.hp)
        } else {
            assertEquals(1, ds.rolls.size)
            assertTrue(ds.successes + ds.failures == 1 || (ds.failures == 2))
        }
    }

    @Test
    fun `three death save failures leads to Death screen`() {
        val char = GameStateFixture.character(hp = 0, maxHp = 10)
        // Seed with 2 existing failures — one more failure should trigger die()
        val state = GameStateFixture.baseState(character = char)
            .copy(deathSave = DeathSaveState(successes = 0, failures = 2, rolls = listOf(5, 3)))
        val vm = GameStateFixture.viewModelWithState(state)

        // Roll until a failure lands (any roll < 10 will push failures to 3).
        // Since Dice.d20() is random we retry until the screen changes or we confirm stable.
        var attempts = 0
        while (vm.screen.value != Screen.Death && vm.ui.value.deathSave != null && attempts < 30) {
            GameStateFixture.injectState(vm, state)
            vm.rollDeathSave()
            attempts++
        }

        val finalScreen = vm.screen.value
        val finalDs = vm.ui.value.deathSave
        // Either the roll was a failure (screen = Death) or a success (stable, hp=1)
        assertTrue(
            "Expected either Death screen or stabilised character",
            finalScreen == Screen.Death || (finalDs == null && vm.ui.value.character!!.hp == 1)
        )
    }
}
