# Infinite-Turn Memory — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the turn-90 memory degradation by replacing hard history cuts with a rolling scene-summary layer, token-budgeted history window, and scene-relevance-filtered NPC injection — all without touching persistence or reducers.

**Architecture:** A new `SceneSummary` list lives in `GameUiState`/`SaveData`. When the game crosses a scene boundary (location change, combat transition, explicit scene tag change), a background AI call compresses the turns since the last summary into ~200 tokens and appends to the list. The prompt is built from `system + last-N scene summaries (token-budgeted) + recent turns (token-budgeted) + user turn`. NPCs are filtered by scene-relevance (present in current location OR seen within last 10 turns) rather than a blunt `takeLast(20)`.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, OkHttp, Coroutines/StateFlow, JUnit4, Robolectric, DeepSeek API.

**Non-goals (deferred to Phase 2/3):** Room DB migration, authoritative reducers for combat/shop/status, hierarchical (arc/era) summary compression, vector retrieval. This plan only changes prompt assembly and adds a summarizer — entity tracking still lives in the existing in-memory state.

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/realmsoffate/game/util/TokenEstimate.kt` — char/4 token estimator
- `app/src/main/kotlin/com/realmsoffate/game/data/SceneSummary.kt` — data class
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/SceneBoundaryDetector.kt` — pure function, returns boundary reasons
- `app/src/main/kotlin/com/realmsoffate/game/game/SceneSummarizer.kt` — orchestrates summarizer call + append
- `app/src/test/kotlin/com/realmsoffate/game/util/TokenEstimateTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/reducers/SceneBoundaryDetectorTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/SceneSummarizerTest.kt` (unit-level, mock AI)

**Modify:**
- `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` — add `sceneSummaries` field to `GameUiState` (line 55-100) and `SaveData` (line 323-362)
- `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — add `SCENE_SUMMARY_SYS` constant
- `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt` — replace `takeLast(40)` (line 54) with token-budget windowing; add `summarizeScene` method
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`:
  - `buildUserPrompt` (line 872-958) — inject scene summaries; change NPC filter from `takeLast(20)` to scene-relevance
  - `dispatchToAi` (line 739-830) — hook scene-summary check after AI response commits
  - Save/load wiring — persist `sceneSummaries` in `SaveData`
- `app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt` — include/restore `sceneSummaries`

---

### Task 1: Token estimator utility

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/util/TokenEstimate.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/util/TokenEstimateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/util/TokenEstimateTest.kt`:

```kotlin
package com.realmsoffate.game.util

