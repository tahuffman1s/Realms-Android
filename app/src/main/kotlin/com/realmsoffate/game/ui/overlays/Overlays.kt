package com.realmsoffate.game.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.realmsoffate.game.game.Feats
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.realmsoffate.game.ui.theme.RealmsTheme
import kotlinx.coroutines.delay

/**
 * Full-screen "LEVEL UP" celebration overlay. Auto-dismisses after ~2.4s or
 * on tap. Shown whenever GameViewModel.pendingLevelUpFlow emits a non-null
 * level number.
 */
@Composable
fun LevelUpOverlay(level: Int, statPoints: Int, onAssignStat: (String) -> Unit, onDismiss: () -> Unit) {
    val realms = RealmsTheme.colors
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
                .background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "LEVEL UP",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 8.sp),
                    color = realms.goldAccent
                )
                Spacer(Modifier.height(8.dp))
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
                                    listOf(realms.goldAccent.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(3.dp, realms.goldAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$level",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = realms.goldAccent
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "You feel stronger.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    "HP +, slots restored.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                if (statPoints > 0) {
                    Text(
                        "$statPoints POINTS TO ASSIGN",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                        color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))
                    val stats = listOf("STR", "DEX", "CON", "INT", "WIS", "CHA")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { stat ->
                                    FilledTonalButton(
                                        onClick = { onAssignStat(stat) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stat, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
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
    val realms = RealmsTheme.colors
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
                .background(Color.Black.copy(alpha = 0.55f * alpha.value)),
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
                                    realms.fumbleRed.copy(alpha = 0.6f * glow.value),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\u2694\uFE0F", fontSize = 54.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "INITIATIVE",
                        style = MaterialTheme.typography.displayLarge.copy(
                            letterSpacing = 10.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = realms.fumbleRed.copy(alpha = alpha.value),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ROLL FOR BLOOD",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp),
                        color = Color.White.copy(alpha = 0.7f * alpha.value)
                    )
                }
            }
        }
    }
}

/**
 * Short/Long rest overlay with hand-rolled Compose illustrations.
 *   - `short:N`: a flickering **campfire** (three wavering flames, warm halo)
 *     and the healed-HP line.
 *   - `long`: a rolling **sunrise** — horizon band brightens, sun lifts, sky
 *     colour-shifts from indigo to warm amber-gold.
 */
