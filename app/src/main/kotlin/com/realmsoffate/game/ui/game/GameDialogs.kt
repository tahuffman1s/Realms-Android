package com.realmsoffate.game.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.realmsoffate.game.game.DeathSaveState
import com.realmsoffate.game.game.PreRollDisplay
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pre-roll preview — the player sees the d20 + breakdown BEFORE the action
 * is sent to the narrator. Designed to read like a small ritual:
 *
 *   YOU ATTEMPT
 *   ┃ "I attack the goblin"
 *
 *           ◆ 14 ◆            (hex d20 silhouette, large numeral)
 *
 *   ATHLETICS · STR check
 *
 *   d20 roll              14
 *   Ability (STR)          +3
 *   Proficiency            +2
 *   ─────────────────────────
 *   TOTAL                  19
 *
 *   The narrator sets the DC.
 *
 *   [ Take Back ]   [ Send It ]
 */
@Composable
internal fun PreRollDialog(
    pre: PreRollDisplay,
    onConfirm: () -> Unit
) {
    val dieColor = when {
        pre.roll == 20 -> MaterialTheme.colorScheme.tertiary
        pre.roll == 1 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val glowColor = when {
        pre.roll == 20 -> MaterialTheme.colorScheme.tertiaryContainer
        pre.roll == 1 -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    // Animated entrance — quick scale-in + fade so it feels like a card flipping.
    val entrance = remember(pre) { Animatable(0.85f) }
    val alpha = remember(pre) { Animatable(0f) }
    LaunchedEffect(pre) {
        alpha.animateTo(1f, tween(160))
        entrance.animateTo(1f, spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ))
    }
    var displayedRoll by remember(pre) { mutableIntStateOf((1..20).random()) }
    var spinComplete by remember(pre) { mutableStateOf(false) }
    LaunchedEffect(pre) {
        // Fast spin phase: 15 random numbers at ~50ms each
        repeat(15) {
            displayedRoll = (1..20).random()
            delay(50)
        }
        // Deceleration phase: 8 numbers, slowing down
        val delays = listOf(80L, 100L, 130L, 170L, 220L, 280L, 350L, 450L)
        delays.forEach { d ->
            displayedRoll = (1..20).random()
            delay(d)
        }
        // Final reveal
        displayedRoll = pre.roll
        spinComplete = true
    }
    // Pulse the halo when crit/fumble — subtle but it sells the moment.
    val haloPulse = if (pre.crit) {
        val infinite = rememberInfiniteTransition(label = "preroll-halo")
        infinite.animateFloat(
            initialValue = 0.7f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(700),
                RepeatMode.Reverse
            ),
            label = "preroll-halo-anim"
        ).value
    } else 1f

    Dialog(
        onDismissRequest = onConfirm,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = RealmsElevation.high,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .alpha(alpha.value)
                .scale(entrance.value)
        ) {
            Column(
                Modifier
                    .padding(RealmsSpacing.xxl)
                    .widthIn(min = 300.dp, max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---- Action quote header ----
                Text(
                    "YOU ATTEMPT",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(if (pre.action.length > 60) 36.dp else 22.dp)
                            .background(dieColor.copy(alpha = 0.7f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "\u201C${pre.action}\u201D",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(RealmsSpacing.xl))

                // ---- d20 silhouette ----
                Box(
                    Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer halo — soft radial glow, brighter on crit/fumble.
                    Canvas(Modifier.matchParentSize()) {
                        drawCircle(
                            color = glowColor.copy(alpha = glowColor.alpha * haloPulse),
                            radius = size.minDimension / 2f
                        )
                    }
                    // Hex d20 face — drawn as a regular hexagon with an inner triangle
                    // that evokes one face of an icosahedron.
                    Canvas(Modifier.size(110.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension / 2f * 0.95f
                        val outer = Path().apply {
                            for (i in 0 until 6) {
                                val a = (i * 60.0 - 30.0) * PI / 180.0
                                val x = cx + (r * cos(a)).toFloat()
                                val y = cy + (r * sin(a)).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        val inner = Path().apply {
                            val rr = r * 0.55f
                            for (i in 0 until 3) {
                                val a = (i * 120.0 - 90.0) * PI / 180.0
                                val x = cx + (rr * cos(a)).toFloat()
                                val y = cy + (rr * sin(a)).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        drawPath(outer, dieColor.copy(alpha = if (spinComplete) 0.14f else 0.08f))
                        drawPath(
                            outer, if (spinComplete) dieColor else dieColor.copy(alpha = 0.4f),
                            style = Stroke(width = if (spinComplete) 3.5f else 2f)
                        )
                        drawPath(
                            inner, dieColor.copy(alpha = 0.35f),
                            style = Stroke(width = 1.5f)
                        )
                    }
                    // The number itself — the only thing that matters.
                    Text(
                        displayedRoll.toString(),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        color = if (spinComplete) dieColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.displayLarge
                    )
                }

                // ---- Crit / fumble flourish ----
                if (pre.crit && spinComplete) {
                    Spacer(Modifier.height(RealmsSpacing.s))
                    Text(
                        if (pre.roll == 20) "🌟 NATURAL TWENTY"
                        else "💀 NATURAL ONE",
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                        color = dieColor,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (pre.roll == 20) "Fate favours you. Whatever the DC, you pass."
                        else "The dice mock you. Whatever the DC, you fail.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = RealmsSpacing.s)
                    )
                }

                Spacer(Modifier.height(if (pre.crit) 14.dp else 18.dp))

                // ---- Everything below slides in after the dice spin completes ----
                val totalScale = animateFloatAsState(
                    targetValue = if (spinComplete) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "total-reveal"
                )
                val totalAlpha = animateFloatAsState(
                    targetValue = if (spinComplete) 1f else 0f,
                    animationSpec = tween(300),
                    label = "total-alpha"
                )

                AnimatedVisibility(
                    visible = spinComplete,
                    enter = fadeIn(animationSpec = tween(300)) +
                            slideInVertically(initialOffsetY = { it / 3 })
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // ---- Skill / ability label ----
                        if (pre.skill != null) {
                            Text(
                                "${pre.skill.uppercase()} · ${pre.ability} check",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Text(
                                "FREE ACTION",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        // ---- Vertical breakdown ----
                        BreakdownRow("d20 roll", pre.roll.toString(), dieColor, isHero = true)
                        BreakdownRow(
                            "Ability (${pre.ability})",
                            formatSignedRoll(pre.mod),
                            MaterialTheme.colorScheme.secondary
                        )
                        if (pre.prof != 0) {
                            BreakdownRow(
                                "Proficiency",
                                formatSignedRoll(pre.prof),
                                MaterialTheme.colorScheme.tertiary
                            )
                        }
                        // Total separator — gradient gold line.
                        Box(
                            Modifier
                                .padding(vertical = 6.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.tertiary,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Row(
                            Modifier.fillMaxWidth().scale(totalScale.value).alpha(totalAlpha.value),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "TOTAL",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                pre.total.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(Modifier.height(RealmsSpacing.m))
                Text(
                    "The narrator sets the DC.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))

                // ---- Button ----
                Button(
                    onClick = onConfirm,
                    enabled = spinComplete,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = dieColor)
                ) {
                    Text(
                        "Send It",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp)
                    )
                }
            }
        }
    }
}

/**
 * One line of the pre-roll breakdown — label on the left, signed value on the
 * right in the row's accent colour. Hero row (the d20 itself) gets larger,
 * unsigned text since it's the source of everything.
 */
@Composable
private fun BreakdownRow(label: String, value: String, accent: Color, isHero: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = if (isHero) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun DeathSaveDialog(
    saves: DeathSaveState,
    onRoll: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* no dismiss — must resolve by rolling */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = RealmsElevation.medium,
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                Column(
                    Modifier.padding(RealmsSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "\uD83D\uDC80",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(Modifier.height(RealmsSpacing.s))
                    Text(
                        "DEATH SAVES",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 6.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(RealmsSpacing.m))
                    Text(
                        "You're bleeding out. Roll d20 — \u226510 succeeds, <10 fails. 3 successes stabilise, 3 failures end it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                        ScoreCol("SUCCESSES", saves.successes, MaterialTheme.colorScheme.primary)
                        ScoreCol("FAILURES", saves.failures, MaterialTheme.colorScheme.error)
                    }
                    if (saves.rolls.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Rolls: " + saves.rolls.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(RealmsSpacing.l))
                    Button(
                        onClick = onRoll,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("ROLL SAVE", fontWeight = FontWeight.Bold, letterSpacing = 3.sp) }
                }
            }
        }
    }
}

@Composable
private fun ScoreCol(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.height(RealmsSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs)) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < count) color
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(1.dp, color, CircleShape)
                )
            }
        }
    }
}
