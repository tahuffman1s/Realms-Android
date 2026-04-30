# Phase 3 — Cheats Perf, Random Character, Lore Reactivity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close issues #18 (cheat menu opens slowly + description text flashes), #20 (one-tap fully randomized character), and #29 (lore screen updates whenever player actions affect lore).

**Architecture:** Three orthogonal changes.
1. **#18:** Stabilize the description block in `CheatsOverlay` so it never collapses to height 0 during a cross-fade (the source of the flash) and pre-resolve button labels via `derivedStateOf` so toggle taps don't churn the entire dialog. Stop eager-collecting two unused cheat flags in `GameScreen` to reduce upstream recomposition pressure.
2. **#20:** New `RandomCharacter` pure utility plus a `PlayerNamePool` data file. A "Random" button on Identity step calls the utility, fills every `rememberSaveable`, and jumps `step = 5` (Confirm). Backstory is already attached at `vm.startNewGame` time — no change there.
3. **#29:** The pipeline already wires `metadata.lore_entries` → `WorldReducer` → `worldLore.history`, but the LLM prompt never tells the model *when* to emit them. Add an explicit "When to emit lore_entries / faction_updates" rule to `Prompts.kt`. Add `lastSeenLoreSize: Int` to `GameUiState` and a "new lore" dot on the Lore tab in `JournalPager`, cleared when the user views that tab.

**Tech Stack:** Kotlin · Jetpack Compose · JUnit4 · Robolectric · Gradle (Kotlin DSL).

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/realmsoffate/game/data/PlayerNamePool.kt` — list of 30+ fantasy first names.
- `app/src/main/kotlin/com/realmsoffate/game/game/RandomCharacter.kt` — pure utility: `RandomCharacter.generate(rng: Random = Random.Default): RandomizedCharacter` returns all 11 fields.
- `app/src/test/kotlin/com/realmsoffate/game/game/RandomCharacterTest.kt` — unit tests for the utility.

**Modify:**
- `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/CheatsOverlay.kt` — reserve fixed-height description container; memoize tile lookup; derive button labels.
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt` — drop `unnaturalTwenty` / `loser` collectors (only `infiniteGold` is read up here; the others are read inside the overlay).
- `app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt` — Random button on Identity step + `randomizeAll()` lambda that fills every state and sets `step = 5`.
- `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — add explicit "when to emit lore_entries" rule near the metadata schema block.
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — add `lastSeenLoreSize: Int = 0` to `GameUiState`; add `markLoreSeen()` method; pass `cheatUnnaturalTwenty` / `cheatLoser` as before but stop reading them in `GameScreen`.
- `app/src/main/kotlin/com/realmsoffate/game/ui/components/PanelTabRow.kt` — add `badge: Boolean = false` to `PanelTab`; render a small dot when true.
- `app/src/main/kotlin/com/realmsoffate/game/ui/game/JournalPager.kt` — wire badge from `state.worldLore.history.size > state.lastSeenLoreSize`; call `vm.markLoreSeen()` when Lore page becomes current.

**Test (modify or add):**
- `app/src/test/kotlin/com/realmsoffate/game/game/RandomCharacterTest.kt` — new (above).

---

## Task 1: Stabilize CheatsOverlay description (no flicker on tile-switch)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/CheatsOverlay.kt`

The flicker is caused by the description container collapsing from height ≈90dp to 0dp when `expanded = null` and re-expanding when a new tile is tapped. `AnimatedContent` cross-fades its content but cannot fade the parent dialog's *height* — `AlertDialog` re-measures synchronously, which reads as a flash. Fix: reserve a constant min-height for the description container so the dialog never reflows; switch the description renderer to always show *some* tile (initialize `expanded` to the first tile id) so first-open shows a description rather than empty space; derive the toggle button label so it doesn't recompose mid-fade.

- [ ] **Step 1.1: Replace the description block with a constant-height container**

In `CheatsOverlay.kt`, change line 77 to default-expand the first tile:

