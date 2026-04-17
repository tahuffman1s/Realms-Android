package com.realmsoffate.game.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.components.RealmsProgressBar
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

// ============================================================
// TOP BAR — 2 tight rows matching the web spec
// ============================================================

@Composable
internal fun GameTopBar(
    state: GameUiState,
    onSettingsClick: () -> Unit = {},
    onMemoriesClick: () -> Unit = {}
) {
    val ch = state.character ?: return
    val realms = RealmsTheme.colors
    val location = state.worldMap?.locations?.getOrNull(state.currentLoc)
    var statsExpanded by remember { mutableStateOf(true) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = RealmsElevation.low,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = RealmsSpacing.m, vertical = RealmsSpacing.s)) {
            // ----- Row 1: name | level badge | combat indicator | party icons | bookmarks | settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ch.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
                Spacer(Modifier.width(RealmsSpacing.s))
                LevelBadge(ch.level)
                if (state.currentScene == "battle") {
                    Spacer(Modifier.width(RealmsSpacing.xs))
                    CombatIndicator()
                }
                if (state.party.isNotEmpty()) {
                    Spacer(Modifier.width(RealmsSpacing.s))
                    PartyIcons(state.party.map { it.name })
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMemoriesClick) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Memories")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            // ----- Collapsible stats section -----
            AnimatedVisibility(
                visible = statsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(RealmsSpacing.s))
                    // HP bar | XP bar | gold
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HpInline(ch.hp, ch.maxHp, Modifier.weight(1f))
                        Spacer(Modifier.width(RealmsSpacing.s))
                        XpInline(ch.xp, ch.level, Modifier.weight(1f))
                        Spacer(Modifier.width(RealmsSpacing.s))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            GoldInline("${ch.gold}", realms.goldAccent)
                        }
                    }
                    // Status effects | location
                    if (ch.conditions.isNotEmpty() || location != null) {
                        Spacer(Modifier.height(RealmsSpacing.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (ch.conditions.isNotEmpty()) {
                                ConditionsStrip(ch.conditions, Modifier.weight(1f, fill = false))
                            }
                            Spacer(Modifier.weight(1f))
                            if (location != null) {
                                LocationInline(location.icon, location.name)
                            }
                        }
                    }
                }
            }
            // Collapse/expand toggle
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { statsExpanded = !statsExpanded },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (statsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (statsExpanded) "Collapse stats" else "Expand stats",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ConditionsStrip(conditions: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        conditions.forEach { c -> ConditionChip(c) }
    }
}

@Composable
private fun ConditionChip(name: String) {
    val realms = RealmsTheme.colors
    val (color, icon) = when (name.lowercase()) {
        "poisoned" -> realms.success to "\uD83E\uDDEA"
        "blessed" -> realms.goldAccent to "\u2728"
        "cursed", "doomed" -> realms.fumbleRed to "\uD83D\uDC80"
        "frightened", "fearful" -> realms.warning to "\uD83D\uDE28"
        "charmed" -> MaterialTheme.colorScheme.tertiary to "\uD83D\uDC95"
        "paralyzed", "stunned" -> realms.info to "\u2744\uFE0F"
        "invisible" -> realms.info to "\uD83D\uDC7B"
        "silenced" -> realms.info to "\uD83E\uDD2B"
        "blinded" -> realms.warning to "\uD83D\uDC41\uFE0F"
        "burning", "on fire" -> realms.fumbleRed to "\uD83D\uDD25"
        "bleeding" -> realms.fumbleRed to "\uD83E\uDE78"
        "exhausted", "fatigued" -> realms.warning to "\uD83D\uDE2B"
        "prone" -> realms.warning to "\u2B07\uFE0F"
        "marked", "branded" -> realms.fumbleRed to "\uD83C\uDFAF"
        "raging" -> realms.fumbleRed to "\uD83D\uDE21"
        "hidden", "stealth" -> realms.info to "\uD83D\uDC41\uFE0F"
        else -> realms.info to "\u2728"
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), MaterialTheme.shapes.small)
    ) {
        Row(
            Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(RealmsSpacing.xxs))
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            "L$level",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.xxs)
        )
    }
}

@Composable
private fun CombatIndicator() {
    val infinite = rememberInfiniteTransition(label = "combatPulse")
    val alpha by infinite.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "cmbAlpha"
    )
    Text(
        "\u2694\uFE0F",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error.copy(alpha = alpha)
    )
}

@Composable
private fun PartyIcons(names: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        names.take(3).forEach { n ->
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(n.take(1).uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        if (names.size > 3) {
            Text(
                "+${names.size - 3}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = RealmsSpacing.s)
            )
        }
    }
}

@Composable
private fun HpInline(hp: Int, maxHp: Int, modifier: Modifier = Modifier) {
    val realms = RealmsTheme.colors
    val pct = (hp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f)
    val color = when {
        pct < 0.33f -> MaterialTheme.colorScheme.error
        pct < 0.66f -> realms.warning
        else -> realms.success
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(RealmsSpacing.xs))
            Text(
                "$hp/$maxHp",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(RealmsSpacing.xxs))
        RealmsProgressBar(progress = pct, color = color)
    }
}

@Composable
private fun XpInline(xp: Int, level: Int, modifier: Modifier = Modifier) {
    val base = if (level <= 1) 0 else GameViewModel.levelThreshold(level)
    val next = GameViewModel.levelThreshold(level + 1)
    val span = (next - base).coerceAtLeast(1)
    val pct = ((xp - base).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(RealmsSpacing.xs))
            Text(
                "$xp / $next",
                style = MaterialTheme.typography.labelMedium,
                color = RealmsTheme.colors.info
            )
        }
        Spacer(Modifier.height(RealmsSpacing.xxs))
        RealmsProgressBar(progress = pct, color = RealmsTheme.colors.info)
    }
}

@Composable
private fun GoldInline(value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("\uD83D\uDCB0", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(RealmsSpacing.xxs))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
private fun LocationInline(icon: String, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(RealmsSpacing.xxs))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp)
        )
    }
}
