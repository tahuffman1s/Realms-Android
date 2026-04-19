package com.realmsoffate.game.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.Choice
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing

// ============================================================
// SETTINGS PANEL — bubble size + text size
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPanel(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onClose: () -> Unit,
    onExportSave: () -> Unit = {},
    onShortRest: () -> Unit = {},
    onLongRest: () -> Unit = {},
    onDebugDump: () -> Unit = {},
    onReturnToTitle: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = RealmsSpacing.s)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(RealmsSpacing.s))
                Text("SETTINGS", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(RealmsSpacing.xl))

            Text(
                "FONT SIZE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(RealmsSpacing.xs))
            Text(
                "Adjusts text size across all chat bubbles",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(RealmsSpacing.l))

            // Preview text at current scale
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "The tavern door creaks open. A cold wind follows you inside.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (15f * fontScale).sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(Modifier.height(RealmsSpacing.l))

            // Slider
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(RealmsSpacing.s))
                Slider(
                    value = fontScale,
                    onValueChange = { onFontScaleChange(it) },
                    valueRange = 0.7f..1.6f,
                    steps = 0,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(RealmsSpacing.s))
                Text(
                    "A",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "${"%.0f".format(fontScale * 100)}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(Modifier.padding(vertical = RealmsSpacing.m))

            // Utility actions
            Text("Actions", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = RealmsSpacing.s))

            OutlinedButton(
                onClick = { onExportSave(); onClose() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export Save") }

            Spacer(Modifier.height(RealmsSpacing.s))

            OutlinedButton(
                onClick = { onShortRest() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Short Rest") }

            Spacer(Modifier.height(RealmsSpacing.s))

            OutlinedButton(
                onClick = { onLongRest() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Long Rest") }

            Spacer(Modifier.height(RealmsSpacing.s))

            OutlinedButton(
                onClick = { onDebugDump(); onClose() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Debug Dump") }

            Spacer(Modifier.height(RealmsSpacing.l))

            OutlinedButton(
                onClick = { onReturnToTitle() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Return to Title") }

            Spacer(Modifier.navigationBarsPadding().height(RealmsSpacing.m))
        }
    }
}

// ============================================================
// CHOICES SHEET — ModalBottomSheet listing AI choices
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoicesSheet(
    choices: List<Choice>,
    onPick: (Choice) -> Unit,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s)) {
            Text("YOUR CHOICES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            choices.forEach { c -> ChoiceTile(c, onClick = { onPick(c) }) ; Spacer(Modifier.height(RealmsSpacing.s)) }
            Spacer(Modifier.navigationBarsPadding().height(RealmsSpacing.m))
        }
    }
}

