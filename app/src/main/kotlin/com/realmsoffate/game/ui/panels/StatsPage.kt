@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.realmsoffate.game.ui.theme.RealmsTheme

// ----------------- STATS -----------------

@Composable
internal fun StatsPanel(state: GameUiState, onClose: () -> Unit) {
    val ch = state.character ?: return
    val realms = RealmsTheme.colors
    PanelSheet("\uD83D\uDCCA  Character", onClose = onClose) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(ch.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "L${ch.level} ${ch.race} ${ch.cls}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(label = "HP", value = "${ch.hp}/${ch.maxHp}", color = realms.success, modifier = Modifier.weight(1f))
                StatTile(label = "AC", value = "${ch.ac}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                StatTile(label = "PROF", value = "+${ch.proficiency}", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                StatTile(label = "XP", value = "${ch.xp}", color = realms.goldAccent, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            SectionCap("ABILITIES")
            Spacer(Modifier.height(4.dp))
            // 3-column grid with icons, matching the web's layout.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AbilityTile("STR", "Strength",     "\uD83D\uDCAA", ch.abilities.str, ch.abilities.strMod, Modifier.weight(1f))
                AbilityTile("DEX", "Dexterity",    "\uD83C\uDFC3", ch.abilities.dex, ch.abilities.dexMod, Modifier.weight(1f))
                AbilityTile("CON", "Constitution", "\u2764\uFE0F", ch.abilities.con, ch.abilities.conMod, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AbilityTile("INT", "Intelligence", "\uD83E\uDDE0", ch.abilities.int, ch.abilities.intMod, Modifier.weight(1f))
                AbilityTile("WIS", "Wisdom",       "\uD83D\uDC41\uFE0F", ch.abilities.wis, ch.abilities.wisMod, Modifier.weight(1f))
                AbilityTile("CHA", "Charisma",     "\uD83D\uDCE3", ch.abilities.cha, ch.abilities.chaMod, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            SectionCap("MORALITY")
            val mcolor = when {
                state.morality >= 30 -> realms.success
                state.morality <= -30 -> realms.fumbleRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val pct = (state.morality + 100) / 200f
            Text(
                "${formatSigned(state.morality)}",
                style = MaterialTheme.typography.titleLarge,
                color = mcolor,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                color = mcolor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (ch.conditions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionCap("CONDITIONS")
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ch.conditions.forEach { cond ->
                        Surface(
                            color = realms.warning.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, realms.warning.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                cond,
                                style = MaterialTheme.typography.labelMedium,
                                color = realms.warning,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            ch.backstory?.let { bs ->
                Spacer(Modifier.height(14.dp))
                SectionCap("BACKSTORY")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackstoryCard("\uD83C\uDF05", "Origin", bs.origin, realms.info)
                    BackstoryCard("\uD83C\uDFAF", "Motivation", bs.motivation, realms.goldAccent)
                    BackstoryCard("\uD83D\uDC94", "Flaw", bs.flaw, realms.fumbleRed)
                    BackstoryCard("\uD83D\uDD17", "Bond", bs.bond, realms.success)
                    BackstoryCard("\uD83D\uDD73\uFE0F", "Dark Secret", bs.darkSecret, MaterialTheme.colorScheme.secondary)
                    BackstoryCard("\uD83D\uDD0D", "Lost Item", bs.lostItem, realms.goldAccent)
                    BackstoryCard("\u2620\uFE0F", "Personal Enemy", bs.personalEnemy, realms.fumbleRed)
                    bs.prophecy?.let { p ->
                        if (p.isNotBlank()) BackstoryCard("\uD83D\uDD2E", "Prophecy", p, MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(10.dp).fillMaxWidth(),
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
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(abbr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                formatSigned(mod),
                style = MaterialTheme.typography.labelMedium,
                color = if (mod >= 0) RealmsTheme.colors.success else MaterialTheme.colorScheme.error
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
