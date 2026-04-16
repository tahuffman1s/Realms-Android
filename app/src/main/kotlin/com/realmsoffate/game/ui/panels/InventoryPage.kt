package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

// ----------------- INVENTORY (equipped slots + 5-col backpack grid) -----------------

@Composable
internal fun InventoryContent(state: GameUiState, onEquip: (Item) -> Unit) {
    val ch = state.character ?: return
    var selected by remember(ch) { mutableStateOf<Item?>(null) }
    // ---- Equipped slots (2 col) ----
    val weapon = ch.inventory.firstOrNull { it.equipped && it.type == "weapon" }
    val armor = ch.inventory.firstOrNull { it.equipped && (it.type == "armor" || it.type == "shield") }
    Row(
        Modifier.padding(horizontal = RealmsSpacing.l).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EquippedSlot(
            label = "WEAPON",
            icon = "\u2694\uFE0F",
            item = weapon,
            onTap = { selected = weapon },
            modifier = Modifier.weight(1f)
        )
        EquippedSlot(
            label = "ARMOR",
            icon = "\uD83D\uDEE1\uFE0F",
            item = armor,
            onTap = { selected = armor },
            modifier = Modifier.weight(1f)
        )
    }
    // ---- Selected item detail card ----
    selected?.let { item ->
        Spacer(Modifier.height(8.dp))
        SelectedItemCard(
            item = item,
            onEquipToggle = { onEquip(item); selected = item.copy(equipped = !item.equipped) },
            onUse = {
                // Consumables route through onEquip for now (a "use" hook would live on the VM).
                onEquip(item); selected = null
            }
        )
    }
    // ---- Backpack grid (5 col) ----
    Spacer(Modifier.height(10.dp))
    SectionHeader("  BACKPACK")
    val backpack = ch.inventory.filter { !(it.equipped && it.type in setOf("weapon", "armor", "shield")) }
    if (backpack.isEmpty()) {
        Text(
            "Pack is empty.",
            modifier = Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = RealmsSpacing.m).heightIn(max = 380.dp)
    ) {
        items(backpack) { item ->
            BackpackCell(
                item = item,
                selected = selected?.name == item.name,
                onClick = { selected = item }
            )
        }
    }
}

@Composable
internal fun InventoryPanel(state: GameUiState, onClose: () -> Unit, onEquip: (Item) -> Unit) {
    val ch = state.character
    PanelSheet(
        "\uD83C\uDF92  Inventory",
        subtitle = if (ch == null || ch.inventory.isEmpty()) null else "${ch.inventory.size} items",
        onClose = onClose
    ) {
        InventoryContent(state, onEquip)
    }
}

