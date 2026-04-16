package com.realmsoffate.game.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun RealmsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    outlined: Boolean = false,
    accentColor: Color? = null,
    selected: Boolean = false,
    elevation: Dp = 0.dp,
    contentPadding: Dp = RealmsSpacing.m,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundColor = when {
        selected && accentColor != null -> accentColor.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val border = when {
        outlined && accentColor != null && selected -> BorderStroke(1.dp, accentColor)
        outlined && accentColor != null -> BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
        else -> null
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            tonalElevation = elevation,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            tonalElevation = elevation,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
