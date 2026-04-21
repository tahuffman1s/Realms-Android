package com.realmsoffate.game.game.handlers

import com.realmsoffate.game.data.Item
import com.realmsoffate.game.data.deepCopy
import com.realmsoffate.game.game.Dice
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.overlays.BuybackEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MerchantHandler(
    private val ui: MutableStateFlow<GameUiState>,
    private val activeShop: MutableStateFlow<String?>,
    private val buybackStocks: MutableStateFlow<Map<String, List<BuybackEntry>>>,
    private val logTimeline: (String, String) -> Unit
) {

    val activeShopState: StateFlow<String?> = activeShop.asStateFlow()
    val buybackStocksState: StateFlow<Map<String, List<BuybackEntry>>> = buybackStocks.asStateFlow()

    fun dismissShop() { activeShop.value = null }
    fun openShop(merchantName: String) { activeShop.value = merchantName }

    fun buyItem(merchant: String, itemName: String, price: Int) {
        val s = ui.value
        val ch = s.character?.deepCopy() ?: return
        if (ch.gold < price) return
        ch.gold -= price
        ch.inventory.add(Item(name = itemName, desc = "Bought from $merchant", type = "item", rarity = "common"))
        ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("Bought $itemName for ${price}g.")
        )
        logTimeline("event", "Bought $itemName for ${price}g from $merchant")
    }

    fun sellItem(merchant: String, item: Item, price: Int) {
        val s = ui.value
        val ch = s.character?.deepCopy() ?: return
        val idx = ch.inventory.indexOfFirst { it.name == item.name && it.rarity == item.rarity }
        if (idx < 0) return
        val existing = ch.inventory[idx]
        if (existing.qty > 1) {
            ch.inventory[idx] = existing.copy(qty = existing.qty - 1)
        } else {
            ch.inventory.removeAt(idx)
        }
        ch.gold += price
        ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("Sold ${item.name} for ${price}g.")
        )
        // Remember for buyback.
        val current = buybackStocks.value.toMutableMap()
        val list = current[merchant].orEmpty().toMutableList()
        list.add(0, BuybackEntry(item = item.copy(qty = 1), price = price * 2))
        while (list.size > 8) list.removeAt(list.size - 1)
        current[merchant] = list
        buybackStocks.value = current
        logTimeline("event", "Sold ${item.name} for ${price}g to $merchant")
    }

    fun buybackItem(merchant: String, item: Item, price: Int) {
        val s = ui.value
        val ch = s.character?.deepCopy() ?: return
        if (ch.gold < price) return
        ch.gold -= price
        ch.inventory.add(item)
        ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("Bought back ${item.name} for ${price}g.")
        )
        val current = buybackStocks.value.toMutableMap()
        val list = current[merchant].orEmpty().toMutableList()
        list.removeAll { it.item.name == item.name && it.price == price }
        current[merchant] = list
        buybackStocks.value = current
    }

    /** CHA check haggle — returns price multiplier (1.0 = no discount, down to 0.8). */
    fun haggle(chaMod: Int): Float {
        val roll = Dice.d20() + chaMod
        return when {
            roll >= 18 -> 0.8f // full 20%
            roll >= 14 -> 0.9f // 10%
            roll >= 10 -> 0.95f // 5%
            else -> 1f
        }
    }
}
