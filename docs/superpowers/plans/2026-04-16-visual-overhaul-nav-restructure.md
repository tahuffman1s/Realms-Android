# Visual Overhaul + Navigation Restructure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all panels reachable from the UI, convert Character/Journal to full-screen tabbed pagers, redesign the chat feed with personality (turn dividers, distinct segment styles, avatar columns, accent bars).

**Architecture:** Three independent phases: (A) bug fixes + dead code cleanup, (B) navigation restructure with HorizontalPager, (C) chat feed visual redesign. Each phase produces a working build. Phase B depends on A (dead code must be gone first). Phase C is independent.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, HorizontalPager + ScrollableTabRow (from `foundation.pager`)

---

## Phase A: Bug Fixes + Dead Code

### Task 1: Fix bugs and remove dead code

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatInput.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/LorePage.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Delete SpellChip dead code from ChatInput.kt**

Delete lines 172–209 (the entire `private fun SpellChip(...)` composable). Nothing calls it.

- [ ] **Step 2: Add /memories slash command in ChatInput.kt**

In `handleSlashCommand`, add before the `else` branch (around line 239):

```kotlin
        "memories", "bookmarks" -> openPanel(Panel.Memories)
```

Update the help string (line 224) to include `/memories`:

```kotlin
"Commands: /save /map /inv /stats /spells /lore /journal /currency /party /quest /rest /shortrest /memories /menu /help"
```

- [ ] **Step 3: Delete MoreMenuSheet + MoreTile dead code from GameOverlays.kt**

Delete lines ~390–458 (the `// ---------- More menu ----------` section comment, `MoreMenuSheet` composable, and `MoreTile` composable). Nothing calls them. Remove any now-unused imports (`LazyVerticalGrid`, `GridCells`, `clickable` if only used there).

- [ ] **Step 4: Fix LoreTab.values() → .entries in LorePage.kt**

Three occurrences around lines 50, 53, 54. Replace each:
```kotlin
// Before:
LoreTab.values()
// After:
LoreTab.entries
```

The `.indexOf()` call also needs to change to use `.entries`:
```kotlin
// Before:
selectedIndex = LoreTab.values().indexOf(tab),
onSelect = { tab = LoreTab.values()[it] }
// After:
selectedIndex = LoreTab.entries.indexOf(tab),
onSelect = { tab = LoreTab.entries[it] }
```

- [ ] **Step 5: Add bookmark icon to TopBar.kt**

Add `onMemoriesClick: () -> Unit = {}` parameter to `GameTopBar`:

```kotlin
@Composable
internal fun GameTopBar(
    state: GameUiState,
    onSettingsClick: () -> Unit = {},
    onMemoriesClick: () -> Unit = {}
) {
```

In Row 1, insert a bookmark `IconButton` just before the settings gear (before line 71):

```kotlin
    IconButton(onClick = onMemoriesClick) {
        Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Memories")
    }
    IconButton(onClick = onSettingsClick) {
        Icon(Icons.Default.Settings, contentDescription = "Settings")
    }
```

- [ ] **Step 6: Wire bookmark icon in GameScreen.kt**

Update the `topBar` call at line 128:

```kotlin
topBar = {
    GameTopBar(
        state,
        onSettingsClick = { panel = Panel.Settings },
        onMemoriesClick = { panel = Panel.Memories }
    )
},
```

- [ ] **Step 7: Verify and commit**

Run: `gradle compileDebugKotlin 2>&1 | tail -10`

```bash
git add -A app/src/main/kotlin/com/realmsoffate/game/ui/
git commit -m "Fix bugs: add /memories command, bookmark icon in top bar, delete dead code, fix deprecated .values()"
```

---

## Phase B: Navigation Restructure

### Task 2: Refactor panel pages to support dual mode (inline + sheet)

**Files:**
- Modify: all 8 panel files in `app/src/main/kotlin/com/realmsoffate/game/ui/panels/`

