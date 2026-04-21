package com.realmsoffate.game.game

import com.realmsoffate.game.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MerchantHandlerTest {

    // -------------------------------------------------------------------------
    // buyItem
    // -------------------------------------------------------------------------

    @Test
    fun `buyItem deducts gold and adds item to inventory`() {
        val vm = GameStateFixture.viewModelWithState(
            GameStateFixture.baseState(character = GameStateFixture.character(gold = 50))
        )

        vm.buyItem("Elara", "Iron Sword", 30)

        val ch = vm.ui.value.character!!
        assertEquals(20, ch.gold)
        assertTrue(ch.inventory.any { it.name == "Iron Sword" })
    }

    @Test
    fun `buyItem posts a system message`() {
        val vm = GameStateFixture.viewModelWithState(
            GameStateFixture.baseState(character = GameStateFixture.character(gold = 50))
        )

        vm.buyItem("Elara", "Iron Sword", 30)

        val messages = vm.ui.value.messages
        assertTrue(messages.any { it is DisplayMessage.System && it.text.contains("Iron Sword") })
    }

    @Test
    fun `buyItem does nothing when gold is insufficient`() {
        val vm = GameStateFixture.viewModelWithState(
            GameStateFixture.baseState(character = GameStateFixture.character(gold = 10))
        )

        vm.buyItem("Elara", "Dragon Scale", 100)

        val ch = vm.ui.value.character!!
        assertEquals(10, ch.gold)
        assertFalse(ch.inventory.any { it.name == "Dragon Scale" })
    }

    // -------------------------------------------------------------------------
    // sellItem
    // -------------------------------------------------------------------------

    @Test
    fun `sellItem removes item adds gold and records buyback entry`() {
        val sword = Item(name = "Old Sword", qty = 1)
        val vm = GameStateFixture.viewModelWithState(
            GameStateFixture.baseState(character = GameStateFixture.character(gold = 10, inventory = listOf(sword)))
        )

        vm.sellItem("Brom", sword, 15)

        val ch = vm.ui.value.character!!
        assertEquals(25, ch.gold)
        assertFalse(ch.inventory.any { it.name == "Old Sword" })
        val buyback = vm.buybackStocks.value["Brom"].orEmpty()
        assertTrue(buyback.any { it.item.name == "Old Sword" })
    }

    @Test
    fun `sellItem decrements qty instead of removing when qty is greater than one`() {
        val arrows = Item(name = "Arrow", qty = 5)
        val vm = GameStateFixture.viewModelWithState(
            GameStateFixture.baseState(character = GameStateFixture.character(gold = 0, inventory = listOf(arrows)))
        )

        vm.sellItem("Brom", arrows, 2)

        val ch = vm.ui.value.character!!
        val remaining = ch.inventory.firstOrNull { it.name == "Arrow" }
        assertEquals(4, remaining?.qty)
    }

    // -------------------------------------------------------------------------
    // buybackItem
    // -------------------------------------------------------------------------

    @Test
    fun `buybackItem deducts gold adds item back and removes from buyback list`() {
        val sword = Item(name = "Old Sword", qty = 1)
        val vm = GameStateFixture.viewModelWithState(
            GameStateFixture.baseState(character = GameStateFixture.character(gold = 10, inventory = listOf(sword)))
        )
        vm.sellItem("Brom", sword, 10)
        val buybackPrice = vm.buybackStocks.value["Brom"]!!.first().price
        GameStateFixture.injectState(vm, vm.ui.value.copy(
            character = vm.ui.value.character!!.also { it.gold = 100 }
        ))

        vm.buybackItem("Brom", sword, buybackPrice)

        val ch = vm.ui.value.character!!
        assertTrue(ch.gold < 100)
        assertTrue(ch.inventory.any { it.name == "Old Sword" })
        assertTrue(vm.buybackStocks.value["Brom"].orEmpty().none { it.item.name == "Old Sword" && it.price == buybackPrice })
    }

    // -------------------------------------------------------------------------
    // haggle
    // -------------------------------------------------------------------------

    @Test
    fun `haggle returns a price multiplier in the range 0_8 to 1_0`() {
        val vm = GameStateFixture.viewModelWithState(GameStateFixture.baseState())

        repeat(40) {
            val multiplier = vm.haggle(chaMod = 0)
            assertTrue("multiplier $multiplier out of range", multiplier in 0.8f..1.0f)
        }
    }
}
