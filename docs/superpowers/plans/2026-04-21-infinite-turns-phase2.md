# Infinite-Turn Memory — Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move narrative entities (NPCs, quests, factions, locations, lore, summaries) into a Room database with a single-gateway repository, inject canonical facts + keyword-matched entities into every prompt, and compress old scenes into arc summaries — so the AI never forgets and never drifts over infinite turns.

**Architecture:** Room is the single source of truth for narrative entities. UI observes `Flow<List<T>>` from `EntityRepository`. Pure reducers emit `EntityChanges` diffs which the repo applies transactionally. The prompt builder pulls scene-relevant + keyword-matched entities into a `CANONICAL FACTS` block. A hierarchical summary compressor rolls oldest scene summaries into arc summaries past a threshold. Save format upgrades to a `.rofsave` zip (`save.json` + `realms.db` + `manifest.json`) with silent v2 → v3 migration.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose, Room 2.6.1 + KSP, Flow/StateFlow, kotlinx.serialization, OkHttp, JUnit4, Robolectric 4.13, DeepSeek API.

**Spec:** [docs/superpowers/specs/2026-04-21-infinite-turns-phase2-design.md](../specs/2026-04-21-infinite-turns-phase2-design.md).

**Non-goals (deferred to Phase 3):** Character/inventory/combat/shop authoritative reducers; vector/semantic retrieval; arc→era compression; full-text-search indexing.

---

## File Structure

**Create (code):**

- `app/src/main/kotlin/com/realmsoffate/game/util/PromptKeywords.kt` — stop-word-filtered token extractor.
- `app/src/main/kotlin/com/realmsoffate/game/data/db/RealmsDb.kt` — Room `@Database` singleton.
- `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/NpcEntity.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/QuestEntity.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/FactionEntity.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/LocationEntity.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/SceneSummaryEntity.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/ArcSummaryEntity.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/NpcDao.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/QuestDao.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/FactionDao.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/LocationDao.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/SceneSummaryDao.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/ArcSummaryDao.kt`
- `app/src/main/kotlin/com/realmsoffate/game/data/db/Mappers.kt` — Entity ↔ domain converters.
- `app/src/main/kotlin/com/realmsoffate/game/data/EntityChanges.kt` — sealed change/patch types.
- `app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt` — interface.
- `app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt` — implementation.
- `app/src/main/kotlin/com/realmsoffate/game/data/CanonicalFacts.kt` — fact data + renderer.
- `app/src/main/kotlin/com/realmsoffate/game/data/ArcSummary.kt` — domain type.
- `app/src/main/kotlin/com/realmsoffate/game/game/ArcSummarizer.kt` — AI call for arc compression.
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/WorldReducer.kt` — if not already split.
- `app/src/main/kotlin/com/realmsoffate/game/data/SaveRofZip.kt` — zip read/write.

**Create (tests):**

- `app/src/test/kotlin/com/realmsoffate/game/util/PromptKeywordsTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/db/MappersTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/RoomEntityRepositoryTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/RepositoryQueriesTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/RepositorySummariesTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/RepositoryMigrationTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/CanonicalFactsTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/ArcSummarizerTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/data/SaveRofZipTest.kt`
- `app/src/test/kotlin/com/realmsoffate/game/game/handlers/SaveServiceV3Test.kt`

**Modify:**

- `app/build.gradle.kts` — add Room + KSP dependencies.
- `build.gradle.kts` — add KSP plugin to top-level.
- `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — `ARC_SUMMARY_SYS`, CANONICAL FACTS directive, budget constants.
- `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` — keep narrative list fields on `SaveData` for v2 migration; mark them `@Deprecated("v2 compat")`.
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — repo glue; prompt-builder additions; UI observers.
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt` — emit `NpcChange`.
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/QuestAndPartyReducer.kt` — emit `QuestChange`.
- `app/src/main/kotlin/com/realmsoffate/game/game/SceneSummarizer.kt` — persist via repo; call `maybeRollupArcs`.
- `app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt` + `app/src/main/kotlin/com/realmsoffate/game/data/SaveStore.kt` — v3 `.rofsave` format + migration.
- UI panels (`app/src/main/kotlin/com/realmsoffate/game/ui/panels/**`) — swap list reads for repo flows.
- `app/src/debug/kotlin/com/realmsoffate/game/debug/**` — add `/lastPrompt`, `/repo/npcs`, `/repo/stats`; extend `/state`.
- Existing reducer test files — update assertions for `EntityChanges`.

---

## Task 1 — Add Room + KSP dependencies

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add KSP plugin to root `build.gradle.kts`**

Open `/mnt/GD2/Backup/Documents/Repositories/RealmsAndroid/build.gradle.kts` and add the KSP line under the existing plugins:

```kotlin
// Top-level build file.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
```

- [ ] **Step 2: Apply KSP + add Room dependencies in `app/build.gradle.kts`**

In the `plugins { ... }` block at the top of `app/build.gradle.kts`, append:

```kotlin
    id("com.google.devtools.ksp")
```

In the `dependencies { ... }` block, append (after the OkHttp block, before `debugImplementation`):

```kotlin
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
```

- [ ] **Step 3: Verify Gradle sync**

Run:
```bash
gradle :app:dependencies --configuration debugImplementation | grep -i room
```
Expected: lines for `androidx.room:room-runtime:2.6.1` and `androidx.room:room-ktx:2.6.1`.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts app/build.gradle.kts
git commit -m "feat: add Room 2.6.1 + KSP deps for phase 2 narrative memory"
```

---

## Task 2 — `PromptKeywords` tokeniser (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/util/PromptKeywords.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/util/PromptKeywordsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/realmsoffate/game/util/PromptKeywordsTest.kt`:

```kotlin
package com.realmsoffate.game.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptKeywordsTest {
    @Test
    fun `empty input yields no tokens`() {
        assertEquals(emptyList<String>(), PromptKeywords.extract(""))
    }

    @Test
    fun `lowercases and splits on non-letters`() {
        val tokens = PromptKeywords.extract("Vesper's house at the Silent-Swamp")
        assertTrue("vesper" in tokens)
        assertTrue("silent" in tokens)
        assertTrue("swamp" in tokens)
    }

    @Test
    fun `drops short tokens under 3 chars`() {
        assertTrue("to" !in PromptKeywords.extract("to the swamp"))
    }

    @Test
    fun `drops common stopwords`() {
        val tokens = PromptKeywords.extract("the king and the court are hostile")
        assertTrue("the" !in tokens)
        assertTrue("and" !in tokens)
        assertTrue("king" in tokens)
        assertTrue("court" in tokens)
    }

    @Test
    fun `dedupes tokens`() {
        val tokens = PromptKeywords.extract("Mira met Mira again")
        assertEquals(1, tokens.count { it == "mira" })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.util.PromptKeywordsTest"`

Expected: FAIL — `PromptKeywords` not defined.

- [ ] **Step 3: Implement**

Create `app/src/main/kotlin/com/realmsoffate/game/util/PromptKeywords.kt`:

```kotlin
package com.realmsoffate.game.util

/**
 * Extract keyword tokens from free-form text for entity retrieval.
 *
 * Lowercased, non-letter-split, deduped, stopword-filtered, min length 3.
 * Used by the prompt builder to find NPCs / factions / locations
 * mentioned in player input or prior narration.
 */
object PromptKeywords {
    private val STOPWORDS = setOf(
        "the","and","but","for","are","was","were","you","your","yours",
        "him","her","his","she","they","their","them","our","ours",
        "this","that","these","those","with","from","into","onto","over","under",
        "than","then","when","where","what","which","who","whom","why","how",
        "will","would","could","should","have","has","had","not","can","cant",
        "now","all","any","some","one","two","too","very","just","still",
        "about","there","here","also","been","being","more","most","much","such",
        "only","even","ever","never","back","down","away","into","each","every"
    )

    fun extract(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z]+"))
            .asSequence()
            .filter { it.length >= 3 && it !in STOPWORDS }
            .distinct()
            .toList()
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.util.PromptKeywordsTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/util/PromptKeywords.kt \
        app/src/test/kotlin/com/realmsoffate/game/util/PromptKeywordsTest.kt
git commit -m "feat: add PromptKeywords tokenizer for keyword retrieval"
```

---

## Task 3 — Room entity classes

**Files:**
- Create:
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/NpcEntity.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/QuestEntity.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/FactionEntity.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/LocationEntity.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/SceneSummaryEntity.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/entities/ArcSummaryEntity.kt`

These are declarative and verified end-to-end when the database compiles. No individual tests here — Task 4's DAO tests cover them.

- [ ] **Step 1: Create `NpcEntity.kt`**

```kotlin
package com.realmsoffate.game.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "npc",
    indices = [
        Index("discovery"),
        Index("last_seen_turn"),
        Index("last_location"),
        Index("name_tokens")
    ]
)
data class NpcEntity(
    @PrimaryKey val id: String,
    val name: String,
    @androidx.room.ColumnInfo(name = "name_tokens") val nameTokens: String,
    val race: String? = null,
    val role: String? = null,
    val age: String? = null,
    val appearance: String? = null,
    val personality: String? = null,
    val faction: String? = null,
    @androidx.room.ColumnInfo(name = "home_location") val homeLocation: String? = null,
    val discovery: String = "lore",
    val relationship: String? = null,
    val thoughts: String? = null,
    @androidx.room.ColumnInfo(name = "last_location") val lastLocation: String? = null,
    @androidx.room.ColumnInfo(name = "met_turn") val metTurn: Int? = null,
    @androidx.room.ColumnInfo(name = "last_seen_turn") val lastSeenTurn: Int? = null,
    @androidx.room.ColumnInfo(name = "dialogue_history") val dialogueHistoryJson: String? = null,
    @androidx.room.ColumnInfo(name = "memorable_quotes") val memorableQuotesJson: String? = null,
    @androidx.room.ColumnInfo(name = "relationship_note") val relationshipNote: String? = null,
    val status: String = "alive"
)
```

- [ ] **Step 2: Create `QuestEntity.kt`**

```kotlin
package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "quest", indices = [Index("status")])
data class QuestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String = "side",
    val desc: String = "",
    val giver: String = "",
    val location: String = "",
    @ColumnInfo(name = "objectives_json") val objectivesJson: String = "[]",
    @ColumnInfo(name = "completed_json") val completedJson: String = "[]",
    val reward: String = "",
    val status: String = "active",
    @ColumnInfo(name = "turn_started") val turnStarted: Int = 0,
    @ColumnInfo(name = "turn_completed") val turnCompleted: Int? = null
)
```

- [ ] **Step 3: Create `FactionEntity.kt`**

```kotlin
package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faction")
data class FactionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String = "",
    val description: String = "",
    @ColumnInfo(name = "base_loc") val baseLoc: String = "",
    val color: String? = null,
    @ColumnInfo(name = "government_json") val governmentJson: String? = null,
    @ColumnInfo(name = "economy_json") val economyJson: String? = null,
    val population: String = "",
    val mood: String = "",
    val disposition: String = "",
    val goal: String = "",
    val ruler: String = "",
    val status: String = "active"
)
```

- [ ] **Step 4: Create `LocationEntity.kt`**

```kotlin
package com.realmsoffate.game.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val type: String,
    val icon: String,
    val x: Int,
    val y: Int,
    val discovered: Int = 0
)
```

- [ ] **Step 5: Create `SceneSummaryEntity.kt`**

```kotlin
package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scene_summary",
    indices = [Index("turn_end"), Index("arc_id")]
)
data class SceneSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "turn_start") val turnStart: Int,
    @ColumnInfo(name = "turn_end") val turnEnd: Int,
    val location: String = "",
    val summary: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "arc_id") val arcId: Long? = null
)
```

- [ ] **Step 6: Create `ArcSummaryEntity.kt`**

```kotlin
package com.realmsoffate.game.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "arc_summary", indices = [Index("turn_end")])
data class ArcSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "turn_start") val turnStart: Int,
    @ColumnInfo(name = "turn_end") val turnEnd: Int,
    val summary: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/db/entities/
git commit -m "feat: add Room entity classes for narrative state tables"
```

---

## Task 4 — DAOs + Room database (first compile target)

**Files:**
- Create:
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/NpcDao.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/QuestDao.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/FactionDao.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/LocationDao.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/SceneSummaryDao.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/dao/ArcSummaryDao.kt`
  - `app/src/main/kotlin/com/realmsoffate/game/data/db/RealmsDb.kt`

- [ ] **Step 1: `NpcDao.kt`**

```kotlin
package com.realmsoffate.game.data.db.dao

import androidx.room.*
import com.realmsoffate.game.data.db.entities.NpcEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NpcDao {
    @Query("SELECT * FROM npc WHERE discovery != 'lore' ORDER BY last_seen_turn DESC")
    fun observeLogged(): Flow<List<NpcEntity>>

    @Query("SELECT * FROM npc WHERE discovery = 'lore' ORDER BY name ASC")
    fun observeLore(): Flow<List<NpcEntity>>

    @Query("SELECT * FROM npc")
    suspend fun getAll(): List<NpcEntity>

    @Query("SELECT * FROM npc WHERE discovery != 'lore'")
    suspend fun getAllLogged(): List<NpcEntity>

    @Query("SELECT * FROM npc WHERE id = :id")
    suspend fun getById(id: String): NpcEntity?

    @Query("""
        SELECT * FROM npc
        WHERE discovery != 'lore'
          AND (
            last_location = :loc
            OR last_seen_turn >= :minTurn
          )
        ORDER BY last_seen_turn DESC
    """)
    suspend fun sceneRelevant(loc: String, minTurn: Int): List<NpcEntity>

    @Query("SELECT * FROM npc WHERE name_tokens LIKE :pattern LIMIT :limit")
    suspend fun matchKeyword(pattern: String, limit: Int): List<NpcEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<NpcEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(row: NpcEntity)

    @Update suspend fun update(row: NpcEntity)

    @Query("DELETE FROM npc") suspend fun clear()
}
```

- [ ] **Step 2: `QuestDao.kt`**

