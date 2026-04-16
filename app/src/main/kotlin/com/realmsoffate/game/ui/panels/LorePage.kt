@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.FilterTabRow
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.components.StatusTag
import com.realmsoffate.game.ui.components.WealthBars
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme
import com.realmsoffate.game.util.formatSigned

// ----------------- LORE (5 tabs: World / Factions / NPCs / History / Rumors) -----------------

private enum class LoreTab(val label: String, val icon: String) {
    World("World", "📜"),
    Factions("Factions", "⚔️"),
    Npcs("NPCs", "👤"),
    History("History", "📖"),
    Rumors("Rumors", "🗣️")
}

@Composable
internal fun LorePanel(state: GameUiState, onClose: () -> Unit) {
    PanelSheet("\uD83D\uDCDA  World Lore", onClose = onClose) {
        val lore = state.worldLore
        if (lore == null) {
            EmptyState("\uD83D\uDCDA", "The world's secrets are not yet recorded.")
            return@PanelSheet
        }
        var tab by remember { mutableStateOf(LoreTab.World) }
        val loreTabs = LoreTab.entries.map { it.label to it.icon }
        FilterTabRow(
            tabs = loreTabs,
            selectedIndex = LoreTab.entries.indexOf(tab),
            onSelect = { tab = LoreTab.entries[it] }
        )
        Spacer(Modifier.height(RealmsSpacing.xs))
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
        Modifier.padding(horizontal = RealmsSpacing.m).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            RealmsCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                SectionHeader("REALM")
                Text(worldName.ifBlank { "Unnamed" }, style = MaterialTheme.typography.titleLarge)
                Text(
                    lore.era.ifBlank { "Age of the Wanderer" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionHeader("WORLD CONDITIONS")
            lore.mutations.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
            Spacer(Modifier.height(RealmsSpacing.s))
            SectionHeader("POWERS AT PLAY")
            // FlowRow so faction chips wrap cleanly onto multiple lines instead of
            // squishing the last entry off-screen.
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = RealmsSpacing.xs).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s),
                verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
            ) {
                lore.factions.forEach { f ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            f.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xs),
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
                Spacer(Modifier.height(RealmsSpacing.s))
                SectionHeader("LIVING WORLD")
                Text(
                    "Whispers reaching you from beyond the immediate scene.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = RealmsSpacing.xs)
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
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().border(
            1.dp, realms.warning.copy(alpha = 0.35f), MaterialTheme.shapes.small
        )
    ) {
        Row(
            Modifier.padding(RealmsSpacing.m),
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
                Spacer(Modifier.height(RealmsSpacing.xxs))
                Text(ev.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LoreFactionsTab(state: GameUiState) {
    val lore = state.worldLore ?: return
    LazyColumn(
        Modifier.padding(horizontal = RealmsSpacing.m).heightIn(max = 560.dp),
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
            RealmsCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (f.status != "active") {
                        Spacer(Modifier.width(RealmsSpacing.s))
                        Surface(
                            color = when (f.status) {
                                "destroyed" -> MaterialTheme.colorScheme.error
                                "subjugated" -> realms.warning
                                "player_controlled" -> realms.success
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }.copy(alpha = 0.16f),
                            shape = MaterialTheme.shapes.extraSmall
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
                                modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
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
                Spacer(Modifier.height(RealmsSpacing.s))
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
                    Spacer(Modifier.height(RealmsSpacing.s))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(RealmsSpacing.m)) {
                            SectionHeader("GOVERNMENT")
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
                                Spacer(Modifier.height(RealmsSpacing.s))
                                SectionHeader("LINEAGE")
                                g.pastRulers.forEach { pr ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .padding(vertical = RealmsSpacing.xxs)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                        )
                                        Spacer(Modifier.width(RealmsSpacing.s))
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
                    Spacer(Modifier.height(RealmsSpacing.s))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(RealmsSpacing.m)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "ECONOMY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
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
                            Spacer(Modifier.height(RealmsSpacing.xs))
                            Surface(
                                color = realms.goldAccent.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    "\uD83D\uDCB0 Currency: ${f.currency}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = realms.goldAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
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
                    Spacer(Modifier.height(RealmsSpacing.s))
                    SectionHeader("MEMBERS")
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
        Modifier.padding(horizontal = RealmsSpacing.m).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
    ) {
        items(lore.npcs) { n ->
            val isDead = n.name.lowercase() in deadNpcNames
            RealmsCard(
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                        Spacer(Modifier.width(RealmsSpacing.s))
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "\u2620\uFE0F DEAD",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
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

@Composable
private fun LoreHistoryTab(state: GameUiState) {
    val lore = state.worldLore ?: return
    val realms = RealmsTheme.colors
    // Group by era in chronological order.
    val eraOrder = listOf("primordial", "ancient", "medieval", "dark_age", "recent")
    val grouped = lore.history.groupBy { it.era }
    LazyColumn(
        Modifier.padding(horizontal = RealmsSpacing.m).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
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
                Spacer(Modifier.height(RealmsSpacing.s))
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
    Column(Modifier.fillMaxWidth().padding(top = RealmsSpacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.4f)))
            Spacer(Modifier.width(RealmsSpacing.s))
            Text(
                overrideLabel ?: label,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = color
            )
            Spacer(Modifier.width(RealmsSpacing.s))
            Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.4f)))
        }
        if (overrideLabel == null && fromYear != toYear) {
            val olderMag = maxOf(-fromYear, -toYear)
            val newerMag = minOf(-fromYear, -toYear)
            Text(
                "$olderMag – $newerMag years ago",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = RealmsSpacing.xxs)
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
    Row(Modifier.fillMaxWidth().padding(vertical = RealmsSpacing.xxs)) {
        Column(Modifier.width(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Box(Modifier.width(2.dp).height(24.dp).background(color.copy(alpha = 0.3f)))
        }
        Spacer(Modifier.width(RealmsSpacing.s))
        Column(Modifier.weight(1f).padding(bottom = RealmsSpacing.xs)) {
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
        Modifier.padding(horizontal = RealmsSpacing.m).heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
    ) {
        items(rumors) { r ->
            RealmsCard(
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDDE3\uFE0F", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text(r, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
