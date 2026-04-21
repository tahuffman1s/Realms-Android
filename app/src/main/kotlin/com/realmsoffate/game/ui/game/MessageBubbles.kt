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

/** Avatar diameter — Material 3 list-item leading-icon size. */
private val BubbleAvatarSize = 36.dp

/**
 * Shared bubble frame used by every chat message type. Guarantees consistent
 * avatar size, spacing token usage, tail-corner treatment, and label tracking
 * across PlayerBubble / NpcDialogueBubble / NarratorProseBubble.
 */
@Composable
internal fun BubbleFrame(
    avatarInitial: String?,
    avatarAccent: Color,
    label: String?,
    accent: Color,
    bgColor: Color,
    avatarOnRight: Boolean,
    tailOnTop: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentAlign: TextAlign? = null,
    content: @Composable () -> Unit
) {
    // Tail corner: use extraSmall radius on the side closest to the avatar so
    // the shape reads as attached. Everything else uses shapes.medium.
    val mediumRadius = 14.dp
    val tailRadius = 4.dp
    val shape = when {
        avatarOnRight && tailOnTop ->
            RoundedCornerShape(mediumRadius, tailRadius, mediumRadius, mediumRadius)
        avatarOnRight ->
            RoundedCornerShape(mediumRadius, mediumRadius, tailRadius, mediumRadius)
        tailOnTop ->
            RoundedCornerShape(tailRadius, mediumRadius, mediumRadius, mediumRadius)
        else ->
            RoundedCornerShape(mediumRadius, mediumRadius, mediumRadius, tailRadius)
    }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
    ) {
        if (!avatarOnRight && avatarInitial != null) BubbleAvatar(avatarInitial, avatarAccent)
        val bubble: @Composable () -> Unit = {
            Surface(
                color = bgColor,
                shape = shape,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
                onClick = onClick ?: {},
                enabled = onClick != null,
                modifier = Modifier.weight(1f, fill = true)
            ) {
                Column(Modifier.padding(RealmsSpacing.m)) {
                    if (!label.isNullOrBlank()) {
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = contentAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(RealmsSpacing.xs))
                    }
                    content()
                }
            }
        }
        bubble()
        if (avatarOnRight && avatarInitial != null) BubbleAvatar(avatarInitial, avatarAccent)
    }
}

@Composable
private fun BubbleAvatar(initial: String, accent: Color) {
    Box(
        Modifier
            .size(BubbleAvatarSize)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.18f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun PlayerBubble(
    text: String,
    characterName: String?
) {
    val realms = RealmsTheme.colors
    val (accent, bgTint) = npcColor(characterName ?: "You", realms.npcPalette)
    val displayName = characterName ?: "You"
    val initial = displayName.take(1).uppercase()
    val cleanText = text
        .removeSurrounding("\"")
        .removeSurrounding("\u201C", "\u201D")
        .trim()
    BubbleFrame(
        avatarInitial = initial,
        avatarAccent = accent,
        label = displayName,
        accent = accent,
        bgColor = bgTint.copy(alpha = 0.14f),
        avatarOnRight = true,
        tailOnTop = true,
        contentAlign = TextAlign.End
    ) {
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
    val initial = if (name.isNotBlank()) name.take(1).uppercase() else null
    val cleanQuote = quote
        .removeSurrounding("\"")
        .removeSurrounding("\u201C", "\u201D")
        .removeSurrounding("'")
        .removeSurrounding("\u2018", "\u2019")
        .trim()
    BubbleFrame(
        avatarInitial = initial,
        avatarAccent = accent,
        label = name.takeIf { it.isNotBlank() },
        accent = accent,
        bgColor = bgTint.copy(alpha = 0.14f),
        avatarOnRight = false,
        tailOnTop = true,
        onClick = if (isInteractive) onTap else null
    ) {
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(RealmsSpacing.s))
                Text(
                    "WORLD EVENT · ${title.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(RealmsSpacing.s))
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xs)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.75f)))
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.5f)))
        Spacer(Modifier.width(RealmsSpacing.s))
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
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.m)
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${c.n}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (c.skill.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Text(
                        c.skill.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
                    )
                }
            }
        }
    }
}