```kotlin
var expanded by remember { mutableStateOf<CheatId?>(CheatId.UNNATURAL_20) }
```

Then replace the `AnimatedContent(...)` block (lines 131–165) with a fixed-height container:

```kotlin
Spacer(Modifier.height(RealmsSpacing.m))
androidx.compose.foundation.layout.Box(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 132.dp)
) {
    AnimatedContent(
        targetState = expanded,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "cheat-description"
    ) { targetId ->
        val tile = remember(targetId) { TILES.firstOrNull { it.id == targetId } }
        if (tile != null) {
            Column {
                Text(tile.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(RealmsSpacing.s))
                CheatActionButton(
                    tile = tile,
                    unnaturalTwenty = unnaturalTwenty,
                    loser = loser,
                    infiniteGold = infiniteGold,
                    characterLevel = characterLevel,
                    onToggleUnnaturalTwenty = onToggleUnnaturalTwenty,
                    onToggleLoser = onToggleLoser,
                    onToggleInfiniteGold = onToggleInfiniteGold,
                    onApplyOverprepared = { onApplyOverprepared(); expanded = null }
                )
            }
        }
    }
}
```

- [ ] **Step 1.2: Extract the action button into a stable composable**

Add this private composable below `Tile` (after the existing `Tile` definition, around line 222):

```kotlin
@Composable
private fun CheatActionButton(
    tile: CheatTile,
    unnaturalTwenty: Boolean,
    loser: Boolean,
    infiniteGold: Boolean,
    characterLevel: Int,
    onToggleUnnaturalTwenty: (Boolean) -> Unit,
    onToggleLoser: (Boolean) -> Unit,
    onToggleInfiniteGold: (Boolean) -> Unit,
    onApplyOverprepared: () -> Unit
) {
    when (tile.id) {
        CheatId.UNNATURAL_20 -> {
            val label = remember(unnaturalTwenty) { if (unnaturalTwenty) "Turn Off" else "Turn On" }
            ActionButton(label = label, onClick = { onToggleUnnaturalTwenty(!unnaturalTwenty) })
        }
        CheatId.LOSER -> {
            val label = remember(loser) { if (loser) "Turn Off" else "Turn On" }
            ActionButton(label = label, onClick = { onToggleLoser(!loser) })
        }
        CheatId.INFINITE_GOLD -> {
            val label = remember(infiniteGold) { if (infiniteGold) "Turn Off" else "Turn On" }
            ActionButton(label = label, onClick = { onToggleInfiniteGold(!infiniteGold) })
        }
        CheatId.OVERPREPARED -> {
            val maxed = characterLevel >= 20
            val label = remember(maxed) { if (maxed) "Already maxed" else "Apply" }
            ActionButton(label = label, enabled = !maxed, onClick = onApplyOverprepared)
        }
    }
}
```

- [ ] **Step 1.3: Verify the file builds**

Run: `gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 1.4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/overlays/CheatsOverlay.kt
git commit -m "fix(cheats): stabilize description block; remove flicker on tile-switch (#18)"
```

---

## Task 2: Trim eager cheat collectors in GameScreen

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

`GameScreen` collects `unnaturalTwenty` and `loser` at line 132–133 but never uses them outside the `CheatsOverlay` block — the overlay can read them itself, avoiding a full screen recomposition every time a player toggles a cheat. Only `infiniteGold` is read in the top bar (line 148) and `cheatsEnabled` for the cheats button visibility.

- [ ] **Step 2.1: Remove unused collectors**

Delete lines 132–133 from `GameScreen.kt`:
```kotlin
val unnaturalTwenty by vm.cheatUnnaturalTwenty.collectAsState()
val loser by vm.cheatLoser.collectAsState()
```

- [ ] **Step 2.2: Read the cheats inline at the overlay site**

Change the `CheatsOverlay(...)` call at line 356 to collect on demand:

