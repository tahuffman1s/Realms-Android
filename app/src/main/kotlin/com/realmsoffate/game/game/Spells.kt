package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.content.ContentRepository
import kotlinx.serialization.Serializable

/**
 * Spell / class-ability record. Level 0 is a mix of true cantrips and
 * class-specific martial abilities (Second Wind, Rage, etc.) so the hotbar
 * can surface them uniformly. Backing list lives in JSON — see
 * `assets/content/spells/spells.json`.
 */
@Serializable
data class Spell(
    val id: String,
    val name: String,
    val level: Int,
    val school: String,
    val classes: List<String>,
    val desc: String,
    val icon: String,
    val damage: String = "-",
    val unlockLevel: Int = 1,
    val restCharged: Boolean = false
)

object Spells {
    val list: List<Spell> get() = ContentRepository.spells

    fun find(name: String): Spell? = list.firstOrNull { it.name.equals(name, true) }

    fun knownFor(cls: String, level: Int): List<Spell> =
        list.filter { cls in it.classes && it.unlockLevel <= level }

    fun grantStartingSpells(ch: Character, cls: ClassDef) {
        val eligible = list.filter { cls.name in it.classes && it.unlockLevel <= ch.level }
        val cantrips = eligible.filter { it.level == 0 }.take(3).map { it.name }
        val l1 = eligible.filter { it.level == 1 }.take(2).map { it.name }
        ch.knownSpells.clear()
        ch.knownSpells.addAll(cantrips + l1)
        ch.maxSpellSlots.clear()
        ch.spellSlots.clear()
        SpellSlots.slotsForLevel(cls.name, ch.level).forEachIndexed { idx, n ->
            if (n > 0 && idx > 0) {
                ch.maxSpellSlots[idx] = n
                ch.spellSlots[idx] = n
            }
        }
    }
}

/**
 * D&D 5e-style slot progression. [index 0] = cantrip count, [1..5] = slots L1-L5.
 */
object SpellSlots {
    private val FULL_CASTER = mapOf(
        1  to listOf(2, 2, 0, 0, 0, 0),
        2  to listOf(2, 3, 0, 0, 0, 0),
        3  to listOf(2, 4, 2, 0, 0, 0),
        4  to listOf(3, 4, 3, 0, 0, 0),
        5  to listOf(3, 4, 3, 2, 0, 0),
        6  to listOf(3, 4, 3, 3, 0, 0),
        7  to listOf(3, 4, 3, 3, 1, 0),
        8  to listOf(3, 4, 3, 3, 2, 0),
        9  to listOf(3, 4, 3, 3, 3, 1),
        10 to listOf(4, 4, 3, 3, 3, 2),
        11 to listOf(4, 4, 3, 3, 3, 2),
        12 to listOf(4, 4, 3, 3, 3, 2),
        13 to listOf(4, 4, 3, 3, 3, 2),
        14 to listOf(4, 4, 3, 3, 3, 2),
        15 to listOf(4, 4, 3, 3, 3, 2),
        16 to listOf(4, 4, 3, 3, 3, 2),
        17 to listOf(4, 4, 3, 3, 3, 2),
        18 to listOf(4, 4, 3, 3, 3, 3),
        19 to listOf(4, 4, 3, 3, 3, 3),
        20 to listOf(4, 4, 3, 3, 3, 3)
    )

    private val HALF_CASTER = mapOf(
        1  to listOf(0, 0, 0, 0, 0, 0),
        2  to listOf(0, 2, 0, 0, 0, 0),
        3  to listOf(0, 3, 0, 0, 0, 0),
        4  to listOf(0, 3, 0, 0, 0, 0),
        5  to listOf(0, 4, 2, 0, 0, 0),
        6  to listOf(0, 4, 2, 0, 0, 0),
        7  to listOf(0, 4, 3, 0, 0, 0),
        8  to listOf(0, 4, 3, 0, 0, 0),
        9  to listOf(0, 4, 3, 2, 0, 0),
        10 to listOf(0, 4, 3, 2, 0, 0),
        11 to listOf(0, 4, 3, 3, 0, 0),
        12 to listOf(0, 4, 3, 3, 0, 0),
        13 to listOf(0, 4, 3, 3, 1, 0),
        14 to listOf(0, 4, 3, 3, 1, 0),
        15 to listOf(0, 4, 3, 3, 2, 0),
        16 to listOf(0, 4, 3, 3, 2, 0),
        17 to listOf(0, 4, 3, 3, 3, 1),
        18 to listOf(0, 4, 3, 3, 3, 1),
        19 to listOf(0, 4, 3, 3, 3, 2),
        20 to listOf(0, 4, 3, 3, 3, 2)
    )

    private val FULL_CLASSES = setOf("Wizard", "Sorcerer", "Cleric", "Bard", "Druid", "Warlock")
    private val HALF_CLASSES = setOf("Paladin", "Ranger")

    fun slotsForLevel(cls: String, level: Int): List<Int> {
        val lv = level.coerceIn(1, 20)
        return when (cls) {
            in FULL_CLASSES -> FULL_CASTER[lv] ?: listOf(0, 0, 0, 0, 0, 0)
            in HALF_CLASSES -> HALF_CASTER[lv] ?: listOf(0, 0, 0, 0, 0, 0)
            else -> listOf(0, 0, 0, 0, 0, 0)
        }
    }

}
