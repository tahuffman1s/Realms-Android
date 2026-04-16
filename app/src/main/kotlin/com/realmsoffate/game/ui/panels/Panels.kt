@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
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
import com.realmsoffate.game.game.Spells
import com.realmsoffate.game.ui.theme.RealmsTheme

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
fun SpellsPanel(
    state: GameUiState,
    onClose: () -> Unit,
    onHotbar: (Int, String?) -> Unit = { _, _ -> },
    onCast: (spell: com.realmsoffate.game.game.Spell) -> Unit = {}
) {
    val ch = state.character ?: return
    var selectedName by remember(ch) { mutableStateOf<String?>(null) }
    PanelSheet(
        "\u2728  Spells",
        subtitle = if (ch.knownSpells.isEmpty()) null else "${ch.knownSpells.size} known",
        onClose = onClose
    ) {
        if (ch.knownSpells.isEmpty()) {
            EmptyState("\u2728", "You know no spells.")
            return@PanelSheet
        }
        // Slot diamonds header
        SpellSlotStrip(ch)
        Spacer(Modifier.height(6.dp))

        val knownSpells = ch.knownSpells.mapNotNull { Spells.find(it) }.sortedBy { it.level }
        val grouped = knownSpells.groupBy { it.level }

        LazyColumn(
            Modifier.padding(horizontal = 14.dp).heightIn(max = 540.dp),
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
}

@Composable
private fun SpellSlotStrip(ch: com.realmsoffate.game.data.Character) {
    val realms = RealmsTheme.colors
    val levels = ch.maxSpellSlots.keys.sorted()
    if (levels.isEmpty()) return
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
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
                        SlotDiamond(filled = i < cur, color = realms.info)
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
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.dp, border, RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val realms = RealmsTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(spell.desc, style = MaterialTheme.typography.bodyMedium)
            if (spell.damage != "-") {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = realms.fumbleRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "\uD83D\uDDE1\uFE0F ${spell.damage}",
                        style = MaterialTheme.typography.labelMedium,
                        color = realms.fumbleRed,
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
                shape = RoundedCornerShape(10.dp)
            ) { Text(if (canCast) "Cast Now" else "No Slots") }
        }
    }
}

// ----------------- STATS -----------------

@Composable
fun StatsPanel(state: GameUiState, onClose: () -> Unit) {
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