```kotlin
if (showCheatsOverlay) {
    val unnaturalTwentyLocal by vm.cheatUnnaturalTwenty.collectAsState()
    val loserLocal by vm.cheatLoser.collectAsState()
    CheatsOverlay(
        unnaturalTwenty = unnaturalTwentyLocal,
        loser = loserLocal,
        infiniteGold = infiniteGold,
        characterLevel = state.character.level,
        onToggleUnnaturalTwenty = vm::setUnnaturalTwenty,
        onToggleLoser = vm::setLoser,
        onToggleInfiniteGold = vm::setInfiniteGold,
        onApplyOverprepared = {
            vm.applyOverprepared()
            showCheatsOverlay = false
        },
        onDismiss = { showCheatsOverlay = false }
    )
}
```

(If the existing call already uses the canonical `vm::setX` references, only the two new `*Local` lines need to be added — keep all other parameters as they were.)

**IMPORTANT:** Read lines 355–370 of `GameScreen.kt` first to confirm the exact existing parameter names and lambdas, then apply the change preserving them. Do not invent function references.

- [ ] **Step 2.3: Verify the file builds**

Run: `gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "perf(cheats): collect cheat flags inside overlay instead of GameScreen (#18)"
```

---

## Task 3: PlayerNamePool data file

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/PlayerNamePool.kt`

Pure data file — no logic. A diverse pool of fantasy first names, mixed-gender, all ≤ 12 chars.

- [ ] **Step 3.1: Create the file**

```kotlin
package com.realmsoffate.game.data

/**
 * First-name pool used by the Random Character feature (issue #20).
 * Mixed-gender, fantasy-flavored, all ≤ 12 chars to fit the existing 30-char name cap.
 *
 * Keep alphabetized so additions don't conflict with concurrent edits.
 */
internal object PlayerNamePool {
    val NAMES: List<String> = listOf(
        "Aric", "Bryn", "Cael", "Daria", "Eira", "Faelan", "Gareth", "Hilde",
        "Ivara", "Jorah", "Kael", "Lyra", "Mirek", "Nyssa", "Orin", "Pyra",
        "Quinn", "Rhea", "Soren", "Talya", "Ulric", "Vex", "Wynn", "Xara",
        "Yorick", "Zenna", "Brann", "Calla", "Dorian", "Elowen", "Fenris",
        "Greta", "Halric", "Isolde", "Joran", "Kira", "Loras", "Marin",
        "Niko", "Ophir", "Perrin", "Rowan", "Saela", "Theron", "Vanya"
    )
}
```

- [ ] **Step 3.2: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/PlayerNamePool.kt
git commit -m "feat(setup): add PlayerNamePool seed list (#20)"
```

---

## Task 4: RandomCharacter utility — failing tests first

**Files:**
- Create: `app/src/test/kotlin/com/realmsoffate/game/game/RandomCharacterTest.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/game/RandomCharacter.kt`

Pure utility. `generate(rng)` returns a `RandomizedCharacter` value class with every field needed by the Identity / Appearance / Race / Class / Stats steps. The Stats step uses the class's `recommended` array directly — guaranteed valid (≤27 point-buy by definition, since recommended presets are pre-tuned).

- [ ] **Step 4.1: Write the failing tests**

