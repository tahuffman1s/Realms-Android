package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun SectionHeader(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(vertical = RealmsSpacing.xs)
    )
}