import com.realmsoffate.game.data.ChatMsg
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenEstimateTest {
    @Test
    fun `estimateTokens returns zero for empty string`() {
        assertEquals(0, TokenEstimate.ofText(""))
    }

    @Test
    fun `estimateTokens uses char div 4 heuristic`() {
        // 16 chars → 4 tokens
        assertEquals(4, TokenEstimate.ofText("abcdefghijklmnop"))
    }

    @Test
    fun `estimateTokens rounds up`() {
        // 5 chars → 2 tokens (5/4 = 1.25, ceil = 2)
        assertEquals(2, TokenEstimate.ofText("hello"))
    }

    @Test
    fun `estimateChatMsg includes role overhead`() {
        // 4-char content + ~4 tokens overhead for role/formatting
        val msg = ChatMsg(role = "user", content = "test")
        // 4/4 + 4 overhead = 5
        assertEquals(5, TokenEstimate.ofMessage(msg))
    }

    @Test
    fun `sumMessages totals individual estimates`() {
        val msgs = listOf(
            ChatMsg(role = "user", content = "abcd"),     // 1 + 4 = 5
            ChatMsg(role = "assistant", content = "efgh")  // 1 + 4 = 5
        )
        assertEquals(10, TokenEstimate.sumMessages(msgs))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.util.TokenEstimateTest"`

Expected: FAIL — `TokenEstimate` does not exist.

- [ ] **Step 3: Implement minimal code to make the test pass**

Create `app/src/main/kotlin/com/realmsoffate/game/util/TokenEstimate.kt`:

```kotlin
package com.realmsoffate.game.util

import com.realmsoffate.game.data.ChatMsg

/**
 * Rough token counter for DeepSeek / OpenAI-style tokenizers.
 *
 * Uses the char/4 heuristic — a well-known approximation that over-estimates
 * short English strings slightly and under-estimates dense JSON. Good enough
 * for windowing decisions; do not use for billing.
 *
 * Each ChatMsg carries ~4 tokens of framing overhead (role label + separators)
 * in the OpenAI chat format; we bake that into `ofMessage`.
 */
object TokenEstimate {
    private const val MSG_FRAMING_OVERHEAD = 4

    fun ofText(s: String): Int = if (s.isEmpty()) 0 else (s.length + 3) / 4

    fun ofMessage(m: ChatMsg): Int = ofText(m.content) + MSG_FRAMING_OVERHEAD

    fun sumMessages(msgs: List<ChatMsg>): Int = msgs.sumOf { ofMessage(it) }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.util.TokenEstimateTest"`

Expected: PASS — 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/util/TokenEstimate.kt \
        app/src/test/kotlin/com/realmsoffate/game/util/TokenEstimateTest.kt
git commit -m "feat: add TokenEstimate utility for AI prompt windowing"
```

---

### Task 2: SceneSummary data model

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/SceneSummary.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` (add field to `GameUiState` and `SaveData`)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/SceneSummaryTest.kt`:

```kotlin
package com.realmsoffate.game.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneSummaryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SceneSummary serializes and deserializes`() {
        val s = SceneSummary(
            turnStart = 5,
            turnEnd = 12,
            sceneName = "ashford-tavern",
            locationName = "Ashford",
            summary = "Met Mira the rogue; she offered a job.",
            keyFacts = listOf("Mira owes Garrick 5g", "Tavern burned down at T11")
        )
        val encoded = json.encodeToString(SceneSummary.serializer(), s)
        val decoded = json.decodeFromString(SceneSummary.serializer(), encoded)
        assertEquals(s, decoded)
    }

    @Test
    fun `SceneSummary has sensible defaults for optional fields`() {
        val s = SceneSummary(
            turnStart = 1, turnEnd = 2,
            sceneName = "", locationName = "",
            summary = "test"
        )
        assertEquals(emptyList<String>(), s.keyFacts)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.SceneSummaryTest"`

Expected: FAIL — `SceneSummary` does not exist.

- [ ] **Step 3: Create the data class**

Create `app/src/main/kotlin/com/realmsoffate/game/data/SceneSummary.kt`:

```kotlin
package com.realmsoffate.game.data

import kotlinx.serialization.Serializable

/**
 * Compressed record of a finished scene. Emitted by the summarizer when a
 * scene boundary is crossed (location change, combat transition, explicit
 * [SCENE:] tag change).
 *
 * turnStart/turnEnd are inclusive and reference `GameUiState.turns` values.
 * summary is the narrative compression (~200 tokens). keyFacts is an optional
 * bullet list the summarizer may emit for high-precision facts that must
 * survive (NPC promises, item handoffs, death confirmations).
 */
@Serializable
data class SceneSummary(
    val turnStart: Int,
    val turnEnd: Int,
    val sceneName: String,
    val locationName: String,
    val summary: String,
    val keyFacts: List<String> = emptyList()
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.SceneSummaryTest"`

Expected: PASS.

- [ ] **Step 5: Add field to GameUiState and SaveData**

Modify `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt`.

At the end of `GameUiState` (before the closing `)` around line 100), add:

```kotlin
    /**
     * Ordered list of compressed scene records. Prepended during the session
     * by [SceneSummarizer] when a scene boundary is crossed; injected into
     * the prompt via [GameViewModel.buildUserPrompt] with a token budget cap.
     */
    val sceneSummaries: List<SceneSummary> = emptyList()
```

At the end of `SaveData` (before the closing `)` around line 362), add:

```kotlin
    /** Scene summaries persisted for reload. Empty on legacy saves; rebuilt forward from next scene boundary. */
    val sceneSummaries: List<SceneSummary> = emptyList()
```

- [ ] **Step 6: Run the full test suite to confirm nothing broke**

Run: `gradle :app:testDebugUnitTest`

Expected: All previously green tests remain green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/SceneSummary.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/Models.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/SceneSummaryTest.kt
git commit -m "feat: add SceneSummary data model with sceneSummaries field on state and save"
```

---

### Task 3: Token-budget sliding window in AiRepository

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt:53-58`
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/AiRepositoryWindowTest.kt`

Rationale: `takeLast(40)` silently drops ~78% of history at turn 90. Replace with a function that keeps the largest suffix of messages whose combined token count ≤ budget, and always preserves the final user message.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/AiRepositoryWindowTest.kt`:

```kotlin
package com.realmsoffate.game.data

import com.realmsoffate.game.util.TokenEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRepositoryWindowTest {

    private fun msg(role: String, len: Int) =
        ChatMsg(role = role, content = "x".repeat(len))

    @Test
    fun `windowByTokenBudget returns all when under budget`() {
        val history = listOf(
            msg("user", 40),       // 10 + 4 = 14
            msg("assistant", 40),  // 14
            msg("user", 40)        // 14
        )
        val windowed = AiRepository.windowByTokenBudget(history, budget = 1000)
        assertEquals(3, windowed.size)
        assertEquals(history, windowed)
    }

    @Test
    fun `windowByTokenBudget trims oldest when over budget`() {
        val history = List(100) { i -> msg(if (i % 2 == 0) "user" else "assistant", 400) }
        // Each message ≈ 100 + 4 = 104 tokens. Budget 1000 → keep ~9 messages.
        val windowed = AiRepository.windowByTokenBudget(history, budget = 1000)
        assertTrue("Should keep fewer than input", windowed.size < history.size)
        assertTrue("Should respect budget", TokenEstimate.sumMessages(windowed) <= 1000)
        assertTrue("Should keep tail (most recent)", windowed.last() == history.last())
    }

    @Test
    fun `windowByTokenBudget always preserves final user message even if oversized`() {
        val big = msg("user", 10_000) // ~2504 tokens alone
        val history = listOf(
            msg("user", 10),
            msg("assistant", 10),
            big
        )
        val windowed = AiRepository.windowByTokenBudget(history, budget = 500)
        assertEquals(1, windowed.size)
        assertEquals(big, windowed[0])
    }

    @Test
    fun `windowByTokenBudget returns empty for empty input`() {
        assertEquals(emptyList<ChatMsg>(), AiRepository.windowByTokenBudget(emptyList(), budget = 1000))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.AiRepositoryWindowTest"`

Expected: FAIL — `windowByTokenBudget` does not exist.

- [ ] **Step 3: Add the windowing function to AiRepository**

Modify `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt`. Add a companion object with the pure windowing function near the top of the class (insert after the `lastPromptTokens` property, around line 43):

```kotlin
    companion object {
        /** Default history budget in tokens — leaves headroom for system + scene summaries + per-turn context. */
        const val HISTORY_TOKEN_BUDGET: Int = 8000

        /**
         * Keep the largest suffix of [history] whose summed token estimate ≤ [budget].
         * Always preserves the final message even if it alone exceeds the budget
         * (a dropped final user turn would break the turn contract).
         */
        fun windowByTokenBudget(history: List<ChatMsg>, budget: Int): List<ChatMsg> {
            if (history.isEmpty()) return emptyList()
            val kept = ArrayDeque<ChatMsg>()
            var used = 0
            for (m in history.asReversed()) {
                val cost = com.realmsoffate.game.util.TokenEstimate.ofMessage(m)
                if (kept.isEmpty()) {
                    // Always keep the final message.
                    kept.addFirst(m)
                    used += cost
                    continue
                }
                if (used + cost > budget) break
                kept.addFirst(m)
                used += cost
            }
            return kept.toList()
        }
    }
```

Then replace line 54 in `callDeepSeek`:

```kotlin
        val trimmed = history.takeLast(40).toMutableList()
```

with:

```kotlin
        val trimmed = windowByTokenBudget(history, HISTORY_TOKEN_BUDGET).toMutableList()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.AiRepositoryWindowTest"`

Expected: PASS — 4 tests green.

- [ ] **Step 5: Run full suite — nothing else should break**

Run: `gradle :app:testDebugUnitTest`

Expected: Prior tests stay green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/AiRepositoryWindowTest.kt
git commit -m "feat: token-budget sliding window replaces takeLast(40) in AiRepository"
```

---

### Task 4: Scene boundary detector

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/game/reducers/SceneBoundaryDetector.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/reducers/SceneBoundaryDetectorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/reducers/SceneBoundaryDetectorTest.kt`:

```kotlin
package com.realmsoffate.game.game.reducers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneBoundaryDetectorTest {

    private fun snap(
        scene: String = "default",
        location: String = "Ashford",
        inCombat: Boolean = false
    ) = SceneBoundaryDetector.Snapshot(
        sceneTag = scene,
        locationName = location,
        inCombat = inCombat
    )

    @Test
    fun `no boundary when nothing changes`() {
        val prev = snap()
        val cur = snap()
        assertNull(SceneBoundaryDetector.detect(prev, cur))
    }

    @Test
    fun `boundary when location changes`() {
        val prev = snap(location = "Ashford")
        val cur = snap(location = "Greymoor")
        val r = SceneBoundaryDetector.detect(prev, cur)
        assertEquals(SceneBoundaryDetector.Reason.LOCATION_CHANGED, r)
    }

    @Test
    fun `boundary when scene tag changes`() {
        val prev = snap(scene = "ashford-tavern")
        val cur = snap(scene = "ashford-market")
        assertEquals(
            SceneBoundaryDetector.Reason.SCENE_TAG_CHANGED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }

    @Test
    fun `boundary when combat starts`() {
        val prev = snap(inCombat = false)
        val cur = snap(inCombat = true)
        assertEquals(
            SceneBoundaryDetector.Reason.COMBAT_STARTED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }

    @Test
    fun `boundary when combat ends`() {
        val prev = snap(inCombat = true)
        val cur = snap(inCombat = false)
        assertEquals(
            SceneBoundaryDetector.Reason.COMBAT_ENDED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }

    @Test
    fun `location change takes precedence over scene tag change`() {
        val prev = snap(scene = "a", location = "Ashford")
        val cur = snap(scene = "b", location = "Greymoor")
        assertEquals(
            SceneBoundaryDetector.Reason.LOCATION_CHANGED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }

    @Test
    fun `scene default to non-default triggers boundary`() {
        val prev = snap(scene = "default")
        val cur = snap(scene = "ashford-tavern")
        assertEquals(
            SceneBoundaryDetector.Reason.SCENE_TAG_CHANGED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.reducers.SceneBoundaryDetectorTest"`

Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement**

Create `app/src/main/kotlin/com/realmsoffate/game/game/reducers/SceneBoundaryDetector.kt`:

```kotlin
package com.realmsoffate.game.game.reducers

/**
 * Pure function that compares two game state snapshots and emits a boundary
 * reason if the scene has meaningfully changed. The scene-summarizer runs
 * whenever this returns non-null.
 *
 * Precedence: LOCATION_CHANGED > COMBAT_* > SCENE_TAG_CHANGED. Location is
 * the strongest signal; a combat transition inside one location still earns
 * a summary (so post-combat notes don't leak into the next fight).
 */
object SceneBoundaryDetector {
    enum class Reason {
        LOCATION_CHANGED,
        SCENE_TAG_CHANGED,
        COMBAT_STARTED,
        COMBAT_ENDED
    }

    data class Snapshot(
        val sceneTag: String,
        val locationName: String,
        val inCombat: Boolean
    )

    fun detect(prev: Snapshot, cur: Snapshot): Reason? {
        if (prev.locationName != cur.locationName && cur.locationName.isNotBlank()) {
            return Reason.LOCATION_CHANGED
        }
        if (prev.inCombat != cur.inCombat) {
            return if (cur.inCombat) Reason.COMBAT_STARTED else Reason.COMBAT_ENDED
        }
        if (prev.sceneTag != cur.sceneTag) {
            return Reason.SCENE_TAG_CHANGED
        }
        return null
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.reducers.SceneBoundaryDetectorTest"`

Expected: PASS — 7 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/reducers/SceneBoundaryDetector.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/reducers/SceneBoundaryDetectorTest.kt
git commit -m "feat: add pure SceneBoundaryDetector for summarizer trigger"
```

---

### Task 5: Summarizer prompt + AI method

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — add `SCENE_SUMMARY_SYS`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt` — add `summarizeScene`

- [ ] **Step 1: Add the prompt constant**

Open `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt`. Add this constant to the `Prompts` object (anywhere near the other top-level constants, e.g. right after `DS_PREFIX`):

```kotlin
    /**
     * System prompt for the scene-summarizer utility call. Expected output is
     * a JSON object: {"summary": "...", "keyFacts": ["...", "..."]}. We lean
     * on structured output because DeepSeek tends to add prose intros otherwise.
     *
     * The model sees the raw ChatMsg history for the scene and the scene/location
     * name; it does NOT see full character state — that would blow token budget
     * and isn't needed for narrative compression.
     */
    const val SCENE_SUMMARY_SYS: String = """You are the historian for an ongoing tabletop RPG session. You receive the dialogue and narration for ONE completed scene, plus the scene name and location. Your job is to compress that scene into:
  - A "summary": a single paragraph, 3-6 sentences, ~150 tokens. Capture: who was present, what was said/done, how it ended, any promises or threats made. Name NPCs explicitly. Write in past tense.
  - "keyFacts": 0-6 bullet strings capturing facts that MUST be preserved (e.g. "Mira now owes the player 5 gold", "The tavern burned down", "Garrick swore revenge on Lord Corwin"). Skip flavor. Only facts that could change future turns.

Return ONLY a JSON object of the form:
{"summary":"...","keyFacts":["...","..."]}

No markdown fences. No prose outside the JSON. No additional keys."""
```

- [ ] **Step 2: Write the failing test for summarizeScene**

Create `app/src/test/kotlin/com/realmsoffate/game/data/AiRepositorySummarizeTest.kt`:

```kotlin
package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AiRepositorySummarizeTest {
    @Test
    fun `parseSummaryResponse handles clean JSON`() {
        val raw = """{"summary":"Met Mira.","keyFacts":["Owes 5g","Tavern burned"]}"""
        val parsed = AiRepository.parseSummaryResponse(raw)
        assertEquals("Met Mira.", parsed?.first)
        assertEquals(listOf("Owes 5g", "Tavern burned"), parsed?.second)
    }

    @Test
    fun `parseSummaryResponse handles fenced JSON`() {
        val raw = "```json\n{\"summary\":\"Short.\",\"keyFacts\":[]}\n```"
        val parsed = AiRepository.parseSummaryResponse(raw)
        assertEquals("Short.", parsed?.first)
        assertEquals(emptyList<String>(), parsed?.second)
    }

    @Test
    fun `parseSummaryResponse returns null on invalid JSON`() {
        assertEquals(null, AiRepository.parseSummaryResponse("not json"))
    }

    @Test
    fun `parseSummaryResponse extracts first JSON object from noisy output`() {
        val raw = "Sure! Here you go: {\"summary\":\"x\",\"keyFacts\":[\"y\"]} (hope that helps)"
        val parsed = AiRepository.parseSummaryResponse(raw)
        assertEquals("x", parsed?.first)
        assertEquals(listOf("y"), parsed?.second)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.AiRepositorySummarizeTest"`

Expected: FAIL — `parseSummaryResponse` does not exist.

- [ ] **Step 4: Implement the parser and the network method**

Modify `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt`. Add these functions to the companion object (beside `windowByTokenBudget`):

```kotlin
        /**
         * Extract (summary, keyFacts) from a DeepSeek response. Tolerates code
         * fences and surrounding prose because models don't always behave.
         * Returns null if no valid JSON object with a "summary" string is found.
         */
        fun parseSummaryResponse(raw: String): Pair<String, List<String>>? {
            val stripped = raw
                .substringAfter("```json", raw)
                .substringAfter("```", raw)
                .substringBeforeLast("```", raw)
            val start = stripped.indexOf('{')
            val end = stripped.lastIndexOf('}')
            if (start < 0 || end < 0 || end <= start) return null
            val jsonSlice = stripped.substring(start, end + 1)
            return try {
                val j = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true; isLenient = true
                }
                val obj = j.parseToJsonElement(jsonSlice).jsonObject
                val summary = obj["summary"]?.jsonPrimitive?.content ?: return null
                val facts = obj["keyFacts"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } }
                    ?: emptyList()
                summary to facts
            } catch (_: Exception) {
                null
            }
        }
