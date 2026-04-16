# UI Phase 3: Game Chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 5-tab bottom nav + More sheet with a 4-tab nav (Chat/Map/Character/Journal) + contextual FAB. Implement a two-row collapsing top bar. Move settings to a gear icon. Extract remaining inline composables from GameScreen.kt.

**Architecture:** Extract-first, rework-second. Extract standalone composables (dialogs, HUD, overlays) into focused files, then rework the navigation structure. GameScreen.kt becomes a pure scaffold wiring file.

**Tech Stack:** Kotlin, Jetpack Compose Material 3

---

## File Structure

After this phase:

| File | Responsibility | Lines from |
|------|---------------|-----------|
| `GameScreen.kt` | Scaffold, state, nav wiring only | Trimmed to ~400-500 |
| `GameDialogs.kt` | PreRollDialog, DeathSaveDialog, BreakdownRow, ScoreCol | Extracted |
| `CombatHud.kt` | CombatHud, InitiativeChip | Extracted |
| `GameOverlays.kt` | TutorialOverlay, ChoicesSheet, SettingsSheet | Extracted + reworked |
| `TopBar.kt` | Two-row collapsing top bar with gear icon | Modified |
| `BottomNav.kt` | 4-tab NavigationBar | New, replaces old BottomNav |

---

### Task 1: Extract GameDialogs.kt

Move the large dialog composables out of GameScreen.kt.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameDialogs.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create GameDialogs.kt**

Extract from GameScreen.kt:
- `PreRollDialog` (lines 912-1214) — the animated d20 dice roll dialog
- `BreakdownRow` (lines 1222-1241) — helper for PreRollDialog
- `DeathSaveDialog` (lines 1370-1441) — death save pips + roll button
- `ScoreCol` (lines 1444-1463) — helper for DeathSaveDialog

Add package, imports. Change `private` to `internal` on `PreRollDialog` and `DeathSaveDialog` (called from GameScreen). `BreakdownRow` and `ScoreCol` stay `private`.

- [ ] **Step 2: Remove from GameScreen.kt**

Delete all 4 composables.

- [ ] **Step 3: Verify**

Run: `gradle test` and `gradle assembleDebug` — both pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameDialogs.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Extract GameDialogs.kt (PreRollDialog, DeathSaveDialog)"
```

---

### Task 2: Extract CombatHud.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/CombatHud.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create CombatHud.kt**

Extract:
- `CombatHud` (lines 1244-1299) — red combat HUD with round counter + initiative
- `InitiativeChip` (lines 1302-1367) — individual combatant chip

Change `CombatHud` to `internal`. `InitiativeChip` stays `private`.

**Fix while extracting:** In `InitiativeChip`, find the active-player chip border color. If it uses `MaterialTheme.colorScheme.error`, change it to `RealmsTheme.colors.goldAccent` so the player's turn is visually distinct from enemy chips.

Also add `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to the combatant name `Text` inside `InitiativeChip`.

- [ ] **Step 2: Remove from GameScreen.kt**

Delete both composables.

- [ ] **Step 3: Verify**

Run: `gradle test` and `gradle assembleDebug` — both pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/CombatHud.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Extract CombatHud.kt; fix player chip color and name overflow"
```

---

### Task 3: Extract GameOverlays.kt

Move remaining overlay/sheet composables.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create GameOverlays.kt**

Extract:
- `TutorialOverlay` (lines 644-743)
- `ChoicesSheet` (lines 800-818) + `ChoiceTile` if still in GameScreen (may already be in MessageBubbles.kt)
- `MemoriesPanel` (lines 463-536)
- `SettingsPanel` (lines 544-638)

All become `internal`. SettingsPanel will later become `SettingsSheet` (just a rename in the call site — the composable is already a ModalBottomSheet).

- [ ] **Step 2: Remove from GameScreen.kt**

Delete all extracted composables.

- [ ] **Step 3: Verify**

Run: `gradle test` and `gradle assembleDebug` — both pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Extract GameOverlays.kt (TutorialOverlay, ChoicesSheet, MemoriesPanel, SettingsPanel)"
```

---

### Task 4: Rework Bottom Navigation to 4 Tabs

Replace the 5-tab nav (Chat/Items/Stats/Map/More) with 4 tabs (Chat/Map/Character/Journal). Delete the More sheet — its actions move to the settings gear and the new tabs.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/game/BottomNav.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create BottomNav.kt with new 4-tab nav**

Create a new file with the new tab enum and NavigationBar:

```kotlin
package com.realmsoffate.game.ui.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class GameTab(val label: String, val icon: ImageVector) {
    Chat("Chat", Icons.Filled.ChatBubble),
    Map("Map", Icons.Filled.Map),
    Character("Character", Icons.Filled.Person),
    Journal("Journal", Icons.Filled.MenuBook)
}

@Composable
internal fun GameBottomNav(
    selected: GameTab,
    onSelect: (GameTab) -> Unit
) {
    NavigationBar {
        GameTab.entries.forEach { t ->
            NavigationBarItem(
                selected = t == selected,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, t.label) },
                label = { Text(t.label) }
            )
        }
    }
}
```

- [ ] **Step 2: Update GameScreen.kt navigation state machine**

In `GameScreen.kt`:
1. Replace `private enum class Tab` with an import of `GameTab` from `BottomNav.kt`
2. Replace `var tab by remember { mutableStateOf(Tab.Chat) }` with `var tab by remember { mutableStateOf(GameTab.Chat) }`
3. Replace the `LaunchedEffect(tab)` block (lines 144-152) with new tab→panel mapping:

