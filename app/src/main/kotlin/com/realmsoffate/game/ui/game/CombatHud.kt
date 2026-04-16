package com.realmsoffate.game.ui.game

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.game.CombatState
import com.realmsoffate.game.game.Combatant
import com.realmsoffate.game.ui.components.RealmsProgressBar
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

@Composable
internal fun CombatHud(
    combat: CombatState,
    npcLog: List<LogNpc> = emptyList(),
    currentTurn: Int = 0
) {
    val realms = RealmsTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u2694\uFE0F", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(RealmsSpacing.s))
                Text(
                    "ROUND ${combat.round}",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                combat.active?.let { active ->
                    val turnColor = if (active.isPlayer) realms.goldAccent else MaterialTheme.colorScheme.error
                    Surface(
                        color = turnColor.copy(alpha = 0.18f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(
                            Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (active.isPlayer) "\u2605" else "\u2620",
                                style = MaterialTheme.typography.labelSmall,
                                color = turnColor
                            )
                            Spacer(Modifier.width(RealmsSpacing.xxs))
                            Text(
                                active.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = turnColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(RealmsSpacing.xs))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs)
            ) {
                combat.order.forEachIndexed { idx, c ->
                    InitiativeChip(c, active = idx == combat.activeIndex)
                }
            }
            // Enemies now appear directly in the initiative order via [ENEMY] tags
        }
    }
}

@Composable
private fun InitiativeChip(c: Combatant, active: Boolean) {
    val realms = RealmsTheme.colors
    val pct = (c.hp.toFloat() / c.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val hpColor = when {
        pct < 0.33f -> MaterialTheme.colorScheme.error
        pct < 0.66f -> realms.warning
        else -> realms.success
    }
    val isEnemy = !c.isPlayer && c.initiative > 0 // enemies added via [ENEMY] tags
    val chipBg = when {
        active -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        isEnemy -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    // Player's active turn uses goldAccent border so it reads as friendly, not enemy-red.
    val chipBorder = when {
        active && c.isPlayer -> realms.goldAccent
        active -> MaterialTheme.colorScheme.error
        isEnemy -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        color = chipBg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.border(1.dp, chipBorder, MaterialTheme.shapes.small)
    ) {
        Column(Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.isPlayer) Text("\u2605", style = MaterialTheme.typography.labelSmall, color = realms.goldAccent)
                else if (isEnemy) Text("\u2620", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Text(
                    c.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(RealmsSpacing.xs))
                Text(
                    "i${c.initiative}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.hp}/${c.maxHp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = hpColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(RealmsSpacing.xs))
                RealmsProgressBar(progress = pct, color = hpColor, height = 4.dp, modifier = Modifier.width(60.dp))
            }
        }
    }
}
