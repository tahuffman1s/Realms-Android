package com.realmsoffate.game.game

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressionHandlerTest {

    private fun setPendingStatPoints(vm: GameViewModel, value: Int) {
        val field = GameViewModel::class.java.getDeclaredField("_pendingStatPoints")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<Int>).value = value
    }

    private fun setPendingFeat(vm: GameViewModel, value: Boolean) {
        val field = GameViewModel::class.java.getDeclaredField("_pendingFeat")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<Boolean>).value = value
    }

    private fun setPendingLevelUp(vm: GameViewModel, value: Int?) {
        val field = GameViewModel::class.java.getDeclaredField("_pendingLevelUp")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<Int?>).value = value
    }

    @Test
    fun `assignStatPoint STR increments str and decrements pending points`() {
        val vm = GameStateFixture.viewModelWithState(GameStateFixture.baseState())
        setPendingStatPoints(vm, 2)

        vm.assignStatPoint("STR")

        assertEquals(11, vm.ui.value.character!!.abilities.str)
        assertEquals(1, vm.pendingStatPoints.value)
    }

    @Test
    fun `assignStatPoint CON also increments maxHp and hp`() {
        val char = GameStateFixture.character(hp = 10, maxHp = 10)
        val vm = GameStateFixture.viewModelWithState(GameStateFixture.baseState(character = char))
        setPendingStatPoints(vm, 1)

        vm.assignStatPoint("CON")

        val ch = vm.ui.value.character!!
        assertEquals(11, ch.abilities.con)
        assertEquals(11, ch.maxHp)
        assertEquals(11, ch.hp)
        assertEquals(0, vm.pendingStatPoints.value)
    }

    @Test
    fun `assignStatPoint with zero pending points does nothing`() {
        val vm = GameStateFixture.viewModelWithState(GameStateFixture.baseState())
        setPendingStatPoints(vm, 0)

        vm.assignStatPoint("STR")

        assertEquals(10, vm.ui.value.character!!.abilities.str)
        assertEquals(0, vm.pendingStatPoints.value)
    }

    @Test
    fun `assignStatPoint with invalid stat name does nothing`() {
        val vm = GameStateFixture.viewModelWithState(GameStateFixture.baseState())
        setPendingStatPoints(vm, 2)

        vm.assignStatPoint("INVALID")

        assertEquals(10, vm.ui.value.character!!.abilities.str)
        assertEquals(2, vm.pendingStatPoints.value)
    }

    @Test
    fun `selectFeat Tough applies maxHp bonus, adds to feats list, clears pendingFeat`() {
        val char = GameStateFixture.character(level = 2, hp = 20, maxHp = 20)
        val vm = GameStateFixture.viewModelWithState(GameStateFixture.baseState(character = char))
        setPendingFeat(vm, true)

        vm.selectFeat("Tough")

        val ch = vm.ui.value.character!!
        assertEquals(listOf("Tough"), ch.feats)
        assertEquals(24, ch.maxHp)
        assertEquals(24, ch.hp)
        assertFalse(vm.pendingFeat.value)
    }

    @Test
    fun `dismissLevelUp clears pendingLevelUp`() {
        val vm = GameStateFixture.viewModelWithState(GameStateFixture.baseState())
        setPendingLevelUp(vm, 3)
        assertEquals(3, vm.pendingLevelUpFlow.value)

        vm.dismissLevelUp()

        assertNull(vm.pendingLevelUpFlow.value)
    }

    @Test
    fun `dismissFeat sets pendingFeat to false`() {
        val vm = GameStateFixture.viewModelWithState(GameStateFixture.baseState())
        setPendingFeat(vm, true)
        assertTrue(vm.pendingFeat.value)

        vm.dismissFeat()

        assertFalse(vm.pendingFeat.value)
    }
}
