package com.realmsoffate.game.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.Spell
import com.realmsoffate.game.ui.components.PanelTab
import com.realmsoffate.game.ui.components.PanelTabRow
import com.realmsoffate.game.ui.panels.*
import kotlinx.coroutines.launch

private enum class CharacterTab(val label: String) {
    Stats("Stats"),
    Inventory("Inventory"),
    Spells("Spells"),
    Party("Party")
}

@Composable
internal fun CharacterPager(
    state: GameUiState,
    onEquip: (Item) -> Unit,
    onDismiss: (String) -> Unit,
    onHotbar: (Int, String?) -> Unit,
    onCast: (Spell) -> Unit
) {
    val tabs = CharacterTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        PanelTabRow(
            tabs = tabs.map { PanelTab(it.label) },
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
                    CharacterTab.Stats -> StatsContent(state)
                    CharacterTab.Inventory -> InventoryContent(state, onEquip)
                    CharacterTab.Spells -> SpellsContent(state, onHotbar, onCast)
                    CharacterTab.Party -> PartyContent(state, onDismiss)
                }
            }
        }
    }
}
