package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.PartyCompanion
import com.realmsoffate.game.game.Combatant
import com.realmsoffate.game.game.CombatState
import com.realmsoffate.game.game.CombatSystem
import com.realmsoffate.game.game.Dice
import com.realmsoffate.game.game.DisplayMessage

/**
 * Result of CombatReducer.transition. Encodes the post-transition combat
 * state, NPC log mutations from enemy deaths, system messages, and a flag
 * for whether to surface the initiative overlay.
 */
data class CombatTransitionResult(
    /** New combat state — null when scene != "battle" and combat ended this turn. */
    val combat: CombatState?,
    /** Updated NPC log if any enemies were marked dead by HP this turn. */
    val npcLog: List<LogNpc>,
    /** System messages to append (e.g., "⚔️ Combat has ended."). */
    val systemMessages: List<DisplayMessage.System>,
    /**
     * True iff a fresh combat encounter just started this turn (no prior
     * combat existed and scene is "battle"). The VM dispatches this signal
     * to set _showInitiative.value = true.
     */
    val showInitiative: Boolean
)

/**
 * Pure reducer for combat scene transitions and per-turn enemy roster
 * updates. Extracted from GameViewModel.submitAction (lines 1109–1164).
 *
 * Scene == "battle":
 *   - If no prior combat, start a fresh encounter via CombatSystem.startCombat
 *     and signal showInitiative = true.
 *   - Otherwise, advance the round via combat.next() and sync HP via
 *     CombatSystem.syncHp.
 *   - Merge parsedEnemies into combat.order: existing entries get their HP
 *     updated; new entries are added with a freshly-rolled d20 initiative.
 *   - Enemies at HP ≤ 0 are marked status=dead in the NPC log
 *     (relationshipNote = "Killed turn N", where N is currentTurn —
 *     post-applyParsed, already the post-increment value).
 *   - Dead enemies are removed from combat.order.
 *
 * Scene != "battle":
 *   - If there was a prior combat, clear it and emit a "⚔️ Combat has
 *     ended." System message.
 *   - Otherwise no-op.
 *
 * Character == null: no-op — returns inputs unchanged (combat, npcLog,
 * emptyList(), false).
 */
object CombatReducer {
    fun transition(
        scene: String,
        combat: CombatState?,
        character: Character?,
        party: List<PartyCompanion>,
        npcLog: List<LogNpc>,
        parsedEnemies: List<Triple<String, Int, Int>>, // (name, hp, maxHp) from parsed.enemies
        currentTurn: Int
    ): CombatTransitionResult {
        // Character == null guard: mirrors the original `val ch2 = cur.character ?: return@let cur`
        if (character == null) {
            return CombatTransitionResult(
                combat = combat,
                npcLog = npcLog,
                systemMessages = emptyList(),
                showInitiative = false
            )
        }

        if (scene.equals("battle", ignoreCase = true)) {
            // Step 1: Decide combat — start fresh or advance the existing round.
            val existing = combat
            var showInitiative = false
            var newCombat = if (existing == null) {
                showInitiative = true
                CombatSystem.startCombat(character, party)
            } else {
                CombatSystem.syncHp(existing.next(), character, party)
            }

            // Step 2: Merge parsedEnemies into the initiative order (only when non-empty).
            var updatedNpcLog = npcLog
            if (parsedEnemies.isNotEmpty()) {
                val currentOrder = newCombat.order.toMutableList()
                parsedEnemies.forEach { (name, hp, maxHp) ->
                    val idx = currentOrder.indexOfFirst { it.name.equals(name, true) && !it.isPlayer }
                    if (idx >= 0) {
                        // Update existing enemy HP
                        currentOrder[idx] = currentOrder[idx].copy(hp = hp, maxHp = maxHp)
                    } else {
                        // New enemy — add to the order
                        currentOrder.add(Combatant(name = name, hp = hp, maxHp = maxHp, initiative = Dice.d(20), isPlayer = false))
                    }
                }

                // Step 3: Find enemies at 0 HP — mark them dead in npcLog before removal.
                val killedByHp = currentOrder.filter { !it.isPlayer && it.hp <= 0 }.map { it.name }
                if (killedByHp.isNotEmpty()) {
                    val mutableLog = updatedNpcLog.toMutableList()
                    killedByHp.forEach { deadName ->
                        val npcIdx = mutableLog.indexOfFirst { it.name.equals(deadName, true) }
                        if (npcIdx >= 0) {
                            mutableLog[npcIdx] = mutableLog[npcIdx].copy(
                                status = "dead",
                                relationship = "dead",
                                relationshipNote = "Killed turn $currentTurn"
                            )
                        }
                    }
                    updatedNpcLog = mutableLog
                }

                // Step 4: Remove dead enemies from combat.order.
                currentOrder.removeAll { !it.isPlayer && it.hp <= 0 }
                newCombat = newCombat.copy(order = currentOrder)
            }

            // Step 5: Return updated combat + npcLog.
            return CombatTransitionResult(
                combat = newCombat,
                npcLog = updatedNpcLog,
                systemMessages = emptyList(),
                showInitiative = showInitiative
            )
        } else if (combat != null) {
            // Scene changed away from battle and there was an active combat — clear it.
            return CombatTransitionResult(
                combat = null,
                npcLog = npcLog,
                systemMessages = listOf(DisplayMessage.System("⚔️ Combat has ended.")),
                showInitiative = false
            )
        } else {
            // Not in battle, no prior combat — no-op.
            return CombatTransitionResult(
                combat = null,
                npcLog = npcLog,
                systemMessages = emptyList(),
                showInitiative = false
            )
        }
    }
}
