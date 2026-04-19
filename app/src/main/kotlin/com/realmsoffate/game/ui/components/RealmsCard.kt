package com.realmsoffate.game.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.ui.theme.RealmsSpacing

/**
 * Opinionated card wrapper using Material 3 [Card], [ElevatedCard], and [OutlinedCard]
 * ([Filled](https://m3.material.io/components/cards/overview)) so elevation and containers
 * follow theme tokens.
 */
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
    val containerColor = when {
        selected && accentColor != null -> accentColor.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val border = when {
        outlined && accentColor != null && selected -> BorderStroke(1.dp, accentColor)
        outlined && accentColor != null -> BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
        else -> null
    }
    val padded: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding)) {
            content()
        }
    }

    when {
        outlined && border != null -> {
            if (onClick != null) {
                OutlinedCard(
                    onClick = onClick,
                    modifier = modifier,
                    shape = shape,
                    border = border,
                    colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
                ) { padded() }
            } else {
                OutlinedCard(
                    modifier = modifier,
                    shape = shape,
                    border = border,
                    colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
                ) { padded() }
            }
        }
        onClick != null && elevation > 0.dp -> {
            ElevatedCard(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
            ) { padded() }
        }
        onClick != null -> {
            Card(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) { padded() }
        }
        elevation > 0.dp -> {
            ElevatedCard(
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
            ) { padded() }
        }
        else -> {
            Card(
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) { padded() }
        }
    }
}