```kotlin
LaunchedEffect(tab) {
    when (tab) {
        GameTab.Chat -> panel = Panel.None
        GameTab.Map -> panel = Panel.Map
        GameTab.Character -> panel = Panel.Inventory  // default sub-tab
        GameTab.Journal -> panel = Panel.Quests       // default sub-tab
    }
}
```

4. Delete the old `BottomNav` composable, `ChoicesFab` composable, `MoreMenuSheet` composable, and `MoreTile` composable from GameScreen.kt (or GameOverlays.kt if they were already moved).
5. Delete the `moreOpen` state variable and all references to it.
6. Replace the `bottomBar` Scaffold slot to use `GameBottomNav`:

```kotlin
bottomBar = {
    Column(Modifier.imePadding()) {
        if (tab == GameTab.Chat) {
            GameInputBar(state, input, onInputChange = { input = it }, ...)
        }
        GameBottomNav(selected = tab, onSelect = { tab = it })
    }
}
```

7. The input bar only shows on the Chat tab — hide it on Map/Character/Journal.

- [ ] **Step 3: Delete the More menu routing**

Remove the `MoreMenuSheet` render call and all `moreOpen`-related code. The actions that were in More now live:
- Save/Download/Debug → Settings gear (handled in Task 5)
- Short Rest/Long Rest → Contextual FAB (handled in Task 5)
- Spells/Lore/Journal/Quests/Party/Currency/Memories → Now reachable via the Character and Journal tabs directly
- Main Menu/Setup → Settings gear

- [ ] **Step 4: Verify**

Run: `gradle test` and `gradle assembleDebug` — both pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/BottomNav.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Rework bottom nav: 4 tabs (Chat/Map/Character/Journal), delete More sheet"
```

---

### Task 5: Add Settings Gear + Contextual FAB

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Add settings gear to TopBar**

In `TopBar.kt`, modify `GameTopBar` to accept an `onSettingsClick: () -> Unit` parameter. Add a gear `IconButton` at the right end of the first row:

```kotlin
IconButton(onClick = onSettingsClick) {
    Icon(Icons.Default.Settings, contentDescription = "Settings")
}
```

- [ ] **Step 2: Implement contextual FAB in GameScreen.kt**

Replace the old `ChoicesFab` in the Scaffold's `floatingActionButton` slot with a contextual FAB that changes based on game state:

```kotlin
floatingActionButton = {
    if (tab == GameTab.Chat) {
        when {
            state.currentChoices.isNotEmpty() && !state.isGenerating -> {
                ExtendedFloatingActionButton(
                    onClick = { choicesOpen = true },
                    icon = { Icon(Icons.Default.List, "Choices") },
                    text = { Text("${state.currentChoices.size} choices") }
                )
            }
            state.combat != null -> {
                FloatingActionButton(onClick = {
                    vm.requestTargetPrompt(TargetPromptSpec(
                        title = "Attack",
                        verb = "I attack",
                        recentTargets = state.combat.order.filter { !it.isPlayer }.map { it.name }
                    ))
                }) {
                    Icon(Icons.Default.GpsFixed, "Attack")
                }
            }
            state.currentScene != "battle" && state.deathSave == null && !state.isGenerating -> {
                // Safe location — offer rest
                FloatingActionButton(onClick = { /* open rest options */ }) {
                    Icon(Icons.Default.LocalFireDepartment, "Rest")
                }
            }
        }
    }
}
```

For the rest FAB, create a small bottom sheet or dialog that offers Short Rest / Long Rest options. Keep it simple — two buttons in an `AlertDialog`.

- [ ] **Step 3: Wire settings gear to open SettingsPanel**

In GameScreen.kt, pass `onSettingsClick = { panel = Panel.Settings }` to `GameTopBar`. The SettingsPanel already exists and opens via the panel routing.

Add the More menu's utility actions (Save, Download, Debug Dump, Main Menu) into the SettingsPanel. Read `GameOverlays.kt` to find `SettingsPanel` and add buttons for:
- Save Game → `vm.saveToSlot()`
- Export Save → trigger the export launcher
- Debug Dump → call `dumpDebugToFile()`
- Return to Title → `vm.returnToTitle()`

- [ ] **Step 4: Verify**

Run: `gradle test` and `gradle assembleDebug` — both pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt
git commit -m "Add settings gear to top bar and contextual FAB (choices/combat/rest)"
```

---

### Task 6: Final Cleanup and Verification

- [ ] **Step 1: Check GameScreen.kt line count**

Run: `wc -l app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`
Target: ~400-600 lines (scaffold + state + panel routing only).

- [ ] **Step 2: Clean up unused imports**

Remove any imports in GameScreen.kt that are no longer needed after extractions.

- [ ] **Step 3: Run full test suite + release build**

Run: `gradle test` — all pass. `gradle assembleRelease` — BUILD SUCCESSFUL.

- [ ] **Step 4: Verify on device**

Run: `gradle installDebug && adb shell am start -n com.realmsoffate.game/.MainActivity`

Verify:
- 4 tabs visible: Chat, Map, Character, Journal
- No More tab or More sheet
- Settings gear in top bar opens settings panel
- Contextual FAB: shows choices count during choices, sword during combat, hidden otherwise
- Input bar only visible on Chat tab
- Character tab opens (will show Inventory panel for now — Phase 4 adds the tabbed sub-view)
- Journal tab opens (will show Quests panel for now — Phase 4 adds the tabbed sub-view)

- [ ] **Step 5: Commit cleanup**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Clean up GameScreen imports after Phase 3 extractions"
```