```kotlin
package com.realmsoffate.game.data.db.dao

import androidx.room.*
import com.realmsoffate.game.data.db.entities.QuestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    @Query("SELECT * FROM quest WHERE status = 'active' ORDER BY turn_started DESC")
    fun observeActive(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quest")
    suspend fun getAll(): List<QuestEntity>

    @Query("SELECT * FROM quest WHERE id = :id")
    suspend fun getById(id: String): QuestEntity?

    @Query("SELECT * FROM quest WHERE status = 'active'")
    suspend fun getActive(): List<QuestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<QuestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(row: QuestEntity)

    @Query("DELETE FROM quest") suspend fun clear()
}
```

- [ ] **Step 3: `FactionDao.kt`**

```kotlin
package com.realmsoffate.game.data.db.dao

import androidx.room.*
import com.realmsoffate.game.data.db.entities.FactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FactionDao {
    @Query("SELECT * FROM faction ORDER BY name ASC")
    fun observeAll(): Flow<List<FactionEntity>>

    @Query("SELECT * FROM faction")
    suspend fun getAll(): List<FactionEntity>

    @Query("SELECT * FROM faction WHERE id = :id")
    suspend fun getById(id: String): FactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<FactionEntity>)

    @Query("DELETE FROM faction") suspend fun clear()
}
```

- [ ] **Step 4: `LocationDao.kt`**

```kotlin
package com.realmsoffate.game.data.db.dao

import androidx.room.*
import com.realmsoffate.game.data.db.entities.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM location ORDER BY id ASC")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM location")
    suspend fun getAll(): List<LocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<LocationEntity>)

    @Query("DELETE FROM location") suspend fun clear()
}
```

- [ ] **Step 5: `SceneSummaryDao.kt`**

```kotlin
package com.realmsoffate.game.data.db.dao

import androidx.room.*
import com.realmsoffate.game.data.db.entities.SceneSummaryEntity

@Dao
interface SceneSummaryDao {
    @Query("SELECT * FROM scene_summary WHERE arc_id IS NULL ORDER BY turn_end DESC LIMIT :limit")
    suspend fun recentUnrolled(limit: Int): List<SceneSummaryEntity>

    @Query("SELECT * FROM scene_summary WHERE arc_id IS NULL ORDER BY turn_end ASC")
    suspend fun allUnrolledOldestFirst(): List<SceneSummaryEntity>

    @Query("SELECT COUNT(*) FROM scene_summary WHERE arc_id IS NULL")
    suspend fun countUnrolled(): Int

    @Query("SELECT * FROM scene_summary")
    suspend fun getAll(): List<SceneSummaryEntity>

    @Insert suspend fun insert(row: SceneSummaryEntity): Long

    @Query("UPDATE scene_summary SET arc_id = :arcId WHERE id IN (:ids)")
    suspend fun assignArcId(ids: List<Long>, arcId: Long)

    @Query("DELETE FROM scene_summary") suspend fun clear()
}
```

- [ ] **Step 6: `ArcSummaryDao.kt`**

```kotlin
package com.realmsoffate.game.data.db.dao

import androidx.room.*
import com.realmsoffate.game.data.db.entities.ArcSummaryEntity

@Dao
interface ArcSummaryDao {
    @Query("SELECT * FROM arc_summary ORDER BY turn_end DESC")
    suspend fun allNewestFirst(): List<ArcSummaryEntity>

    @Insert suspend fun insert(row: ArcSummaryEntity): Long

    @Query("DELETE FROM arc_summary") suspend fun clear()
}
```

- [ ] **Step 7: `RealmsDb.kt`**

```kotlin
package com.realmsoffate.game.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.realmsoffate.game.data.db.dao.*
import com.realmsoffate.game.data.db.entities.*

@Database(
    entities = [
        NpcEntity::class,
        QuestEntity::class,
        FactionEntity::class,
        LocationEntity::class,
        SceneSummaryEntity::class,
        ArcSummaryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RealmsDb : RoomDatabase() {
    abstract fun npcDao(): NpcDao
    abstract fun questDao(): QuestDao
    abstract fun factionDao(): FactionDao
    abstract fun locationDao(): LocationDao
    abstract fun sceneSummaryDao(): SceneSummaryDao
    abstract fun arcSummaryDao(): ArcSummaryDao

    companion object {
        const val FILE_NAME = "realms.db"

        fun open(context: Context, dbFile: java.io.File): RealmsDb =
            Room.databaseBuilder(context, RealmsDb::class.java, dbFile.absolutePath)
                .fallbackToDestructiveMigration()
                .build()

        /** In-memory factory for unit tests (Robolectric). */
        fun inMemory(context: Context): RealmsDb =
            Room.inMemoryDatabaseBuilder(context, RealmsDb::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
```

- [ ] **Step 8: Verify compilation**

Run: `gradle :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL. If KSP fails on @Entity issues, re-inspect class annotations.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/db/
git commit -m "feat: add Room DAOs and RealmsDb for narrative entities"
```

---

## Task 5 — Mappers: Entity ↔ domain (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/db/Mappers.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/data/db/MappersTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.realmsoffate.game.data.db

import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.LoreNpc
import com.realmsoffate.game.data.Quest
import com.realmsoffate.game.data.Faction
import com.realmsoffate.game.data.GovernmentInfo
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.data.db.entities.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun `LogNpc roundtrips through NpcEntity`() {
        val npc = LogNpc(
            id = "mira-cole",
            name = "Mira Cole",
            race = "human", role = "innkeeper",
            relationship = "friendly",
            lastLocation = "Silent Swamp",
            metTurn = 3, lastSeenTurn = 12,
            dialogueHistory = mutableListOf("Hello, traveler."),
            memorableQuotes = mutableListOf("T3: \"You'll regret this.\"")
        )
        val entity = Mappers.toEntity(npc)
        val back = Mappers.toLogNpc(entity)!!
        assertEquals(npc.id, back.id)
        assertEquals(npc.name, back.name)
        assertEquals(npc.lastLocation, back.lastLocation)
        assertEquals(npc.dialogueHistory, back.dialogueHistory)
        assertEquals(npc.memorableQuotes, back.memorableQuotes)
    }

    @Test
    fun `LoreNpc roundtrips through NpcEntity`() {
        val lore = LoreNpc(
            id = "old-sage",
            name = "Old Sage",
            race = "elf",
            role = "historian",
            location = "Hightower"
        )
        val entity = Mappers.toEntity(lore)
        val back = Mappers.toLoreNpc(entity)
        assertEquals(lore.id, back.id)
        assertEquals(lore.location, back.location)
    }

    @Test
    fun `toLogNpc returns null when discovery is lore`() {
        val entity = NpcEntity(id = "x", name = "X", nameTokens = "x", discovery = "lore")
        assertNull(Mappers.toLogNpc(entity))
    }

    @Test
    fun `nameTokens is lowercased space-separated`() {
        val npc = LogNpc(id = "v", name = "Vesper Vance", metTurn = 1, lastSeenTurn = 1)
        val e = Mappers.toEntity(npc)
        assertEquals("vesper vance", e.nameTokens)
    }

    @Test
    fun `Quest roundtrips with objectives json`() {
        val q = Quest(
            id = "q1", title = "Find the key", desc = "...", giver = "Mira",
            location = "swamp",
            objectives = mutableListOf("Enter swamp", "Kill hydra"),
            completed = mutableListOf(true, false),
            reward = "50 gp", turnStarted = 5
        )
        val e = Mappers.toEntity(q)
        val back = Mappers.toQuest(e)
        assertEquals(q.objectives, back.objectives)
        assertEquals(q.completed, back.completed)
        assertEquals(q.status, back.status)
    }

    @Test
    fun `Faction roundtrips with government json`() {
        val f = Faction(
            id = "court", name = "Obsidian Court", type = "empire",
            description = "...", baseLoc = "north",
            government = GovernmentInfo(form = "monarchy", ruler = "Elenna")
        )
        val e = Mappers.toEntity(f)
        val back = Mappers.toFaction(e)
        assertEquals("Elenna", back.government?.ruler)
    }

    @Test
    fun `MapLocation roundtrips`() {
        val l = MapLocation(id = 3, name = "Hightower", type = "city", icon = "🏰", x = 10, y = 20, discovered = true)
        val back = Mappers.toMapLocation(Mappers.toEntity(l))
        assertEquals(l, back)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.db.MappersTest"`
Expected: FAIL — Mappers undefined.

- [ ] **Step 3: Implement `Mappers.kt`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/db/Mappers.kt`:

```kotlin
package com.realmsoffate.game.data.db

import com.realmsoffate.game.data.*
import com.realmsoffate.game.data.db.entities.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Mappers {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val stringListSerializer = kotlinx.serialization.builtins.ListSerializer(
        kotlinx.serialization.builtins.serializer<String>()
    )
    private val boolListSerializer = kotlinx.serialization.builtins.ListSerializer(
        kotlinx.serialization.builtins.serializer<Boolean>()
    )

    fun tokensFor(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")

    // --- NPC (LogNpc side) -------------------------------------------------
    fun toEntity(n: LogNpc): NpcEntity = NpcEntity(
        id = n.id.ifBlank { IdGen.npcId(n.name) },
        name = n.name,
        nameTokens = tokensFor(n.name),
        race = n.race.ifBlank { null },
        role = n.role.ifBlank { null },
        age = n.age.ifBlank { null },
        appearance = n.appearance.ifBlank { null },
        personality = n.personality.ifBlank { null },
        faction = n.faction,
        homeLocation = null,
        discovery = when (n.status) { "dead" -> "dead"; "missing" -> "missing"; else -> "met" },
        relationship = n.relationship,
        thoughts = n.thoughts.ifBlank { null },
        lastLocation = n.lastLocation.ifBlank { null },
        metTurn = n.metTurn,
        lastSeenTurn = n.lastSeenTurn,
        dialogueHistoryJson = json.encodeToString(stringListSerializer, n.dialogueHistory.toList()),
        memorableQuotesJson = json.encodeToString(stringListSerializer, n.memorableQuotes.toList()),
        relationshipNote = n.relationshipNote.ifBlank { null },
        status = n.status
    )

    fun toLogNpc(e: NpcEntity): LogNpc? {
        if (e.discovery == "lore") return null
        return LogNpc(
            id = e.id,
            name = e.name,
            race = e.race ?: "",
            role = e.role ?: "",
            age = e.age ?: "",
            relationship = e.relationship ?: "neutral",
            appearance = e.appearance ?: "",
            personality = e.personality ?: "",
            thoughts = e.thoughts ?: "",
            faction = e.faction,
            lastLocation = e.lastLocation ?: "",
            metTurn = e.metTurn ?: 0,
            lastSeenTurn = e.lastSeenTurn ?: 0,
            dialogueHistory = (e.dialogueHistoryJson?.let { json.decodeFromString(stringListSerializer, it) } ?: emptyList()).toMutableList(),
            memorableQuotes = (e.memorableQuotesJson?.let { json.decodeFromString(stringListSerializer, it) } ?: emptyList()).toMutableList(),
            relationshipNote = e.relationshipNote ?: "",
            status = e.status
        )
    }

    // --- NPC (LoreNpc side) ------------------------------------------------
    fun toEntity(n: LoreNpc): NpcEntity = NpcEntity(
        id = n.id.ifBlank { IdGen.npcId(n.name) },
        name = n.name,
        nameTokens = tokensFor(n.name),
        race = n.race.ifBlank { null },
        role = n.role.ifBlank { null },
        age = n.age.ifBlank { null },
        appearance = n.appearance.ifBlank { null },
        personality = n.personality.ifBlank { null },
        faction = n.faction,
        homeLocation = n.location.ifBlank { null },
        discovery = "lore"
    )

    fun toLoreNpc(e: NpcEntity): LoreNpc = LoreNpc(
        id = e.id,
        name = e.name,
        race = e.race ?: "",
        role = e.role ?: "",
        age = e.age ?: "",
        appearance = e.appearance ?: "",
        personality = e.personality ?: "",
        location = e.homeLocation ?: "",
        faction = e.faction
    )

    // --- Quest -------------------------------------------------------------
    fun toEntity(q: Quest) = QuestEntity(
        id = q.id,
        title = q.title,
        type = q.type,
        desc = q.desc,
        giver = q.giver,
        location = q.location,
        objectivesJson = json.encodeToString(stringListSerializer, q.objectives.toList()),
        completedJson = json.encodeToString(boolListSerializer, q.completed.toList()),
        reward = q.reward,
        status = q.status,
        turnStarted = q.turnStarted,
        turnCompleted = q.turnCompleted
    )

    fun toQuest(e: QuestEntity) = Quest(
        id = e.id,
        title = e.title,
        type = e.type,
        desc = e.desc,
        giver = e.giver,
        location = e.location,
        objectives = json.decodeFromString(stringListSerializer, e.objectivesJson).toMutableList(),
        completed = json.decodeFromString(boolListSerializer, e.completedJson).toMutableList(),
        reward = e.reward,
        status = e.status,
        turnStarted = e.turnStarted,
        turnCompleted = e.turnCompleted
    )

    // --- Faction -----------------------------------------------------------
    fun toEntity(f: Faction) = FactionEntity(
        id = f.id.ifBlank { IdGen.factionId(f.name) },
        name = f.name,
        type = f.type,
        description = f.description,
        baseLoc = f.baseLoc,
        color = f.color,
        governmentJson = f.government?.let { json.encodeToString(it) },
        economyJson = f.economy?.let { json.encodeToString(it) },
        population = f.population,
        mood = f.mood,
        disposition = f.disposition,
        goal = f.goal,
        ruler = f.ruler,
        status = f.status
    )

    fun toFaction(e: FactionEntity) = Faction(
        id = e.id,
        name = e.name,
        type = e.type,
        description = e.description,
        baseLoc = e.baseLoc,
        color = e.color,
        government = e.governmentJson?.let { json.decodeFromString<GovernmentInfo>(it) },
        economy = e.economyJson?.let { json.decodeFromString<EconomyInfo>(it) },
        population = e.population,
        mood = e.mood,
        disposition = e.disposition,
        goal = e.goal,
        status = e.status,
        ruler = e.ruler
    )

    // --- Location ----------------------------------------------------------
    fun toEntity(l: MapLocation) = LocationEntity(
        id = l.id, name = l.name, type = l.type, icon = l.icon,
        x = l.x, y = l.y, discovered = if (l.discovered) 1 else 0
    )
    fun toMapLocation(e: LocationEntity) = MapLocation(
        id = e.id, name = e.name, type = e.type, icon = e.icon,
        x = e.x, y = e.y, discovered = e.discovered != 0
    )
}
```

Notes on assumptions:
- `IdGen.npcId(name)` and `IdGen.factionId(name)` already exist in `data/IdGen.kt` (phase 1 added them). If a different function name is in use, grep and substitute.

- [ ] **Step 4: Run tests — expect PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.db.MappersTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/db/Mappers.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/db/MappersTest.kt
git commit -m "feat: add Entity↔domain mappers with JSON blob serialization"
```

---

## Task 6 — `EntityChanges` types

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/EntityChanges.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/ArcSummary.kt`

No tests — these are pure data-class definitions.

- [ ] **Step 1: `EntityChanges.kt`**

```kotlin
package com.realmsoffate.game.data

/** Diff produced by reducers and applied by the repository in one transaction. */
data class EntityChanges(
    val npcs: List<NpcChange> = emptyList(),
    val quests: List<QuestChange> = emptyList(),
    val factions: List<FactionChange> = emptyList(),
    val locations: List<LocationChange> = emptyList()
) {
    val isEmpty: Boolean get() =
        npcs.isEmpty() && quests.isEmpty() && factions.isEmpty() && locations.isEmpty()
}

sealed class NpcChange {
    data class Insert(val npc: LogNpc) : NpcChange()
    data class InsertLore(val npc: LoreNpc) : NpcChange()
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
    val status: String? = null,
    val faction: String? = null
)

sealed class QuestChange {
    data class Insert(val quest: Quest) : QuestChange()
    data class Update(val id: String, val patch: QuestPatch) : QuestChange()
}
data class QuestPatch(
    val status: String? = null,
    val turnCompleted: Int? = null,
    val objectiveCompleted: Int? = null  // index of objective to mark true
)

sealed class FactionChange {
    data class Insert(val faction: Faction) : FactionChange()
    data class Update(val id: String, val patch: FactionPatch) : FactionChange()
}
data class FactionPatch(
    val ruler: String? = null,
    val disposition: String? = null,
    val mood: String? = null,
    val status: String? = null,
    val goal: String? = null
)

sealed class LocationChange {
    data class Insert(val location: MapLocation) : LocationChange()
    data class SetDiscovered(val id: Int, val discovered: Boolean) : LocationChange()
}

/** Snapshot returned by repository for reducer input. */
data class EntitySnapshot(
    val npcs: List<LogNpc>,
    val quests: List<Quest>,
    val factions: List<Faction>,
    val locations: List<MapLocation>
)
```

- [ ] **Step 2: `ArcSummary.kt`**

```kotlin
package com.realmsoffate.game.data

import kotlinx.serialization.Serializable

@Serializable
data class ArcSummary(
    val id: Long = 0,
    val turnStart: Int,
    val turnEnd: Int,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: Verify compilation**

Run: `gradle :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/EntityChanges.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/ArcSummary.kt
git commit -m "feat: add EntityChanges diff types and ArcSummary domain model"
```

---

## Task 7 — `EntityRepository` interface + observable reads (TDD, Robolectric)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/data/RoomEntityRepositoryTest.kt`

- [ ] **Step 1: Interface stub**

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt
package com.realmsoffate.game.data

import kotlinx.coroutines.flow.Flow

interface EntityRepository {
    // Observable reads for UI
    fun observeLoggedNpcs(): Flow<List<LogNpc>>
    fun observeLoreNpcs(): Flow<List<LoreNpc>>
    fun observeActiveQuests(): Flow<List<Quest>>
    fun observeFactions(): Flow<List<Faction>>
    fun observeLocations(): Flow<List<MapLocation>>
}
```

- [ ] **Step 2: Write failing test**

```kotlin
// app/src/test/kotlin/com/realmsoffate/game/data/RoomEntityRepositoryTest.kt
package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.db.Mappers
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomEntityRepositoryTest {
    private lateinit var db: RealmsDb
    private lateinit var repo: RoomEntityRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = RealmsDb.inMemory(ctx)
        repo = RoomEntityRepository(db)
    }

    @After fun tearDown() { db.close() }

    @Test fun `observeLoggedNpcs emits after upsert of met npc`() = runTest {
        val npc = LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1)
        db.npcDao().upsertOne(Mappers.toEntity(npc))

        val logged = repo.observeLoggedNpcs().first()
        assertEquals(1, logged.size)
        assertEquals("Vesper", logged[0].name)
    }

    @Test fun `observeLoreNpcs emits only lore discovery rows`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LoreNpc(id = "sage", name = "Old Sage")))
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1)))

        val lore = repo.observeLoreNpcs().first()
        assertEquals(1, lore.size)
        assertEquals("Old Sage", lore[0].name)
    }
}
```

- [ ] **Step 3: Run test — expect FAIL**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RoomEntityRepositoryTest"`
Expected: FAIL — `RoomEntityRepository` undefined.

