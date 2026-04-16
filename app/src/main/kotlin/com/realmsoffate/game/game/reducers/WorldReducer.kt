package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.HistoryEntry
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.ParsedReply
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.game.DisplayMessage

/**
 * Result of applying world-lore-related tags (faction updates, dead-leader
 * cascade, lore history entries). The VM consumes [worldLore] for state.copy,
 * appends [systemMessages] into newMsgs, and drains [timelineEntries] into
 * the VM's timeline list.
 */
data class WorldApplyResult(
    val worldLore: WorldLore?,
    val systemMessages: List<DisplayMessage.System>,
    val timelineEntries: List<TimelineEntry>
)

/**
 * Pure reducer over the world-lore domain. Extracted from
 * GameViewModel.applyParsed (lines 1429–1490).
 *
 * Cross-domain note: the faction-leader death cascade (step 2) reads NPC
 * display names to find matching faction rulers. That's why this reducer
 * takes [npcLog] as an explicit input — the coupling between NPC deaths and
 * faction state is real and made explicit by the parameter list. Callers
 * must pass the already-reduced npcLog (post-NpcLogReducer).
 */
object WorldReducer {
    fun apply(
        worldLore: WorldLore?,
        npcLog: List<LogNpc>,
        parsed: ParsedReply,
        currentTurn: Int
    ): WorldApplyResult {
        val systemMessages = mutableListOf<DisplayMessage.System>()
        val timelineEntries = mutableListOf<TimelineEntry>()
        var worldLoreUpdated = worldLore

        // 1) Faction updates — ref may be a stable faction id (slug) or display name.
        parsed.factionUpdates.forEach { (factionRef, field, value) ->
            worldLoreUpdated = worldLoreUpdated?.let { lore ->
                val newFactions = lore.factions.map { f ->
                    // Try ID match first, then fall back to name match.
                    val matches = (f.id.isNotBlank() && f.id == factionRef) ||
                        f.name.equals(factionRef, true)
                    if (matches) {
                        when (field.lowercase()) {
                            "status" -> f.copy(status = value)
                            "ruler" -> f.copy(ruler = value, government = f.government?.copy(ruler = value))
                            "disposition" -> f.copy(disposition = value)
                            "mood" -> f.copy(mood = value)
                            "description" -> f.copy(description = value)
                            "type" -> f.copy(type = value)
                            "name" -> f.copy(name = value)
                            else -> f
                        }
                    } else f
                }
                lore.copy(factions = newFactions)
            }
            // Use resolved display name for system message (prefer matched faction name).
            // Resolved AFTER the update so a rename shows the NEW name.
            val factionDisplayName = worldLoreUpdated?.factions
                ?.firstOrNull { (it.id.isNotBlank() && it.id == factionRef) || it.name.equals(factionRef, true) }
                ?.name ?: factionRef
            systemMessages.add(DisplayMessage.System("📜 $factionDisplayName: $field → $value"))
            timelineEntries.add(TimelineEntry(currentTurn, "event", "Faction $factionDisplayName: $field changed to $value"))
        }

        // 2) Mark faction leaders as deceased when they die — use resolved display names.
        // No system messages or timeline entries emitted here — cascade only.
        parsed.npcDeaths.forEach { deadRef ->
            val deadNpcIdx = NpcLogReducer.resolveNpcIdx(deadRef, npcLog)
            val deadName = if (deadNpcIdx >= 0) npcLog[deadNpcIdx].name else deadRef
            val factions = worldLoreUpdated?.factions.orEmpty()
            factions.forEachIndexed { fIdx, faction ->
                if (faction.ruler.equals(deadName, ignoreCase = true) ||
                    faction.government?.ruler?.equals(deadName, ignoreCase = true) == true
                ) {
                    val updatedFactions = worldLoreUpdated!!.factions.toMutableList()
                    updatedFactions[fIdx] = faction.copy(
                        ruler = "$deadName (Deceased)",
                        government = faction.government?.copy(ruler = "$deadName (Deceased)")
                    )
                    worldLoreUpdated = worldLoreUpdated!!.copy(factions = updatedFactions)
                }
            }
        }

        // 3) New lore entries
        parsed.loreEntries.forEach { entry ->
            worldLoreUpdated = worldLoreUpdated?.let { lore ->
                val newHistory = lore.history + HistoryEntry(
                    era = "recent",
                    year = currentTurn,
                    text = entry
                )
                lore.copy(history = newHistory)
            }
            timelineEntries.add(TimelineEntry(currentTurn, "event", "Lore: $entry"))
        }

        return WorldApplyResult(
            worldLore = worldLoreUpdated,
            systemMessages = systemMessages,
            timelineEntries = timelineEntries
        )
    }
}
