package com.realmsoffate.game.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun ChatFeed(
    state: GameUiState,
    listState: LazyListState,
    bookmarks: List<String>,
    onToggleBookmark: (String) -> Unit,
    onNpcReply: (String) -> Unit,
    onNpcReaction: (String, String, String) -> Unit,
    onAttackNpc: (String) -> Unit,
    onOpenJournal: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenShop: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.m),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
            when (msg) {
                is DisplayMessage.Player -> PlayerBubble(msg.text, state.character?.name)
                is DisplayMessage.Narration -> {
                    NarrationBlock(
                        msg.text, state.character?.name, msg, msg.segments,
                        npcLog = state.npcLog,
                        isLatestTurn = idx == state.messages.lastIndex || (idx == state.messages.size - 2 && state.messages.lastOrNull() is DisplayMessage.System),
                        bookmarks = state.bookmarks,
                        onToggleBookmark = onToggleBookmark,
                        onNpcTap = { /* future use */ },
                        onNpcReply = onNpcReply,
                        onNpcReaction = onNpcReaction,
                        onAttackNpc = onAttackNpc,
                        onOpenJournal = onOpenJournal,
                        onOpenStats = onOpenStats
                    )
                }
                is DisplayMessage.Event -> EventCard(msg.icon, msg.title, msg.text)
                is DisplayMessage.System -> SystemLine(msg.text)
            }
        }

        if (state.isGenerating) {
            item { NarratorThinking() }
        }

        if (state.availableMerchants.isNotEmpty() && !state.isGenerating) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
                ) {
                    state.availableMerchants.forEach { merchant ->
                        Surface(
                            onClick = { onOpenShop(merchant) },
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                Modifier.padding(horizontal = RealmsSpacing.m, vertical = RealmsSpacing.s),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Store, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(merchant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Error card with explicit dismiss button (no auto-dismiss)
        state.error?.let { err ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, top = RealmsSpacing.s, bottom = RealmsSpacing.s, end = RealmsSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
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
