package com.realmsoffate.game.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.realmsoffate.game.game.Feats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.realmsoffate.game.ui.theme.RealmsSpacing
import kotlinx.coroutines.delay

/**
 * Full-screen "LEVEL UP" celebration overlay. Auto-dismisses after ~2.4s or
 * on tap. Shown whenever GameViewModel.pendingLevelUpFlow emits a non-null
 * level number.
 */
@Composable
fun LevelUpOverlay(level: Int, statPoints: Int, onAssignStat: (String) -> Unit, onDismiss: () -> Unit) {
    val scale = remember { Animatable(0.4f) }

    LaunchedEffect(level) {
        scale.animateTo(1.12f, tween(280))
        scale.animateTo(1f, tween(180))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true, usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "LEVEL UP",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 8.sp),
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(RealmsSpacing.s))
                Box(
                    Modifier
                        .size(168.dp)
                        .scale(scale.value),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(168.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(MaterialTheme.colorScheme.tertiary, Color.Transparent)
                                )
                            )
                    )
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(3.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$level",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Spacer(Modifier.height(RealmsSpacing.l))
                Text(
                    "You feel stronger.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                Text(
                    "HP +, slots restored.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(RealmsSpacing.l))
                if (statPoints > 0) {
                    Text(
                        "$statPoints POINTS TO ASSIGN",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                        color = Color.White
                    )
                    Spacer(Modifier.height(RealmsSpacing.m))
                    val stats = listOf("STR", "DEX", "CON", "INT", "WIS", "CHA")
                    Column(verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                        stats.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                                row.forEach { stat ->
                                    FilledTonalButton(
                                        onClick = { onAssignStat(stat) },
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text(stat, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(RealmsSpacing.m))
                } else {
                    // Auto-dismiss when no points left
                    LaunchedEffect(Unit) { delay(1500); onDismiss() }
                }
            }
        }
    }
}

/**
 * "INITIATIVE" overlay — flashes when combat starts.
 * Dramatic entrance: the word scales up from 0 with a red glow, holds, then fades.
 * Auto-dismisses ~1.6s.
 */
@Composable
fun InitiativeOverlay(onDismiss: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(180))
        scale.animateTo(1.25f, tween(220))
        scale.animateTo(1f, tween(160))
        glow.animateTo(1f, tween(400))
        kotlinx.coroutines.delay(900)
        alpha.animateTo(0f, tween(400))
        onDismiss()
    }

    Dialog(
        onDismissRequest = { /* not user-dismissible — auto closes */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f * alpha.value)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.scale(scale.value),
                contentAlignment = Alignment.Center
            ) {
                // Red halo behind the text.
                Box(
                    Modifier
                        .size(360.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f * glow.value),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\u2694\uFE0F", fontSize = 54.sp)
                    Spacer(Modifier.height(RealmsSpacing.s))
                    Text(
                        "INITIATIVE",
                        style = MaterialTheme.typography.displayLarge.copy(
                            letterSpacing = 10.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.error.copy(alpha = alpha.value),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(RealmsSpacing.xs))
                    Text(
                        "ROLL FOR BLOOD",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp),
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = alpha.value)
                    )
                }
            }
        }
    }
}


/**
 * Feat selection overlay — shown at levels 4, 8, 12, 16, 20 instead of stat points.
 * Full-screen dialog with scrollable feat cards.
 */
@Composable
fun FeatSelectionOverlay(
    currentFeats: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val available = Feats.list.filter { it.name !in currentFeats }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f)
        ) {
            Column(Modifier.padding(RealmsSpacing.xl)) {
                Text(
                    "CHOOSE A FEAT",
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "A reward for reaching a milestone level.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(RealmsSpacing.m))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(RealmsSpacing.s),
                    modifier = Modifier.weight(1f)
                ) {
                    items(available) { feat ->
                        Surface(
                            onClick = { onSelect(feat.name) },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(RealmsSpacing.m), verticalAlignment = Alignment.CenterVertically) {
                                Text(feat.icon, fontSize = 28.sp)
                                Spacer(Modifier.width(RealmsSpacing.m))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        feat.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        feat.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
