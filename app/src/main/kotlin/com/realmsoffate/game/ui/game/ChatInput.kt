package com.realmsoffate.game.ui.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.overlays.TargetPromptSpec
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun GameInputBar(
    state: GameUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onTargetPrompt: (TargetPromptSpec) -> Unit,
    onSpellsOpen: () -> Unit = {},
    hasChoices: Boolean = false,
    onChoicesOpen: () -> Unit = {}
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
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s),
                        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CombatAssistChip(Icons.Filled.GpsFixed, "Attack", onClick = {
                            onTargetPrompt(
                                TargetPromptSpec(
                                    title = "Light Attack",
                                    verb = "I attack",
                                    recentTargets = recentTargets
                                )
                            )
                        })
                        CombatAssistChip(Icons.Filled.Bolt, "Heavy", onClick = {
                            onTargetPrompt(
                                TargetPromptSpec(
                                    title = "Heavy Attack",
                                    verb = "I strike with a heavy blow at",
                                    recentTargets = recentTargets
                                )
                            )
                        })
                        CombatAssistChip(Icons.Filled.AutoAwesome, "Spells", onClick = { onSpellsOpen() })
                    }
                }
            }
        }
        // ---- Input row (its own surface so the divider between hotbar +
        //      input is visually obvious on mobile) ----
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s),
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
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                if (hasChoices) {
                    Spacer(Modifier.width(RealmsSpacing.s))
                    FilledIconButton(
                        onClick = onChoicesOpen,
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) { Icon(Icons.AutoMirrored.Filled.List, "Choices") }
                }
                Spacer(Modifier.width(RealmsSpacing.s))
                FilledIconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !state.isGenerating,
                    modifier = Modifier.size(52.dp),
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
private fun CombatAssistChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(label, fontWeight = FontWeight.Medium)
        },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        border = AssistChipDefaults.assistChipBorder(enabled = true),
        modifier = Modifier.heightIn(min = 48.dp)
    )
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

