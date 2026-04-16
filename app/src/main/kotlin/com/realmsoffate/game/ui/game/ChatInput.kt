package com.realmsoffate.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.overlays.TargetPromptSpec
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun GameInputBar(
    state: GameUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onTargetPrompt: (TargetPromptSpec) -> Unit,
    onSpellsOpen: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth()) {
        // ---- Action bar — attack/heavy/spells chips, visible during combat ----
        state.character?.let { ch ->
            if (state.currentScene == "battle") {
                val recentTargets = if (state.combat != null) {
                    state.combat.order
                        .filter { !it.isPlayer }
                        .map { it.name }
                        .distinct()
                        .take(8)
                } else {
                    val currentLocName = state.worldMap?.locations?.getOrNull(state.currentLoc)?.name.orEmpty()
                    val nearbyNpcs = state.npcLog
                        .filter { it.lastLocation == currentLocName }
                        .sortedByDescending { it.lastSeenTurn }
                        .take(6)
                        .map { it.name }
                    (nearbyNpcs + state.party.map { it.name }).distinct().take(8)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = RealmsElevation.low,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = RealmsSpacing.s),
                        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionChip(Icons.Filled.GpsFixed, "Attack", onClick = {
                            onTargetPrompt(
                                TargetPromptSpec(
                                    title = "Light Attack",
                                    verb = "I attack",
                                    recentTargets = recentTargets
                                )
                            )
                        })
                        ActionChip(Icons.Filled.Bolt, "Heavy", onClick = {
                            onTargetPrompt(
                                TargetPromptSpec(
                                    title = "Heavy Attack",
                                    verb = "I strike with a heavy blow at",
                                    recentTargets = recentTargets
                                )
                            )
                        })
                        ActionChip(Icons.Filled.AutoAwesome, "Spells", onClick = { onSpellsOpen() })
                    }
                }
            }
        }
        // ---- Input row (its own surface so the divider between hotbar +
        //      input is visually obvious on mobile) ----
        Surface(
            tonalElevation = RealmsElevation.low,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = RealmsSpacing.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !state.isGenerating,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (state.isGenerating) "Narrator is writing..." else "What do you do?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    shape = MaterialTheme.shapes.large,
                    singleLine = false,
                    maxLines = 5
                )
                Spacer(Modifier.width(RealmsSpacing.s))
                FilledIconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !state.isGenerating,
                    modifier = Modifier.size(52.dp),  // bigger send target
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }
}

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.height(46.dp)  // bigger touch target
    ) {
        Row(
            Modifier.padding(horizontal = RealmsSpacing.m, vertical = RealmsSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs)
        ) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * A spell is self-castable when its description targets the caster — heals,
 * buffs, class resources, and the canonical list of martial self-effects.
 */
internal fun isSelfCastable(spell: com.realmsoffate.game.game.Spell): Boolean {
    val name = spell.name.lowercase()
    if (spell.school == "Martial") return true
    if ("cure wounds" in name || "healing word" in name || "lay on hands" in name) return true
    if ("shield" == name || "true strike" == name || "misty step" in name) return true
    if ("bless" == name) return true
    if ("hunter's mark" in name) return false // targets enemy
    // Default: heals/buffs that mention "yourself" / "you" / "a creature" in desc.
    val desc = spell.desc.lowercase()
    return "yourself" in desc || "you gain" in desc || "you heal" in desc
}

@Composable
private fun SpellChip(slot: Int, name: String, icon: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.height(46.dp)
    ) {
        Row(
            Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs)
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$slot",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(icon, fontSize = 16.sp)
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Handles /commands. Returns true if the input was consumed.
 */
internal fun handleSlashCommand(
    input: String,
    vm: GameViewModel,
    openPanel: (Panel) -> Unit
): Boolean {
    val cmd = input.trim()
    if (!cmd.startsWith("/")) return false
    val tokens = cmd.removePrefix("/").lowercase().trim().split(Regex("\\s+"))
    val root = tokens.firstOrNull() ?: return true
    when (root) {
        "help" -> vm.postSystemMessage(
            "Commands: /save /map /inv /stats /spells /lore /journal /currency /party /quest /rest /shortrest /menu /help"
        )
        "save" -> { vm.saveToSlot(); vm.postSystemMessage("Saved.") }
        "map" -> openPanel(Panel.Map)
        "inv", "bag", "items" -> openPanel(Panel.Inventory)
        "stats", "sheet" -> openPanel(Panel.Stats)
        "spells" -> openPanel(Panel.Spells)
        "lore" -> openPanel(Panel.Lore)
        "journal", "npcs" -> openPanel(Panel.Journal)
        "currency", "coin", "money" -> openPanel(Panel.Currency)
        "party" -> openPanel(Panel.Party)
        "quest", "quests" -> openPanel(Panel.Quests)
        "rest", "longrest" -> vm.longRest()
        "shortrest" -> vm.shortRest()
        "menu", "title" -> vm.returnToTitle()
        else -> vm.postSystemMessage("Unknown command: /$root. Type /help.")
    }
    return true
}