```kotlin
// app/src/test/kotlin/com/realmsoffate/game/game/RandomCharacterTest.kt
package com.realmsoffate.game.game

import com.realmsoffate.game.data.PlayerNamePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RandomCharacterTest {

    @Test
    fun `generate produces non-blank name from pool`() {
        val r = RandomCharacter.generate(Random(1))
        assertTrue(r.name.isNotBlank())
        assertTrue("name '${r.name}' should come from pool", r.name in PlayerNamePool.NAMES)
    }

    @Test
    fun `generate picks a known race and class`() {
        val r = RandomCharacter.generate(Random(2))
        assertTrue(Races.find(r.race) != null)
        assertTrue(Classes.find(r.cls) != null)
    }

    @Test
    fun `generate uses class recommended preset for stats`() {
        val r = RandomCharacter.generate(Random(3))
        val def = Classes.find(r.cls)!!
        assertTrue(r.baseStats.contentEquals(def.recommended))
    }

    @Test
    fun `generate picks distinct primary and secondary bonus indices`() {
        repeat(20) { seed ->
            val r = RandomCharacter.generate(Random(seed.toLong()))
            assertNotEquals("seed=$seed", r.primaryBonus, r.secondaryBonus)
        }
    }

    @Test
    fun `generate fills every appearance and identity field`() {
        val r = RandomCharacter.generate(Random(4))
        assertTrue(r.skinTone.startsWith("#"))
        assertTrue(r.hairColor.startsWith("#"))
        assertTrue(r.hairStyle.isNotBlank())
        assertTrue(r.build.isNotBlank())
        assertTrue(r.gender.isNotBlank())
        assertTrue(r.ageBand.isNotBlank())
    }

    @Test
    fun `generate is non-deterministic across fresh entropy`() {
        // Different seeds should produce different characters (at least one field differs).
        val a = RandomCharacter.generate(Random(100))
        val b = RandomCharacter.generate(Random(200))
        val differs = a.name != b.name || a.race != b.race || a.cls != b.cls ||
            a.skinTone != b.skinTone || a.hairColor != b.hairColor ||
            a.hairStyle != b.hairStyle || a.build != b.build ||
            a.gender != b.gender || a.ageBand != b.ageBand
        assertTrue("two different seeds should not produce identical characters", differs)
    }

    @Test
    fun `generate is deterministic for a given seed`() {
        val a = RandomCharacter.generate(Random(42))
        val b = RandomCharacter.generate(Random(42))
        assertEquals(a, b)
    }
}
```

- [ ] **Step 4.2: Run the tests to confirm they fail**

Run: `gradle :app:testDebugUnitTest --tests com.realmsoffate.game.game.RandomCharacterTest`
Expected: FAIL with "RandomCharacter not found" or similar.

- [ ] **Step 4.3: Implement RandomCharacter**

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/game/RandomCharacter.kt
package com.realmsoffate.game.game

import com.realmsoffate.game.data.PlayerNamePool
import kotlin.random.Random

/**
 * Issue #20 — one-tap fully randomized character.
 *
 * Pure utility. All fields land on the same surfaces the manual character-creation flow
 * uses, so the caller can drop these straight into the existing `rememberSaveable`s and
 * jump to the Confirm step.
 */
internal data class RandomizedCharacter(
    val name: String,
    val gender: String,
    val ageBand: String,
    val skinTone: String,
    val hairColor: String,
    val hairStyle: String,
    val build: String,
    val race: String,
    val cls: String,
    val baseStats: IntArray,
    val primaryBonus: Int,
    val secondaryBonus: Int
) {
    // contentEquals for the IntArray so equality is value-based (test relies on this).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RandomizedCharacter) return false
        return name == other.name && gender == other.gender && ageBand == other.ageBand &&
            skinTone == other.skinTone && hairColor == other.hairColor &&
            hairStyle == other.hairStyle && build == other.build &&
            race == other.race && cls == other.cls &&
            baseStats.contentEquals(other.baseStats) &&
            primaryBonus == other.primaryBonus && secondaryBonus == other.secondaryBonus
    }

    override fun hashCode(): Int {
        var h = name.hashCode()
        h = 31 * h + gender.hashCode()
        h = 31 * h + ageBand.hashCode()
        h = 31 * h + skinTone.hashCode()
        h = 31 * h + hairColor.hashCode()
        h = 31 * h + hairStyle.hashCode()
        h = 31 * h + build.hashCode()
        h = 31 * h + race.hashCode()
        h = 31 * h + cls.hashCode()
        h = 31 * h + baseStats.contentHashCode()
        h = 31 * h + primaryBonus
        h = 31 * h + secondaryBonus
        return h
    }
}

internal object RandomCharacter {

