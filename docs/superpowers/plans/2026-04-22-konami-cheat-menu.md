# Konami Cheat Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a hidden cheat system unlocked by typing an emoji Konami code (`⬆️⬆️⬇️⬇️⬅️➡️⬅️➡️🅱️🅰️`) into the in-game chat field, exposing a top-bar joker button to toggle four cheats (Unnatural 20, Loser, 1%, Overprepared) and a title-screen disable button.

**Architecture:** A persistent `CheatsStore` (DataStore-backed) holds five boolean flags; a pure-Kotlin `Cheats` singleton mirrors their values in memory so roll-site code in `Dice.kt` (and the VM's gold-clamp hook) can read them synchronously without Android dependencies. UI changes are additive: conditional joker icon in `TopBar`, new `CheatsOverlay` dialog, and a conditional Cheats button on `TitleScreen` — all hidden when the master flag is off.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX DataStore (`preferencesDataStore`), Robolectric for DataStore tests, JUnit4, kotlinx.coroutines.

**Spec reference:** `docs/superpowers/specs/2026-04-22-konami-cheat-menu-design.md`

---

## File Structure

**New files:**
- `app/src/main/kotlin/com/realmsoffate/game/game/Cheats.kt` — in-memory singleton (pure Kotlin).
- `app/src/main/kotlin/com/realmsoffate/game/data/CheatsStore.kt` — DataStore persistence.
- `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/CheatsOverlay.kt` — the 2×2 grid dialog.
- `app/src/test/kotlin/com/realmsoffate/game/game/DiceCheatsTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/ProgressionOverpreparedTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/KonamiInterceptTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/InfiniteGoldClampTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/CheatsStoreTest.kt`

**Modified files:**
- `app/src/main/kotlin/com/realmsoffate/game/game/Dice.kt` — consult `Cheats`.
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — wire `CheatsStore`, intercept konami in `submitAction`, gold-clamp hook, expose `cheatsEnabled` state.
- `app/src/main/kotlin/com/realmsoffate/game/game/handlers/ProgressionHandler.kt` — `applyOverprepared()`.
- `app/src/main/kotlin/com/realmsoffate/game/ui/setup/TitleScreen.kt` — conditional Cheats button + disable dialog.
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt` — conditional joker icon + ∞ gold display.
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt` — wire `CheatsOverlay` into overlay state.
- `app/src/main/kotlin/com/realmsoffate/game/RealmsApp.kt` — ensure context access pattern (already via `RealmsApp.instance`).

---

## Commands the engineer will use

- Run all JVM tests: `gradle test`
- Run a single test class: `gradle test --tests "com.realmsoffate.game.game.DiceCheatsTest"`
- Build + deploy + launch:
  ```
  gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
  ```
- Commit:
  ```
  git add <files>
  git commit -m "<message>"
  ```
  (use HEREDOC with Co-Authored-By line per project convention if committing through the commit skill)

---

## Task 1: Create `Cheats` singleton (in-memory runtime state)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/game/Cheats.kt`

- [ ] **Step 1.1: Create the singleton**

Write `app/src/main/kotlin/com/realmsoffate/game/game/Cheats.kt`:

```kotlin
package com.realmsoffate.game.game

/**
 * In-memory mirror of the persisted cheat flags in [com.realmsoffate.game.data.CheatsStore].
 * Consulted by [Dice] (hot path, pure Kotlin) and by VM gold-clamp logic.
 *
 * Writes happen on the VM's viewModelScope as prefs flows emit; reads happen from
 * arbitrary threads. Fields are @Volatile for that reason.
 */
object Cheats {
    @Volatile var enabled: Boolean = false
    @Volatile var forceCrit: Boolean = false
    @Volatile var forceFail: Boolean = false
    @Volatile var infiniteGold: Boolean = false

    fun reset() {
        enabled = false
        forceCrit = false
        forceFail = false
        infiniteGold = false
    }
}
```

- [ ] **Step 1.2: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/Cheats.kt
git commit -m "feat(cheats): add in-memory Cheats singleton for runtime state"
```

---

## Task 2: Intercept `Dice.d20()` / `Dice.d(20)` via `Cheats`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/Dice.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/DiceCheatsTest.kt`

- [ ] **Step 2.1: Write failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/DiceCheatsTest.kt`:

```kotlin
package com.realmsoffate.game.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceCheatsTest {
    @After fun tearDown() { Cheats.reset() }

    @Test fun `forceCrit makes d20 always return 20`() {
        Cheats.forceCrit = true
        repeat(100) { assertEquals(20, Dice.d20()) }
    }

    @Test fun `forceFail makes d20 always return 1`() {
        Cheats.forceFail = true
        repeat(100) { assertEquals(1, Dice.d20()) }
    }

    @Test fun `forceCrit also overrides d(20)`() {
        Cheats.forceCrit = true
        repeat(50) { assertEquals(20, Dice.d(20)) }
    }

    @Test fun `forceFail also overrides d(20)`() {
        Cheats.forceFail = true
        repeat(50) { assertEquals(1, Dice.d(20)) }
    }

    @Test fun `no flags - d20 stays random in range`() {
        val results = (1..200).map { Dice.d20() }
        results.forEach { assertTrue(it in 1..20) }
        assertTrue("should see more than one outcome", results.toSet().size > 1)
    }

    @Test fun `forceCrit does NOT affect non-d20 sizes`() {
        Cheats.forceCrit = true
        val d6s = (1..200).map { Dice.d(6) }
        d6s.forEach { assertTrue(it in 1..6) }
        assertTrue(d6s.toSet().size > 1)
    }
}
```

- [ ] **Step 2.2: Run test to verify it fails**

Run: `gradle test --tests "com.realmsoffate.game.game.DiceCheatsTest"`
Expected: FAIL — `forceCrit` cases return random values, not 20.

- [ ] **Step 2.3: Patch `Dice.kt`**

Replace contents of `app/src/main/kotlin/com/realmsoffate/game/game/Dice.kt`:

```kotlin
package com.realmsoffate.game.game

import kotlin.random.Random

object Dice {
    fun d20(): Int = when {
        Cheats.forceCrit -> 20
        Cheats.forceFail -> 1
        else -> Random.nextInt(1, 21)
    }

    fun d(n: Int): Int = when {
        n == 20 && Cheats.forceCrit -> 20
        n == 20 && Cheats.forceFail -> 1
        else -> Random.nextInt(1, n + 1)
    }

    fun roll(formula: String): Int {
        // Supports "NdM(+/-K)" e.g. "1d8+2", "2d6"
        val m = Regex("(\\d+)d(\\d+)([+-]\\d+)?").matchEntire(formula.replace(" ", "")) ?: return 0
        val n = m.groupValues[1].toInt()
        val size = m.groupValues[2].toInt()
        val bonus = m.groupValues[3].ifBlank { "0" }.toInt()
        var total = bonus
        repeat(n) { total += Random.nextInt(1, size + 1) }
        return total
    }
}
```

Note: `roll("NdM+K")` is intentionally *not* intercepted — damage dice stay honest.

- [ ] **Step 2.4: Run test to verify it passes**

Run: `gradle test --tests "com.realmsoffate.game.game.DiceCheatsTest"`
Expected: PASS (all 6 tests).

- [ ] **Step 2.5: Run full test suite — nothing else broke**

Run: `gradle test`
Expected: PASS (all existing tests plus new ones).

- [ ] **Step 2.6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/Dice.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/DiceCheatsTest.kt
git commit -m "feat(cheats): Dice.d20/d(20) consult Cheats.forceCrit and forceFail"
```

---

## Task 3: `CheatsStore` (DataStore-backed persistence)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/CheatsStore.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/CheatsStoreTest.kt`

- [ ] **Step 3.1: Write failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/CheatsStoreTest.kt`:

```kotlin
package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheatsStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val store = CheatsStore(ctx)

    @After fun tearDown() = runTest { store.disable() }

    @Test fun `unlock sets enabled true`() = runTest {
        store.unlock()
        assertTrue(store.enabled.first())
    }

    @Test fun `disable clears enabled and all four flags`() = runTest {
        store.unlock()
        store.setUnnaturalTwenty(true)
        store.setInfiniteGold(true)
        store.disable()
        assertFalse(store.enabled.first())
        assertFalse(store.unnaturalTwenty.first())
        assertFalse(store.loser.first())
        assertFalse(store.infiniteGold.first())
    }

    @Test fun `setUnnaturalTwenty true clears loser`() = runTest {
        store.setLoser(true)
        store.setUnnaturalTwenty(true)
        assertTrue(store.unnaturalTwenty.first())
        assertFalse(store.loser.first())
    }

    @Test fun `setLoser true clears unnaturalTwenty`() = runTest {
        store.setUnnaturalTwenty(true)
        store.setLoser(true)
        assertTrue(store.loser.first())
        assertFalse(store.unnaturalTwenty.first())
    }

    @Test fun `setInfiniteGold does not affect other cheats`() = runTest {
        store.setUnnaturalTwenty(true)
        store.setInfiniteGold(true)
        assertTrue(store.unnaturalTwenty.first())
        assertTrue(store.infiniteGold.first())
    }
}
```

- [ ] **Step 3.2: Run test to verify it fails**

Run: `gradle test --tests "com.realmsoffate.game.data.CheatsStoreTest"`
Expected: FAIL — `CheatsStore` does not exist.

- [ ] **Step 3.3: Create `CheatsStore`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/CheatsStore.kt`:

