package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.IdGen
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.ParsedReply
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.data.sanitizeDisplayName
import com.realmsoffate.game.game.CombatState
import com.realmsoffate.game.game.DisplayMessage

/**
 * Result of running [NpcLogReducer.apply]. Carries the updated NPC log plus the
 * downstream signals the VM must still react to (system messages for deaths,
 * timeline entries, combat-order filtering).
 */
data class NpcLogApplyResult(
    /** Updated NPC log after merge/auto-register/deaths/updates. */
    val npcLog: List<LogNpc>,
    /** Combat state with dead NPCs removed from the turn order — null iff the
     *  input combat was null, or unchanged if no deaths occurred. */
    val combat: CombatState?,
    /** System messages generated this turn — e.g. "☠️ Vesper has died." */
    val systemMessages: List<DisplayMessage.System>,
    /** Timeline entries emitted this turn. */
    val timelineEntries: List<TimelineEntry>,
    /** Display names of NPCs that died this turn, resolved from refs. Used by
     *  callers (faction-leader cascade in Phase II.3) that need the same
     *  resolution the reducer did, without re-resolving. */
    val deadDisplayNames: List<String>
)

/**
 * Pure reducer over the NPC log + related effects (combat order filtering,
 * system messages, timeline entries). Extracted from GameViewModel.applyParsed.
 *
 * The reducer takes the input npcLog and combat state and returns fresh copies
 * — it never mutates the inputs. Behavioral contract is byte-for-byte
 * equivalent to the pre-extraction applyParsed lines 1417–1618.
 */
