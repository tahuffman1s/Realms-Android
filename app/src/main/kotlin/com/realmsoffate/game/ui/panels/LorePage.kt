@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.components.StatusTag
import com.realmsoffate.game.ui.components.WealthBars
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.util.formatSigned

// ----------------- LORE (collapsible sections: World / Factions / NPCs / History / Rumors) -----------------

@Composable
internal fun LoreContent(state: GameUiState) {
    val lore = state.worldLore
    if (lore == null) {
        EmptyState("\uD83D\uDCDA", "The world's secrets are not yet recorded.")
        return
    }
    var expanded by remember { mutableStateOf(setOf("World")) }
    val toggle: (String) -> Unit = { key ->
        expanded = if (key in expanded) expanded - key else expanded + key
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = RealmsSpacing.m),
        verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s),
        contentPadding = PaddingValues(vertical = RealmsSpacing.s)
    ) {
        collapsibleLoreSection("World", "\uD83C\uDF0D  World", "World" in expanded, { toggle("World") }) {
            loreWorldItems(state)
        }
        collapsibleLoreSection("Factions", "\u2694\uFE0F  Factions", "Factions" in expanded, { toggle("Factions") }) {
            loreFactionsItems(state)
        }
        collapsibleLoreSection("NPCs", "\uD83D\uDC64  NPCs", "NPCs" in expanded, { toggle("NPCs") }) {
            loreNpcsItems(state)
        }
        collapsibleLoreSection("History", "\uD83D\uDCDC  History", "History" in expanded, { toggle("History") }) {
            loreHistoryItems(state)
        }
        collapsibleLoreSection("Rumors", "\uD83D\uDDE3\uFE0F  Rumors", "Rumors" in expanded, { toggle("Rumors") }) {
            loreRumorsItems(state)
        }
    }
}

private fun LazyListScope.collapsibleLoreSection(
    key: String,
    label: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    item(key = "header_$key") {
        CollapsibleLoreHeader(label, isExpanded, onToggle)
    }
    if (isExpanded) {
        content()
        item(key = "footer_$key") {
            Spacer(Modifier.height(RealmsSpacing.xs))
        }
    }
}

