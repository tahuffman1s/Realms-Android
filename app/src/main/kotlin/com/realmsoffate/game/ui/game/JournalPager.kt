package com.realmsoffate.game.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.components.PanelTab
import com.realmsoffate.game.ui.components.PanelTabRow
import com.realmsoffate.game.ui.panels.*
import kotlinx.coroutines.launch

private enum class JournalTab(val label: String) {
    Quests("Quests"),
    Npcs("NPCs"),
    Lore("Lore")
}

@Composable
internal fun JournalPager(
    state: GameUiState,
    onAbandon: (String) -> Unit,
    onLoreSeen: () -> Unit,
    focusNpc: String? = null
) {
    val tabs = JournalTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val historyCount = state.worldLore?.history?.size ?: 0
    val hasUnseenLore = historyCount > state.lastSeenLoreSize

    LaunchedEffect(pagerState.currentPage, historyCount) {
        if (tabs[pagerState.currentPage] == JournalTab.Lore && hasUnseenLore) {
            onLoreSeen()
        }
    }

    Column(Modifier.fillMaxSize()) {
        PanelTabRow(
            tabs = tabs.map { t ->
                PanelTab(
                    label = t.label,
                    badge = t == JournalTab.Lore && hasUnseenLore
                )
            },
            selectedIndex = pagerState.currentPage,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
        )
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val mod = if (page != pagerState.currentPage) {
                Modifier.fillMaxSize().clearAndSetSemantics {}
            } else {
                Modifier.fillMaxSize()
            }
            Column(mod) {
                when (tabs[page]) {
                    JournalTab.Quests -> QuestsContent(state, onAbandon)
                    JournalTab.Npcs -> JournalContent(state, focusNpc)
                    JournalTab.Lore -> LoreContent(state)
                }
            }
        }
    }
}