- [ ] **Step 4: Implement `RoomEntityRepository` (observable-read portion only)**

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt
package com.realmsoffate.game.data

import com.realmsoffate.game.data.db.Mappers
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEntityRepository(private val db: RealmsDb) : EntityRepository {
    override fun observeLoggedNpcs(): Flow<List<LogNpc>> =
        db.npcDao().observeLogged().map { rows -> rows.mapNotNull(Mappers::toLogNpc) }

    override fun observeLoreNpcs(): Flow<List<LoreNpc>> =
        db.npcDao().observeLore().map { rows -> rows.map(Mappers::toLoreNpc) }

    override fun observeActiveQuests(): Flow<List<Quest>> =
        db.questDao().observeActive().map { rows -> rows.map(Mappers::toQuest) }

    override fun observeFactions(): Flow<List<Faction>> =
        db.factionDao().observeAll().map { rows -> rows.map(Mappers::toFaction) }

    override fun observeLocations(): Flow<List<MapLocation>> =
        db.locationDao().observeAll().map { rows -> rows.map(Mappers::toMapLocation) }
}
```

- [ ] **Step 5: Run test — expect PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RoomEntityRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/RoomEntityRepositoryTest.kt
git commit -m "feat: add RoomEntityRepository with observable UI queries"
```

---

## Task 8 — Repository on-demand queries (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/KeywordHits.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/data/RepositoryQueriesTest.kt`

- [ ] **Step 1: Define `KeywordHits`**

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/data/KeywordHits.kt
package com.realmsoffate.game.data

data class KeywordHits(
    val npcs: List<LogNpc>,
    val factions: List<Faction>,
    val locations: List<MapLocation>
) {
    companion object { val EMPTY = KeywordHits(emptyList(), emptyList(), emptyList()) }
}
```

- [ ] **Step 2: Extend interface**

Add to `EntityRepository`:

```kotlin
suspend fun snapshotForReducers(): EntitySnapshot
suspend fun sceneRelevantNpcs(location: String, currentTurn: Int, withinTurns: Int = 10): List<LogNpc>
suspend fun keywordMatchedEntities(tokens: List<String>, limit: Int = 15): KeywordHits
```

- [ ] **Step 3: Write failing test**

```kotlin
// app/src/test/kotlin/com/realmsoffate/game/data/RepositoryQueriesTest.kt
package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.db.Mappers
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryQueriesTest {
    private lateinit var db: RealmsDb
    private lateinit var repo: RoomEntityRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = RealmsDb.inMemory(ctx)
        repo = RoomEntityRepository(db)
    }
    @After fun tearDown() { db.close() }

    @Test fun `sceneRelevantNpcs matches by current location`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "a", name = "Alpha", lastLocation = "Hightower", metTurn = 1, lastSeenTurn = 1)))
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "b", name = "Beta", lastLocation = "Lowvale", metTurn = 1, lastSeenTurn = 1)))
        val result = repo.sceneRelevantNpcs("Hightower", currentTurn = 50, withinTurns = 10)
        assertEquals(listOf("Alpha"), result.map { it.name })
    }

    @Test fun `sceneRelevantNpcs matches by recent lastSeenTurn`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "a", name = "Alpha", lastLocation = "Other", metTurn = 1, lastSeenTurn = 48)))
        val result = repo.sceneRelevantNpcs("Hightower", currentTurn = 50, withinTurns = 10)
        assertEquals(listOf("Alpha"), result.map { it.name })
    }

    @Test fun `keywordMatchedEntities finds by name tokens`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "v", name = "Vesper Vance", metTurn = 1, lastSeenTurn = 1)))
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "m", name = "Mira Cole", metTurn = 1, lastSeenTurn = 1)))
        val hits = repo.keywordMatchedEntities(listOf("vesper", "nothing"))
        assertEquals(1, hits.npcs.size)
        assertEquals("Vesper Vance", hits.npcs[0].name)
    }

    @Test fun `snapshotForReducers returns current lists`() = runTest {
        db.npcDao().upsertOne(Mappers.toEntity(LogNpc(id = "v", name = "V", metTurn = 1, lastSeenTurn = 1)))
        val snap = repo.snapshotForReducers()
        assertEquals(1, snap.npcs.size)
    }
}
```

- [ ] **Step 4: Run — expect FAIL**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RepositoryQueriesTest"`
Expected: FAIL.

- [ ] **Step 5: Implement queries in `RoomEntityRepository`**

Append to the class:

```kotlin
    override suspend fun snapshotForReducers(): EntitySnapshot {
        val npcs = db.npcDao().getAllLogged().mapNotNull(Mappers::toLogNpc)
        val quests = db.questDao().getAll().map(Mappers::toQuest)
        val factions = db.factionDao().getAll().map(Mappers::toFaction)
        val locations = db.locationDao().getAll().map(Mappers::toMapLocation)
        return EntitySnapshot(npcs, quests, factions, locations)
    }

    override suspend fun sceneRelevantNpcs(
        location: String, currentTurn: Int, withinTurns: Int
    ): List<LogNpc> {
        val minTurn = (currentTurn - withinTurns).coerceAtLeast(0)
        return db.npcDao().sceneRelevant(location, minTurn).mapNotNull(Mappers::toLogNpc)
    }

    override suspend fun keywordMatchedEntities(
        tokens: List<String>, limit: Int
    ): KeywordHits {
        if (tokens.isEmpty()) return KeywordHits.EMPTY
        val npcHits = mutableMapOf<String, LogNpc>()
        for (tok in tokens) {
            db.npcDao().matchKeyword("%$tok%", limit).forEach { e ->
                val n = Mappers.toLogNpc(e) ?: return@forEach
                npcHits.putIfAbsent(n.id, n)
            }
            if (npcHits.size >= limit) break
        }
        val factionHits = mutableListOf<Faction>()
        val allFactions = db.factionDao().getAll().map(Mappers::toFaction)
        for (tok in tokens) {
            allFactions
                .filter { it.name.lowercase().contains(tok) && factionHits.none { f -> f.id == it.id } }
                .forEach { factionHits.add(it) }
            if (factionHits.size >= limit) break
        }
        val locationHits = mutableListOf<MapLocation>()
        val allLocs = db.locationDao().getAll().map(Mappers::toMapLocation)
        for (tok in tokens) {
            allLocs
                .filter { it.name.lowercase().contains(tok) && locationHits.none { l -> l.id == it.id } }
                .forEach { locationHits.add(it) }
            if (locationHits.size >= limit) break
        }
        return KeywordHits(npcHits.values.toList().take(limit), factionHits.take(limit), locationHits.take(limit))
    }
```

- [ ] **Step 6: Run — expect PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RepositoryQueriesTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/KeywordHits.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/RepositoryQueriesTest.kt
git commit -m "feat: add repo queries for snapshot, scene relevance, keyword retrieval"
```

---

## Task 9 — Repository `applyChanges` (transactional writes, TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt`
- Create test: merge into `RoomEntityRepositoryTest.kt`

- [ ] **Step 1: Extend interface**

Add:
```kotlin
suspend fun applyChanges(changes: EntityChanges)
suspend fun clear()
```

- [ ] **Step 2: Add failing tests to `RoomEntityRepositoryTest`**

Append:

```kotlin
    @Test fun `applyChanges insert npc writes to db`() = runTest {
        val npc = LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1)
        repo.applyChanges(EntityChanges(npcs = listOf(NpcChange.Insert(npc))))
        val rows = db.npcDao().getAllLogged()
        assertEquals(1, rows.size)
        assertEquals("Vesper", rows[0].name)
    }

    @Test fun `applyChanges update patches fields and appends dialogue`() = runTest {
        val base = LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1,
            dialogueHistory = mutableListOf("hi"))
        repo.applyChanges(EntityChanges(npcs = listOf(NpcChange.Insert(base))))
        repo.applyChanges(EntityChanges(npcs = listOf(
            NpcChange.Update("v", NpcPatch(
                lastLocation = "Hightower",
                lastSeenTurn = 10,
                relationship = "allied",
                appendDialogue = listOf("bye")
            ))
        )))
        val back = com.realmsoffate.game.data.db.Mappers.toLogNpc(db.npcDao().getById("v")!!)!!
        assertEquals("Hightower", back.lastLocation)
        assertEquals(10, back.lastSeenTurn)
        assertEquals("allied", back.relationship)
        assertEquals(listOf("hi", "bye"), back.dialogueHistory)
    }

    @Test fun `applyChanges markDead sets status and discovery`() = runTest {
        repo.applyChanges(EntityChanges(npcs = listOf(
            NpcChange.Insert(LogNpc(id = "v", name = "Vesper", metTurn = 1, lastSeenTurn = 1))
        )))
        repo.applyChanges(EntityChanges(npcs = listOf(NpcChange.MarkDead("v", 50))))
        val row = db.npcDao().getById("v")!!
        assertEquals("dead", row.status)
        assertEquals("dead", row.discovery)
        assertEquals(50, row.lastSeenTurn)
    }
```

