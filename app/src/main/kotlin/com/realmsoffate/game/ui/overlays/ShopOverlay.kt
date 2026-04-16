package com.realmsoffate.game.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.ui.theme.RealmsTheme

/**
 * Shop overlay with Buy / Sell tabs and a buyback lane.
 *   - **Buy** tab: merchant stock at current (possibly haggled) price.
 *   - **Sell** tab: the player's inventory; non-quest items are sellable for
 *     ~50% of a heuristic base value. Sold items are remembered for this
 *     merchant and surface in the **Buyback** section at full price.
 *   - **Haggle**: one CHA check per merchant, up to 20% off buy prices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopOverlay(
    merchant: String,
    stock: Map<String, Int>,
    character: Character,
    onBuy: (item: String, price: Int) -> Unit,
    onSell: (item: Item, price: Int) -> Unit,
    onBuyback: (item: Item, price: Int) -> Unit,
    onHaggle: (chaCheck: () -> Int) -> Float,
    buybackStock: List<BuybackEntry>,
    onClose: () -> Unit
) {
    val realms = RealmsTheme.colors
    var tab by remember(merchant) { mutableStateOf("buy") }
    var haggled by remember(merchant) { mutableStateOf(false) }
    var discount by remember(merchant) { mutableFloatStateOf(1f) }
    var haggleResult by remember(merchant) { mutableStateOf<String?>(null) }

    // Bug fix: if all buyback items have been repurchased, the "back" tab no longer
    // renders a tab button — reset to "buy" so the user isn't stuck on a blank view.
    if (tab == "back" && buybackStock.isEmpty()) tab = "buy"

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("\uD83D\uDCB0 $merchant", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Your gold: ${character.gold}g",
                        style = MaterialTheme.typography.labelMedium,
                        color = realms.goldAccent
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            // Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShopTabBtn("Buy", tab == "buy", Modifier.weight(1f)) { tab = "buy" }
                ShopTabBtn("Sell", tab == "sell", Modifier.weight(1f)) { tab = "sell" }
                if (buybackStock.isNotEmpty()) {
                    ShopTabBtn("Buyback", tab == "back", Modifier.weight(1f)) { tab = "back" }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Haggle row — affects Buy prices only.
            if (tab == "buy") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (!haggled) {
                                haggled = true
                                discount = onHaggle { character.abilities.chaMod }
                                haggleResult = if (discount < 1f)
                                    "Haggled — ${((1f - discount) * 100).toInt()}% off"
                                else "Haggle failed — prices stand."
                            }
                        },
                        enabled = !haggled,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) { Text("HAGGLE", style = MaterialTheme.typography.labelMedium) }
                    Spacer(Modifier.width(10.dp))
                    haggleResult?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (discount < 1f) realms.success else realms.fumbleRed
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when (tab) {
                "buy" -> {
                    if (stock.isEmpty()) {
                        EmptyShopNote("The merchant shrugs — nothing to sell today.")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            items(stock.entries.toList()) { (item, basePrice) ->
                                val price = (basePrice * discount).toInt().coerceAtLeast(1)
                                val canAfford = character.gold >= price
                                ShopRow(
                                    label = item, price = price, canAfford = canAfford,
                                    actionLabel = "Buy",
                                    onAction = { if (canAfford) onBuy(item, price) }
                                )
                            }
                        }
                    }
                }
                "sell" -> {
                    val sellable = character.inventory.filter { it.rarity != "quest" && it.type != "key" }
                    if (sellable.isEmpty()) {
                        EmptyShopNote("Nothing in your pack the merchant wants.")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            items(sellable) { item ->
                                // Bug fix: multiply per-unit value by stack quantity so
                                // selling 10 arrows correctly shows and transacts 10x the price.
                                val price = sellValue(item) * item.qty
                                ShopRow(
                                    label = "${item.name} (${item.rarity})",
                                    price = price,
                                    canAfford = true,
                                    actionLabel = "Sell",
                                    onAction = { onSell(item, price) }
                                )
                            }
                        }
                    }
                }
                "back" -> {
                    if (buybackStock.isEmpty()) {
                        EmptyShopNote("No recent sales to buy back.")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            items(buybackStock) { entry ->
                                val canAfford = character.gold >= entry.price
                                ShopRow(
                                    label = "${entry.item.name} (rebuy)",
                                    price = entry.price,
                                    canAfford = canAfford,
                                    actionLabel = "Buy back",
                                    onAction = { if (canAfford) onBuyback(entry.item, entry.price) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

data class BuybackEntry(val item: Item, val price: Int)

private fun sellValue(item: Item): Int = when (item.rarity.lowercase()) {
    "legendary" -> 500
    "epic" -> 200
    "rare" -> 80
    "uncommon" -> 30
    else -> 8
} * (if (item.type == "weapon" || item.type == "armor") 2 else 1)

@Composable
private fun ShopTabBtn(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(40.dp).border(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(10.dp)
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ShopRow(
    label: String,
    price: Int,
    canAfford: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val realms = RealmsTheme.colors
    Surface(
        onClick = onAction,
        enabled = canAfford,
        color = if (canAfford) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp,
            if (canAfford) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
            RoundedCornerShape(12.dp)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Surface(
                color = realms.goldAccent.copy(alpha = if (canAfford) 0.14f else 0.06f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "${price}g",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (canAfford) realms.goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyShopNote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(vertical = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
