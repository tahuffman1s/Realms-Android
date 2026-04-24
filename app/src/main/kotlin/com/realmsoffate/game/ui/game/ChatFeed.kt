package com.realmsoffate.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun ChatFeed(
    state: GameUiState,
    listState: LazyListState,
    onNpcReply: (String) -> Unit,
    onAttackNpc: (String) -> Unit,
    onOpenJournal: (String) -> Unit,
    onOpenStats: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Fold check-result System lines into their preceding Narration:
    // indices of System messages that should NOT render their own row because
    // their ✓/✗ text has been attached to the prior Narration's stat strip.
    val foldedSystemIndices: Set<Int> = remember(state.messages) {
        buildSet {
            state.messages.forEachIndexed { i, m ->
                if (m is DisplayMessage.System && isCheckLine(m.text)) {
                    val prev = state.messages.getOrNull(i - 1)
                    if (prev is DisplayMessage.Narration) add(i)
                }
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.m),
        verticalArrangement = Arrangement.spacedBy(RealmsSpacing.m)
    ) {
        // Empty state — shown when there are no messages and nothing is generating
        if (state.messages.isEmpty() && !state.isGenerating) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    val ch = state.character
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            ch?.name ?: "Adventurer",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (ch != null) {
                            Text(
                                "${ch.race} ${ch.cls}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(RealmsSpacing.l))
                        Text(
                            "Your story begins...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }

        itemsIndexed(state.messages) { idx, msg ->
            if (idx in foldedSystemIndices) return@itemsIndexed
            Column {
                // Silent hairline gap before each narration block (except the very first message)
                if (msg is DisplayMessage.Narration && idx > 0) {
                    TurnGap()
                }
                when (msg) {
                    is DisplayMessage.Player -> PlayerBubble(msg.text, state.character?.name)
                    is DisplayMessage.Narration -> {
                        // Look ahead: if the next message is a foldable check System,
                        // pass its text into this narration's stat strip.
                        val nextIdx = idx + 1
                        val checkText = if (nextIdx in foldedSystemIndices) {
                            (state.messages[nextIdx] as? DisplayMessage.System)?.text
                        } else null
                        NarrationBlock(
                            msg.text, state.character?.name, msg, msg.segments,
                            npcLog = state.npcLog,
                            isLatestTurn = idx == state.messages.lastIndex
                                || (idx == state.messages.size - 2 && state.messages.lastOrNull() is DisplayMessage.System),
                            checkText = checkText,
                            onNpcTap = { /* future use */ },
                            onNpcReply = onNpcReply,
                            onAttackNpc = onAttackNpc,
                            onOpenJournal = onOpenJournal,
                            onOpenStats = onOpenStats
                        )
                    }
                    is DisplayMessage.Event -> EventCard(msg.icon, msg.title, msg.text)
                    is DisplayMessage.System -> SystemLine(msg.text)
                }
            }
        }

        if (state.isGenerating) {
            item { NarratorThinking() }
        }

        // Error card — M3 error-container banner with explicit dismiss
        state.error?.let { err ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(
                            start = RealmsSpacing.l,
                            top = RealmsSpacing.m,
                            bottom = RealmsSpacing.m,
                            end = RealmsSpacing.xs
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
                    ) {
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = onClearError) {
                            Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}

private fun isCheckLine(text: String): Boolean {
    val t = text.trimStart()
    return t.startsWith("✓") || t.startsWith("✗")
}

@Composable
private fun TurnGap() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
    }
}
