package com.realmsoffate.game.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.GraveyardEntry
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.theme.RealmsTheme

/**
 * BitLife-style death screen — REST IN PEACE header, character summary, then
 * a scrollable "LIFE STORY" vertical timeline with color-coded dots per
 * event category. Two bottom actions: back to Title, or straight to New
 * Character.
 */
@Composable
fun DeathScreen(vm: GameViewModel) {
    val death by vm.lastDeath.collectAsState()
    val entry = death ?: return run {
        // Safety fallback — shouldn't happen, but kick back to title if so.
        vm.returnToTitle()
    }

    val realms = RealmsTheme.colors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.returnToTitle() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("MAIN MENU", style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = { vm.goToCharacterCreation() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("NEW HERO", style = MaterialTheme.typography.labelLarge) }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.statusBarsPadding().height(28.dp))
            Text(
                "REST IN PEACE",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 6.sp),
                color = realms.fumbleRed
            )
            Spacer(Modifier.height(4.dp))
            Text(
                entry.characterName,
                style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                "L${entry.level} ${entry.race} ${entry.cls}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "of ${entry.worldName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill("TURNS", entry.turns.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatPill("XP", entry.xp.toString(), realms.goldAccent, Modifier.weight(1f))
                StatPill("GOLD", "${entry.gold}g", realms.goldAccent, Modifier.weight(1f))
                StatPill("MORAL", formatSigned(entry.morality), moralityColor(entry.morality, realms), Modifier.weight(1f))
            }

            if (entry.mutations.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("WORLD CONDITIONS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        entry.mutations.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                entry.causeOfDeath,
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = realms.fumbleRed,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "— LIFE STORY —",
                style = MaterialTheme.typography.labelLarge,
                color = realms.goldAccent
            )
            Spacer(Modifier.height(12.dp))

            TimelineColumn(entry.timeline)

            if (entry.companions.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "FELLOW TRAVELLERS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                entry.companions.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }

            entry.backstoryText?.let {
                Spacer(Modifier.height(18.dp))
                Text("UNFINISHED STORY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
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
private fun TimelineColumn(entries: List<TimelineEntry>) {
    val realms = RealmsTheme.colors
    Column(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { idx, t ->
            val color = when (t.category) {
                "birth" -> realms.success
                "levelup" -> realms.goldAccent
                "quest" -> MaterialTheme.colorScheme.secondary
                "travel" -> realms.info
                "event" -> realms.warning
                "death" -> realms.fumbleRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.width(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    if (idx < entries.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(color.copy(alpha = 0.4f))
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(
                        "Turn ${t.turn} · ${t.category.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                    Text(t.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun moralityColor(n: Int, r: com.realmsoffate.game.ui.theme.RealmsExtendedColors): Color = when {
    n >= 30 -> r.success
    n <= -30 -> r.fumbleRed
    else -> Color.Unspecified.takeIf { false } ?: r.info
}

private fun formatSigned(n: Int) = if (n >= 0) "+$n" else n.toString()
