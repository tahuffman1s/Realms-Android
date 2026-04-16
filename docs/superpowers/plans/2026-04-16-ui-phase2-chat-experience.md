# UI Phase 2: Chat Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split GameScreen.kt (3,311 lines) into focused files, implement document-order segment rendering, collapsible narration, and fix all chat UX bugs — making the chat feel like a polished native Android messaging app.

**Architecture:** Extract-first, modify-second. Each task produces a compiling, working app. Pure mechanical extractions (move code, update imports) come before behavioral changes (rendering, collapsibility). The existing `GameScreen.kt` is the source; new files are created in `app/src/main/kotlin/com/realmsoffate/game/ui/game/`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Robolectric JUnit 4

---

## File Structure

After this phase, the `ui/game/` directory will contain:

| File | Responsibility | Source lines from GameScreen.kt |
|------|---------------|-------------------------------|
| `GameScreen.kt` | Scaffold, state collection, wiring, panel routing | ~97-534 (trimmed) |
| `ChatInput.kt` | Text field, send button, combat hotbar, slash commands | ~1084-1210, 1407-1455 |
| `MessageBubbles.kt` | PlayerBubble, NpcDialogueBubble, NarratorQuipBubble, EventCard, SystemLine, NarratorThinking, SwipeableMessage | ~2078-2260, 2901-3324 |
| `NarrationBlock.kt` | Narration dispatcher, collapsible prose, document-order segments, ProseDetailDialog replacement | ~2280-2899, 2482-2691 |
| `ChatFeed.kt` | LazyColumn, scroll behavior, empty state, error card, merchant row | ~236-343 (extracted + enhanced) |
| `TopBar.kt` | GameTopBar + all inline widgets (HP, XP, gold, location, conditions, combat, party, level) | ~828-1078 |

---

### Task 1: Extract ChatInput.kt

The most self-contained extraction. `GameInputBar` + `ActionChip` + `SpellChip` + `handleSlashCommand` have no dependencies on other composables in GameScreen.kt.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatInput.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create ChatInput.kt**

Create a new file containing these composables and functions extracted from GameScreen.kt:
- `GameInputBar` (lines 1084-1182) — the main input composable
- `ActionChip` (lines 1184-1206) — combat action chip
- `SpellChip` (lines 1224-1261) — spell hotbar chip
- `handleSlashCommand` (lines 1407-1455) — slash command helper
- `isSelfCastable` (lines 1212-1222) — spell helper function

Add the correct package declaration (`package com.realmsoffate.game.ui.game`) and all necessary imports. Change `private` visibility to `internal` on any functions that GameScreen.kt still calls.

- [ ] **Step 2: Apply input UX fixes in the new file**

In `GameInputBar` in the new `ChatInput.kt`:
- Add `enabled = !state.isGenerating` to the `OutlinedTextField` (currently only the send button is disabled)
- Change `placeholder` to: `Text(if (state.isGenerating) "Narrator is writing..." else "What do you do?", style = MaterialTheme.typography.bodyMedium)`
- Change `maxLines = 3` to `maxLines = 5`

- [ ] **Step 3: Remove the extracted code from GameScreen.kt**

