package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.ParsedReply
import com.realmsoffate.game.data.PartyCompanion
import com.realmsoffate.game.data.Quest
import com.realmsoffate.game.data.TimelineEntry

/** Result of QuestReducer.apply. */
data class QuestApplyResult(
    val quests: List<Quest>,
    val timelineEntries: List<TimelineEntry>
)

/** Result of PartyReducer.apply. */
data class PartyApplyResult(
    val party: List<PartyCompanion>,
    val timelineEntries: List<TimelineEntry>
)

/**
 * Pure reducer over quest state. Extracted from GameViewModel.applyParsed
 * (lines 1440–1465). Handles starts, objective updates, completes, fails.
 *
 * Note: Quest objects have mutable internal lists (objectives, completed).
 * The reducer does not deep-copy Quest objects — it mutates their inner lists
 * in place for questUpdates, matching the pre-extraction behavior exactly.
 * The wrapping List<Quest> is never mutated; a new list is returned.
 */
object QuestReducer {
    fun apply(
        quests: List<Quest>,
        parsed: ParsedReply,
        currentLocName: String,
        currentTurn: Int
    ): QuestApplyResult {
        val timelineEntries = mutableListOf<TimelineEntry>()
        val result = quests.toMutableList()

        parsed.questStarts.forEach { q ->
            val withLoc = q.copy(location = currentLocName)
            result.add(withLoc)
            timelineEntries.add(TimelineEntry(currentTurn, "quest", "Quest started: ${q.title}"))
        }
        parsed.questUpdates.forEach { (title, obj) ->
            val idx = result.indexOfFirst { it.title.equals(title, true) && it.status == "active" }
            if (idx >= 0) {
                val q = result[idx]
                val oi = q.objectives.indexOfFirst { it.equals(obj, true) }
                if (oi >= 0) q.completed[oi] = true else {
                    q.objectives.add(obj)
                    q.completed.add(true)
                }
            }
        }
        parsed.questComplete.forEach { t ->
            val idx = result.indexOfFirst { it.title.equals(t, true) }
            if (idx >= 0) result[idx] = result[idx].copy(status = "completed", turnCompleted = currentTurn)
        }
        parsed.questFails.forEach { t ->
            val idx = result.indexOfFirst { it.title.equals(t, true) }
            if (idx >= 0) result[idx] = result[idx].copy(status = "failed", turnCompleted = currentTurn)
        }

        return QuestApplyResult(quests = result, timelineEntries = timelineEntries)
    }
}

/**
 * Pure reducer over party membership. Extracted from GameViewModel.applyParsed
 * (lines 1467–1480). Handles joins and leaves.
 *
 * partyJoins: appends verbatim (no dedup). Emits a "birth" timeline entry.
 * partyLeaves: removes first case-insensitive match. Emits an "event" timeline
 * entry using the removed entry's exact name. Silently no-ops if no match.
 */
object PartyReducer {
    fun apply(
        party: List<PartyCompanion>,
        parsed: ParsedReply,
        currentTurn: Int
    ): PartyApplyResult {
        val timelineEntries = mutableListOf<TimelineEntry>()
        val result = party.toMutableList().apply {
            parsed.partyJoins.forEach {
                add(it)
                timelineEntries.add(TimelineEntry(currentTurn, "birth", "${it.name} the ${it.race} ${it.role} joined the party."))
            }
            parsed.partyLeaves.forEach { name ->
                val idx = indexOfFirst { it.name.equals(name, ignoreCase = true) }
                if (idx >= 0) {
                    val gone = removeAt(idx)
                    timelineEntries.add(TimelineEntry(currentTurn, "event", "${gone.name} left the party."))
                }
            }
        }

        return PartyApplyResult(party = result, timelineEntries = timelineEntries)
    }
}
