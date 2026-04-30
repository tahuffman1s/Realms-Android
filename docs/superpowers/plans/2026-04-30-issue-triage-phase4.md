# Phase 4 — Prose & Memory Diagnostic Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Empirically identify which prompt-assembly levers cause issues #25 (prose quality degraded) and #27 (game not weighting past actions enough), so Phase 5 can implement only the changes that actually move the needle.

**Architecture:** This is an *investigation*, not a feature. We add diagnostic capture (full assembled prompt per turn), establish a deterministic-script baseline by replaying a fixed 10-action scenario via the debug bridge, then run four small isolated spikes one at a time, capturing prompts and replies for side-by-side comparison. LLM output at temperature 1.0 is non-deterministic, so each variant is replayed three times and we look at *systematic* differences (voice presence, callback density, banned-pattern frequency), not single-run diffs. After all spikes, the winners are bundled into one combined-winner replay for confirmation, then Phase 5 is written from the evidence.

The four spikes — each a minimal, reversible change:

| # | Issue | Hypothesis | Change |
|---|-------|-----------|--------|
| A | #25 | Style anchor pinned to scene-summary text is in past-tense historian voice, dragging prose toward bland reportage | Replace `styleSample` source with a hand-curated narrator-voice constant |
| B | #25 | `frequency_penalty=0.3` flattens distinctive recurring vocabulary | Drop to 0.1 |
| C | #27 | No "what the player did" block in user prompt — player decisions only live in the windowed chat history dominated by assistant envelopes | Add `RECENT PLAYER CHOICES` block listing the last 5 raw player inputs |
| D | #27 | `RECENT STORY` block is 2 narrations × 300 chars, assistant-only — too thin to anchor recency | Bump to 4 × 600 chars |

**Tech Stack:** Kotlin · Jetpack Compose · JUnit4 · Robolectric · Gradle (Kotlin DSL) · DeepSeek HTTP API · debug bridge HTTP (port 8735).

---

## Replay protocol (used by Tasks 2, 3, 4, 5, 6, 7)

Every spike uses the same fixed scenario so prompts are comparable across runs. The scenario is driven via the existing debug-bridge endpoints (`POST /macro/new-game`, `POST /command/submit`).

**Scenario:** Human Fighter named "Kael" (matches debug-bridge default), scenario seed deterministic via `/macro/new-game` defaults.

**Action script** (10 turns, executed in order via `POST /command/submit` with body `{"text":"..."}`):

```
T1: I look around the room and listen for anything unusual.
T2: I approach the closest NPC and ask their name.
T3: I tell them: "I'm looking for someone who knows about the missing caravan."
T4: I check the bar for rumors about the Black Order.
T5: I leave the tavern and head toward the market square.
T6: I try to pickpocket a wealthy-looking merchant.
T7: I draw my sword and confront the guard who saw me.
T8: I tell the guard: "It wasn't me — check the alley behind us."
T9: I follow the lead about the missing caravan toward the city gates.
T10: I ask any traveler at the gate if they've seen black-cloaked riders pass through.
```

The script deliberately mixes social, stealth, combat, and callback-bait prompts ("the Black Order" in T4, "missing caravan" in T3/T9, "black-cloaked riders" in T10) so we can see whether spike C and D improve callback density.

**Capture per run:** dump the resulting `DebugTurn` list via the new `GET /ai/debug-log` endpoint (added in Task 1). Save to `docs/superpowers/plans/phase4-evidence/<variant>-run<N>.json`. Three runs per variant.

**Comparison metrics (eyeball, not automated):**