object NpcLogReducer {
    fun apply(
        npcLog: List<LogNpc>,
        combat: CombatState?,
        parsed: ParsedReply,
        currentTurn: Int,
        currentLocName: String
    ): NpcLogApplyResult {
        // 1) Clone npcLog into a mutable working copy. Sanitize any legacy entries
        //    that have slug-form display names lingering from pre-fix saves.
        val workingLog = npcLog.map { it.sanitizeDisplayName() }.toMutableList()

        // 2) Build the current ID set once for collision checks during this merge pass.
        val existingNpcIds = workingLog.map { it.id }.filter { it.isNotBlank() }.toMutableSet()

        // 3) parsed.npcsMet merge — ID-first branch + legacy-name branch.
        //    Dedup uses IdGen.nameKey so "Mira Cole" / "Mira-Cole" / "mira-cole"
        //    all collapse to the same entry. If the AI emits a slug in the name
        //    field, we reverse-slug it so the journal never displays hyphens.
        parsed.npcsMet.forEach { n ->
            // Canonical display: if incoming name equals the slug form (AI put the
            // ID in the name field), reverse-slug it for UI presentation.
            val cleanName = when {
                n.name.isBlank() -> n.name
                IdGen.isSlug(n.name) && (n.id.isBlank() || n.name.equals(n.id, ignoreCase = true)) ->
                    IdGen.slugToDisplay(n.name)
                else -> n.name
            }
            if (n.id.isNotBlank()) {
                // ID-first: prefer stable-ID match, fall back to name-key equivalence
                // so a hallucinated ID for the same NPC still dedupes.
                val byId = workingLog.indexOfFirst { it.id == n.id }
                val existing = if (byId >= 0) byId else workingLog.indexOfFirst {
                    cleanName.isNotBlank() && IdGen.nameKey(it.name) == IdGen.nameKey(cleanName)
                }
                if (existing >= 0) {
                    val old = workingLog[existing]
                    // Don't clobber a pretty display name with the slug form.
                    val keepOldName = cleanName.isBlank() ||
                        (IdGen.isSlug(cleanName) && !IdGen.isSlug(old.name))
                    workingLog[existing] = old.copy(
                        name = if (keepOldName) old.name else cleanName,
                        race = n.race.ifBlank { old.race },
                        role = n.role.ifBlank { old.role },
                        age = n.age.ifBlank { old.age },
                        relationship = n.relationship.ifBlank { old.relationship },
                        appearance = n.appearance.ifBlank { old.appearance },
                        personality = n.personality.ifBlank { old.personality },
                        thoughts = n.thoughts.ifBlank { old.thoughts },
                        lastSeenTurn = currentTurn,
                        lastLocation = currentLocName.ifBlank { old.lastLocation }
                    )
                } else {
                    val safeId = if (n.id !in existingNpcIds) {
                        n.id
                    } else {
                        IdGen.forName(cleanName, existingNpcIds)
                    }
                    existingNpcIds.add(safeId)
                    workingLog.add(n.copy(id = safeId, name = cleanName, lastLocation = currentLocName))
                }
            } else {
                // Legacy format — match by name-key so separator variants dedupe.
                val existing = workingLog.indexOfFirst {
                    IdGen.nameKey(it.name) == IdGen.nameKey(cleanName)
                }
                if (existing >= 0) {
                    val old = workingLog[existing]
                    workingLog[existing] = old.copy(
                        relationship = n.relationship.ifBlank { old.relationship },
                        lastSeenTurn = currentTurn,
                        lastLocation = currentLocName.ifBlank { old.lastLocation }
                    )
                } else {
                    val newId = IdGen.forName(cleanName, existingNpcIds)
                    existingNpcIds.add(newId)
                    workingLog.add(n.copy(id = newId, name = cleanName, lastLocation = currentLocName))
                }
            }
        }

        // 4) Auto-register any NPC who appears in a dialog or action tag but was not
        // formally introduced via [NPC_MET]. The narrator often skips the tag for
        // minor or recurring NPCs, and the user expects every named speaker/actor
        // to show up in the log. Later [NPC_MET] updates will enrich these stubs.
        val autoRefs = buildList {
            parsed.npcDialogs.forEach { (ref, _) -> if (ref.isNotBlank()) add(ref) }
            parsed.npcActions.forEach { (ref, _) -> if (ref.isNotBlank()) add(ref) }
        }.distinctBy { IdGen.nameKey(it) }
        autoRefs.forEach { ref ->
            val idx = resolveNpcIdx(ref, workingLog)
            if (idx >= 0) {
                // Already logged — refresh lastSeen and location.
                val old = workingLog[idx]
                workingLog[idx] = old.copy(
                    lastSeenTurn = currentTurn,
                    lastLocation = currentLocName.ifBlank { old.lastLocation }
                )
            } else {
                // Stub entry. If ref is slug-form, use it as the ID and reverse-slug
                // for the display name so the journal never shows "mira-cole".
                val isSlug = IdGen.isSlug(ref)
                val stubId = if (isSlug) {
                    if (ref !in existingNpcIds) ref else IdGen.forName(ref, existingNpcIds)
                } else {
                    IdGen.forName(ref, existingNpcIds)
                }
                val displayName = if (isSlug) IdGen.slugToDisplay(ref) else ref
                existingNpcIds.add(stubId)
                workingLog.add(LogNpc(
                    id = stubId,
                    name = displayName,
                    lastLocation = currentLocName,
                    metTurn = currentTurn,
                    lastSeenTurn = currentTurn
                ))
            }
        }

        // 5) Attach dialogue captured via the structured [NPC_DIALOG:ref] tag.
        // ref may be a stable ID or a legacy display name — resolved via resolveNpcIdx.
        parsed.npcDialogs.forEach { (ref, quote) ->
            val idx = resolveNpcIdx(ref, workingLog)
            if (idx >= 0 && quote.isNotBlank()) {
                val old = workingLog[idx]
                val entry = "T$currentTurn: \"$quote\""
                if (entry !in old.dialogueHistory) {
                    val merged = (old.dialogueHistory + entry).takeLast(20).toMutableList()
                    workingLog[idx] = old.copy(dialogueHistory = merged, lastSeenTurn = currentTurn)
                }
            }
        }

        // 6) Narrator-curated memorable quotes via [NPC_QUOTE:ref|quote] — store in
        // a separate list (capped at 12) so they persist beyond the rolling
        // dialogueHistory buffer. Deduplicated by exact entry text.
        parsed.npcQuotes.forEach { (ref, quote) ->
            val idx = resolveNpcIdx(ref, workingLog)
            if (idx >= 0 && quote.isNotBlank()) {
                val old = workingLog[idx]
                val entry = "T$currentTurn: \"$quote\""
                if (entry !in old.memorableQuotes) {
                    val merged = (old.memorableQuotes + entry).takeLast(12).toMutableList()
                    workingLog[idx] = old.copy(memorableQuotes = merged, lastSeenTurn = currentTurn)
                }
            }
        }

        // 7) NPC deaths — ref may be a stable ID or a legacy display name.
        val systemMessages = mutableListOf<DisplayMessage.System>()
        val timelineEntries = mutableListOf<TimelineEntry>()
        parsed.npcDeaths.forEach { deadRef ->
            val idx = resolveNpcIdx(deadRef, workingLog)
            val displayName = if (idx >= 0) workingLog[idx].name else deadRef
            if (idx >= 0) {
                workingLog[idx] = workingLog[idx].copy(
                    status = "dead",
                    relationship = "dead",
                    relationshipNote = "Killed turn $currentTurn"
                )
            }
            systemMessages.add(DisplayMessage.System("☠️ $displayName has died."))
            timelineEntries.add(TimelineEntry(currentTurn, "event", "$displayName died"))
        }

        // 8) Remove dead NPCs from combat order — resolve refs to display names first.
        val deadDisplayNames = parsed.npcDeaths.map { ref ->
            val i = resolveNpcIdx(ref, workingLog)
            if (i >= 0) workingLog[i].name.lowercase() else ref.lowercase()
        }.toSet()
        var updatedCombat = combat
        if (updatedCombat != null && deadDisplayNames.isNotEmpty()) {
            updatedCombat = updatedCombat.copy(
                order = updatedCombat.order.filter { c ->
                    c.name.lowercase() !in deadDisplayNames
                }
            )
        }

        // 9) NPC updates — ref may be a stable ID or a legacy display name.
        // With stable IDs, the "name" field simply updates the display label in place;
        // no complex merge is needed because the id is the stable key.
        parsed.npcUpdates.forEach { (ref, field, value) ->
            val idx = resolveNpcIdx(ref, workingLog)
            if (idx >= 0) {
                val old = workingLog[idx]
                workingLog[idx] = when (field.lowercase()) {
                    "relationship" -> old.copy(relationship = value)
                    "role" -> old.copy(role = value)
                    "faction" -> old.copy(faction = value)
                    "location" -> old.copy(lastLocation = value)
                    "status" -> old.copy(status = value)
                    "name" -> {
                        // Update the display name directly. The stable id remains unchanged.
                        val newName = value.trim()
                        if (newName.isBlank() || newName.equals(old.name, true)) {
                            old
                        } else if (old.id.isNotBlank()) {
                            // ID-keyed entry — just update the display name in place.
                            old.copy(name = newName)
                        } else {
                            // Legacy (no stable ID) — check for a duplicate stub under
                            // the new name and merge if found.
                            val existingNewIdx = workingLog.indexOfFirst {
                                it.name.equals(newName, true) && !it.name.equals(old.name, true)
                            }
                            if (existingNewIdx >= 0) {
                                val other = workingLog[existingNewIdx]
                                val merged = mergeNpcEntries(primary = other, secondary = old).copy(name = newName)
                                workingLog.removeAt(existingNewIdx)
                                val targetIdx = if (existingNewIdx < idx) idx - 1 else idx
                                workingLog[targetIdx] = merged
                                return@forEach
                            }
                            old.copy(name = newName)
                        }
                    }
                    else -> old
                }
            }
        }

        return NpcLogApplyResult(
            npcLog = workingLog,
            combat = updatedCombat,
            systemMessages = systemMessages,
            timelineEntries = timelineEntries,
            deadDisplayNames = parsed.npcDeaths.map { ref ->
                val i = resolveNpcIdx(ref, workingLog)
                if (i >= 0) workingLog[i].name else ref
            }
        )
    }

