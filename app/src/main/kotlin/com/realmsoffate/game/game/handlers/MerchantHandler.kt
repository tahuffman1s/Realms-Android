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

    /**
     * Exchange gold ↔ a faction's local currency at a rate derived from its
     * economy. 1 gold → `rate` local coins; `rate = 0.6 + 0.2 * (wealth - 3)`
     * clamped to [0.3, 1.6]. Directions: "to" = gold → local, "from" = local → gold.
     */
    fun exchange(factionName: String, direction: String, goldAmount: Int) {
        val s = ui.value
        val ch = s.character?.deepCopy() ?: return
        val faction = s.worldLore?.factions?.firstOrNull { it.name == factionName } ?: return
        val wealth = faction.economy?.wealth ?: 3
        val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
        val localCurrency = faction.currency
        when (direction) {
            "to" -> {
                if (ch.gold < goldAmount) return
                val localGained = (goldAmount * rate).toInt()
                ch.gold -= goldAmount
                ch.currencyBalances[localCurrency] =
                    (ch.currencyBalances[localCurrency] ?: 0) + localGained
                ui.value = s.copy(
                    character = ch,
                    messages = s.messages + DisplayMessage.System("Exchanged ${goldAmount}g → $localGained $localCurrency.")
                )
            }
            "from" -> {
                val currentLocal = ch.currencyBalances[localCurrency] ?: 0
                val localNeeded = goldAmount // caller passes local-amount-to-spend in this path
                if (currentLocal < localNeeded) return
                val goldGained = (localNeeded / rate).toInt()
                ch.currencyBalances[localCurrency] = currentLocal - localNeeded
                ch.gold += goldGained
                ui.value = s.copy(
                    character = ch,
                    messages = s.messages + DisplayMessage.System("Exchanged $localNeeded $localCurrency → ${goldGained}g.")
                )
            }
        }
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