Each panel page currently has one composable (e.g., `InventoryPanel`) that wraps everything in `PanelSheet`. We need to split each into:
- An **inner content** composable (e.g., `InventoryContent`) — the scrollable column content, no PanelSheet wrapper
- The existing **panel** composable (e.g., `InventoryPanel`) — thin wrapper that calls `PanelSheet { InventoryContent(...) }`

This lets the pager render the content directly while slash commands still open the sheet.

- [ ] **Step 1: Refactor InventoryPage.kt**

Extract the body of `InventoryPanel` into `InventoryContent`:

```kotlin
@Composable
internal fun InventoryContent(state: GameUiState, onEquip: (Item) -> Unit) {
    val ch = state.character ?: return
    var selected by remember(ch) { mutableStateOf<Item?>(null) }
    // ... everything that was inside PanelSheet { } ...
}

@Composable
internal fun InventoryPanel(state: GameUiState, onClose: () -> Unit, onEquip: (Item) -> Unit) {
    PanelSheet("🎒  Inventory",
        subtitle = state.character?.let { if (it.inventory.isEmpty()) null else "${it.inventory.size} items" },
        onClose = onClose
    ) {
        InventoryContent(state, onEquip)
    }
}
```

- [ ] **Step 2: Refactor StatsPage.kt**

Same pattern — extract `StatsContent(state)` from `StatsPanel`. The content composable should be a scrollable `Column`.

```kotlin
@Composable
internal fun StatsContent(state: GameUiState) {
    val ch = state.character ?: return
    val realms = RealmsTheme.colors
    Column(
        Modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.xs)
    ) {
        // ... existing body ...
    }
}

@Composable
internal fun StatsPanel(state: GameUiState, onClose: () -> Unit) {
    PanelSheet("📊  Character", onClose = onClose) { StatsContent(state) }
}
```

- [ ] **Step 3: Refactor SpellsPage.kt**

Extract `SpellsContent(state, onHotbar, onCast)` from `SpellsPanel`.

- [ ] **Step 4: Refactor PartyPage.kt**

Extract `PartyContent(state, onDismiss)` from `PartyPanel`.

- [ ] **Step 5: Refactor CurrencyPage.kt**

Extract `CurrencyContent(state, onExchange)` from `CurrencyPanel`.

- [ ] **Step 6: Refactor QuestsPage.kt**

Extract `QuestsContent(state, onAbandon)` from `QuestsPanel`.

- [ ] **Step 7: Refactor NpcJournalPage.kt**

Extract `JournalContent(state, focusNpc)` from `JournalPanel`.

- [ ] **Step 8: Refactor LorePage.kt**

Extract `LoreContent(state)` from `LorePanel`.

- [ ] **Step 9: Verify and commit**

Run: `gradle compileDebugKotlin 2>&1 | tail -10`

```bash
git add -A app/src/main/kotlin/com/realmsoffate/game/ui/panels/
git commit -m "Refactor panels to dual mode: extract XxxContent composables for pager use"
```

---

### Task 3: Create CharacterPager.kt and JournalPager.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/CharacterPager.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/JournalPager.kt`

- [ ] **Step 1: Create CharacterPager.kt**

```kotlin
package com.realmsoffate.game.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.Item
import com.realmsoffate.game.game.Spell
import com.realmsoffate.game.ui.panels.*
import kotlinx.coroutines.launch

private enum class CharacterTab(val label: String) {
    Stats("Stats"),
    Inventory("Inventory"),
    Spells("Spells"),
    Party("Party"),
    Currency("Currency")
}

@Composable
internal fun CharacterPager(
    state: GameUiState,
    onEquip: (Item) -> Unit,
    onDismiss: (String) -> Unit,
    onExchange: (String, String, Int) -> Unit,
    onHotbar: (Int, String?) -> Unit,
    onCast: (Spell) -> Unit
) {
    val tabs = CharacterTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            tab.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 1.sp
                        )
                    }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (tabs[page]) {
                CharacterTab.Stats -> StatsContent(state)
                CharacterTab.Inventory -> InventoryContent(state, onEquip)
                CharacterTab.Spells -> SpellsContent(state, onHotbar, onCast)
                CharacterTab.Party -> PartyContent(state, onDismiss)
                CharacterTab.Currency -> CurrencyContent(state, onExchange)
            }
        }
    }
}
```

