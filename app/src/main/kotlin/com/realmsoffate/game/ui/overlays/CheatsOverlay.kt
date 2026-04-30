package com.realmsoffate.game.ui.overlays

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.ui.theme.RealmsSpacing

private enum class CheatId { UNNATURAL_20, LOSER, INFINITE_GOLD, OVERPREPARED }

private data class CheatTile(
    val id: CheatId,
    val emoji: String,
    val name: String,
    val teaser: String,
    val description: String
)

private val TILES = listOf(
    CheatTile(
        CheatId.UNNATURAL_20, "🎯", "Unnatural 20", "Every roll is a nat 20.",
        "Every d20 roll lands on 20. Skill checks, initiative, haggles — all triumph. Disables Loser."
    ),
    CheatTile(
        CheatId.LOSER, "💩", "Loser", "Every roll is a 1.",
        "Every d20 roll is a 1. Nothing works. Embrace calamity. Disables Unnatural 20."
    ),
    CheatTile(
        CheatId.INFINITE_GOLD, "💰", "1%", "Infinite gold.",
        "Your gold is bottomless. Spend freely; the coffers refill each turn. Displays as ∞."
    ),
    CheatTile(
        CheatId.OVERPREPARED, "📚", "Overprepared", "Instant L20.",
        "Instantly level your character to 20. Stat points and feats auto-assigned. One-shot."
    )
)

@Composable
fun CheatsOverlay(
    unnaturalTwenty: Boolean,
    loser: Boolean,
    infiniteGold: Boolean,
    characterLevel: Int,
    onToggleUnnaturalTwenty: (Boolean) -> Unit,
    onToggleLoser: (Boolean) -> Unit,
    onToggleInfiniteGold: (Boolean) -> Unit,
    onApplyOverprepared: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf<CheatId?>(CheatId.UNNATURAL_20) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("🃏  Cheats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "The dungeon master looks away...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                    Tile(
                        tile = TILES[0],
                        active = unnaturalTwenty,
                        expanded = expanded == CheatId.UNNATURAL_20,
                        onTap = { expanded = if (expanded == CheatId.UNNATURAL_20) null else CheatId.UNNATURAL_20 },
                        modifier = Modifier.weight(1f)
                    )
                    Tile(
                        tile = TILES[1],
                        active = loser,
                        expanded = expanded == CheatId.LOSER,
                        onTap = { expanded = if (expanded == CheatId.LOSER) null else CheatId.LOSER },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(RealmsSpacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                    Tile(
                        tile = TILES[2],
                        active = infiniteGold,
                        expanded = expanded == CheatId.INFINITE_GOLD,
                        onTap = { expanded = if (expanded == CheatId.INFINITE_GOLD) null else CheatId.INFINITE_GOLD },
                        modifier = Modifier.weight(1f)
                    )
                    Tile(
                        tile = TILES[3],
                        active = false,
                        expanded = expanded == CheatId.OVERPREPARED,
                        onTap = { expanded = if (expanded == CheatId.OVERPREPARED) null else CheatId.OVERPREPARED },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(RealmsSpacing.m))
                // Fixed-height container: AlertDialog never reflows mid-fade, so swapping
                // tiles cross-fades cleanly without the height jump that read as a flash.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp)
                ) {
                    AnimatedContent(
                        targetState = expanded,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "cheat-description"
                    ) { targetId ->
                        val tile = remember(targetId) { TILES.firstOrNull { it.id == targetId } }
                        if (tile != null) {
                            Column {
                                Text(tile.description, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(RealmsSpacing.s))
                                CheatActionButton(
                                    tile = tile,
                                    unnaturalTwenty = unnaturalTwenty,
                                    loser = loser,
                                    infiniteGold = infiniteGold,
                                    characterLevel = characterLevel,
                                    onToggleUnnaturalTwenty = onToggleUnnaturalTwenty,
                                    onToggleLoser = onToggleLoser,
                                    onToggleInfiniteGold = onToggleInfiniteGold,
                                    onApplyOverprepared = { onApplyOverprepared(); expanded = null }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun Tile(
    tile: CheatTile,
    active: Boolean,
    expanded: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        active -> MaterialTheme.colorScheme.primary
        expanded -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        onClick = onTap,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .heightIn(min = 120.dp)
            .border(if (active) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(RealmsSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(tile.emoji, fontSize = 40.sp)
            Spacer(Modifier.height(RealmsSpacing.xs))
            Text(
                tile.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                tile.teaser,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (active) {
                Spacer(Modifier.height(RealmsSpacing.xs))
                Text(
                    "✓ ON",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CheatActionButton(
    tile: CheatTile,
    unnaturalTwenty: Boolean,
    loser: Boolean,
    infiniteGold: Boolean,
    characterLevel: Int,
    onToggleUnnaturalTwenty: (Boolean) -> Unit,
    onToggleLoser: (Boolean) -> Unit,
    onToggleInfiniteGold: (Boolean) -> Unit,
    onApplyOverprepared: () -> Unit
) {
    when (tile.id) {
        CheatId.UNNATURAL_20 -> {
            val label = remember(unnaturalTwenty) { if (unnaturalTwenty) "Turn Off" else "Turn On" }
            ActionButton(label = label, onClick = { onToggleUnnaturalTwenty(!unnaturalTwenty) })
        }
        CheatId.LOSER -> {
            val label = remember(loser) { if (loser) "Turn Off" else "Turn On" }
            ActionButton(label = label, onClick = { onToggleLoser(!loser) })
        }
        CheatId.INFINITE_GOLD -> {
            val label = remember(infiniteGold) { if (infiniteGold) "Turn Off" else "Turn On" }
            ActionButton(label = label, onClick = { onToggleInfiniteGold(!infiniteGold) })
        }
        CheatId.OVERPREPARED -> {
            val maxed = characterLevel >= 20
            val label = remember(maxed) { if (maxed) "Already maxed" else "Apply" }
            ActionButton(label = label, enabled = !maxed, onClick = onApplyOverprepared)
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) { Text(label) }
}
