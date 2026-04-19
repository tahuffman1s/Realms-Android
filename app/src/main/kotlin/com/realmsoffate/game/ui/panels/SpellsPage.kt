@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.Spells
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.theme.RealmsSpacing

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
internal fun SpellsContent(
    state: GameUiState,
    onHotbar: (Int, String?) -> Unit = { _, _ -> },
    onCast: (spell: com.realmsoffate.game.game.Spell) -> Unit = {}
) {
    val ch = state.character ?: return
    var selectedName by remember(ch) { mutableStateOf<String?>(null) }
    if (ch.knownSpells.isEmpty()) {
        EmptyState("\u2728", "You know no spells.")
        return
    }
    // Slot diamonds header
    SpellSlotStrip(ch)
    Spacer(Modifier.height(6.dp))

    val knownSpells = ch.knownSpells.mapNotNull { Spells.find(it) }.sortedBy { it.level }
    val grouped = knownSpells.groupBy { it.level }

    LazyColumn(
        Modifier.padding(horizontal = RealmsSpacing.l).heightIn(max = 540.dp),
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

@Composable
internal fun SpellsPanel(
    state: GameUiState,
    onClose: () -> Unit,
    onHotbar: (Int, String?) -> Unit = { _, _ -> },
    onCast: (spell: com.realmsoffate.game.game.Spell) -> Unit = {}
) {
    val ch = state.character
    PanelSheet(
        "\u2728  Spells",
        subtitle = if (ch == null || ch.knownSpells.isEmpty()) null else "${ch.knownSpells.size} known",
        onClose = onClose
    ) {
        SpellsContent(state, onHotbar, onCast)
    }
}

@Composable
private fun SpellSlotStrip(ch: com.realmsoffate.game.data.Character) {
    val levels = ch.maxSpellSlots.keys.sorted()
    if (levels.isEmpty()) return
    Row(
        Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.xs),
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
                        SlotDiamond(filled = i < cur, color = MaterialTheme.colorScheme.secondary)
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
    RealmsCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        outlined = true,
        accentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        selected = selected,
        contentPadding = RealmsSpacing.s,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    RealmsCard(
        outlined = true,
        accentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.medium,
        contentPadding = RealmsSpacing.m,
        modifier = Modifier.fillMaxWidth()
    ) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(spell.desc, style = MaterialTheme.typography.bodyMedium)
        if (spell.damage != "-") {
            Spacer(Modifier.height(RealmsSpacing.xs))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    "\uD83D\uDDE1\uFE0F ${spell.damage}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
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
            shape = MaterialTheme.shapes.small
        ) { Text(if (canCast) "Cast Now" else "No Slots") }
    }
}