Add needed imports for `dp` and `sp`:
```kotlin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
```

- [ ] **Step 2: Create JournalPager.kt**

```kotlin
package com.realmsoffate.game.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.panels.*
import kotlinx.coroutines.launch

private enum class JournalTab(val label: String) {
    Quests("Quests"),
    Npcs("NPCs"),
    Lore("Lore")
}

@Composable
internal fun JournalPager(
    state: GameUiState,
    onAbandon: (String) -> Unit,
    focusNpc: String? = null
) {
    val tabs = JournalTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            tab.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 1.sp
                        )
                    }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (tabs[page]) {
                JournalTab.Quests -> QuestsContent(state, onAbandon)
                JournalTab.Npcs -> JournalContent(state, focusNpc)
                JournalTab.Lore -> LoreContent(state)
            }
        }
    }
}
```

- [ ] **Step 3: Verify and commit**

Run: `gradle compileDebugKotlin 2>&1 | tail -10`

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/CharacterPager.kt \
       app/src/main/kotlin/com/realmsoffate/game/ui/game/JournalPager.kt
git commit -m "Create CharacterPager and JournalPager with HorizontalPager + ScrollableTabRow"
```

---

### Task 4: Wire pagers into GameScreen.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Change tab dispatch to render pagers**

Replace the `LaunchedEffect(tab)` block (lines 101–108). The Character and Journal tabs no longer set a panel — they render inline content. Map still sets `Panel.Map`. Chat sets `Panel.None`.

```kotlin
LaunchedEffect(tab) {
    when (tab) {
        GameTab.Chat -> panel = Panel.None
        GameTab.Map -> panel = Panel.Map
        GameTab.Character -> panel = Panel.None  // pager handles sub-nav
        GameTab.Journal -> panel = Panel.None     // pager handles sub-nav
    }
}
```

- [ ] **Step 2: Render pagers in the Scaffold body**

In the Scaffold body (around line 190), wrap the existing chat content in a `when(tab)`:

```kotlin
{ pad ->
    Column(Modifier.padding(pad).fillMaxSize()) {
        when (tab) {
            GameTab.Chat -> {
                // Existing chat content: SceneBanner + CombatHud + ChatFeed
                // (move the existing code here)
            }
            GameTab.Map -> {
                // Map is handled by Panel.Map early return — this branch shouldn't be reached
                // but add a safety: WorldMapScreen(...)
            }
            GameTab.Character -> {
                CharacterPager(
                    state = state,
                    onEquip = vm::equipToggle,
                    onDismiss = vm::dismissCompanion,
                    onExchange = vm::exchange,
                    onHotbar = vm::updateHotbar,
                    onCast = { spell -> /* same cast logic as SpellsPanel */ }
                )
            }
            GameTab.Journal -> {
                JournalPager(
                    state = state,
                    onAbandon = vm::abandonQuest,
                    focusNpc = journalFocusNpc
                )
            }
        }
    }
}
```

- [ ] **Step 3: Remove panel branches that are now handled by pagers**

In the `when(panel)` dispatch (lines 330–400), remove the branches for panels that are now rendered inside pagers: `Panel.Inventory`, `Panel.Quests`, `Panel.Party`, `Panel.Lore`, `Panel.Journal`, `Panel.Currency`, `Panel.Spells`, `Panel.Stats`.

Keep: `Panel.Memories`, `Panel.Settings`, and the `else -> {}` branch.

Also keep the individual panel composables callable from slash commands — when a slash command sets `panel = Panel.Inventory`, we still want to open the sheet. So re-add the removed branches but have them set `tab = GameTab.Character` instead:

```kotlin
    // Slash-command panel opens navigate to the correct pager tab
    Panel.Inventory, Panel.Spells, Panel.Stats, Panel.Party, Panel.Currency -> {
        tab = GameTab.Character
        panel = Panel.None
    }
    Panel.Quests, Panel.Journal, Panel.Lore -> {
        tab = GameTab.Journal
        panel = Panel.None
    }
