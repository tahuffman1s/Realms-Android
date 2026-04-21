package com.realmsoffate.game.data

/** Diff produced by reducers and applied by the repository in one transaction. */
data class EntityChanges(
    val npcs: List<NpcChange> = emptyList(),
    val quests: List<QuestChange> = emptyList(),
    val factions: List<FactionChange> = emptyList(),
    val locations: List<LocationChange> = emptyList()
) {
    val isEmpty: Boolean
        get() = npcs.isEmpty() && quests.isEmpty() && factions.isEmpty() && locations.isEmpty()
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
    val objectiveCompleted: Int? = null
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