- [ ] **Step 3: Run — expect FAIL**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RoomEntityRepositoryTest"`

- [ ] **Step 4: Implement `applyChanges`**

Add to `RoomEntityRepository`:

```kotlin
    override suspend fun applyChanges(changes: EntityChanges) {
        if (changes.isEmpty) return
        androidx.room.withTransaction(db) {
            applyNpcChanges(changes.npcs)
            applyQuestChanges(changes.quests)
            applyFactionChanges(changes.factions)
            applyLocationChanges(changes.locations)
        }
    }

    private suspend fun applyNpcChanges(ops: List<NpcChange>) {
        if (ops.isEmpty()) return
        val dao = db.npcDao()
        for (op in ops) {
            when (op) {
                is NpcChange.Insert -> dao.upsertOne(Mappers.toEntity(op.npc))
                is NpcChange.InsertLore -> dao.upsertOne(Mappers.toEntity(op.npc))
                is NpcChange.Update -> {
                    val existing = dao.getById(op.id) ?: continue
                    val merged = mergePatch(existing, op.patch)
                    dao.upsertOne(merged)
                }
                is NpcChange.MarkDead -> {
                    val existing = dao.getById(op.id) ?: continue
                    dao.upsertOne(existing.copy(
                        status = "dead",
                        discovery = "dead",
                        lastSeenTurn = op.turn
                    ))
                }
            }
        }
    }

    private fun mergePatch(e: com.realmsoffate.game.data.db.entities.NpcEntity, p: NpcPatch): com.realmsoffate.game.data.db.entities.NpcEntity {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val stringList = kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.builtins.serializer<String>())
        val existingDialogue: List<String> = e.dialogueHistoryJson?.let { json.decodeFromString(stringList, it) } ?: emptyList()
        val existingQuotes: List<String> = e.memorableQuotesJson?.let { json.decodeFromString(stringList, it) } ?: emptyList()
        val newDialogue = (existingDialogue + p.appendDialogue).takeLast(20)
        val newQuotes = if (p.appendMemorableQuote != null) (existingQuotes + p.appendMemorableQuote).takeLast(12) else existingQuotes
        return e.copy(
            lastLocation = p.lastLocation ?: e.lastLocation,
            lastSeenTurn = p.lastSeenTurn ?: e.lastSeenTurn,
            relationship = p.relationship ?: e.relationship,
            thoughts = p.thoughts ?: e.thoughts,
            dialogueHistoryJson = json.encodeToString(stringList, newDialogue),
            memorableQuotesJson = json.encodeToString(stringList, newQuotes),
            relationshipNote = p.relationshipNote ?: e.relationshipNote,
            status = p.status ?: e.status,
            faction = p.faction ?: e.faction
        )
    }

    private suspend fun applyQuestChanges(ops: List<QuestChange>) {
        if (ops.isEmpty()) return
        val dao = db.questDao()
        for (op in ops) {
            when (op) {
                is QuestChange.Insert -> dao.upsertOne(Mappers.toEntity(op.quest))
                is QuestChange.Update -> {
                    val existing = dao.getById(op.id) ?: continue
                    val q = Mappers.toQuest(existing)
                    val updated = q.copy(
                        status = op.patch.status ?: q.status,
                        turnCompleted = op.patch.turnCompleted ?: q.turnCompleted,
                        completed = q.completed.toMutableList().also {
                            val idx = op.patch.objectiveCompleted
                            if (idx != null && idx in it.indices) it[idx] = true
                        }
                    )
                    dao.upsertOne(Mappers.toEntity(updated))
                }
            }
        }
    }

    private suspend fun applyFactionChanges(ops: List<FactionChange>) {
        if (ops.isEmpty()) return
        val dao = db.factionDao()
        for (op in ops) {
            when (op) {
                is FactionChange.Insert -> dao.upsert(listOf(Mappers.toEntity(op.faction)))
                is FactionChange.Update -> {
                    val existing = dao.getById(op.id) ?: continue
                    val f = Mappers.toFaction(existing)
                    val updated = f.copy(
                        ruler = op.patch.ruler ?: f.ruler,
                        disposition = op.patch.disposition ?: f.disposition,
                        mood = op.patch.mood ?: f.mood,
                        status = op.patch.status ?: f.status,
                        goal = op.patch.goal ?: f.goal
                    )
                    dao.upsert(listOf(Mappers.toEntity(updated)))
                }
            }
        }
    }

    private suspend fun applyLocationChanges(ops: List<LocationChange>) {
        if (ops.isEmpty()) return
        val dao = db.locationDao()
        val all = dao.getAll().associateBy { it.id }.toMutableMap()
        for (op in ops) {
            when (op) {
                is LocationChange.Insert -> all[op.location.id] = Mappers.toEntity(op.location)
                is LocationChange.SetDiscovered -> all[op.id]?.let {
                    all[op.id] = it.copy(discovered = if (op.discovered) 1 else 0)
                }
            }
        }
        dao.upsert(all.values.toList())
    }

    override suspend fun clear() {
        androidx.room.withTransaction(db) {
            db.npcDao().clear()
            db.questDao().clear()
            db.factionDao().clear()
            db.locationDao().clear()
            db.sceneSummaryDao().clear()
            db.arcSummaryDao().clear()
        }
    }
```

Note: You can delete the empty `db.runInTransaction {}` call — it's replaced by `withTransaction`. Left only if the `withTransaction` import warrants it. Use only the `withTransaction` block; delete the empty `runInTransaction` block before committing.

Import `androidx.room.withTransaction` at the top of the file.

Needed upsertOne on QuestDao — add if missing:
```kotlin
// in QuestDao.kt
@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertOne(row: QuestEntity)
```

