@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.realmsoffate.game.ui.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing

// ----------------- JOURNAL -----------------

private enum class JournalFilter(val label: String, val match: (String) -> Boolean) {
    All("All", { true }),
    Friendly("Friendly", { r ->
        listOf("friendly", "ally", "love", "grateful", "allied", "romantic", "warm", "helpful", "loyal", "trusted").any { r.contains(it) }
    }),
    Hostile("Hostile", { r ->
        listOf("hostile", "enemy", "rival", "feared", "angry", "aggressive", "hateful", "suspicious", "distrustful").any { r.contains(it) }
    }),
    Neutral("Neutral", { r ->
        val friendly = listOf("friendly", "ally", "love", "grateful", "allied", "romantic", "warm", "helpful", "loyal", "trusted")
        val hostile = listOf("hostile", "enemy", "rival", "feared", "angry", "aggressive", "hateful", "suspicious", "distrustful")
        !friendly.any { r.contains(it) } && !hostile.any { r.contains(it) }
    })
}

@Composable
internal fun JournalContent(state: GameUiState, focusNpc: String? = null) {
    var filter by remember { mutableStateOf(JournalFilter.All) }
    var expandedNpcName by remember(focusNpc) { mutableStateOf(focusNpc) }
    val filtered = state.npcLog.filter { filter.match(it.relationship.lowercase()) }
    if (state.npcLog.isEmpty()) {
        EmptyState("\uD83D\uDCD6", "No NPCs met yet.")
        return
    }

    LazyColumn(
        Modifier.padding(horizontal = RealmsSpacing.l).fillMaxSize(),
        contentPadding = PaddingValues(top = RealmsSpacing.s, bottom = RealmsSpacing.m),
        verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
    ) {
            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs)
                ) {
                    JournalFilter.entries.forEach { opt ->
                        FilterChip(
                            selected = opt == filter,
                            onClick = { filter = opt },
                            label = { Text(opt.label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No ${filter.label.lowercase()} NPCs in journal.",
                        modifier = Modifier.padding(vertical = RealmsSpacing.xl),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        items(filtered, key = { it.name }) { n ->
            NpcJournalRow(
                npc = n,
                expanded = expandedNpcName == n.name,
                onToggle = {
                    expandedNpcName = if (expandedNpcName == n.name) null else n.name
                }
            )
        }
    }
}

@Composable
internal fun JournalPanel(state: GameUiState, focusNpc: String? = null, onClose: () -> Unit) {
    PanelSheet(
        "\uD83D\uDCD6  Journal",
        subtitle = if (state.npcLog.isEmpty()) null else "${state.npcLog.size} NPCs met",
        onClose = onClose
    ) {
        JournalContent(state, focusNpc)
    }
}

// ─── Journal row (summary + optional expanded detail) ─────────────────

@Composable
private fun NpcJournalRow(
    npc: com.realmsoffate.game.data.LogNpc,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val isDead = npc.status == "dead"
    val avatarColor = if (isDead)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.tertiaryContainer

    RealmsCard(
        onClick = onToggle,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        outlined = expanded,
        accentColor = if (expanded) MaterialTheme.colorScheme.primary else null
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isDead) "☠" else npc.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isDead) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(RealmsSpacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        npc.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(RealmsSpacing.s))
                    StatusTag(npc)
                }
                Text(
                    buildString {
                        if (npc.race.isNotBlank()) append(npc.race)
                        if (npc.role.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(npc.role)
                        }
                        if (npc.age.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(npc.age)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (npc.personality.isNotBlank()) {
                    Text(
                        npc.personality,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = RealmsSpacing.xxs)
                    )
                }
                Text(
                    "Last seen T${npc.lastSeenTurn} · ${npc.lastLocation}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = RealmsSpacing.xxs)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse entry" else "Expand entry",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            NpcExpandedDetailSections(npc)
        }
    }
}

// ─── Status tag (relationship or dead) ───────────────────────────────

@Composable
private fun StatusTag(npc: com.realmsoffate.game.data.LogNpc) {
    if (npc.status == "dead") {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                "DEAD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
            )
        }
    } else {
        RelationshipTag(npc.relationship)
    }
}