```kotlin
package com.realmsoffate.game.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cheatsDataStore by preferencesDataStore(name = "rpg_cheats")

class CheatsStore(private val context: Context) {
    private val keyEnabled = booleanPreferencesKey("cheats_enabled")
    private val keyUnnaturalTwenty = booleanPreferencesKey("cheat_unnatural_twenty")
    private val keyLoser = booleanPreferencesKey("cheat_loser")
    private val keyInfiniteGold = booleanPreferencesKey("cheat_infinite_gold")

    val enabled: Flow<Boolean> = context.cheatsDataStore.data.map { it[keyEnabled] ?: false }
    val unnaturalTwenty: Flow<Boolean> = context.cheatsDataStore.data.map { it[keyUnnaturalTwenty] ?: false }
    val loser: Flow<Boolean> = context.cheatsDataStore.data.map { it[keyLoser] ?: false }
    val infiniteGold: Flow<Boolean> = context.cheatsDataStore.data.map { it[keyInfiniteGold] ?: false }

    suspend fun unlock() {
        context.cheatsDataStore.edit { it[keyEnabled] = true }
    }

    suspend fun disable() {
        context.cheatsDataStore.edit {
            it[keyEnabled] = false
            it[keyUnnaturalTwenty] = false
            it[keyLoser] = false
            it[keyInfiniteGold] = false
        }
    }

    suspend fun setUnnaturalTwenty(on: Boolean) {
        context.cheatsDataStore.edit {
            it[keyUnnaturalTwenty] = on
            if (on) it[keyLoser] = false
        }
    }

    suspend fun setLoser(on: Boolean) {
        context.cheatsDataStore.edit {
            it[keyLoser] = on
            if (on) it[keyUnnaturalTwenty] = false
        }
    }

    suspend fun setInfiniteGold(on: Boolean) {
        context.cheatsDataStore.edit { it[keyInfiniteGold] = on }
    }
}
```

- [ ] **Step 3.4: Run test to verify it passes**

Run: `gradle test --tests "com.realmsoffate.game.data.CheatsStoreTest"`
Expected: PASS (5 tests).

- [ ] **Step 3.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/CheatsStore.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/CheatsStoreTest.kt
git commit -m "feat(cheats): add DataStore-backed CheatsStore with mutex toggles"
```

---

## Task 4: Wire `CheatsStore` into `GameViewModel` and sync to `Cheats` singleton

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`
  - Constructor: add `cheatsStore` param (default via `RealmsApp.instance` for simplicity, consistent with `PreferencesStore` pattern).
  - `init { ... }`: collect flows → update `Cheats` singleton.
  - Expose `cheatsEnabled: StateFlow<Boolean>` for UI.