1. **Voice signals (#25):** count distinct sardonic asides per turn (spec says ≥2-3); presence of second-person-present-tense narration; presence of voice exemplars from `Prompts.SYS` (e.g. "Between you and me", "I've seen this before", "That was almost elegant"); absence of past-tense reportage in `prose` segments.
2. **Memory signals (#27):** in T7-T10, count references back to T1-T6 events (the merchant, the guard incident, the rumors heard, the player's own claims). Count NPCs from earlier turns named again in later turns. Count contradictions (NPC who appeared in T2 mentioned with wrong relationship/location in T9).

These are *qualitative*. The plan does not attempt automated scoring — three runs per variant is sample size for eyeball pattern-matching, not statistics.

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/realmsoffate/game/data/StyleExemplarConstants.kt` — narrator-voice constant string used by Spike A.
- `app/src/debug/kotlin/com/realmsoffate/game/debug/AiDebugEndpoints.kt` — HTTP routes to retrieve the debug log as JSON for off-device replay capture.
- `docs/superpowers/plans/phase4-evidence/` — directory for captured run JSONs (gitignored — see Task 0).
- `docs/superpowers/plans/phase4-evidence/README.md` — explains the per-variant subdirectories and the comparison metrics.
- `app/src/test/kotlin/com/realmsoffate/game/data/StyleExemplarConstantsTest.kt` — verifies the constant is non-blank narrator-voice.
- `app/src/test/kotlin/com/realmsoffate/game/data/PromptUserBlocksTest.kt` — small extracted-helper tests for Spikes C and D blocks (extracted from `buildUserPrompt`).

**Modify:**
- `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` — add `systemPromptSent: String = ""` field to `DebugTurn` (default empty so old saves still deserialize).
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — pass the assembled `sys` string into `logDebugTurn`; threading through `dispatchToAi` (Task 1). Spike implementations (Tasks 3, 5, 6) edit this file as well.
- `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt` — Spike B: lower `frequency_penalty` to 0.1.
- `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — extract two pure-helper functions `renderRecentPlayerChoicesBlock(...)` and `renderRecentStoryBlock(...)` (Spikes C and D) so they're unit-testable.
- `app/src/debug/kotlin/com/realmsoffate/game/debug/DebugBridge.kt` — register `AiDebugEndpoints` (Task 1).
- `.gitignore` — add `docs/superpowers/plans/phase4-evidence/` so multi-MB run captures don't pollute the repo.

**Test (modify or add):**
- See Create list above.

---

## Task 0: Branch + evidence directory + gitignore

**Files:**
- Modify: `.gitignore`
- Create: `docs/superpowers/plans/phase4-evidence/README.md`

- [ ] **Step 0.1: Verify you're on `phase3-issue-triage` and create the diagnostic branch from main**

```bash
git fetch origin main
git checkout -b phase4-prose-memory-diagnose origin/main
```

Expected: clean tree, branch created from latest main.

- [ ] **Step 0.2: Add the evidence directory to .gitignore**

Append to `.gitignore`:

```
# Phase 4 diagnostic run captures — large JSON dumps, not for repo
docs/superpowers/plans/phase4-evidence/*.json
docs/superpowers/plans/phase4-evidence/**/*.json
```

(Track the README in the directory but ignore the JSON dumps.)

- [ ] **Step 0.3: Create the evidence directory README**

Write `docs/superpowers/plans/phase4-evidence/README.md`:

```markdown
# Phase 4 Diagnostic Evidence

Capture JSONs from each spike replay. Files in this directory are gitignored
except for this README.

## Layout

- `baseline-run{1,2,3}.json` — current main, no changes
- `spikeA-run{1,2,3}.json` — narrator-voice style exemplar
- `spikeB-run{1,2,3}.json` — frequency_penalty 0.1
- `spikeC-run{1,2,3}.json` — RECENT PLAYER CHOICES block
- `spikeD-run{1,2,3}.json` — RECENT STORY 4×600
- `combined-run{1,2,3}.json` — winning spikes layered

Each JSON is the response from `GET /ai/debug-log` after replaying the 10-turn
script in the parent plan.

## Reading the captures

The `debugLog` array contains one entry per turn with `userPromptSent`,
`systemPromptSent` (added in Task 1), and `rawAiResponse`. Compare across
variants by eyeball — see the "Comparison metrics" section in
`../2026-04-30-issue-triage-phase4.md`.
```

- [ ] **Step 0.4: Commit**

```bash
git add .gitignore docs/superpowers/plans/phase4-evidence/README.md
git commit -m "chore(phase4): branch + evidence directory for prompt diagnostics"
```

---

## Task 1: Capture full system prompt per turn + JSON debug-log endpoint

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`
- Create: `app/src/debug/kotlin/com/realmsoffate/game/debug/AiDebugEndpoints.kt`
- Modify: `app/src/debug/kotlin/com/realmsoffate/game/debug/DebugBridge.kt`

The existing `DebugTurn` records `userPromptSent` but not the assembled system message (`Prompts.DS_PREFIX + sys + StyleExemplar.block(...)`). Without the system message we can't see what changed across spikes A and B. Also expose `_debugLog` over HTTP so replay scripts can pull captures off-device without needing a debug-dump button tap.

- [ ] **Step 1.1: Add `systemPromptSent` to `DebugTurn`**

In `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt`, modify the `DebugTurn` data class (line 329):

```kotlin
@Serializable
data class DebugTurn(
    val turn: Int,
    val playerAction: String,
    val classifiedSkill: String?,
    val diceRoll: Int,
    val userPromptSent: String,
    val rawAiResponse: String,
    val parsedScene: String,
    val parsedNarration: String,
    val parsedTags: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Phase 4 diagnostic: full assembled system message (DS_PREFIX + sys + style block).
     *  Default "" so v1/v2/v3 saves keep deserializing — old DebugTurns don't have this. */
    val systemPromptSent: String = ""
)
```

- [ ] **Step 1.2: Thread the assembled system string into `logDebugTurn`**

In `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`:

(a) Update the `logDebugTurn` signature (line 356) and the `DebugTurn(...)` constructor call (around line 397):

```kotlin
private fun logDebugTurn(
    turn: Int, action: String, skill: String?, roll: Int,
    prompt: String, raw: String, parsed: com.realmsoffate.game.data.ParsedReply,
    systemPrompt: String = ""
) {
    // ... existing tags assembly unchanged ...
    _debugLog.add(DebugTurn(
        turn = turn, playerAction = action, classifiedSkill = skill,
        diceRoll = roll, userPromptSent = prompt, rawAiResponse = raw,
        parsedScene = "${parsed.scene}|${parsed.sceneDesc}",
        parsedNarration = parsed.segments.joinToString(" | ") { /* ... unchanged ... */ }.take(500),
        parsedTags = tags,
        systemPromptSent = systemPrompt
    ))
    if (_debugLog.size > 50) _debugLog.removeAt(0)
}
```

(b) Update the call site at line 979 (inside `dispatchToAi`). The full assembled system is built at line 947:

```kotlin
val sessionSystem = sessionSystemFor(state, char)
val sys = Prompts.SYS + "\n\n" + sessionSystem +
    "\n\n" + com.realmsoffate.game.data.CANONICAL_FACTS_DIRECTIVE
```

…and the per-attempt actually-sent system is `attemptSys` (line 962). The `attemptSys` for the attempt that produced the parsed reply is what we want. Track it through the loop:

Replace the existing loop at lines 961-978 with a version that captures the winning attempt's system:

```kotlin
var raw = ""
var parsed: ParsedReply = TagParser.parse("", state.turns + 1)  // placeholder INVALID
var winningAttemptSys: String = sys  // captured for diagnostic
val invalidHint = "\n\nPREVIOUS RESPONSE WAS INVALID JSON. Emit ONE valid JSON object with keys scene, segments, choices (exactly 4), metadata. Escape all internal double quotes as \\\". No nested objects inside array elements unless the schema specifies one. ASCII straight quotes only."
for (attempt in 1..3) {
    val attemptSys = if (attempt == 1) sys else sys + invalidHint
    raw = try {
        ai.generate(
            provider = _provider.value,
            apiKey = _apiKey.value,
            systemPrompt = attemptSys,
            history = nh,
            styleSample = state.sceneSummaries.firstOrNull()?.summary
        )
    } catch (t: Throwable) {
        _ui.value = _ui.value.copy(isGenerating = false, error = "Network error: ${t.message}")
        return@launch
    }
    parsed = TagParser.parse(raw, state.turns + 1)
    winningAttemptSys = attemptSys
    if (parsed.source == ParseSource.JSON) break
    android.util.Log.w("GameViewModel", "envelope parse failed on attempt $attempt/3; retrying with correction hint")
}
// Append the StyleExemplar block too — AiRepository.callDeepSeek concatenates it
// onto the system message but doesn't return what was sent. Reproduce that here so
// the captured system prompt matches what hit the wire.
val styleSample = state.sceneSummaries.firstOrNull()?.summary
val capturedSystem = com.realmsoffate.game.data.Prompts.DS_PREFIX + winningAttemptSys +
    com.realmsoffate.game.data.StyleExemplar.block(styleSample)
logDebugTurn(state.turns + 1, action, skill, roll, userPrompt, raw, parsed, capturedSystem)
```

(Old line 979's call to `logDebugTurn` is replaced by the new last line above. The change is: capture `winningAttemptSys`, reconstruct the wire-identical system, pass it to the logger.)

- [ ] **Step 1.3: Create the AI-debug endpoint**

Create `app/src/debug/kotlin/com/realmsoffate/game/debug/AiDebugEndpoints.kt`:

```kotlin
package com.realmsoffate.game.debug

import com.realmsoffate.game.data.DebugTurn
import com.realmsoffate.game.game.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/**
 * Phase 4 diagnostic: exposes the in-memory DebugTurn log over HTTP so replay
 * scripts can capture full prompt + response payloads off-device.
 *
 * Routes:
 *   GET /ai/debug-log         — full log as JSON list of DebugTurn
 *   GET /ai/debug-log?last=N  — last N entries only (default: all 50)
 *
 * The log is capped at 50 entries by GameViewModel; we don't add gzip or paging
 * because 50×~5KB envelopes is well under any reasonable HTTP body limit.
 */
class AiDebugEndpoints(private val vm: GameViewModel) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    fun register() {
        DebugServer.route("GET", "/ai/debug-log") { req ->
            val log = withContext(Dispatchers.Main) { vm.snapshotDebugLog() }
            val nParam = req.query["last"]?.toIntOrNull()
            val sliced = if (nParam != null && nParam > 0) log.takeLast(nParam) else log
            val body = json.encodeToString(ListSerializer(DebugTurn.serializer()), sliced)
            HttpResponse.json(body)
        }
    }
}
```

(Note: `DebugServer.route` and `HttpResponse.json` are existing helpers used by the other endpoint files in the same directory — `StateEndpoints.kt`, `MacroEndpoints.kt`. If your local convention differs, mirror what `StateEndpoints.kt` does. Read 5 lines of that file first to confirm the helper shape.)

- [ ] **Step 1.4: Add `snapshotDebugLog()` to GameViewModel**

In `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`, near the `_debugLog` declaration (around line 354), add a public read accessor:

```kotlin
/** Phase 4 diagnostic: returns a copy of the current debug log for off-device capture. */
fun snapshotDebugLog(): List<com.realmsoffate.game.data.DebugTurn> = _debugLog.toList()
```

- [ ] **Step 1.5: Register the new endpoint in DebugBridge**

In `app/src/debug/kotlin/com/realmsoffate/game/debug/DebugBridge.kt`, find where the other `*Endpoints(vm).register()` calls happen and add:

```kotlin
AiDebugEndpoints(vm).register()
```

Read the file first (it's small) to confirm the registration call style — match the existing pattern exactly.

- [ ] **Step 1.6: Verify the project builds**

Run: `gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 1.7: Deploy and smoke-test the new endpoint**

Run: `gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735`

Then start a game (use the app UI or `curl -X POST http://localhost:8735/macro/new-game`), submit one action, and check the endpoint:

```bash
curl -s http://localhost:8735/ai/debug-log | head -c 2000
```

Expected: a JSON array with at least one object containing `systemPromptSent` populated with `DS_PREFIX` + SYS + session system + (optionally) STYLE block — many KB long.

- [ ] **Step 1.8: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Models.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/debug/kotlin/com/realmsoffate/game/debug/AiDebugEndpoints.kt \
        app/src/debug/kotlin/com/realmsoffate/game/debug/DebugBridge.kt
git commit -m "feat(debug): capture full system prompt + GET /ai/debug-log (phase 4)"
```

---

## Task 2: Establish baseline — replay 10-turn script on current main, capture × 3

**Files:** none modified — this is a recording session.

The branch already contains Task 1 instrumentation. Spikes A-D are not yet applied, so this run captures the *current main behavior* (instrumented). We replay three times because temperature 1.0 yields run-to-run variance.

- [ ] **Step 2.1: Reset to a clean game**

Run: `curl -X POST http://localhost:8735/macro/new-game -d '{}' -H 'Content-Type: application/json'`
Expected: HTTP 200, fresh save.

- [ ] **Step 2.2: Replay the 10-turn action script**

Run this fish-shell script (the working shell in this environment):

```fish
set actions \
    "I look around the room and listen for anything unusual." \
    "I approach the closest NPC and ask their name." \
    "I tell them: \"I'm looking for someone who knows about the missing caravan.\"" \
    "I check the bar for rumors about the Black Order." \
    "I leave the tavern and head toward the market square." \
    "I try to pickpocket a wealthy-looking merchant." \
    "I draw my sword and confront the guard who saw me." \
    "I tell the guard: \"It wasn't me — check the alley behind us.\"" \
    "I follow the lead about the missing caravan toward the city gates." \
    "I ask any traveler at the gate if they've seen black-cloaked riders pass through."

for action in $actions
    curl -s -X POST http://localhost:8735/command/submit \
        -H 'Content-Type: application/json' \
        -d (jq -n --arg t "$action" '{text:$t}')
    # Wait for the LLM round-trip (≈3-8s). Poll /state for isGenerating=false.
    while test (curl -s http://localhost:8735/state | jq -r '.isGenerating') = "true"
        sleep 1
    end
    sleep 1
end
```

Expected: 10 turns appear in the chat. If the bridge times out or returns 5xx, abort and re-test the connection — DO NOT capture a partial run.

- [ ] **Step 2.3: Capture the run**

```bash
curl -s 'http://localhost:8735/ai/debug-log?last=10' | jq '.' \
    > docs/superpowers/plans/phase4-evidence/baseline-run1.json
```

Expected: file ~50-200KB, 10 entries, each with non-empty `systemPromptSent`, `userPromptSent`, `rawAiResponse`.

- [ ] **Step 2.4: Repeat steps 2.1-2.3 twice more**

Save to `baseline-run2.json` and `baseline-run3.json`. Each run starts from a fresh `/macro/new-game` so cache and history don't bleed across runs.

- [ ] **Step 2.5: Eyeball the baseline against the comparison metrics**

Open the three baseline JSONs. Skim the `rawAiResponse.segments[?(@.kind=="aside")]` arrays in each turn. Count:

- Asides per turn (target ≥2 per the spec).
- Fraction of turns with at least one *specific* sardonic aside (a unique reaction to what just happened, not a generic "the world is dangerous" line).
- Turns 7-10: how many reference back to T1-T6 events (caravan, Black Order, the merchant, the guard)?
- Any prose segments that read past-tense / historian-style?

Write your eyeball notes to `docs/superpowers/plans/phase4-evidence/baseline-notes.md` (untracked, see .gitignore — but you can keep it locally for the synthesis step). This is the reference everything else gets compared against.

- [ ] **Step 2.6: No commit needed**

Baseline data is gitignored. The instrumentation commit from Task 1 is what's tracked.

---

## Task 3: Spike A — narrator-voice style exemplar

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/StyleExemplarConstants.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/StyleExemplarConstantsTest.kt`

The current `styleSample` passed to `ai.generate(...)` is `state.sceneSummaries.firstOrNull()?.summary` — a past-tense historian compression produced by `Prompts.SCENE_SUMMARY_SYS`. The `StyleExemplar.block` header reads "match this voice — tone, cadence, word choice", so we are explicitly anchoring the live narrator voice to historian voice. Spike A swaps in a hand-curated 3-sentence exemplar that *is* the BG3-narrator voice the SYS prompt is asking for.

- [ ] **Step 3.1: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/StyleExemplarConstantsTest.kt`:

```kotlin
package com.realmsoffate.game.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleExemplarConstantsTest {

    @Test
    fun `narrator exemplar is non-blank`() {
        assertTrue(StyleExemplarConstants.NARRATOR_VOICE.isNotBlank())
    }

    @Test
    fun `narrator exemplar uses second-person present tense, not past-tense reportage`() {
        val text = StyleExemplarConstants.NARRATOR_VOICE.lowercase()
        // Second-person is the BG3 narrator's calling card.
        assertTrue("should address the player", text.contains(" you "))
        // Past-tense reportage signals (the historian voice we are running from).
        assertFalse("avoid past-tense -ed verbs typical of scene summaries",
            Regex("\\b(walked|killed|told|asked|leaned|nodded|said)\\b").containsMatchIn(text))
    }

    @Test
    fun `narrator exemplar contains at least 3 sentences for StyleExemplar render`() {
        val sentences = StyleExemplarConstants.NARRATOR_VOICE
            .split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        assertTrue("StyleExemplar.render takes the first 3 sentences; need ≥3",
            sentences.size >= 3)
    }
}
```

- [ ] **Step 3.2: Run the test to confirm it fails**

Run: `gradle :app:testDebugUnitTest --tests com.realmsoffate.game.data.StyleExemplarConstantsTest`
Expected: FAIL — `StyleExemplarConstants` not found.

- [ ] **Step 3.3: Implement the constant**

Create `app/src/main/kotlin/com/realmsoffate/game/data/StyleExemplarConstants.kt`:

```kotlin
package com.realmsoffate.game.data

/**
 * Phase 4 diagnostic — Spike A.
 *
 * Replaces the scene-summary-derived [StyleExemplar] sample (which was
 * historian-voice past-tense) with a hand-curated exemplar in the actual
 * narrator voice that [Prompts.SYS] asks the model to embody:
 * second-person, present-tense, sardonic, BG3-narrator.
 *
 * The string is fed verbatim through [StyleExemplar.render], which keeps the
 * first 3 sentence-terminated sentences. Keep this 3-4 sentences, voice-rich.
 */
object StyleExemplarConstants {
    const val NARRATOR_VOICE: String =
        "You push through the tavern door and the rain follows you in. " +
        "The barkeep looks up from her cloth like she's seen this exact entrance before — and she has, twice this week. " +
        "Between you and me, the careful ones don't come in dripping wet. " +
        "But here you are, and here I am, and this is the part where it gets interesting."
}
```

- [ ] **Step 3.4: Run the test to confirm it passes**

Run: `gradle :app:testDebugUnitTest --tests com.realmsoffate.game.data.StyleExemplarConstantsTest`
Expected: PASS (3 tests).

- [ ] **Step 3.5: Wire the constant in GameViewModel**

In `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`, find the `ai.generate(...)` call at line 964 inside `dispatchToAi` and replace the `styleSample` argument:

```kotlin
raw = try {
    ai.generate(
        provider = _provider.value,
        apiKey = _apiKey.value,
        systemPrompt = attemptSys,
        styleSample = com.realmsoffate.game.data.StyleExemplarConstants.NARRATOR_VOICE,
        history = nh
    )
} catch (t: Throwable) { /* unchanged */ }
```

Also update the captured-system reconstruction added in Task 1.2 so it matches what's now sent:

```kotlin
val capturedSystem = com.realmsoffate.game.data.Prompts.DS_PREFIX + winningAttemptSys +
    com.realmsoffate.game.data.StyleExemplar.block(
        com.realmsoffate.game.data.StyleExemplarConstants.NARRATOR_VOICE
    )
```

- [ ] **Step 3.6: Verify the project builds**

Run: `gradle :app:compileDebugKotlin && gradle :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no regressions in existing tests).

- [ ] **Step 3.7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/StyleExemplarConstants.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/StyleExemplarConstantsTest.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "spike(prose): pin StyleExemplar to narrator-voice constant (#25, phase 4 spike A)"
```

- [ ] **Step 3.8: Deploy and replay**

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Run the 10-turn replay protocol from Task 2 three times, saving to `phase4-evidence/spikeA-run{1,2,3}.json`. Eyeball notes go in `spikeA-notes.md` (untracked).

- [ ] **Step 3.9: Eyeball compare A vs baseline**

Open `baseline-run{1,2,3}.json` and `spikeA-run{1,2,3}.json` side by side. For each variant:

- Aside count per turn (A should match or exceed baseline).
- Voice fingerprint: does the narrator sound more like the SYS exemplars ("Between you and me…", "I've seen this before…")? Or still bland?
- Past-tense leak in `prose` segments: A should reduce or eliminate these.

Record your verdict in `spikeA-notes.md`: **moves the needle / no effect / regresses**. This verdict feeds Task 8.

---

## Task 4: Spike B — frequency_penalty 0.3 → 0.1

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt`

`frequency_penalty=0.3` actively discourages repeated tokens — flattening distinctive recurring vocabulary across a long session. Drop to 0.1 (still mild deduplication) and see whether the voice gets richer.

- [ ] **Step 4.1: Make the penalty a tunable constant**

In `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt`, near the top of the `companion object` (around line 44), add:

```kotlin
/** Phase 4 spike B — was 0.3, testing 0.1 to see whether reduced repetition
 *  pressure restores distinctive recurring narrator vocabulary. */
const val FREQUENCY_PENALTY: Double = 0.1
```

- [ ] **Step 4.2: Use the constant in the request body**

In `callDeepSeek` (around line 155), replace the literal 0.3:

```kotlin
put("frequency_penalty", FREQUENCY_PENALTY)
```

(No tests added — this is a single numeric parameter; the captured system prompt + raw response in the debug log are the verification.)

- [ ] **Step 4.3: Verify the project builds**

Run: `gradle :app:compileDebugKotlin && gradle :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no regressions.

- [ ] **Step 4.4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt
git commit -m "spike(prose): drop frequency_penalty 0.3 -> 0.1 (#25, phase 4 spike B)"
```

- [ ] **Step 4.5: Deploy and replay**

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Run the 10-turn replay × 3 → `phase4-evidence/spikeB-run{1,2,3}.json`.

- [ ] **Step 4.6: Eyeball compare B vs baseline**

Compare against baseline only — *not* against spike A — because each spike is being measured independently. (Combined-winners run is Task 7.) Verdict in `spikeB-notes.md`.

---

## Task 5: Spike C — RECENT PLAYER CHOICES block

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/PromptUserBlocksTest.kt`

The model has rich blocks for *world state* (NPCs, factions, lore, locations) but no curated block for *what the player has done*. Player decisions live only in raw history (windowed at 8000 tokens, ~3-5 full turns). Add an explicit ledger.

- [ ] **Step 5.1: Write the failing test for the new block helper**

Create `app/src/test/kotlin/com/realmsoffate/game/data/PromptUserBlocksTest.kt`:

```kotlin
package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptUserBlocksTest {

    @Test
    fun `recent choices block returns empty for no actions`() {
        assertEquals("", renderRecentPlayerChoicesBlock(emptyList()))
    }

    @Test
    fun `recent choices block lists last 5 actions, oldest first`() {
        val actions = listOf("a1", "a2", "a3", "a4", "a5", "a6")
        val out = renderRecentPlayerChoicesBlock(actions)
        assertTrue("block should contain header", out.contains("RECENT PLAYER CHOICES"))
        // a1 is dropped; a2..a6 kept, oldest first
        val a2Pos = out.indexOf("a2")
        val a6Pos = out.indexOf("a6")
        assertTrue("a2 should appear", a2Pos >= 0)
        assertTrue("a6 should appear", a6Pos >= 0)
        assertTrue("a2 should appear before a6 (chronological)", a2Pos < a6Pos)
        assertTrue("a1 should NOT appear (cap is 5)", !out.contains("a1"))
    }

    @Test
    fun `recent choices block truncates each entry to 200 chars with ellipsis`() {
        val long = "x".repeat(300)
        val out = renderRecentPlayerChoicesBlock(listOf(long))
        // Should contain a 200-char x-run + an ellipsis marker.
        assertTrue(out.contains("x".repeat(200)))
        assertTrue(out.contains("…") || out.contains("..."))
    }

    @Test
    fun `recent story block uses 4-message 600-char window`() {
        val msgs = listOf("m1".padEnd(700, 'a'), "m2".padEnd(700, 'b'),
                          "m3".padEnd(700, 'c'), "m4".padEnd(700, 'd'),
                          "m5".padEnd(700, 'e'))
        val out = renderRecentStoryBlock(msgs)
        // Last 4: m2..m5
        assertTrue(!out.contains("m1"))
        assertTrue(out.contains("m2"))
        assertTrue(out.contains("m5"))
        // Each truncated to 600 chars (so 'a'.repeat(700) shows 600 a's, not 700).
        // Use a clear unique marker per message ("m2"..."m5") for ordering and presence checks.
    }
}
```

- [ ] **Step 5.2: Run the test to confirm it fails**

Run: `gradle :app:testDebugUnitTest --tests com.realmsoffate.game.data.PromptUserBlocksTest`
Expected: FAIL — `renderRecentPlayerChoicesBlock` and `renderRecentStoryBlock` not defined.

- [ ] **Step 5.3: Implement both helpers in Prompts.kt**

Append to `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` (after the `BUDGET_*` constants at the bottom):

```kotlin
/**
 * Phase 4 spike C — RECENT PLAYER CHOICES block.
 *
 * The user prompt has rich blocks for world state but nothing for what the
 * player did recently. Player text is only in the raw chat history, which is
 * windowed at 8000 tokens — ~3-5 full turns — and is dominated by assistant
 * envelope JSON. This block surfaces the last 5 raw player inputs in a
 * dedicated section, oldest first (chronological), each truncated to 200
 * chars to keep the budget tight.
 *
 * Returns "" for an empty list so callers can unconditionally append.
 */
fun renderRecentPlayerChoicesBlock(actions: List<String>): String {
    val recent = actions.takeLast(5)
    if (recent.isEmpty()) return ""
    return buildString {
        append("\n\nRECENT PLAYER CHOICES (the player's actual recent decisions — honor them, callback to them):\n")
        recent.forEach { a ->
            val trimmed = if (a.length > 200) a.take(200) + "…" else a
            append("- ").append(trimmed).append('\n')
        }
    }
}

/**
 * Phase 4 spike D — RECENT STORY block resize (was takeLast(2) × 300 chars).
 * Bumped to 4 narrations × 600 chars to widen recency anchoring without
 * blowing the per-turn token budget.
 *
 * Returns "" for empty input. Joins narrations newest-last with a "---"
 * separator so the model sees clear scene boundaries.
 */
fun renderRecentStoryBlock(narrations: List<String>): String {
    val recent = narrations.takeLast(4)
    if (recent.isEmpty()) return ""
    val joined = recent.joinToString("\n---\n") { it.take(600) }
    return "\n\nRECENT STORY (continue from here, do not reset or contradict):\n$joined"
}
```

- [ ] **Step 5.4: Run the tests to confirm they pass**

Run: `gradle :app:testDebugUnitTest --tests com.realmsoffate.game.data.PromptUserBlocksTest`
Expected: PASS (4 tests).

- [ ] **Step 5.5: Wire the choices block into `buildUserPrompt`**

In `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`, inside `buildUserPrompt` (around line 1278, just after the `if (recentNarration.isNotBlank())` block), add:

```kotlin
val recentPlayerActions = s.history
    .filter { it.role == "user" }
    .map { it.content }
append(com.realmsoffate.game.data.renderRecentPlayerChoicesBlock(recentPlayerActions))
```

(Spike D will rewire `recentNarration` to use `renderRecentStoryBlock` in Task 6 — for now, leave the existing `recentNarration` block alone so spike C is isolated.)

- [ ] **Step 5.6: Verify the project builds and tests pass**

Run: `gradle :app:compileDebugKotlin && gradle :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5.7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/PromptUserBlocksTest.kt
git commit -m "spike(memory): RECENT PLAYER CHOICES block (#27, phase 4 spike C)"
```

- [ ] **Step 5.8: Revert spikes A and B before deploying spike C alone**

Each spike is measured against baseline independently. Revert A and B locally (do not push), keep C.

```bash
# Identify the spike A and B commits, revert them (creating revert commits).
# The revert commits will be cleaned up at the end of the phase.
git revert --no-edit HEAD~1   # spike B
git revert --no-edit HEAD~2   # spike A (now HEAD~2 because the B-revert is between)
```

Verify with `git log --oneline -6` — the working tree should now contain only spike C plus Task 1 instrumentation, with revert commits showing the A/B rollback.

- [ ] **Step 5.9: Deploy and replay**

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Run the 10-turn replay × 3 → `phase4-evidence/spikeC-run{1,2,3}.json`.

- [ ] **Step 5.10: Eyeball compare C vs baseline**

Specifically check turns 7-10 in spikeC vs baseline. Does spike C produce more callbacks to T1-T6 events (the merchant, the guard, the caravan rumors)? Verdict in `spikeC-notes.md`.

---

## Task 6: Spike D — RECENT STORY 4 × 600 chars

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`

The helper `renderRecentStoryBlock(...)` was added in Task 5.3 with tests. Spike D wires it into `buildUserPrompt`, replacing the inline 2 × 300 implementation.

- [ ] **Step 6.1: Replace the inline RECENT STORY assembly**

In `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`, find this block (around lines 1232-1235, 1278-1280):

```kotlin
val recentNarration = s.messages
    .filterIsInstance<DisplayMessage.Narration>()
    .takeLast(2)
    .joinToString("\n---\n") { it.text.take(300) }
```

…and the corresponding append:

```kotlin
if (recentNarration.isNotBlank()) {
    append("\n\nRECENT STORY (continue from here, do not reset or contradict):\n$recentNarration")
}
```

Replace BOTH with a single call to the new helper, *and* keep the `recentNarration` variable populated for the keyword-extraction tokens at line 1239-1240 (the keyword extraction also uses `recentNarration`). To preserve that, build the keyword input independently:

```kotlin
// Keyword input — use the last 2 narrations × 300 chars (unchanged from baseline)
// because expanding this would change retrieval scoring, which is out of scope for spike D.
val recentNarrationForKeywords = s.messages
    .filterIsInstance<DisplayMessage.Narration>()
    .takeLast(2)
    .joinToString("\n---\n") { it.text.take(300) }

// Prompt block — use the wider 4 × 600 window (spike D).
val narrationsForPrompt = s.messages
    .filterIsInstance<DisplayMessage.Narration>()
    .map { it.text }
```

Then later, where the old `if (recentNarration.isNotBlank()) { append(...) }` was, write:

```kotlin
append(com.realmsoffate.game.data.renderRecentStoryBlock(narrationsForPrompt))
```

And update the `tokens` line to use `recentNarrationForKeywords`:

```kotlin
val tokens = (com.realmsoffate.game.util.PromptKeywords.extract(action) +
    com.realmsoffate.game.util.PromptKeywords.extract(recentNarrationForKeywords)).distinct()
```

(The point of the split: spike D should change *only* what the model sees, not what the keyword retriever sees — so retrieval scoring remains constant across spikes and we're not confounding two changes.)

- [ ] **Step 6.2: Verify the project builds and tests pass**

Run: `gradle :app:compileDebugKotlin && gradle :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.3: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "spike(memory): RECENT STORY 4x600 chars (#27, phase 4 spike D)"
```

- [ ] **Step 6.4: Revert spike C, leaving D alone**

```bash
git revert --no-edit HEAD~1   # spike C
```

Now the working tree contains only spike D + Task 1 instrumentation.

- [ ] **Step 6.5: Deploy and replay**

```bash
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Run the 10-turn replay × 3 → `phase4-evidence/spikeD-run{1,2,3}.json`.

- [ ] **Step 6.6: Eyeball compare D vs baseline**

Check the same memory metrics from Task 5.10. Verdict in `spikeD-notes.md`.

---

## Task 7: Combined-winners replay

**Files:** depends on which spikes won — re-apply those changes.

After all four spikes have been measured independently, layer the *winners* (those marked "moves the needle" in their notes) into a single build and verify they compose well — sometimes two changes that work alone confound each other.

- [ ] **Step 7.1: Identify winners from the eyeball notes**

Open `spikeA-notes.md`, `spikeB-notes.md`, `spikeC-notes.md`, `spikeD-notes.md`. List the spikes marked "moves the needle". If zero spikes won, skip Task 7 entirely and proceed to Task 8 — the negative result is itself useful.

If one spike won, skip Task 7 (no compositional risk to test).

If 2+ spikes won, proceed.

- [ ] **Step 7.2: Cherry-pick the winning spike commits onto a fresh sub-branch**

```bash
git checkout phase4-prose-memory-diagnose
git checkout -b phase4-combined-winners

# Identify the original spike commit hashes (NOT the revert commits) via:
git log --oneline --all | grep "phase 4 spike"

# Cherry-pick the winners. Order: A, B, C, D (whichever won).
git cherry-pick <sha-of-spike-X>
git cherry-pick <sha-of-spike-Y>
# ... etc
```

Resolve any merge conflicts by accepting both spikes' changes (they touch different files / different sections).

- [ ] **Step 7.3: Build, test, deploy, replay × 3**

```bash
gradle :app:compileDebugKotlin && gradle :app:testDebugUnitTest
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Run the 10-turn replay × 3 → `phase4-evidence/combined-run{1,2,3}.json`.

- [ ] **Step 7.4: Eyeball compare combined vs baseline AND vs each individual winner**

Check whether the combined run is at least as good as each individual winner across both metric families. If combined regresses on any metric a single winner improved, we have *interference* — note which pair conflicts. Verdict in `combined-notes.md`.

---

## Task 8: Synthesize → write Phase 5 plan, restore branch state

**Files:**
- Create: `docs/superpowers/plans/2026-04-30-issue-triage-phase5.md` (or whatever the next phase is named — use today's date)

- [ ] **Step 8.1: Synthesize findings**

Open all five `*-notes.md` files. Write a one-paragraph summary per spike: did it move the needle, on which metric, by how much (subjective scale: clear / marginal / none / regression), and any side effects observed.

- [ ] **Step 8.2: Draft the Phase 5 plan**

Use the `superpowers:writing-plans` skill to draft a Phase 5 implementation plan that:

- Implements *only* the spikes that won (clear or strong marginal effect).
- Promotes the winning changes from "spike" status to permanent: e.g., for spike A, rename `StyleExemplarConstants.NARRATOR_VOICE` to a stable name and remove the "Phase 4 diagnostic" comment header; for spike C, decide whether the 5-action / 200-char window needs tuning per evidence.
- Preserves Task 1 instrumentation (the `systemPromptSent` field + `/ai/debug-log` endpoint) — those stay regardless of which spikes won; they're useful long-term diagnostics.
- Calls out any spike that *regressed* with a note on why (so future-you doesn't re-attempt it).
- Calls out any compositional interference observed in Task 7.

Save to `docs/superpowers/plans/<today>-issue-triage-phase5.md`.

- [ ] **Step 8.3: Restore the branch to a single clean state for PR**

The diagnostic branch currently has Task 1 + a chain of revert commits + spike C reverted + spike D applied. That's not a sensible PR target. Squash to two clean commits:

1. The Task 1 instrumentation (worth keeping on main regardless).
2. *Nothing else* — the spikes themselves are deferred to Phase 5 implementation.

```bash
# Reset to main, then re-apply just the Task 1 instrumentation commit.
git checkout phase4-prose-memory-diagnose
git reset --hard origin/main
git cherry-pick <sha-of-task-1-instrumentation-commit>
# Add the Phase 5 plan on top.
git add docs/superpowers/plans/<today>-issue-triage-phase5.md
git commit -m "docs(plans): add Phase 5 prose & memory implementation plan"
```

- [ ] **Step 8.4: Push and open the PR**

```bash
git push -u origin phase4-prose-memory-diagnose
gh pr create --title "Phase 4: prose & memory diagnostics + Phase 5 plan" \
  --body "$(cat <<'EOF'
## Summary
- Adds full system-prompt capture in `DebugTurn` and a `GET /ai/debug-log` debug-bridge endpoint for off-device prompt forensics.
- Ran four isolated diagnostic spikes against issues #25 (prose degradation) and #27 (past actions not weighted enough); evidence is gitignored locally.
- Phase 5 plan documents which levers won and outlines the implementation work.

## Test plan
- [ ] `gradle test` passes
- [ ] `gradle installDebug` and verify `curl http://localhost:8735/ai/debug-log` returns populated entries after a turn
- [ ] Phase 5 plan reviewed for correctness against the spike notes

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

(Note: the spike implementations themselves are not on this PR — they are tracked in the Phase 5 plan and will land in subsequent PRs once approved. This PR keeps only the instrumentation and the plan.)

---

## Self-review notes

- **Spec coverage:** The Phase 4 ask is "diagnose #25 and #27 — gather evidence before writing a Phase 5 plan." Tasks 1-7 cover the four hypotheses identified in the diagnostic conversation (StyleExemplar voice mismatch, frequency_penalty, missing player-decisions block, recent-story window). Task 8 produces the Phase 5 plan. Each spike is isolated (its own commit, reverted before the next spike runs) so we can attribute behavioral changes to specific levers.
- **Out of scope (deferred to Phase 5+):** Spike implementations themselves; ContradictionQueue → prompt feedback (a Phase 5+ candidate but not a Phase 4 spike — too large to test as a 1-line change); time-based scene-summary fallback; arc-summary keyword-retrieval expansion; Prompts.SYS prose-vs-rules rebalance from commit f2baef8 (a separate Phase 5+ effort).
- **Determinism caveat:** all spike comparisons rely on three-run eyeball pattern matching at temperature 1.0. The plan does not pretend this is rigorous statistics — it is sample-of-three pattern detection. If a spike's effect is subtle, three runs may be inconclusive; in that case, the verdict in the spike-notes file should say "marginal/inconclusive" and Phase 5 deprioritizes that lever.
- **Type consistency:** `renderRecentPlayerChoicesBlock(List<String>)` and `renderRecentStoryBlock(List<String>)` are declared in Prompts.kt (Task 5.3), tested in `PromptUserBlocksTest` (Task 5.1), and called from `GameViewModel.buildUserPrompt` (Tasks 5.5 and 6.1). `StyleExemplarConstants.NARRATOR_VOICE` is declared in Task 3.3, tested in 3.1, used in 3.5. `DebugTurn.systemPromptSent` is added in Task 1.1, populated in Task 1.2, exposed in Task 1.3 via `vm.snapshotDebugLog()`.
- **Frequent commits:** 1 (instrumentation) + 4 (one per spike) + ≤3 (revert commits) + 1 (combined winner) + 1 (final squash) + 1 (Phase 5 plan) ≈ 7-11 commits across 8 tasks. Each spike is independently revertable.
- **Risk:** the biggest risk is *running* the replay protocol — DeepSeek API costs scale with the number of runs. 10 turns × 3 runs × 6 variants (baseline + 4 spikes + combined) = 180 API calls + ~50 baseline-validation manual turns. At ~2k prompt tokens + ~1k completion tokens per turn, total cost is well under $1 USD on DeepSeek pricing — manageable but worth doing in one focused session rather than spread over weeks.

---

## Decisions

The empirical replay protocol (Tasks 2 / 3.8-3.9 / 4.5-4.6 / 5.9-5.10 / 6.5-6.6 / 7) was **skipped by user decision**. Spikes were approved on diagnostic confidence rather than three-run eyeball pattern matching.

### Approved (bundled into the same PR as instrumentation)

- **Spike A — narrator-voice `StyleExemplarConstants.NARRATOR_VOICE`.** Mechanically wrong: the slot labeled "match this voice" was being filled with a past-tense historian compression, the opposite of the second-person present-tense narrator the SYS prompt asks for. Promoted from "diagnostic spike" to permanent code; doc comments rewritten to drop the experiment framing.
- **Spike C — `RECENT PLAYER CHOICES` user-prompt block.** Structural gap: rich blocks existed for world state, NPCs, factions, lore — none for player decisions. Player text was buried in raw chat history dominated by assistant envelope JSON. Adding an explicit ledger of the last 5 player inputs is a well-understood prompt-engineering pattern with low risk.
- **Spike D — `RECENT STORY` 2×300 → 4×600.** Recency anchor was ~100 words for a long-form RPG. Widening costs ~1.8KB of user-prompt tokens per turn — well within budget — and pairs naturally with Spike C (decisions ledger + recent narrative both anchored at the front of the action context).

### Deferred — Spike B (frequency_penalty 0.3 → 0.1)

DeepSeek's documented creative-writing sweet spot is `frequency_penalty=0.3, presence_penalty=0.1` (referenced in `AiRepository.kt:25-30`). Lowering the frequency penalty has a real risk of *increasing* repetition, not decreasing it — and repetition is itself a prose-quality regression. Without empirical A/B data, the change could move the needle in either direction. Re-enter this experiment if Spikes A/C/D land and prose still feels flat — at that point the `phase4-spike-b` branch is still available to cherry-pick and run a focused replay against.

### Out of scope, parked for follow-up phases

- **ContradictionQueue → prompt feedback.** `ContradictionQueue.checkArc(...)` already detects when arc summaries reference dead/cursed NPCs without past-tense cues, but the queue is observe-only — never fed back into the user prompt. Adding a "RECENT CONTRADICTIONS" guard block is a natural Phase 5 candidate.
- **Time-based scene-summary fallback.** `SceneBoundaryDetector` only fires on location/combat/scene-tag change. Long single-scene exchanges silently lose oldest history past the 8000-token cap. A turn-count fallback (e.g. force a summary after every 8 turns regardless of boundary) would close this gap.
- **`Prompts.SYS` prose-vs-rules rebalance.** Commit `f2baef8` traded ~22% of the prompt's words from voice exemplars to BAD/GOOD constraint pairs ("Phase 4: few-shot prompt polish — trade prose for BAD/GOOD pairs"). Subsequent commits added more rules without restoring voice content. A targeted rebalance — moving some BAD/GOOD pairs into a tighter checklist while restoring voice exemplars — is its own multi-iteration project. Not Phase 5 material; needs its own brainstorm.
- **Arc-summary retrieval expansion.** Currently `BUDGET_ARC_SUMMARIES = 1500` chars and arcs are surfaced via keyword match + newest-fill, capping at ~2-3 arcs per turn. A pinned "key decisions ever made" block (independent of keyword retrieval) would surface long-running consequences that share no keywords with the current action.
