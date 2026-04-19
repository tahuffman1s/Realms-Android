package com.realmsoffate.game.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.Choice
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

// ============================================================
// HELPERS
// ============================================================

internal fun sceneEmoji(s: String): String = when (s) {
    "cave" -> "\uD83D\uDD73\uFE0F"; "forest" -> "\uD83C\uDF32"; "tavern" -> "\uD83C\uDF7A"
    "battle" -> "\u2694\uFE0F"; "dungeon" -> "\uD83C\uDFF0"; "town" -> "\uD83C\uDFD8\uFE0F"
    "mountain" -> "\u26F0\uFE0F"; "camp" -> "\u26FA"; "ruins" -> "\uD83D\uDDFF"
    "castle" -> "\uD83C\uDFF0"; "swamp" -> "\uD83D\uDD79\uFE0F"; "ocean" -> "\uD83C\uDF0A"
    "desert" -> "\uD83C\uDFDC\uFE0F"; "temple" -> "\u26EA"; "road" -> "\uD83D\uDEE3\uFE0F"
    "underground" -> "\uD83D\uDD73\uFE0F"; else -> "\uD83C\uDF0C"
}

internal fun npcColor(name: String, palette: List<Pair<Color, Color>>): Pair<Color, Color> {
    if (name.isBlank()) return palette[0]
    val idx = (name.lowercase().hashCode() and 0x7FFFFFFF) % palette.size
    return palette[idx]
}

/**
 * Resolves a raw NPC ref (may be a slug id, a display name, or an arbitrary
 * string) to the NPC's current display name from the log. Falls back to the
 * raw ref if no match — that keeps rendering safe for new NPCs whose
 * [NPC_MET] hasn't been applied yet and for legacy name-form refs.
 */
internal fun resolveNpcDisplayName(
    ref: String,
    npcLog: List<LogNpc>
): String {
    if (ref.isBlank()) return ref
    val byId = npcLog.firstOrNull { it.id == ref }
    if (byId != null && byId.name.isNotBlank()) return byId.name
    val byName = npcLog.firstOrNull { it.name.equals(ref, ignoreCase = true) }
    if (byName != null && byName.name.isNotBlank()) return byName.name
    return ref
}

internal fun formatSignedRoll(n: Int) = if (n >= 0) "+$n" else n.toString()

// ============================================================
// COMPOSABLES
// ============================================================