@Composable
fun RestOverlay(kind: String, onDismiss: () -> Unit) {
    val realms = RealmsTheme.colors
    val long = kind == "long"
    val healed = if (!long) kind.substringAfter(":").toIntOrNull() ?: 0 else 0

    LaunchedEffect(kind) {
        delay(if (long) 3200 else 2400)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (long) SunriseIllustration() else CampfireIllustration()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (long) "LONG REST" else "SHORT REST",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp),
                        color = realms.goldAccent
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (long) "Dawn breaks. You are made whole." else "The fire crackles. You recover $healed HP.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

/**
 * Campfire — three flickering triangular flames with independent phase noise,
 * a warm radial halo that pulses, and a small log bed. All Compose Canvas.
 */
@Composable
private fun CampfireIllustration() {
    val infinite = rememberInfiniteTransition(label = "fire")
    val flicker1 by infinite.animateFloat(
        0.85f, 1.15f,
        infiniteRepeatable(tween(280, easing = androidx.compose.animation.core.EaseInOut), RepeatMode.Reverse),
        label = "f1"
    )
    val flicker2 by infinite.animateFloat(
        0.9f, 1.2f,
        infiniteRepeatable(tween(200, easing = androidx.compose.animation.core.EaseInOut), RepeatMode.Reverse),
        label = "f2"
    )
    val flicker3 by infinite.animateFloat(
        0.75f, 1.1f,
        infiniteRepeatable(tween(340, easing = androidx.compose.animation.core.EaseInOut), RepeatMode.Reverse),
        label = "f3"
    )
    val halo by infinite.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.EaseInOut), RepeatMode.Reverse),
        label = "halo"
    )

    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(160.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = size.minDimension

        // Warm halo
        drawCircle(
            color = Color(0xFFFF9A3D).copy(alpha = 0.35f * halo),
            radius = unit * 0.46f,
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFFFFCC66).copy(alpha = 0.25f * halo),
            radius = unit * 0.28f,
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )

        // Logs — two crossed short rectangles at the base.
        val logY = cy + unit * 0.18f
        val logW = unit * 0.42f
        val logH = unit * 0.07f
        rotate(-15f, androidx.compose.ui.geometry.Offset(cx, logY)) {
            drawRoundRect(
                color = Color(0xFF6B3A1A),
                topLeft = androidx.compose.ui.geometry.Offset(cx - logW / 2, logY - logH / 2),
                size = androidx.compose.ui.geometry.Size(logW, logH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(logH / 2)
            )
        }
        rotate(15f, androidx.compose.ui.geometry.Offset(cx, logY)) {
            drawRoundRect(
                color = Color(0xFF4A2A10),
                topLeft = androidx.compose.ui.geometry.Offset(cx - logW / 2, logY - logH / 2),
                size = androidx.compose.ui.geometry.Size(logW, logH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(logH / 2)
            )
        }

        // Three flames.
        drawFlame(cx - unit * 0.12f, logY, unit * 0.18f, flicker1, Color(0xFFFF6A1A), Color(0xFFFFC93D))
        drawFlame(cx + unit * 0.11f, logY, unit * 0.2f, flicker2, Color(0xFFFF8E2B), Color(0xFFFFE27A))
        drawFlame(cx, logY - unit * 0.02f, unit * 0.26f, flicker3, Color(0xFFFF4D1A), Color(0xFFFFE14C))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlame(
    bx: Float,
    by: Float,
    height: Float,
    scale: Float,
    outer: Color,
    inner: Color
) {
    val h = height * scale
    val w = h * 0.55f
    val tip = androidx.compose.ui.geometry.Offset(bx, by - h)
    val left = androidx.compose.ui.geometry.Offset(bx - w / 2f, by)
    val right = androidx.compose.ui.geometry.Offset(bx + w / 2f, by)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(left.x, left.y)
        cubicTo(left.x, by - h * 0.6f, tip.x - w * 0.1f, by - h * 0.9f, tip.x, tip.y)
        cubicTo(tip.x + w * 0.1f, by - h * 0.9f, right.x, by - h * 0.6f, right.x, right.y)
        close()
    }
    drawPath(path, outer)
    // Inner softer flame (shorter).
    val h2 = h * 0.65f
    val w2 = w * 0.65f
    val path2 = androidx.compose.ui.graphics.Path().apply {
        moveTo(bx - w2 / 2f, by)
        cubicTo(bx - w2 / 2f, by - h2 * 0.6f, bx - w2 * 0.1f, by - h2 * 0.9f, bx, by - h2)
        cubicTo(bx + w2 * 0.1f, by - h2 * 0.9f, bx + w2 / 2f, by - h2 * 0.6f, bx + w2 / 2f, by)
        close()
    }
    drawPath(path2, inner)
}

/**
 * Sunrise — horizon band fills with warm amber as the sun rises from below.
 * Sky drifts from indigo → rose → gold over ~3s.
 */
@Composable
private fun SunriseIllustration() {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2800, easing = androidx.compose.animation.core.EaseOutCubic))
    }
    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(width = 220.dp, height = 140.dp)
    ) {
        val p = progress.value
        // Sky gradient
        val skyTop = lerpColor(Color(0xFF141826), Color(0xFF5C4075), p.coerceAtMost(0.5f) * 2f)
        val skyMid = lerpColor(Color(0xFF3C1E4E), Color(0xFFFF8E6B), p)
        val skyLow = lerpColor(Color(0xFF5A1E0C), Color(0xFFFFC576), p)
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(skyTop, skyMid, skyLow)
            )
        )
        // Sun
        val sunRadius = size.minDimension * 0.22f
        val sunCx = size.width / 2f
        val sunCy = size.height * (1.15f - 0.9f * p)
        drawCircle(
            color = Color(0xFFFFE27A).copy(alpha = 0.35f),
            radius = sunRadius * 1.8f,
            center = androidx.compose.ui.geometry.Offset(sunCx, sunCy)
        )
        drawCircle(
            color = Color(0xFFFFC93D),
            radius = sunRadius,
            center = androidx.compose.ui.geometry.Offset(sunCx, sunCy)
        )
        // Horizon line
        val horizonY = size.height * 0.78f
        drawRect(
            color = Color(0xFF1A0E08).copy(alpha = 0.55f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - horizonY)
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt
    )
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
    val realms = RealmsTheme.colors
    val available = Feats.list.filter { it.name !in currentFeats }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "CHOOSE A FEAT",
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp),
                    color = realms.goldAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "A reward for reaching a milestone level.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(available) { feat ->
                        Surface(
                            onClick = { onSelect(feat.name) },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(feat.icon, fontSize = 28.sp)
                                Spacer(Modifier.width(12.dp))
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
