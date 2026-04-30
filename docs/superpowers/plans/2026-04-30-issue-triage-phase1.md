# Issue Triage Phase 1 — Release-Safety + Small Bug/Polish Bundle

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close 10 well-specified issues in a single coordinated pass: ship-blocking release-safety fixes (#14, #16), correctness bugs (#13, #15, #17), and polish (#11, #12, #19, #21). Items #18, #20, #22, #23, #25, #26, #27, #29 are deferred to follow-up plans.

**Architecture:** Each task touches a small surface and is independently testable. Order is: release-safety → correctness → polish. Commits per task so any task can be reverted in isolation. Where TDD adds value (logic with a clear pre/post condition), tests come first; for pure UI surface changes (icon swap, padding, bullet removal), the change is verified by deploy + visual check + screenshot via the debug bridge.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle Kotlin DSL, JUnit/Robolectric (`gradle test`). Verification through emulator deploy + Debug Bridge per `CLAUDE.md`.

**Issues covered (in execution order):**
1. **#16** Debug Bridge must not ship in release builds (verification only — already in `src/debug/`)
2. **#14** Hide Debug Dump action in release builds
3. **#17** Racial ability bonuses apply by default on character creation
4. **#13** Characters spawn with clothes in addition to armor
5. **#15** Don't render player input bubbles (still send to LLM)
6. **#11** NPC dialog right-swipe deep-links to that NPC's journal entry
7. **#21** Warn before Begin if point-buy points are unspent
8. **#12** NPC dialog: drop leading bullet, match Character bubble padding
9. **#19** Replace die icon on Recommended stats button
10. Final batch verification + commit cleanup

---

## Task 1: Verify Debug Bridge is excluded from release APK (#16)

**Files:**
- Inspect: `app/src/debug/kotlin/com/realmsoffate/game/debug/` (already source-set isolated)
- Inspect: `app/src/main/AndroidManifest.xml`, `app/src/debug/AndroidManifest.xml` (if present)
- Inspect: `app/build.gradle.kts`

The bridge's source already lives under `app/src/debug/`. This task is verification, not relocation.

- [ ] **Step 1: Confirm no main-source-set references to debug bridge classes**

```bash
grep -rn "DebugBridge\|DebugServer\|DebugSerializer\|com.realmsoffate.game.debug" app/src/main --include="*.kt"
```

Expected: **No matches.** If any appear, they must be moved to `src/debug/` or guarded by an interface defined in main with the implementation in debug.

- [ ] **Step 2: Confirm no INTERNET permission leaks into release manifest**

```bash
cat app/src/main/AndroidManifest.xml | grep -i "INTERNET\|permission"
ls app/src/debug/AndroidManifest.xml 2>/dev/null && cat app/src/debug/AndroidManifest.xml
```

Expected: `INTERNET` permission (if needed for the bridge) must be in `app/src/debug/AndroidManifest.xml` only. If it's in `app/src/main/AndroidManifest.xml` and the only consumer is the debug bridge, move it. If the LLM client also needs internet, this stays in main — note it and skip.

- [ ] **Step 3: Build a release APK**

```bash
gradle assembleRelease
```

Expected: BUILD SUCCESSFUL. Note the APK path printed in output.

- [ ] **Step 4: Verify the release APK does not contain bridge classes**

```bash
APK=$(find app/build/outputs/apk/release -name "*.apk" | head -1)
unzip -p "$APK" classes.dex | strings | grep -i "DebugBridge\|DebugServer\|DescribeEndpoints" | head -5
```

Expected: **No matches.** (If `dex` strings is too noisy, `apkanalyzer dex packages "$APK" | grep debug` is an alternative.)

- [ ] **Step 5: Document findings**

If steps 1–4 passed, no code change needed — append a one-line confirmation to the PR/commit. If anything failed, **stop and convert this task into a code change**: move the offending file to `src/debug/` and re-run.

- [ ] **Step 6: Commit (only if any change was needed)**

```bash
git add app/src/main app/src/debug app/build.gradle.kts
git commit -m "chore: ensure debug bridge stays out of release APK (#16)"
```

If no change was needed, skip the commit and note "verified clean" in the Phase 1 summary commit at the end.

---

## Task 2: Hide Debug Dump action in release builds (#14)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt:159-163`

The `ActionIcon` for "Debug Dump" is currently shown unconditionally. Gate it on `BuildConfig.DEBUG`.

- [ ] **Step 1: Open the file and locate the call site**

The relevant block is around line 159–163:

```kotlin
ActionIcon(
    icon = Icons.Filled.BugReport,
    label = "Debug Dump",
    onClick = { onDebugDump(); onClose() }
)
```

- [ ] **Step 2: Wrap the ActionIcon in a `BuildConfig.DEBUG` guard**

Replace the block with:

```kotlin
if (BuildConfig.DEBUG) {
    ActionIcon(
        icon = Icons.Filled.BugReport,
        label = "Debug Dump",
        onClick = { onDebugDump(); onClose() }
    )
}
```

Confirm `BuildConfig` is already imported in this file (it is — used at line 182 for `VERSION_NAME`). No new import needed.

- [ ] **Step 3: Compile**

```bash
gradle :app:compileDebugKotlin :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL on both variants.

- [ ] **Step 4: Manual verification on debug**

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Open Settings panel, confirm "Debug Dump" is **present** in debug build.

- [ ] **Step 5: Manual verification on release**

```bash
gradle installRelease
adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity
```

Open Settings panel, confirm "Debug Dump" is **absent** in release build. Then re-install debug for further work:

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt
git commit -m "fix(settings): hide Debug Dump action in release builds (#14)"
```

---

## Task 3: Apply racial ability bonuses by default on character creation (#17)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/ui/setup/RacialBonusDefaultTest.kt` (new)
- Reference: `app/src/main/kotlin/com/realmsoffate/game/game/Races.kt` (already has per-race bonuses)

**Background:** `RaceDef` already encodes the canonical 5e bonuses (e.g., Elf `dexBonus=2, intBonus=1`). The character-creation flow currently asks the player to manually pick where +2 and +1 fall (`primaryBonus` / `secondaryBonus` indexes 0–5 = STR..CHA), ignoring the race's canon. The fix: when the player picks a race, **default** `primaryBonus` and `secondaryBonus` to that race's largest two bonuses, while keeping the manual selectors available so players can still rearrange.

**Note on Human:** Human has +1 to all six. We can't represent "+1 all" with the current two-slot selector. For now, default Human to `primaryBonus = STR (0)` and `secondaryBonus = DEX (1)` — same shape as the existing default — and add a TODO comment noting the model loses 4 of Human's 6 bonuses. A proper fix requires extending the model to a vector of bonuses; out of scope for this issue.

- [ ] **Step 1: Add a helper on `RaceDef` that returns the default (primary, secondary) ability indices**

Open `app/src/main/kotlin/com/realmsoffate/game/game/Races.kt` and append after the `Races` object:

```kotlin
/**
 * Default (primary, secondary) ability indices (0=STR, 1=DEX, 2=CON, 3=INT, 4=WIS, 5=CHA)
 * derived from a race's per-stat bonuses. Used by character creation to pre-fill the
 * +2 / +1 selector with the race's canonical 5e bonus layout.
 *
 * Human (+1 to all six) cannot be fully represented by a two-slot selector — we return
 * (STR, DEX) as a representative pair. See issue #17 for the long-term fix.
 */
fun RaceDef.defaultBonusIndices(): Pair<Int, Int> {
    val bonuses = intArrayOf(strBonus, dexBonus, conBonus, intBonus, wisBonus, chaBonus)
    val sortedIdx = bonuses.indices.sortedByDescending { bonuses[it] }
    return sortedIdx[0] to sortedIdx[1]
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/RaceDefaultBonusTest.kt`:

```kotlin
package com.realmsoffate.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

class RaceDefaultBonusTest {
    @Test fun `elf defaults to DEX primary and INT secondary`() {
        val (p, s) = Races.find("Elf")!!.defaultBonusIndices()
        assertEquals(1, p) // DEX
        assertEquals(3, s) // INT
    }

    @Test fun `dwarf defaults to CON primary and STR secondary`() {
        val (p, s) = Races.find("Dwarf")!!.defaultBonusIndices()
        assertEquals(2, p) // CON
        assertEquals(0, s) // STR
    }

    @Test fun `tiefling defaults to CHA primary and INT secondary`() {
        val (p, s) = Races.find("Tiefling")!!.defaultBonusIndices()
        assertEquals(5, p)
        assertEquals(3, s)
    }

    @Test fun `half-orc defaults to STR primary and CON secondary`() {
        val (p, s) = Races.find("Half-Orc")!!.defaultBonusIndices()
        assertEquals(0, p)
        assertEquals(2, s)
    }
}
```

- [ ] **Step 3: Run the test — expect PASS**

Since the helper was already implemented in Step 1, this test should pass immediately:

```bash
gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.RaceDefaultBonusTest"
```

Expected: 4 tests PASS. (If you prefer strict TDD ordering, swap Steps 1 and 2 — write the test first, watch it fail, then add the helper.)

- [ ] **Step 4: Wire the default into `CharacterCreationScreen`**

Open `app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt`. Find the state declarations for `primaryBonus` and `secondaryBonus` (search for `primaryBonus` — they're declared as `rememberSaveable { mutableIntStateOf(...) }` near the top of the screen). Replace their initialization with values derived from the selected race, and add a `LaunchedEffect(race)` that updates them whenever the race changes.

The exact pattern (insert after the existing `var race by rememberSaveable { ... }` declaration):

```kotlin
// Default the +2 / +1 picks from the selected race's canonical bonuses.
// Player can still override on the Stats step.
val initialBonuses = remember(race) {
    Races.find(race)?.defaultBonusIndices() ?: (0 to 1)
}
var primaryBonus by rememberSaveable(race) { mutableIntStateOf(initialBonuses.first) }
var secondaryBonus by rememberSaveable(race) { mutableIntStateOf(initialBonuses.second) }
```

The `rememberSaveable(race)` key means the state is reset when `race` changes — which is the desired behavior: pick a new race, pick up new defaults. The user can still edit them on the Stats step.

- [ ] **Step 5: Compile and deploy**

```bash
gradle :app:compileDebugKotlin && gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity
```

- [ ] **Step 6: Manual verification via Debug Bridge**

```bash
curl -s localhost:8735/describe | head -50
```

Walk through character creation: pick **Elf** → advance to Stats step → confirm "+2 to" is **DEX** and "+1 to" is **INT** by default. Change race to **Dwarf** → return to Stats → confirm "+2 to" is **CON**, "+1 to" is **STR**. Final ConfirmStep should display the totals with the racial bonus baked in.

- [ ] **Step 7: Run full test suite to catch regressions**

```bash
gradle test
```

Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/Races.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/RaceDefaultBonusTest.kt
git commit -m "fix(setup): default racial ability bonuses from race definition (#17)"
```

---

## Task 4: Add default clothes to every class's starting gear (#13)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/Classes.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/ClassStartingClothesTest.kt` (new)

**Decision:** Add a single, neutral `Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)` to every class. Slot type `"clothes"` should match whatever the equipment system uses for the Clothes slot — verify by grepping the equipment slot code first.

- [ ] **Step 1: Confirm the clothes slot type string used by `EquipmentEffects` / inventory**

```bash
grep -rn "\"clothes\"\|slot ==\|category ==" app/src/main/kotlin/com/realmsoffate/game/game/EquipmentEffects.kt app/src/main/kotlin/com/realmsoffate/game/data/Models.kt | head -20
```

Capture the exact slot string the equipment system recognizes for Clothes (likely `"clothes"` or `"clothing"`). Use that string in Step 3.

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/ClassStartingClothesTest.kt`:

```kotlin
package com.realmsoffate.game.game

import org.junit.Assert.assertTrue
import org.junit.Test

class ClassStartingClothesTest {
    @Test fun `every class has a clothes item in starting gear`() {
        // Use the slot string discovered in Step 1; this example assumes "clothes".
        val clothesType = "clothes"
        val missing = Classes.list.filter { cls ->
            cls.startingItems.none { it.type.equals(clothesType, ignoreCase = true) }
        }
        assertTrue(
            "Classes missing clothes in starting gear: ${missing.map { it.name }}",
            missing.isEmpty()
        )
    }

    @Test fun `clothes start equipped`() {
        val clothesType = "clothes"
        Classes.list.forEach { cls ->
            val clothes = cls.startingItems.firstOrNull { it.type.equals(clothesType, true) }
            requireNotNull(clothes) { "${cls.name} has no clothes" }
            assertTrue("${cls.name} clothes not equipped", clothes.equipped)
        }
    }
}
```

- [ ] **Step 3: Run the test — expect FAIL**

```bash
gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.ClassStartingClothesTest"
```

Expected: FAIL — at least one class is missing clothes.

- [ ] **Step 4: Add a `Traveler's Clothes` item to every class's `startingItems`**

In `app/src/main/kotlin/com/realmsoffate/game/game/Classes.kt`, for each of the 12 `ClassDef(...)` entries, add this `Item` at the end of the `startingItems = listOf(...)` list:

```kotlin
Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
```

(Use whatever exact slot string Step 1 confirmed — adjust if not `"clothes"`.)

- [ ] **Step 5: Re-run the test — expect PASS**

```bash
gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.ClassStartingClothesTest"
```

Expected: 2 tests PASS.

- [ ] **Step 6: Run full test suite (catch any equipment-effect regressions)**

```bash
gradle test
```

Expected: all PASS. If any equipment-effect or starting-gold tests fail (e.g., starting wealth calculations), the new item shouldn't change those — investigate, don't paper over.

- [ ] **Step 7: Deploy and verify in-game**

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Roll a new character, open the inventory/character sheet, confirm the Clothes slot shows "Traveler's Clothes" and the Armor slot still shows the class's armor (Fighter: Chain Mail; Wizard: nothing/robes; etc.).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/Classes.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/ClassStartingClothesTest.kt
git commit -m "fix(classes): start every class with Traveler's Clothes (#13)"
```

---

## Task 5: Stop rendering player input bubbles in the chat (#15)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt:91`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt:115,175` (if these also render `PlayerBubble` independently)

**Critical:** This is a **display-only** change. The `DisplayMessage.Player` entries must continue to be added to the message log so they're sent to DeepSeek as part of the conversation history and included in scene summaries. We're hiding them from the visible feed only.

- [ ] **Step 1: Confirm prompt assembly does NOT depend on PlayerBubble rendering**

```bash
grep -rn "DisplayMessage\.Player\|messages.filter\|buildUserPrompt\|conversationHistory" app/src/main/kotlin/com/realmsoffate/game/data app/src/main/kotlin/com/realmsoffate/game/game | head -20
```

Verify that `AiRepository` / prompt builders consume `DisplayMessage.Player` from state (or wherever it's stored) **separately from** the UI rendering. If prompt assembly happens to walk the same `state.messages` list, hiding from UI must not delete from the list — only skip the render.

If you find any code path that uses the visibility of the bubble to decide whether to send to the LLM, **stop** — that's a bug we'd be hiding. Note it and convert into a tracked sub-issue before proceeding.

- [ ] **Step 2: Locate the three render sites**

```bash
grep -rn "PlayerBubble(" app/src/main/kotlin/com/realmsoffate/game/ui --include="*.kt"
```

Expected sites:
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt:91`
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt:115`
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt:175`

- [ ] **Step 3: In `ChatFeed.kt`, skip rendering for `DisplayMessage.Player`**

Open `ChatFeed.kt:91`. The current block looks like:

```kotlin
when (msg) {
    is DisplayMessage.Player -> PlayerBubble(msg.text, state.character?.name)
    is DisplayMessage.Narration -> { ... }
    is DisplayMessage.Event -> EventCard(msg.icon, msg.title, msg.text)
    is DisplayMessage.System -> SystemLine(msg.text)
}
```

Change the `Player` branch to a no-op:

```kotlin
when (msg) {
    is DisplayMessage.Player -> Unit  // hidden from chat feed; still sent to LLM (#15)
    is DisplayMessage.Narration -> { ... }
    is DisplayMessage.Event -> EventCard(msg.icon, msg.title, msg.text)
    is DisplayMessage.System -> SystemLine(msg.text)
}
```

- [ ] **Step 4: In `NarrationBlock.kt`, gate the two `PlayerBubble(` calls**

Open `NarrationBlock.kt`. For each of the two call sites (lines ~115 and ~175), trace upward to understand what conditional they're under (likely "show the player line that triggered this narration"). Wrap each `PlayerBubble(...)` call in a feature-flag-style constant so they can be re-enabled if we change our minds:

At the top of `NarrationBlock.kt` (under existing imports), add:

```kotlin
// Keep player input out of the visible chat — see issue #15.
// Input is still sent to the LLM via DisplayMessage.Player in state.messages.
private const val SHOW_PLAYER_BUBBLES_INLINE = false
```

Then wrap each `PlayerBubble(...)` call:

```kotlin
if (SHOW_PLAYER_BUBBLES_INLINE) {
    PlayerBubble(/* existing args */)
}
```

Compose will eliminate the dead branch on compile when the const is `false`. (Don't delete the call entirely — the surrounding scope often contains layout/spacing that should still render, or the call may be the only thing there. Check the surrounding 5-10 lines of each site to confirm.)

- [ ] **Step 5: Compile**

```bash
gradle :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If you get an "unused parameter" warning on the Player branch's `msg`, that's fine.

- [ ] **Step 6: Deploy and test the loop end-to-end**

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Start or load a game. Submit a freeform input ("look around the tavern carefully"). Verify:
- Player bubble does **not** appear in chat.
- Narrator response **does** appear and is contextually responsive to the input (proving the LLM received it).
- Submit a follow-up that references the prior input (e.g., "what did I see?"). Confirm the narrator remembers — proving prompt history still includes player turns.

Use `/describe` from Debug Bridge to confirm chat tree:

```bash
curl -s localhost:8735/describe | grep -i "player\|bubble"
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt
git commit -m "feat(chat): hide player input bubbles; still sent to LLM (#15)"
```

---

## Task 6: NPC bubble right-swipe deep-links to that NPC's journal entry (#11)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt:74,152` (`onSwipeRight = { onOpenJournal(displayName) }`)
- Inspect: the receiver of `onOpenJournal` — find what it does today and whether it accepts an NPC slug/name

**Background:** Right-swipe already passes a `displayName` (NPC name) to `onOpenJournal`. The bug is that the journal panel ignores the argument and opens to the general view. We need the panel to scroll to / select that NPC.

- [ ] **Step 1: Trace `onOpenJournal` from the call site to the receiver**

```bash
grep -rn "onOpenJournal" app/src/main/kotlin/com/realmsoffate/game --include="*.kt"
```

Identify (a) where `onOpenJournal` is bound at the screen level and (b) the journal panel composable that ultimately decides what to display. Capture the function signature and any existing "select NPC" hook on the panel.

- [ ] **Step 2: Add (or use) a "selected NPC slug" state on the journal panel**

If the journal panel already takes an optional initial NPC slug, route `displayName` through to it (slugify if needed — check whether NPC entries are keyed by display name or slug; if slug, use the same slug derivation as `NpcLogReducer`).

If no such hook exists, add one — typical pattern for the Realms panel system:

```kotlin
// In the journal panel signature
@Composable
fun JournalPanel(
    state: GameState,
    initialNpcSlug: String? = null,   // <-- new
    onClose: () -> Unit,
)

// Inside, observe initialNpcSlug and switch the active tab + scroll target on change.
LaunchedEffect(initialNpcSlug) {
    if (initialNpcSlug != null) {
        // switch to NPCs tab and scroll to that NPC entry
    }
}
```

- [ ] **Step 3: Plumb `displayName` from the swipe through to the panel**

Update the chain: `NarrationBlock.onSwipeRight = { onOpenJournal(displayName) }` → screen handler stores the slug + opens panel → panel reads `initialNpcSlug`.

In the screen-level state, add (if absent):

```kotlin
var pendingJournalNpc by remember { mutableStateOf<String?>(null) }
```

When `onOpenJournal(name)` is called, set `pendingJournalNpc = slugify(name)` and open the journal panel. Pass `initialNpcSlug = pendingJournalNpc` into the panel. Reset to `null` when the panel closes.

- [ ] **Step 4: Manual verification (no unit test — this is a UI-routing change)**

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Trigger a turn that produces NPC dialogue. Right-swipe an NPC bubble. Confirm:
- Journal panel opens.
- NPCs tab is active.
- That NPC's entry is visible (scrolled into view) or is the selected detail.
- Right-swiping a different NPC opens the panel to that one.
- For an NPC that isn't tracked yet (no slug), the panel still opens to the NPCs tab — fallback should not crash.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui
git commit -m "feat(chat): NPC bubble right-swipe opens that NPC's journal entry (#11)"
```

---

## Task 7: Warn before Begin if point-buy points are unspent (#21)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt` (around the BEGIN button at line 131 and the `pointCost` helper used at line 375)

- [ ] **Step 1: Locate `pointCost` and the BEGIN button**

The Stats step computes `val spent = pointCost(baseStats); val remaining = 27 - spent` (line 375–376). The BEGIN button is `GradientBeginButton(...)` at line 131. We need to compute `remaining` at the BEGIN-button scope (it's currently inside `StatsStep`), and gate the click.

- [ ] **Step 2: Hoist remaining-points calc to the screen scope**

In the screen-level body where `baseStats` is held, add:

```kotlin
val pointsRemaining = remember(baseStats.value) { 27 - pointCost(baseStats.value) }
```

(Place it above the `Scaffold` / `Box` that contains the BEGIN button.)

- [ ] **Step 3: Add a confirmation dialog**

Add a state variable and dialog at the screen scope:

```kotlin
var showUnspentWarning by remember { mutableStateOf(false) }

if (showUnspentWarning) {
    AlertDialog(
        onDismissRequest = { showUnspentWarning = false },
        title = { Text("Unspent points") },
        text = {
            Text(
                "You still have $pointsRemaining ability " +
                "${if (pointsRemaining == 1) "point" else "points"} to spend. " +
                "Continue anyway?"
            )
        },
        confirmButton = {
            TextButton(onClick = {
                showUnspentWarning = false
                // call the existing onBegin / submit lambda
                beginCharacter()  // <-- replace with actual call used by the BEGIN button today
            }) { Text("Begin anyway") }
        },
        dismissButton = {
            TextButton(onClick = {
                showUnspentWarning = false
                step = 4  // jump back to Stats step (zero-indexed: 4 = StatsStep)
            }) { Text("Go back") }
        }
    )
}
```

- [ ] **Step 4: Gate the BEGIN click on `pointsRemaining > 0`**

Find the `GradientBeginButton(... onClick = { ... })` call at line 131. Wrap the existing onClick body:

```kotlin
GradientBeginButton(
    enabled = /* existing enabled expr */,
    onClick = {
        if (pointsRemaining > 0) {
            showUnspentWarning = true
        } else {
            beginCharacter()  // existing inline body
        }
    }
)
```

- [ ] **Step 5: Compile and deploy**

```bash
gradle :app:compileDebugKotlin && gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity
```

- [ ] **Step 6: Manual verification**

- Roll a character, leave Stats at the default (which will likely have unspent points, depending on initial state). Tap BEGIN → dialog shows "You still have N points...".
- Tap "Go back" → returns to Stats step; dialog dismissed.
- Spend all points until `remaining == 0`. Tap BEGIN → no dialog, goes straight to game.
- Tap BEGIN with `remaining = 1`, choose "Begin anyway" → starts game with under-spent stats.

Capture screenshots via debug bridge:

```bash
curl -s localhost:8735/screenshot > /tmp/begin-warning.png
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt
git commit -m "feat(setup): confirm before BEGIN if point-buy points unspent (#21)"
```

---

## Task 8: NPC dialog — drop leading bullet, match Character bubble padding (#12)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt:306-328` (NpcDialogueBubble)

The leading bullet is the `Box(.size(8.dp).background(accent))` at line 311–317. PlayerBubble (line 137–164) uses `fillMaxWidth(0.85f)` with `Arrangement.End`. To match symmetric padding, NpcDialogueBubble should mirror that layout: `fillMaxWidth(0.85f)` aligned to start.

- [ ] **Step 1: Remove the bullet `Box`**

Open `MessageBubbles.kt:306`. Delete lines 311–317 (the `Box(...).background(accent)` block) and the parent `Row` becomes redundant — replace the outer `Row(Modifier.fillMaxWidth(), ...)` with a `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start)` containing the `BubbleFrame` directly.

- [ ] **Step 2: Match width to PlayerBubble**

Change `BubbleFrame(... modifier = Modifier.weight(1f))` to `BubbleFrame(... modifier = Modifier.fillMaxWidth(0.85f))` so NPC bubbles take 85% of the row width — same as PlayerBubble (line 146).

The final structure should mirror PlayerBubble exactly except for `Arrangement.Start` (vs `End`) and `avatarOnRight = false` (vs `true`).

- [ ] **Step 3: Compile and deploy**

```bash
gradle :app:compileDebugKotlin && gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity
```

- [ ] **Step 4: Manual verification — eyeball symmetry**

Trigger a turn with both NPC dialogue and player input visible (player input is hidden after #15, but if there's narration present, the previous-turn player line may still appear depending on order). Compare:
- NPC bubble starts flush-left at the same offset as the right edge of a PlayerBubble.
- No bullet/dot precedes the NPC name.
- Bubble width visually matches PlayerBubble.

```bash
curl -s localhost:8735/screenshot > /tmp/npc-bubble.png
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt
git commit -m "fix(chat): drop NPC bullet, match PlayerBubble padding (#12)"
```

---

## Task 9: Replace die icon on Recommended button with a glyph (#19)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt:398`

- [ ] **Step 1: Swap the icon**

The current code at line 398:

```kotlin
leadingIcon = { Icon(Icons.Default.Casino, null, Modifier.size(18.dp)) }
```

Replace `Icons.Default.Casino` with `Icons.Default.AutoAwesome` (sparkle glyph, conveys "auto-fill suggestion"):

```kotlin
leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)) }
```

If `AutoAwesome` isn't in scope, add the import: `import androidx.compose.material.icons.filled.AutoAwesome`.

- [ ] **Step 2: Compile and deploy**

```bash
gradle :app:compileDebugKotlin && gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity
```

- [ ] **Step 3: Visual verification**

Roll a new character → Stats step. Confirm Recommended button shows a sparkle, not a die. Tap it → applies the class's recommended preset as before.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt
git commit -m "fix(setup): use AutoAwesome icon for Recommended button (#19)"
```

---

## Task 10: Phase 1 verification + close-out

- [ ] **Step 1: Run full test suite**

```bash
gradle test
```

Expected: all PASS.

- [ ] **Step 2: Run lint**

```bash
gradle lint
```

Expected: no new errors. Warnings introduced by Phase 1 changes should be reviewed; tolerate if benign.

- [ ] **Step 3: Build release**

```bash
gradle assembleRelease
```

Expected: BUILD SUCCESSFUL. Confirms the `BuildConfig.DEBUG` gate in #14 compiles cleanly into release.

- [ ] **Step 4: Run Debug Bridge P0 + P1 verification suite**

Per `CLAUDE.md`, after substantive changes run P0+P1 from `.cursor/rules/debug-bridge-test-procedures.mdc`:

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
# then run the P0+P1 procedures listed in the test-procedures rule
```

- [ ] **Step 5: Update README only if user-visible behavior changed**

Per `CLAUDE.md` — "README only for user-visible changes". The user-visible changes in Phase 1: Debug Dump hidden in release (intentionally invisible — skip), default racial bonuses (mention in changelog), clothes added to starting gear, player bubbles hidden, NPC right-swipe behavior, BEGIN warning, NPC bubble layout, Recommended button icon. Add a short bullet list under a "Recent changes" section if the README has one; otherwise skip.

- [ ] **Step 6: Final summary commit if any uncommitted hygiene changes remain**

```bash
git status
# If clean, Phase 1 is done. If anything outstanding, commit it explicitly.
```

---

## Self-review notes

- **Spec coverage:** Tasks 1–9 each map to a specific issue from the triage. Issues #18, #20, #22, #23, #25, #26, #27, #29 + epic #24 + feature #28 are explicitly deferred and listed in the goal section.
- **Placeholders:** None. Every step has the actual code change or actual command. The `// existing onBegin lambda` references in Task 7 require reading the file to find the exact name — that's research, not a placeholder; the engineer is expected to do it.
- **Type consistency:** `defaultBonusIndices()` defined in Task 3 returns `Pair<Int, Int>` and is consumed identically. `pointsRemaining: Int` in Task 7 is consumed by the dialog text. `initialNpcSlug: String?` in Task 6 is consumed by the panel. No mismatches.
- **Risk:** Task 1 (release-bridge verification) may surface code that needs to move from main → debug source set. If that happens, scope expands. Stop and re-plan if the verification fails — don't try to keep going.
- **Order:** Release-safety first (1, 2) so the most important guards are in early. Correctness next (3, 4, 5). UX polish last (6–9). Each commit is independently revertable.

---

## Follow-up plans (not in this document)

These need separate plan documents — they share little context with Phase 1 and are large enough that combining would obscure the task list:

- **Phase 2 — NPC + skill-check correctness:** #22 (5e skill names), #23 (NPC duplicate-on-rename), #26 (NPC noise filter). Touches `Prompts.kt`, `AutoTagUnknownNpcs.kt`, `NpcLogReducer.kt`, the skill classifier. Heavy on parser / reducer test coverage.
- **Phase 3 — perf + features:** #18 (cheats overlay perf), #20 (random character), #29 (lore reactivity). Mixed surface — perf audit, new feature, state observation fix.
- **Diagnose-first triage:** #25 (prose degradation) and #27 (memory of past actions). These need reproduction + bisect against recent prompt changes before a plan can be written. Recommend a focused investigation session, not a plan.
- **Epics:** #24 (content-to-JSON, 7 milestones — already broken down in the issue), #28 (auto-update — needs a design doc first). Each warrants its own multi-PR plan.