```

Then add the public `summarizeScene` method right after `classifyAction` (after line 176):

```kotlin
    /**
     * Compress a completed scene's history into a short summary + key facts.
     * Uses the scene-summary system prompt and low temperature for consistency.
     * Returns null on network error or unparseable response — caller should
     * treat missing summaries as "better to miss one than crash the turn flow".
     */
    suspend fun summarizeScene(
        apiKey: String,
        sceneName: String,
        locationName: String,
        sceneHistory: List<ChatMsg>
    ): Pair<String, List<String>>? = withContext(Dispatchers.IO) {
        if (sceneHistory.isEmpty()) return@withContext null
        try {
            val header = "SCENE: $sceneName\nLOCATION: $locationName"
            val transcript = sceneHistory.joinToString("\n\n") { m ->
                "[${m.role.uppercase()}]\n${m.content}"
            }
            val userContent = "$header\n\n---\n\n$transcript"
            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", Prompts.SCENE_SUMMARY_SYS)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userContent)
                })
            }
            val body = buildJsonObject {
                put("model", "deepseek-chat")
                put("max_tokens", 400)
                put("temperature", 0.2)
                put("messages", messages)
            }
            val req = Request.Builder()
                .url("https://api.deepseek.com/v1/chat/completions")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            resp.use {
                if (!it.isSuccessful) return@withContext null
                val text = it.body?.string().orEmpty()
                val root = json.parseToJsonElement(text).jsonObject
                val content = root["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    ?: return@withContext null
                parseSummaryResponse(content)
            }
        } catch (_: Exception) {
            null
        }
    }
