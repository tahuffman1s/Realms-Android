package com.realmsoffate.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.Choice
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.theme.RealmsTheme

// ============================================================
// MEMORIES PANEL — bookmarked narration/event moments
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoriesPanel(state: GameUiState, onClose: () -> Unit, onDelete: (String) -> Unit = {}) {
    val realms = RealmsTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bookmark, null, Modifier.size(20.dp), tint = realms.goldAccent)
                Spacer(Modifier.width(8.dp))
                Text("MEMORIES", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            if (state.bookmarks.isEmpty()) {
                Text(
                    "No moments pinned yet.\nTap the bookmark icon on any message bubble to save it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.bookmarks) { text ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Filled.Bookmark,
                                    null,
                                    Modifier.size(16.dp).padding(top = 2.dp),
                                    tint = realms.goldAccent
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDelete(text) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        "Remove",
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

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
    val realms = RealmsTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, Modifier.size(20.dp), tint = realms.goldAccent)
                Spacer(Modifier.width(8.dp))
                Text("SETTINGS", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))

            Text(
                "FONT SIZE",
                style = MaterialTheme.typography.labelLarge,
                color = realms.goldAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Adjusts text size across all chat bubbles",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // Preview text at current scale
            Surface(
                color = realms.asideBubble.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
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

            Spacer(Modifier.height(16.dp))

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
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = fontScale,
                    onValueChange = { onFontScaleChange(it) },
                    valueRange = 0.7f..1.6f,
                    steps = 0,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
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

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Utility actions
            Text(
                "ACTIONS",
                style = MaterialTheme.typography.labelLarge,
                color = realms.goldAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            ListItem(
                headlineContent = { Text("Export Save") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                modifier = Modifier.clickable { onExportSave(); onClose() }
            )

            ListItem(
                headlineContent = { Text("Short Rest") },
                supportingContent = { Text("Recover some HP without ending the day") },
                leadingContent = { Icon(Icons.Default.Bedtime, null) },
                modifier = Modifier.clickable { onShortRest(); onClose() }
            )

            ListItem(
                headlineContent = { Text("Long Rest") },
                supportingContent = { Text("Full recovery — advances time and resets resources") },
                leadingContent = { Icon(Icons.Default.NightShelter, null) },
                modifier = Modifier.clickable { onLongRest(); onClose() }
            )

            ListItem(
                headlineContent = { Text("Debug Dump") },
                leadingContent = { Icon(Icons.Default.BugReport, null) },
                modifier = Modifier.clickable { onDebugDump(); onClose() }
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            ListItem(
                headlineContent = {
                    Text("Return to Title", color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable { onReturnToTitle() }
            )

            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

// ============================================================
// TUTORIAL OVERLAY — guided first-play walkthrough
// ============================================================

@Composable
internal fun TutorialOverlay(step: Int, onNext: () -> Unit, onDismiss: () -> Unit) {
    val realms = RealmsTheme.colors

    data class TutStep(val title: String, val message: String, val icon: String, val alignment: Alignment)

    val steps = listOf(
        TutStep("Welcome, Adventurer", "Welcome to Realms! The narrator will tell your story. Each turn, the world reacts to your choices. Let's learn the basics.", "\u2694\uFE0F", Alignment.Center),
        TutStep("The Story Feed", "This is the story feed. The narrator describes the world, NPCs talk in colored bubbles, and your choices play out here.", "\uD83D\uDCDC", Alignment.Center),
        TutStep("Your Actions", "Type what you want to do here. Say anything — talk to NPCs, examine objects, cast spells, or just explore.", "\u270D\uFE0F", Alignment.BottomCenter),
        TutStep("Choices", "After each turn, you'll get choices. Tap the choices button to see them — or type your own action!", "\uD83C\uDFAD", Alignment.BottomEnd),
        TutStep("Dice Rolls", "When you pick a skill-based action, you'll roll a d20. Watch it spin — the narrator decides if you pass based on the total.", "\uD83C\uDFB2", Alignment.Center),
        TutStep("Navigation", "Use the bottom tabs to check your inventory, stats, map, and more.", "\uD83E\uDDED", Alignment.BottomCenter),
        TutStep("Swipe Actions", "Swipe right on any message to examine. Swipe left on NPC dialogue to attack.", "\uD83D\uDC46", Alignment.Center),
        TutStep("Bookmarks", "Tap the bookmark icon on any bubble to save favourite moments to your Memories.", "\uD83D\uDD16", Alignment.Center),
        TutStep("You're Ready!", "Go forth, adventurer. Make choices. Face consequences. Try not to die on the first turn.\n\n...No promises from the narrator.", "\uD83C\uDF1F", Alignment.Center)
    )

    val current = steps.getOrNull(step) ?: return
    val isLast = step == steps.lastIndex

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onNext),
        contentAlignment = current.alignment
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 340.dp)
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(current.icon, fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = realms.goldAccent,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                // Progress dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == step) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        i == step -> realms.goldAccent
                                        i < step  -> realms.goldAccent.copy(alpha = 0.4f)
                                        else      -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = if (isLast) onDismiss else onNext,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (isLast) "Begin!" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("YOUR CHOICES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            choices.forEach { c -> ChoiceTile(c, onClick = { onPick(c) }) ; Spacer(Modifier.height(8.dp)) }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

// ============================================================
// MORE MENU SHEET — grid of quick-action tiles
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreMenuSheet(
    onClose: () -> Unit,
    onAction: (String) -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = listOf(
        Icons.Filled.Save to ("Save" to "save"),
        Icons.Filled.Download to ("Download" to "download"),
        Icons.Filled.FolderOpen to ("Load" to "menu"),
        Icons.Filled.Home to ("Main Menu" to "menu"),
        Icons.Filled.AutoAwesome to ("Spells" to "spells"),
        Icons.AutoMirrored.Filled.MenuBook to ("Lore" to "lore"),
        Icons.Filled.Book to ("Journal" to "journal"),
        Icons.Filled.BookmarkBorder to ("Memories" to "memories"),
        Icons.Filled.CurrencyExchange to ("Currency" to "currency"),
        Icons.Filled.Groups to ("Party" to "party"),
        @Suppress("DEPRECATION") Icons.Filled.Assignment to ("Quests" to "quests"),
        Icons.Filled.HotelClass to ("Short Rest" to "shortrest"),
        Icons.Filled.NightsStay to ("Long Rest" to "longrest"),
        Icons.Filled.Tune to ("Settings" to "settings"),
        Icons.Filled.BugReport to ("Debug Dump" to "debug"),
        Icons.Filled.Settings to ("Setup" to "setup")
    )
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("MORE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(items) { (icon, pair) ->
                    val (label, action) = pair
                    MoreTile(icon = icon, label = label, onClick = { onAction(action) })
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

@Composable
internal fun MoreTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
