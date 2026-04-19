@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.ui.theme.RealmsSpacing

/** One segment in a [PanelTabRow]. */
internal data class PanelTab(
    val label: String,
    val icon: String? = null
)

/**
 * Material Design 3 **secondary tabs** — selection shown with the tab indicator line, not pill chips.
 * Matches [Tabs (Material 3)](https://m3.material.io/components/tabs/overview): use secondary tabs
 * inside a content region for further categorization.
 *
 * Uses [SecondaryScrollableTabRow] when there are five or more tabs or when combined label length
 * suggests overflow; otherwise [SecondaryTabRow] for an even split.
 *
 * The default [HorizontalDivider] under M3 tab rows is omitted (`divider = {}`).
 *
 * Vertical spacing is applied only to the **bottom** so the tab strip sits flush under the
 * game top bar (no empty band between chrome and tabs).
 */
@Composable
internal fun PanelTabRow(
    tabs: List<PanelTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = RealmsSpacing.l,
    verticalPadding: Dp = RealmsSpacing.xs
) {
    val safeIndex = selectedIndex.coerceIn(0, maxOf(tabs.lastIndex, 0))
    val scrollable = tabs.size >= 5 ||
        tabs.any { (it.icon.orEmpty().length + it.label.length) > 22 }

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = verticalPadding)
    ) {
        if (scrollable) {
            SecondaryScrollableTabRow(
                selectedTabIndex = safeIndex,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = horizontalPadding,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = {}
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = safeIndex == index,
                        onClick = { onSelect(index) },
                        text = { PanelTabLabel(tab) }
                    )
                }
            }
        } else {
            SecondaryTabRow(
                selectedTabIndex = safeIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = {}
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = safeIndex == index,
                        onClick = { onSelect(index) },
                        text = { PanelTabLabel(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelTabLabel(tab: PanelTab) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (!tab.icon.isNullOrBlank()) {
            Text(tab.icon, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
        }
        Text(tab.label, style = MaterialTheme.typography.labelLarge)
    }
}
