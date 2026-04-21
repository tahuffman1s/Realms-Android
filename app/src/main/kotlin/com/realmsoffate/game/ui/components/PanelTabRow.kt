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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.ui.theme.RealmsSpacing

/** One segment in a [PanelTabRow]. */
internal data class PanelTab(
    val label: String,
    val icon: String? = null
)

/**
 * Material Design 3 **secondary tabs** that always stretch to fill the available width,
 * splitting evenly between all segments. Selection is shown with the tab indicator line.
 *
 * The default [HorizontalDivider] under M3 tab rows is omitted (`divider = {}`).
 */
@Composable
internal fun PanelTabRow(
    tabs: List<PanelTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") horizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = RealmsSpacing.xs
) {
    val safeIndex = selectedIndex.coerceIn(0, maxOf(tabs.lastIndex, 0))
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = verticalPadding)
    ) {
        SecondaryTabRow(
            selectedTabIndex = safeIndex,
            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun PanelTabLabel(tab: PanelTab) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (!tab.icon.isNullOrBlank()) {
            Text(
                tab.icon,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(RealmsSpacing.xs))
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.25.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