```

- [ ] **Step 5: Run parser tests to verify pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.AiRepositorySummarizeTest"`

Expected: PASS — 4 tests green.

- [ ] **Step 6: Full test run**

Run: `gradle :app:testDebugUnitTest`

Expected: All green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/AiRepositorySummarizeTest.kt
git commit -m "feat: add summarizeScene API call + JSON response parser"
```

---

### Task 6: SceneSummarizer orchestrator

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/game/SceneSummarizer.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/game/SceneSummarizerTest.kt`

This is the orchestrator that decides what history slice to send, appends the resulting SceneSummary to state, and tolerates AI failure. It is a pure-ish class: no direct state mutation, returns a new `List<SceneSummary>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/game/SceneSummarizerTest.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.ChatMsg
import com.realmsoffate.game.data.SceneSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SceneSummarizerTest {

    private fun msg(role: String, c: String) = ChatMsg(role, c)

    @Test
    fun `skips when history slice is empty`() = runTest {
        val summarizer = SceneSummarizer { _, _, _, _ -> "ignored" to listOf("x") }
        val existing = listOf<SceneSummary>()
        val result = summarizer.summarizeIfPossible(
            apiKey = "k",
            priorSummaries = existing,
            scene = "a", location = "L",
            history = emptyList(),
            turnStart = 1, turnEnd = 1
        )
        assertSame(existing, result)
    }

    @Test
    fun `appends summary when AI returns one`() = runTest {
        val summarizer = SceneSummarizer { _, _, _, _ -> "A thing happened." to listOf("fact1") }
        val prior = listOf<SceneSummary>()
        val result = summarizer.summarizeIfPossible(
            apiKey = "k",
            priorSummaries = prior,
            scene = "ashford-tavern", location = "Ashford",
            history = listOf(msg("user", "I enter"), msg("assistant", "You see Mira")),
            turnStart = 5, turnEnd = 8
        )
        assertEquals(1, result.size)
        val s = result[0]
        assertEquals("A thing happened.", s.summary)
        assertEquals(listOf("fact1"), s.keyFacts)
        assertEquals(5, s.turnStart)
        assertEquals(8, s.turnEnd)
        assertEquals("ashford-tavern", s.sceneName)
        assertEquals("Ashford", s.locationName)
    }

    @Test
    fun `returns prior list unchanged on AI failure`() = runTest {
        val summarizer = SceneSummarizer { _, _, _, _ -> null }
        val prior = listOf(
            SceneSummary(1, 2, "s", "L", "old", emptyList())
        )
        val result = summarizer.summarizeIfPossible(
            apiKey = "k",
            priorSummaries = prior,
            scene = "s2", location = "L2",
            history = listOf(msg("user", "hi")),
            turnStart = 3, turnEnd = 3
        )
        assertSame(prior, result)
    }

    @Test
    fun `appends to existing summaries in order`() = runTest {
        val summarizer = SceneSummarizer { _, _, _, _ -> "new" to emptyList() }
        val prior = listOf(SceneSummary(1, 2, "s1", "L1", "first"))
        val result = summarizer.summarizeIfPossible(
            apiKey = "k",
            priorSummaries = prior,
            scene = "s2", location = "L2",
            history = listOf(msg("user", "x")),
            turnStart = 3, turnEnd = 4
        )
        assertEquals(2, result.size)
        assertEquals("first", result[0].summary)
        assertEquals("new", result[1].summary)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.SceneSummarizerTest"`

Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement**

