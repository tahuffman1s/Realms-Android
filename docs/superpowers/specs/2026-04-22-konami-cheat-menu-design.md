# Konami-Unlocked Cheat Menu — Design

**Date:** 2026-04-22
**Branch:** TBD (new feature branch)
**Status:** Spec, approved for planning

## Summary

A hidden cheat system unlocked by typing an emoji Konami code (`⬆️⬆️⬇️⬇️⬅️➡️⬅️➡️🅱️🅰️`) into the in-game chat field. Once unlocked, a joker icon appears in the top bar during gameplay, opening a 2×2 grid of four cheats: **Unnatural 20**, **Loser**, **1%**, and **Overprepared**. A matching Cheats button appears on the title screen that serves as a single-tap "disable everything" switch; disabling re-hides both entry points. Re-enable by typing the code again.

## Goals

- Hidden, discoverable-only-if-you-know cheat system.
- Four cheats covering skill-check outcomes, economy, and progression.
- Title-screen entry point for disabling; in-game entry point for toggling.
- Cheat state is device-global (persisted in DataStore prefs, not in `.rofsave`).

## Non-Goals

- No per-save cheat tracking. Saves do not record whether cheats were used.
- No additional cheats beyond the four specified.
- No in-narrative acknowledgment of cheats by the AI (but the AI sees the numeric gold/level, so it will naturally describe them).
- No UI tests; verification is via debug-bridge in-emulator steps.

## Architecture

Three new files, plus light touchpoints on existing code:

| New file | Role |
|---|---|
| `data/CheatsStore.kt` | DataStore-backed persistence. Owns the five flags (master + four cheats). Exposes `Flow`s. |
| `game/Cheats.kt` | Pure-Kotlin in-memory singleton consulted by `Dice` and gold clamp logic. Written to by the VM as prefs flows emit. No Android imports. |
| `ui/overlays/CheatsOverlay.kt` | The 2×2 grid dialog with expandable description panels. |

**State flow:**
```
DataStore (source of truth)
    ↓ Flow<Boolean>
CheatsStore
    ↓ collected in GameViewModel.init
Cheats (singleton, in-memory)
    ↓ read synchronously
Dice.d20() / Dice.d(20)
VM turn tick (gold clamp)
UI (top bar joker visibility, ∞ display)
```

The singleton indirection exists because `Dice` is pure Kotlin (no suspend, no Context), and roll sites are hot paths that must not suspend.

**Invariant enforcement:** mutual exclusion between Unnatural 20 and Loser is enforced at the `CheatsStore` write boundary — `setUnnaturalTwenty(true)` also writes `loser = false` in the same transaction, and vice versa. UI cannot produce inconsistent state.

**Master disable cascade:** `CheatsStore.disable()` writes `enabled = false` **and** clears all four cheat flags in one transaction. This means re-enabling later starts from a clean slate (no stale toggles resurrect).

## Components

### CheatsStore

