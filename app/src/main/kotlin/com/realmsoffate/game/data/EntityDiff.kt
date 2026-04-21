package com.realmsoffate.game.data

/**
 * Computes [EntityChanges] from before/after list snapshots produced by reducers.
 *
 * Phase 2 keeps the existing reducers as List<T> transforms (no rewrite). The VM
 * invokes them, then calls [computeAll] with old/new lists to produce a diff the
 * repository can apply transactionally.
 */
object EntityDiff {
    fun computeAll(
        oldNpcs: List<LogNpc>,
        newNpcs: List<LogNpc>,
        oldQuests: List<Quest>,
        newQuests: List<Quest>,
        oldFactions: List<Faction>,
        newFactions: List<Faction>,
        oldLocations: List<MapLocation>,
        newLocations: List<MapLocation>
    ): EntityChanges = EntityChanges(
        npcs = npcDiff(oldNpcs, newNpcs),
        quests = questDiff(oldQuests, newQuests),
        factions = factionDiff(oldFactions, newFactions),
        locations = locationDiff(oldLocations, newLocations)
    )

    fun npcDiff(old: List<LogNpc>, new: List<LogNpc>): List<NpcChange> {
        val oldById = old.associateBy { it.id }
        val changes = mutableListOf<NpcChange>()
        for (n in new) {
            val prev = oldById[n.id]
            when {
                prev == null -> changes += NpcChange.Insert(n)
                prev.status != "dead" && n.status == "dead" ->
                    changes += NpcChange.MarkDead(n.id, n.lastSeenTurn)
                prev != n -> {
                    val patch = NpcPatch(
                        lastLocation = n.lastLocation.takeIf { it != prev.lastLocation },
                        lastSeenTurn = n.lastSeenTurn.takeIf { it != prev.lastSeenTurn },
                        relationship = n.relationship.takeIf { it != prev.relationship },
                        thoughts = n.thoughts.takeIf { it != prev.thoughts },
                        appendDialogue = n.dialogueHistory - prev.dialogueHistory.toSet(),
                        appendMemorableQuote = (n.memorableQuotes - prev.memorableQuotes.toSet()).lastOrNull(),
                        relationshipNote = n.relationshipNote.takeIf { it != prev.relationshipNote },
                        status = n.status.takeIf { it != prev.status },
                        faction = n.faction?.takeIf { it != prev.faction }
                    )
                    // Fall back to a full insert (upsert) when the patch can't capture the delta.
                    if (patch.isNoop()) changes += NpcChange.Insert(n)
                    else changes += NpcChange.Update(n.id, patch)
                }
            }
        }
        return changes
    }

    private fun NpcPatch.isNoop(): Boolean =
        lastLocation == null && lastSeenTurn == null && relationship == null &&
            thoughts == null && appendDialogue.isEmpty() && appendMemorableQuote == null &&
            relationshipNote == null && status == null && faction == null

    fun questDiff(old: List<Quest>, new: List<Quest>): List<QuestChange> {
        val oldById = old.associateBy { it.id }
        val changes = mutableListOf<QuestChange>()
        for (q in new) {
            val prev = oldById[q.id]
            if (prev == null) changes += QuestChange.Insert(q)
            else if (prev != q) {
                changes += QuestChange.Update(
                    id = q.id,
                    patch = QuestPatch(
                        status = q.status.takeIf { it != prev.status },
                        turnCompleted = q.turnCompleted.takeIf { it != prev.turnCompleted }
                    )
                )
                // Objectives replaced wholesale via upsert (Quest already replaces the row).
                if (q.objectives != prev.objectives || q.completed != prev.completed) {
                    changes += QuestChange.Insert(q)
                }
            }
        }
        return changes
    }

    fun factionDiff(old: List<Faction>, new: List<Faction>): List<FactionChange> {
        val oldById = old.associateBy { it.id }
        val changes = mutableListOf<FactionChange>()
        for (f in new) {
            val prev = oldById[f.id]
            if (prev == null) changes += FactionChange.Insert(f)
            else if (prev != f) {
                val patch = FactionPatch(
                    ruler = f.ruler.takeIf { it != prev.ruler },
                    disposition = f.disposition.takeIf { it != prev.disposition },
                    mood = f.mood.takeIf { it != prev.mood },
                    status = f.status.takeIf { it != prev.status },
                    goal = f.goal.takeIf { it != prev.goal }
                )
                val structural =
                    f.name != prev.name || f.description != prev.description ||
                        f.baseLoc != prev.baseLoc || f.government != prev.government ||
                        f.economy != prev.economy
                if (structural) changes += FactionChange.Insert(f)
                else changes += FactionChange.Update(f.id, patch)
            }
        }
        return changes
    }

    fun locationDiff(old: List<MapLocation>, new: List<MapLocation>): List<LocationChange> {
        val oldById = old.associateBy { it.id }
        val changes = mutableListOf<LocationChange>()
        for (l in new) {
            val prev = oldById[l.id]
            if (prev == null) changes += LocationChange.Insert(l)
            else if (prev.discovered != l.discovered) changes += LocationChange.SetDiscovered(l.id, l.discovered)
            else if (prev != l) changes += LocationChange.Insert(l)
        }
        return changes
    }
}
