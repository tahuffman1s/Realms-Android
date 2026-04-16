package com.realmsoffate.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

@Composable
internal fun WealthBars(wealth: Int) {
    val realms = RealmsTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xxs)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .width(14.dp)
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (i < wealth) realms.goldAccent
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
            )
        }
    }
}