    private val GENDERS = listOf("Male", "Female", "Non-binary", "Unspecified")
    private val AGE_BANDS = listOf("Young", "Adult", "Mature", "Elder")
    // Mirror the palettes in CharacterCreationScreen.kt — kept in sync deliberately.
    private val SKIN_TONES = listOf(
        "#F7D4B9", "#E8BC95", "#D19A72", "#B67B4F",
        "#935531", "#6B3A1A", "#4A2509", "#8C5A3C", "#C49C7C"
    )
    private val HAIR_COLORS = listOf(
        "#2A1E10", "#5A4330", "#8C6A44", "#B89066",
        "#D9B070", "#E8D8B0", "#9E9E9E", "#C0392B"
    )
    private val HAIR_STYLES = listOf(
        "Short", "Long", "Braided", "Shaved", "Bun", "Ponytail", "Tousled", "Flowing"
    )
    private val BUILDS = listOf("Lean", "Average", "Muscular", "Stocky", "Hulking")

    fun generate(rng: Random = Random.Default): RandomizedCharacter {
        val race = Races.list.random(rng)
        val cls = Classes.list.random(rng)
        val (primary, secondary) = race.defaultBonusIndices()
        // Some races default to the same primary/secondary index pair — re-roll the
        // secondary against the other 5 axes if so, so the Confirm step isn't blocked.
        val safeSecondary = if (primary == secondary) {
            val others = (0..5).filter { it != primary }
            others.random(rng)
        } else {
            secondary
        }
        return RandomizedCharacter(
            name = PlayerNamePool.NAMES.random(rng),
            gender = GENDERS.random(rng),
            ageBand = AGE_BANDS.random(rng),
            skinTone = SKIN_TONES.random(rng),
            hairColor = HAIR_COLORS.random(rng),
            hairStyle = HAIR_STYLES.random(rng),
            build = BUILDS.random(rng),
            race = race.name,
            cls = cls.name,
            baseStats = cls.recommended.copyOf(),
            primaryBonus = primary,
            secondaryBonus = safeSecondary
        )
    }
}
```

- [ ] **Step 4.4: Run the tests to confirm they pass**

Run: `gradle :app:testDebugUnitTest --tests com.realmsoffate.game.game.RandomCharacterTest`
Expected: PASS (7 tests).

- [ ] **Step 4.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/RandomCharacter.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/RandomCharacterTest.kt
git commit -m "feat(setup): RandomCharacter utility — full randomizer for #20"
```

---

