package com.realmsoffate.game.ui.dice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.realmsoffate.game.game.CheckDisplay
import com.realmsoffate.game.ui.theme.RealmsTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated d20 reveal — pure Compose, no external 3D.
 *   Phase 0: tumble (numbers cycle, die spins, 700ms)
 *   Phase 1: lock in result with a pulse + crit/fumble glow
 *   Phase 2: breakdown strip (roll + mod + prof = total, pass/fail)
 *
 * Colors are theme-driven via RealmsTheme.colors — crit-gold and fumble-red
 * track light/dark variants instead of being hardcoded hex literals.
 */
@Composable
fun DiceRollerDialog(check: CheckDisplay, onClose: () -> Unit) {
    val realms = RealmsTheme.colors
    var phase by remember(check) { mutableIntStateOf(0) }
    var shownRoll by remember(check) { mutableIntStateOf(check.roll) }
    val scale = remember(check) { Animatable(0.6f) }

    val tumble = rememberInfiniteTransition(label = "tumble")
    val spin by tumble.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Restart),
        label = "spin"
    )

    LaunchedEffect(check) {
        val end = System.currentTimeMillis() + 700
        while (System.currentTimeMillis() < end) {
            shownRoll = (1..20).random()
            delay(40)
        }
        shownRoll = check.roll
        phase = 1
        scale.animateTo(1.15f, tween(180))
        scale.animateTo(1f, tween(180))
        delay(650)
        phase = 2
        delay(2000)
        onClose()
    }

    val crit = check.roll == 20
    val fumble = check.roll == 1
    val dieColor = when {
        crit -> realms.critGold
        fumble -> realms.fumbleRed
        else -> MaterialTheme.colorScheme.primary
    }
    val glow = when {
        crit -> realms.critGlow
        fumble -> realms.fumbleGlow
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(dismissOnClickOutside = true)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.padding(24.dp).widthIn(min = 280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    check.skill.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = dieColor
                )
                Text(
                    "${check.ability} · DC ${check.dc}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                // d20 glyph — hexagonal silhouette with the shown number centered.
                Box(
                    Modifier
                        .size(132.dp)
                        .scale(scale.value),
                    contentAlignment = Alignment.Center
                ) {
                    // Glow ring
                    Canvas(Modifier.matchParentSize()) {
                        drawCircle(color = glow, radius = size.minDimension / 2f)
                    }
                    // Rotating d20 silhouette during tumble, still after lock-in.
                    val rot = if (phase == 0) spin else 0f
                    Canvas(
                        Modifier
                            .size(108.dp)
                            .rotate(rot)
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension / 2f * 0.92f
                        // Outer hexagon (d20 silhouette from camera-facing)
                        val outer = Path().apply {
                            for (i in 0 until 6) {
                                val a = (i * 60.0 - 30.0) * PI / 180.0
                                val x = cx + (r * cos(a)).toFloat()
                                val y = cy + (r * sin(a)).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        // Inner triangle "face" (tri geometry to evoke a d20 facet)
                        val inner = Path().apply {
                            val rr = r * 0.58f
                            for (i in 0 until 3) {
                                val a = (i * 120.0 - 90.0) * PI / 180.0
                                val x = cx + (rr * cos(a)).toFloat()
                                val y = cy + (rr * sin(a)).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        drawPath(outer, dieColor.copy(alpha = 0.12f))
                        drawPath(outer, dieColor, style = Stroke(width = 3f))
                        drawPath(inner, dieColor.copy(alpha = 0.25f), style = Stroke(width = 1.5f))
                    }
                    Text(
                        shownRoll.toString(),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = dieColor,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 48.sp
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = phase >= 1 && (crit || fumble),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        if (crit) "CRITICAL SUCCESS" else "CRITICAL FAILURE",
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                        color = dieColor
                    )
                }

                AnimatedVisibility(
                    visible = phase >= 2,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill("d20", check.roll.toString(), dieColor)
                            Plus()
                            Pill("MOD", formatSigned(check.mod), MaterialTheme.colorScheme.secondary)
                            if (check.prof != 0) {
                                Plus()
                                Pill("PROF", "+${check.prof}", MaterialTheme.colorScheme.tertiary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "= ${check.total}",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (check.passed) realms.success else realms.fumbleRed,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (check.passed) "SUCCESS — beats DC ${check.dc}" else "FAIL — misses DC ${check.dc}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun Pill(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Plus() {
    Text(
        "+",
        modifier = Modifier.padding(horizontal = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatSigned(n: Int) = if (n >= 0) "+$n" else n.toString()