```

Actually, this needs to be a `LaunchedEffect(panel)` to react to panel changes from slash commands:

```kotlin
LaunchedEffect(panel) {
    when (panel) {
        Panel.Inventory, Panel.Spells, Panel.Stats, Panel.Party, Panel.Currency -> {
            tab = GameTab.Character
            panel = Panel.None
        }
        Panel.Quests, Panel.Journal, Panel.Lore -> {
            tab = GameTab.Journal
            panel = Panel.None
        }
        else -> {} // Memories, Settings, Map, None stay as-is
    }
}
```

- [ ] **Step 4: Verify and commit**

Run: `gradle compileDebugKotlin 2>&1 | tail -10`

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Wire CharacterPager and JournalPager into GameScreen, slash commands navigate to pager tabs"
```

---

### Task 5: Build, test, install

- [ ] **Step 1: Run tests**

Run: `gradle test 2>&1 | tail -20`
Expected: All pass (no game logic changes)

- [ ] **Step 2: Build and install**

Run: `gradle installDebug && adb shell am start -n com.realmsoffate.game/.MainActivity`

- [ ] **Step 3: Commit phase B**

```bash
git add -A
git commit -m "Phase B complete: Character/Journal tabs as full-screen pagers with sub-tabs"
```

---

## Phase C: Chat Feed Redesign

### Task 6: Add TurnDivider to ChatFeed

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt`

- [ ] **Step 1: Create TurnDivider composable**

Add to ChatFeed.kt (or a new file if preferred, but it's small enough to live in ChatFeed):

```kotlin
@Composable
private fun TurnDivider(turnNumber: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.weight(1f).height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.outlineVariant))
            )
        )
        Text(
            "TURN $turnNumber",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = RealmsSpacing.s)
        )
        Box(
            Modifier.weight(1f).height(1.dp).background(
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outlineVariant, Color.Transparent))
            )
        )
    }
}
```

- [ ] **Step 2: Insert dividers in the LazyColumn**

In the `itemsIndexed` block, before each `DisplayMessage.Player` that isn't the first message, emit a `TurnDivider`. Track turn count:

```kotlin
var turnCount = 0
itemsIndexed(state.messages) { idx, msg ->
    // Emit turn divider before each Narration (which starts a new AI turn)
    if (msg is DisplayMessage.Narration && idx > 0) {
        turnCount++
        TurnDivider(turnCount)
    }
    when (msg) { ... }
}
```

Add needed imports: `Brush`, `Color`, `Box`, `background`, `height`.

- [ ] **Step 3: Verify and commit**

Run: `gradle compileDebugKotlin 2>&1 | tail -10`

```bash
git commit -am "Add turn dividers with ornamental TURN N labels between narrator turns"
```

---

### Task 7: Rework prose narration and segment dispatch in NarrationBlock

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt`

- [ ] **Step 1: Add left accent bar to NarratorProseBubble**

In MessageBubbles.kt (or NarrationBlock.kt, wherever `NarratorProseBubble` lives), modify the prose Surface to include a left border. Replace the `Surface` with a `Surface` that has a `drawBehind` or a `Row` with a colored `Box` divider:

In NarrationBlock.kt, find the `NarratorProseBubble` composable's Surface and add a `Modifier.border`:

```kotlin
Surface(
    color = realms.narratorBubble,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth().drawBehind {
        drawRect(
            color = realms.asideOnBubble.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = Size(3.dp.toPx(), size.height)
        )
    }
) {
```

This draws a 3dp left accent bar in aside purple at 30% alpha.

- [ ] **Step 2: Create distinct segment composables**