@Composable
internal fun PlayerBubble(
    text: String,
    characterName: String?
) {
    val realms = RealmsTheme.colors
    val (accent, bgTint) = npcColor(characterName ?: "You", realms.npcPalette)
    val displayName = characterName ?: "You"
    val initial = displayName.take(1).uppercase()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Left: bubble with asymmetric corners (tail points right toward avatar)
        Surface(
            color = bgTint.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.5.sp
                            ),
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val cleanText = text
                        .removeSurrounding("\"")
                        .removeSurrounding("\u201C", "\u201D")
                        .trim()
                    Text(
                        text = com.realmsoffate.game.util.parseInline(
                            cleanText,
                            boldColor = accent,
                            codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                            codeText = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = (15f * LocalFontScale.current).sp
                        ),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // Right: 32dp gold avatar
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.4f), accent.copy(alpha = 0.15f))
                    )
                )
                .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initial,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatChangePills(msg: DisplayMessage.Narration) {
    val pills = mutableListOf<Triple<String, Color, Color>>() // text, bg, fg

    if (msg.hpBefore != msg.hpAfter) {
        val hpDiff = msg.hpAfter - msg.hpBefore
        val lost = hpDiff < 0
        pills.add(Triple(
            "♥ ${if (hpDiff > 0) "+" else ""}$hpDiff HP",
            if (lost) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            if (lost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ))
    }
    if (msg.goldBefore != msg.goldAfter) {
        val diff = msg.goldAfter - msg.goldBefore
        pills.add(Triple(
            "💰 ${if (diff > 0) "+" else ""}${diff}g",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary
        ))
    }
    if (msg.xpGained > 0) {
        pills.add(Triple(
            "★ +${msg.xpGained} XP",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary
        ))
    }
    // Status effects gained
    msg.conditionsAdded.forEach { condition ->
        pills.add(Triple(
            "+$condition",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary
        ))
    }
    // Status effects removed
    msg.conditionsRemoved.forEach { condition ->
        pills.add(Triple(
            "-$condition",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant
        ))
    }
    // Items gained
    msg.itemsGained.forEach { item ->
        pills.add(Triple(
            "+$item",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary
        ))
    }
    // Items lost
    msg.itemsRemoved.forEach { item ->
        pills.add(Triple(
            "-$item",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error
        ))
    }
    // Morality shift
    if (msg.moralDelta != 0) {
        val good = msg.moralDelta > 0
        pills.add(Triple(
            "⚖ ${if (good) "+" else ""}${msg.moralDelta} Moral",
            if (good) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        ))
    }
    // Faction reputation changes
    msg.repDeltas.forEach { (faction, delta) ->
        val positive = delta > 0
        pills.add(Triple(
            "💡 ${if (positive) "+" else ""}$delta $faction",
            if (positive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            if (positive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
        ))
    }

    if (pills.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            pills.forEach { (text, bg, fg) ->
                Surface(
                    color = bg,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
internal fun NpcDialogueBubble(
    name: String,
    quote: String,
    onTap: () -> Unit = {},
    isInteractive: Boolean = true
) {
    val (accent, bgTint) = npcColor(name, RealmsTheme.colors.npcPalette)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = isInteractive) { onTap() },
        verticalAlignment = Alignment.Top
    ) {
        // Left: 32dp avatar circle with gradient background
        if (name.isNotBlank()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.4f), accent.copy(alpha = 0.15f))
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        // Right: bubble with asymmetric corners (small top-left = speech tail)
        Surface(
            color = bgTint.copy(alpha = 0.12f),
            shape = RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    if (name.isNotBlank()) {
                        Text(
                            name.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.5.sp
                            ),
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    val cleanQuote = quote
                        .removeSurrounding("\"")
                        .removeSurrounding("\u201C", "\u201D")
                        .removeSurrounding("'")
                        .removeSurrounding("\u2018", "\u2019")
                        .trim()
                    Text(
                        text = com.realmsoffate.game.util.parseInline(
                            cleanQuote,
                            boldColor = accent,
                            codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                            codeText = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = (15f * LocalFontScale.current).sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwipeableMessage(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    leftLabel: String? = "Attack",
    rightLabel: String? = "Examine",
    leftIcon: androidx.compose.ui.graphics.vector.ImageVector? = Icons.Filled.Bolt,
    rightIcon: androidx.compose.ui.graphics.vector.ImageVector? = Icons.Filled.Search,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val threshold = 100f

    Box(Modifier.fillMaxWidth()) {
        // Background action labels revealed by swipe
        Row(
            Modifier
                .matchParentSize()
                .padding(horizontal = RealmsSpacing.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side (revealed on swipe right) — Examine
            AnimatedVisibility(visible = offsetX > 30f && rightLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rightIcon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        rightLabel.orEmpty(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // Right side (revealed on swipe left) — Attack (only for dialogue bubbles)
            AnimatedVisibility(visible = offsetX < -30f && leftLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        leftLabel.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    leftIcon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        // The actual message content — draggable
        val a11yActions = buildList {
            add(CustomAccessibilityAction(leftLabel ?: "Action") { onSwipeLeft(); true })
            add(CustomAccessibilityAction(rightLabel ?: "Info") { onSwipeRight(); true })
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), 0) }
                .semantics { customActions = a11yActions }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > threshold) onSwipeRight()
                            else if (offsetX < -threshold) onSwipeLeft()
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-200f, 200f)
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
internal fun EventCard(icon: String, title: String, text: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    "WORLD EVENT · ${title.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = com.realmsoffate.game.ui.theme.NarrationBodyStyle, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
internal fun SystemLine(text: String) {
    // Colorize check-result lines so PASS/FAIL pops in the chat feed.
    val (bg, fg) = when {
        text.startsWith("✓") -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        text.startsWith("✗") -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            color = bg,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = if (text.startsWith("✓") || text.startsWith("✗")) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.xs)
            )
        }
    }
}

@Composable
internal fun NarratorThinking() {
    val infinite = rememberInfiniteTransition(label = "think")
    val alpha by infinite.animateFloat(
        0.35f, 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.xs)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.75f)))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.5f)))
        Spacer(Modifier.width(12.dp))
        Text(
            "The narrator weighs your words\u2026",
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ChoiceTile(c: Choice, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("${c.n}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (c.skill.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Text(
                        c.skill.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.xxs)
                    )
                }
            }
        }
    }
}