- [ ] **Step 4.1: Add constructor param and Factory update**

In `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` around line 167, change constructor:

```kotlin
class GameViewModel(
    private val ai: AiRepository,
    private val prefs: PreferencesStore,
    private val cheatsStore: com.realmsoffate.game.data.CheatsStore,
    private val repo: com.realmsoffate.game.data.EntityRepository = com.realmsoffate.game.data.db.RealmsDbHolder.repo
) : ViewModel() {
```

Update Factory around line 1774:

```kotlin
companion object {
    val Factory = viewModelFactory {
        initializer {
            val ctx = RealmsApp.instance
            GameViewModel(
                ai = AiRepository(),
                prefs = PreferencesStore(ctx),
                cheatsStore = com.realmsoffate.game.data.CheatsStore(ctx)
            )
        }
    }
    // ... levelThreshold unchanged
}
```

- [ ] **Step 4.2: Expose `cheatsEnabled` StateFlow for UI**

Just after line 264 (`val pendingStatPoints: StateFlow<Int> = ...`), add:

```kotlin
val cheatsEnabled: StateFlow<Boolean> = cheatsStore.enabled.stateIn(
    viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false
)
val cheatUnnaturalTwenty: StateFlow<Boolean> = cheatsStore.unnaturalTwenty.stateIn(
    viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false
)
val cheatLoser: StateFlow<Boolean> = cheatsStore.loser.stateIn(
    viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false
)
val cheatInfiniteGold: StateFlow<Boolean> = cheatsStore.infiniteGold.stateIn(
    viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false
)
```

