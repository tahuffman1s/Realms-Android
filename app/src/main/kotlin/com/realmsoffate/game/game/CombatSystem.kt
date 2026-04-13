package com.realmsoffate.game.game

import kotlinx.serialization.Serializable

/**
 * Lightweight combat round tracker. Only tracks the player-side initiative
 * order (player + companions); enemies stay in the narrator's prose since
 * the AI handles enemy HP / AC / moves. The combat HUD uses this to show a
 * round counter and the allied initiative queue.
 */
@Serializable
data class Combatant(
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val initiative: Int,
    val isPlayer: Boolean = false
)

@Serializable
data class CombatState(
    val round: Int = 1,
    val order: List<Combatant> = emptyList(),
    /** Index into `order` — whose turn is it, visually. */
    val activeIndex: Int = 0
) {
    val active: Combatant? get() = order.getOrNull(activeIndex)
    fun next(): CombatState {
        if (order.isEmpty()) return this
        val nextIdx = activeIndex + 1
        return if (nextIdx >= order.size) copy(round = round + 1, activeIndex = 0)
        else copy(activeIndex = nextIdx)
    }
}

/**
 * Death saving throws — classic D&D 5e rules at 0 HP:
 *   d20 ≥ 10 = success; < 10 = failure; nat 20 = regain 1 HP immediately;
 *   nat 1 = counts as two failures. 3 successes = stable; 3 failures = die.
 */
@Serializable
data class DeathSaveState(
    val successes: Int = 0,
    val failures: Int = 0,
    val rolls: List<Int> = emptyList()
) {
    val stable: Boolean get() = successes >= 3
    val dead: Boolean get() = failures >= 3
}

object DeathSaves {
    /** Applies one d20 roll to the state. Returns updated state + event label. */
    fun roll(state: DeathSaveState, d20: Int): Pair<DeathSaveState, String> {
        val rolls = state.rolls + d20
        return when {
            d20 == 20 -> state.copy(rolls = rolls, successes = 3, failures = 0) to
                "Nat 20 — you surge back with a breath."
            d20 == 1 -> state.copy(rolls = rolls, failures = state.failures + 2) to
                "Nat 1 — the light dims further. Two failures!"
            d20 >= 10 -> state.copy(rolls = rolls, successes = state.successes + 1) to
                "Success ($d20) — you cling to life."
            else -> state.copy(rolls = rolls, failures = state.failures + 1) to
                "Fail ($d20) — the cold creeps closer."
        }
    }
}

object CombatSystem {
    /** Build initiative order from the player + current party. Player rolls 1d20 + DEX mod. */
    fun startCombat(character: com.realmsoffate.game.data.Character, party: List<com.realmsoffate.game.data.PartyCompanion>): CombatState {
        val player = Combatant(
            name = character.name,
            hp = character.hp,
            maxHp = character.maxHp,
            initiative = Dice.d20() + character.abilities.dexMod,
            isPlayer = true
        )
        val allies = party.map {
            Combatant(
                name = it.name,
                hp = it.hp,
                maxHp = it.maxHp,
                initiative = Dice.d20(),
                isPlayer = false
            )
        }
        val order = (listOf(player) + allies).sortedByDescending { it.initiative }
        return CombatState(round = 1, order = order, activeIndex = 0)
    }

    /** Syncs HP on the existing state from latest player+party (after each turn). */
    fun syncHp(combat: CombatState, character: com.realmsoffate.game.data.Character, party: List<com.realmsoffate.game.data.PartyCompanion>): CombatState {
        val newOrder = combat.order.map { c ->
            if (c.isPlayer) c.copy(hp = character.hp, maxHp = character.maxHp)
            else {
                val match = party.firstOrNull { it.name == c.name }
                if (match != null) c.copy(hp = match.hp, maxHp = match.maxHp)
                else c
            }
        }
        return combat.copy(order = newOrder)
    }
}
