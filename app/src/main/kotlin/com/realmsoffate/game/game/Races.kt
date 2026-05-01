package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.content.ContentRepository
import kotlinx.serialization.Serializable

@Serializable
data class RaceDef(
    val name: String,
    val strBonus: Int = 0, val dexBonus: Int = 0, val conBonus: Int = 0,
    val intBonus: Int = 0, val wisBonus: Int = 0, val chaBonus: Int = 0,
    val traits: List<String>,
    val heightRange: String,
    val physiqueTemplate: String
) {
    fun applyTo(a: Abilities) {
        a.str += strBonus; a.dex += dexBonus; a.con += conBonus
        a.int += intBonus; a.wis += wisBonus; a.cha += chaBonus
    }
}

object Races {
    fun find(name: String) = ContentRepository.races.firstOrNull { it.name.equals(name, true) }
}

/**
 * Default (primary, secondary) ability indices (0=STR, 1=DEX, 2=CON, 3=INT, 4=WIS, 5=CHA)
 * derived from a race's per-stat bonuses. Used by character creation to pre-fill the
 * +2 / +1 selector with the race's canonical 5e bonus layout.
 *
 * Human (+1 to all six) cannot be fully represented by a two-slot selector — we return
 * (STR, DEX) as a representative pair. See issue #17 for the long-term fix.
 */
fun RaceDef.defaultBonusIndices(): Pair<Int, Int> {
    val bonuses = intArrayOf(strBonus, dexBonus, conBonus, intBonus, wisBonus, chaBonus)
    val sortedIdx = bonuses.indices.sortedByDescending { bonuses[it] }
    return sortedIdx[0] to sortedIdx[1]
}
