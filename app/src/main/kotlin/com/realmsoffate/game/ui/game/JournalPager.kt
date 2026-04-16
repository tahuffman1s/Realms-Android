package com.realmsoffate.game.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameUiState
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
    focusNpc: String? = null
) {
    val tabs = JournalTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            tab.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 1.sp
                        )
                    }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (tabs[page]) {
                JournalTab.Quests -> QuestsContent(state, onAbandon)
                JournalTab.Npcs -> JournalContent(state, focusNpc)
                JournalTab.Lore -> LoreContent(state)
            }
        }
    }
}
