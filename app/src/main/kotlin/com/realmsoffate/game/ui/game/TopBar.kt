package com.realmsoffate.game.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.theme.RealmsTheme

// ============================================================
// TOP BAR — 2 tight rows matching the web spec
// ============================================================

@Composable
internal fun GameTopBar(
    state: GameUiState,
    onSettingsClick: () -> Unit = {}
) {
    val ch = state.character ?: return
    val realms = RealmsTheme.colors
    val location = state.worldMap?.locations?.getOrNull(state.currentLoc)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // ----- Row 1: name | level badge | combat indicator | party icons | settings gear
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ch.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                LevelBadge(ch.level)
                if (state.currentScene == "battle") {
                    Spacer(Modifier.width(6.dp))
                    CombatIndicator()
                }
                Spacer(Modifier.weight(1f))
                if (state.party.isNotEmpty()) {
                    PartyIcons(state.party.map { it.name })
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            Spacer(Modifier.height(8.dp))
            // ----- Row 2: HP text + bar | XP text + bar | gold | location chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                HpInline(ch.hp, ch.maxHp, Modifier.weight(1.2f))
                Spacer(Modifier.width(10.dp))
                XpInline(ch.xp, ch.level, Modifier.weight(1.1f))
                Spacer(Modifier.width(10.dp))
                GoldInline("${ch.gold}", realms.goldAccent)
                if (location != null) {
                    Spacer(Modifier.width(8.dp))
                    LocationInline(location.icon, location.name)
                }
            }
            // ----- Row 3 (only when present): active conditions strip -----
            if (ch.conditions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ConditionsStrip(ch.conditions)
            }
        }
    }
}

@Composable
private fun ConditionsStrip(conditions: List<String>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(3.dp))
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
    val realms = RealmsTheme.colors
    Surface(
        color = realms.goldAccent.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            "L$level",
            style = MaterialTheme.typography.labelMedium,
            color = realms.goldAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                modifier = Modifier.padding(start = 8.dp)
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
            Spacer(Modifier.width(4.dp))
            Text(
                "$hp/$maxHp",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
            Spacer(Modifier.width(4.dp))
            Text(
                "$xp / $next",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun GoldInline(value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("\uD83D\uDCB0", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(2.dp))
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
        Spacer(Modifier.width(2.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp)
        )
    }
}