    /**
     * Resolves a ref string (stable ID OR legacy display name) to an index in
     * [npcLog]. Prefers ID match, falls back to case-insensitive name match.
     * Returns -1 if no match. Exposed `internal` so GameViewModel can still
     * resolve refs for code paths not yet extracted (Phase II.3 faction-leader
     * cascade reaches into the already-reduced npcLog).
     */
    internal fun resolveNpcIdx(ref: String, npcLog: List<LogNpc>): Int {
        if (ref.isBlank()) return -1
        val byId = npcLog.indexOfFirst { it.id == ref }
        if (byId >= 0) return byId
        val refKey = IdGen.nameKey(ref)
        return npcLog.indexOfFirst { IdGen.nameKey(it.name) == refKey }
    }

    /** Private merge helper — used only by the rename-fallback in npcUpdates. */
    private fun mergeNpcEntries(primary: LogNpc, secondary: LogNpc): LogNpc {
        fun pickStr(a: String, b: String): String = if (a.isNotBlank()) a else b
        fun pickStrN(a: String?, b: String?): String? = a?.takeIf { it.isNotBlank() } ?: b
        val dialog = (secondary.dialogueHistory + primary.dialogueHistory)
            .distinct()
            .takeLast(20)
            .toMutableList()
        val memorable = (secondary.memorableQuotes + primary.memorableQuotes)
            .distinct()
            .takeLast(12)
            .toMutableList()
        return primary.copy(
            race = pickStr(primary.race, secondary.race),
            role = pickStr(primary.role, secondary.role),
            age = pickStr(primary.age, secondary.age),
            relationship = pickStr(primary.relationship, secondary.relationship),
            appearance = pickStr(primary.appearance, secondary.appearance),
            personality = pickStr(primary.personality, secondary.personality),
            thoughts = pickStr(primary.thoughts, secondary.thoughts),
            faction = pickStrN(primary.faction, secondary.faction),
            lastLocation = pickStr(primary.lastLocation, secondary.lastLocation),
            metTurn = minOf(primary.metTurn, secondary.metTurn),
            lastSeenTurn = maxOf(primary.lastSeenTurn, secondary.lastSeenTurn),
            dialogueHistory = dialog,
            memorableQuotes = memorable,
            relationshipNote = pickStr(primary.relationshipNote, secondary.relationshipNote),
            status = if (primary.status == "alive" && secondary.status != "alive") secondary.status else primary.status
        )
    }
}