Add missing import at top of file if not present:
```kotlin
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Step 4.3: Sync flows → Cheats singleton in `init`**

Find the existing `init { ... }` block in `GameViewModel` (search for `init {`; there's one near the top of the class body). Inside it, add:

```kotlin
init {
    // ... existing init code ...

    viewModelScope.launch {
        cheatsStore.enabled.collect { com.realmsoffate.game.game.Cheats.enabled = it }
    }
    viewModelScope.launch {
        cheatsStore.unnaturalTwenty.collect { com.realmsoffate.game.game.Cheats.forceCrit = it }
    }
    viewModelScope.launch {
        cheatsStore.loser.collect { com.realmsoffate.game.game.Cheats.forceFail = it }
    }
    viewModelScope.launch {
        cheatsStore.infiniteGold.collect { com.realmsoffate.game.game.Cheats.infiniteGold = it }
    }
}
```

If the VM does not yet have an `init {}` block, add one at the top of the class body.

- [ ] **Step 4.4: Expose VM methods for UI to call**

Add these public methods near the existing `dismissLevelUp` / etc. (anywhere in the class):

```kotlin
fun unlockCheats() {
    viewModelScope.launch { cheatsStore.unlock() }
}

fun disableCheats() {
    viewModelScope.launch { cheatsStore.disable() }
}

fun setUnnaturalTwenty(on: Boolean) {
    viewModelScope.launch { cheatsStore.setUnnaturalTwenty(on) }
}

fun setLoser(on: Boolean) {
    viewModelScope.launch { cheatsStore.setLoser(on) }
}

fun setInfiniteGold(on: Boolean) {
    viewModelScope.launch { cheatsStore.setInfiniteGold(on) }
}
```

- [ ] **Step 4.5: Build to verify compile**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "feat(cheats): wire CheatsStore into GameViewModel, sync to Cheats singleton"
```

---

## Task 5: Intercept the Konami code in `submitAction`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` (around line 715 — `fun submitAction`).
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/KonamiInterceptTest.kt`

- [ ] **Step 5.1: Write failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/KonamiInterceptTest.kt`:

```kotlin
package com.realmsoffate.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-level check of the Konami sequence constant + its exact-match behavior.
 * The full VM intercept flow is verified at runtime via the debug bridge.
 */
class KonamiInterceptTest {

    @Test fun `konami constant is exactly the 10-char emoji sequence`() {
        // The constant is defined in GameViewModel. This test asserts the public
        // representation used by the intercept — keeping the string canonical.
        val expected = "⬆️⬆️" +           // ⬆️⬆️
                "⬇️⬇️" +                  // ⬇️⬇️
                "⬅️➡️⬅️➡️" + // ⬅️➡️⬅️➡️
                "🅱️🅰️"         // 🅱️🅰️
        assertEquals(expected, GameViewModel.KONAMI_CODE)
    }

    @Test fun `exact match helper returns true for trimmed exact input`() {
        assertEquals(true, GameViewModel.isKonami(GameViewModel.KONAMI_CODE))
        assertEquals(true, GameViewModel.isKonami("  ${GameViewModel.KONAMI_CODE}  "))
    }

    @Test fun `substring does not match`() {
        assertEquals(false, GameViewModel.isKonami("${GameViewModel.KONAMI_CODE} and also attack"))
        assertEquals(false, GameViewModel.isKonami("hi"))
        assertEquals(false, GameViewModel.isKonami(""))
    }
}
```

- [ ] **Step 5.2: Run test to verify it fails**

Run: `gradle test --tests "com.realmsoffate.game.game.KonamiInterceptTest"`
Expected: FAIL — `GameViewModel.KONAMI_CODE` and `isKonami` do not exist.

- [ ] **Step 5.3: Add `KONAMI_CODE` constant and `isKonami` helper**

Inside `GameViewModel`'s `companion object` (near `levelThreshold`, around line 1786), add:

```kotlin
/** Emoji Konami code that unlocks the cheat menu when typed as the full chat message. */
const val KONAMI_CODE = "⬆️⬆️" +
        "⬇️⬇️" +
        "⬅️➡️⬅️➡️" +
        "🅱️🅰️"

/** Returns true iff [text] (after trimming) exactly equals the Konami code. */
fun isKonami(text: String): Boolean = text.trim() == KONAMI_CODE
```

- [ ] **Step 5.4: Intercept in `submitAction`**

In `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` at line 715, modify `submitAction` so the first thing it does (before `tryClaimSubmit`) is the Konami check:

```kotlin
fun submitAction(action: String, skill: String? = null, seed: Boolean = false) {
    // Konami intercept — swallow the message, unlock cheats, show system toast.
    if (!seed && isKonami(action)) {
        val wasEnabled = Cheats.enabled
        viewModelScope.launch { cheatsStore.unlock() }
        val msg = if (wasEnabled) "Cheats already unlocked" else "🎉 Cheats unlocked — see the 🃏 in the top bar"
        _ui.value = _ui.value.copy(
            messages = _ui.value.messages + DisplayMessage.System(msg)
        )
        return
    }

    val state = tryClaimSubmit() ?: return
    // ... rest of existing submitAction body unchanged ...
}
```

(Keep the rest of the function body as-is.)

- [ ] **Step 5.5: Run tests to verify pass**

Run: `gradle test --tests "com.realmsoffate.game.game.KonamiInterceptTest"`
Expected: PASS.

- [ ] **Step 5.6: Run full test suite**

Run: `gradle test`
Expected: PASS.

- [ ] **Step 5.7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/KonamiInterceptTest.kt
git commit -m "feat(cheats): intercept konami emoji sequence in submitAction to unlock"
```

---

## Task 6: `ProgressionHandler.applyOverprepared()` — one-shot to L20

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/handlers/ProgressionHandler.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/ProgressionOverpreparedTest.kt`

- [ ] **Step 6.1: Write failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/ProgressionOverpreparedTest.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.game.handlers.ProgressionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionOverpreparedTest {

    private fun freshHandler(ch: Character): Triple<ProgressionHandler, MutableStateFlow<GameUiState>, MutableStateFlow<Int?>> {
        val ui = MutableStateFlow(GameUiState(character = ch))
        val pendingLevelUp = MutableStateFlow<Int?>(null)
        val pendingStatPoints = MutableStateFlow(0)
        val pendingFeat = MutableStateFlow(false)
        return Triple(
            ProgressionHandler(ui, pendingLevelUp, pendingStatPoints, pendingFeat),
            ui,
            pendingLevelUp
        )
    }

    @Test fun `L1 character jumps to L20`() {
        val ch = Character(name = "Test", race = "Human", cls = "Fighter", level = 1, xp = 0,
                abilities = Abilities(str = 15, dex = 12, con = 14, int = 10, wis = 10, cha = 8))
        val (handler, ui, _) = freshHandler(ch)
        handler.applyOverprepared()
        val after = ui.value.character!!
        assertEquals(20, after.level)
        assertEquals(GameViewModel.levelThreshold(20), after.xp)
        assertTrue("hp must be > 0", after.hp > 0)
        assertEquals("hp should be full", after.maxHp, after.hp)
    }

    @Test fun `L20 character is a no-op`() {
        val ch = Character(name = "Max", race = "Elf", cls = "Wizard", level = 20, xp = 500_000)
        val (handler, ui, _) = freshHandler(ch)
        handler.applyOverprepared()
        val after = ui.value.character!!
        assertEquals(20, after.level)
    }

    @Test fun `L7 character reaches L20`() {
        val ch = Character(name = "Mid", race = "Dwarf", cls = "Cleric", level = 7, xp = 24_000)
        val (handler, ui, _) = freshHandler(ch)
        handler.applyOverprepared()
        assertEquals(20, ui.value.character!!.level)
    }

    @Test fun `mid-combat is refused`() {
        val ch = Character(name = "Test", race = "Human", cls = "Fighter", level = 1)
        val ui = MutableStateFlow(GameUiState(
            character = ch,
            combat = CombatState(order = emptyList(), round = 1, turnIdx = 0)
        ))
        val handler = ProgressionHandler(
            ui, MutableStateFlow(null), MutableStateFlow(0), MutableStateFlow(false)
        )
        handler.applyOverprepared()
        assertEquals("level unchanged while in combat", 1, ui.value.character!!.level)
    }

    @Test fun `pendingLevelUp cleared after apply`() {
        val ch = Character(name = "Test", race = "Human", cls = "Fighter", level = 1)
        val (handler, _, pendingLevelUp) = freshHandler(ch)
        pendingLevelUp.value = 2
        handler.applyOverprepared()
        assertNull(pendingLevelUp.value)
    }
}
```

**Note to the engineer:** `GameUiState` and `CombatState` are existing types. If the `CombatState` constructor shown above doesn't match the actual signature, adjust to the real one — the test only needs *any* non-null `combat` to trigger the mid-combat guard.

- [ ] **Step 6.2: Run test to verify it fails**

Run: `gradle test --tests "com.realmsoffate.game.game.ProgressionOverpreparedTest"`
Expected: FAIL — `applyOverprepared` does not exist.

- [ ] **Step 6.3: Implement `applyOverprepared`**

Append to `app/src/main/kotlin/com/realmsoffate/game/game/handlers/ProgressionHandler.kt` inside the class:

```kotlin
    /**
     * One-shot cheat: jump current character from current level to 20.
     * - HP gains: max of class hit die per level (+ CON mod where applicable)
     * - XP set to level-20 threshold so the regular reducer won't trigger again
     * - Pending level-up / stat-point / feat flags cleared (auto-applied)
     *
     * No-ops if the character is already level 20 or if the party is mid-combat.
     */
    fun applyOverprepared() {
        val s = ui.value
        if (s.combat != null) {
            ui.value = s.copy(messages = s.messages + DisplayMessage.System("Cannot level up during combat."))
            return
        }
        val ch = s.character?.deepCopy() ?: return
        if (ch.level >= 20) return

        val clsDef = com.realmsoffate.game.game.Classes.find(ch.cls)
        val hitDie = clsDef?.hitDie ?: 8
        val conMod = ch.abilities.conMod
        val primary = clsDef?.primary ?: "STR"

        for (target in (ch.level + 1)..20) {
            ch.level = target
            val hpGain = (hitDie + conMod).coerceAtLeast(1)
            ch.maxHp += hpGain
            // Feat levels 4/8/12/16/20: grant "Tough" as the default (idempotent).
            if (target % 4 == 0) {
                if (!ch.feats.any { it.equals("Tough", ignoreCase = true) }) {
                    com.realmsoffate.game.game.Feats.find("Tough")?.let { feat ->
                        feat.apply(ch)
                        ch.feats.add("Tough")
                    }
                }
            } else {
                // Non-feat level: +2 stat points auto-assigned to class primary.
                when (primary.uppercase()) {
                    "STR" -> ch.abilities.str += 2
                    "DEX" -> ch.abilities.dex += 2
                    "CON" -> { ch.abilities.con += 2; ch.maxHp += 2 }
                    "INT" -> ch.abilities.int += 2
                    "WIS" -> ch.abilities.wis += 2
                    "CHA" -> ch.abilities.cha += 2
                }
            }
        }
        ch.hp = ch.maxHp
        ch.xp = com.realmsoffate.game.game.GameViewModel.levelThreshold(20)
        _pendingLevelUp.value = null
        _pendingStatPoints.value = 0
        _pendingFeat.value = false
        ui.value = s.copy(
            character = ch,
            messages = s.messages + DisplayMessage.System("You are Overprepared.")
        )
    }
```

Add any missing imports to the top of the file if needed — should not need any (all fully-qualified names used).

- [ ] **Step 6.4: Run tests to verify pass**

Run: `gradle test --tests "com.realmsoffate.game.game.ProgressionOverpreparedTest"`
Expected: PASS.

- [ ] **Step 6.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/handlers/ProgressionHandler.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/ProgressionOverpreparedTest.kt
git commit -m "feat(cheats): ProgressionHandler.applyOverprepared jumps character to L20"
```

---

## Task 7: Infinite-gold clamp in the VM turn pipeline

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/InfiniteGoldClampTest.kt`

- [ ] **Step 7.1: Identify the clamp site**

Run this to find the best place to hook the clamp:

```
grep -n "isGenerating = false\|finalizeTurn\|afterTurnApplied\|onTurnComplete" app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt | head
```

Pick the post-turn finalization site (the place where `_ui.value` receives the reducer's result and `isGenerating` is flipped back to false). If no obvious hook exists, add a private function:

```kotlin
private fun applyInfiniteGoldIfCheating() {
    if (!Cheats.infiniteGold) return
    val s = _ui.value
    val ch = s.character ?: return
    if (ch.gold != INF_GOLD) {
        val updated = ch.deepCopy().apply { gold = INF_GOLD }
        _ui.value = s.copy(character = updated)
    }
}

private companion object {
    private const val INF_GOLD = 999_999
}
```

(If the class already has a `companion object`, merge `INF_GOLD` in there instead of creating a second companion. Kotlin allows only one `companion object` per class.)

**Better placement:** put `INF_GOLD` as a top-level `private const val` just below the imports, to avoid merging into the existing companion:

```kotlin
private const val INF_GOLD = 999_999
```

Then the function body uses the top-level constant.

Call `applyInfiniteGoldIfCheating()` at the end of the AI-response application path — right after the main reducer updates the UI state (search for the line immediately following where `CharacterApplyResult`/reducer result is merged back into `_ui.value`, or at the top of whichever function finalizes a turn). A reliable hook is inside `dispatchToAi`'s success/completion block.

- [ ] **Step 7.2: Write failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/InfiniteGoldClampTest.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This test targets the pure clamp helper. We don't instantiate the full VM;
 * instead we invoke the clamp logic directly via an extracted top-level helper.
 */
class InfiniteGoldClampTest {

    @After fun tearDown() { Cheats.reset() }

    @Test fun `clamp bumps gold to 999999 when infiniteGold is on`() {
        Cheats.infiniteGold = true
        val ch = Character(name = "T", race = "H", cls = "Fighter", gold = 100)
        val ui = MutableStateFlow(GameUiState(character = ch))
        clampInfiniteGold(ui)
        assertEquals(999_999, ui.value.character!!.gold)
    }

    @Test fun `clamp is a no-op when flag is off`() {
        Cheats.infiniteGold = false
        val ch = Character(name = "T", race = "H", cls = "Fighter", gold = 100)
        val ui = MutableStateFlow(GameUiState(character = ch))
        clampInfiniteGold(ui)
        assertEquals(100, ui.value.character!!.gold)
    }

    @Test fun `clamp is idempotent`() {
        Cheats.infiniteGold = true
        val ch = Character(name = "T", race = "H", cls = "Fighter", gold = 999_999)
        val ui = MutableStateFlow(GameUiState(character = ch))
        val before = ui.value
        clampInfiniteGold(ui)
        assertEquals("no state churn", before, ui.value)
    }
}
```

- [ ] **Step 7.3: Run test to verify it fails**

Run: `gradle test --tests "com.realmsoffate.game.game.InfiniteGoldClampTest"`
Expected: FAIL — `clampInfiniteGold` does not exist.

- [ ] **Step 7.4: Extract the clamp as a top-level helper**

At the bottom of `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` (or just below the imports, top-level, outside the class), add:

```kotlin
internal const val INF_GOLD = 999_999

/**
 * Clamp character.gold to [INF_GOLD] if the infinite-gold cheat is active.
 * Extracted as a top-level helper so unit tests can target it directly.
 */
internal fun clampInfiniteGold(ui: kotlinx.coroutines.flow.MutableStateFlow<GameUiState>) {
    if (!Cheats.infiniteGold) return
    val s = ui.value
    val ch = s.character ?: return
    if (ch.gold == INF_GOLD) return
    val updated = com.realmsoffate.game.data.deepCopy(ch).apply { gold = INF_GOLD }
    ui.value = s.copy(character = updated)
}
```

**If `com.realmsoffate.game.data.deepCopy` is an extension function** (e.g., `ch.deepCopy()`), use that form instead:
```kotlin
val updated = ch.deepCopy().apply { gold = INF_GOLD }
```

Check via:
```
grep -n "fun.*deepCopy\|deepCopy():" app/src/main/kotlin/com/realmsoffate/game/data/ -r | head
```

- [ ] **Step 7.5: Call the clamp at the post-turn point**

Inside `GameViewModel`, in the AI-response completion path (end of `dispatchToAi`'s success handler, i.e. wherever `_ui.value = ...` assigns the final post-turn state), add one line:

```kotlin
clampInfiniteGold(_ui)
```

- [ ] **Step 7.6: Run tests to verify pass**

Run: `gradle test --tests "com.realmsoffate.game.game.InfiniteGoldClampTest"`
Expected: PASS.

- [ ] **Step 7.7: Run full suite**

Run: `gradle test`
Expected: PASS.

- [ ] **Step 7.8: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/InfiniteGoldClampTest.kt
git commit -m "feat(cheats): clamp character gold to 999999 post-turn when infinite-gold is on"
```

---

## Task 8: Top-bar gold display swaps to ∞ when infinite-gold is on

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt`

- [ ] **Step 8.1: Add VM-aware gold display**

In `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt`, around line 189, the current line is:

```kotlin
GoldInline(
    "${ch.gold}",
    MaterialTheme.colorScheme.tertiary
)
```

Replace with:

```kotlin
val infiniteGold = com.realmsoffate.game.game.Cheats.infiniteGold
GoldInline(
    if (infiniteGold) "∞" else "${ch.gold}",
    MaterialTheme.colorScheme.tertiary
)
```

**Note:** `Cheats.infiniteGold` is read directly (not via a state-backed flow) — that's a read of a `@Volatile var`. Compose will *not* automatically recompose on it. For reliable reactivity, pass the flag in via the `GameTopBar` signature instead:

Change `GameTopBar` signature (around line 80) to add a parameter:

```kotlin
@Composable
internal fun GameTopBar(
    state: GameUiState,
    showSceneContext: Boolean = false,
    onSettingsClick: () -> Unit = {},
    infiniteGold: Boolean = false,
) {
```

Use `infiniteGold` at the call site:

```kotlin
GoldInline(
    if (infiniteGold) "∞" else "${ch.gold}",
    MaterialTheme.colorScheme.tertiary
)
```

Update the caller in `GameScreen.kt` (around line 127):

```kotlin
val infiniteGold by vm.cheatInfiniteGold.collectAsState()
GameTopBar(
    state,
    showSceneContext = tab == GameTab.Chat &&
        state.currentScene != "default" &&
        state.combat == null,
    onSettingsClick = { panel = Panel.Settings },
    infiniteGold = infiniteGold,
)
```

- [ ] **Step 8.2: Build to confirm compile**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8.3: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "feat(cheats): top bar renders gold as ∞ when infinite-gold cheat is active"
```

---

## Task 9: Build the `CheatsOverlay` — 2×2 grid with expandable descriptions

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/CheatsOverlay.kt`

- [ ] **Step 9.1: Create the overlay file**

Create `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/CheatsOverlay.kt`:

```kotlin
package com.realmsoffate.game.ui.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.ui.theme.RealmsSpacing

private enum class CheatId { UNNATURAL_20, LOSER, INFINITE_GOLD, OVERPREPARED }

private data class CheatTile(
    val id: CheatId,
    val emoji: String,
    val name: String,
    val teaser: String,
    val description: String
)

private val TILES = listOf(
    CheatTile(CheatId.UNNATURAL_20, "🎯", "Unnatural 20", "Every roll is a nat 20.",
        "Every d20 roll lands on 20. Skill checks, initiative, haggles — all triumph. Disables Loser."),
    CheatTile(CheatId.LOSER, "💩", "Loser", "Every roll is a 1.",
        "Every d20 roll is a 1. Nothing works. Embrace calamity. Disables Unnatural 20."),
    CheatTile(CheatId.INFINITE_GOLD, "💰", "1%", "Infinite gold.",
        "Your gold is bottomless. Spend freely; the coffers refill each turn. Displays as ∞."),
    CheatTile(CheatId.OVERPREPARED, "📚", "Overprepared", "Instant L20.",
        "Instantly level your character to 20. Stat points and feats auto-assigned. One-shot.")
)

@Composable
fun CheatsOverlay(
    unnaturalTwenty: Boolean,
    loser: Boolean,
    infiniteGold: Boolean,
    characterLevel: Int,
    onToggleUnnaturalTwenty: (Boolean) -> Unit,
    onToggleLoser: (Boolean) -> Unit,
    onToggleInfiniteGold: (Boolean) -> Unit,
    onApplyOverprepared: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf<CheatId?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("🃏  Cheats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "The dungeon master looks away...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                // 2×2 grid
                Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                    Tile(
                        tile = TILES[0],
                        active = unnaturalTwenty,
                        expanded = expanded == CheatId.UNNATURAL_20,
                        onTap = { expanded = if (expanded == CheatId.UNNATURAL_20) null else CheatId.UNNATURAL_20 },
                        modifier = Modifier.weight(1f)
                    )
                    Tile(
                        tile = TILES[1],
                        active = loser,
                        expanded = expanded == CheatId.LOSER,
                        onTap = { expanded = if (expanded == CheatId.LOSER) null else CheatId.LOSER },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(RealmsSpacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)) {
                    Tile(
                        tile = TILES[2],
                        active = infiniteGold,
                        expanded = expanded == CheatId.INFINITE_GOLD,
                        onTap = { expanded = if (expanded == CheatId.INFINITE_GOLD) null else CheatId.INFINITE_GOLD },
                        modifier = Modifier.weight(1f)
                    )
                    Tile(
                        tile = TILES[3],
                        active = false,  // Overprepared is one-shot, never "active"
                        expanded = expanded == CheatId.OVERPREPARED,
                        onTap = { expanded = if (expanded == CheatId.OVERPREPARED) null else CheatId.OVERPREPARED },
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedVisibility(visible = expanded != null) {
                    Column(Modifier.padding(top = RealmsSpacing.m)) {
                        val tile = TILES.firstOrNull { it.id == expanded }
                        if (tile != null) {
                            Text(tile.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(RealmsSpacing.s))
                            when (tile.id) {
                                CheatId.UNNATURAL_20 -> ActionButton(
                                    label = if (unnaturalTwenty) "Turn Off" else "Turn On",
                                    onClick = { onToggleUnnaturalTwenty(!unnaturalTwenty) }
                                )
                                CheatId.LOSER -> ActionButton(
                                    label = if (loser) "Turn Off" else "Turn On",
                                    onClick = { onToggleLoser(!loser) }
                                )
                                CheatId.INFINITE_GOLD -> ActionButton(
                                    label = if (infiniteGold) "Turn Off" else "Turn On",
                                    onClick = { onToggleInfiniteGold(!infiniteGold) }
                                )
                                CheatId.OVERPREPARED -> ActionButton(
                                    label = if (characterLevel >= 20) "Already maxed" else "Apply",
                                    enabled = characterLevel < 20,
                                    onClick = { onApplyOverprepared(); expanded = null }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun Tile(
    tile: CheatTile,
    active: Boolean,
    expanded: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        active -> MaterialTheme.colorScheme.primary
        expanded -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        onClick = onTap,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .heightIn(min = 120.dp)
            .border(if (active) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
    ) {
        Column(
            Modifier.fillMaxSize().padding(RealmsSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(tile.emoji, fontSize = 40.sp)
            Spacer(Modifier.height(RealmsSpacing.xs))
            Text(
                tile.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                tile.teaser,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (active) {
                Spacer(Modifier.height(RealmsSpacing.xs))
                Text("✓ ON", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) { Text(label) }
}
```

- [ ] **Step 9.2: Build to confirm compile**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9.3: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/overlays/CheatsOverlay.kt
git commit -m "feat(cheats): CheatsOverlay — 2x2 grid with expandable descriptions"
```

---

## Task 10: Top-bar joker button + wire CheatsOverlay into GameScreen

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 10.1: Extend `GameTopBar` signature with cheats-visible flag + callback**

In `TopBar.kt` around line 80, extend:

```kotlin
@Composable
internal fun GameTopBar(
    state: GameUiState,
    showSceneContext: Boolean = false,
    onSettingsClick: () -> Unit = {},
    infiniteGold: Boolean = false,
    cheatsEnabled: Boolean = false,
    onCheatsClick: () -> Unit = {},
) {
```

Inside the `actions = { ... }` block (around line 149), add a joker button *before* the existing settings button:

```kotlin
actions = {
    IconButton(onClick = { statsExpanded = !statsExpanded }) { /* ... unchanged ... */ }
    if (cheatsEnabled) {
        IconButton(onClick = onCheatsClick) {
            Text("🃏", fontSize = 22.sp)
        }
    }
    IconButton(onClick = onSettingsClick) {
        Icon(Icons.Default.Settings, contentDescription = "Settings")
    }
}
```

- [ ] **Step 10.2: Wire the overlay from `GameScreen`**

In `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`:

Near the existing screen-level state declarations (top of the `GameScreen` composable where `var panel by remember ...` etc. are defined), add:

```kotlin
var showCheatsOverlay by remember { mutableStateOf(false) }
val cheatsEnabled by vm.cheatsEnabled.collectAsState()
val unnaturalTwenty by vm.cheatUnnaturalTwenty.collectAsState()
val loser by vm.cheatLoser.collectAsState()
val infiniteGold by vm.cheatInfiniteGold.collectAsState()
```

Update the `GameTopBar(...)` call around line 127 to pass the new params:

```kotlin
GameTopBar(
    state,
    showSceneContext = tab == GameTab.Chat &&
        state.currentScene != "default" &&
        state.combat == null,
    onSettingsClick = { panel = Panel.Settings },
    infiniteGold = infiniteGold,
    cheatsEnabled = cheatsEnabled,
    onCheatsClick = { showCheatsOverlay = true }
)
```

At the bottom of the composable's content — alongside other dialogs/overlays — render:

```kotlin
if (showCheatsOverlay) {
    com.realmsoffate.game.ui.overlays.CheatsOverlay(
        unnaturalTwenty = unnaturalTwenty,
        loser = loser,
        infiniteGold = infiniteGold,
        characterLevel = state.character?.level ?: 1,
        onToggleUnnaturalTwenty = { vm.setUnnaturalTwenty(it) },
        onToggleLoser = { vm.setLoser(it) },
        onToggleInfiniteGold = { vm.setInfiniteGold(it) },
        onApplyOverprepared = {
            vm.applyOverprepared()
            showCheatsOverlay = false
        },
        onDismiss = { showCheatsOverlay = false }
    )
}
```

- [ ] **Step 10.3: Expose `applyOverprepared` from VM**

In `GameViewModel`, add:

```kotlin
fun applyOverprepared() { progressionHandler.applyOverprepared() }
```

(Near other handler-delegation methods.)

- [ ] **Step 10.4: Build to confirm compile**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "feat(cheats): joker icon in top bar opens CheatsOverlay when enabled"
```

---

## Task 11: Title-screen Cheats button + disable confirmation dialog

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/TitleScreen.kt`

- [ ] **Step 11.1: Wire `cheatsEnabled` into the title screen**

At the top of the `TitleScreen` composable (around line 54, below `val slots by vm.saveSlots.collectAsState()`), add:

```kotlin
val cheatsEnabled by vm.cheatsEnabled.collectAsState()
var showDisableDialog by remember { mutableStateOf(false) }
```

- [ ] **Step 11.2: Add the conditional Cheats tile**

Find the second `Row` containing `SecondaryTile`s (around line 153: "Graveyard" + "API Setup"). **After** this row, add a third row that only renders when cheats are enabled:

```kotlin
if (cheatsEnabled) {
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SecondaryTile(
            icon = Icons.Default.Settings,  // unused — iconText overrides
            label = "Cheats",
            enabled = true,
            onClick = { showDisableDialog = true },
            modifier = Modifier.weight(1f),
            iconText = "🃏"
        )
        // Spacer weight to keep the tile left-aligned
        Spacer(Modifier.weight(1f))
    }
}
```

- [ ] **Step 11.3: Add the disable confirmation dialog**

At the very bottom of the `TitleScreen` composable body (alongside `if (loadSheet) { ... }` etc.), add:

```kotlin
if (showDisableDialog) {
    AlertDialog(
        onDismissRequest = { showDisableDialog = false },
        title = { Text("Disable Cheats?") },
        text = { Text("All cheat toggles will be cleared. Type the code in chat to re-enable.") },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.disableCheats()
                    showDisableDialog = false
                }
            ) {
                Text("Disable", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { showDisableDialog = false }) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 11.4: Build to confirm compile**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/setup/TitleScreen.kt
git commit -m "feat(cheats): title-screen Cheats button with disable confirmation"
```

---

## Task 12: Deploy to emulator + runtime verification

**Files:** none — runtime verification only.

- [ ] **Step 12.1: Deploy**

```
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Expected: App launches.

- [ ] **Step 12.2: Baseline debug bridge P0 + P1**

Follow `.cursor/rules/debug-bridge-test-procedures.mdc` P0 (`/ping`, `/describe`) and P1 (basic crash-free navigation through Title → Character Creation → Game).
Expected: P0/P1 pass, no crash.

- [ ] **Step 12.3: Unlock test**

1. Start or continue a save to reach the chat screen.
2. Copy-paste the konami sequence `⬆️⬆️⬇️⬇️⬅️➡️⬅️➡️🅱️🅰️` into the chat field; tap send.

Expected:
- Snackbar / system message: "🎉 Cheats unlocked — see the 🃏 in the top bar"
- Joker 🃏 icon visible in the top bar.
- No narration from AI, no turn counter increment.

Also verify: submit the code a second time. Expected: "Cheats already unlocked" message; still no AI call.

- [ ] **Step 12.4: Unnatural 20 test**

1. Tap 🃏 → tap Unnatural 20 tile → tap Turn On.
2. Close overlay, type an ambiguous action in chat (e.g. "I try to pick the lock").

Expected: pre-roll displays `20` (nat 20); AI narrates a success.

Revert: reopen overlay, tap Unnatural 20 → Turn Off. Try another action; rolls random in 1–20.

- [ ] **Step 12.5: Loser test + mutual exclusion**

1. In overlay, tap Unnatural 20 → Turn On. Tap Loser → Turn On.

Expected: Unnatural 20 toggle visually loses its ON state; Loser shows ON.

2. Type an action. Pre-roll shows `1`.

- [ ] **Step 12.6: Infinite-gold test**

1. Turn off Loser. Turn on 1%.

Expected: Top-bar gold shows `∞`.

2. Go to a merchant, buy any item.

Expected: deduction visible momentarily; after the next turn resolves, top-bar gold is back to `∞` and the underlying value is 999999 (verify via debug bridge `/describe` or stats panel).

- [ ] **Step 12.7: Overprepared test**

1. Create a fresh L1 character (or use current).
2. Open 🃏 overlay → tap Overprepared → tap Apply.

Expected: system message "You are Overprepared."; character level jumps to 20; HP maxed; stats panel reflects L20.

Verify idempotency: reopen overlay, tap Overprepared. Button reads "Already maxed" and is disabled.

Verify combat guard: enter combat, open overlay, tap Overprepared. (It should still be disabled at L20 from the prior step — the guard is a belt-and-suspenders check.)

- [ ] **Step 12.8: Disable test**

1. Return to title screen (exit save or reload).

Expected: Cheats button with 🃏 icon visible on title screen.

2. Tap Cheats button → Disable.

Expected: confirmation dialog → tap Disable → button disappears.

3. Load the save again.

Expected: Joker 🃏 gone from top bar. Rolls are random. Gold displays numerically.

4. Re-type konami in chat.

Expected: fresh unlock cycle; joker re-appears; all four cheats are OFF (not restored from prior session).

- [ ] **Step 12.9: Commit the verification log**

This task modifies no source — no commit.

---

## Self-Review

Check against the spec (`docs/superpowers/specs/2026-04-22-konami-cheat-menu-design.md`):

- [x] **CheatsStore with 4 prefs keys** → Task 3.
- [x] **Cheats singleton** → Task 1.
- [x] **Dice intercept on d20/d(20), not on `roll()`** → Task 2.
- [x] **Konami intercept in `submitAction`** → Task 5.
- [x] **Mutual exclusion at the store write boundary** → Task 3 (`setUnnaturalTwenty` / `setLoser` clear each other).
- [x] **Master disable clears all four flags atomically** → Task 3 (`disable()` writes all 5 keys).
- [x] **Overprepared with combat guard + L20 idempotency** → Task 6.
- [x] **Infinite-gold clamp post-turn, 999999 underlying, ∞ displayed** → Tasks 7 + 8.
- [x] **CheatsOverlay with expandable description, active border, Apply for one-shot** → Task 9.
- [x] **Top-bar joker icon + conditional visibility** → Task 10.
- [x] **Title-screen Cheats button + disable dialog** → Task 11.
- [x] **Runtime verification** → Task 12.

Type consistency spot-check:
- `CheatsStore` methods: `unlock`, `disable`, `setUnnaturalTwenty`, `setLoser`, `setInfiniteGold` — referenced consistently across Tasks 3, 4, 10, 11.
- VM methods: `unlockCheats`, `disableCheats`, `setUnnaturalTwenty`, `setLoser`, `setInfiniteGold`, `applyOverprepared` — consistent across Tasks 4, 10, 11.
- Flow names: `cheatsEnabled`, `cheatUnnaturalTwenty`, `cheatLoser`, `cheatInfiniteGold` — consistent across Tasks 4, 8, 10, 11.
- Singleton fields: `Cheats.enabled`, `Cheats.forceCrit`, `Cheats.forceFail`, `Cheats.infiniteGold` — consistent across Tasks 1, 2, 4, 7, 8.

No placeholders. No references to undefined symbols. Plan covers every spec requirement.