// ─── Expanded detail (inline under list summary) ─────────────────────

@Composable
private fun NpcExpandedDetailSections(npc: com.realmsoffate.game.data.LogNpc) {
    Column {
        if (npc.id.isNotBlank()) {
            Text(
                "#${npc.id}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(RealmsSpacing.s))
        }
        if (npc.personality.isNotBlank()) {
            Spacer(Modifier.height(RealmsSpacing.s))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    "\u201C${npc.personality}\u201D",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xs)
                )
            }
        }
        if (npc.appearance.isNotBlank()) {
            Spacer(Modifier.height(RealmsSpacing.s))
            SectionHeader("APPEARANCE")
            val traits = extractTraitChips(npc.appearance)
            if (traits.isNotEmpty()) {
                Spacer(Modifier.height(RealmsSpacing.xs))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs)
                ) {
                    traits.forEach { t -> TraitChip(t) }
                }
            }
            Spacer(Modifier.height(RealmsSpacing.xs))
            Text(npc.appearance, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(RealmsSpacing.s))
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
            Spacer(Modifier.height(RealmsSpacing.s))
            SectionHeader("YOUR ASSESSMENT")
            Text(
                "\u201C${npc.thoughts}\u201D",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            )
        }
        if (npc.memorableQuotes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(RealmsSpacing.s))
                Text(
                    "MEMORABLE LINES",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.height(RealmsSpacing.xs))
            npc.memorableQuotes.forEach { line ->
                DialogueLine(line, highlighted = true)
            }
        }
        if (npc.dialogueHistory.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionHeader("RECENT DIALOGUE")
            Spacer(Modifier.height(RealmsSpacing.xs))
            npc.dialogueHistory.takeLast(6).forEach { line ->
                DialogueLine(line)
            }
        }
    }
}

// ─── Supporting composables ──────────────────────────────────────────

@Composable
private fun DialogueLine(
    raw: String,
    highlighted: Boolean = false
) {
    val turnSep = raw.indexOf(':')
    val turnLabel = if (turnSep > 0 && raw.startsWith("T")) raw.substring(0, turnSep) else ""
    val body = if (turnSep > 0) raw.substring(turnSep + 1).trim() else raw
    val barColor = if (highlighted) MaterialTheme.colorScheme.tertiary
                   else MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.padding(vertical = RealmsSpacing.xxs)) {
        if (turnLabel.isNotBlank()) {
            Text(
                turnLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp)
            )
        }
        Box(
            Modifier
                .width(if (highlighted) 3.dp else 2.dp)
                .heightIn(min = RealmsSpacing.l)
                .background(barColor)
        )
        Spacer(Modifier.width(RealmsSpacing.s))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TraitChip(text: String) {
    SuggestionChip(
        onClick = {},
        label = { Text(text, fontWeight = FontWeight.Bold) }
    )
}

private fun extractTraitChips(text: String): List<String> {
    val lc = text.lowercase()
    val hits = mutableListOf<String>()
    val map = listOf(
        "tall" to "Tall", "short" to "Short", "stocky" to "Stocky",
        "gaunt" to "Gaunt", "slender" to "Slender", "muscular" to "Muscular",
        "scarred" to "Scarred", "hooded" to "Hooded", "cloaked" to "Cloaked",
        "robed" to "Robed", "armored" to "Armored", "armoured" to "Armoured",
        "bearded" to "Bearded", "tattoo" to "Tattooed", "one-eyed" to "One-Eyed",
        "missing" to "Missing ‹digit›", "pale" to "Pale",
        "dark-skinned" to "Dark-skinned", "soot" to "Sooty",
        "burnt" to "Burn-scar", "noble" to "Noble bearing",
        "weathered" to "Weathered", "young" to "Young",
        "ancient" to "Ancient", "elderly" to "Elderly"
    )
    map.forEach { (kw, label) -> if (kw in lc) hits += label }
    return hits.distinct().take(6)
}

@Composable
private fun RelationshipTag(relationship: String) {
    val (bg, fg) = when (relationship.lowercase()) {
        "friendly", "ally", "love" ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "hostile", "enemy" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "suspicious", "wary" ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else ->
            MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(
            relationship.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
        )
    }
}
