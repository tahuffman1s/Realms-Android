package com.realmsoffate.game.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.realmsoffate.game.BuildConfig
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
    onDebugDump: () -> Unit = {},
    onReturnToTitle: () -> Unit = {},
    onDisableCheats: () -> Unit = {},
    cheatsEnabled: Boolean = false,
    balanceUsd: String? = null,
    onRefreshBalance: () -> Unit = {}
) {
    var showDisableCheatsDialog by remember { mutableStateOf(false) }
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

            Spacer(Modifier.height(RealmsSpacing.m))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DeepSeek balance", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    balanceUsd?.let { "$$it" } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onRefreshBalance) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh balance")
                }
            }
            Spacer(Modifier.height(RealmsSpacing.s))
            HorizontalDivider(Modifier.padding(vertical = RealmsSpacing.m))

            // Utility actions
            Text("Actions", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = RealmsSpacing.s))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                ActionIcon(
                    icon = Icons.Filled.FileDownload,
                    label = "Export Save",
                    onClick = { onExportSave(); onClose() }
                )
                ActionIcon(
                    icon = Icons.Filled.BugReport,
                    label = "Debug Dump",
                    onClick = { onDebugDump(); onClose() }
                )
                ActionIcon(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Return to Title",
                    onClick = { onReturnToTitle() },
                    tint = MaterialTheme.colorScheme.error
                )
                if (cheatsEnabled) {
                    ActionIcon(
                        iconText = "🃏",
                        label = "Disable Cheats",
                        onClick = { showDisableCheatsDialog = true },
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(RealmsSpacing.l))
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.navigationBarsPadding().height(RealmsSpacing.m))
        }
    }

    if (showDisableCheatsDialog) {
        AlertDialog(
            onDismissRequest = { showDisableCheatsDialog = false },
            title = { Text("Disable Cheats?") },
            text = { Text("All cheat toggles will be cleared. Type the code in chat to re-enable.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDisableCheats()
                        showDisableCheatsDialog = false
                        onClose()
                    }
                ) {
                    Text("Disable", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableCheatsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActionIcon(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconText: String? = null,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 72.dp)
    ) {
        OutlinedIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = tint),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.5f))
        ) {
            when {
                icon != null -> Icon(icon, contentDescription = label)
                iconText != null -> Text(iconText, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(RealmsSpacing.xxs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center
        )
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

