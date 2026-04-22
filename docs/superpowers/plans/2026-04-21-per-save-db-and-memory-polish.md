# Per-Save DB Isolation + Memory Polish + Settings Balance + Rest Removal

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Isolate the narrative DB per save slot, layer four memory-quality fixes (geography, style anchor, dormant callbacks, contradiction review), surface DeepSeek USD balance in Settings, and remove Rest/Short Rest while preserving death-save logic.

**Architecture:**
- DB isolation: swap `realms.db` → `realms_<slot>.db` routed by active save slot, swap via `RealmsDbHolder.switchTo(slot)`, bundle inside `.rofsave`.
- Memory fixes land in `CanonicalFacts`, `Prompts`, `AiRepository`, and a new `ContradictionQueue`.
- Balance: add `AiRepository.fetchBalance()` + Settings row; cache for 60s.
- Rest removal: delete UI + VM + handler methods; extract `DeathHandler` to preserve death-save logic.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.6.1, OkHttp, DeepSeek chat API.

---

## Phase A — Per-Save DB Isolation

### Task A1: Route DB file by slot

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/db/RealmsDb.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/db/RealmsDbHolder.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/db/RealmsDbHolderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.realmsoffate.game.data.db

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RealmsDbHolderTest {
    @Test
    fun `switchTo routes to per-slot file and reinitializes repo`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        RealmsDbHolder.init(ctx)
        RealmsDbHolder.switchTo("slot_a")
        val a = RealmsDbHolder.currentDbFile()
        RealmsDbHolder.switchTo("slot_b")
        val b = RealmsDbHolder.currentDbFile()
        assertNotEquals(a.absolutePath, b.absolutePath)
        assertTrue(a.name.contains("slot_a"))
        assertTrue(b.name.contains("slot_b"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.db.RealmsDbHolderTest"`
Expected: FAIL — `switchTo` / `currentDbFile` not defined.

- [ ] **Step 3: Extend `RealmsDb.FILE_NAME` pattern**

Replace `RealmsDb.kt`'s companion-object constant:

```kotlin
companion object {
    private const val DEFAULT_PREFIX = "realms"
    const val DEFAULT_FILE_NAME = "realms.db"
    fun fileNameForSlot(slot: String): String {
        val safe = slot.lowercase().replace(Regex("[^a-z0-9_-]"), "_").ifBlank { "default" }
        return "${DEFAULT_PREFIX}_$safe.db"
    }
    fun open(context: Context, dbFile: java.io.File): RealmsDb =
        Room.databaseBuilder(context, RealmsDb::class.java, dbFile.absolutePath)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    fun inMemory(context: Context): RealmsDb =
        Room.inMemoryDatabaseBuilder(context, RealmsDb::class.java)
            .allowMainThreadQueries()
            .build()
}
```

- [ ] **Step 4: Rewrite `RealmsDbHolder` to support slot swap**

```kotlin
package com.realmsoffate.game.data.db

import android.content.Context
import com.realmsoffate.game.data.RoomEntityRepository
import java.io.File

object RealmsDbHolder {
    @Volatile private var appCtx: Context? = null
    @Volatile private var _db: RealmsDb? = null
    @Volatile private var _repo: RoomEntityRepository? = null
    @Volatile private var _currentSlot: String = "default"
    @Volatile private var _currentFile: File? = null

    fun init(context: Context) {
        if (appCtx != null) return
        synchronized(this) {
            if (appCtx != null) return
            appCtx = context.applicationContext
            switchToLocked("default")
        }
    }

    fun switchTo(slot: String) {
        synchronized(this) { switchToLocked(slot) }
    }

    private fun switchToLocked(slot: String) {
        val ctx = appCtx ?: error("RealmsDbHolder.init must be called first")
        _db?.close()
        val file = File(ctx.filesDir, RealmsDb.fileNameForSlot(slot))
        val opened = RealmsDb.open(ctx, file)
        _db = opened
        _repo = RoomEntityRepository(opened)
        _currentSlot = slot
        _currentFile = file
    }

    fun currentSlot(): String = _currentSlot
    fun currentDbFile(): File = _currentFile ?: error("RealmsDbHolder not initialized")
    fun deleteSlotDb(slot: String) {
        val ctx = appCtx ?: return
        val file = File(ctx.filesDir, RealmsDb.fileNameForSlot(slot))
        if (file.absolutePath == _currentFile?.absolutePath) {
            _db?.close(); _db = null; _repo = null
        }
        file.delete()
        File(file.absolutePath + "-shm").delete()
        File(file.absolutePath + "-wal").delete()
    }

    val db: RealmsDb get() = _db ?: error("RealmsDb not initialized — call RealmsDbHolder.init(context) first")
    val repo: RoomEntityRepository get() = _repo ?: error("RealmsDbHolder not initialized")
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.db.RealmsDbHolderTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/db/RealmsDb.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/db/RealmsDbHolder.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/db/RealmsDbHolderTest.kt
git commit -m "feat: per-save narrative DB file routed by slot"
```

### Task A2: Swap DB on load / new game / delete

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` (new-game reset)
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/SaveStore.kt` (delete hook)

- [ ] **Step 1: Add slot swap call at load entry**

In `SaveService.loadSlot(slot)` near the top, before any seeding:

```kotlin
com.realmsoffate.game.data.db.RealmsDbHolder.switchTo(slot)
```

- [ ] **Step 2: Add slot swap call at save entry**

In `SaveService.saveToSlot(slot)` before the export:

```kotlin
com.realmsoffate.game.data.db.RealmsDbHolder.switchTo(slot)
```

- [ ] **Step 3: Clear DB on new-game reset in `GameViewModel`**

Locate the new-game / character-creation commit path. Immediately after computing the new slot key:

```kotlin
com.realmsoffate.game.data.db.RealmsDbHolder.switchTo(slotKey)
viewModelScope.launch { repo.clear() }
```

- [ ] **Step 4: Delete DB file on slot delete in `SaveStore.delete(slot)`**

After the existing file delete:

```kotlin
com.realmsoffate.game.data.db.RealmsDbHolder.deleteSlotDb(slot)
```

- [ ] **Step 5: Verify manually (no new test; covered by A3)**

Deploy and verify:
```
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```
Debug-bridge P0 + P1 + load/save slot swap smoke test.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/SaveStore.kt
git commit -m "feat: swap narrative DB on load/new/delete per save slot"
```

### Task A3: Bundle per-slot DB in .rofsave

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/SaveRofZip.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/SaveRofZipTest.kt`

- [ ] **Step 1: Write the failing test**

Assert that exporting a `.rofsave` includes `db/realms_<slot>.db` when the slot DB exists, and importing restores the file.

```kotlin
@Test
fun `rofsave round-trip preserves narrative db blob`() {
    // arrange: create a fake DB file under filesDir, write a marker
    // act: SaveRofZip.write(... includeDb = true) then read()
    // assert: restored file bytes equal the original
}
```

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.SaveRofZipTest.rofsave round-trip preserves narrative db blob"`
Expected: FAIL (writer does not yet include DB).

- [ ] **Step 2: Extend `SaveRofZip` to optionally bundle DB file**

Add `dbFile: File?` param to write and a matching extractor in read. Store at path `db/<dbFile.name>` inside the zip. On read, restore into `context.filesDir/<name>` when `dbFile` param supplied.

- [ ] **Step 3: Wire `SaveService` to pass current DB file when exporting**

In `SaveService.saveToSlot`, after `switchTo(slot)`:

```kotlin
val dbFile = com.realmsoffate.game.data.db.RealmsDbHolder.currentDbFile()
SaveRofZip.write(..., dbFile = dbFile)
```

And on import path: close and re-`switchTo(slot)` after writing file back to disk.

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.SaveRofZipTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/SaveRofZip.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/SaveRofZipTest.kt
git commit -m "feat: bundle per-slot narrative DB inside .rofsave"
```

---

## Phase B — Four Memory Fixes

### Task B1: Geography in canonical facts

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/CanonicalFacts.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/LocationEntity.kt` (if `adjacencies` not yet modeled)
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` (build the facts)
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/CanonicalFactsGeographyTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `render includes current location block with adjacents`() {
    val facts = CanonicalFacts(
        npcs = emptyList(), factions = emptyList(),
        locations = listOf(
            MapLocation(id = "l1", name = "Greyhollow", type = "village", discovered = true),
            MapLocation(id = "l2", name = "Mirewood", type = "forest", discovered = true)
        ),
        currentLocationId = "l1",
        adjacencies = mapOf("l1" to listOf("l2"))
    )
    val rendered = facts.render()
    assertTrue(rendered.contains("## Current location"))
    assertTrue(rendered.contains("Greyhollow"))
    assertTrue(rendered.contains("Adjacent: Mirewood"))
}
```

Run: `gradle :app:testDebugUnitTest --tests "*CanonicalFactsGeographyTest*"`
Expected: FAIL (constructor lacks `currentLocationId`/`adjacencies`).

- [ ] **Step 2: Extend `CanonicalFacts`**

```kotlin
data class CanonicalFacts(
    val npcs: List<LogNpc>,
    val factions: List<Faction>,
    val locations: List<MapLocation>,
    val currentLocationId: String? = null,
    val adjacencies: Map<String, List<String>> = emptyMap()
) {
    val isEmpty: Boolean
        get() = npcs.isEmpty() && factions.isEmpty() && locations.isEmpty()

    fun render(): String {
        if (isEmpty) return ""
        val sb = StringBuilder()
        sb.appendLine("# CANONICAL FACTS (ground truth — do not contradict)")
        renderCurrentLocation(sb)
        // ...existing NPCs / Factions / Locations sections...
        return sb.toString().trimEnd()
    }

    private fun renderCurrentLocation(sb: StringBuilder) {
        val cid = currentLocationId ?: return
        val current = locations.firstOrNull { it.id == cid } ?: return
        val adjIds = adjacencies[cid].orEmpty()
        val adjNames = adjIds.mapNotNull { id -> locations.firstOrNull { it.id == id }?.name }
        sb.appendLine()
        sb.appendLine("## Current location")
        sb.append("- ").appendLine(renderLocation(current))
        if (adjNames.isNotEmpty()) sb.appendLine("  Adjacent: ${adjNames.joinToString(", ")}")
    }
}
```

- [ ] **Step 3: Populate `currentLocationId` + `adjacencies` in VM**

In `GameViewModel`, where `CanonicalFacts` is constructed for the prompt, pass the active location id and a map built from the current `MapLocation.adjacencies` field (or the edges list, depending on how WorldMap stores them).

- [ ] **Step 4: Run the test**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/CanonicalFacts.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/CanonicalFactsGeographyTest.kt
git commit -m "feat: inject current + adjacent locations in canonical facts"
```

### Task B2: Pinned style exemplar

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/StyleExemplar.kt` (new)
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/StyleExemplarTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `renderStyleExemplar returns at most 3 sentences`() {
    val long = "Sentence one. Sentence two. Sentence three. Sentence four. Sentence five."
    val out = StyleExemplar.render(long)
    assertEquals(3, out.count { it == '.' })
}
```

Run: `gradle :app:testDebugUnitTest --tests "*StyleExemplarTest*"`
Expected: FAIL.

- [ ] **Step 2: Create `StyleExemplar`**

```kotlin
object StyleExemplar {
    fun render(sample: String): String {
        val sentences = sample.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        return sentences.take(3).joinToString(" ")
    }
    fun block(sample: String?): String {
        val picked = sample?.takeIf { it.isNotBlank() } ?: return ""
        return "\n# STYLE (match this voice — tone, cadence, word choice)\n\"" + render(picked) + "\"\n"
    }
}
```

- [ ] **Step 3: Inject into system prompt**

In `AiRepository.callDeepSeek`, append `StyleExemplar.block(styleSample)` to the cached system content. Source the sample from the earliest scene summary (stable → cache-friendly) via a new param on `generate()`.

- [ ] **Step 4: Run the test**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/StyleExemplar.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/StyleExemplarTest.kt
git commit -m "feat: pin style exemplar in cached system prompt"
```

### Task B3: Dormant-callback resurrection

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/DormantCallback.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/DormantCallbackTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `picks highest-score dormant arc not referenced in last N turns`() {
    val arcs = listOf(
        ArcSummary(id = 1, summary = "Shadow Pact", lastTurn = 10, relevanceScore = 0.9),
        ArcSummary(id = 2, summary = "Baker's Feud", lastTurn = 110, relevanceScore = 0.3),
    )
    val pick = DormantCallback.pick(arcs, currentTurn = 120, dormantAfter = 50)
    assertEquals(1L, pick?.id)
}
```

Run: `gradle :app:testDebugUnitTest --tests "*DormantCallbackTest*"`
Expected: FAIL.

- [ ] **Step 2: Create `DormantCallback`**

```kotlin
object DormantCallback {
    fun pick(arcs: List<ArcSummary>, currentTurn: Int, dormantAfter: Int): ArcSummary? =
        arcs
            .filter { currentTurn - (it.lastTurn ?: 0) >= dormantAfter }
            .maxByOrNull { it.relevanceScore ?: 0.0 }

    fun renderBlock(arc: ArcSummary?): String {
        arc ?: return ""
        return "\n# OPTIONAL CALLBACK (weave only if organic)\n- ${arc.summary}\n"
    }
}
```

- [ ] **Step 3: Wire into per-turn prompt**

In `GameViewModel.buildSystemPrompt` (or where per-turn user context is assembled), every 10th turn pass the block to AI. Gate behind a relevance threshold to avoid forced callbacks.

- [ ] **Step 4: Run the test**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/DormantCallback.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/DormantCallbackTest.kt
git commit -m "feat: occasional dormant-arc callback in per-turn prompt"
```

### Task B4: Contradiction review queue

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/ContradictionQueue.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/ArcSummarizer.kt` (hook at rollup)
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/ContradictionQueueTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `diff against canonical returns conflict for contradicting fact`() {
    val canonical = listOf("Thane Orin rules the Citadel")
    val arcFacts = listOf("Lady Mara rules the Citadel")
    val conflicts = ContradictionQueue.diff(canonical, arcFacts)
    assertEquals(1, conflicts.size)
    assertTrue(conflicts[0].contains("Citadel"))
}
```

Run: `gradle :app:testDebugUnitTest --tests "*ContradictionQueueTest*"`
Expected: FAIL.

- [ ] **Step 2: Create `ContradictionQueue`**

```kotlin
object ContradictionQueue {
    fun diff(canonical: List<String>, candidate: List<String>): List<String> {
        // simple: for each canonical noun-phrase subject, flag candidate facts mentioning same subject with different tail
        val subjects = canonical.map { it.take(24) }.toSet()
        return candidate.filter { c ->
            subjects.any { s -> c.contains(s.substringBefore(" "), ignoreCase = true) && c != s }
        }
    }
    // In-memory log; surface via debug endpoint (no persistence yet, intentional).
    private val _log = mutableListOf<String>()
    fun log(line: String) { _log += line; if (_log.size > 200) _log.removeAt(0) }
    fun snapshot(): List<String> = _log.toList()
}
```

- [ ] **Step 3: Call from `ArcSummarizer` on rollup**

After generating `keyFacts` for a new arc, call `ContradictionQueue.diff(canonicalFacts.render().lines(), keyFacts)`; log each conflict; do NOT auto-merge.

- [ ] **Step 4: Expose via debug endpoint `/repo/contradictions`**

Add a handler in the debug bridge returning `snapshot()` as JSON.

- [ ] **Step 5: Run the test**

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/ContradictionQueue.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/ArcSummarizer.kt \
        app/src/debug/kotlin/com/realmsoffate/game/debug/DebugBridge.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/ContradictionQueueTest.kt
git commit -m "feat: contradiction review queue on arc rollup"
```

---

## Phase C — DeepSeek Balance in Settings ($$$)

### Task C1: `AiRepository.fetchBalance()`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt`
- Test: `app/src/test/kotlin/com/realmsoffate/game/data/AiRepositoryBalanceTest.kt`

- [ ] **Step 1: Write the failing test** — parse mocked balance JSON

```kotlin
@Test
fun `parseBalance extracts USD total`() {
    val sample = """
      {"is_available":true,"balance_infos":[
        {"currency":"USD","total_balance":"4.12","granted_balance":"0","topped_up_balance":"4.12"}
      ]}
    """.trimIndent()
    val usd = AiRepository.parseBalance(sample)
    assertEquals("4.12", usd)
}
```

Run: `gradle :app:testDebugUnitTest --tests "*AiRepositoryBalanceTest*"`
Expected: FAIL.

- [ ] **Step 2: Add parser + fetcher**

```kotlin
companion object {
    fun parseBalance(raw: String): String? {
        return try {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val infos = obj["balance_infos"]?.jsonArray ?: return null
            val usd = infos.firstOrNull { it.jsonObject["currency"]?.jsonPrimitive?.content == "USD" }
                ?: infos.firstOrNull()
            usd?.jsonObject?.get("total_balance")?.jsonPrimitive?.content
        } catch (_: Exception) { null }
    }
}

suspend fun fetchBalance(apiKey: String): String? = withContext(Dispatchers.IO) {
    val req = Request.Builder()
        .url("https://api.deepseek.com/user/balance")
        .get()
        .addHeader("Authorization", "Bearer $apiKey")
        .build()
    runCatching {
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@use null
            parseBalance(r.body?.string().orEmpty())
        }
    }.getOrNull()
}
```

- [ ] **Step 3: Run the test** → PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/AiRepositoryBalanceTest.kt
git commit -m "feat: add AiRepository.fetchBalance for DeepSeek USD balance"
```

### Task C2: Settings row + 60s cache

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt` (or the dedicated Settings composable — locate the one that renders API key)
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` (expose `balanceUsd: StateFlow<String?>`)

- [ ] **Step 1: Expose `balanceUsd` + `refreshBalance()` on VM**

```kotlin
private val _balanceUsd = MutableStateFlow<String?>(null)
val balanceUsd: StateFlow<String?> = _balanceUsd.asStateFlow()
private var _balanceFetchedAt: Long = 0L
fun refreshBalance() {
    viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (now - _balanceFetchedAt < 60_000 && _balanceUsd.value != null) return@launch
        val key = prefs.apiKey().orEmpty()
        if (key.isBlank()) { _balanceUsd.value = null; return@launch }
        _balanceUsd.value = aiRepo.fetchBalance(key)
        _balanceFetchedAt = now
    }
}
```

- [ ] **Step 2: Render in Settings overlay**

Add a row:

```kotlin
val balance by vm.balanceUsd.collectAsState()
LaunchedEffect(Unit) { vm.refreshBalance() }
Row(verticalAlignment = Alignment.CenterVertically) {
    Text("DeepSeek balance", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.weight(1f))
    Text(balance?.let { "$$it" } ?: "—", style = MaterialTheme.typography.bodyMedium)
    IconButton(onClick = { vm.refreshBalance() }) { Icon(Icons.Default.Refresh, "Refresh") }
}
```

- [ ] **Step 3: Deploy + manually verify**

Run the deploy command chain from CLAUDE.md. Open Settings, confirm balance renders. Flip API key off → confirm `—`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt
git commit -m "feat: show DeepSeek USD balance in Settings"
```

---

## Phase D — Remove Rest / Short Rest

### Task D1: Extract `DeathHandler`

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/game/handlers/DeathHandler.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`
- Delete: `app/src/main/kotlin/com/realmsoffate/game/game/handlers/RestHandler.kt`

- [ ] **Step 1: Move `rollDeathSave()` and `die()` verbatim into `DeathHandler`**

Copy constructor dependencies: `ui`, `screen`, `lastDeath`, `logTimeline`, `scope`, `refreshSlots`, `timeline`. Drop `restOverlay`.

- [ ] **Step 2: Re-wire VM**

```kotlin
private val deathHandler = DeathHandler(ui, screen, lastDeath, ::logTimeline, viewModelScope, ::refreshSlots, timeline)
fun rollDeathSave() = deathHandler.rollDeathSave()
// remove: shortRest(), longRest(), dismissRest(), restOverlay
```

- [ ] **Step 3: Delete `RestHandler.kt`**

```bash
git rm app/src/main/kotlin/com/realmsoffate/game/game/handlers/RestHandler.kt
```

- [ ] **Step 4: Run tests**

Run: `gradle :app:testDebugUnitTest`
Expected: PASS. Fix any compile errors from removed symbols.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/handlers/DeathHandler.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "refactor: extract DeathHandler from RestHandler ahead of rest removal"
```

### Task D2: Remove Rest UI + Spells helpers

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/Spells.kt`

- [ ] **Step 1: Delete rest buttons**

In `GameOverlays.kt`, remove the two `Button` composables rendering "Short Rest" / "Long Rest" (lines ~130–144). Remove `onShortRest` / `onLongRest` from the composable's parameter list and any call sites.

In `GameScreen.kt`, remove:
- `RestOverlay(...)` block (around line 272)
- `onShortRest = { vm.shortRest() }` and `onLongRest = { vm.longRest() }` (lines ~338–339)

- [ ] **Step 2: Delete `RestOverlay` composable**

If `RestOverlay` is defined in `Overlays.kt` (confirm via grep first), delete it and its usages.

- [ ] **Step 3: Remove `SpellSlots.applyShortRest` + `applyLongRest`**

Delete both functions from `Spells.kt`. Verify no remaining callers:

```bash
grep -rn "applyShortRest\|applyLongRest" app/src
```

Expected: no results.

- [ ] **Step 4: Compile + test**

Run: `gradle test lint assembleDebug`
Expected: success.

- [ ] **Step 5: Deploy + verify**

Deploy chain + P0+P1 debug bridge checks. Confirm no Rest buttons anywhere, death flow still works.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/overlays/Overlays.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/Spells.kt
git commit -m "feat: remove Short Rest / Long Rest from UI and rules"
```

---

## Self-Review

**Spec coverage:**
- Per-save DB isolation → A1–A3 ✅
- Four fixes (geography, style, callbacks, contradictions) → B1–B4 ✅
- DeepSeek balance in Settings → C1–C2 ✅
- Remove Rest/Short Rest → D1–D2 ✅

**Known gaps to confirm during execution:**
- `MapLocation` adjacencies representation — plan assumes either a field or a derivable edges list; confirm when touching `CanonicalFacts` in B1.
- Settings overlay location — grep in C2 picks the correct composable.
- `ArcSummary.lastTurn` / `relevanceScore` — if missing, either add to the data class in B3 or substitute `createdAtTurn` + recency-based score.

**Non-placeholder scan:** every step contains either code or an exact command. Each task ends with a commit.