New `DataStore<Preferences>` instance (separate from `PreferencesStore`'s `rpg_prefs`, named `rpg_cheats`) to keep cheats isolated and easy to wipe in tests.

Keys:
- `cheats_enabled: Boolean` — master flag
- `cheat_unnatural_twenty: Boolean`
- `cheat_loser: Boolean`
- `cheat_infinite_gold: Boolean`

(Overprepared has no persistent flag — it's a one-shot action gated at call time by `character.level >= 20`.)

Public API (all suspend or Flow):
```kotlin
val enabled: Flow<Boolean>
val unnaturalTwenty: Flow<Boolean>
val loser: Flow<Boolean>
val infiniteGold: Flow<Boolean>
suspend fun unlock()                      // sets enabled = true
suspend fun disable()                     // clears enabled + all four flags
suspend fun setUnnaturalTwenty(on: Boolean)  // also clears loser if on
suspend fun setLoser(on: Boolean)         // also clears unnaturalTwenty if on
suspend fun setInfiniteGold(on: Boolean)
```

### Cheats (singleton)

```kotlin
object Cheats {
    @Volatile var enabled: Boolean = false
    @Volatile var forceCrit: Boolean = false
    @Volatile var forceFail: Boolean = false
    @Volatile var infiniteGold: Boolean = false

    fun reset() {
        enabled = false; forceCrit = false; forceFail = false; infiniteGold = false
    }
}
```

`@Volatile` because roll sites read on arbitrary threads (combat, VM coroutines) while prefs collectors write on the VM's `viewModelScope` dispatcher.

### Konami interception

Sequence (const):
```kotlin
const val KONAMI_CODE = "⬆️⬆️⬇️⬇️" +
                       "⬅️➡️⬅️➡️" +
                       "🅱️🅰️"
// Decoded: ⬆️⬆️⬇️⬇️⬅️➡️⬅️➡️🅱️🅰️
```

In `GameViewModel` (or wherever chat submission originates), the first check is:
```kotlin
if (message.trim() == KONAMI_CODE) {
    viewModelScope.launch { cheatsStore.unlock() }
    _snackbar.emit(if (wasEnabled) "Cheats already unlocked" else "🎉 Cheats unlocked")
    return  // swallow: no history append, no AI call, no turn tick
}
```

This runs *before* any existing empty/guard logic so an empty-message guard cannot block it.

### Dice interception

```kotlin
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
    fun roll(formula: String): Int { /* unchanged */ }
}
```

`roll("NdM+K")` is **not** intercepted — damage dice remain honest. Unnatural 20 affects skill/attack/initiative d20s only.

### Gold clamp

In `GameViewModel.onTurnComplete()` (or equivalent post-turn hook), after existing state updates:
```kotlin
if (Cheats.infiniteGold) {
    val ch = ui.value.character?.deepCopy() ?: return
    if (ch.gold != INF_GOLD) {
        ch.gold = INF_GOLD
        ui.value = ui.value.copy(character = ch)
    }
}
private const val INF_GOLD = 999_999
```

Display swap in exactly two places (confirmed by grep):
- `ui/game/TopBar.kt` — gold label
- `ui/panels/StatsPage.kt` — gold line

```kotlin
val goldText = if (Cheats.infiniteGold) "∞" else character.gold.toString()
```

The actual numeric value is still `999999` in prompts, save data, and merchant affordability math — only the two user-facing labels render "∞".

### Overprepared (one-shot)

New method on `ProgressionHandler`:
```kotlin
fun applyOverprepared(): Result<Unit> {
    val s = ui.value
    if (s.combat != null) return Result.failure(IllegalStateException("Cannot level during combat"))
    val ch = s.character?.deepCopy() ?: return Result.failure(...)
    if (ch.level >= 20) return Result.success(Unit)  // idempotent

    for (target in (ch.level + 1)..20) {
        ch.level = target
        ch.maxHp += maxHitDieFor(ch.cls)  // max roll each level
        ch.hp = ch.maxHp
        // stat points: 2 per level (or whatever existing gainStatPointsPerLevel is)
        repeat(statPointsPerLevel(target)) { /* auto-assign to primary stat */ }
        if (target % 4 == 0) autoGrantDefaultFeat(ch)
    }
    ch.xp = GameViewModel.levelThreshold(20)
    _pendingLevelUp.value = null
    _pendingStatPoints.value = 0
    _pendingFeat.value = false
    ui.value = s.copy(character = ch, messages = s.messages + DisplayMessage.System("You are Overprepared."))
    return Result.success(Unit)
}
```

Stat-point auto-assignment: pick the class's primary stat (STR for fighter, DEX for rogue, etc.) — deterministic, no user choice. Feat selection: grant a generic default feat (e.g., "Tough") to avoid gating on a picker. Rationale: this is a cheat, the player is not looking for agency here; they want instant power.

### CheatsOverlay UI

Layout:
```
┌─────────────────────────────────────┐
│ 🃏 Cheats                           │
│ The dungeon master looks away...    │
├──────────────┬──────────────────────┤
│  🎯          │  💩                  │
│ Unnatural 20 │  Loser               │
│ "Every d20…" │  "Every d20…"        │
├──────────────┼──────────────────────┤
│  💰          │  📚                  │
│  1%          │  Overprepared        │
│ "Bottomless…"│  "Instant 20…"       │
├──────────────┴──────────────────────┤
│ [Expanded description panel         │
│  appears here when a tile is        │
│  tapped. Contains full description  │
│  + action button.]                  │
└─────────────────────────────────────┘
               [Close]
```

- Tapping a tile sets `expandedCheat` state; panel animates in below the grid.
- Tapping the same tile again, or tapping Close, collapses the panel.
- Active toggles show a 2dp primary-color border + a small ✓ badge in the top-right corner.
- Panel's action button: "Turn On" / "Turn Off" for toggles, "Apply" for Overprepared (disabled and labeled "Already maxed" when `character.level >= 20`).

Tile data (final copy):

| Emoji | Name | Short teaser | Full description |
|---|---|---|---|
| 🎯 | Unnatural 20 | Every roll is a nat 20. | Every d20 roll lands on 20. Skill checks, initiative, haggles — all triumph. Disables Loser. |
| 💩 | Loser | Every roll is a 1. | Every d20 roll is a 1. Nothing works. Embrace calamity. Disables Unnatural 20. |
| 💰 | 1% | Infinite gold. | Your gold is bottomless. Spend freely; the coffers refill each turn. Displays as ∞. |
| 📚 | Overprepared | Instant L20. | Instantly level your character to 20. Stat points and feats auto-assigned. One-shot. |

### Title-screen Cheats button

In `TitleScreen.kt`, add a third row (or replace the current arrangement) containing a conditionally-rendered `SecondaryTile`:
```kotlin
val cheatsEnabled by vm.cheatsEnabled.collectAsState(initial = false)
if (cheatsEnabled) {
    SecondaryTile(
        icon = Icons.Default.Casino,  // or emoji-only with iconText = "🃏"
        label = "Cheats",
        enabled = true,
        onClick = { showDisableDialog = true },
        iconText = "🃏"
    )
}
```

`showDisableDialog = true` opens an `AlertDialog`:
- Title: "Disable Cheats?"
- Body: "All cheat toggles will be cleared. Type the code in chat to re-enable."
- Confirm button "Disable" (error color) → `vm.disableCheats()` → calls `cheatsStore.disable()`.
- Dismiss button "Cancel".

### Top-bar joker button

In `TopBar.kt`, add an `IconButton` between existing buttons:
```kotlin
if (Cheats.enabled) {
    IconButton(onClick = onOpenCheats) {
        Text("🃏", fontSize = 22.sp)
    }
}
```

`onOpenCheats` is hoisted via an existing overlay-state pattern in `GameScreen.kt` (same as Inventory/Quests/etc.).

## Data Flow Summary

1. **Unlock:** chat submit → VM intercepts → `cheatsStore.unlock()` → prefs flow → `Cheats.enabled = true` → top bar recomposes with joker visible; title screen (when next shown) renders Cheats button.
2. **Toggle cheat:** tap tile → tap action button → VM calls `cheatsStore.setXxx(true)` → prefs flow → `Cheats.forceCrit = true` (etc.) → next `Dice.d20()` reads new value.
3. **Apply Overprepared:** tap tile → tap Apply → VM calls `progressionHandler.applyOverprepared()` → character deep-copied and updated → UI state emits → snackbar shown.
4. **Disable:** title screen tap → confirm → `cheatsStore.disable()` → all five flags cleared in one prefs transaction → `Cheats.reset()` → top bar joker and title Cheats button both disappear on next recomposition.

## Error Handling & Edge Cases

- **Double-unlock is a no-op** — intercept still fires; snackbar distinguishes "already unlocked".
- **Disable during active run** — flags flip instantly; in-flight rolls already resolved are unaffected; character's stored `gold` is *not* reset from 999999 (we don't silently rewrite player state).
- **Overprepared mid-combat** — disallowed; show "Cannot level during combat" message; dialog stays open.
- **Overprepared on already-L20 character** — tile button renders "Already maxed" and is disabled.
- **Unnatural 20 + combat crits** — forcing 20 triggers natural-crit damage rules in `CombatSystem`. Intentional.
- **1% with merchant overflow** — merchants read numeric gold (999999), so affordability is trivial; spend messages are truthful within a turn, re-clamp happens after.
- **Save/load crossing cheat boundary** — cheats are device-global prefs, not in `.rofsave`. Loading an old save while cheats are on applies current flags. Gold/level accumulated during cheating is *not* rolled back on disable.
- **Empty/whitespace chat submit** — konami check uses `message.trim()`; empty input fails the exact-equals test and falls through to normal handling.
- **Unicode variation selectors** — sequence is built from explicit codepoints (`️`) to prevent IDE reformatting from breaking the compare.

## Testing

Unit tests (JVM; Robolectric only where DataStore requires Android context):

| Test file | Coverage |
|---|---|
| `DiceCheatsTest` | `Cheats.forceCrit` → `d20()` always returns 20 (100 iterations); same for `d(20)`. `forceFail` → 1. Both false → in range [1,20]. Reset between cases. |
| `CheatsStoreTest` (Robolectric) | `setUnnaturalTwenty(true)` clears `loser`; `setLoser(true)` clears `unnaturalTwenty`; `disable()` clears all four; `unlock()` sets master only. |
| `KonamiInterceptTest` | VM.submitTurn with exact sequence → flag flips, no AI call, no history append. Trailing space → still intercepted (trim). Substring → passes through normally. |
| `ProgressionOverpreparedTest` | L1 character → level=20, xp=threshold(20), HP = sum of max hit dice, stat points drained, feats present. L20 → no-op. L7 → L20. Mid-combat → returns failure, character unchanged. |
| `InfiniteGoldClampTest` | Flag on + character.gold = 100 → `onTurnComplete()` → gold = 999999. Flag off → no clamp. |

In-emulator (debug-bridge) verification — P0/P1 baseline + feature steps:
1. Type konami in chat → snackbar appears; joker in top bar; message not in history.
2. Open cheats overlay; tap each tile to verify description expands.
3. Toggle Unnatural 20 → trigger a skill check via debug-bridge → verify log shows roll=20.
4. Toggle Loser → verify Unnatural 20 turned off → trigger skill check → roll=1.
5. Toggle 1% → verify top bar shows ∞ → spend at merchant → after next turn, back to ∞.
6. Tap Overprepared → confirm → verify character.level == 20 in stats panel.
7. Return to title → Cheats button present → tap → confirm disable → joker gone.
8. Re-type konami → everything re-appears.

## Files Changed / Added

**New:**
- `app/src/main/kotlin/com/realmsoffate/game/data/CheatsStore.kt`
- `app/src/main/kotlin/com/realmsoffate/game/game/Cheats.kt`
- `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/CheatsOverlay.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/DiceCheatsTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/CheatsStoreTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/KonamiInterceptTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/ProgressionOverpreparedTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/InfiniteGoldClampTest.kt`

**Modified:**
- `app/src/main/kotlin/com/realmsoffate/game/game/Dice.kt` — consult `Cheats`.
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — konami intercept, CheatsStore wiring, gold clamp hook, expose `cheatsEnabled` state.
- `app/src/main/kotlin/com/realmsoffate/game/game/handlers/ProgressionHandler.kt` — `applyOverprepared()`.
- `app/src/main/kotlin/com/realmsoffate/game/ui/setup/TitleScreen.kt` — conditional Cheats button + disable dialog.
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt` — conditional joker icon + ∞ gold display.
- `app/src/main/kotlin/com/realmsoffate/game/ui/panels/StatsPage.kt` — ∞ gold display.
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt` — wire `CheatsOverlay` into overlay state.
- `app/src/main/kotlin/com/realmsoffate/game/RealmsApp.kt` — construct `CheatsStore` and pass into VM.

## Open Questions

None at spec-approval time. Stat-point auto-assignment target stat and default feat name can be finalized during implementation without revisiting the spec.
