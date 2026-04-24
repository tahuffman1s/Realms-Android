package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

private val EQUIPPABLE_TYPES = setOf("weapon", "armor", "shield", "amulet", "ring", "clothes")

// ----------------- INVENTORY (equipped slots + 5-col backpack grid) -----------------

@Composable
internal fun InventoryContent(state: GameUiState, onEquip: (Item) -> Unit, onUse: (Item) -> Unit) {
    val ch = state.character ?: return
    var selected by remember(ch) { mutableStateOf<Item?>(null) }
    // ---- Equipped slots ----
    val weapon = ch.inventory.firstOrNull { it.equipped && it.type == "weapon" }
    val armor = ch.inventory.firstOrNull { it.equipped && (it.type == "armor" || it.type == "shield") }
    val amulet = ch.inventory.firstOrNull { it.equipped && it.type == "amulet" }
    val clothes = ch.inventory.firstOrNull { it.equipped && it.type == "clothes" }
    val equippedRings = ch.inventory.filter { it.equipped && it.type == "ring" }.take(2)
    val ring1 = equippedRings.getOrNull(0)
    val ring2 = equippedRings.getOrNull(1)
    val equippedNames = listOfNotNull(weapon?.name, armor?.name, amulet?.name, clothes?.name, ring1?.name, ring2?.name)
    val selectedIsEquipped = selected?.name in equippedNames
    InventorySectionHeader("EQUIPMENT")
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
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.padding(horizontal = RealmsSpacing.l).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EquippedSlot(
            label = "AMULET",
            icon = "📿",
            item = amulet,
            onTap = { selected = amulet },
            modifier = Modifier.weight(1f)
        )
        EquippedSlot(
            label = "CLOTHES",
            icon = "👕",
            item = clothes,
            onTap = { selected = clothes },
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.padding(horizontal = RealmsSpacing.l).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EquippedSlot(
            label = "RING 1",
            icon = "💍",
            item = ring1,
            onTap = { selected = ring1 },
            modifier = Modifier.weight(1f)
        )
        EquippedSlot(
            label = "RING 2",
            icon = "💍",
            item = ring2,
            onTap = { selected = ring2 },
            modifier = Modifier.weight(1f)
        )
    }
    if (selectedIsEquipped) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = RealmsSpacing.l)) {
            SelectedItemCard(
                item = selected!!,
                onEquipToggle = {
                    val i = selected!!
                    onEquip(i); selected = i.copy(equipped = !i.equipped)
                },
                onUse = { val i = selected!!; onUse(i); selected = null }
            )
        }
    }
    // ---- Backpack grid (2 col; detail card appears inline under the tapped row) ----
    InventorySectionHeader("BACKPACK")
    val backpack = ch.inventory.filter { !(it.equipped && it.type in EQUIPPABLE_TYPES) }
    if (backpack.isEmpty()) {
        Text(
            "Pack is empty.",
            modifier = Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val cols = 2
    val rows = backpack.chunked(cols)
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = RealmsSpacing.l).heightIn(max = 520.dp)
    ) {
        rows.forEach { row ->
            row.forEach { bpItem ->
                item(key = "cell-${bpItem.name}") {
                    BackpackCell(
                        item = bpItem,
                        selected = selected?.name == bpItem.name,
                        onClick = { selected = bpItem }
                    )
                }
            }
            repeat(cols - row.size) { idx ->
                item(key = "pad-${row.first().name}-$idx") {}
            }
            val selInRow = selected != null && row.any { it.name == selected!!.name }
            if (selInRow) {
                item(
                    key = "detail-${selected!!.name}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    SelectedItemCard(
                        item = selected!!,
                        onEquipToggle = {
                            val i = selected!!
                            onEquip(i); selected = i.copy(equipped = !i.equipped)
                        },
                        onUse = { val i = selected!!; onUse(i); selected = null }
                    )
                }
            }
        }
    }
}

@Composable
internal fun InventoryPanel(state: GameUiState, onClose: () -> Unit, onEquip: (Item) -> Unit, onUse: (Item) -> Unit) {
    val ch = state.character
    PanelSheet(
        "\uD83C\uDF92  Inventory",
        subtitle = if (ch == null || ch.inventory.isEmpty()) null else "${ch.inventory.size} items",
        onClose = onClose
    ) {
        InventoryContent(state, onEquip, onUse)
    }
}

@Composable
private fun InventorySectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RealmsSpacing.s)
    )
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
        modifier = modifier.heightIn(min = 88.dp)
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
                Text(icon, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
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
        modifier = Modifier.fillMaxWidth()
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
            if (item.type in EQUIPPABLE_TYPES) {
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
    RealmsCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        outlined = true,
        accentColor = color,
        selected = selected,
        contentPadding = RealmsSpacing.m,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.type.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (item.qty > 1) {
                Text(
                    "×${item.qty}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(itemIconFor(item), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                val sub = when {
                    item.ac != null -> "AC ${item.ac}"
                    item.damage != null -> item.damage
                    item.equipped -> "equipped"
                    else -> null
                }
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
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
    "amulet" -> "\uD83D\uDCFF"
    "ring" -> "\uD83D\uDC8D"
    "clothes" -> "\uD83D\uDC55"
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
