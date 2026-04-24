@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.realmsoffate.game.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.components.RealmsProgressBar
import com.realmsoffate.game.ui.theme.RealmsSpacing
import java.util.Locale

// ============================================================
// TOP BAR — M3 TopAppBar: single title row, tight stat block, 4dp progress tracks
// ============================================================

private fun titleCaseWords(value: String): String =
    value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { word ->
        word.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
        }
    }

@Composable
internal fun GameTopBar(
    state: GameUiState,
    /** Scene name + description below XP / location when stats are expanded (chat tab, non-default scene, not in combat). */
    showSceneContext: Boolean = false,
    onSettingsClick: () -> Unit = {},
    infiniteGold: Boolean = false,
    cheatsEnabled: Boolean = false,
    onCheatsClick: () -> Unit = {},
) {
    val ch = state.character ?: return
    val location = state.worldMap?.locations?.getOrNull(state.currentLoc)
    var statsExpanded by remember { mutableStateOf(false) }

    val chromeSurface = MaterialTheme.colorScheme.surfaceContainer
    val configuration = LocalConfiguration.current
    // Same max width for both bars (½ screen) so tracks align visually.
    val progressBarMaxWidth = configuration.screenWidthDp.dp / 2

    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = chromeSurface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = chromeSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Default compact height only — do not use TopAppBarExpandedHeight (large-app-bar slot); it reserves ~152dp and creates empty space.
            TopAppBar(
                modifier = Modifier.fillMaxWidth(),
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = barColors,
                title = {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(end = RealmsSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
                    ) {
                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                text = ch.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${titleCaseWords(ch.race)} ${titleCaseWords(ch.cls)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (state.currentScene == "battle") {
                            CombatIndicator()
                        }
                        if (state.party.isNotEmpty()) {
                            PartyIcons(state.party.map { it.name })
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { statsExpanded = !statsExpanded }) {
                        Icon(
                            imageVector = if (statsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (statsExpanded) {
                                "Collapse stats"
                            } else {
                                "Expand stats"
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (cheatsEnabled) {
                        IconButton(onClick = onCheatsClick) {
                            Text(
                                "🃏",
                                fontSize = 22.sp,
                                modifier = Modifier.semantics { contentDescription = "Cheats" }
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )

            AnimatedVisibility(
                visible = statsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    Modifier.padding(
                        horizontal = RealmsSpacing.m,
                        vertical = RealmsSpacing.s
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HpInline(
                            ch.hp,
                            ch.maxHp,
                            progressBarMaxWidth,
                            Modifier.weight(1f)
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            GoldInline(
                                if (infiniteGold) "∞" else "${ch.gold}",
                                MaterialTheme.colorScheme.tertiary
                            )
                            if (location != null) {
                                Spacer(Modifier.height(RealmsSpacing.xxs))
                                LocationInline(location.icon, location.name)
                            }
                        }
                    }
                    Spacer(Modifier.height(RealmsSpacing.s))
                    XpWithLevelColumn(
                        xp = ch.xp,
                        level = ch.level,
                        barMaxWidth = progressBarMaxWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showSceneContext) {
                        Spacer(Modifier.height(RealmsSpacing.s))
                        TopBarSceneContext(
                            scene = state.currentScene,
                            desc = state.currentSceneDesc
                        )
                    }
                    if (ch.conditions.isNotEmpty()) {
                        Spacer(Modifier.height(RealmsSpacing.xs))
                        ConditionsStrip(ch.conditions)
                    }
                }
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
    val (color, icon) = when (name.lowercase()) {
        "poisoned" -> MaterialTheme.colorScheme.primary to "\uD83E\uDDEA"
        "blessed" -> MaterialTheme.colorScheme.tertiary to "\u2728"
        "cursed", "doomed" -> MaterialTheme.colorScheme.error to "\uD83D\uDC80"
        "frightened", "fearful" -> MaterialTheme.colorScheme.tertiary to "\uD83D\uDE28"
        "charmed" -> MaterialTheme.colorScheme.tertiary to "\uD83D\uDC95"
        "paralyzed", "stunned" -> MaterialTheme.colorScheme.secondary to "\u2744\uFE0F"
        "invisible" -> MaterialTheme.colorScheme.secondary to "\uD83D\uDC7B"
        "silenced" -> MaterialTheme.colorScheme.secondary to "\uD83E\uDD2B"
        "blinded" -> MaterialTheme.colorScheme.tertiary to "\uD83D\uDC41\uFE0F"
        "burning", "on fire" -> MaterialTheme.colorScheme.error to "\uD83D\uDD25"
        "bleeding" -> MaterialTheme.colorScheme.error to "\uD83E\uDE78"
        "exhausted", "fatigued" -> MaterialTheme.colorScheme.tertiary to "\uD83D\uDE2B"
        "prone" -> MaterialTheme.colorScheme.tertiary to "\u2B07\uFE0F"
        "marked", "branded" -> MaterialTheme.colorScheme.error to "\uD83C\uDFAF"
        "raging" -> MaterialTheme.colorScheme.error to "\uD83D\uDE21"
        "hidden", "stealth" -> MaterialTheme.colorScheme.secondary to "\uD83D\uDC41\uFE0F"
        else -> MaterialTheme.colorScheme.secondary to "\u2728"
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.border(
            width = 1.dp,
            color = color.copy(alpha = 0.38f),
            shape = MaterialTheme.shapes.small
        )
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
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LevelBadge(level: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            "Lv. $level",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.xxs)
        )
    }
}

@Composable
private fun CombatIndicator() {
    val infinite = rememberInfiniteTransition(label = "combatPulse")
    val alpha by infinite.animateFloat(
        0.55f,
        1f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "cmbAlpha"
    )
    Icon(
        Icons.Outlined.LocalFireDepartment,
        contentDescription = "In combat",
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.error.copy(alpha = alpha)
    )
}

@Composable
private fun PartyIcons(names: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        names.take(3).forEach { n ->
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        n.take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        if (names.size > 3) {
            Text(
                "+${names.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = RealmsSpacing.s)
            )
        }
    }
}

@Composable
private fun HpInline(
    hp: Int,
    maxHp: Int,
    maxProgressWidth: Dp,
    modifier: Modifier = Modifier
) {
    val pct = (hp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f)
    val color = when {
        pct < 0.33f -> MaterialTheme.colorScheme.error
        pct < 0.66f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "HP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(RealmsSpacing.xs))
            Text(
                "$hp/$maxHp",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(RealmsSpacing.xxs))
        Box(Modifier.widthIn(max = maxProgressWidth)) {
            RealmsProgressBar(
                progress = pct,
                color = color,
                modifier = Modifier.fillMaxWidth(),
                height = 4.dp
            )
        }
    }
}

/**
 * Row 1: level + XP labels; row 2: XP track (same horizontal band / width cap as [HpInline]).
 */
@Composable
private fun XpWithLevelColumn(
    xp: Int,
    level: Int,
    barMaxWidth: Dp,
    modifier: Modifier = Modifier
) {
    val base = if (level <= 1) 0 else GameViewModel.levelThreshold(level)
    val next = GameViewModel.levelThreshold(level + 1)
    val span = (next - base).coerceAtLeast(1)
    val pct = ((xp - base).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
        ) {
            LevelBadge(level)
            Text(
                "XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$xp / $next",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(Modifier.height(RealmsSpacing.xxs))
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .widthIn(max = barMaxWidth)
                    .align(Alignment.CenterStart)
            ) {
                RealmsProgressBar(
                    progress = pct,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                    height = 4.dp
                )
            }
        }
    }
}

/**
 * Compact scene line: no background, no dividers; sits under the XP bar.
 * Not independently collapsible — collapses with the enclosing stats panel.
 */
@Composable
private fun TopBarSceneContext(scene: String, desc: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val sceneTitleStyle = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 0.45.sp,
            fontWeight = FontWeight.SemiBold,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            )
        )
        val sceneDescStyle = MaterialTheme.typography.labelSmall.copy(
            lineHeight = 14.sp,
            fontWeight = FontWeight.Normal,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            )
        )
        Text(
            sceneEmoji(scene),
            style = sceneTitleStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(RealmsSpacing.s))
        Column(Modifier.weight(1f)) {
            Text(
                scene.uppercase(),
                style = sceneTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (desc.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    style = sceneDescStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GoldInline(value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("\uD83D\uDCB0", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(RealmsSpacing.xxs))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}

@Composable
private fun LocationInline(icon: String, name: String) {
    val iconStyle = MaterialTheme.typography.labelLarge.copy(
        fontFamily = FontFamily.Default,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
    val nameStyle = MaterialTheme.typography.labelLarge.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = iconStyle)
        Spacer(Modifier.width(RealmsSpacing.xxs))
        Text(
            name,
            style = nameStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp)
        )
    }
}