Create `app/src/main/kotlin/com/realmsoffate/game/game/SceneSummarizer.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.AiRepository
import com.realmsoffate.game.data.ChatMsg
import com.realmsoffate.game.data.SceneSummary

/**
 * Orchestrates one scene-summary call and returns the updated list. A thin
 * shim over [AiRepository.summarizeScene] that's easy to unit-test — the
 * primary constructor accepts a callable `summarize` lambda so tests can
 * inject a deterministic stub instead of hitting the network.
 *
 * Failure policy: missing summaries are preferred over crashing. If the AI
 * call returns null, the prior list is returned unchanged and the caller
 * can simply try again at the next scene boundary.
 */
class SceneSummarizer(
    private val summarize: suspend (
        apiKey: String,
        scene: String,
        location: String,
        history: List<ChatMsg>
    ) -> Pair<String, List<String>>?
) {
    constructor(ai: AiRepository) : this({ apiKey, scene, loc, hist ->
        ai.summarizeScene(apiKey, scene, loc, hist)
    })

    suspend fun summarizeIfPossible(
        apiKey: String,
        priorSummaries: List<SceneSummary>,
        scene: String,
        location: String,
        history: List<ChatMsg>,
        turnStart: Int,
        turnEnd: Int
    ): List<SceneSummary> {
        if (history.isEmpty()) return priorSummaries
        val result = summarize(apiKey, scene, location, history) ?: return priorSummaries
        return priorSummaries + SceneSummary(
            turnStart = turnStart,
            turnEnd = turnEnd,
            sceneName = scene,
            locationName = location,
            summary = result.first,
            keyFacts = result.second
        )
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.SceneSummarizerTest"`

Expected: PASS — 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/SceneSummarizer.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/SceneSummarizerTest.kt
git commit -m "feat: add SceneSummarizer orchestrator with injectable AI call"
```

---

### Task 7: Wire SceneSummarizer into dispatchToAi

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:739-830`

We trigger a summary in the background when a scene boundary is crossed. The summary AI call runs in `viewModelScope.launch` after the main response has already updated state — the user is never blocked waiting for summary.

- [ ] **Step 1: Add the summarizer field to GameViewModel**

At the top of the `GameViewModel` class (near other fields, around line 187), add:

```kotlin
    private val sceneSummarizer = SceneSummarizer(ai)
```

Import it:

```kotlin
import com.realmsoffate.game.game.SceneSummarizer
```