Delete `GameInputBar`, `ActionChip`, `SpellChip`, `handleSlashCommand`, and `isSelfCastable` from GameScreen.kt. Add `import com.realmsoffate.game.ui.game.GameInputBar` if needed (it's in the same package so may not need an explicit import).

- [ ] **Step 4: Verify build and tests**

Run: `gradle test`
Expected: PASS. Run: `gradle assembleDebug` — BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatInput.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Extract ChatInput.kt from GameScreen; fix input UX (enabled state, placeholder, maxLines)"
```

---

### Task 2: Extract TopBar.kt

All top bar composables form a self-contained group with no outgoing dependencies.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create TopBar.kt**

Extract these composables from GameScreen.kt into a new file:
- `GameTopBar` (lines 828-880)
- `ConditionsStrip` (lines 882-891)
- `ConditionChip` (lines 893-935)
- `LevelBadge` (lines 937-952)
- `CombatIndicator` (lines 954-967)
- `PartyIcons` (lines 969-993)
- `HpInline` (lines 995-1023)
- `XpInline` (lines 1025-1049)
- `GoldInline` (lines 1051-1063)
- `LocationInline` (lines 1065-1078)

Add correct package, imports. Change `private` to `internal` where GameScreen.kt calls them.

- [ ] **Step 2: Remove extracted code from GameScreen.kt**

Delete all 10 composables from GameScreen.kt.

- [ ] **Step 3: Verify build and tests**

Run: `gradle test` — PASS. `gradle assembleDebug` — BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Extract TopBar.kt from GameScreen (10 composables)"
```

---

### Task 3: Extract MessageBubbles.kt

All individual bubble composables except the narration dispatcher.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create MessageBubbles.kt**

Extract these composables and helpers:
- `PlayerBubble` (lines 2078-2155)
- `StatChangePills` (lines 2157-2260)
- `NarratorQuipBubble` (lines 2901-2923)
- `NpcDialogueBubble` (lines 2945-3146)
- `SwipeableMessage` (lines 3148-3218)
- `EventCard` (lines 3220-3242)
- `SystemLine` (lines 3244-3267)
- `NarratorThinking` (lines 3269-3290)
- `ChoiceTile` (lines 3292-3324)
- `BubbleWithReaction` (lines 2453-2480) — currently unused but extract anyway
- `sceneEmoji` (line 2069-2076) — helper used by SceneBanner and bubbles
- `npcColorPalette` (lines 2926-2937) — file-scope val
- `npcColor` (lines 2939-2943) — helper
- `resolveNpcDisplayName` (lines 2268-2278) — helper
- `formatSignedRoll` (line 1791) — helper used by PreRollDialog/bubbles

Add correct package, imports. Change `private` to `internal` where needed.

- [ ] **Step 2: Fix NPC dialogue bubble name overflow**

In `NpcDialogueBubble` in the new file, add `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to the NPC name `Text` composable.

- [ ] **Step 3: Remove extracted code from GameScreen.kt**

Delete all extracted composables and helpers from GameScreen.kt.

- [ ] **Step 4: Verify build and tests**

Run: `gradle test` — PASS. `gradle assembleDebug` — BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Extract MessageBubbles.kt from GameScreen (15 composables + helpers)"
```

---

### Task 4: Extract NarrationBlock.kt with Document-Order Rendering

This is the behavioral change task. Extract the narration dispatcher AND rewrite it to render segments in document order instead of merging prose to the top.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create NarrationBlock.kt**

Extract from GameScreen.kt:
- `NarrationBlock` (lines 2280-2450)
- `NarratorBubble` (lines 2693-2789)
- `ProseDetailDialog` (lines 2816-2899) — will be deleted in Step 3
- `summarizeProse` (lines 2792-2814)
- `NarrationSegment` sealed class (lines 2482-2491)
- `splitNarration` function (lines 2492-2691)

- [ ] **Step 2: Rewrite NarrationBlock to render in document order**

Replace the current `NarrationBlock` implementation. The current version merges all prose to the top, then renders other segments. The new version should:

1. If `structuredSegments` is non-empty, iterate `structuredSegments` in order and render each segment as its own bubble using the composables from `MessageBubbles.kt`:
   - `NarrationSegmentData.NarratorProse` → render with `NarratorBubble` (but inline, not summary+dialog)
   - `NarrationSegmentData.NarratorAside` → render with `NarratorQuipBubble`
   - `NarrationSegmentData.NpcDialog` → render with `NpcDialogueBubble`
   - `NarrationSegmentData.NpcAction` → render as italic text (no bubble, just styled)
   - `NarrationSegmentData.PlayerAction` → render with `PlayerBubble`
   - `NarrationSegmentData.PlayerDialog` → render with `PlayerBubble`
2. If `structuredSegments` is empty, fall back to the legacy `splitNarration` path (keep existing behavior).
3. Append `StatChangePills` at the end of either path.

- [ ] **Step 3: Implement collapsible narration**

Replace `NarratorBubble` with a new implementation that:
- When `isLatestTurn = true`: renders full prose inline using `NarrationMarkdown` directly — no summary, no dialog
- When `isLatestTurn = false`: renders a 2-3 line preview (`summarizeProse`) with a "Tap to expand" affordance. Tapping toggles an `expanded` state that shows the full content inline via `AnimatedVisibility`. No modal dialog.

Delete `ProseDetailDialog` — it is replaced by the inline expansion.

- [ ] **Step 4: Fix legacy swipe-attack guard**

In the `splitNarration` fallback path inside `NarrationBlock`, find the left-swipe handler for NPC dialogue segments. Add the `isLatestTurn` guard that the structured path already has:

```kotlin
onSwipeLeft = if (isLatestTurn) { { onAttackNpc(seg.name) } } else null,
```

- [ ] **Step 5: Remove extracted code from GameScreen.kt**

Delete `NarrationBlock`, `NarratorBubble`, `ProseDetailDialog`, `summarizeProse`, `NarrationSegment`, and `splitNarration` from GameScreen.kt.

- [ ] **Step 6: Verify build and tests**

Run: `gradle test` — PASS. `gradle assembleDebug` — BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Extract NarrationBlock.kt: document-order segments, collapsible narration, delete ProseDetailDialog"
```

---

### Task 5: Extract ChatFeed.kt with UX Improvements

Extract the LazyColumn and its surrounding logic into a dedicated composable, adding empty state and improved error handling.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create ChatFeed.kt**

Create a `ChatFeed` composable that encapsulates the LazyColumn (currently lines 236-343 of GameScreen.kt). It should accept:

```kotlin
@Composable
fun ChatFeed(
    state: GameUiState,
    listState: LazyListState,
    bookmarks: List<String>,
    onToggleBookmark: (String) -> Unit,
    onNpcReply: (String) -> Unit,
    onNpcReaction: (String, String, String) -> Unit,
    onAttackNpc: (String) -> Unit,
    onOpenJournal: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenShop: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
)
```

Move the `itemsIndexed(state.messages)` dispatch, the `NarratorThinking` item, the merchant row, and the error card into this composable.

- [ ] **Step 2: Add empty state**

At the top of the `LazyColumn`, before `itemsIndexed`, add:

```kotlin
if (state.messages.isEmpty() && !state.isGenerating) {
    item {
        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
            val ch = state.character
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ch?.name ?: "Adventurer",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (ch != null) {
                    Text(
                        "${ch.race} ${ch.cls}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Your story begins...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
```

- [ ] **Step 3: Improve error card**

Replace the auto-dismissing error card with a persistent one that has an X button:

```kotlin
state.error?.let { err ->
    item {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Row(
                Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = onClearError) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
```

Remove the `LaunchedEffect(err)` auto-dismiss — the error stays until the user taps X.

- [ ] **Step 4: Fix onNpcReply focus**

In the `ChatFeed` composable, the `onNpcReply` callback is passed from GameScreen.kt. In GameScreen.kt where the callback is defined (around line 254-257), change:

```kotlin
input = "I say to $npcName: "
focus.clearFocus()
```

To:

```kotlin
input = "I say to $npcName: "
focusRequester.requestFocus()
```

Make sure there's a `val focusRequester = remember { FocusRequester() }` in GameScreen and that the input field in ChatInput.kt has `.focusRequester(focusRequester)` on its modifier. The `focusRequester` will need to be threaded through or hoisted to GameScreen level.

- [ ] **Step 5: Update GameScreen.kt**

Replace the inline LazyColumn code with a call to `ChatFeed(...)`, passing all necessary callbacks.

- [ ] **Step 6: Verify build and tests**

Run: `gradle test` — PASS. `gradle assembleDebug` — BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Extract ChatFeed.kt: empty state, improved error card, focus fix"
```

---

### Task 6: Bookmark Touch Targets + Accessibility

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt`

- [ ] **Step 1: Fix bookmark touch targets**

In all bubble composables that have bookmark toggle icons (`PlayerBubble`, `NarratorQuipBubble`, `NarratorBubble`, and `NpcDialogueBubble`), find the bookmark `IconButton` or `Surface` and ensure the touch target is at minimum 48dp:

```kotlin
IconButton(
    onClick = onToggleBookmark,
    modifier = Modifier.size(48.dp)
) {
    Icon(
        if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
        modifier = Modifier.size(20.dp)  // icon size stays small
    )
}
```

The `IconButton` composable has a 48dp minimum by default, but some usages use `Surface` with `Modifier.size(24.dp)` instead — replace those with `IconButton`.

- [ ] **Step 2: Add semantics to SwipeableMessage**

In `SwipeableMessage` in `MessageBubbles.kt`, add accessibility actions so screen reader users can trigger swipe actions:

```kotlin
Modifier.semantics {
    if (onSwipeLeft != null) {
        customActions = listOf(
            CustomAccessibilityAction(leftLabel ?: "Action") { onSwipeLeft(); true }
        ) + if (onSwipeRight != null) {
            listOf(CustomAccessibilityAction(rightLabel ?: "Info") { onSwipeRight(); true })
        } else emptyList()
    }
}
```

Add import `import androidx.compose.ui.semantics.*`.

- [ ] **Step 3: Verify build and tests**

Run: `gradle test` — PASS. `gradle assembleDebug` — BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt
git commit -m "Fix bookmark touch targets (48dp min) and add swipe a11y semantics"
```

---

### Task 7: Final Verification and Cleanup

- [ ] **Step 1: Verify GameScreen.kt line count**

Run: `wc -l app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`
Expected: significantly smaller than 3,311 (target ~800-1200 lines with panels still inline).

- [ ] **Step 2: Run full test suite**

Run: `gradle test`
Expected: All 54 tests pass.

- [ ] **Step 3: Run release build**

Run: `gradle assembleRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Install and verify on device**

Run: `gradle installDebug && adb shell am start -n com.realmsoffate.game/.MainActivity`

Visually verify:
- Chat feed renders messages in document order (NPC dialogue interleaved with prose, not grouped after)
- Latest turn narration is fully expanded; older turns show collapsed preview
- Tapping collapsed narration expands inline (no modal dialog)
- Input field disabled during AI generation with "Narrator is writing..." placeholder
- NPC reply prefills text AND opens keyboard
- Empty state card shown on fresh game before first turn
- Error card has X dismiss button, doesn't auto-dismiss
- Bookmark icons have proper 48dp touch targets
- No compilation warnings about unused imports

- [ ] **Step 5: Clean up any unused imports in GameScreen.kt**

After all extractions, GameScreen.kt will have many imports that moved to the new files. Remove unused imports.

- [ ] **Step 6: Commit cleanup**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Clean up unused imports in GameScreen after Phase 2 extractions"
```
