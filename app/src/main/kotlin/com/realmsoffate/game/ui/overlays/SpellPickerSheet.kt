package com.realmsoffate.game.ui.overlays

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
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.game.Spell
import com.realmsoffate.game.game.Spells
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.theme.RealmsSpacing

private val spellLevelLabels = mapOf(
    0 to "CANTRIPS & ABILITIES",
    1 to "1ST LEVEL",
    2 to "2ND LEVEL",
    3 to "3RD LEVEL",
    4 to "4TH LEVEL",
    5 to "5TH LEVEL"
)

/**
 * Combat-time spell picker. Mirrors the Attack button's ModalBottomSheet
 * pattern: bottom-anchored sheet that lists the character's known spells
 * grouped by level. Tapping a castable spell dismisses the sheet and fires
 * [onCast]; the caller routes into the target prompt or self-cast flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellPickerSheet(
    character: Character,
    onCast: (Spell) -> Unit,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val known = remember(character) {
        character.knownSpells.mapNotNull { Spells.find(it) }.sortedBy { it.level }
    }
    val grouped = remember(known) { known.groupBy { it.level } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = RealmsSpacing.s)) {
            Text(
                "CAST A SPELL",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(RealmsSpacing.xs))
            SlotStrip(character)

            if (known.isEmpty()) {
                Spacer(Modifier.height(RealmsSpacing.m))
                Text(
                    "You know no spells.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(RealmsSpacing.s))
                LazyColumn(
                    Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grouped.keys.sorted().forEach { lvl ->
                        item {
                            Text(
                                spellLevelLabels[lvl] ?: "LEVEL $lvl",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }
                        items(grouped[lvl].orEmpty()) { spell ->
                            val canCast = spell.level == 0 ||
                                (character.spellSlots[spell.level] ?: 0) > 0
                            SpellRow(spell, canCast) {
                                if (canCast) onCast(spell)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(RealmsSpacing.s))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel") }
            Spacer(Modifier.navigationBarsPadding().height(RealmsSpacing.m))
        }
    }
}

@Composable
private fun SlotStrip(ch: Character) {
    val levels = ch.maxSpellSlots.keys.sorted()
    if (levels.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Box(
        Modifier
            .size(10.dp)
            .rotate(45f)
            .background(
                if (filled) color else Color.Transparent,
                RoundedCornerShape(1.dp)
            )
            .border(1.dp, color, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun SpellRow(
    spell: Spell,
    canCast: Boolean,
    onClick: () -> Unit
) {
    RealmsCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        outlined = true,
        accentColor = if (canCast) MaterialTheme.colorScheme.outlineVariant
                      else MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
        contentPadding = RealmsSpacing.s,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spell.icon, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    spell.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    buildString {
                        append(spell.school)
                        if (spell.level > 0) append(" · L${spell.level}")
                        if (spell.damage != "-") append(" · ${spell.damage}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (!canCast) {
                Text(
                    "No slots",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