(If you're in the `com.realmsoffate.game.game` package already, no import needed.)

- [ ] **Step 2: Capture the pre-turn snapshot before calling the AI**

In `dispatchToAi` (around line 756), just before building the user message, capture what the state looks like before the turn:

```kotlin
        val preSnapshot = SceneBoundaryDetector.Snapshot(
            sceneTag = state.currentScene,
            locationName = state.worldMap.locations.getOrNull(state.currentLoc)?.name ?: "",
            inCombat = state.combat != null
        )
        val turnsBeforeResponse = state.turns
        val priorSummaryEndTurn = state.sceneSummaries.lastOrNull()?.turnEnd ?: 0
```

Import at the top of the file:

```kotlin
import com.realmsoffate.game.game.reducers.SceneBoundaryDetector
```

- [ ] **Step 3: Check for boundary after state updates and launch summary job**

In `dispatchToAi`, after `withHistory` is assigned and `_ui.value = withHistory` runs (find the final `_ui.value = withHistory` around line 820-830 of the current file — confirm with `gradle assembleDebug` line after). Right after that assignment, insert:

```kotlin
        val postSnapshot = SceneBoundaryDetector.Snapshot(
            sceneTag = withHistory.currentScene,
            locationName = withHistory.worldMap.locations.getOrNull(withHistory.currentLoc)?.name ?: "",
            inCombat = withHistory.combat != null
        )
        val boundary = SceneBoundaryDetector.detect(preSnapshot, postSnapshot)
        if (boundary != null) {
            val apiKey = _apiKey.value
            val sceneName = preSnapshot.sceneTag.ifBlank { "default" }
            val locationName = preSnapshot.locationName
            // Slice the history that belongs to the completed scene: from after
            // the previous summary's end turn up through this turn's user+assistant.
            // Each turn is roughly 2 messages (user + assistant), so we window
            // by turn count rather than message count.
            val turnsCovered = (turnsBeforeResponse - priorSummaryEndTurn).coerceAtLeast(1)
            val approxMessages = (turnsCovered * 2).coerceAtMost(withHistory.history.size)
            val sceneHistory = withHistory.history.takeLast(approxMessages + 2) // +2 = current user+assistant
            val turnStart = priorSummaryEndTurn + 1
            val turnEnd = withHistory.turns
            viewModelScope.launch {
                val updated = sceneSummarizer.summarizeIfPossible(
                    apiKey = apiKey,
                    priorSummaries = withHistory.sceneSummaries,
                    scene = sceneName,
                    location = locationName,
                    history = sceneHistory,
                    turnStart = turnStart,
                    turnEnd = turnEnd
                )
                if (updated !== withHistory.sceneSummaries) {
                    _ui.update { cur -> cur.copy(sceneSummaries = updated) }
                }
            }
        }
```

Make sure `kotlinx.coroutines.flow.update` is imported at the top of the file:

```kotlin
import kotlinx.coroutines.flow.update
```

- [ ] **Step 4: Compile**

Run: `gradle :app:compileDebugKotlin`

Expected: PASS. If any imports are missing, resolve them.

- [ ] **Step 5: Run full suite**

Run: `gradle :app:testDebugUnitTest`

Expected: All green. No new tests here yet — integration is covered in Task 10.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "feat: trigger scene summary in background on scene boundary"
```

---

### Task 8: Inject scene summaries into buildUserPrompt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:872-958`

- [ ] **Step 1: Locate `buildUserPrompt` and find the recent-narration section**

Find the block that appends RECENT STORY (around line 944-950):

```kotlin
            val recentNarration = s.messages
                .filterIsInstance<DisplayMessage.Narration>()
                .takeLast(2)
                .joinToString("\n---\n") { it.text.take(300) }
            if (recentNarration.isNotBlank()) {
                append("\n\nRECENT STORY (continue from here...):\n$recentNarration")
            }
```

- [ ] **Step 2: Insert scene-summary injection BEFORE the recent-story block**

Immediately above the recent-narration block, add:

```kotlin
            // Scene summaries — compressed history of prior scenes, token-budgeted.
            // Keeps oldest-first so the model reads chronologically.
            val summaryBudget = 2000 // tokens
            val summaries = s.sceneSummaries
            if (summaries.isNotEmpty()) {
                val kept = ArrayDeque<com.realmsoffate.game.data.SceneSummary>()
                var used = 0
                for (sm in summaries.asReversed()) {
                    val block = buildString {
                        append("• [T${sm.turnStart}-${sm.turnEnd} @ ${sm.locationName}] ")
                        append(sm.summary)
                        if (sm.keyFacts.isNotEmpty()) {
                            append(" FACTS: ")
                            append(sm.keyFacts.joinToString("; "))
                        }
                    }
                    val cost = com.realmsoffate.game.util.TokenEstimate.ofText(block)
                    if (used + cost > summaryBudget && kept.isNotEmpty()) break
                    kept.addFirst(sm)
                    used += cost
                }
                if (kept.isNotEmpty()) {
                    append("\n\nSTORY SO FAR (earlier scenes compressed):")
                    kept.forEach { sm ->
                        append("\n• [T${sm.turnStart}-${sm.turnEnd} @ ${sm.locationName}] ")
                        append(sm.summary)
                        if (sm.keyFacts.isNotEmpty()) {
                            append(" FACTS: ${sm.keyFacts.joinToString("; ")}")
                        }
                    }
                }
            }
```

- [ ] **Step 3: Write a focused test using GameStateFixture**

Create `app/src/test/kotlin/com/realmsoffate/game/game/BuildUserPromptSummaryTest.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.SceneSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for scene-summary injection into user prompt. We verify
 * behavior by inspecting the built prompt string — the production function is
 * in GameViewModel but exercises pure string assembly from a state snapshot.
 *
 * NOTE: buildUserPrompt is currently private in GameViewModel. This test
 * assumes Task 8 also makes it package-private (remove `private`) OR exposes
 * a thin `@VisibleForTesting` wrapper. Choose the wrapper approach:
 * `@VisibleForTesting internal fun buildUserPromptForTest(...) = buildUserPrompt(...)`.
 */
class BuildUserPromptSummaryTest {

    @Test
    fun `prompt includes STORY SO FAR header when summaries exist`() {
        val state = GameStateFixture.withSummaries(
            listOf(
                SceneSummary(1, 4, "ashford-tavern", "Ashford", "Met Mira who offered a job.", listOf("Owes 5g"))
            )
        )
        val prompt = GameStateFixture.buildPrompt(state, action = "Continue")
        assertTrue("Prompt should contain STORY SO FAR header", prompt.contains("STORY SO FAR"))
        assertTrue("Prompt should contain Mira", prompt.contains("Mira"))
        assertTrue("Prompt should contain key facts", prompt.contains("Owes 5g"))
    }

    @Test
    fun `prompt omits STORY SO FAR when no summaries exist`() {
        val state = GameStateFixture.withSummaries(emptyList())
        val prompt = GameStateFixture.buildPrompt(state, action = "Continue")
        assertFalse("Prompt must not contain header when summaries empty", prompt.contains("STORY SO FAR"))
    }
}
```

- [ ] **Step 4: Extend GameStateFixture to support this test**

Open `app/src/test/kotlin/com/realmsoffate/game/game/GameStateFixture.kt` and add the helper functions. (Check the existing file first — if the fixture already returns a configured `GameUiState`, just add `withSummaries` and `buildPrompt` helpers that delegate to it.)

Add to `GameStateFixture.kt`:

```kotlin
    fun withSummaries(summaries: List<com.realmsoffate.game.data.SceneSummary>): com.realmsoffate.game.data.GameUiState {
        // Adapt to whatever base fixture helper already exists. If there is a
        // `default()` or `make()` function, call it and `.copy()` the summaries in.
        return default().copy(sceneSummaries = summaries)
    }

    fun buildPrompt(state: com.realmsoffate.game.data.GameUiState, action: String): String {
        val vm = GameViewModelTestHarness.forState(state)
        return vm.buildUserPromptForTest(action = action)
    }
```

If `GameViewModelTestHarness` does not exist, this is a signal the test needs a narrower approach. Fallback: extract the scene-summary rendering into a top-level pure function in `GameViewModel.kt`:

```kotlin
internal fun renderSceneSummariesBlock(
    summaries: List<com.realmsoffate.game.data.SceneSummary>,
    tokenBudget: Int = 2000
): String {
    if (summaries.isEmpty()) return ""
    val kept = ArrayDeque<com.realmsoffate.game.data.SceneSummary>()
    var used = 0
    for (sm in summaries.asReversed()) {
        val block = buildString {
            append("• [T${sm.turnStart}-${sm.turnEnd} @ ${sm.locationName}] ")
            append(sm.summary)
            if (sm.keyFacts.isNotEmpty()) append(" FACTS: ${sm.keyFacts.joinToString("; ")}")
        }
        val cost = com.realmsoffate.game.util.TokenEstimate.ofText(block)
        if (used + cost > tokenBudget && kept.isNotEmpty()) break
        kept.addFirst(sm)
        used += cost
    }
    if (kept.isEmpty()) return ""
    return buildString {
        append("\n\nSTORY SO FAR (earlier scenes compressed):")
        kept.forEach { sm ->
            append("\n• [T${sm.turnStart}-${sm.turnEnd} @ ${sm.locationName}] ")
            append(sm.summary)
            if (sm.keyFacts.isNotEmpty()) append(" FACTS: ${sm.keyFacts.joinToString("; ")}")
        }
    }
}
```

Use it inside `buildUserPrompt` with `append(renderSceneSummariesBlock(s.sceneSummaries))` replacing the inline block. Then change the test to call `renderSceneSummariesBlock` directly — far simpler than a ViewModel harness.

Updated test file becomes:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.SceneSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildUserPromptSummaryTest {
    @Test
    fun `renderSceneSummariesBlock includes header and facts`() {
        val block = renderSceneSummariesBlock(listOf(
            SceneSummary(1, 4, "ashford-tavern", "Ashford", "Met Mira.", listOf("Owes 5g"))
        ))
        assertTrue(block.contains("STORY SO FAR"))
        assertTrue(block.contains("Mira"))
        assertTrue(block.contains("Owes 5g"))
    }

    @Test
    fun `renderSceneSummariesBlock returns empty string when list empty`() {
        assertTrue(renderSceneSummariesBlock(emptyList()).isEmpty())
    }

    @Test
    fun `renderSceneSummariesBlock respects token budget keeping newest`() {
        val many = (1..50).map {
            SceneSummary(it, it, "s$it", "L$it", "Long summary text ".repeat(20), emptyList())
        }
        val block = renderSceneSummariesBlock(many, tokenBudget = 200)
        // Should contain last few but not first.
        assertTrue("Should contain newest", block.contains("[T50-50"))
        assertFalse("Should drop oldest under tight budget", block.contains("[T1-1 @ L1]"))
    }
}
```

- [ ] **Step 5: Run the test**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.BuildUserPromptSummaryTest"`

Expected: PASS — 3 tests green.

- [ ] **Step 6: Run full suite**

Run: `gradle :app:testDebugUnitTest`

Expected: All green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/BuildUserPromptSummaryTest.kt
git commit -m "feat: inject token-budgeted scene summaries into user prompt"
```

---

### Task 9: Scene-relevance NPC filtering in buildUserPrompt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:909-929` (the NPC roster section)

Replace `takeLast(20)` with a relevance filter: include NPCs where `lastSeenTurn >= currentTurn - 10` OR `lastLocation == currentLocation`. Cap at 30.

- [ ] **Step 1: Write the failing test for the filter**

Create `app/src/test/kotlin/com/realmsoffate/game/game/NpcRelevanceFilterTest.kt`:

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.LogNpc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcRelevanceFilterTest {

    private fun npc(name: String, lastSeen: Int, lastLoc: String = "") =
        LogNpc(id = name.lowercase(), name = name, metTurn = 0, lastSeenTurn = lastSeen, lastLocation = lastLoc)

    @Test
    fun `includes NPC seen within recency window`() {
        val all = listOf(npc("Mira", lastSeen = 45))
        val r = filterRelevantNpcs(all, currentTurn = 50, currentLocation = "Ashford")
        assertEquals(1, r.size)
    }

    @Test
    fun `includes NPC in current location even if old`() {
        val all = listOf(npc("Garrick", lastSeen = 1, lastLoc = "Ashford"))
        val r = filterRelevantNpcs(all, currentTurn = 80, currentLocation = "Ashford")
        assertEquals(1, r.size)
    }

    @Test
    fun `excludes NPC neither recent nor co-located`() {
        val all = listOf(npc("Oldfriend", lastSeen = 1, lastLoc = "Faraway"))
        val r = filterRelevantNpcs(all, currentTurn = 80, currentLocation = "Ashford")
        assertTrue(r.isEmpty())
    }

    @Test
    fun `caps at 30 preferring most recent`() {
        val all = (1..100).map { npc("n$it", lastSeen = it) }
        val r = filterRelevantNpcs(all, currentTurn = 200, currentLocation = "")
        // Recency window is currentTurn - 10 = 190, so NONE are in window.
        // But location-coloc also produces none (all locs blank != "")
        // so result = empty. Test the cap at a different scenario:
        val allClose = (1..100).map { npc("n$it", lastSeen = 200 - it, lastLoc = "") }
        // All within recency (lastSeen >= 190 means lastSeen 190..199 → i ∈ 1..10)
        val r2 = filterRelevantNpcs(allClose, currentTurn = 200, currentLocation = "")
        // i=1 → lastSeen=199, ... i=10 → lastSeen=190. So 10 included, not capped.
        assertEquals(10, r2.size)
    }

    @Test
    fun `cap enforced when more than 30 relevant`() {
        val many = (1..50).map { npc("n$it", lastSeen = 199, lastLoc = "Ashford") }
        val r = filterRelevantNpcs(many, currentTurn = 200, currentLocation = "Ashford")
        assertEquals(30, r.size)
    }

    @Test
    fun `sorted by lastSeenTurn descending within cap`() {
        val all = listOf(
            npc("Old", lastSeen = 192),
            npc("Newest", lastSeen = 199),
            npc("Mid", lastSeen = 195)
        )
        val r = filterRelevantNpcs(all, currentTurn = 200, currentLocation = "")
        assertEquals("Newest", r[0].name)
        assertEquals("Mid", r[1].name)
        assertEquals("Old", r[2].name)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.NpcRelevanceFilterTest"`

Expected: FAIL — `filterRelevantNpcs` does not exist.

- [ ] **Step 3: Implement the filter as a top-level function in GameViewModel.kt**

Add to `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` (top-level, either just above or just below the `renderSceneSummariesBlock` function from Task 8):

```kotlin
internal fun filterRelevantNpcs(
    all: List<com.realmsoffate.game.data.LogNpc>,
    currentTurn: Int,
    currentLocation: String,
    recencyWindow: Int = 10,
    cap: Int = 30
): List<com.realmsoffate.game.data.LogNpc> {
    val recencyFloor = currentTurn - recencyWindow
    val relevant = all.filter { n ->
        n.lastSeenTurn >= recencyFloor ||
            (currentLocation.isNotBlank() && n.lastLocation.equals(currentLocation, ignoreCase = true))
    }
    return relevant.sortedByDescending { it.lastSeenTurn }.take(cap)
}
```

- [ ] **Step 4: Replace the NPC roster section in buildUserPrompt**

Find the block around line 909-929 that currently uses `takeLast(20)` or similar for NPCs. Replace with:

```kotlin
            val currentLocName = s.worldMap.locations.getOrNull(s.currentLoc)?.name ?: ""
            val relevantNpcs = filterRelevantNpcs(
                all = s.npcLog,
                currentTurn = s.turns,
                currentLocation = currentLocName
            )
            if (relevantNpcs.isNotEmpty()) {
                append("\n\nKNOWN NPCs (scene-relevant):")
                relevantNpcs.forEach { n ->
                    append("\n• ${n.name}")
                    if (n.race.isNotBlank()) append(" (${n.race}")
                    if (n.role.isNotBlank()) append(", ${n.role}")
                    if (n.race.isNotBlank() || n.role.isNotBlank()) append(")")
                    append(" — ${n.relationship}")
                    if (n.status != "alive") append(" [${n.status}]")
                    if (n.lastLocation.isNotBlank()) append(" @ ${n.lastLocation}")
                    append(" (T${n.lastSeenTurn})")
                    if (n.personality.isNotBlank()) append("\n  Personality: ${n.personality.take(120)}")
                    if (n.relationshipNote.isNotBlank()) append("\n  Note: ${n.relationshipNote.take(120)}")
                    val recentQuote = n.memorableQuotes.lastOrNull()
                    if (!recentQuote.isNullOrBlank()) append("\n  Memorable: $recentQuote")
                }
            }
```

This replaces whatever the previous NPC section looked like. Verify you removed the old block entirely — search the function for `takeLast(20)` and `npcLog` to confirm.

- [ ] **Step 5: Run tests**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.NpcRelevanceFilterTest"`

Expected: PASS — 6 tests green.

- [ ] **Step 6: Full suite**

Run: `gradle :app:testDebugUnitTest`

Expected: All green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/NpcRelevanceFilterTest.kt
git commit -m "feat: filter NPCs by scene relevance instead of takeLast(20)"
```

---

### Task 10: Persist sceneSummaries across save/load

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/SaveStore.kt` (only if SaveData.toUiState / UiState.toSaveData mapping exists there)

- [ ] **Step 1: Find where GameUiState is converted to SaveData**

Grep for where `SaveData(` is constructed:

Run: `grep -rn "SaveData(" app/src/main/kotlin/com/realmsoffate/game/`

Typical sites:
- `SaveService.kt` — save path
- `SaveStore.kt` or `GameViewModel.kt` — load path (apply SaveData to GameUiState)

- [ ] **Step 2: Add sceneSummaries in the save path**

In the `SaveData(...)` construction, add the parameter:

```kotlin
            sceneSummaries = state.sceneSummaries
```

- [ ] **Step 3: Add sceneSummaries in the load path**

Wherever `SaveData` is turned back into `GameUiState` (likely in `GameViewModel.applyLoadedSave(...)` or similar), add:

```kotlin
                sceneSummaries = save.sceneSummaries
```

- [ ] **Step 4: Write a save/load roundtrip test**

If `SaveServiceTest.kt` already exists, add a test case. Otherwise add one to whichever test file covers save roundtrip:

```kotlin
    @Test
    fun `sceneSummaries roundtrip via save-load`() {
        val state = GameStateFixture.default().copy(
            sceneSummaries = listOf(
                com.realmsoffate.game.data.SceneSummary(
                    turnStart = 1, turnEnd = 5,
                    sceneName = "s", locationName = "L",
                    summary = "Test summary", keyFacts = listOf("A fact")
                )
            )
        )
        val saveData = GameStateFixture.toSaveData(state)
        val restored = GameStateFixture.fromSaveData(saveData)
        assertEquals(state.sceneSummaries, restored.sceneSummaries)
    }
```

(If `toSaveData` / `fromSaveData` helpers don't exist in the fixture, add them as thin wrappers calling whatever production methods perform the conversion.)

- [ ] **Step 5: Run the test**

Run: `gradle :app:testDebugUnitTest --tests "*SaveServiceTest*"` (or the relevant test class)

Expected: PASS, including the new roundtrip.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/SaveStore.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/SaveServiceTest.kt
git commit -m "feat: persist sceneSummaries in save/load pipeline"
```

---

### Task 11: Deploy + runtime verification

**Files:** none (deploy + observe)

This task follows `.cursor/rules/debug-bridge-test-procedures.mdc` P0+P1. Given the user-flow changes, we also run P2 (scene transitions) and P5 (save/load).

- [ ] **Step 1: Clean build**

Run: `gradle clean assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Deploy**

Run:

```bash
gradle installDebug && \
  adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && \
  adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Expected: app launches on emulator.

- [ ] **Step 3: P0 — boot sanity**

- Start new game, pick any race/class.
- Observe first narration appears without crash.

- [ ] **Step 4: P1 — one free-action turn**

- Enter "look around", submit.
- Confirm AI responds normally (no regression from windowing change).

- [ ] **Step 5: Scene-boundary spot check**

- Play 3-5 turns in one location.
- Travel to a new location.
- Query the debug bridge for state:
  ```bash
  curl -s http://localhost:8735/describe | jq '.sceneSummaries'
  ```
- Expected: within ~15s of travel, `sceneSummaries` has at least one entry with `summary` populated and `turnEnd` = turn count just before travel.

- [ ] **Step 6: Save/load roundtrip**

- Create a save slot.
- Reload from the slot.
- Query `/describe` again — `sceneSummaries` should be preserved.

- [ ] **Step 7: Long-session check (quick)**

- Play ~15 turns with two location changes.
- Confirm `/describe` shows multiple summaries.
- Trigger a new turn and inspect the outgoing prompt (debug bridge `/lastPrompt` endpoint if available, or check logcat):
  ```bash
  adb logcat | grep -i "prompt"
  ```
- Expected: the user message contains a `STORY SO FAR` block and scene-relevance-filtered `KNOWN NPCs` list.

- [ ] **Step 8: Commit verification notes**

(No code to commit; this step is a checklist confirmation. If any step fails, treat as a bug and fix before declaring Phase 1 complete.)

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Rolling summary layer → Tasks 2, 5, 6, 7, 10
- ✅ Enriched LogNpc scene-relevance filter → Task 9
- ✅ Token-budget sliding window → Task 3
- ✅ Persistence of summaries → Task 10
- ✅ Runtime verification → Task 11

**Placeholder scan:**
- No TBDs, TODOs, or "fill in later" references.
- All code blocks are complete and directly paste-able.
- Task 8 has a fallback path (top-level `renderSceneSummariesBlock`) if the harness approach proves awkward — prefer the fallback.
- Task 10 acknowledges the actual save/load wiring may live in multiple files and requires a grep step.

**Type consistency:**
- `SceneSummary` fields consistent across Tasks 2, 6, 7, 8.
- `SceneBoundaryDetector.Snapshot` field names (`sceneTag`, `locationName`, `inCombat`) consistent Task 4 ↔ Task 7.
- `TokenEstimate.ofMessage` / `ofText` / `sumMessages` signatures consistent Tasks 1, 3, 8.
- `filterRelevantNpcs` parameters consistent Task 9 test ↔ impl.

**Known risks to mitigate during execution:**
1. Task 7 inserts code mid-function in `GameViewModel.dispatchToAi`. Line numbers are a moving target — prefer searching for the `withHistory` and `_ui.value` anchor strings rather than trusting line numbers.
2. Task 10 requires grep-driven discovery; don't bypass it.
3. The summarizer runs on `viewModelScope.launch` without a rate limit — if the user rapid-fires 10 location changes, 10 summary jobs run. For Phase 1 this is acceptable (each is independent, last-writer-wins is fine). Flag this for Phase 2 if it becomes a problem.

---

## Phase 2 / 3 preview (not in scope here)

- **Phase 2 plan** will cover Room DB migration for NPCs, quests, locations, factions, inventory, combat state, status effects, shop inventory + rewrite reducers to write authoritative state. This is what fixes the combat/shop/status bugs.
- **Phase 3 plan** will cover hierarchical summary compression (scene → arc → era), vector-retrieval over past turns, and long-save optimizations (>1000 turn sessions).
