package com.realmsoffate.game.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
// SHARED BOOKMARK ICON
// ============================================================

@Composable
internal fun InlineBookmark(
    isBookmarked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = RealmsTheme.colors.goldAccent,
    inactiveTint: Color = tint.copy(alpha = 0.35f)
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(44.dp)
    ) {
        Icon(
            if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
            modifier = Modifier.size(22.dp),
            tint = if (isBookmarked) tint else inactiveTint
        )
    }
}

// ============================================================
// COMPOSABLES
// ============================================================

@Composable
internal fun SceneBanner(scene: String, desc: String) {
    var expanded by remember(scene, desc) { mutableStateOf(false) }
    // Heuristic: if the description is short enough to fit, no need for the
    // expand affordance — keeps the chevron from showing on one-liners.
    val isLong = desc.length > 80
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "scene-chevron-rotation"
    )
    val secondary = MaterialTheme.colorScheme.secondary
    Surface(
        onClick = { if (isLong) expanded = !expanded },
        enabled = isLong,
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = RealmsSpacing.m)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ),
                MaterialTheme.shapes.medium
            )
            .drawBehind {
                drawRect(
                    color = secondary,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
    ) {
        Row(
            Modifier
                .padding(horizontal = RealmsSpacing.xl, vertical = RealmsSpacing.s)
                .animateContentSize(animationSpec = tween(220)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sceneEmoji(scene), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    scene.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (desc.isNotBlank()) Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
                )
            }
            if (isLong) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
internal fun PlayerBubble(
    text: String,
    characterName: String?,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
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
                InlineBookmark(
                    isBookmarked, onToggleBookmark,
                    modifier = Modifier.padding(end = RealmsSpacing.s),
                    inactiveTint = accent.copy(alpha = 0.4f)
                )
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
                            codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
    val realms = RealmsTheme.colors
    val pills = mutableListOf<Triple<String, Color, Color>>() // text, bg, fg

    if (msg.hpBefore != msg.hpAfter) {
        val hpDiff = msg.hpAfter - msg.hpBefore
        val lost = hpDiff < 0
        pills.add(Triple(
            "♥ ${if (hpDiff > 0) "+" else ""}$hpDiff HP",
            if (lost) realms.fumbleRed.copy(alpha = 0.2f) else realms.success.copy(alpha = 0.2f),
            if (lost) realms.fumbleRed else realms.success
        ))
    }
    if (msg.goldBefore != msg.goldAfter) {
        val diff = msg.goldAfter - msg.goldBefore
        pills.add(Triple(
            "💰 ${if (diff > 0) "+" else ""}${diff}g",
            realms.goldAccent.copy(alpha = 0.2f),
            realms.goldAccent
        ))
    }
    if (msg.xpGained > 0) {
        pills.add(Triple(
            "★ +${msg.xpGained} XP",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.secondary
        ))
    }
    // Status effects gained
    msg.conditionsAdded.forEach { condition ->
        pills.add(Triple(
            "+$condition",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.secondary
        ))
    }
    // Status effects removed
    msg.conditionsRemoved.forEach { condition ->
        pills.add(Triple(
            "-$condition",
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant
        ))
    }
    // Items gained
    msg.itemsGained.forEach { item ->
        pills.add(Triple(
            "+$item",
            realms.success.copy(alpha = 0.15f),
            realms.success
        ))
    }
    // Items lost
    msg.itemsRemoved.forEach { item ->
        pills.add(Triple(
            "-$item",
            realms.fumbleRed.copy(alpha = 0.15f),
            realms.fumbleRed
        ))
    }
    // Morality shift
    if (msg.moralDelta != 0) {
        val good = msg.moralDelta > 0
        pills.add(Triple(
            "⚖ ${if (good) "+" else ""}${msg.moralDelta} Moral",
            if (good) realms.success.copy(alpha = 0.2f) else realms.fumbleRed.copy(alpha = 0.2f),
            if (good) realms.success else realms.fumbleRed
        ))
    }
    // Faction reputation changes
    msg.repDeltas.forEach { (faction, delta) ->
        val positive = delta > 0
        pills.add(Triple(
            "💡 ${if (positive) "+" else ""}$delta $faction",
            if (positive) realms.info.copy(alpha = 0.2f) else realms.warning.copy(alpha = 0.2f),
            if (positive) realms.info else realms.warning
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NpcDialogueBubble(
    name: String,
    quote: String,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onTap: () -> Unit = {},
    onReaction: (String) -> Unit = {},
    isInteractive: Boolean = true
) {
    val (accent, bgTint) = npcColor(name, RealmsTheme.colors.npcPalette)
    var showReactions by remember { mutableStateOf(false) }
    var appliedReaction by remember { mutableStateOf<String?>(null) }

    Column {
        // Bubble + overlapping reaction pill
        Box(Modifier.padding(bottom = if (appliedReaction != null) 8.dp else 0.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (isInteractive) onTap() },
                        onLongClick = { if (isInteractive) showReactions = !showReactions }
                    ),
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
                                    codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    codeText = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = (15f * LocalFontScale.current).sp
                                )
                            )
                        }
                        InlineBookmark(
                            isBookmarked, onToggleBookmark,
                            modifier = Modifier.padding(start = RealmsSpacing.s),
                            inactiveTint = accent.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            // Applied reaction pill — overlapping bottom-right
            appliedReaction?.let { emoji ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-12).dp, y = 10.dp)
                        .height(26.dp)
                        .widthIn(min = 34.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = RealmsSpacing.xs)
                    ) {
                        Text(emoji, fontSize = 14.sp)
                    }
                }
            }
        }

        // Reaction picker — compact row below bubble, appears on long-press
        AnimatedVisibility(
            visible = showReactions,
            enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(100)) + shrinkVertically(animationSpec = tween(100))
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.padding(start = RealmsSpacing.s, top = RealmsSpacing.xs)
            ) {
                Row(
                    Modifier.padding(horizontal = RealmsSpacing.xs, vertical = RealmsSpacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val reactions = listOf(
                        "\uD83D\uDC4D" to "approve",
                        "\uD83D\uDC4E" to "disapprove",
                        "\uD83D\uDE02" to "laugh",
                        "\uD83D\uDE20" to "angry",
                        "\u2753" to "question",
                        "\uD83D\uDE31" to "shocked",
                        "\uD83E\uDD14" to "suspicious"
                    )
                    reactions.forEach { (emoji, action) ->
                        Surface(
                            onClick = {
                                appliedReaction = emoji
                                showReactions = false
                                onReaction(action)
                            },
                            color = Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }
                    // "+" button for custom emoji via system keyboard
                    Box {
                        var emojiCapture by remember { mutableStateOf("") }
                        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                        val keyboardController = LocalSoftwareKeyboardController.current
                        Surface(
                            onClick = {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        // Invisible text field — captures emoji from keyboard
                        BasicTextField(
                            value = emojiCapture,
                            onValueChange = { v ->
                                if (v.isNotBlank()) {
                                    appliedReaction = v
                                    showReactions = false
                                    emojiCapture = ""
                                    onReaction("emoji:$v")
                                }
                            },
                            modifier = Modifier
                                .size(1.dp)
                                .alpha(0f)
                                .focusRequester(focusRequester),
                            singleLine = true
                        )
                    }
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
    val realms = RealmsTheme.colors
    Surface(
        color = realms.warning.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().border(1.dp, realms.warning.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
    ) {
        Column(Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    "WORLD EVENT · ${title.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = realms.warning
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = com.realmsoffate.game.ui.theme.NarrationBodyStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun SystemLine(text: String) {
    val realms = RealmsTheme.colors
    // Colorize check-result lines so PASS/FAIL pops in the chat feed.
    val (bg, fg) = when {
        text.startsWith("✓") -> realms.success.copy(alpha = 0.18f) to realms.success
        text.startsWith("✗") -> realms.fumbleRed.copy(alpha = 0.18f) to realms.fumbleRed
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            color = bg,
            shape = MaterialTheme.shapes.large
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
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

/** Wraps any bubble with a small bookmark reaction button floating at bottom-right. */
@Composable
internal fun BubbleWithReaction(
    isBookmarked: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Box {
        content()
        Surface(
            onClick = onToggle,
            color = if (isBookmarked) RealmsTheme.colors.goldAccent.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(24.dp)
        ) {
            Icon(
                if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Bookmark",
                modifier = Modifier.padding(4.dp),
                tint = if (isBookmarked) RealmsTheme.colors.goldAccent
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}
