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
import androidx.compose.material.icons.filled.Close
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
import com.realmsoffate.game.ui.components.FilterTabRow
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

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
internal fun JournalPanel(state: GameUiState, focusNpc: String? = null, onClose: () -> Unit) {
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
                Modifier.padding(horizontal = RealmsSpacing.m).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RealmsCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.height(RealmsSpacing.s))
        }
        FilterTabRow(
            tabs = JournalFilter.entries.map { it.label to it.icon },
            selectedIndex = JournalFilter.entries.indexOf(filter),
            onSelect = { filter = JournalFilter.entries[it] }
        )
        Spacer(Modifier.height(RealmsSpacing.s))
        if (filtered.isEmpty()) {
            Text(
                "No ${filter.label.lowercase()} NPCs in journal.",
                modifier = Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.xxl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@PanelSheet
        }

        // Selected NPC detail — animates in/out instead of abruptly appearing.
        val selectedNpc = selected?.let { sel -> state.npcLog.firstOrNull { it.name == sel } }
        AnimatedVisibility(
            visible = selectedNpc != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            if (selectedNpc != null) {
                Column {
                    NpcDetailCard(npc = selectedNpc, onClose = { selected = null })
                    Spacer(Modifier.height(RealmsSpacing.s))
                }
            }
        }

        LazyColumn(
            Modifier.padding(horizontal = RealmsSpacing.m).heightIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
        ) {
            items(filtered) { n ->
                RealmsCard(
                    onClick = { selected = if (selected == n.name) null else n.name },
                    selected = selected == n.name,
                    accentColor = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                            Spacer(Modifier.width(RealmsSpacing.xs))
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    "☠️ DEAD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
                                )
                            }
                        }
                    }
                    if (n.personality.isNotBlank()) Text(
                        n.personality,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = RealmsSpacing.xs)
                    )
                    Text(
                        "Last seen T${n.lastSeenTurn} · ${n.lastLocation}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = RealmsSpacing.xs)
                    )
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
    RealmsCard(
        outlined = true,
        accentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = RealmsSpacing.m).fillMaxWidth()
    ) {
        if (npc.status == "dead") {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = RealmsSpacing.s)
            ) {
                Row(
                    Modifier.padding(horizontal = RealmsSpacing.m, vertical = RealmsSpacing.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("☠️", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(RealmsSpacing.s))
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
            Spacer(Modifier.width(RealmsSpacing.m))
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
            Spacer(Modifier.height(RealmsSpacing.s))
            Surface(
                color = realms.info.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    "\u201C${npc.personality}\u201D",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = realms.info,
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
                    color = realms.goldAccent
                )
            }
            Spacer(Modifier.height(RealmsSpacing.xs))
            npc.memorableQuotes.forEach { line ->
                DialogueLine(line, accent = realms.goldAccent)
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
    Row(Modifier.padding(vertical = RealmsSpacing.xxs)) {
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
                .heightIn(min = RealmsSpacing.l)
                .background(barColor.copy(alpha = if (accent != null) 0.8f else 0.4f))
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
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = RealmsSpacing.xxs)
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
    Surface(color = color.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small) {
        Text(
            relationship.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
        )
    }
}
