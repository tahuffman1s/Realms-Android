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
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.Spell
import com.realmsoffate.game.ui.panels.*
import kotlinx.coroutines.launch

private enum class CharacterTab(val label: String) {
    Stats("Stats"),
    Inventory("Inventory"),
    Spells("Spells"),
    Party("Party"),
    Currency("Currency")
}

@Composable
internal fun CharacterPager(
    state: GameUiState,
    onEquip: (Item) -> Unit,
    onDismiss: (String) -> Unit,
    onExchange: (String, String, Int) -> Unit,
    onHotbar: (Int, String?) -> Unit,
    onCast: (Spell) -> Unit
) {
    val tabs = CharacterTab.entries
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
                CharacterTab.Stats -> StatsContent(state)
                CharacterTab.Inventory -> InventoryContent(state, onEquip)
                CharacterTab.Spells -> SpellsContent(state, onHotbar, onCast)
                CharacterTab.Party -> PartyContent(state, onDismiss)
                CharacterTab.Currency -> CurrencyContent(state, onExchange)
            }
        }
    }
}