@Composable
private fun CollapsibleLoreHeader(label: String, isExpanded: Boolean, onToggle: () -> Unit) {
    Surface(
        color = if (isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = RealmsSpacing.m, vertical = RealmsSpacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isExpanded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse $label" else "Expand $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun LorePanel(state: GameUiState, onClose: () -> Unit) {
    PanelSheet("\uD83D\uDCDA  World Lore", onClose = onClose) {
        LoreContent(state)
    }
}

private fun LazyListScope.loreWorldItems(state: GameUiState) {
    val lore = state.worldLore ?: return
    val worldName = lore.worldName.ifBlank { state.worldMap?.locations?.firstOrNull()?.name.orEmpty() }
    item(key = "world_realm") {
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
    item(key = "world_conditions") {
        Column {
            SectionHeader("WORLD CONDITIONS")
            lore.mutations.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
            Spacer(Modifier.height(RealmsSpacing.s))
            SectionHeader("POWERS AT PLAY")
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
    }
    if (state.worldEvents.isNotEmpty()) {
        item(key = "world_living_header") {
            Column {
                Spacer(Modifier.height(RealmsSpacing.s))
                SectionHeader("LIVING WORLD")
                Text(
                    "Whispers reaching you from beyond the immediate scene.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = RealmsSpacing.xs)
                )
            }
        }
        items(
            items = state.worldEvents.takeLast(8).reversed(),
            key = { ev -> "world_event_${ev.turn}_${ev.title}" }
        ) { ev ->
            LivingWorldRow(ev)
        }
    }
}

@Composable
private fun LivingWorldRow(ev: com.realmsoffate.game.data.WorldEvent) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().border(
            1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.small
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
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
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

private fun LazyListScope.loreFactionsItems(state: GameUiState) {
    val lore = state.worldLore ?: return
    if (lore.factions.isEmpty()) {
        item(key = "factions_empty") { EmptyState("\u2694\uFE0F", "No factions recorded.") }
        return
    }
    items(
        items = lore.factions,
        key = { f -> "faction_${f.name}" }
    ) { f ->
            val rep = state.factionRep[f.name] ?: 0
            val repColor = when {
                rep >= 50 -> MaterialTheme.colorScheme.primary
                rep <= -50 -> MaterialTheme.colorScheme.error
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
                                "destroyed" -> MaterialTheme.colorScheme.errorContainer
                                "subjugated" -> MaterialTheme.colorScheme.tertiaryContainer
                                "player_controlled" -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                f.status.uppercase().replace("_", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (f.status) {
                                    "destroyed" -> MaterialTheme.colorScheme.error
                                    "subjugated" -> MaterialTheme.colorScheme.tertiary
                                    "player_controlled" -> MaterialTheme.colorScheme.primary
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
                        color = MaterialTheme.colorScheme.secondary
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
                                                .background(MaterialTheme.colorScheme.primary)
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

private fun LazyListScope.loreNpcsItems(state: GameUiState) {
    val lore = state.worldLore ?: return
    if (lore.npcs.isEmpty()) {
        item(key = "npcs_empty") { EmptyState("\uD83D\uDC64", "No NPCs recorded.") }
        return
    }
    val deadNpcNames = state.npcLog
        .filter { it.status == "dead" }
        .map { it.name.lowercase() }
        .toSet()
    items(
        items = lore.npcs,
        key = { n -> "npc_${n.name}" }
    ) { n ->
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
                            MaterialTheme.colorScheme.outline
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isDead) {
                        Spacer(Modifier.width(RealmsSpacing.s))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "\u2620\uFE0F DEAD",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
                            )
                        }
                    }
                }
                Text(
                    "${n.race} ${n.role} · ${n.location}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (n.appearance.isNotBlank()) Text(
                    n.appearance,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
                if (n.personality.isNotBlank()) Text(
                    "— ${n.personality}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                )
                n.faction?.let {
                    Text(
                        "\u25B8 $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

}

private fun LazyListScope.loreHistoryItems(state: GameUiState) {
    val lore = state.worldLore ?: return
    val eraOrder = listOf("primordial", "ancient", "medieval", "dark_age", "recent")
    val grouped = lore.history.groupBy { it.era }
    eraOrder.forEach { era ->
        val entries = grouped[era].orEmpty()
        if (entries.isNotEmpty()) {
            item(key = "hist_era_$era") { EraHeader(era, entries.minOf { it.year }, entries.maxOf { it.year }) }
            items(
                items = entries,
                key = { entry -> "hist_${entry.era}_${entry.year}_${entry.text.hashCode()}" }
            ) { entry -> HistoryRow(entry) }
        }
    }
    if (state.worldEvents.isNotEmpty()) {
        item(key = "hist_living_header") {
            Column {
                Spacer(Modifier.height(RealmsSpacing.s))
                EraHeader("recent_live", 0, 0, overrideLabel = "— LIVING WORLD —")
            }
        }
        items(
            items = state.worldEvents,
            key = { "hist_live_${it.turn}_${it.title}" }
        ) {
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
    item(key = "hist_present") {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                "— PRESENT DAY —",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun EraHeader(era: String, fromYear: Int, toYear: Int, overrideLabel: String? = null) {
    val (label, color) = when (era) {
        "primordial" -> "— PRIMORDIAL AGE —" to MaterialTheme.colorScheme.secondary
        "ancient" -> "— ANCIENT AGE —" to MaterialTheme.colorScheme.secondary
        "medieval" -> "— MEDIEVAL AGE —" to MaterialTheme.colorScheme.primary
        "dark_age" -> "— DARK AGE —" to MaterialTheme.colorScheme.error
        "recent" -> "— RECENT HISTORY —" to MaterialTheme.colorScheme.tertiary
        else -> (overrideLabel ?: era.uppercase()) to MaterialTheme.colorScheme.tertiary
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
    val color = when (entry.era) {
        "primordial" -> MaterialTheme.colorScheme.secondary
        "ancient" -> MaterialTheme.colorScheme.secondary
        "medieval" -> MaterialTheme.colorScheme.primary
        "dark_age" -> MaterialTheme.colorScheme.error
        "recent" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.tertiary
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

private fun LazyListScope.loreRumorsItems(state: GameUiState) {
    val rumors = state.worldLore?.rumors.orEmpty()
    if (rumors.isEmpty()) {
        item(key = "rumors_empty") { EmptyState("\uD83D\uDDE3\uFE0F", "No rumors yet. Taverns are quiet.") }
        return
    }
    items(
        items = rumors,
        key = { r -> "rumor_${r.hashCode()}" }
    ) { r ->
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
