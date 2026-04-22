package com.realmsoffate.game.data.db

import com.realmsoffate.game.data.EconomyInfo
import com.realmsoffate.game.data.Faction
import com.realmsoffate.game.data.GovernmentInfo
import com.realmsoffate.game.data.IdGen
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.LoreNpc
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.data.Quest
import com.realmsoffate.game.data.ArcSummary
import com.realmsoffate.game.data.SceneSummary
import com.realmsoffate.game.data.db.entities.ArcSummaryEntity
import com.realmsoffate.game.data.db.entities.FactionEntity
import com.realmsoffate.game.data.db.entities.LocationEntity
import com.realmsoffate.game.data.db.entities.NpcEntity
import com.realmsoffate.game.data.db.entities.QuestEntity
import com.realmsoffate.game.data.db.entities.SceneSummaryEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object Mappers {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val stringList = ListSerializer(String.serializer())
    private val boolList = ListSerializer(Boolean.serializer())

    fun tokensFor(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")

    private fun slug(name: String): String = IdGen.forName(name, emptySet())

    // --- NPC (LogNpc side) -------------------------------------------------
    fun toEntity(n: LogNpc): NpcEntity = NpcEntity(
        id = n.id.ifBlank { slug(n.name) },
        name = n.name,
        nameTokens = tokensFor(n.name),
        race = n.race.ifBlank { null },
        role = n.role.ifBlank { null },
        age = n.age.ifBlank { null },
        appearance = n.appearance.ifBlank { null },
        personality = n.personality.ifBlank { null },
        faction = n.faction,
        homeLocation = null,
        discovery = when (n.status) {
            "dead" -> "dead"
            "missing" -> "missing"
            else -> "met"
        },
        relationship = n.relationship,
        thoughts = n.thoughts.ifBlank { null },
        lastLocation = n.lastLocation.ifBlank { null },
        metTurn = n.metTurn,
        lastSeenTurn = n.lastSeenTurn,
        dialogueHistoryJson = json.encodeToString(stringList, n.dialogueHistory.toList()),
        memorableQuotesJson = json.encodeToString(stringList, n.memorableQuotes.toList()),
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
            dialogueHistory = (e.dialogueHistoryJson?.let { json.decodeFromString(stringList, it) } ?: emptyList()).toMutableList(),
            memorableQuotes = (e.memorableQuotesJson?.let { json.decodeFromString(stringList, it) } ?: emptyList()).toMutableList(),
            relationshipNote = e.relationshipNote ?: "",
            status = e.status
        )
    }

    // --- NPC (LoreNpc side) ------------------------------------------------
    fun toEntity(n: LoreNpc): NpcEntity = NpcEntity(
        id = n.id.ifBlank { slug(n.name) },
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
        objectivesJson = json.encodeToString(stringList, q.objectives.toList()),
        completedJson = json.encodeToString(boolList, q.completed.toList()),
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
        objectives = json.decodeFromString(stringList, e.objectivesJson).toMutableList(),
        completed = json.decodeFromString(boolList, e.completedJson).toMutableList(),
        reward = e.reward,
        status = e.status,
        turnStarted = e.turnStarted,
        turnCompleted = e.turnCompleted
    )

    // --- Faction -----------------------------------------------------------
    fun toEntity(f: Faction) = FactionEntity(
        id = f.id.ifBlank { slug(f.name) },
        name = f.name,
        type = f.type,
        description = f.description,
        baseLoc = f.baseLoc,
        color = f.color,
        governmentJson = f.government?.let { json.encodeToString(GovernmentInfo.serializer(), it) },
        economyJson = f.economy?.let { json.encodeToString(EconomyInfo.serializer(), it) },
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
        government = e.governmentJson?.let { json.decodeFromString(GovernmentInfo.serializer(), it) },
        economy = e.economyJson?.let { json.decodeFromString(EconomyInfo.serializer(), it) },
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

    // --- Scene / Arc summary ----------------------------------------------
    fun toEntity(s: SceneSummary) = SceneSummaryEntity(
        id = s.id,
        turnStart = s.turnStart,
        turnEnd = s.turnEnd,
        sceneName = s.sceneName,
        location = s.locationName,
        summary = s.summary,
        keyFactsJson = json.encodeToString(stringList, s.keyFacts),
        createdAt = s.createdAt,
        arcId = null
    )

    fun toSceneSummary(e: SceneSummaryEntity) = SceneSummary(
        turnStart = e.turnStart,
        turnEnd = e.turnEnd,
        sceneName = e.sceneName,
        locationName = e.location,
        summary = e.summary,
        keyFacts = runCatching { json.decodeFromString(stringList, e.keyFactsJson) }.getOrDefault(emptyList()),
        id = e.id,
        createdAt = e.createdAt
    )

    fun toEntity(a: ArcSummary) = ArcSummaryEntity(
        id = a.id,
        turnStart = a.turnStart,
        turnEnd = a.turnEnd,
        summary = a.summary,
        createdAt = a.createdAt
    )

    fun toArcSummary(e: ArcSummaryEntity) = ArcSummary(
        id = e.id,
        turnStart = e.turnStart,
        turnEnd = e.turnEnd,
        summary = e.summary,
        createdAt = e.createdAt
    )
}