- [ ] **Step 5: Run — expect PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RoomEntityRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/db/dao/QuestDao.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/RoomEntityRepositoryTest.kt
git commit -m "feat: add transactional applyChanges with patch merge semantics"
```

---

## Task 10 — Summary append + arc rollup in repository (TDD)

**Files:**
- Modify: `EntityRepository.kt`, `RoomEntityRepository.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/data/RepositorySummariesTest.kt`

- [ ] **Step 1: Extend interface**

```kotlin
suspend fun appendSceneSummary(s: SceneSummary): Long
suspend fun recentSceneSummaries(limit: Int = 20): List<SceneSummary>
suspend fun countUnrolledScenes(): Int
suspend fun allArcSummaries(): List<ArcSummary>
suspend fun rollupScenes(sceneIds: List<Long>, arc: ArcSummary)
```

- [ ] **Step 2: Add `SceneSummary.id` mapping helpers**

Check `SceneSummary` data class in `app/src/main/kotlin/com/realmsoffate/game/data/SceneSummary.kt`. It was added in phase 1; confirm it has an `id: Long = 0` (autogenerated) and `turnStart/turnEnd/location/summary/createdAt` fields. If `id` is absent, add it:

```kotlin
@Serializable
data class SceneSummary(
    val id: Long = 0,
    val turnStart: Int,
    val turnEnd: Int,
    val location: String = "",
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

If SceneSummary already has different field names, adapt mappers below accordingly (grep for `class SceneSummary`).

- [ ] **Step 3: Add mapper helpers to `Mappers.kt`**

```kotlin
    fun toEntity(s: SceneSummary) = com.realmsoffate.game.data.db.entities.SceneSummaryEntity(
        id = s.id,
        turnStart = s.turnStart,
        turnEnd = s.turnEnd,
        location = s.location,
        summary = s.summary,
        createdAt = s.createdAt,
        arcId = null
    )
    fun toSceneSummary(e: com.realmsoffate.game.data.db.entities.SceneSummaryEntity) = SceneSummary(
        id = e.id,
        turnStart = e.turnStart,
        turnEnd = e.turnEnd,
        location = e.location,
        summary = e.summary,
        createdAt = e.createdAt
    )
    fun toEntity(a: ArcSummary) = com.realmsoffate.game.data.db.entities.ArcSummaryEntity(
        id = a.id,
        turnStart = a.turnStart,
        turnEnd = a.turnEnd,
        summary = a.summary,
        createdAt = a.createdAt
    )
    fun toArcSummary(e: com.realmsoffate.game.data.db.entities.ArcSummaryEntity) = ArcSummary(
        id = e.id,
        turnStart = e.turnStart,
        turnEnd = e.turnEnd,
        summary = e.summary,
        createdAt = e.createdAt
    )
```

- [ ] **Step 4: Write failing test**

```kotlin
// app/src/test/kotlin/com/realmsoffate/game/data/RepositorySummariesTest.kt
package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositorySummariesTest {
    private lateinit var db: RealmsDb
    private lateinit var repo: RoomEntityRepository
    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = RealmsDb.inMemory(ctx)
        repo = RoomEntityRepository(db)
    }
    @After fun tearDown() { db.close() }

    @Test fun `appendSceneSummary inserts and returns id`() = runTest {
        val id = repo.appendSceneSummary(SceneSummary(turnStart = 1, turnEnd = 5, summary = "..."))
        assertTrue(id > 0)
        assertEquals(1, repo.countUnrolledScenes())
    }

    @Test fun `recentSceneSummaries excludes rolled-up scenes`() = runTest {
        val s1 = repo.appendSceneSummary(SceneSummary(turnStart = 1, turnEnd = 5, summary = "a"))
        val s2 = repo.appendSceneSummary(SceneSummary(turnStart = 6, turnEnd = 10, summary = "b"))
        repo.rollupScenes(listOf(s1), ArcSummary(turnStart = 1, turnEnd = 5, summary = "arc"))
        val recent = repo.recentSceneSummaries(limit = 20)
        assertEquals(listOf("b"), recent.map { it.summary })
    }

    @Test fun `rollupScenes inserts arc and marks scenes transactionally`() = runTest {
        val id1 = repo.appendSceneSummary(SceneSummary(turnStart = 1, turnEnd = 5, summary = "a"))
        val id2 = repo.appendSceneSummary(SceneSummary(turnStart = 6, turnEnd = 10, summary = "b"))
        repo.rollupScenes(listOf(id1, id2), ArcSummary(turnStart = 1, turnEnd = 10, summary = "arc"))
        val arcs = repo.allArcSummaries()
        assertEquals(1, arcs.size)
        assertEquals("arc", arcs[0].summary)
        assertEquals(0, repo.countUnrolledScenes())
    }
}
```

- [ ] **Step 5: Run — expect FAIL**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RepositorySummariesTest"`

- [ ] **Step 6: Implement**

Append to `RoomEntityRepository`:

```kotlin
    override suspend fun appendSceneSummary(s: SceneSummary): Long =
        db.sceneSummaryDao().insert(Mappers.toEntity(s))

    override suspend fun recentSceneSummaries(limit: Int): List<SceneSummary> =
        db.sceneSummaryDao().recentUnrolled(limit).map(Mappers::toSceneSummary)

    override suspend fun countUnrolledScenes(): Int =
        db.sceneSummaryDao().countUnrolled()

    override suspend fun allArcSummaries(): List<ArcSummary> =
        db.arcSummaryDao().allNewestFirst().map(Mappers::toArcSummary)

    override suspend fun rollupScenes(sceneIds: List<Long>, arc: ArcSummary) {
        androidx.room.withTransaction(db) {
            val arcId = db.arcSummaryDao().insert(Mappers.toEntity(arc))
            db.sceneSummaryDao().assignArcId(sceneIds, arcId)
        }
    }
```

- [ ] **Step 7: Run — expect PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RepositorySummariesTest"`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/EntityRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/RoomEntityRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/db/Mappers.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/RepositorySummariesTest.kt
git commit -m "feat: add scene summary append and transactional arc rollup"
```

---

## Task 11 — `CanonicalFacts` data + renderer (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/CanonicalFacts.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/data/CanonicalFactsTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.realmsoffate.game.data

import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalFactsTest {
    @Test fun `render returns empty string when no facts`() {
        val out = CanonicalFacts(emptyList(), emptyList(), emptyList()).render()
        assertTrue(out.isEmpty())
    }

    @Test fun `render includes NPCs section when npcs non-empty`() {
        val facts = CanonicalFacts(
            npcs = listOf(LogNpc(
                id="v", name="Vesper", race="human", role="sorcerer",
                faction = "court", status="alive", relationship="allied",
                lastLocation="Silent Swamp", metTurn=5, lastSeenTurn=47,
                thoughts="Will help if paid in arcane lore."
            )),
            factions = emptyList(),
            locations = emptyList()
        )
        val out = facts.render()
        assertTrue(out.contains("CANONICAL FACTS"))
        assertTrue(out.contains("Vesper"))
        assertTrue(out.contains("sorcerer"))
        assertTrue(out.contains("Silent Swamp"))
    }

    @Test fun `render includes factions and locations sections`() {
        val facts = CanonicalFacts(
            npcs = emptyList(),
            factions = listOf(Faction(id="court", name="Obsidian Court", type="empire", description="", baseLoc="north", ruler="Elenna", disposition="hostile")),
            locations = listOf(MapLocation(id=1, name="Silent Swamp", type="marsh", icon="🌿", x=0, y=0, discovered=true))
        )
        val out = facts.render()
        assertTrue(out.contains("## Factions"))
        assertTrue(out.contains("Obsidian Court"))
        assertTrue(out.contains("Elenna"))
        assertTrue(out.contains("## Locations"))
        assertTrue(out.contains("Silent Swamp"))
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/data/CanonicalFacts.kt
package com.realmsoffate.game.data

/**
 * Bundle of ground-truth entity records to inject into the user prompt.
 * The AI is instructed not to contradict these.
 */
data class CanonicalFacts(
    val npcs: List<LogNpc>,
    val factions: List<Faction>,
    val locations: List<MapLocation>
) {
    val isEmpty get() = npcs.isEmpty() && factions.isEmpty() && locations.isEmpty()

    fun render(): String {
        if (isEmpty) return ""
        val sb = StringBuilder()
        sb.appendLine("# CANONICAL FACTS (ground truth — do not contradict)")
        if (npcs.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## NPCs")
            for (n in npcs) sb.appendLine("- ${renderNpc(n)}")
        }
        if (factions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Factions")
            for (f in factions) sb.appendLine("- ${renderFaction(f)}")
        }
        if (locations.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Locations")
            for (l in locations) sb.appendLine("- ${renderLocation(l)}")
        }
        return sb.toString().trimEnd()
    }

    private fun renderNpc(n: LogNpc): String {
        val parts = mutableListOf<String>()
        parts += n.name
        val details = buildString {
            val bits = mutableListOf<String>()
            if (n.race.isNotBlank()) bits += n.race
            if (n.role.isNotBlank()) bits += n.role
            if (n.faction != null) bits += "faction=${n.faction}"
            bits += "status=${n.status}"
            if (n.relationship.isNotBlank()) bits += "relationship=${n.relationship}"
            if (n.lastLocation.isNotBlank()) bits += "last seen turn ${n.lastSeenTurn} at ${n.lastLocation}"
            append(bits.joinToString(", "))
        }
        parts += "($details)"
        if (n.thoughts.isNotBlank()) parts += "thoughts: \"${n.thoughts}\""
        return parts.joinToString(" ")
    }

    private fun renderFaction(f: Faction): String = buildString {
        append(f.name)
        val bits = mutableListOf<String>()
        if (f.type.isNotBlank()) bits += f.type
        if (f.ruler.isNotBlank()) bits += "ruler: ${f.ruler}"
        if (f.disposition.isNotBlank()) bits += "disposition: ${f.disposition}"
        if (f.goal.isNotBlank()) bits += "goal: ${f.goal}"
        if (bits.isNotEmpty()) append(" (").append(bits.joinToString(", ")).append(")")
    }

    private fun renderLocation(l: MapLocation): String = buildString {
        append(l.name)
        val bits = mutableListOf<String>()
        if (l.type.isNotBlank()) bits += l.type
        if (l.discovered) bits += "discovered"
        if (bits.isNotEmpty()) append(" (").append(bits.joinToString(", ")).append(")")
    }
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/CanonicalFacts.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/CanonicalFactsTest.kt
git commit -m "feat: add CanonicalFacts data class and prompt renderer"
```

---

## Task 12 — Repository migration: `seedFromSaveData`, `exportToSaveData` (TDD)

**Files:**
- Modify: `EntityRepository.kt`, `RoomEntityRepository.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/data/RepositoryMigrationTest.kt`

- [ ] **Step 1: Extend interface**

```kotlin
suspend fun seedFromSaveData(save: SaveData)
suspend fun exportToSaveData(base: SaveData): SaveData
```

`base` is the non-entity SaveData shape; repository overlays entity lists.

- [ ] **Step 2: Write failing test**

```kotlin
package com.realmsoffate.game.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryMigrationTest {
    private lateinit var db: RealmsDb
    private lateinit var repo: RoomEntityRepository
    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = RealmsDb.inMemory(ctx)
        repo = RoomEntityRepository(db)
    }
    @After fun tearDown() { db.close() }

    @Test fun `seed dedupes NPCs present in both worldLore and npcLog`() = runTest {
        val loreNpc = LoreNpc(id = "mira-cole", name = "Mira Cole", race = "human", role = "innkeeper", location = "Hightower")
        val logNpc = LogNpc(id = "mira-cole", name = "Mira Cole", race = "human", metTurn = 5, lastSeenTurn = 30, relationship = "allied")
        val save = fixtureSave(loreNpcs = listOf(loreNpc), logNpcs = listOf(logNpc))
        repo.seedFromSaveData(save)
        val loreCount = db.npcDao().observeLore().first().size
        val logCount = db.npcDao().observeLogged().first().size
        assertEquals(0, loreCount)          // logged state wins
        assertEquals(1, logCount)
    }

    @Test fun `seed inserts quests factions locations and scene summaries`() = runTest {
        val save = fixtureSave(
            quests = listOf(Quest(id="q1", title="t", desc="", giver="g", location="", objectives=mutableListOf("a"), reward="", turnStarted=1)),
            factions = listOf(Faction(id="f", name="F", type="", description="", baseLoc="")),
            scenes = listOf(SceneSummary(turnStart=1, turnEnd=5, summary="s1"))
        )
        repo.seedFromSaveData(save)
        assertEquals(1, db.questDao().getAll().size)
        assertEquals(1, db.factionDao().getAll().size)
        assertEquals(1, db.sceneSummaryDao().getAll().size)
    }
}

// Helper that builds a minimal v2 SaveData fixture for tests.
private fun fixtureSave(
    loreNpcs: List<LoreNpc> = emptyList(),
    logNpcs: List<LogNpc> = emptyList(),
    quests: List<Quest> = emptyList(),
    factions: List<Faction> = emptyList(),
    scenes: List<SceneSummary> = emptyList()
): SaveData {
    // NOTE: adapt to the actual minimal SaveData constructor — fill required
    // fields with stub defaults. See current `data/Models.kt`.
    return SaveData(
        character = Character(name = "Test", race = "human", cls = "fighter"),
        morality = 0,
        factionRep = emptyMap(),
        worldMap = WorldMap(locations = mutableListOf(), roads = emptyList(), startId = 0, terrain = emptyList(), rivers = emptyList(), lakes = emptyList()),
        currentLoc = 0,
        playerPos = null,
        worldLore = WorldLore(factions = factions, npcs = loreNpcs, primordial = emptyList(), mutations = emptyList()),
        worldEvents = emptyList(),
        lastEventTurn = 0,
        npcLog = logNpcs,
        party = emptyList(),
        quests = quests,
        hotbar = emptyList(),
        history = emptyList(),
        turns = 0,
        scene = "",
        savedAt = "2026-04-21",
        sceneSummaries = scenes
    )
}
```

**Note:** the helper may need adjustment — grep the actual `SaveData(...)` call sites to match required field ordering. If the helper compiles, proceed.

- [ ] **Step 3: Run — expect FAIL**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RepositoryMigrationTest"`

- [ ] **Step 4: Implement migration methods**

Add `import kotlinx.coroutines.flow.first` to the top of `RoomEntityRepository.kt` if not already present, then append to the class:

```kotlin
    override suspend fun seedFromSaveData(save: SaveData) {
        androidx.room.withTransaction(db) {
            clear()

            // NPCs: merge lore + log, log wins on overlap (by IdGen.nameKey).
            val mergedNpcs = mutableMapOf<String, com.realmsoffate.game.data.db.entities.NpcEntity>()
            save.worldLore?.npcs?.forEach { lore ->
                val entity = Mappers.toEntity(lore)
                mergedNpcs[IdGen.nameKey(lore.name)] = entity
            }
            save.npcLog.forEach { log ->
                val entity = Mappers.toEntity(log)
                mergedNpcs[IdGen.nameKey(log.name)] = entity   // log wins
            }
            db.npcDao().upsert(mergedNpcs.values.toList())

            // Factions + Locations + Quests
            save.worldLore?.factions?.let { fs ->
                db.factionDao().upsert(fs.map(Mappers::toEntity))
            }
            db.locationDao().upsert(save.worldMap.locations.map(Mappers::toEntity))
            db.questDao().upsert(save.quests.map(Mappers::toEntity))

            // Scene summaries (phase 1 field)
            for (s in save.sceneSummaries) {
                db.sceneSummaryDao().insert(Mappers.toEntity(s).copy(id = 0))
            }
        }
    }

    override suspend fun exportToSaveData(base: SaveData): SaveData {
        val snap = snapshotForReducers()
        val lore = db.npcDao().observeLore().first().map(Mappers::toLoreNpc)
        val scenes = db.sceneSummaryDao().getAll().map(Mappers::toSceneSummary)
        return base.copy(
            npcLog = snap.npcs,
            quests = snap.quests,
            worldLore = base.worldLore?.copy(factions = snap.factions, npcs = lore),
            worldMap = base.worldMap.copy(locations = snap.locations.toMutableList()),
            sceneSummaries = scenes
        )
    }
```

If `IdGen.nameKey` doesn't exist, add it to `data/IdGen.kt`:

```kotlin
fun nameKey(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
```

- [ ] **Step 5: Run — expect PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.data.RepositoryMigrationTest"`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/ \
        app/src/test/kotlin/com/realmsoffate/game/data/RepositoryMigrationTest.kt
git commit -m "feat: add seedFromSaveData and exportToSaveData for v2→v3 migration"
```

---

## Task 13 — Update `NpcLogReducer` to emit `NpcChange`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt`
- Modify existing test: `app/src/test/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducerTest.kt`

- [ ] **Step 1: Update test assertions first**

Open the existing `NpcLogReducerTest.kt`. For each test, change assertions from "final list shape" to "emitted changes shape":

- Replace `result.npcLog` reads with `result.npcChanges`.
- `Insert` tests expect a `NpcChange.Insert` with the full `LogNpc`.
- `Update` tests expect a `NpcChange.Update(id, patch)` — verify patch fields match expectation.
- Death tests expect `NpcChange.MarkDead(id, turn)`.

Leave `combat`, `systemMessages`, `timelineEntries`, `deadDisplayNames` assertions intact.

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.reducers.NpcLogReducerTest"`
Expected: FAIL — reducer still returns old shape.

- [ ] **Step 2: Update `NpcLogApplyResult`**

```kotlin
data class NpcLogApplyResult(
    val npcChanges: List<NpcChange>,
    val combat: CombatState?,
    val systemMessages: List<DisplayMessage.System>,
    val timelineEntries: List<TimelineEntry>,
    val deadDisplayNames: List<String>
)
```

- [ ] **Step 3: Rewrite body to emit changes**

Rework `NpcLogReducer.apply`:
- Accept `currentNpcs: List<LogNpc>` (snapshot) plus existing params.
- Build a mutable `changes: MutableList<NpcChange>`.
- Instead of mutating/creating rows in a working list, for each operation emit the corresponding `NpcChange`.
- Paths:
  - New NPC from `parsed.npcsMet`, unseen id/nameKey → `NpcChange.Insert(LogNpc(...))`.
  - Known NPC, update fields → `NpcChange.Update(id, NpcPatch(...))` carrying only the fields that changed this turn (`lastLocation`, `lastSeenTurn`, `appendDialogue`, etc.).
  - Dead NPC (from `parsed.npcDeaths`) → `NpcChange.MarkDead(id, turn)`; still produce the system-message / timeline side-effects.
- Return `NpcLogApplyResult(npcChanges = changes, ..., deadDisplayNames = ...)`.

**Do not read or return `List<LogNpc>` anywhere — that responsibility moves to the repo.**

- [ ] **Step 4: Update callers**

`GameViewModel.applyParsed` and any other callers: swap from reading `result.npcLog` to forwarding `result.npcChanges` into an `EntityChanges` (the VM glue task applies this in a single transaction later — for now, keep temporary code that applies to `ui.value.npcLog` to avoid breaking the app; a TODO comment marking the transitional state is fine, but it MUST be removed in Task 22).

```kotlin
// Temporary scaffolding (removed in Task 22):
// Apply changes to in-memory list to keep the app running; replaced by repo in VM glue.
val applied = applyNpcChangesInMemory(ui.value.npcLog, result.npcChanges)
_ui.value = ui.value.copy(npcLog = applied, combat = result.combat ?: ui.value.combat)
```

Put `applyNpcChangesInMemory` as a private helper in `GameViewModel.kt`:

```kotlin
private fun applyNpcChangesInMemory(current: List<LogNpc>, ops: List<NpcChange>): List<LogNpc> {
    val map = current.associateBy { it.id }.toMutableMap()
    for (op in ops) {
        when (op) {
            is NpcChange.Insert -> map[op.npc.id] = op.npc
            is NpcChange.InsertLore -> {} // ignored in-memory; only Room keeps lore
            is NpcChange.MarkDead -> map[op.id]?.let { map[op.id] = it.copy(status = "dead", lastSeenTurn = op.turn) }
            is NpcChange.Update -> map[op.id]?.let { existing ->
                val p = op.patch
                map[op.id] = existing.copy(
                    lastLocation = p.lastLocation ?: existing.lastLocation,
                    lastSeenTurn = p.lastSeenTurn ?: existing.lastSeenTurn,
                    relationship = p.relationship ?: existing.relationship,
                    thoughts = p.thoughts ?: existing.thoughts,
                    dialogueHistory = (existing.dialogueHistory + p.appendDialogue).takeLast(20).toMutableList(),
                    memorableQuotes = if (p.appendMemorableQuote != null)
                        (existing.memorableQuotes + p.appendMemorableQuote).takeLast(12).toMutableList()
                        else existing.memorableQuotes,
                    relationshipNote = p.relationshipNote ?: existing.relationshipNote,
                    status = p.status ?: existing.status,
                    faction = p.faction ?: existing.faction
                )
            }
        }
    }
    return map.values.toList()
}
```

- [ ] **Step 5: Run reducer tests — expect PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.reducers.NpcLogReducerTest"`
Expected: PASS.

- [ ] **Step 6: Run full test suite — no regressions**

Run: `gradle :app:testDebugUnitTest`
Expected: all phase 1 tests still pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducerTest.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "refactor: NpcLogReducer emits NpcChange diff instead of full list"
```

---

## Task 14 — Update `QuestAndPartyReducer` to emit `QuestChange`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/reducers/QuestAndPartyReducer.kt`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/game/reducers/QuestAndPartyReducerTest.kt`

Apply the same transformation as Task 13, scoped to quests:

- [ ] **Step 1: Update test assertions** to check `result.questChanges` with `QuestChange.Insert` / `QuestChange.Update`.

- [ ] **Step 2: Update reducer result type**

```kotlin
data class QuestAndPartyApplyResult(
    val questChanges: List<QuestChange>,
    val party: List<PartyCompanion>,     // party still in-memory, phase 2
    val systemMessages: List<DisplayMessage.System>,
    val timelineEntries: List<TimelineEntry>
)
```

- [ ] **Step 3: Rewrite `apply` body** to produce `QuestChange.Insert` / `QuestChange.Update(id, QuestPatch(...))` instead of mutating a list. Party still returned as updated `List<PartyCompanion>` (party is not in Room in phase 2).

- [ ] **Step 4: Update VM glue** — apply quest changes in-memory similarly:

```kotlin
private fun applyQuestChangesInMemory(current: List<Quest>, ops: List<QuestChange>): List<Quest> {
    val map = current.associateBy { it.id }.toMutableMap()
    for (op in ops) when (op) {
        is QuestChange.Insert -> map[op.quest.id] = op.quest
        is QuestChange.Update -> map[op.id]?.let { existing ->
            val p = op.patch
            val completed = existing.completed.toMutableList().also {
                val idx = p.objectiveCompleted
                if (idx != null && idx in it.indices) it[idx] = true
            }
            map[op.id] = existing.copy(
                status = p.status ?: existing.status,
                turnCompleted = p.turnCompleted ?: existing.turnCompleted,
                completed = completed
            )
        }
    }
    return map.values.toList()
}
```

- [ ] **Step 5: Run reducer tests — PASS**

- [ ] **Step 6: Run full suite — no regressions**

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/reducers/QuestAndPartyReducer.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/reducers/QuestAndPartyReducerTest.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "refactor: QuestAndPartyReducer emits QuestChange diff"
```

---

## Task 15 — `WorldReducer` for factions + locations (TDD)

**Files:**
- Create (or enhance): `app/src/main/kotlin/com/realmsoffate/game/game/reducers/WorldReducer.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/game/reducers/WorldReducerTest.kt`

First check if `WorldReducer.kt` already exists (grep the file listing in Task Files Impacted). If it does, adapt to emit `FactionChange` + `LocationChange`. If not, create from scratch.

- [ ] **Step 1: Write failing test**

```kotlin
package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.*
import org.junit.Assert.*
import org.junit.Test

class WorldReducerTest {
    @Test fun `FACTION_UPDATE tag emits FactionChange Update`() {
        val parsed = ParsedReply.empty().copy(
            factionUpdates = mapOf("court" to FactionUpdateSpec(ruler = "Elenna"))
        )
        val base = Faction(id = "court", name = "Obsidian Court", type = "empire",
            description = "", baseLoc = "", ruler = "Vance")
        val result = WorldReducer.apply(factions = listOf(base), locations = emptyList(), parsed = parsed, currentTurn = 10)
        assertEquals(1, result.factionChanges.size)
        val fc = result.factionChanges[0] as FactionChange.Update
        assertEquals("court", fc.id)
        assertEquals("Elenna", fc.patch.ruler)
    }

    @Test fun `discovered location emits LocationChange SetDiscovered`() {
        val parsed = ParsedReply.empty().copy(discoveredLocations = listOf(3))
        val result = WorldReducer.apply(factions = emptyList(),
            locations = listOf(MapLocation(id=3, name="x", type="t", icon="i", x=0, y=0)),
            parsed = parsed, currentTurn = 10)
        assertEquals(1, result.locationChanges.size)
        val lc = result.locationChanges[0] as LocationChange.SetDiscovered
        assertEquals(3, lc.id)
        assertTrue(lc.discovered)
    }
}
```

**Note:** adjust `ParsedReply.empty()` and `.copy(...)` calls to match the actual `ParsedReply` signature. Grep `data class ParsedReply(` to find fields, then use `.copy(...)` filling required non-default fields.

`FactionUpdateSpec` may need to be added to `data/Models.kt` if not present:

```kotlin
data class FactionUpdateSpec(
    val ruler: String? = null,
    val disposition: String? = null,
    val mood: String? = null,
    val status: String? = null,
    val goal: String? = null
)
```

`ParsedReply` needs fields `factionUpdates: Map<String, FactionUpdateSpec>` and `discoveredLocations: List<Int>` — add them if not present; the narrative-tag parser already emits these (phase 1 / earlier), so plumbing likely exists. Grep `factionUpdates` in `data/Parser*.kt` to confirm.

- [ ] **Step 2: Run — FAIL** (reducer or types don't exist yet)

- [ ] **Step 3: Implement `WorldReducer.kt`**

```kotlin
package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.*

data class WorldApplyResult(
    val factionChanges: List<FactionChange>,
    val locationChanges: List<LocationChange>
)

object WorldReducer {
    fun apply(
        factions: List<Faction>,
        locations: List<MapLocation>,
        parsed: ParsedReply,
        currentTurn: Int
    ): WorldApplyResult {
        val fChanges = mutableListOf<FactionChange>()
        val lChanges = mutableListOf<LocationChange>()

        parsed.factionUpdates.forEach { (id, spec) ->
            if (factions.any { it.id == id }) {
                fChanges += FactionChange.Update(id, FactionPatch(
                    ruler = spec.ruler,
                    disposition = spec.disposition,
                    mood = spec.mood,
                    status = spec.status,
                    goal = spec.goal
                ))
            }
        }
        parsed.discoveredLocations.forEach { locId ->
            if (locations.any { it.id == locId && !it.discovered }) {
                lChanges += LocationChange.SetDiscovered(locId, true)
            }
        }
        return WorldApplyResult(fChanges, lChanges)
    }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Wire into VM**

In `GameViewModel.applyParsed`, call the reducer and stash changes:

```kotlin
val worldResult = WorldReducer.apply(
    factions = ui.value.worldLore?.factions ?: emptyList(),
    locations = ui.value.worldMap.locations,
    parsed = parsed, currentTurn = ui.value.turns
)
// apply in-memory until VM glue task replaces this
_ui.value = ui.value.copy(
    worldMap = ui.value.worldMap.copy(
        locations = applyLocationChangesInMemory(ui.value.worldMap.locations, worldResult.locationChanges).toMutableList()
    ),
    worldLore = ui.value.worldLore?.copy(
        factions = applyFactionChangesInMemory(ui.value.worldLore!!.factions, worldResult.factionChanges)
    )
)
```

Add the two private helpers in `GameViewModel.kt`:

```kotlin
private fun applyFactionChangesInMemory(current: List<Faction>, ops: List<FactionChange>): List<Faction> {
    val map = current.associateBy { it.id }.toMutableMap()
    for (op in ops) when (op) {
        is FactionChange.Insert -> map[op.faction.id] = op.faction
        is FactionChange.Update -> map[op.id]?.let { existing ->
            map[op.id] = existing.copy(
                ruler = op.patch.ruler ?: existing.ruler,
                disposition = op.patch.disposition ?: existing.disposition,
                mood = op.patch.mood ?: existing.mood,
                status = op.patch.status ?: existing.status,
                goal = op.patch.goal ?: existing.goal
            )
        }
    }
    return map.values.toList()
}

private fun applyLocationChangesInMemory(current: List<MapLocation>, ops: List<LocationChange>): List<MapLocation> {
    val list = current.toMutableList()
    for (op in ops) when (op) {
        is LocationChange.Insert -> list.add(op.location)
        is LocationChange.SetDiscovered -> {
            val idx = list.indexOfFirst { it.id == op.id }
            if (idx >= 0) list[idx] = list[idx].copy(discovered = op.discovered)
        }
    }
    return list
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/reducers/WorldReducer.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/Models.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/reducers/WorldReducerTest.kt
git commit -m "feat: add WorldReducer emitting FactionChange and LocationChange"
```

---

## Task 16 — `ArcSummarizer` (TDD with injectable AI call)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/game/ArcSummarizer.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/game/ArcSummarizerTest.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — add `ARC_SUMMARY_SYS`.

- [ ] **Step 1: Add `ARC_SUMMARY_SYS` to Prompts.kt**

```kotlin
const val ARC_SUMMARY_SYS = """You are compressing a sequence of scene summaries into a single arc summary.
Preserve: named characters and their fates, major decisions the player made, faction shifts,
unresolved plot threads, key locations visited.
Omit minor dialogue, transient scenery, weather.
Target ~300 tokens. Output JSON: {"summary":"..."} and nothing else."""
```

- [ ] **Step 2: Write failing test**

```kotlin
package com.realmsoffate.game.game

import com.realmsoffate.game.data.ArcSummary
import com.realmsoffate.game.data.SceneSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ArcSummarizerTest {
    @Test fun `produces ArcSummary spanning turn range from scene list`() = runTest {
        val fakeAi = { _: List<String> -> """{"summary":"arc text"}""" }
        val summarizer = ArcSummarizer(summarize = fakeAi)
        val scenes = listOf(
            SceneSummary(id = 1, turnStart = 1, turnEnd = 5, summary = "a"),
            SceneSummary(id = 2, turnStart = 6, turnEnd = 10, summary = "b")
        )
        val arc = summarizer.run(scenes)
        assertEquals(1, arc.turnStart)
        assertEquals(10, arc.turnEnd)
        assertEquals("arc text", arc.summary)
    }

    @Test fun `falls back to raw concat on JSON parse failure`() = runTest {
        val fakeAi = { _: List<String> -> "not json" }
        val summarizer = ArcSummarizer(summarize = fakeAi)
        val scenes = listOf(SceneSummary(id = 1, turnStart = 1, turnEnd = 5, summary = "a"))
        val arc = summarizer.run(scenes)
        assertTrue(arc.summary.isNotEmpty())
    }
}
```

- [ ] **Step 3: Run — FAIL**

- [ ] **Step 4: Implement**

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/game/ArcSummarizer.kt
package com.realmsoffate.game.game

import com.realmsoffate.game.data.ArcSummary
import com.realmsoffate.game.data.SceneSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ArcSummarizer(
    /** Injectable so tests can supply fakes; production passes an AiRepository lambda. */
    private val summarize: suspend (inputs: List<String>) -> String
) {
    @Serializable private data class Envelope(val summary: String = "")

    suspend fun run(scenes: List<SceneSummary>): ArcSummary {
        require(scenes.isNotEmpty()) { "Cannot roll up zero scenes" }
        val inputs = scenes.map { "T${it.turnStart}-${it.turnEnd}: ${it.summary}" }
        val raw = summarize(inputs)
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val summary = runCatching {
            json.decodeFromString<Envelope>(raw).summary.ifBlank { raw }
        }.getOrElse { raw }.trim()

        return ArcSummary(
            turnStart = scenes.minOf { it.turnStart },
            turnEnd = scenes.maxOf { it.turnEnd },
            summary = summary
        )
    }
}
```

- [ ] **Step 5: Run — PASS**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/ArcSummarizer.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/ArcSummarizerTest.kt
git commit -m "feat: add ArcSummarizer and ARC_SUMMARY_SYS prompt"
```

---

## Task 17 — `SceneSummarizer` persists via repo + triggers rollup

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/SceneSummarizer.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — add `ROLLUP_THRESHOLD` and `ROLLUP_BATCH_SIZE`.
- Modify test: `app/src/test/kotlin/com/realmsoffate/game/game/SceneSummarizerTest.kt`

- [ ] **Step 1: Add constants to Prompts.kt**

```kotlin
const val ROLLUP_THRESHOLD = 20
const val ROLLUP_BATCH_SIZE = 10
```

- [ ] **Step 2: Update `SceneSummarizer` signature**

Change from appending to an in-memory `List<SceneSummary>` to calling `repo.appendSceneSummary` then `maybeRollupArcs`:

```kotlin
class SceneSummarizer(
    private val repo: EntityRepository,
    private val summarize: suspend (List<ChatMsg>) -> String,
    private val arcSummarizer: ArcSummarizer? = null     // may be null if arc rollup disabled
) {
    suspend fun appendAndMaybeRollup(
        summaryText: String,
        turnStart: Int,
        turnEnd: Int,
        location: String
    ) {
        repo.appendSceneSummary(SceneSummary(
            turnStart = turnStart,
            turnEnd = turnEnd,
            location = location,
            summary = summaryText
        ))
        maybeRollupArcs()
    }

    internal suspend fun maybeRollupArcs() {
        val arc = arcSummarizer ?: return
        val count = repo.countUnrolledScenes()
        if (count < com.realmsoffate.game.data.ROLLUP_THRESHOLD) return
        val all = repo.recentSceneSummaries(limit = Int.MAX_VALUE)
            .filter { true }  // already unrolled, recentUnrolled filter applied upstream
            .sortedBy { it.turnEnd }
        val batch = all.take(com.realmsoffate.game.data.ROLLUP_BATCH_SIZE)
        if (batch.size < com.realmsoffate.game.data.ROLLUP_BATCH_SIZE) return
        val arcSummary = arc.run(batch)
        repo.rollupScenes(batch.map { it.id }, arcSummary)
    }
}
```

(Preserve phase 1's summarize call path — this skeleton replaces only the persistence part. Reconcile against the existing SceneSummarizer's run-summarize method and keep its public trigger surface intact.)

- [ ] **Step 3: Update tests**

Mock `EntityRepository` in the existing SceneSummarizer test (use a fake that records `appendSceneSummary` calls). Assert that after append, if unrolled-count reaches threshold, `rollupScenes` is called. Add a test where count is below threshold → no rollup.

- [ ] **Step 4: Run — PASS**

Run: `gradle :app:testDebugUnitTest --tests "com.realmsoffate.game.game.SceneSummarizerTest"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/SceneSummarizer.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/SceneSummarizerTest.kt
git commit -m "feat: SceneSummarizer persists via repo and triggers arc rollup"
```

---

## Task 18 — Prompt builder: CANONICAL FACTS + keyword retrieval

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — directive string + budget constants.
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — `buildUserPrompt` additions.

- [ ] **Step 1: Add directive + budgets to Prompts.kt**

```kotlin
const val CANONICAL_FACTS_DIRECTIVE = """
The CANONICAL FACTS block is ground truth. When you mention any named NPC, faction, or location from that block,
use the facts exactly — do not change names, factions, dispositions, or statuses. To change a fact, emit the
appropriate update tag ([NPC_UPDATE:...], [FACTION_UPDATE:...]) and describe the in-fiction event that caused the change.
""".trim()

const val BUDGET_ARC_SUMMARIES = 1500
const val BUDGET_SCENE_SUMMARIES = 2000
const val BUDGET_KNOWN_NPCS = 600
const val BUDGET_CANONICAL_FACTS = 800
const val BUDGET_RECENT_TURNS = 6000
```

Also append the directive to the system prompt string. Find the existing `SYSTEM_PROMPT` (or similar) in `Prompts.kt` and concatenate the directive onto it. Example:

```kotlin
const val SYSTEM_PROMPT = """..."""  // existing
const val SYSTEM_PROMPT_V2 = "$SYSTEM_PROMPT\n\n$CANONICAL_FACTS_DIRECTIVE"
```

Then ensure `AiRepository` / `GameViewModel` use `SYSTEM_PROMPT_V2` when sending turns.

- [ ] **Step 2: In `GameViewModel.buildUserPrompt`, assemble CANONICAL FACTS**

Find `buildUserPrompt` (around line 872 per phase 1 plan — grep for the function). After the STORY SO FAR + KNOWN NPCs blocks (phase 1), before PLAYER ACTION, inject:

```kotlin
// --- CANONICAL FACTS (phase 2) ---
val canonicalFacts = runBlocking { buildCanonicalFacts(playerInput) }
if (!canonicalFacts.isEmpty) {
    sb.appendLine()
    sb.appendLine(canonicalFacts.render())
}
```

`runBlocking` only because `buildUserPrompt` is currently synchronous; if it's already suspend (phase 1 likely), use direct `await`/suspend call.

Add helper:

```kotlin
private suspend fun buildCanonicalFacts(playerInput: String): CanonicalFacts {
    val turn = ui.value.turns
    val locName = ui.value.worldMap.locations.firstOrNull { it.id == ui.value.currentLoc }?.name ?: ""

    val sceneRelevant = repo.sceneRelevantNpcs(locName, turn)
    val prevNarration = ui.value.history.lastOrNull { it.role == "assistant" }?.content ?: ""
    val tokens = (PromptKeywords.extract(playerInput) + PromptKeywords.extract(prevNarration)).distinct()
    val kw = repo.keywordMatchedEntities(tokens, limit = 15)

    val activeQuests = ui.value.quests.filter { it.status == "active" }
    val questGiverNames = activeQuests.map { it.giver }.filter { it.isNotBlank() }.toSet()
    val questRelevantNpcs = repo.snapshotForReducers().npcs.filter { it.name in questGiverNames }

    val partyNames = ui.value.party.map { it.name }.toSet()
    val partyNpcs = repo.snapshotForReducers().npcs.filter { it.name in partyNames }

    val allNpcs = (sceneRelevant + kw.npcs + partyNpcs + questRelevantNpcs)
        .distinctBy { it.id }
    val allFactions = kw.factions.distinctBy { it.id }
    val allLocations = kw.locations.distinctBy { it.id }

    // Token-budget enforcement.
    val budget = com.realmsoffate.game.data.BUDGET_CANONICAL_FACTS
    val npcs = trimToBudget(allNpcs) { it.name + it.thoughts }.takeTokens(budget / 2)
    val factions = allFactions.takeTokens(budget / 4)
    val locations = allLocations.takeTokens(budget / 4)

    return CanonicalFacts(npcs, factions, locations)
}

// helper extension used for rough token-budget truncation
private fun <T> List<T>.takeTokens(maxTokens: Int, weight: (T) -> Int = { 60 }): List<T> {
    var sum = 0; val out = mutableListOf<T>()
    for (x in this) {
        val w = weight(x); if (sum + w > maxTokens) break
        out += x; sum += w
    }
    return out
}
```

(The `60`-token-per-entity default is a rough estimate; tune later. `TokenEstimate.ofText(CanonicalFacts(...).render())` can be used for precise truncation in a follow-up.)

- [ ] **Step 3: Runtime sanity — type checks / compile**

Run: `gradle :app:compileDebugKotlin`

- [ ] **Step 4: Verify phase 1 tests still pass**

Run: `gradle :app:testDebugUnitTest`
Expected: all green. No test added for the prompt builder itself in this task — runtime verification in Task 24 covers it.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "feat: inject CANONICAL FACTS with keyword retrieval into user prompt"
```

---

## Task 19 — Tag parser tightening: auto-tag unknown proper nouns

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Parser*.kt` (grep for the tag parser; likely `ParseReply.kt` or similar)
- Or modify: `GameViewModel.applyParsed` — post-parse scan.

- [ ] **Step 1: Locate the post-parse injection point**

Run: `grep -rn "npcsMet" app/src/main/kotlin/com/realmsoffate/game/` to find where `ParsedReply.npcsMet` is produced. The auto-tag sits either at the tail of parsing (adding entries to `npcsMet`) or in `GameViewModel.applyParsed` right before `NpcLogReducer.apply`.

Prefer the former — keep reducers clean.

- [ ] **Step 2: Implement helper**

Create `app/src/main/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcs.kt`:

```kotlin
package com.realmsoffate.game.data

/**
 * Scan AI narration for capitalized two-word proper nouns (likely NPC names)
 * that are not already referenced by a [NPC:...] tag and are not common English
 * words. Emit synthetic NpcSpec entries so they enter the journal.
 */
object AutoTagUnknownNpcs {
    private val COMMON = setOf(
        "The","And","But","When","Where","What","Why","How","Who","Which",
        "You","He","She","It","They","We","I","Me","My","His","Her","Their",
        "Yes","No","Okay","Well","Still","Then","Now","Here","There"
    )

    private val PROPER_NOUN = Regex("\\b([A-Z][a-z]{2,}(?: [A-Z][a-z]{2,}){0,2})\\b")

    fun scan(
        narration: String,
        existingNpcs: List<LogNpc>,
        currentLoc: String,
        turn: Int
    ): List<NpcSpec> {
        val existingNames = existingNpcs.map { it.name.lowercase() }.toSet()
        val out = mutableListOf<NpcSpec>()
        for (m in PROPER_NOUN.findAll(narration)) {
            val name = m.groupValues[1]
            val head = name.substringBefore(' ')
            if (head in COMMON) continue
            if (name.lowercase() in existingNames) continue
            if (out.any { it.name.equals(name, ignoreCase = true) }) continue
            out += NpcSpec(
                name = name,
                race = "",
                role = "",
                relationship = "neutral",
                location = currentLoc
            )
        }
        return out
    }
}
```

Check: `NpcSpec` is the type used by `ParsedReply.npcsMet`. If the existing field is named differently (e.g. `NpcMetSpec`), grep for it and align.

- [ ] **Step 3: Wire into parser or VM**

In `GameViewModel.applyParsed`, right after `parsed = parser.parse(...)`, merge auto-tagged NPCs:

```kotlin
val narration = parsed.narration ?: ""
val existingNpcs = ui.value.npcLog
val autoTags = AutoTagUnknownNpcs.scan(narration, existingNpcs,
    currentLoc = locName, turn = ui.value.turns)
val parsedAug = parsed.copy(npcsMet = parsed.npcsMet + autoTags)
// ... use parsedAug downstream ...
```

Adjust field names to match actual `ParsedReply` + `NpcSpec` definitions.

- [ ] **Step 4: Write a test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcsTest.kt`:

```kotlin
package com.realmsoffate.game.data

import org.junit.Assert.*
import org.junit.Test

class AutoTagUnknownNpcsTest {
    @Test fun `detects two-word proper noun`() {
        val specs = AutoTagUnknownNpcs.scan(
            narration = "You meet Mira Cole at the inn.",
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 5
        )
        assertEquals(1, specs.size)
        assertEquals("Mira Cole", specs[0].name)
    }

    @Test fun `skips existing NPCs`() {
        val specs = AutoTagUnknownNpcs.scan(
            narration = "Mira Cole smiles.",
            existingNpcs = listOf(LogNpc(id="m", name="Mira Cole", metTurn=1, lastSeenTurn=1)),
            currentLoc = "Hightower", turn = 5
        )
        assertTrue(specs.isEmpty())
    }

    @Test fun `skips common words at start`() {
        val specs = AutoTagUnknownNpcs.scan(
            narration = "The Queen rules here.",
            existingNpcs = emptyList(),
            currentLoc = "Hightower", turn = 5
        )
        assertTrue(specs.none { it.name.startsWith("The ") })
    }
}
```

- [ ] **Step 5: Run — PASS**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcs.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcsTest.kt
git commit -m "feat: auto-tag unknown proper nouns in AI narration"
```

---

## Task 20 — `SaveRofZip` utility (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/SaveRofZip.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/data/SaveRofZipTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.realmsoffate.game.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveRofZipTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `zip and unzip roundtrip preserves files`() {
        val workdir = tmp.newFolder("in")
        val saveJson = File(workdir, "save.json").apply { writeText("""{"v":1}""") }
        val manifestJson = File(workdir, "manifest.json").apply { writeText("""{"version":3}""") }
        val dbFile = File(workdir, "realms.db").apply { writeBytes(ByteArray(256) { it.toByte() }) }

        val zipOut = tmp.newFile("slot.rofsave")
        SaveRofZip.write(zipOut, mapOf(
            "save.json" to saveJson,
            "manifest.json" to manifestJson,
            "realms.db" to dbFile
        ))

        val outDir = tmp.newFolder("out")
        SaveRofZip.extract(zipOut, outDir)

        val readBack = File(outDir, "realms.db").readBytes()
        assertArrayEquals(dbFile.readBytes(), readBack)
        assertEquals("""{"v":1}""", File(outDir, "save.json").readText())
    }

    @Test fun `readManifest returns json without full extract`() {
        val workdir = tmp.newFolder("in")
        val manifestJson = File(workdir, "manifest.json").apply {
            writeText("""{"version":3,"savedAt":"2026-04-21"}""")
        }
        val zipOut = tmp.newFile("slot.rofsave")
        SaveRofZip.write(zipOut, mapOf("manifest.json" to manifestJson))
        val text = SaveRofZip.readManifest(zipOut)
        assertTrue(text.contains("\"version\":3"))
    }
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/data/SaveRofZip.kt
package com.realmsoffate.game.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Read/write .rofsave zip containers for save slots. */
object SaveRofZip {
    fun write(out: File, files: Map<String, File>) {
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            for ((name, src) in files) {
                if (!src.exists()) continue
                zos.putNextEntry(ZipEntry(name))
                src.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    fun extract(zip: File, destDir: File) {
        destDir.mkdirs()
        ZipFile(zip).use { zf ->
            zf.entries().asSequence().forEach { entry ->
                val target = File(destDir, entry.name)
                target.parentFile?.mkdirs()
                zf.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { out -> input.copyTo(out) }
                }
            }
        }
    }

    /** Read just the manifest.json without extracting other entries. */
    fun readManifest(zip: File): String {
        ZipFile(zip).use { zf ->
            val entry = zf.getEntry("manifest.json") ?: return ""
            return zf.getInputStream(entry).bufferedReader().use { it.readText() }
        }
    }
}
```

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/SaveRofZip.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/SaveRofZipTest.kt
git commit -m "feat: add SaveRofZip utility for .rofsave container files"
```

---

## Task 21 — `SaveService` v3 format + v2 → v3 migration (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/SaveStore.kt`
- Create test: `app/src/test/kotlin/com/realmsoffate/game/game/handlers/SaveServiceV3Test.kt`

- [ ] **Step 1: Bump SaveData.version**

In `data/Models.kt` change `SaveData.version`: default from 2 to 3. Keep the field nullable-in-parser so old v2 JSON still loads.

- [ ] **Step 2: Update `SaveService` save path**

Replace any `writeJson(slotFile, saveJsonText)` with a new flow:
1. Serialize non-entity `SaveData` (character, worldMap, party, currentLoc, etc.) to `save.json` in a temp dir.
2. Build `manifest.json` in the same dir: `{"version":3,"savedAt":"<ts>","character":"<name>"}`.
3. Copy Room DB file (`context.getDatabasePath("realms.db")`) into the temp dir as `realms.db` (also copy `-shm`, `-wal` if present).
4. Call `SaveRofZip.write(slotFile(slotId), mapOf("save.json" to .., "manifest.json" to .., "realms.db" to ..))`.
5. Delete temp dir.

- [ ] **Step 3: Update `SaveService` load path**

1. If slot file is `.rofsave` → extract to a temp dir, parse `manifest.json`, read `save.json` into `SaveData`, copy `realms.db` into place (close RealmsDb singleton first, overwrite, reopen). Do NOT run `seedFromSaveData` — Room already has the state.
2. If slot file is legacy `.json` → parse → `seedFromSaveData(save)` → atomically re-save as `.rofsave` → back up original as `<slot>.v2.bak.json` → delete original `.json`.

- [ ] **Step 4: Write a failing Robolectric test**

```kotlin
package com.realmsoffate.game.game.handlers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.RoomEntityRepository
import com.realmsoffate.game.data.SaveData
import com.realmsoffate.game.data.db.RealmsDb
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SaveServiceV3Test {
    @Test fun `save and load round-trips through rofsave`() = runTest {
        // Minimal: save small SaveData, assert .rofsave is produced, load it back,
        // assert Character name matches.
        // (Concrete impl left for the engineer — leverage SaveService API as-is.)
    }

    @Test fun `v2 legacy JSON migrates to rofsave on first load`() = runTest {
        // Write a fixture v2 SaveData JSON to a slot path (use Json.encodeToString on a fixture),
        // call SaveService.load(slot), assert .rofsave exists afterwards, and .v2.bak.json exists.
    }
}
```

The test bodies depend on the exact `SaveService` API — grep the current interface (`load(slot)`, `save(slot, SaveData)`, …) and fill in. If the current API is on a companion object or on `SaveStore`, adapt accordingly.

- [ ] **Step 5: Implement — run until PASS**

Iterate: compile, run test, fix, repeat. Key pitfalls:
- Closing Room before overwriting `.db` is critical — otherwise Room may hold file handles.
- On migration, write `v2.bak` before the slot `.rofsave` — if zip write fails mid-way, the user still has the backup.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/handlers/SaveService.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/SaveStore.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/Models.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/handlers/SaveServiceV3Test.kt
git commit -m "feat: v3 .rofsave save format with v2 JSON migration"
```

---

## Task 22 — `GameViewModel` glue: snapshot → reducers → `applyChanges`

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt`

This task removes the transitional in-memory apply helpers added in Tasks 13/14/15 and routes through the repository instead.

- [ ] **Step 1: Inject `EntityRepository` into `GameViewModel`**

Update the VM constructor / factory. If `GameViewModel` is currently no-arg with lazy init, add a `val repo: EntityRepository` property constructed from a `RealmsDb` singleton opened at app startup (see `MainActivity` / `RealmsApplication`). Wire it through the existing factory pattern.

- [ ] **Step 2: Replace `applyParsed` interior**

In `applyParsed`, replace the three transitional blocks with:

```kotlin
val snapshot = repo.snapshotForReducers()

val npcResult = NpcLogReducer.apply(
    currentNpcs = snapshot.npcs,
    combat = ui.value.combat,
    parsed = parsedAug,
    currentTurn = ui.value.turns,
    currentLocName = locName
)
val questResult = QuestAndPartyReducer.apply(
    currentQuests = snapshot.quests,
    party = ui.value.party,
    parsed = parsedAug,
    currentTurn = ui.value.turns
)
val worldResult = WorldReducer.apply(
    factions = snapshot.factions,
    locations = snapshot.locations,
    parsed = parsedAug,
    currentTurn = ui.value.turns
)

val changes = EntityChanges(
    npcs = npcResult.npcChanges,
    quests = questResult.questChanges,
    factions = worldResult.factionChanges,
    locations = worldResult.locationChanges
)
repo.applyChanges(changes)

// Drop the applyXChangesInMemory helpers and the ui.value.copy(npcLog = ...) lines.
// UI now observes repo flows (Task 23).
```

Retain existing systemMessages / timelineEntries / combat side-effects — those still flow through `_ui.value`.

- [ ] **Step 3: Remove dead code**

Delete `applyNpcChangesInMemory`, `applyQuestChangesInMemory`, `applyFactionChangesInMemory`, `applyLocationChangesInMemory`. No longer needed.

- [ ] **Step 4: Compile + run tests**

Run: `gradle :app:compileDebugKotlin && gradle :app:testDebugUnitTest`

Reducer tests stay green (they assert on `EntityChanges`). VM-level tests (if any) may need updates.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "refactor: route reducer output through EntityRepository.applyChanges"
```

---

## Task 23 — UI panels observe repo flows

**Files:**
- Modify: UI panels under `app/src/main/kotlin/com/realmsoffate/game/ui/panels/` (grep for readers of `ui.value.npcLog`, `ui.value.quests`, `ui.value.worldLore?.factions`, `ui.value.worldLore?.npcs`, `ui.value.worldMap.locations`).
- Modify: `GameUiState` — remove the redundant fields (or deprecate with a TODO for Phase 3).

- [ ] **Step 1: Grep for readers**

```bash
grep -rn "ui.value.npcLog\|\.npcLog\|\.quests\|worldLore?.factions\|worldLore?.npcs\|worldMap.locations" app/src/main/kotlin/com/realmsoffate/game/ui/
```

- [ ] **Step 2: For each panel, switch to `collectAsState`**

Example (NPC Journal):

```kotlin
@Composable
fun NpcJournalPanel(viewModel: GameViewModel) {
    val npcs by viewModel.repo.observeLoggedNpcs().collectAsState(initial = emptyList())
    // render using `npcs` instead of viewModel.ui.collectAsState().value.npcLog
    ...
}
```

Expose `repo` via a getter on `GameViewModel` for convenience:

```kotlin
val entityRepo: EntityRepository get() = repo
```

- [ ] **Step 3: Quest log panel, Factions panel, Lore panel (NPCs + Factions), Map overlay**

Apply the same pattern. Each becomes a one-line change for its list source.

- [ ] **Step 4: Remove `npcLog`, `quests`, lists from `GameUiState`**

Once all UI readers are migrated, delete these fields from `GameUiState`:
- `npcLog: List<LogNpc>`
- `quests: List<Quest>`
- Consider keeping `worldLore` lists populated-but-deprecated for SaveData-JSON compatibility during export (they're written from repo snapshot on save).

If removing breaks compile sites not yet migrated, grep and migrate them.

- [ ] **Step 5: Compile + run tests**

Run: `gradle :app:compileDebugKotlin && gradle :app:testDebugUnitTest && gradle :app:lint`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/ \
        app/src/main/kotlin/com/realmsoffate/game/data/Models.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "refactor: UI panels observe EntityRepository flows directly"
```

---

## Task 24 — Debug bridge endpoints

**Files:**
- Modify: `app/src/debug/kotlin/com/realmsoffate/game/debug/` — add/extend HTTP routes.

- [ ] **Step 1: Locate debug bridge route registration**

Run: `grep -rn "GET /state\|\"/state\"" app/src/debug/kotlin/`

Identify the file that registers HTTP routes.

- [ ] **Step 2: Extend `/state`**

Add to the JSON returned:

```kotlin
"arcSummaries" to repo.allArcSummaries(),
"canonicalFactsPreview" to buildCanonicalFactsForDebug()
```

`buildCanonicalFactsForDebug()` mirrors the VM's prompt-builder helper (extract into a shared helper if needed).

- [ ] **Step 3: Add `/lastPrompt` endpoint**

Return the last `userPromptSent` field from the most recent `DebugTurn`. Phase 1 already captures this in `DebugTurn` entries.

```kotlin
get("/lastPrompt") {
    val last = ui.value.debugLog.lastOrNull()
    respond(mapOf(
        "turn" to (last?.turn ?: 0),
        "userPromptSent" to (last?.userPromptSent ?: "")
    ))
}
```

- [ ] **Step 4: Add `/repo/npcs`**

```kotlin
get("/repo/npcs") {
    val q = request.queryParam("q") ?: ""
    val tokens = PromptKeywords.extract(q)
    val hits = repo.keywordMatchedEntities(tokens, limit = 15)
    respond(mapOf(
        "query" to q,
        "tokens" to tokens,
        "npcs" to hits.npcs,
        "factions" to hits.factions,
        "locations" to hits.locations
    ))
}
```

- [ ] **Step 5: Add `/repo/stats`**

```kotlin
get("/repo/stats") {
    respond(mapOf(
        "npcs" to repo.snapshotForReducers().npcs.size,
        "quests" to repo.snapshotForReducers().quests.size,
        "factions" to repo.snapshotForReducers().factions.size,
        "locations" to repo.snapshotForReducers().locations.size,
        "sceneSummariesUnrolled" to repo.countUnrolledScenes(),
        "arcSummaries" to repo.allArcSummaries().size
    ))
}
```

- [ ] **Step 6: Compile**

Run: `gradle :app:compileDebugKotlin`

- [ ] **Step 7: Commit**

```bash
git add app/src/debug/kotlin/
git commit -m "feat: debug bridge endpoints /lastPrompt, /repo/npcs, /repo/stats, /state additions"
```

---

## Task 25 — Deploy + runtime verification

**Files:** none (deploy + observe)

Follow `.cursor/rules/debug-bridge-test-procedures.mdc` P0 + P1 as the minimum, plus the phase-2-specific procedures.

- [ ] **Step 1: Clean build**

Run: `gradle clean assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Deploy**

```bash
gradle installDebug && \
  adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && \
  adb -s emulator-5554 forward tcp:8735 tcp:8735
```

- [ ] **Step 3: P0 — boot sanity**

- Start new game.
- First narration appears without crash.
- `curl -s http://localhost:8735/describe` returns HTTP 200.

- [ ] **Step 4: P1 — free-action turn**

- Enter "look around"; AI responds.
- `curl -s http://localhost:8735/lastPrompt | jq '.userPromptSent' | head -50` — prompt contains expected blocks (STORY SO FAR, KNOWN NPCs).

- [ ] **Step 5: P2 — scene transitions**

- Play 3-5 turns, travel to a new location.
- Within ~15s: `curl -s http://localhost:8735/repo/stats | jq '.sceneSummariesUnrolled'` ≥ 1.

- [ ] **Step 6: P5 — save/load roundtrip**

- Save to slot 1.
- Inspect filesystem (via adb): `adb shell run-as com.realmsoffate.game ls files/saves/` should show `slot_1.rofsave`.
- Kill app, reload from slot 1.
- `curl /repo/stats` counts match pre-kill state.

- [ ] **Step 7: P10 — keyword retrieval (NEW)**

- Meet NPC "Vesper Vance" at turn 5 (prompt AI to introduce her in a scene).
- Play 30+ turns without mentioning her; verify `/repo/npcs?q=vesper` returns her record.
- Enter "what happened to Vesper?" as player turn.
- `curl /lastPrompt | jq '.userPromptSent'` — confirm "Vesper Vance" appears in CANONICAL FACTS block.

- [ ] **Step 8: P11 — arc rollup (NEW)**

- Play 20+ scene transitions (travel, combat ending, location changes).
- `curl /repo/stats | jq '.arcSummaries'` ≥ 1.
- `curl /state | jq '.arcSummaries[0]'` — has non-empty summary text.

- [ ] **Step 9: P12 — canonical anti-drift (NEW)**

- Inspect a faction in the Lore panel; note ruler/disposition.
- Play 5 turns across varied scenes.
- `curl /lastPrompt | jq '.userPromptSent'` — CANONICAL FACTS block contains the faction with matching ruler.
- Inspect the AI narration over those 5 turns for ruler / disposition consistency. Log any drift; file as a follow-up bug but do not block merge if the injection is working.

- [ ] **Step 10: Legacy v2 migration smoke test**

- Restore an existing v2 `save.json` into the emulator's app data (via adb push).
- Launch app; attempt to load the slot.
- Expected: automatic migration to `.rofsave`, original backed up as `.v2.bak.json`. UI continues with state intact (NPCs, quests, factions visible in their panels, loaded from Room).

- [ ] **Step 11: Commit verification notes**

No code; this task is a checklist pass. If any step fails, treat as a bug and fix before declaring Phase 2 complete.

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Room DB for narrative entities → Tasks 3, 4
- ✅ Merged NPC table (LogNpc + LoreNpc) → Tasks 3, 5, 12
- ✅ EntityRepository single gateway → Tasks 7, 8, 9, 10, 12
- ✅ Flow-based UI observation → Tasks 7, 23
- ✅ Reducers emit EntityChanges → Tasks 13, 14, 15
- ✅ CANONICAL FACTS block → Tasks 11, 18
- ✅ Keyword retrieval → Tasks 2, 8, 18
- ✅ Hierarchical summary compression → Tasks 16, 17
- ✅ `.rofsave` zip save format → Tasks 20, 21
- ✅ v2 → v3 migration → Tasks 12, 21
- ✅ Tag parser tightening / auto-tag → Task 19
- ✅ Debug bridge endpoints → Task 24
- ✅ Runtime verification (P0, P1, P2, P5, P10, P11, P12) → Task 25

**Placeholder scan:**
- "TBD" / "TODO" / "fill in later": none.
- Two code locations intentionally reference the engineer adapting: (a) Task 12 helper `fixtureSave` may need field reorder against current `SaveData` signature — documented as a grep step. (b) Task 19 uses `NpcSpec` naming which matches the current parser field; if naming drifts, grep-and-replace documented.
- Task 21 test bodies are described in intent (test names + purpose) rather than full code because `SaveService`'s current public API is not visible at plan time and the engineer must read the file to write correct fixtures. This is acknowledged explicitly in-step.

**Type consistency:**
- `NpcChange.Insert / InsertLore / Update / MarkDead` used identically in Tasks 6, 9, 13.
- `NpcPatch` field names match between Task 6, 9 (`mergePatch`), 13 (in-memory helper).
- `QuestChange.Insert / Update`; `QuestPatch.objectiveCompleted` index used consistently in 6, 9, 14.
- `FactionChange / FactionPatch` — fields match in Tasks 6, 9, 15.
- `LocationChange.Insert / SetDiscovered` — identical across Tasks 6, 9, 15.
- `EntitySnapshot` field names (`npcs, quests, factions, locations`) consistent Tasks 6, 8, 22.
- `KeywordHits` shape consistent Tasks 8, 18, 24.
- `EntityRepository` method signatures (`observeLoggedNpcs`, `sceneRelevantNpcs`, `keywordMatchedEntities`, `applyChanges`, etc.) consistent across all tasks.
- `ROLLUP_THRESHOLD / ROLLUP_BATCH_SIZE` referenced only in Task 17 — single definition site.

**Known risks to mitigate during execution:**
1. Room + KSP + Kotlin 2.2.10 version alignment — if KSP version `2.2.10-2.0.2` fails, try `2.2.10-2.0.1` or the latest patch on maven central; do not upgrade Kotlin.
2. Task 17 integrates with an existing `SceneSummarizer` that already has its own summarize path. Do not destroy that path — only redirect persistence from in-memory list to `repo.appendSceneSummary`. Anchor on the `append` callsite via grep rather than rewriting the file wholesale.
3. Task 21 (save migration) touches the most fragile path — always write the `v2.bak.json` before overwriting. Consider a feature flag in `BuildConfig` to disable auto-migration if it turns out to corrupt saves in testing; remove the flag once stable.
4. Task 23 (UI migration) is mechanical but wide — if a single panel breaks, recovery is one `git revert` per commit. Keep each panel's migration as its own commit if possible.
5. The `AutoTagUnknownNpcs` regex is conservative by design; false negatives (missing real NPCs) are acceptable, false positives (creating ghost entries) are not. If play-testing shows too many ghost rows, tighten the regex before loosening.

---

## Phase 3 preview (not in scope)

- Character / inventory / gold / combat / shop / status — in-memory in Phase 2, moved to Room + authoritative reducers in Phase 3.
- Vector / semantic retrieval over summaries and NPCs (Room FTS4 or embedding-backed index).
- Arc → era compression for multi-year playthroughs (> 5,000 turns).
- Contradiction detector / repair loop — a validator pass after each AI turn cross-checking narration against canonical facts.
