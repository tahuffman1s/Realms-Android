package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.Item

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
    val recommended: IntArray = intArrayOf(13, 13, 13, 13, 13, 13)
)

/**
 * Look-up helper — recommended array per class. Falls back to a balanced
 * 13-spread if the class isn't in the table.
 */
fun ClassDef.recommendedArray(): IntArray = recommended

object Classes {
    val list = listOf(
        ClassDef(
            name = "Fighter", hitDie = 10, primary = "STR",
            savingThrows = listOf("STR", "CON"),
            proficiencies = listOf("Athletics", "Intimidation"),
            startingItems = listOf(
                Item("Longsword", "+1d8 slashing", "weapon", "common", equipped = true, damage = "1d8"),
                Item("Chain Mail", "AC 16", "armor", "common", equipped = true, ac = 16),
                Item("Shield", "+2 AC", "shield", "common", equipped = true),
                Item("Healing Potion", "Restore 2d4+2 HP", "consumable", "common", qty = 2)
            ),
            isCaster = false,
            recommended = intArrayOf(15, 14, 14, 8, 12, 8) // Fighter
        ),
        ClassDef(
            name = "Wizard", hitDie = 6, primary = "INT",
            savingThrows = listOf("INT", "WIS"),
            proficiencies = listOf("Arcana", "Investigation"),
            startingItems = listOf(
                Item("Quarterstaff", "+1d6 bludgeoning", "weapon", "common", equipped = true, damage = "1d6"),
                Item("Spellbook", "Contains your known spells", "book", "uncommon"),
                Item("Component Pouch", "For spellcasting", "pouch", "common"),
                Item("Scroll of Shield", "Reaction, +5 AC", "scroll", "common")
            ),
            isCaster = true, spellAbility = "INT",
            recommended = intArrayOf(8, 14, 14, 15, 12, 8) // Wizard
        ),
        ClassDef(
            name = "Rogue", hitDie = 8, primary = "DEX",
            savingThrows = listOf("DEX", "INT"),
            proficiencies = listOf("Stealth", "Sleight of Hand", "Perception", "Deception"),
            startingItems = listOf(
                Item("Shortsword", "+1d6 piercing", "weapon", "common", equipped = true, damage = "1d6"),
                Item("Shortbow", "+1d6 piercing ranged", "weapon", "common", damage = "1d6"),
                Item("Leather Armor", "AC 11+DEX", "armor", "common", equipped = true, ac = 11),
                Item("Thieves' Tools", "Pick locks, disable traps", "tool", "common"),
                Item("Daggers", "+1d4 piercing", "weapon", "common", qty = 3)
            ),
            isCaster = false,
            recommended = intArrayOf(8, 15, 14, 14, 12, 8) // Rogue
        ),
        ClassDef(
            name = "Cleric", hitDie = 8, primary = "WIS",
            savingThrows = listOf("WIS", "CHA"),
            proficiencies = listOf("Medicine", "Religion", "Insight"),
            startingItems = listOf(
                Item("Mace", "+1d6 bludgeoning", "weapon", "common", equipped = true, damage = "1d6"),
                Item("Scale Mail", "AC 14+DEX", "armor", "common", equipped = true, ac = 14),
                Item("Holy Symbol", "For divine spells", "focus", "common"),
                Item("Shield", "+2 AC", "shield", "common", equipped = true)
            ),
            isCaster = true, spellAbility = "WIS",
            recommended = intArrayOf(14, 8, 14, 8, 15, 12) // Cleric
        ),
        ClassDef(
            name = "Ranger", hitDie = 10, primary = "DEX",
            savingThrows = listOf("STR", "DEX"),
            proficiencies = listOf("Survival", "Nature", "Perception", "Stealth"),
            startingItems = listOf(
                Item("Longbow", "+1d8 piercing ranged", "weapon", "common", equipped = true, damage = "1d8"),
                Item("Shortsword", "+1d6 piercing", "weapon", "common", equipped = true, damage = "1d6"),
                Item("Leather Armor", "AC 11+DEX", "armor", "common", equipped = true, ac = 11),
                Item("Quiver", "20 arrows", "gear", "common")
            ),
            isCaster = true, spellAbility = "WIS",
            recommended = intArrayOf(8, 15, 14, 12, 14, 8) // Ranger
        ),
        ClassDef(
            name = "Barbarian", hitDie = 12, primary = "STR",
            savingThrows = listOf("STR", "CON"),
            proficiencies = listOf("Athletics", "Survival", "Intimidation"),
            startingItems = listOf(
                Item("Greataxe", "+1d12 slashing", "weapon", "common", equipped = true, damage = "1d12"),
                Item("Handaxes", "+1d6 slashing", "weapon", "common", qty = 2),
                Item("Hide Armor", "AC 12+DEX", "armor", "common", equipped = true, ac = 12)
            ),
            isCaster = false,
            recommended = intArrayOf(15, 14, 14, 8, 12, 8) // Barbarian
        ),
        ClassDef(
            name = "Bard", hitDie = 8, primary = "CHA",
            savingThrows = listOf("DEX", "CHA"),
            proficiencies = listOf("Performance", "Persuasion", "Deception", "Insight"),
            startingItems = listOf(
                Item("Rapier", "+1d8 piercing", "weapon", "common", equipped = true, damage = "1d8"),
                Item("Lute", "Performance focus", "instrument", "common"),
                Item("Leather Armor", "AC 11+DEX", "armor", "common", equipped = true, ac = 11)
            ),
            isCaster = true, spellAbility = "CHA",
            recommended = intArrayOf(8, 14, 14, 8, 12, 15) // Bard
        ),
        ClassDef(
            name = "Paladin", hitDie = 10, primary = "STR",
            savingThrows = listOf("WIS", "CHA"),
            proficiencies = listOf("Athletics", "Religion", "Persuasion"),
            startingItems = listOf(
                Item("Warhammer", "+1d8 bludgeoning", "weapon", "common", equipped = true, damage = "1d8"),
                Item("Chain Mail", "AC 16", "armor", "common", equipped = true, ac = 16),
                Item("Shield", "+2 AC", "shield", "common", equipped = true),
                Item("Holy Symbol", "For divine spells", "focus", "common")
            ),
            isCaster = true, spellAbility = "CHA",
            recommended = intArrayOf(15, 8, 14, 8, 12, 14) // Paladin
        ),
        ClassDef(
            name = "Sorcerer", hitDie = 6, primary = "CHA",
            savingThrows = listOf("CON", "CHA"),
            proficiencies = listOf("Arcana", "Persuasion"),
            startingItems = listOf(
                Item("Dagger", "+1d4 piercing", "weapon", "common", equipped = true, damage = "1d4"),
                Item("Arcane Focus", "Crystal orb", "focus", "common"),
                Item("Component Pouch", "For spellcasting", "pouch", "common")
            ),
            isCaster = true, spellAbility = "CHA",
            recommended = intArrayOf(8, 14, 14, 8, 12, 15) // Sorcerer
        ),
        ClassDef(
            name = "Warlock", hitDie = 8, primary = "CHA",
            savingThrows = listOf("WIS", "CHA"),
            proficiencies = listOf("Arcana", "Deception", "Intimidation"),
            startingItems = listOf(
                Item("Light Crossbow", "+1d8 piercing ranged", "weapon", "common", equipped = true, damage = "1d8"),
                Item("Leather Armor", "AC 11+DEX", "armor", "common", equipped = true, ac = 11),
                Item("Pact Weapon Focus", "Arcane channel", "focus", "uncommon")
            ),
            isCaster = true, spellAbility = "CHA",
            recommended = intArrayOf(8, 14, 14, 8, 12, 15) // Warlock
        ),
        ClassDef(
            name = "Monk", hitDie = 8, primary = "DEX",
            savingThrows = listOf("STR", "DEX"),
            proficiencies = listOf("Acrobatics", "Stealth", "Athletics"),
            startingItems = listOf(
                Item("Quarterstaff", "+1d6 bludgeoning", "weapon", "common", equipped = true, damage = "1d6"),
                Item("Darts", "+1d4 piercing", "weapon", "common", qty = 10),
                Item("Monk's Robes", "AC 10+DEX+WIS", "armor", "common", equipped = true)
            ),
            isCaster = false,
            recommended = intArrayOf(8, 15, 14, 8, 14, 12) // Monk
        ),
        ClassDef(
            name = "Druid", hitDie = 8, primary = "WIS",
            savingThrows = listOf("INT", "WIS"),
            proficiencies = listOf("Nature", "Animal Handling", "Survival"),
            startingItems = listOf(
                Item("Scimitar", "+1d6 slashing", "weapon", "common", equipped = true, damage = "1d6"),
                Item("Wooden Shield", "+2 AC", "shield", "common", equipped = true),
                Item("Druidic Focus", "Carved staff", "focus", "common"),
                Item("Healing Berries", "+1d4 HP", "consumable", "common", qty = 3)
            ),
            isCaster = true, spellAbility = "WIS",
            recommended = intArrayOf(8, 14, 14, 12, 15, 8) // Druid
        )
    )

    fun find(name: String) = list.firstOrNull { it.name.equals(name, true) }

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
