package com.realmsoffate.game.data

import androidx.room.withTransaction
import com.realmsoffate.game.data.db.Mappers
import com.realmsoffate.game.data.db.RealmsDb
import com.realmsoffate.game.data.db.entities.NpcEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class RoomEntityRepository(private val db: RealmsDb) : EntityRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringList = ListSerializer(String.serializer())

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

    override suspend fun snapshotForReducers(): EntitySnapshot {
        val npcs = db.npcDao().getAllLogged().mapNotNull(Mappers::toLogNpc)
        val quests = db.questDao().getAll().map(Mappers::toQuest)
        val factions = db.factionDao().getAll().map(Mappers::toFaction)
        val locations = db.locationDao().getAll().map(Mappers::toMapLocation)
        return EntitySnapshot(npcs, quests, factions, locations)
    }

    override suspend fun sceneRelevantNpcs(
        location: String,
        currentTurn: Int,
        withinTurns: Int
    ): List<LogNpc> {
        val minTurn = (currentTurn - withinTurns).coerceAtLeast(0)
        return db.npcDao().sceneRelevant(location, minTurn).mapNotNull(Mappers::toLogNpc)
    }

    override suspend fun keywordMatchedEntities(tokens: List<String>, limit: Int): KeywordHits {
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

    override suspend fun applyChanges(changes: EntityChanges) {
        if (changes.isEmpty) return
        db.withTransaction {
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
                    dao.upsertOne(mergePatch(existing, op.patch))
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

    private fun mergePatch(e: NpcEntity, p: NpcPatch): NpcEntity {
        val existingDialogue: List<String> =
            e.dialogueHistoryJson?.let { json.decodeFromString(stringList, it) } ?: emptyList()
        val existingQuotes: List<String> =
            e.memorableQuotesJson?.let { json.decodeFromString(stringList, it) } ?: emptyList()
        val newDialogue = (existingDialogue + p.appendDialogue).takeLast(20)
        val newQuotes = if (p.appendMemorableQuote != null)
            (existingQuotes + p.appendMemorableQuote).takeLast(12)
        else existingQuotes
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
                is FactionChange.Insert -> dao.upsertOne(Mappers.toEntity(op.faction))
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
                    dao.upsertOne(Mappers.toEntity(updated))
                }
            }
        }
    }

    private suspend fun applyLocationChanges(ops: List<LocationChange>) {
        if (ops.isEmpty()) return
        val dao = db.locationDao()
        for (op in ops) {
            when (op) {
                is LocationChange.Insert -> dao.upsertOne(Mappers.toEntity(op.location))
                is LocationChange.SetDiscovered -> {
                    val existing = dao.getById(op.id) ?: continue
                    dao.upsertOne(existing.copy(discovered = if (op.discovered) 1 else 0))
                }
            }
        }
    }

    override suspend fun clear() {
        db.withTransaction {
            db.npcDao().clear()
            db.questDao().clear()
            db.factionDao().clear()
            db.locationDao().clear()
            db.sceneSummaryDao().clear()
            db.arcSummaryDao().clear()
        }
    }

    override suspend fun appendSceneSummary(s: SceneSummary): Long =
        db.sceneSummaryDao().insert(Mappers.toEntity(s))

    override suspend fun recentSceneSummaries(limit: Int): List<SceneSummary> =
        db.sceneSummaryDao().recentUnrolled(limit).map(Mappers::toSceneSummary)

    override suspend fun countUnrolledScenes(): Int =
        db.sceneSummaryDao().countUnrolled()

    override suspend fun allArcSummaries(): List<ArcSummary> =
        db.arcSummaryDao().allNewestFirst().map(Mappers::toArcSummary)

    override suspend fun rollupScenes(sceneIds: List<Long>, arc: ArcSummary) {
        db.withTransaction {
            val arcId = db.arcSummaryDao().insert(Mappers.toEntity(arc))
            db.sceneSummaryDao().assignArcId(sceneIds, arcId)
        }
    }

    override suspend fun seedFromSaveData(save: SaveData) {
        db.withTransaction {
            clearInline()

            val mergedNpcs = mutableMapOf<String, com.realmsoffate.game.data.db.entities.NpcEntity>()
            save.worldLore?.npcs?.forEach { lore ->
                val entity = Mappers.toEntity(lore)
                mergedNpcs[IdGen.nameKey(lore.name)] = entity
            }
            save.npcLog.forEach { log ->
                mergedNpcs[IdGen.nameKey(log.name)] = Mappers.toEntity(log)
            }
            if (mergedNpcs.isNotEmpty()) db.npcDao().upsert(mergedNpcs.values.toList())

            save.worldLore?.factions?.takeIf { it.isNotEmpty() }?.let { fs ->
                db.factionDao().upsert(fs.map(Mappers::toEntity))
            }
            if (save.worldMap.locations.isNotEmpty()) {
                db.locationDao().upsert(save.worldMap.locations.map(Mappers::toEntity))
            }
            if (save.quests.isNotEmpty()) {
                db.questDao().upsert(save.quests.map(Mappers::toEntity))
            }
            for (s in save.sceneSummaries) {
                db.sceneSummaryDao().insert(Mappers.toEntity(s).copy(id = 0))
            }
        }
    }

    private suspend fun clearInline() {
        db.npcDao().clear()
        db.questDao().clear()
        db.factionDao().clear()
        db.locationDao().clear()
        db.sceneSummaryDao().clear()
        db.arcSummaryDao().clear()
    }

    override suspend fun exportToSaveData(base: SaveData): SaveData {
        val snap = snapshotForReducers()
        val lore = db.npcDao().observeLore().first().map(Mappers::toLoreNpc)
        val scenes = db.sceneSummaryDao().getAll().map(Mappers::toSceneSummary)
        val mergedMap = base.worldMap.copy(locations = snap.locations.toMutableList())
        return base.copy(
            npcLog = snap.npcs,
            quests = snap.quests,
            worldLore = base.worldLore?.copy(factions = snap.factions, npcs = lore),
            worldMap = mergedMap,
            sceneSummaries = scenes
        )
    }
}
