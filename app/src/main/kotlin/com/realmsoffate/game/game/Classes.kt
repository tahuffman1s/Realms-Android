package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.data.content.ContentRepository
import kotlinx.serialization.Serializable

@Serializable
data class ClassDef(
    val name: String,
    val hitDie: Int,
    val primary: String,
    val savingThrows: List<String>,
    val proficiencies: List<String>,
    val startingItems: List<Item>,
    val isCaster: Boolean,
    val spellAbility: String? = null,
    /**
     * Recommended ability score array — STR, DEX, CON, INT, WIS, CHA.
     * Verbatim from the web source-of-truth's CLASSES table.
     */
    val recommended: List<Int> = listOf(13, 13, 13, 13, 13, 13)
)

/**
 * Look-up helper — recommended array per class. Falls back to a balanced
 * 13-spread if the class isn't in the table.
 */
fun ClassDef.recommendedArray(): IntArray = recommended.toIntArray()

object Classes {
    fun find(name: String) = ContentRepository.classes.firstOrNull { it.name.equals(name, true) }

    fun rollHp(clsName: String, con: Int): Int {
        val cls = find(clsName) ?: return 10
        val conMod = (con - 10) / 2
        return cls.hitDie + conMod
    }
}

fun applyClassStart(ch: Character, cls: ClassDef) {
    ch.maxHp = Classes.rollHp(cls.name, ch.abilities.con)
    ch.hp = ch.maxHp
    ch.inventory.clear()
    ch.inventory.addAll(cls.startingItems.map { it.copy() })
    ch.ac = EquipmentEffects.effectiveAc(ch)
    if (cls.isCaster) Spells.grantStartingSpells(ch, cls)
}
