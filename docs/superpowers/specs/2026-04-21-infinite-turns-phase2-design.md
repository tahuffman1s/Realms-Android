# Infinite-Turn Memory — Phase 2 Design

**Date:** 2026-04-21
**Status:** Approved for implementation planning
**Predecessor:** [Phase 1 plan](../plans/2026-04-21-infinite-turns-phase1.md) (merged in #3)

## Goal

Deliver genuinely infinite-turn sessions for Realms of Fate Android: narrative state (NPCs, quests, factions, locations, lore) must remain **durable, canonical, and recallable** no matter how many turns have been played — without the AI forgetting entities or hallucinating contradictions.

## Driver

Two pain points from play-testing after Phase 1:

1. **Entity amnesia.** NPCs, quests, and factions get forgotten or renamed after many turns. Phase 1's scene-relevance filter keeps recent NPCs in the prompt but cannot surface an NPC last seen hundreds of turns ago when the player references them.
2. **World drift.** The AI silently mutates established facts — a town's ruler, a faction's disposition, a quest's status — because nothing in the prompt asserts these as ground truth.

Ranked pain (user): entity amnesia > world drift > numeric drift (HP/gold) > combat/shop rules.

## Non-goals (deferred to Phase 3)

- Character / inventory / gold moved to Room.
- Authoritative reducers for combat, shop, status effects.
- Vector / semantic retrieval (e.g. "that wizard I met in the swamp").
- Arc → era compression (multi-era playthroughs > ~5,000 turns).
- Full-text-search indexing on summaries.

Phase 2 handles **narrative entities only** (NPCs, quests, factions, locations, lore NPCs, summaries). Character/combat/shop stay in-memory with the existing JSON save path.

---

## Architecture

Three layers:

```
┌─ Compose UI ────────────────────────────────────────────┐
│  collectAsState on Flow<List<…>> for journal,          │
│  quest log, factions panel, map overlay.               │
└────────────────────────┬────────────────────────────────┘
                         │ Flow (reactive)
┌─ EntityRepository ──────────────────────────────────────┐
│  Single gateway to Room.                               │
│  - Flow<List<T>> queries for UI                        │
│  - suspend queries for prompt builder                  │
│  - suspend applyChanges(EntityChanges) for writes      │
│  - seed/export for save migration                      │
└────────────────────────┬────────────────────────────────┘
                         │ DAO calls (suspend)
┌─ Room DB (realms.db) ───────────────────────────────────┐
│  Tables: npc, quest, faction, location,                │
│          scene_summary, arc_summary                    │
│  Indexes tuned for scene-relevance + keyword queries.  │
└─────────────────────────────────────────────────────────┘

GameViewModel
├─ Builds prompt: pulls scene-relevant + keyword-matched
│  entities from repo → CANONICAL FACTS block.
├─ After AI response: runs pure reducers → EntityChanges
│  → repo.applyChanges() in one transaction.
└─ SceneSummarizer: appends scene summaries; periodically
   rolls oldest K into an arc_summary row.
```

### Invariants

- **Room is the single source of truth** for narrative entities.
- **UI observes Room directly** via Flow — no dual storage in `GameUiState`.
- **Reducers remain pure.** They take a snapshot, return `EntityChanges`. No DAO calls inside reducers.
- **Writes flow one direction: reducer → repo → Room → Flow → UI.** UI never mutates Room directly.
- **Transactions wrap each turn's changes** — partial failure rolls back cleanly.

### What stays out of Room

- `Character` (stats, inventory, gold, spells, conditions, feats)
- `CombatState`, shop stocks, buyback lists
- `WorldMap` geometry (terrain, rivers, roads, lakes) — static after world-gen
- `PlayerPos`, `currentLoc` (int id)
- Party companions (small, transient — in-memory list fine)
- `displayMessages`, `timeline`, `debugLog`
- World-gen ephemera: `worldEvents`, `mutations`, `rumors`, `worldName`, `era`

These continue to live in `GameUiState` and persist via the JSON sidecar (see §Save Migration).

---

## Data Model

### `npc` — merged LoreNpc + LogNpc

Lore NPCs and player-logged NPCs share one table. `discovery` column distinguishes state (`lore` → player has not met; `met` → recorded in journal; `dead` / `missing` — terminal states).

```sql
CREATE TABLE npc (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  name_tokens TEXT NOT NULL,       -- lowercased, space-separated; for LIKE queries
  race TEXT,
  role TEXT,
  age TEXT,
  appearance TEXT,
  personality TEXT,
  faction TEXT,                    -- references faction.id (soft)
  home_location TEXT,              -- lore-canonical home
  discovery TEXT NOT NULL DEFAULT 'lore',
  relationship TEXT,               -- null until discovery != 'lore'
  thoughts TEXT,
  last_location TEXT,
  met_turn INTEGER,
  last_seen_turn INTEGER,
  dialogue_history TEXT,           -- JSON array of strings
  memorable_quotes TEXT,           -- JSON array
  relationship_note TEXT,
  status TEXT DEFAULT 'alive'
);
CREATE INDEX idx_npc_discovery     ON npc(discovery);
CREATE INDEX idx_npc_last_seen     ON npc(last_seen_turn DESC);
CREATE INDEX idx_npc_last_location ON npc(last_location);
CREATE INDEX idx_npc_name_tokens   ON npc(name_tokens);
```

### `quest`

```sql
CREATE TABLE quest (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  type TEXT DEFAULT 'side',
  desc TEXT,
  giver TEXT,
  location TEXT,
  objectives TEXT,                 -- JSON array
  completed TEXT,                  -- JSON bool array parallel to objectives
  reward TEXT,
  status TEXT DEFAULT 'active',
  turn_started INTEGER,
  turn_completed INTEGER
);
CREATE INDEX idx_quest_status ON quest(status);
```

### `faction`

`government` and `economy` are nested structs — preserved as JSON blobs rather than flattened into columns.

```sql
CREATE TABLE faction (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  type TEXT,
  description TEXT,
  base_loc TEXT,
  color TEXT,
  government_json TEXT,            -- serialized GovernmentInfo
  economy_json TEXT,               -- serialized EconomyInfo
  population TEXT,
  mood TEXT,
  disposition TEXT,
  goal TEXT,
  ruler TEXT,
  status TEXT DEFAULT 'active'
);
```

### `location`

```sql
CREATE TABLE location (
  id INTEGER PRIMARY KEY,          -- matches MapLocation.id
  name TEXT NOT NULL,
  type TEXT,
  icon TEXT,
  x INTEGER,
  y INTEGER,
  discovered INTEGER DEFAULT 0     -- boolean
);
```

### `scene_summary` + `arc_summary`

```sql
CREATE TABLE scene_summary (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  turn_start INTEGER NOT NULL,
  turn_end INTEGER NOT NULL,
  location TEXT,
  summary TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  arc_id INTEGER                   -- null = not yet rolled up
);
CREATE INDEX idx_scene_turn_end ON scene_summary(turn_end DESC);
CREATE INDEX idx_scene_arc_id   ON scene_summary(arc_id);

CREATE TABLE arc_summary (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  turn_start INTEGER NOT NULL,
  turn_end INTEGER NOT NULL,
  summary TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_arc_turn_end ON arc_summary(turn_end DESC);
```

Rolled-up scene rows are retained (for debug / future vector retrieval), just flagged with `arc_id` which excludes them from prompt injection.

---

## Repository API

`EntityRepository` is the only entry point to Room. Domain code does not touch DAOs directly.

### Observable reads (UI)

```kotlin
fun observeLoggedNpcs(): Flow<List<LogNpc>>      // discovery IN ('met','dead','missing',...)
fun observeLoreNpcs(): Flow<List<LoreNpc>>       // discovery = 'lore'
fun observeActiveQuests(): Flow<List<Quest>>
fun observeFactions(): Flow<List<Faction>>
fun observeLocations(): Flow<List<MapLocation>>
```

UI panels (journal, quest log, lore, factions, map) switch from reading `ui.value.*` lists to `collectAsState` on these flows.

### On-demand reads (prompt builder)

```kotlin
suspend fun sceneRelevantNpcs(
    location: String,
    currentTurn: Int,
    withinTurns: Int = 10
): List<LogNpc>

suspend fun keywordMatchedEntities(
    tokens: List<String>,
    limit: Int = 15
): KeywordHits
// data class KeywordHits(
//     val npcs: List<LogNpc>,
//     val factions: List<Faction>,
//     val locations: List<MapLocation>
// )

suspend fun recentSceneSummaries(limit: Int = 20): List<SceneSummary>   // WHERE arc_id IS NULL
suspend fun allArcSummaries(): List<ArcSummary>

suspend fun canonicalFactsFor(
    npcIds: Set<String>,
    factionIds: Set<String>,
    locationIds: Set<Int>
): CanonicalFacts

suspend fun snapshotForReducers(): EntitySnapshot   // one-shot read of lists needed for reducer input
```

### Writes

```kotlin
suspend fun applyChanges(changes: EntityChanges)   // single Room transaction
suspend fun appendSceneSummary(s: SceneSummary): Long
suspend fun rollupScenes(sceneIds: List<Long>, arc: ArcSummary)   // transactional
```

### Lifecycle

```kotlin
suspend fun seedFromSaveData(save: SaveData)       // one-time v2 → v3 migration
suspend fun exportToSaveData(): SaveData           // JSON export (share/backup)
suspend fun clear()                                 // new game
```

### `EntityChanges` — reducer output type

```kotlin
data class EntityChanges(
    val npcs: List<NpcChange> = emptyList(),
    val quests: List<QuestChange> = emptyList(),
    val factions: List<FactionChange> = emptyList(),
    val locations: List<LocationChange> = emptyList()
)

sealed class NpcChange {
    data class Insert(val npc: LogNpc) : NpcChange()
    data class Update(val id: String, val patch: NpcPatch) : NpcChange()
    data class MarkDead(val id: String, val turn: Int) : NpcChange()
}

data class NpcPatch(
    val lastLocation: String? = null,
    val lastSeenTurn: Int? = null,
    val relationship: String? = null,
    val thoughts: String? = null,
    val appendDialogue: List<String> = emptyList(),
    val appendMemorableQuote: String? = null,
    val relationshipNote: String? = null,
    val status: String? = null
)
```

`QuestChange`, `FactionChange`, `LocationChange` follow the same `Insert` / `Update(id, patch)` / state-transition pattern.

---

## Reducer Contract Changes

Existing pure reducers keep their signatures but emit `EntityChanges` fragments instead of full lists.

### `NpcLogReducer` (largest change)

**Before (phase 1):**
```kotlin
fun apply(
    npcLog: List<LogNpc>,
    combat: CombatState?,
    parsed: ParsedReply,
    currentTurn: Int,
    currentLocName: String
): NpcLogApplyResult    // .npcLog: List<LogNpc>
```

**After (phase 2):**
```kotlin
fun apply(
    currentNpcs: List<LogNpc>,    // snapshot for merge logic
    combat: CombatState?,
    parsed: ParsedReply,
    currentTurn: Int,
    currentLocName: String
): NpcLogApplyResult    // .npcChanges: List<NpcChange>
```

Merge logic becomes: on each `npcsMet` entry, look up existing row by id / nameKey; if present → emit `NpcChange.Update(id, patch)`; else → `NpcChange.Insert(LogNpc(...))`. Death lines emit `NpcChange.MarkDead`.

### `QuestAndPartyReducer`

Party stays as in-memory list (not in Room, per non-goals). Quest mutations emit `QuestChange.Insert / Update / Complete`.

### `WorldReducer`

New reducer-family output: emits `FactionChange` (ruler/disposition/status updates) and `LocationChange` (discovered flags, visited markers). Existing world-mutation logic migrates here.

### `CombatReducer`, `CharacterReducer`

Unchanged — combat & character are in-memory in phase 2.

### VM glue (inside `GameViewModel.applyParsed`)

```kotlin
val snapshot = repo.snapshotForReducers()

val npcResult = NpcLogReducer.apply(snapshot.npcs, combat, parsed, turn, loc)
val questResult = QuestAndPartyReducer.apply(snapshot.quests, party, parsed, turn)
val worldResult = WorldReducer.apply(snapshot.factions, snapshot.locations, parsed, turn)

val changes = EntityChanges(
    npcs = npcResult.npcChanges,
    quests = questResult.questChanges,
    factions = worldResult.factionChanges,
    locations = worldResult.locationChanges,
)

repo.applyChanges(changes)    // one transaction
```

### Reducer tests

Existing tests update from asserting final list shape to asserting emitted `Changes` shape. Same coverage; different assertion style. No Room required.

---

## Prompt Builder Changes

### Block ordering in user message

```
ARC SUMMARIES         ← new, Phase 2
SCENE SUMMARIES       ← Phase 1, now repo-backed
KNOWN NPCs (compact)  ← Phase 1, now repo-backed
CANONICAL FACTS       ← new, Phase 2
PLAYER ACTION: ...
```

### CANONICAL FACTS block

Injects ground-truth records for every entity relevant this turn. Example rendering:

```
# CANONICAL FACTS (ground truth — do not contradict)

## NPCs
- Vesper Vance (human sorcerer, faction=Obsidian Court, status=alive,
  relationship=allied, last seen turn 847 at Silent Swamp; thoughts:
  "Will help the player only if paid in arcane lore.")
- Mira Cole (...)

## Factions
- Obsidian Court (empire, ruler: Queen Elenna, disposition: hostile to
  player, goal: subjugate the western reach)

## Locations
- Silent Swamp (marsh, discovered, controlling faction: Obsidian Court)
```

### Selection algorithm

Assemble a deduped set prioritised in this order, serialise compactly, truncate at token budget (dropping lowest priority first):

1. **Scene-relevant NPCs** (`sceneRelevantNpcs(currentLoc, turn)`).
2. **Keyword-matched entities** — token extraction over `playerInput` + previous narration → `keywordMatchedEntities(tokens)`.
3. **Party companions** (always).
4. **Active quest givers and targets** (always).

### Keyword extraction

```kotlin
fun extractTokens(text: String): List<String> {
    val stopwords = setOf("the", "and", "but", ...)   // ~100 common English
    return text.lowercase()
        .split(Regex("[^a-z]+"))
        .filter { it.length >= 3 && it !in stopwords }
        .distinct()
}
```

Token set built from this turn's `playerInput` plus last turn's AI narration. Typically 5-15 tokens. SQL query: `WHERE name_tokens LIKE '%token%'` per token, UNION, LIMIT 15.

### System prompt addition

```
The CANONICAL FACTS block is ground truth. When you mention any named NPC,
faction, or location from that block, use the facts exactly — do not
change names, factions, dispositions, or statuses. To change a fact,
emit the appropriate update tag ([NPC_UPDATE:...], [FACTION_UPDATE:...])
and describe the in-fiction event that caused the change.
```

### Tag parser tightening (anti-drift assist)

Post-parse scan of AI narration: regex for capitalised proper-noun patterns; for any name not matched to an existing NPC and not a common English word, auto-synthesise a minimal `NpcChange.Insert` with `discovery='met'` and default fields. Prevents silent creation of un-canonical ghost NPCs that later get contradicted.

### Token budgets (tunable in `Prompts.kt`)

| Block | Tokens |
|---|---|
| Arc summaries | 1500 |
| Scene summaries (unrolled) | 2000 |
| Known NPCs (phase 1 compact list) | 600 |
| CANONICAL FACTS | 800 |
| Recent turns | 6000 |

Summaries + NPCs + CANONICAL FACTS ≈ 4,900 tokens of context frame; recent turns add up to 6,000; ~11K total — fits comfortably within DeepSeek's 128K window with headroom for future growth.

---

## Hierarchical Summary Compression

### Trigger

After each scene-summary append, the summariser checks whether rollup should fire:

```kotlin
suspend fun maybeRollupArcs() {
    val unrolled = repo.countScenesWithoutArc()
    if (unrolled >= ROLLUP_THRESHOLD) {               // e.g. 20
        val batch = repo.recentSceneSummaries(limit = Int.MAX_VALUE)
            .filter { it.arcId == null }
            .sortedBy { it.turnEnd }
            .take(ROLLUP_BATCH_SIZE)                  // e.g. 10
        rollupBatch(batch)
    }
}
```

Constants (`Prompts.kt`):

- `ROLLUP_THRESHOLD = 20` — unrolled scenes required before first rollup.
- `ROLLUP_BATCH_SIZE = 10` — scenes per arc.

### Arc summariser

Reuses `SceneSummarizer`'s AI plumbing with a distinct system prompt `ARC_SUMMARY_SYS`:

```
You are compressing a sequence of scene summaries into a single arc
summary. Preserve: named characters and their fates, major decisions
the player made, faction shifts, unresolved plot threads, key locations
visited. Omit minor dialogue, transient scenery, weather. Target ~300
tokens. Output JSON: {"summary": "..."}.
```

### Transaction

```kotlin
suspend fun rollupScenes(sceneIds: List<Long>, arc: ArcSummary) {
    db.withTransaction {
        val arcId = arcDao.insert(arc)
        sceneDao.assignArcId(sceneIds, arcId)
    }
}
```

---

## Save Migration

### v3 format

`SaveData.version` 2 → 3. A v3 save slot is a single **`.rofsave` zip file** containing:

- `save.json` — non-Room state (Character, WorldMap, combat, party, currentLoc, playerPos, etc.)
- `realms.db` (+ `-shm`, `-wal`) — Room entities, scene/arc summaries.
- `manifest.json` — version, savedAt, character name (for slot listing without unzipping).

Slot directory layout:
```
saves/
  slot_1.rofsave       (zip)
  slot_2.rofsave
  autosave.rofsave
```

`SaveStore` handles zip read/write transparently; callers see the same slot API.

### First-run migration from v2

On load:

1. If slot is a legacy v2 `.json` (no `.rofsave`) → detect and migrate.
2. Parse JSON with the existing v2 parser.
3. Back up original to `slot_N.v2.bak.json` (retained 30 days).
4. Create fresh Room DB in a temp directory.
5. Call `repo.seedFromSaveData(save)`:
   - Dedupe-merge `worldLore.npcs` (LoreNpc) + `npcLog` (LogNpc) by `IdGen.nameKey` → one `npc` row each. LogNpc state wins on overlap.
   - Insert factions, locations, quests.
   - Insert `scene_summary` rows from `save.sceneSummaries` (phase 1 field).
6. Package non-Room state + DB into a `.rofsave` zip, write atomically.
7. Delete original `.json`.

Silent, one-time per legacy slot.

### Export / import

- Export: `repo.exportToSaveData()` reassembles a `SaveData`-shape JSON; zipped into `.rofsave` for sharing.
- Import: unzip, validate manifest, `seedFromSaveData`.

### Rollback safety

`v2.bak.json` retained for 30 days lets users downgrade if needed. Entries older than that are pruned on next app start.

---

## Error Handling

### Room write failures

`applyChanges` runs in a single transaction — partial failure rolls back the whole turn's entity diff.

- Log the exception to the debug bridge (`/errors`).
- Toast user: "Memory update failed, continuing."
- VM caches the failed `EntityChanges` and retries before next turn's changes are applied.
- After 3 consecutive retry failures: durable banner — "Game state may be inconsistent — save and reload recommended."

### Boot-time DB corruption

Room integrity check on open. On failure, offer "Restore from v2 backup" UI option; fall back to the legacy JSON save if present.

### Summariser / rollup failures

Reuse phase 1's last-writer-wins pattern. Failed arc rollup leaves `arc_id` NULL on the batch; next rollup attempt retries.

### Keyword retrieval failures

Non-critical. Skip the keyword block for that turn; scene-relevant + canonical facts still function.

---

## Testing

### JVM unit tests (no device, no Room)

- **Reducer tests** — `NpcLogReducerTest`, `QuestAndPartyReducerTest`, `WorldReducerTest` updated to assert `EntityChanges` output.
- **`NpcPatch` merge** — pure `LogNpc + NpcPatch → LogNpc` helper, deterministic, no DB.
- **Keyword tokenizer** — stop-word filtering, min length, case folding. `PromptKeywordsTest`.
- **Arc rollup trigger** — unrolled-count threshold logic. `ArcRollupTriggerTest`.

### Robolectric tests (in-memory Room)

- **`EntityRepositoryTest`** — Flow emissions on upsert/delete; transactional rollback on partial-failure; `applyChanges` atomicity.
- **`NpcQueryTest`** — scene-relevance query filter correctness.
- **`KeywordQueryTest`** — token list → matched NPCs/factions/locations expected-subset.
- **`MigrationTest`** — v2 `SaveData` → `seedFromSaveData` → row counts + NPC dedup.

### Runtime verification on emulator

Follow `.cursor/rules/debug-bridge-test-procedures.mdc` P0 + P1 baseline, plus targeted phase-2 procedures:

- **P2 (scene transitions)** — scene summaries written to Room.
- **P5 (save/load)** — close app → reopen → state restored from `.db` inside `.rofsave`.
- **P10 (new — keyword retrieval)** — meet NPC "Vesper" at turn 5; play 50 turns without mentioning her; ask "what happened to Vesper?"; `/lastPrompt` shows Vesper in CANONICAL FACTS.
- **P11 (new — arc rollup)** — play 20+ scene transitions; `/state` shows `arcSummaries` non-empty and older `sceneSummaries` have `arcId` populated.
- **P12 (new — canonical anti-drift)** — meet an NPC, confirm faction in journal; play 30 turns; `/lastPrompt` shows CANONICAL FACTS matching stored state; narration does not contradict over next 5 turns.

### Debug bridge endpoints (added)

- `GET /state` — extended with `arcSummaries` and `canonicalFacts` (what would be injected next turn).
- `GET /lastPrompt` — full user message last sent to AI; CANONICAL FACTS block visible.
- `GET /repo/npcs?q=<token>` — repository keyword-query debug view.
- `GET /repo/stats` — counts per table, plus rolled-up vs unrolled scene counts.

---

## Files Impacted (preview for planning)

**Create:**
- `app/src/main/kotlin/com/realmsoffate/game/data/db/RealmsDb.kt` — Room `@Database`.
- `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/` — `NpcEntity.kt`, `QuestEntity.kt`, `FactionEntity.kt`, `LocationEntity.kt`, `SceneSummaryEntity.kt`, `ArcSummaryEntity.kt`.
- `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/` — one DAO per entity.
- `app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt` (+ implementation `RoomEntityRepository.kt`).
- `app/src/main/kotlin/com/realmsoffate/game/data/EntityChanges.kt` — change/patch types.
- `app/src/main/kotlin/com/realmsoffate/game/data/CanonicalFacts.kt` — fact block data + renderer.
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/WorldReducer.kt` — if not already split out.
- `app/src/main/kotlin/com/realmsoffate/game/data/ArcSummarizer.kt` — reuses SceneSummarizer plumbing.
- `app/src/main/kotlin/com/realmsoffate/game/util/PromptKeywords.kt` — tokeniser.
- `app/src/main/kotlin/com/realmsoffate/game/data/SaveRofZip.kt` — zip packaging for `.rofsave`.
- Tests mirroring the above under `app/src/test/`.

**Modify:**
- `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` — remove narrative lists from `GameUiState` / `SaveData` (keep JSON-compat fields deprecated for migration).
- `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — `ARC_SUMMARY_SYS`, `CANONICAL_FACTS` directive, new budget constants.
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — VM glue for `snapshotForReducers` + `applyChanges`; prompt-builder wiring for CANONICAL FACTS + keyword retrieval; UI readers switched to repo flows.
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt`, `QuestAndPartyReducer.kt` — emit `EntityChanges`.
- `app/src/main/kotlin/com/realmsoffate/game/game/SceneSummarizer.kt` — append via repo; call `maybeRollupArcs` after success.
- `app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt` + `data/SaveStore.kt` — v3 `.rofsave` format; v2 → v3 migration.
- UI panels (`ui/panels/...` — NPC Journal, Quest Log, Factions, Lore, Map overlay) — swap `ui.value.X` reads for `repo.observeX().collectAsState()`.
- `app/src/debug/...` — debug bridge `/state`, `/lastPrompt`, `/repo/*` endpoints.
- `app/build.gradle.kts` — add Room dependencies (`androidx.room:room-runtime`, `room-ktx`, `room-compiler` via `ksp`), `androidx.room:room-testing`.

---

## Success Criteria

Phase 2 is done when all of the following hold in runtime verification on the emulator:

1. ✅ 100+-turn sessions survive app kill + reload with no entity loss (P5).
2. ✅ An NPC last seen >30 turns ago reappears in CANONICAL FACTS when the player mentions them by name (P10).
3. ✅ Arc summaries fire automatically past 20 scene boundaries; older scenes rolled up; `/state` reflects it (P11).
4. ✅ Stored faction/ruler/disposition appears verbatim in CANONICAL FACTS and is not contradicted by AI narration over a 5-turn window (P12).
5. ✅ Legacy v2 saves migrate silently to `.rofsave` with no data loss; 30-day rollback backup preserved.
6. ✅ All existing phase-1 JVM + Robolectric tests still pass; new tests (Repository, Migration, Reducer, Tokenizer, Rollup) pass.
7. ✅ `gradle assembleDebug` and `gradle lint` clean.