@Composable
private fun EquippedSlot(
    label: String,
    icon: String,
    item: Item?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rarityColor = item?.let { rarityColor(it.rarity) } ?: MaterialTheme.colorScheme.outlineVariant
    RealmsCard(
        onClick = if (item != null) onTap else null,
        shape = MaterialTheme.shapes.medium,
        outlined = true,
        accentColor = rarityColor,
        contentPadding = RealmsSpacing.m,
        modifier = modifier
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        if (item != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(itemIconFor(item), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    if (item.ac != null) Text("AC ${item.ac}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.damage != null) Text(item.damage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelectedItemCard(item: Item, onEquipToggle: () -> Unit, onUse: () -> Unit) {
    val color = rarityColor(item.rarity)
    RealmsCard(
        outlined = true,
        accentColor = color,
        shape = MaterialTheme.shapes.medium,
        contentPadding = RealmsSpacing.m,
        modifier = Modifier.padding(horizontal = RealmsSpacing.l).fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(itemIconFor(item), style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RarityTag(item.rarity, color)
                    TypeTag(item.type)
                    if (item.qty > 1) Text("×${item.qty}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (item.desc.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(item.desc, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.type in setOf("weapon", "armor", "shield")) {
                Button(
                    onClick = onEquipToggle,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(if (item.equipped) "Unequip" else "Equip")
                }
            }
            if (item.type == "consumable") {
                OutlinedButton(
                    onClick = onUse,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) { Text("Use") }
            }
        }
    }
}

@Composable
private fun BackpackCell(item: Item, selected: Boolean, onClick: () -> Unit) {
    val color = rarityColor(item.rarity)
    Surface(
        onClick = onClick,
        color = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.aspectRatio(0.85f).border(
            1.dp,
            if (selected) color else color.copy(alpha = 0.25f),
            MaterialTheme.shapes.small
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(itemIconFor(item), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    item.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
            if (item.qty > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                ) {
                    Text(
                        "${item.qty}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            // Rarity color bar at the bottom.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(color)
            )
        }
    }
}

@Composable
private fun RarityTag(rarity: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            rarity.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun TypeTag(type: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            type,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun rarityColor(rarity: String): Color {
    val r = RealmsTheme.colors
    return when (rarity.lowercase()) {
        "uncommon" -> r.rarityUncommon
        "rare" -> r.rarityRare
        "epic", "very rare" -> r.rarityEpic
        "legendary", "artifact" -> r.rarityLegendary
        else -> r.rarityCommon
    }
}

private fun itemEmoji(type: String): String = when (type.lowercase()) {
    "weapon" -> "\u2694\uFE0F"
    "armor" -> "\uD83E\uDD7C"
    "shield" -> "\uD83D\uDEE1\uFE0F"
    "consumable" -> "\uD83E\uDDEA"
    "scroll" -> "\uD83D\uDCDC"
    "key" -> "\uD83D\uDD11"
    "food" -> "\uD83C\uDF57"
    "treasure" -> "\uD83D\uDC8E"
    else -> "\uD83D\uDCE6"
}

/**
 * Keyword-driven icon resolver — matches the web's item-icon logic more closely.
 * Falls back to the type-based emoji when no keyword hits.
 */
private fun itemIconFor(item: Item): String {
    val n = item.name.lowercase()
    return when {
        // Weapons
        "sword" in n || "blade" in n || "saber" in n -> "\u2694\uFE0F"
        "dagger" in n -> "\uD83D\uDDE1\uFE0F"
        "axe" in n -> "\uD83E\uDE93"
        "bow" in n || "crossbow" in n -> "\uD83C\uDFF9"
        "staff" in n || "wand" in n || "rod" in n -> "\uD83E\uDE84"
        "hammer" in n || "mace" in n || "maul" in n -> "\uD83D\uDD28"
        "spear" in n || "pike" in n || "halberd" in n -> "\uD83D\uDD31"
        // Armor & shields
        "shield" in n -> "\uD83D\uDEE1\uFE0F"
        "armor" in n || "mail" in n || "plate" in n -> "\uD83E\uDD7C"
        "helm" in n || "helmet" in n -> "\u26D1\uFE0F"
        "boot" in n -> "\uD83E\uDD7E"
        // Consumables
        "potion" in n || "elixir" in n || "draught" in n -> "\uD83E\uDDEA"
        "herb" in n || "tonic" in n -> "\uD83C\uDF3F"
        "food" in n || "ration" in n || "bread" in n -> "\uD83C\uDF5E"
        "wine" in n || "ale" in n || "mead" in n -> "\uD83C\uDF77"
        // Tools / misc
        "scroll" in n -> "\uD83D\uDCDC"
        "book" in n || "tome" in n || "grimoire" in n -> "\uD83D\uDCDA"
        "key" in n -> "\uD83D\uDD11"
        "torch" in n -> "\uD83D\uDD6F\uFE0F"
        "rope" in n -> "\uD83E\uDEA2"
        "lantern" in n -> "\uD83C\uDFEE"
        // Treasure
        "ring" in n -> "\uD83D\uDC8D"
        "amulet" in n || "necklace" in n || "locket" in n -> "\uD83D\uDCFF"
        "crown" in n || "circlet" in n -> "\uD83D\uDC51"
        "gem" in n || "jewel" in n || "diamond" in n -> "\uD83D\uDC8E"
        "gold" in n || "coin" in n -> "\uD83D\uDCB0"
        "map" in n -> "\uD83D\uDDFA\uFE0F"
        else -> itemEmoji(item.type)
    }
}
