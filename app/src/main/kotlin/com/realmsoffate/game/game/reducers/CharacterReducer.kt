package com.realmsoffate.game.game.reducers

import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.ParsedReply
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.game.Classes
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.game.SpellSlots

/**
 * Signals that a level-up occurred this turn. The VM reads this to surface
 * the level-up overlay, trigger feat/stat-point selection, and append a
 * timeline entry. Null when no level-up happened.
 */
data class LevelUpSignal(
    val newLevel: Int,
    /** True at levels 4, 8, 12, 16, 20 — player picks a feat instead of stat points. */
    val featPending: Boolean,
    /** Points awarded to the pending-stat-points pool when featPending is false. */
    val statPointsGained: Int
)

/**
 * Result of applying per-turn mechanical character changes from a ParsedReply.
 *
 * The reducer is pure — no state flows, no shared mutable side effects.
 * The caller (GameViewModel.applyParsed) dispatches `levelUp` and
 * `timelineEntries` into the VM's observable state after the reducer returns.
 */
data class CharacterApplyResult(
    /** The character after mutations — a fresh copy, not the input. */
    val character: Character,
    /** HP before the damage/heal math ran. Used for stat-change pill display. */
    val hpBefore: Int,
    /** Gold before the gain/loss math ran. Used for stat-change pill display. */
    val goldBefore: Int,
    /** Non-null when the character leveled up this turn. */
    val levelUp: LevelUpSignal? = null,
    /** Timeline entries emitted by the reducer (e.g., a "levelup" entry). */
    val timelineEntries: List<TimelineEntry> = emptyList()
)

object CharacterReducer {
    /**
     * Applies the mechanical fields of [parsed] to [ch], returning the new
     * character state plus the signals the VM needs to react to.
     *
     * Behavioral contract (must match the pre-extraction applyParsed):
     * - HP: clamped to [0, maxHp] after adding heal and subtracting damage.
     * - XP: added straight; level-up triggers when XP >= levelThreshold(level+1)
     *   AND level < 20.
     * - Level-up: +1 level, maxHp += (class hit die OR 8) + conMod, HP restored
     *   to maxHp, spell slots refreshed to the new level's allotment.
     * - Feat trigger: every 4th level (4/8/12/16/20) → featPending = true,
     *   statPointsGained = 0. Other level-ups → featPending = false,
     *   statPointsGained = 2.
     * - Timeline entry: emits ("levelup", "Reached level N.") with the CURRENT
     *   turn number. Because the reducer doesn't know the turn, it takes
     *   `currentTurn` as an argument.
     * - Gold: clamped to >= 0.
     * - Items removed: qty > 1 decrements qty; qty == 1 removes the entry.
     *   Case-insensitive name match.
     * - Conditions added: de-duplicated case-insensitively.
     * - Conditions removed: case-insensitive removal of all matches.
     *
     * NOTE: This reducer does NOT handle parsed.partyJoins — that's a
     * state-level concern (party membership changes GameUiState.party, not
     * the character). Phase II.4 will extract PartyReducer separately.
     */
    fun apply(
        ch: Character,
        parsed: ParsedReply,
        currentTurn: Int,
        levelThreshold: (Int) -> Int = { GameViewModel.levelThreshold(it) }
    ): CharacterApplyResult {
        // Capture stats before mutations for stat-change pill display
        val hpBefore = ch.hp
        val goldBefore = ch.gold

        // Mutate a working copy of the character
        val char = ch.copy(
            abilities = ch.abilities.copy(),
            inventory = ch.inventory.toMutableList(),
            knownSpells = ch.knownSpells.toMutableList(),
            spellSlots = ch.spellSlots.toMutableMap(),
            maxSpellSlots = ch.maxSpellSlots.toMutableMap()
        )
        char.hp = (char.hp - parsed.damage + parsed.heal).coerceAtMost(char.maxHp).coerceAtLeast(0)
        char.xp += parsed.xp
        char.gold = (char.gold + parsed.goldGained - parsed.goldLost).coerceAtLeast(0)
        if (parsed.itemsGained.isNotEmpty()) char.inventory.addAll(parsed.itemsGained)
        // Consumed / dropped items — match by name (case-insensitive).
        parsed.itemsRemoved.forEach { name ->
            val idx = char.inventory.indexOfFirst { it.name.equals(name, true) }
            if (idx >= 0) {
                val e = char.inventory[idx]
                if (e.qty > 1) char.inventory[idx] = e.copy(qty = e.qty - 1)
                else char.inventory.removeAt(idx)
            }
        }
        // Conditions
        parsed.conditionsAdded.forEach { c ->
            if (char.conditions.none { it.equals(c, true) }) char.conditions += c
        }
        parsed.conditionsRemoved.forEach { c ->
            char.conditions.removeAll { it.equals(c, true) }
        }

        // Level up if xp threshold reached (D&D 5e-ish milestones)
        val nextXp = levelThreshold(char.level + 1)
        var levelUpSignal: LevelUpSignal? = null
        val timelineEntries = mutableListOf<TimelineEntry>()
        if (char.xp >= nextXp && char.level < 20) {
            char.level += 1
            val clsDef = Classes.find(char.cls)
            val hpGain = (clsDef?.hitDie ?: 8) + char.abilities.conMod
            char.maxHp += hpGain
            char.hp = (char.hp + hpGain).coerceAtMost(char.maxHp)
            // Refresh slot table to the new level's allotment
            SpellSlots.slotsForLevel(char.cls, char.level).forEachIndexed { idx, n ->
                if (idx == 0 || n <= 0) return@forEachIndexed
                char.maxSpellSlots[idx] = n
                char.spellSlots[idx] = n
            }
            // Feat levels: 4, 8, 12, 16, 20 — offer feat choice instead of stat points
            val featPending = char.level % 4 == 0
            levelUpSignal = LevelUpSignal(
                newLevel = char.level,
                featPending = featPending,
                statPointsGained = if (featPending) 0 else 2
            )
            timelineEntries += TimelineEntry(currentTurn, "levelup", "Reached level ${char.level}.")
        }

        return CharacterApplyResult(
            character = char,
            hpBefore = hpBefore,
            goldBefore = goldBefore,
            levelUp = levelUpSignal,
            timelineEntries = timelineEntries
        )
    }
}
