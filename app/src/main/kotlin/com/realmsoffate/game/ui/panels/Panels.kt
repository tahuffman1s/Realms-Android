@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.Spells
import com.realmsoffate.game.ui.theme.RealmsTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelSheet(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(6.dp))
        content()
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun EmptyState(icon: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCap(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/** Compact pill-style filter tabs used by Quests/Journal panels. */
@Composable
private fun FilterTabs(
    tabs: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            val selected = i == selectedIndex
            Surface(
                onClick = { onSelect(i) },
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ----------------- INVENTORY (equipped slots + 5-col backpack grid) -----------------

@Composable
fun InventoryPanel(state: GameUiState, onClose: () -> Unit, onEquip: (Item) -> Unit) {
    val ch = state.character ?: return
    var selected by remember(ch) { mutableStateOf<Item?>(null) }
    PanelSheet(
        "\uD83C\uDF92  Inventory",
        subtitle = if (ch.inventory.isEmpty()) null else "${ch.inventory.size} items",
        onClose = onClose
    ) {
        // ---- Equipped slots (2 col) ----
        val weapon = ch.inventory.firstOrNull { it.equipped && it.type == "weapon" }
        val armor = ch.inventory.firstOrNull { it.equipped && (it.type == "armor" || it.type == "shield") }
        Row(
            Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
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
        SectionCap("  BACKPACK")
        val backpack = ch.inventory.filter { !(it.equipped && it.type in setOf("weapon", "armor", "shield")) }
        if (backpack.isEmpty()) {
            Text(
                "Pack is empty.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@PanelSheet
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp).heightIn(max = 380.dp)
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
    Surface(
        onClick = { if (item != null) onTap() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, rarityColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.padding(12.dp)) {
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
}

@Composable
private fun SelectedItemCard(item: Item, onEquipToggle: () -> Unit, onUse: () -> Unit) {
    val color = rarityColor(item.rarity)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth().border(
            1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(14.dp)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.2f)),
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
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (item.equipped) "Unequip" else "Equip")
                    }
                }
                if (item.type == "consumable") {
                    OutlinedButton(
                        onClick = onUse,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Use") }
                }
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
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.aspectRatio(0.85f).border(
            1.dp,
            if (selected) color else color.copy(alpha = 0.25f),
            RoundedCornerShape(10.dp)
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            if (item.qty > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
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
        shape = RoundedCornerShape(6.dp)
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
        shape = RoundedCornerShape(6.dp)
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

// ----------------- QUESTS -----------------

private enum class QuestFilter(val label: String, val icon: String, val status: String?) {
    Active("Active", "📜", "active"),
    Done("Done", "✅", "completed"),
    Failed("Failed", "❌", "failed"),
    All("All", "∗", null)
}

@Composable
fun QuestsPanel(state: GameUiState, onClose: () -> Unit, onAbandon: (String) -> Unit) {
    var filter by remember { mutableStateOf(QuestFilter.Active) }
    val filtered = state.quests.filter { filter.status == null || it.status == filter.status }
    PanelSheet(
        "\uD83D\uDCDC  Quests",
        subtitle = if (state.quests.isEmpty()) null else {
            val active = state.quests.count { it.status == "active" }
            val done = state.quests.count { it.status == "completed" }
            "$active active · $done completed"
        },
        onClose = onClose
    ) {
        if (state.quests.isEmpty()) {
            EmptyState("\uD83D\uDCDC", "No quests yet. The world waits.")
            return@PanelSheet
        }
        FilterTabs(
            tabs = QuestFilter.values().map { it.label to it.icon },
            selectedIndex = QuestFilter.values().indexOf(filter),
            onSelect = { filter = QuestFilter.values()[it] }
        )
        Spacer(Modifier.height(6.dp))
        if (filtered.isEmpty()) {
            Text(
                "No ${filter.label.lowercase()} quests.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@PanelSheet
        }
        LazyColumn(
            Modifier.padding(horizontal = 14.dp).heightIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered) { q ->
                val realms = RealmsTheme.colors
                val accent = when (q.status) {
                    "completed" -> realms.success
                    "failed" -> realms.fumbleRed
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().border(
                        1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(q.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusTag(q.status, accent)
                        }
                        Text(
                            "${q.type.uppercase()} · ${q.giver}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(q.desc, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        q.objectives.forEachIndexed { i, obj ->
                            val done = q.completed.getOrElse(i) { false }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (done) "\u2714" else "\u25CB",
                                    color = if (done) realms.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    obj,
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (q.reward.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = realms.goldAccent.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Reward: ${q.reward}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = realms.goldAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (q.status == "active") {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { onAbandon(q.id) }) {
                                Text("Abandon", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTag(status: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
        Text(
            status.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ----------------- PARTY -----------------

@Composable
fun PartyPanel(state: GameUiState, onClose: () -> Unit, onDismiss: (String) -> Unit) {
    PanelSheet(
        "\uD83D\uDC65  Party",
        subtitle = if (state.party.isEmpty()) null else "${state.party.size} companions",
        onClose = onClose
    ) {
        if (state.party.isEmpty()) {
            EmptyState("\uD83D\uDC64", "You travel alone.")
            return@PanelSheet
        }
        LazyColumn(
            Modifier.padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.party) { c ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(c.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text("${c.race} ${c.role} · L${c.level}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onDismiss(c.name) }) { Text("Dismiss") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("HP ${c.hp}/${c.maxHp}", style = MaterialTheme.typography.labelMedium, color = RealmsTheme.colors.success)
                        if (c.personality.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(c.personality, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ----------------- LORE (5 tabs: World / Factions / NPCs / History / Rumors) -----------------

private enum class LoreTab(val label: String, val icon: String) {
    World("World", "📜"),
    Factions("Factions", "⚔️"),
    Npcs("NPCs", "👤"),
    History("History", "📖"),
    Rumors("Rumors", "🗣️")
}

@Composable
fun LorePanel(state: GameUiState, onClose: () -> Unit) {
    PanelSheet("\uD83D\uDCDA  World Lore", onClose = onClose) {
        val lore = state.worldLore
        if (lore == null) {
            EmptyState("\uD83D\uDCDA", "The world's secrets are not yet recorded.")
            return@PanelSheet
        }
        var tab by remember { mutableStateOf(LoreTab.World) }
        ScrollableTabRow(
            selectedTabIndex = LoreTab.values().indexOf(tab),
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 12.dp
        ) {
            LoreTab.values().forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text("${t.icon} ${t.label}", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        when (tab) {
            LoreTab.World -> LoreWorldTab(state)
            LoreTab.Factions -> LoreFactionsTab(state)
            LoreTab.Npcs -> LoreNpcsTab(state)
            LoreTab.History -> LoreHistoryTab(state)
            LoreTab.Rumors -> LoreRumorsTab(state)
        }
    }
}

@Composable
private fun LoreWorldTab(state: GameUiState) {
    val lore = state.worldLore ?: return
    val worldName = lore.worldName.ifBlank { state.worldMap?.locations?.firstOrNull()?.name.orEmpty() }
    LazyColumn(
        Modifier.padding(horizontal = 14.dp).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("REALM", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(worldName.ifBlank { "Unnamed" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        lore.era.ifBlank { "Age of the Wanderer" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SectionCap("WORLD CONDITIONS")
            lore.mutations.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
            Spacer(Modifier.height(8.dp))
            SectionCap("POWERS AT PLAY")
            // FlowRow so faction chips wrap cleanly onto multiple lines instead of
            // squishing the last entry off-screen.
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lore.factions.forEach { f ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            f.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        // Living World — silent rumours of recent shifts. The narrator weaves
        // these into prose; the player checks here for the bigger picture.
        if (state.worldEvents.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionCap("LIVING WORLD")
                Text(
                    "Whispers reaching you from beyond the immediate scene.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(state.worldEvents.takeLast(8).reversed()) { ev ->
                LivingWorldRow(ev)
            }
        }
    }
}

@Composable
private fun LivingWorldRow(ev: com.realmsoffate.game.data.WorldEvent) {
    val realms = RealmsTheme.colors
    Surface(
        color = realms.warning.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp, realms.warning.copy(alpha = 0.35f), RoundedCornerShape(12.dp)
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(ev.icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ev.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = realms.warning,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "T${ev.turn}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(ev.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LoreFactionsTab(state: GameUiState) {
    val lore = state.worldLore ?: return
    LazyColumn(
        Modifier.padding(horizontal = 14.dp).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(lore.factions) { f ->
            val rep = state.factionRep[f.name] ?: 0
            val realms = RealmsTheme.colors
            val repColor = when {
                rep >= 50 -> realms.success
                rep <= -50 -> realms.fumbleRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (f.status != "active") {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = when (f.status) {
                                    "destroyed" -> MaterialTheme.colorScheme.error
                                    "subjugated" -> realms.warning
                                    "player_controlled" -> realms.success
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    f.status.uppercase().replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (f.status) {
                                        "destroyed" -> MaterialTheme.colorScheme.error
                                        "subjugated" -> realms.warning
                                        "player_controlled" -> realms.success
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        StatusTag("REP ${formatSigned(rep)}", repColor)
                    }
                    Text(
                        "${f.type.replaceFirstChar { it.uppercase() }} · seat: ${f.baseLoc}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(f.description, style = MaterialTheme.typography.bodySmall)
                    if (f.population.isNotBlank()) {
                        Text(
                            "Population: ${f.population} · Mood: ${f.mood} · ${f.disposition}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (f.goal.isNotBlank()) {
                        Text(
                            "Goal: ${f.goal}",
                            style = MaterialTheme.typography.labelMedium,
                            color = realms.info
                        )
                    }
                    // ---- Government block ----
                    f.government?.let { g ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("GOVERNMENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    g.form.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val isDeceased = g.ruler.contains("Deceased", ignoreCase = true)
                                Text(
                                    "Ruler: ${g.ruler}" + if (g.rulerTrait.isNotBlank() && !isDeceased) " (${g.rulerTrait})" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDeceased) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                if (g.yearsInPower > 0) {
                                    Text(
                                        "In power: ${g.yearsInPower} years · Succession: ${g.succession}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (g.dynasty.isNotBlank() && g.dynasty != "None") {
                                    Text("Dynasty: ${g.dynasty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (g.pastRulers.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("LINEAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    g.pastRulers.forEach { pr ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier
                                                    .padding(vertical = 2.dp)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "${pr.yearsAgo}y ago — ${pr.name} · ${pr.fate}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ---- Economy block with 5-bar wealth ----
                    f.economy?.let { e ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("ECONOMY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    WealthBars(wealth = e.wealth)
                                }
                                Text(
                                    e.level,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (e.description.isNotBlank()) {
                                    Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    color = realms.goldAccent.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "\uD83D\uDCB0 Currency: ${f.currency}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = realms.goldAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (e.exports.isNotEmpty()) {
                                    Text("Exports: ${e.exports.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (e.imports.isNotEmpty()) {
                                    Text("Imports: ${e.imports.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (e.tax.isNotBlank()) {
                                    Text("Tax: ${e.tax}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    // Member NPCs
                    val members = state.worldLore?.npcs.orEmpty().filter { it.faction == f.name }
                    if (members.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("MEMBERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        members.forEach { m ->
                            Text(
                                "· ${m.name} — ${m.role} (${m.race})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WealthBars(wealth: Int) {
    val realms = RealmsTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .width(14.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i < wealth) realms.goldAccent
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
            )
        }
    }
}

@Composable
private fun LoreNpcsTab(state: GameUiState) {
    val lore = state.worldLore ?: return
    if (lore.npcs.isEmpty()) {
        EmptyState("\uD83D\uDC64", "No NPCs recorded.")
        return
    }
    // Cross-reference met-NPC journal to surface dead status on lore NPCs.
    val deadNpcNames = state.npcLog
        .filter { it.status == "dead" }
        .map { it.name.lowercase() }
        .toSet()

    LazyColumn(
        Modifier.padding(horizontal = 14.dp).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(lore.npcs) { n ->
            val isDead = n.name.lowercase() in deadNpcNames
            Surface(
                color = if (isDead)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            n.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isDead)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isDead) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "\u2620\uFE0F DEAD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "${n.race} ${n.role} · ${n.location}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (isDead) 0.45f else 1f
                        )
                    )
                    if (n.appearance.isNotBlank()) Text(
                        n.appearance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (isDead) 0.4f else 1f
                        )
                    )
                    if (n.personality.isNotBlank()) Text(
                        "— ${n.personality}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (isDead) 0.4f else 1f
                        )
                    )
                    n.faction?.let {
                        Text(
                            "\u25B8 $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(
                                alpha = if (isDead) 0.4f else 1f
                            ),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoreHistoryTab(state: GameUiState) {
    val lore = state.worldLore ?: return
    val realms = RealmsTheme.colors
    // Group by era in chronological order.
    val eraOrder = listOf("primordial", "ancient", "medieval", "dark_age", "recent")
    val grouped = lore.history.groupBy { it.era }
    LazyColumn(
        Modifier.padding(horizontal = 14.dp).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        eraOrder.forEach { era ->
            val entries = grouped[era].orEmpty()
            if (entries.isNotEmpty()) {
                item { EraHeader(era, entries.minOf { it.year }, entries.maxOf { it.year }) }
                items(entries) { entry -> HistoryRow(entry) }
            }
        }
        if (state.worldEvents.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                EraHeader("recent_live", 0, 0, overrideLabel = "— LIVING WORLD —")
            }
            items(state.worldEvents) {
                HistoryRow(
                    entry = com.realmsoffate.game.data.HistoryEntry(
                        era = "live",
                        year = it.turn,
                        text = "${it.icon} ${it.title} — ${it.text}"
                    ),
                    labelOverride = "Turn ${it.turn}"
                )
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "— PRESENT DAY —",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp),
                    color = realms.goldAccent
                )
            }
        }
    }
}

@Composable
private fun EraHeader(era: String, fromYear: Int, toYear: Int, overrideLabel: String? = null) {
    val realms = RealmsTheme.colors
    val (label, color) = when (era) {
        "primordial" -> "— PRIMORDIAL AGE —" to MaterialTheme.colorScheme.secondary
        "ancient" -> "— ANCIENT AGE —" to realms.info
        "medieval" -> "— MEDIEVAL AGE —" to MaterialTheme.colorScheme.primary
        "dark_age" -> "— DARK AGE —" to realms.fumbleRed
        "recent" -> "— RECENT HISTORY —" to realms.goldAccent
        else -> (overrideLabel ?: era.uppercase()) to realms.warning
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.4f)))
            Spacer(Modifier.width(8.dp))
            Text(
                overrideLabel ?: label,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = color
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.4f)))
        }
        if (overrideLabel == null && fromYear != toYear) {
            val olderMag = maxOf(-fromYear, -toYear)
            val newerMag = minOf(-fromYear, -toYear)
            Text(
                "$olderMag – $newerMag years ago",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: com.realmsoffate.game.data.HistoryEntry,
    labelOverride: String? = null
) {
    val realms = RealmsTheme.colors
    val color = when (entry.era) {
        "primordial" -> MaterialTheme.colorScheme.secondary
        "ancient" -> realms.info
        "medieval" -> MaterialTheme.colorScheme.primary
        "dark_age" -> realms.fumbleRed
        "recent" -> realms.goldAccent
        else -> realms.warning
    }
    val yearsAgoLabel = labelOverride ?: when {
        entry.year < 0 -> "${-entry.year} yrs ago"
        entry.year == 0 -> "Today"
        else -> "Year ${entry.year}"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.width(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Box(Modifier.width(2.dp).height(24.dp).background(color.copy(alpha = 0.3f)))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
            Text(yearsAgoLabel, style = MaterialTheme.typography.labelSmall, color = color)
            com.realmsoffate.game.util.NarrationMarkdown(
                text = entry.text,
                baseStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LoreRumorsTab(state: GameUiState) {
    val rumors = state.worldLore?.rumors.orEmpty()
    if (rumors.isEmpty()) {
        EmptyState("\uD83D\uDDE3\uFE0F", "No rumors yet. Taverns are quiet.")
        return
    }
    LazyColumn(
        Modifier.padding(horizontal = 14.dp).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rumors) { r ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDDE3\uFE0F", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text(r, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ----------------- JOURNAL -----------------

private enum class JournalFilter(val label: String, val icon: String, val match: (String) -> Boolean) {
    All("All", "∗", { true }),
    Friendly("Friendly", "🟢", { r ->
        listOf("friendly", "ally", "love", "grateful", "allied", "romantic", "warm", "helpful", "loyal", "trusted").any { r.contains(it) }
    }),
    Hostile("Hostile", "🔴", { r ->
        listOf("hostile", "enemy", "rival", "feared", "angry", "aggressive", "hateful", "suspicious", "distrustful").any { r.contains(it) }
    }),
    Neutral("Neutral", "🟣", { r ->
        val friendly = listOf("friendly", "ally", "love", "grateful", "allied", "romantic", "warm", "helpful", "loyal", "trusted")
        val hostile = listOf("hostile", "enemy", "rival", "feared", "angry", "aggressive", "hateful", "suspicious", "distrustful")
        !friendly.any { r.contains(it) } && !hostile.any { r.contains(it) }
    })
}

@Composable
fun JournalPanel(state: GameUiState, focusNpc: String? = null, onClose: () -> Unit) {
    var filter by remember { mutableStateOf(JournalFilter.All) }
    var selected by remember { mutableStateOf<String?>(focusNpc) }
    val filtered = state.npcLog.filter { filter.match(it.relationship.lowercase()) }
    PanelSheet(
        "\uD83D\uDCD6  Journal",
        subtitle = if (state.npcLog.isEmpty()) null else "${state.npcLog.size} NPCs met",
        onClose = onClose
    ) {
        if (state.npcLog.isEmpty()) {
            EmptyState("\uD83D\uDCD6", "No NPCs met yet.")
            return@PanelSheet
        }
        // ---- Player header card ----
        state.character?.let { ch ->
            Row(
                Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                ch.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "L${ch.level} ${ch.race} ${ch.cls} (you)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        FilterTabs(
            tabs = JournalFilter.entries.map { it.label to it.icon },
            selectedIndex = JournalFilter.entries.indexOf(filter),
            onSelect = { filter = JournalFilter.entries[it] }
        )
        Spacer(Modifier.height(6.dp))
        if (filtered.isEmpty()) {
            Text(
                "No ${filter.label.lowercase()} NPCs in journal.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@PanelSheet
        }

        // Selected NPC detail (appears inline above the list when one is picked).
        selected?.let { sel ->
            val npc = state.npcLog.firstOrNull { it.name == sel }
            if (npc != null) {
                NpcDetailCard(npc = npc, onClose = { selected = null })
                Spacer(Modifier.height(8.dp))
            }
        }

        LazyColumn(
            Modifier.padding(horizontal = 14.dp).heightIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { n ->
                Surface(
                    onClick = { selected = if (selected == n.name) null else n.name },
                    color = if (selected == n.name)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(n.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.name, style = MaterialTheme.typography.titleMedium)
                                Text("${n.race} ${n.role} · ${n.age}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RelationshipTag(n.relationship)
                            if (n.status == "dead") {
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "☠️ DEAD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (n.personality.isNotBlank()) Text(
                            n.personality,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            "Last seen T${n.lastSeenTurn} · ${n.lastLocation}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NpcDetailCard(
    npc: com.realmsoffate.game.data.LogNpc,
    onClose: () -> Unit
) {
    val realms = RealmsTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth().border(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            if (npc.status == "dead") {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("☠️", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DECEASED",
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        npc.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(npc.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${npc.race} ${npc.role} · ${npc.age}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (npc.id.isNotBlank()) {
                        Text(
                            "#${npc.id}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
            if (npc.personality.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = realms.info.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "\u201C${npc.personality}\u201D",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = realms.info,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (npc.appearance.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("APPEARANCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                val traits = extractTraitChips(npc.appearance)
                if (traits.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        traits.forEach { t -> TraitChip(t) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(npc.appearance, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    "Relationship: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    npc.relationship,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "Last seen T${npc.lastSeenTurn} at ${npc.lastLocation}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (npc.thoughts.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("YOUR ASSESSMENT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    "\u201C${npc.thoughts}\u201D",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                )
            }
            if (npc.memorableQuotes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "MEMORABLE LINES",
                        style = MaterialTheme.typography.labelLarge,
                        color = realms.goldAccent
                    )
                }
                Spacer(Modifier.height(4.dp))
                npc.memorableQuotes.forEach { line ->
                    DialogueLine(line, accent = realms.goldAccent)
                }
            }
            if (npc.dialogueHistory.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("RECENT DIALOGUE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                npc.dialogueHistory.takeLast(6).forEach { line ->
                    DialogueLine(line)
                }
            }
        }
    }
}

@Composable
private fun DialogueLine(
    raw: String,
    accent: androidx.compose.ui.graphics.Color? = null
) {
    // Entries are stored as "T12: \"the dialogue\"" — parse gently.
    val turnSep = raw.indexOf(':')
    val turnLabel = if (turnSep > 0 && raw.startsWith("T")) raw.substring(0, turnSep) else ""
    val body = if (turnSep > 0) raw.substring(turnSep + 1).trim() else raw
    val realms = RealmsTheme.colors
    val barColor = accent ?: MaterialTheme.colorScheme.primary
    Row(Modifier.padding(vertical = 2.dp)) {
        if (turnLabel.isNotBlank()) {
            Text(
                turnLabel,
                style = MaterialTheme.typography.labelSmall,
                color = realms.goldAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp)
            )
        }
        Box(
            Modifier
                .width(if (accent != null) 3.dp else 2.dp)
                .heightIn(min = 16.dp)
                .background(barColor.copy(alpha = if (accent != null) 0.8f else 0.4f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TraitChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/**
 * Scans an appearance description for obvious traits and returns short labels
 * for chip rendering. "Tall, gaunt, scarred, armored" etc.
 */
private fun extractTraitChips(text: String): List<String> {
    val lc = text.lowercase()
    val hits = mutableListOf<String>()
    val map = listOf(
        "tall" to "Tall",
        "short" to "Short",
        "stocky" to "Stocky",
        "gaunt" to "Gaunt",
        "slender" to "Slender",
        "muscular" to "Muscular",
        "scarred" to "Scarred",
        "hooded" to "Hooded",
        "cloaked" to "Cloaked",
        "robed" to "Robed",
        "armored" to "Armored",
        "armoured" to "Armoured",
        "bearded" to "Bearded",
        "tattoo" to "Tattooed",
        "one-eyed" to "One-Eyed",
        "missing" to "Missing ‹digit›",
        "pale" to "Pale",
        "dark-skinned" to "Dark-skinned",
        "soot" to "Sooty",
        "burnt" to "Burn-scar",
        "noble" to "Noble bearing",
        "weathered" to "Weathered",
        "young" to "Young",
        "ancient" to "Ancient",
        "elderly" to "Elderly"
    )
    map.forEach { (kw, label) -> if (kw in lc) hits += label }
    return hits.distinct().take(6)
}

@Composable
private fun RelationshipTag(relationship: String) {
    val realms = RealmsTheme.colors
    val color = when (relationship.lowercase()) {
        "friendly", "ally", "love" -> realms.success
        "hostile", "enemy" -> realms.fumbleRed
        "suspicious", "wary" -> realms.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
        Text(
            relationship.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ----------------- CURRENCY (exchange + rate table) -----------------

@Composable
fun CurrencyPanel(
    state: GameUiState,
    onClose: () -> Unit,
    onExchange: (factionName: String, direction: String, amount: Int) -> Unit = { _, _, _ -> }
) {
    val ch = state.character ?: return
    val realms = RealmsTheme.colors
    val factions = state.worldLore?.factions.orEmpty()
    val localFactionName = state.worldLore?.let { lore ->
        state.worldMap?.let { wm ->
            com.realmsoffate.game.game.LoreGen.findLocalFaction(wm, lore, state.currentLoc)?.name
        }
    }
    var exchangeTarget by remember(ch) { mutableStateOf<String?>(null) }

    // Total wealth estimate in gold equivalent.
    val wealthGold = ch.gold + ch.currencyBalances.entries.sumOf { (currency, amt) ->
        val faction = factions.firstOrNull { it.currency == currency }
        val w = faction?.economy?.wealth ?: 3
        val rate = (0.6 + 0.2 * (w - 3)).coerceIn(0.3, 1.6)
        (amt / rate).toInt()
    }

    PanelSheet(
        "\uD83D\uDCB0  Coin",
        subtitle = localFactionName?.let { "Local: $it" },
        onClose = onClose
    ) {
        LazyColumn(
            Modifier.padding(horizontal = 14.dp).heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Wealth header card.
            item {
                Surface(
                    color = realms.goldAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\uD83D\uDCB0", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("GOLD ON HAND", style = MaterialTheme.typography.labelMedium, color = realms.goldAccent)
                                Text(
                                    "${ch.gold}",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = realms.goldAccent,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Total wealth (gold-eq): $wealthGold",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Per-faction currency cards with exchange affordance.
            item { SectionCap("LOCAL CURRENCIES") }
            items(factions) { f ->
                val balance = ch.currencyBalances[f.currency] ?: 0
                val wealth = f.economy?.wealth ?: 3
                val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
                val rep = state.factionRep[f.name] ?: 0
                CurrencyFactionCard(
                    name = f.name,
                    currency = f.currency,
                    balance = balance,
                    wealth = wealth,
                    rate = rate,
                    repColor = when {
                        rep >= 50 -> realms.success
                        rep <= -50 -> realms.fumbleRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    rep = rep,
                    isLocal = (f.name == localFactionName),
                    onExchange = { exchangeTarget = f.name }
                )
            }

            // Exchange UI for the chosen faction.
            exchangeTarget?.let { t ->
                val f = factions.firstOrNull { it.name == t }
                if (f != null) {
                    item {
                        ExchangeCard(
                            faction = f,
                            character = ch,
                            onCommit = { direction, amount ->
                                onExchange(t, direction, amount)
                                exchangeTarget = null
                            },
                            onCancel = { exchangeTarget = null }
                        )
                    }
                }
            }

            // Rate table at the bottom.
            item {
                SectionCap("EXCHANGE RATES")
                RatesTable(factions)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Rates follow economy strength: richer markets = more favourable swaps. Shops trade in local currency; higher reputation means better prices.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CurrencyFactionCard(
    name: String,
    currency: String,
    balance: Int,
    wealth: Int,
    rate: Double,
    repColor: Color,
    rep: Int,
    isLocal: Boolean,
    onExchange: () -> Unit
) {
    val realms = RealmsTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp,
            if (isLocal) realms.goldAccent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(14.dp)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (isLocal) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = realms.goldAccent.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "LOCAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = realms.goldAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(currency, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusTag("REP ${formatSigned(rep)}", repColor)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Balance: $balance $currency",
                        style = MaterialTheme.typography.labelMedium,
                        color = realms.goldAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Rate: 1 gold = ${"%.2f".format(rate)} $currency",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                WealthBars(wealth = wealth)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExchange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Exchange", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun ExchangeCard(
    faction: com.realmsoffate.game.data.Faction,
    character: com.realmsoffate.game.data.Character,
    onCommit: (direction: String, amount: Int) -> Unit,
    onCancel: () -> Unit
) {
    val realms = RealmsTheme.colors
    val wealth = faction.economy?.wealth ?: 3
    val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
    var direction by remember { mutableStateOf("to") } // to = gold → local, from = local → gold
    var amountText by remember { mutableStateOf("10") }
    val amount = amountText.toIntOrNull() ?: 0
    val preview = if (direction == "to") (amount * rate).toInt() else (amount / rate).toInt()
    val currentLocal = character.currencyBalances[faction.currency] ?: 0
    val canAfford = if (direction == "to") character.gold >= amount else currentLocal >= amount

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("EXCHANGE WITH ${faction.name.uppercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            // Direction toggle
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = direction == "to",
                    onClick = { direction = "to" },
                    label = { Text("Gold → ${faction.currency}", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = direction == "from",
                    onClick = { direction = "from" },
                    label = { Text("${faction.currency} → Gold", style = MaterialTheme.typography.labelMedium) }
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text(if (direction == "to") "Gold to spend" else "${faction.currency} to spend") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = realms.goldAccent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "You get: $preview ${if (direction == "to") faction.currency else "gold"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = realms.goldAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Rate: 1 gold = ${"%.2f".format(rate)} ${faction.currency}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onCommit(direction, amount) },
                    enabled = amount > 0 && canAfford,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Exchange") }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Cancel") }
            }
            if (!canAfford && amount > 0) {
                Text(
                    "Insufficient funds.",
                    style = MaterialTheme.typography.labelSmall,
                    color = realms.fumbleRed,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RatesTable(factions: List<com.realmsoffate.game.data.Faction>) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row {
                Text("Currency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("Econ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(70.dp))
                Text("Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(70.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            factions.forEach { f ->
                val wealth = f.economy?.wealth ?: 3
                val rate = (0.6 + 0.2 * (wealth - 3)).coerceIn(0.3, 1.6)
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        f.currency,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        f.economy?.level.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(70.dp),
                        maxLines = 1
                    )
                    Text(
                        "${"%.2f".format(rate)}×",
                        style = MaterialTheme.typography.bodySmall,
                        color = RealmsTheme.colors.goldAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(70.dp)
                    )
                }
            }
        }
    }
}

// ----------------- SPELLS (slot diamonds + grouped grid + detail) -----------------

private val spellLevelLabels = mapOf(
    0 to "CANTRIPS & ABILITIES",
    1 to "1ST LEVEL",
    2 to "2ND LEVEL",
    3 to "3RD LEVEL",
    4 to "4TH LEVEL",
    5 to "5TH LEVEL"
)

@Composable
fun SpellsPanel(
    state: GameUiState,
    onClose: () -> Unit,
    onHotbar: (Int, String?) -> Unit = { _, _ -> },
    onCast: (spell: com.realmsoffate.game.game.Spell) -> Unit = {}
) {
    val ch = state.character ?: return
    var selectedName by remember(ch) { mutableStateOf<String?>(null) }
    PanelSheet(
        "\u2728  Spells",
        subtitle = if (ch.knownSpells.isEmpty()) null else "${ch.knownSpells.size} known",
        onClose = onClose
    ) {
        if (ch.knownSpells.isEmpty()) {
            EmptyState("\u2728", "You know no spells.")
            return@PanelSheet
        }
        // Slot diamonds header
        SpellSlotStrip(ch)
        Spacer(Modifier.height(6.dp))

        val knownSpells = ch.knownSpells.mapNotNull { Spells.find(it) }.sortedBy { it.level }
        val grouped = knownSpells.groupBy { it.level }

        LazyColumn(
            Modifier.padding(horizontal = 14.dp).heightIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            grouped.keys.sorted().forEach { lvl ->
                val entries = grouped[lvl].orEmpty()
                item {
                    Text(
                        spellLevelLabels[lvl] ?: "LEVEL $lvl",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                // Render pairs of spells as rows of two — emulating the web's 2-col grid
                // without pulling in another nested LazyVerticalGrid.
                val pairs = entries.chunked(2)
                items(pairs) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { spell ->
                            SpellCard(
                                spell = spell,
                                selected = selectedName == spell.name,
                                onClick = { selectedName = if (selectedName == spell.name) null else spell.name },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            selectedName?.let { name ->
                val sp = Spells.find(name)
                if (sp != null) {
                    item {
                        SpellDetailCard(
                            spell = sp,
                            canCast = sp.level == 0 || (ch.spellSlots[sp.level] ?: 0) > 0,
                            onCast = { onCast(sp) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpellSlotStrip(ch: com.realmsoffate.game.data.Character) {
    val realms = RealmsTheme.colors
    val levels = ch.maxSpellSlots.keys.sorted()
    if (levels.isEmpty()) return
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        levels.forEach { lvl ->
            val max = ch.maxSpellSlots[lvl] ?: 0
            val cur = ch.spellSlots[lvl] ?: 0
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "L$lvl",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(max) { i ->
                        SlotDiamond(filled = i < cur, color = realms.info)
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotDiamond(filled: Boolean, color: Color) {
    val size = 10.dp
    Box(
        Modifier
            .size(size)
            .rotate(45f)
            .background(
                if (filled) color else Color.Transparent,
                RoundedCornerShape(1.dp)
            )
            .border(1.dp, color, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun SpellCard(
    spell: com.realmsoffate.game.game.Spell,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.dp, border, RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(spell.icon, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    spell.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                Text(
                    "${spell.school}${if (spell.level > 0) " · L${spell.level}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SpellDetailCard(
    spell: com.realmsoffate.game.game.Spell,
    canCast: Boolean,
    onCast: () -> Unit
) {
    val realms = RealmsTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(spell.icon, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(spell.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${spell.school} · ${if (spell.level == 0) "Cantrip" else "Level ${spell.level}"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (spell.classes.isNotEmpty()) {
                        Text(
                            spell.classes.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(spell.desc, style = MaterialTheme.typography.bodyMedium)
            if (spell.damage != "-") {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = realms.fumbleRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "\uD83D\uDDE1\uFE0F ${spell.damage}",
                        style = MaterialTheme.typography.labelMedium,
                        color = realms.fumbleRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onCast,
                enabled = canCast,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) { Text(if (canCast) "Cast Now" else "No Slots") }
        }
    }
}

// ----------------- STATS -----------------

@Composable
fun StatsPanel(state: GameUiState, onClose: () -> Unit) {
    val ch = state.character ?: return
    val realms = RealmsTheme.colors
    PanelSheet("\uD83D\uDCCA  Character", onClose = onClose) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(ch.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "L${ch.level} ${ch.race} ${ch.cls}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(label = "HP", value = "${ch.hp}/${ch.maxHp}", color = realms.success, modifier = Modifier.weight(1f))
                StatTile(label = "AC", value = "${ch.ac}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                StatTile(label = "PROF", value = "+${ch.proficiency}", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                StatTile(label = "XP", value = "${ch.xp}", color = realms.goldAccent, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            SectionCap("ABILITIES")
            Spacer(Modifier.height(4.dp))
            // 3-column grid with icons, matching the web's layout.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AbilityTile("STR", "Strength",     "\uD83D\uDCAA", ch.abilities.str, ch.abilities.strMod, Modifier.weight(1f))
                AbilityTile("DEX", "Dexterity",    "\uD83C\uDFC3", ch.abilities.dex, ch.abilities.dexMod, Modifier.weight(1f))
                AbilityTile("CON", "Constitution", "\u2764\uFE0F", ch.abilities.con, ch.abilities.conMod, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AbilityTile("INT", "Intelligence", "\uD83E\uDDE0", ch.abilities.int, ch.abilities.intMod, Modifier.weight(1f))
                AbilityTile("WIS", "Wisdom",       "\uD83D\uDC41\uFE0F", ch.abilities.wis, ch.abilities.wisMod, Modifier.weight(1f))
                AbilityTile("CHA", "Charisma",     "\uD83D\uDCE3", ch.abilities.cha, ch.abilities.chaMod, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            SectionCap("MORALITY")
            val mcolor = when {
                state.morality >= 30 -> realms.success
                state.morality <= -30 -> realms.fumbleRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val pct = (state.morality + 100) / 200f
            Text(
                "${formatSigned(state.morality)}",
                style = MaterialTheme.typography.titleLarge,
                color = mcolor,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                color = mcolor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (ch.conditions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionCap("CONDITIONS")
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ch.conditions.forEach { cond ->
                        Surface(
                            color = realms.warning.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, realms.warning.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                cond,
                                style = MaterialTheme.typography.labelMedium,
                                color = realms.warning,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            ch.backstory?.let { bs ->
                Spacer(Modifier.height(14.dp))
                SectionCap("BACKSTORY")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackstoryCard("\uD83C\uDF05", "Origin", bs.origin, realms.info)
                    BackstoryCard("\uD83C\uDFAF", "Motivation", bs.motivation, realms.goldAccent)
                    BackstoryCard("\uD83D\uDC94", "Flaw", bs.flaw, realms.fumbleRed)
                    BackstoryCard("\uD83D\uDD17", "Bond", bs.bond, realms.success)
                    BackstoryCard("\uD83D\uDD73\uFE0F", "Dark Secret", bs.darkSecret, MaterialTheme.colorScheme.secondary)
                    BackstoryCard("\uD83D\uDD0D", "Lost Item", bs.lostItem, realms.goldAccent)
                    BackstoryCard("\u2620\uFE0F", "Personal Enemy", bs.personalEnemy, realms.fumbleRed)
                    bs.prophecy?.let { p ->
                        if (p.isNotBlank()) BackstoryCard("\uD83D\uDD2E", "Prophecy", p, MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BackstoryCard(icon: String, label: String, text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AbilityTile(
    abbr: String,
    full: String,
    icon: String,
    score: Int,
    mod: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(abbr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                formatSigned(mod),
                style = MaterialTheme.typography.labelMedium,
                color = if (mod >= 0) RealmsTheme.colors.success else MaterialTheme.colorScheme.error
            )
            Text(
                full,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun formatSigned(n: Int) = if (n >= 0) "+$n" else n.toString()