## Task 5: Random button on Identity step

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt`

Add a small Random button next to the Name field. On tap: call `RandomCharacter.generate()`, fill every state, jump `step = 5`. Re-tap from the Confirm step (via Back → Identity → Random) gives fresh entropy because each call uses `Random.Default`. Icon: `Icons.Default.AutoAwesome` (already imported on line 14).

- [ ] **Step 5.1: Add a `randomizeAll` lambda in `CharacterCreationScreen`**

In the body of `CharacterCreationScreen` (after the existing `beginCharacter = { ... }` block, around line 102), add:

```kotlin
val randomizeAll = {
    val r = com.realmsoffate.game.game.RandomCharacter.generate()
    name = r.name
    gender = r.gender
    ageBand = r.ageBand
    skinTone = r.skinTone
    hairColor = r.hairColor
    hairStyle = r.hairStyle
    build = r.build
    race = r.race
    cls = r.cls
    baseStats.value = r.baseStats.copyOf()
    primaryBonus = r.primaryBonus
    secondaryBonus = r.secondaryBonus
    step = 5
}
```

- [ ] **Step 5.2: Pass the lambda into `IdentityStep` and add the Random button**

Change the `IdentityStep(...)` call at line 180–184 to pass the new lambda:

```kotlin
0 -> IdentityStep(
    name = name, onName = { name = it.take(30) },
    gender = gender, onGender = { gender = it },
    ageBand = ageBand, onAge = { ageBand = it },
    onRandom = randomizeAll
)
```

Update the `IdentityStep` signature (line 254) to accept `onRandom`, and add the Random button just under the Name field:

```kotlin
@Composable
private fun IdentityStep(
    name: String, onName: (String) -> Unit,
    gender: String, onGender: (String) -> Unit,
    ageBand: String, onAge: (String) -> Unit,
    onRandom: () -> Unit
) {
    SectionHeader("📜  IDENTITY")
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text("Name") },
        placeholder = { Text("Kaelis, Vara, Thorn…") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(RealmsSpacing.xs))
    OutlinedButton(
        onClick = onRandom,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Randomize Everything")
    }
    Text(
        "Roll a complete character — name, look, race, class, stats, all randomized. Lands on the final review.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(RealmsSpacing.xs))
    SectionHeader("GENDER")
    ChipRow(
        options = listOf("Male", "Female", "Non-binary", "Unspecified"),
        selected = gender,
        onSelect = onGender
    )
    SectionHeader("AGE")
    ChipRow(
        options = listOf("Young", "Adult", "Mature", "Elder"),
        selected = ageBand,
        onSelect = onAge
    )
    Text(
        "Age colors how NPCs address you — young are dismissed, elders respected or patronized.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

- [ ] **Step 5.3: Verify the file builds**

Run: `gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt
git commit -m "feat(setup): one-tap Randomize Everything button on Identity step (#20)"
```

---

## Task 6: Strengthen lore-emission directive in Prompts.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt`

The envelope schema mentions `"lore_entries": []` and `"faction_updates"` (lines 230–231) but no rule tells the LLM *when* to emit them after player actions. The pipeline already wires them into `worldLore.history`. Make the rule explicit so the lore screen actually reflects player actions.

- [ ] **Step 6.1: Locate the schema rule region**

Read `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` lines 220–250 to find the metadata schema block. Identify the next free line after the schema (typically after the closing `}` of the JSON example) where new bullet rules are added.

- [ ] **Step 6.2: Add a "When to emit lore_entries / faction_updates" block**

Insert this block immediately after the metadata schema example (right after the `"rep_deltas"` example line at 231 — paste it as a new section of explicit rules). If the surrounding text is heredoc-style multiline, add it as part of the same string literal:

```kotlin
appendLine("LORE-AFFECTING ACTIONS — when player actions change the world, RECORD them:")
appendLine("- metadata.lore_entries: [\"Sentence describing what changed.\"] — emit ONE entry per turn when ANY of:")
appendLine("  * Player kills/spares a named NPC, faction leader, or boss")
appendLine("  * Player discovers a new location, landmark, or hidden place")
appendLine("  * Player learns a secret about a faction, NPC, or the world")
appendLine("  * Player resolves or escalates a faction conflict")
appendLine("  * Player completes a major quest milestone")
appendLine("  Format: third-person, past tense, ≤120 chars, names + outcome.")
appendLine("  Example: \"Kaelis slew Lord Marcus in the Black Cathedral, ending the Black Order's ruling line.\"")
appendLine("- metadata.faction_updates: [{\"id\":\"slug\",\"field\":\"status|ruler|disposition|mood\",\"value\":\"\"}]")
appendLine("  Emit when faction status/ruler/mood changes from player action. Use canonical slug.")
appendLine("- If nothing lore-affecting happened, emit []. Do NOT pad with flavor — only real changes.")
```

(If the surrounding code is *not* `appendLine` — e.g., a raw triple-quoted string — adapt to that style verbatim. Read 5 lines above + 5 below the insertion point first to copy the formatting exactly. The content above is the directive; the string-construction style must match the file.)

- [ ] **Step 6.3: Verify the file builds**

Run: `gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt
git commit -m "feat(prompts): explicit lore_entries / faction_updates emission rules (#29)"
```

---

## Task 7: Lore-seen tracking + GameViewModel API

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`

Add `lastSeenLoreSize: Int = 0` to `GameUiState` and a `markLoreSeen()` method that snaps it to the current `worldLore.history.size`. The badge is computed in the UI; the VM only owns the cursor.

- [ ] **Step 7.1: Add the field to `GameUiState`**

Find the `data class GameUiState(...)` declaration (around line 60). Add this line in the parameter list, immediately after `worldLore: WorldLore? = null`:

```kotlin
val lastSeenLoreSize: Int = 0,
```

- [ ] **Step 7.2: Add `markLoreSeen()` to the VM**

Find a place in `GameViewModel.kt` near the other UI-mutation methods (e.g., near the cheat-toggle methods around line 304). Add:

```kotlin
fun markLoreSeen() {
    val cur = _ui.value
    val total = cur.worldLore?.history?.size ?: 0
    if (cur.lastSeenLoreSize != total) {
        _ui.value = cur.copy(lastSeenLoreSize = total)
    }
}
```

(If the VM uses `_ui.update { it.copy(...) }` style, follow that style instead. Read 20 lines around line 304 first.)

- [ ] **Step 7.3: Verify the file builds**

Run: `gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "feat(lore): track lastSeenLoreSize cursor in GameUiState (#29)"
```

---

## Task 8: Lore tab badge in JournalPager

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/components/PanelTabRow.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/JournalPager.kt`

Surface a small dot on the Lore tab when there's unseen lore. Clear it when the user opens the tab.

- [ ] **Step 8.1: Add `badge` to `PanelTab` and render a dot**

In `PanelTabRow.kt`, change the data class (line 26):

```kotlin
internal data class PanelTab(
    val label: String,
    val icon: String? = null,
    val badge: Boolean = false
)
```

In `PanelTabLabel` (line 71), append a dot after the label when `tab.badge` is true. After the inner `Text(tab.label, ...)` call inside the `Row` (around line 85–91), add:

```kotlin
if (tab.badge) {
    Spacer(Modifier.width(RealmsSpacing.xs))
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}
```

You will also need to add these imports at the top of the file (only the ones not already present):

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
```

- [ ] **Step 8.2: Wire the badge from `state` in JournalPager**

In `JournalPager.kt`, change `JournalPager` to take `vm: GameViewModel` (or a `markLoreSeen: () -> Unit` callback — pick whichever matches existing call style; read the call site first). For minimal blast radius, accept a callback:

```kotlin
@Composable
internal fun JournalPager(
    state: GameUiState,
    onAbandon: (String) -> Unit,
    onLoreSeen: () -> Unit,
    focusNpc: String? = null
) {
    val tabs = JournalTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val historyCount = state.worldLore?.history?.size ?: 0
    val hasUnseenLore = historyCount > state.lastSeenLoreSize

    // Mark lore seen when the Lore page becomes current.
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage, historyCount) {
        if (tabs[pagerState.currentPage] == JournalTab.Lore && hasUnseenLore) {
            onLoreSeen()
        }
    }

    Column(Modifier.fillMaxSize()) {
        PanelTabRow(
            tabs = tabs.map { t ->
                PanelTab(
                    label = t.label,
                    badge = t == JournalTab.Lore && hasUnseenLore
                )
            },
            selectedIndex = pagerState.currentPage,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
        )
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val mod = if (page != pagerState.currentPage) {
                Modifier.fillMaxSize().clearAndSetSemantics {}
            } else {
                Modifier.fillMaxSize()
            }
            Column(mod) {
                when (tabs[page]) {
                    JournalTab.Quests -> QuestsContent(state, onAbandon)
                    JournalTab.Npcs -> JournalContent(state, focusNpc)
                    JournalTab.Lore -> LoreContent(state)
                }
            }
        }
    }
}
```

- [ ] **Step 8.3: Update the JournalPager caller**

Search for the call site:

```bash
grep -rn "JournalPager(" app/src/main/kotlin
```

Wherever it's called, add `onLoreSeen = vm::markLoreSeen` to the parameter list. (Likely a single call site; if multiple, update all.)

- [ ] **Step 8.4: Verify the project builds**

Run: `gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/components/PanelTabRow.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/game/JournalPager.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "feat(lore): unseen-lore dot on Lore tab; clear on view (#29)"
```

(Include `GameScreen.kt` only if Step 8.3 modified it. If the caller is elsewhere, swap the path.)

---

## Task 9: Final verification — lint, assemble, deploy, debug-bridge P0+P1

**Files:** none (verification step).

The CLAUDE.md deploy gate requires runtime verification, not just compile-clean. P0+P1 from `.cursor/rules/debug-bridge-test-procedures.mdc` are the minimum; add P-tags relevant to the changed surfaces.

- [ ] **Step 9.1: Run the full unit test suite**

Run: `gradle test`
Expected: PASS (no new failures; `RandomCharacterTest` passes).

- [ ] **Step 9.2: Lint**

Run: `gradle lint`
Expected: BUILD SUCCESSFUL with no new errors.

- [ ] **Step 9.3: Assemble release**

Run: `gradle assembleRelease`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9.4: Install + start app + forward debug bridge**

Run: `gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735`
Expected: Install OK, app launches, port forwarded.

- [ ] **Step 9.5: Debug-bridge smoke**

Run P0 (boot) + P1 (basic turn) per `.cursor/rules/debug-bridge-test-procedures.mdc`. Then add per-feature checks:

- **#18 cheats:** open the cheats overlay (long-press settings or whichever entrypoint), verify it appears within ~1s, tap each tile once, verify description text fades smoothly with no visible reflow/jump, toggle Unnatural 20 → verify the Tile shows ✓ ON and the action button label flips.
- **#20 random:** start new character, on Identity step tap "Randomize Everything" → land on Confirm with all fields populated; tap BEGIN → game starts. Re-run on a fresh save to confirm different output.
- **#29 lore:** start a new game, take a turn that should trigger lore (e.g., kill an NPC mentioned in lore via combat). Open Journal → Lore tab; if a new history entry appears, the unseen-dot should clear after the first view of the Lore tab. (Until the LLM behavior shift catches, manually trigger `[LORE_ADD]`-like content via `vm.markLoreSeen()` in a debug shell to confirm the cursor logic.)

- [ ] **Step 9.6: README — only if user-visible behavior changed**

Per CLAUDE.md, README touches are required only for user-visible changes. #20 (Random button) and #29 (Lore badge) qualify; #18 is a perf fix (acceptable to skip the README).

If updating: add bullets under the "Features" or "What's new" section. Commit:

```bash
git add README.md
git commit -m "docs: note Random Character + Lore reactivity (#20, #29)"
```

---

## Self-review notes

- **Spec coverage:** Tasks 1–2 cover #18. Tasks 3–5 cover #20. Tasks 6–8 cover #29. Task 9 is the deploy gate.
- **Out of scope:** #25 (prose degradation — needs diagnose), #27 (memory of past actions — needs diagnose) require investigation sessions before plans. #24 (content-to-JSON epic), #28 (auto-update epic) each need their own multi-PR plan. These are explicitly *not* phase 3.
- **Type consistency:** `RandomizedCharacter` field names match the rememberSaveable names in `CharacterCreationScreen.kt` (`name`, `gender`, `ageBand`, `skinTone`, `hairColor`, `hairStyle`, `build`, `race`, `cls`, `baseStats`, `primaryBonus`, `secondaryBonus`). `lastSeenLoreSize: Int` is read identically in `JournalPager` and `markLoreSeen()`. `PanelTab.badge: Boolean` is a single-direction read.
- **Placeholder scan:** Two steps (2.2, 6.2, 7.2) instruct the engineer to verify exact existing call style before applying — that's research, not a placeholder; the directive includes the *content* to insert, just not assumptions about how the surrounding string is built.
- **Frequent commits:** 8 commits across 9 tasks (Task 9 may add a 9th if README changes). Each commit is independently revertable.
- **Risk:** Task 6 prompt directive is the most uncertain — LLM behavior is downstream and can't be unit-tested. If post-deploy testing shows the model still doesn't emit `lore_entries`, follow up with an even tighter directive or a few-shot example block. Don't expand scope here.