In NarrationBlock.kt, replace the `NarratorQuipBubble` calls for each segment type with distinct composables:

**NpcActionLine** — left-aligned with NPC color accent:
```kotlin
@Composable
private fun NpcActionLine(text: String, accentColor: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.xs)) {
        Box(Modifier.width(2.dp).height(IntrinsicSize.Min).background(accentColor.copy(alpha = 0.3f)))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = FontStyle.Italic,
                fontSize = (13f * LocalFontScale.current).sp
            ),
            color = accentColor.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = RealmsSpacing.s)
        )
    }
}
```

**NarratorAsideLine** — centered with tildes:
```kotlin
@Composable
private fun NarratorAsideLine(text: String) {
    Text(
        "~ $text ~",
        style = MaterialTheme.typography.bodySmall.copy(
            fontStyle = FontStyle.Italic,
            fontSize = (13f * LocalFontScale.current).sp
        ),
        color = RealmsTheme.colors.asideOnBubble.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.xxl, vertical = RealmsSpacing.xs)
    )
}
```

**PlayerActionLine** — right-aligned with gold accent:
```kotlin
@Composable
private fun PlayerActionLine(text: String) {
    val realms = RealmsTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.xs),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = FontStyle.Italic,
                fontSize = (13f * LocalFontScale.current).sp
            ),
            color = realms.goldAccent.copy(alpha = 0.8f),
            modifier = Modifier.padding(end = RealmsSpacing.s),
            textAlign = TextAlign.End
        )
        Box(Modifier.width(2.dp).height(IntrinsicSize.Min).background(realms.goldAccent.copy(alpha = 0.3f)))
    }
}
```

- [ ] **Step 3: Update segment dispatch**

In the `structuredSegments.forEach { seg -> when(seg) }` block, replace:

```kotlin
is NarrationSegmentData.Aside -> NarratorAsideLine(text = seg.text)
is NarrationSegmentData.NpcAction -> {
    val (accent, _) = npcColor(seg.speaker ?: "", RealmsTheme.colors.npcPalette)
    NpcActionLine(text = actionText, accentColor = accent)
}
is NarrationSegmentData.PlayerAction -> PlayerActionLine(text = seg.text)
```

Note: `NpcAction` segments should have a `speaker` field — check the data class. If not, default to `onSurfaceVariant`.

- [ ] **Step 4: Verify and commit**

Run: `gradle compileDebugKotlin 2>&1 | tail -10`

```bash
git commit -am "Rework narration: prose accent bar, distinct NPC action/aside/player action styles"
```

---

### Task 8: Rework NPC and Player dialog bubbles

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt`

- [ ] **Step 1: Rework NpcDialogueBubble — avatar column layout**

Replace the inner layout of `NpcDialogueBubble`. The key changes:
- Avatar moves to its own left `Column` (32dp instead of 24dp)
- Bubble gets asymmetric corners: `RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)`
- Background becomes lighter: `bgTint.copy(alpha = 0.12f)` with 1dp accent border at 20%
- Name label uppercased in `labelSmall`
- Avatar gets gradient background: `Brush.linearGradient(accent.copy(0.4f), accent.copy(0.15f))`

The full inner layout:

```kotlin
Row(
    Modifier.fillMaxWidth(0.92f).padding(vertical = RealmsSpacing.xs),
    horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s),
    verticalAlignment = Alignment.Top
) {
    // Avatar
    Box(
        Modifier.size(32.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(0.4f), accent.copy(0.15f))))
            .border(1.5.dp, accent.copy(0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, style = MaterialTheme.typography.titleSmall, color = accent)
    }
    // Bubble
    Surface(
        color = bgTint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
        modifier = Modifier.weight(1f)
    ) {
        Column(Modifier.padding(RealmsSpacing.m)) {
            Text(name.uppercase(), style = labelSmall, color = accent, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(RealmsSpacing.xs))
            // Quote with parseInline, italic
        }
    }
}
```

Keep the existing bookmark icon, reaction picker, and swipe functionality — just change the layout structure.

- [ ] **Step 2: Rework PlayerBubble — mirrored avatar column**

Replace the inner layout of `PlayerBubble`:

```kotlin
Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End
) {
    Row(
        Modifier.fillMaxWidth(0.85f),
        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s),
        verticalAlignment = Alignment.Top
    ) {
        // Bubble (weight 1)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp),
            border = BorderStroke(1.dp, realms.goldAccent.copy(alpha = 0.3f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(Modifier.padding(RealmsSpacing.m)) {
                // Quote with parseInline, italic — no "You:" label
            }
        }
        // Avatar
        Box(
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(realms.goldAccent.copy(0.3f), realms.goldAccent.copy(0.1f))))
                .border(1.5.dp, realms.goldAccent.copy(0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, style = MaterialTheme.typography.titleSmall, color = realms.goldAccent)
        }
    }
}
```

Remove the "You:" / "$displayName:" label — the gold avatar is sufficient.

- [ ] **Step 3: Verify and commit**

Run: `gradle compileDebugKotlin 2>&1 | tail -10`

```bash
git commit -am "Rework dialog bubbles: avatar columns, asymmetric corners, lighter backgrounds"
```

---

### Task 9: Rework SceneBanner and StatChangePills

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt`

