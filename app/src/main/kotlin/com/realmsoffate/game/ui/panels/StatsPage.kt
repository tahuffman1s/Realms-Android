@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsProgressBar
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.util.formatSigned

// ----------------- STATS -----------------

@Composable
internal fun StatsContent(state: GameUiState) {
    val ch = state.character ?: return
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.xs)
    ) {
        Text(ch.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "L${ch.level} ${ch.race} ${ch.cls}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(RealmsSpacing.m))

        Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s), modifier = Modifier.fillMaxWidth()) {
            StatTile(label = "HP", value = "${ch.hp}/${ch.maxHp}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            StatTile(label = "AC", value = "${ch.ac}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            StatTile(label = "PROF", value = "+${ch.proficiency}", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
            StatTile(label = "XP", value = "${ch.xp}", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(RealmsSpacing.m))
        SectionHeader("ABILITIES")
        Spacer(Modifier.height(RealmsSpacing.xs))
        // 3-column grid with icons, matching the web's layout.
        Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs), modifier = Modifier.fillMaxWidth()) {
            AbilityTile("STR", "Strength",     "\uD83D\uDCAA", ch.abilities.str, ch.abilities.strMod, Modifier.weight(1f))
            AbilityTile("DEX", "Dexterity",    "\uD83C\uDFC3", ch.abilities.dex, ch.abilities.dexMod, Modifier.weight(1f))
            AbilityTile("CON", "Constitution", "\u2764\uFE0F", ch.abilities.con, ch.abilities.conMod, Modifier.weight(1f))
        }
        Spacer(Modifier.height(RealmsSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs), modifier = Modifier.fillMaxWidth()) {
            AbilityTile("INT", "Intelligence", "\uD83E\uDDE0", ch.abilities.int, ch.abilities.intMod, Modifier.weight(1f))
            AbilityTile("WIS", "Wisdom",       "\uD83D\uDC41\uFE0F", ch.abilities.wis, ch.abilities.wisMod, Modifier.weight(1f))
            AbilityTile("CHA", "Charisma",     "\uD83D\uDCE3", ch.abilities.cha, ch.abilities.chaMod, Modifier.weight(1f))
        }

        Spacer(Modifier.height(RealmsSpacing.m))
        SectionHeader("MORALITY")
        val mcolor = when {
            state.morality >= 30 -> MaterialTheme.colorScheme.primary
            state.morality <= -30 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val pct = (state.morality + 100) / 200f
        Text(
            "${formatSigned(state.morality)}",
            style = MaterialTheme.typography.titleLarge,
            color = mcolor,
            fontWeight = FontWeight.Bold
        )
        RealmsProgressBar(progress = pct, color = mcolor)

        if (ch.conditions.isNotEmpty()) {
            Spacer(Modifier.height(RealmsSpacing.m))
            SectionHeader("CONDITIONS")
            Spacer(Modifier.height(RealmsSpacing.xs))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs)
            ) {
                ch.conditions.forEach { cond ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.small)
                    ) {
                        Text(
                            cond,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
                        )
                    }
                }
            }
        }

        ch.backstory?.let { bs ->
            Spacer(Modifier.height(RealmsSpacing.m))
            SectionHeader("BACKSTORY")
            Column(verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                BackstoryCard("\uD83C\uDF05", "Origin", bs.origin, MaterialTheme.colorScheme.secondary)
                BackstoryCard("\uD83C\uDFAF", "Motivation", bs.motivation, MaterialTheme.colorScheme.tertiary)
                BackstoryCard("\uD83D\uDC94", "Flaw", bs.flaw, MaterialTheme.colorScheme.error)
                BackstoryCard("\uD83D\uDD17", "Bond", bs.bond, MaterialTheme.colorScheme.primary)
                BackstoryCard("\uD83D\uDD73\uFE0F", "Dark Secret", bs.darkSecret, MaterialTheme.colorScheme.secondary)
                BackstoryCard("\uD83D\uDD0D", "Lost Item", bs.lostItem, MaterialTheme.colorScheme.tertiary)
                BackstoryCard("\u2620\uFE0F", "Personal Enemy", bs.personalEnemy, MaterialTheme.colorScheme.error)
                bs.prophecy?.let { p ->
                    if (p.isNotBlank()) BackstoryCard("\uD83D\uDD2E", "Prophecy", p, MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
internal fun StatsPanel(state: GameUiState, onClose: () -> Unit) {
    PanelSheet("\uD83D\uDCCA  Character", onClose = onClose) {
        StatsContent(state)
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(RealmsSpacing.s).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BackstoryCard(icon: String, label: String, text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(RealmsSpacing.m)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 18.sp)
            }
            Spacer(Modifier.width(RealmsSpacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(RealmsSpacing.xxs))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AbilityTile(
    abbr: String,
    full: String,
    icon: String,
    score: Int,
    mod: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.s).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(RealmsSpacing.xxs))
            Text(abbr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                formatSigned(mod),
                style = MaterialTheme.typography.labelMedium,
                color = if (mod >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                full,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