- [ ] **Step 1: Rework SceneBanner**

Replace the `Surface` with a gradient + accent bar treatment:

```kotlin
Surface(
    onClick = { if (isLong) expanded = !expanded },
    enabled = isLong,
    color = Color.Transparent,
    modifier = Modifier
        .fillMaxWidth()
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
```

Where `secondary = MaterialTheme.colorScheme.secondary`.

- [ ] **Step 2: Add emoji prefixes to StatChangePills**

In `StatChangePills`, modify the pill text construction to include emoji prefixes:

```kotlin
// HP
if (msg.hpBefore != msg.hpAfter) {
    val delta = msg.hpAfter - msg.hpBefore
    val prefix = "♥ "
    pills.add(Triple("$prefix${formatSigned(delta)} HP", ...))
}
// XP
if (msg.xpGained > 0) {
    pills.add(Triple("★ +${msg.xpGained} XP", ...))
}
// Gold
if (msg.goldBefore != msg.goldAfter) {
    val delta = msg.goldAfter - msg.goldBefore
    pills.add(Triple("💰 ${formatSigned(delta)}g", ...))
}
// Moral
if (msg.moralDelta != 0) {
    val prefix = if (msg.moralDelta > 0) "⚖ " else "⚖ "
    pills.add(Triple("$prefix${formatSigned(msg.moralDelta)} Moral", ...))
}
// Rep
msg.repDeltas.forEach { (faction, delta) ->
    pills.add(Triple("💡 ${formatSigned(delta)} $faction", ...))
}
```

Items gained/lost and conditions keep their existing text format.

- [ ] **Step 3: Verify and commit**

Run: `gradle compileDebugKotlin 2>&1 | tail -10`

```bash
git commit -am "Rework SceneBanner (gradient + accent bar) and add emoji prefixes to stat pills"
```

---

### Task 10: Final verification

- [ ] **Step 1: Run tests**

Run: `gradle test 2>&1 | tail -20`

- [ ] **Step 2: Build and install**

Run: `gradle installDebug && adb shell am start -n com.realmsoffate.game/.MainActivity`

- [ ] **Step 3: Verify on device**

Check:
- Character tab shows pager with 5 sub-tabs (Stats/Inventory/Spells/Party/Currency)
- Journal tab shows pager with 3 sub-tabs (Quests/NPCs/Lore)
- Bookmark icon in top bar opens Memories sheet
- `/memories` slash command works
- Turn dividers appear between narrator turns
- NPC dialog has avatar column with speech-bubble tail
- Player dialog mirrors NPC on right side
- Prose narration has subtle left accent bar
- NPC actions show character color accent
- Asides have ~ tildes ~
- Scene banners have gradient background
- Stat pills have emoji prefixes
