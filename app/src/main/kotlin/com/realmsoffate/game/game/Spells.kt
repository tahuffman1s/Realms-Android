package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character

/**
 * Full 34-entry spell / class-ability database ported verbatim from
 * realms_of_fate.html's SPELL_DB. Level 0 is a mix of true cantrips and
 * class-specific martial abilities (Second Wind, Rage, etc.) so the hotbar
 * can surface them uniformly.
 */
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
    val list: List<Spell> = listOf(
        // ----- CANTRIPS & CLASS ABILITIES -----
        Spell("fire-bolt", "Fire Bolt", 0, "Evocation", listOf("Wizard", "Sorcerer"),
            "Hurl a mote of fire. 1d10 fire damage.", "🔥", "1d10 fire"),
        Spell("eldritch-blast", "Eldritch Blast", 0, "Evocation", listOf("Warlock"),
            "Beam of crackling energy. 1d10 force damage.", "⚡", "1d10 force"),
        Spell("sacred-flame", "Sacred Flame", 0, "Evocation", listOf("Cleric"),
            "Radiant flame descends. 1d8 radiant damage. DEX save.", "✨", "1d8 radiant"),
        Spell("vicious-mockery", "Vicious Mockery", 0, "Enchantment", listOf("Bard"),
            "Psychic insult. 1d4 psychic damage + disadvantage.", "🎭", "1d4 psychic"),
        Spell("produce-flame", "Produce Flame", 0, "Conjuration", listOf("Druid"),
            "Flickering flame. 1d8 fire damage or light source.", "🕯️", "1d8 fire"),
        Spell("true-strike", "True Strike", 0, "Divination", listOf("Wizard", "Sorcerer", "Bard", "Warlock"),
            "Gain advantage on next attack.", "🎯"),
        Spell("mage-hand", "Mage Hand", 0, "Conjuration", listOf("Wizard", "Sorcerer", "Bard", "Warlock"),
            "Spectral hand manipulates objects at range.", "🖐️"),
        Spell("thaumaturgy", "Thaumaturgy", 0, "Transmutation", listOf("Cleric"),
            "Manifest minor divine wonder.", "💫"),
        Spell("second-wind", "Second Wind", 0, "Martial", listOf("Fighter"),
            "Heal 1d10+level HP. Once per rest.", "💨", "Heal 1d10", restCharged = true),
        Spell("rage", "Rage", 0, "Martial", listOf("Barbarian"),
            "+2 damage, resistance to physical. Once per rest.", "😠", "+2 dmg", restCharged = true),
        Spell("sneak-attack", "Sneak Attack", 0, "Martial", listOf("Rogue"),
            "Extra 1d6 damage when you have advantage.", "🗡️", "+1d6"),
        Spell("flurry-of-blows", "Flurry of Blows", 0, "Martial", listOf("Monk"),
            "Two unarmed strikes as bonus action.", "👊", "2x unarmed"),
        Spell("lay-on-hands", "Lay on Hands", 0, "Martial", listOf("Paladin"),
            "Heal 5×Paladin level HP total pool.", "🤲", "Heal"),
        Spell("hunters-mark", "Hunter's Mark", 0, "Martial", listOf("Ranger"),
            "Mark target. +1d6 damage per hit.", "🎯", "+1d6"),
        Spell("wild-shape", "Wild Shape", 0, "Martial", listOf("Druid"),
            "Transform into a beast. Twice per rest.", "🐺", unlockLevel = 2, restCharged = true),

        // ----- LEVEL 1 -----
        Spell("magic-missile", "Magic Missile", 1, "Evocation", listOf("Wizard", "Sorcerer"),
            "Three darts of force. 3×1d4+1. Always hits.", "⭐", "3d4+3 force"),
        Spell("shield", "Shield", 1, "Abjuration", listOf("Wizard", "Sorcerer"),
            "Reaction: +5 AC until next turn.", "🛡️"),
        Spell("healing-word", "Healing Word", 1, "Evocation", listOf("Cleric", "Bard", "Druid"),
            "Bonus action heal. 1d4+WIS mod.", "💚", "Heal 1d4+mod"),
        Spell("thunderwave", "Thunderwave", 1, "Evocation", listOf("Wizard", "Sorcerer", "Bard", "Druid"),
            "Wave of thunder. 2d8 thunder + push.", "🌊", "2d8 thunder"),
        Spell("guiding-bolt", "Guiding Bolt", 1, "Evocation", listOf("Cleric"),
            "Flash of light. 4d6 radiant + advantage on next attack.", "☀️", "4d6 radiant"),
        Spell("hex", "Hex", 1, "Enchantment", listOf("Warlock"),
            "Curse target. +1d6 necrotic per hit.", "💀", "+1d6 necrotic"),
        Spell("charm-person", "Charm Person", 1, "Enchantment", listOf("Bard", "Sorcerer", "Warlock", "Wizard"),
            "Charm a humanoid. WIS save.", "💕"),
        Spell("divine-smite", "Divine Smite", 1, "Martial", listOf("Paladin"),
            "Expend slot for +2d8 radiant damage on hit.", "⚔️", "+2d8 radiant", unlockLevel = 2),
        Spell("cure-wounds", "Cure Wounds", 1, "Evocation", listOf("Cleric", "Druid", "Paladin"),
            "Touch heal 1d8+WIS mod.", "❤️‍🩹", "Heal 1d8+mod"),

        // ----- LEVEL 2 -----
        Spell("misty-step", "Misty Step", 2, "Conjuration", listOf("Wizard", "Sorcerer", "Warlock"),
            "Bonus action teleport 30ft.", "💨", unlockLevel = 3),
        Spell("scorching-ray", "Scorching Ray", 2, "Evocation", listOf("Wizard", "Sorcerer"),
            "Three fire rays. 3×2d6 fire.", "🔥", "6d6 fire", unlockLevel = 3),
        Spell("hold-person", "Hold Person", 2, "Enchantment", listOf("Wizard", "Sorcerer", "Bard", "Cleric", "Warlock", "Druid"),
            "Paralyze a humanoid. WIS save.", "⛔", unlockLevel = 3),
        Spell("spiritual-weapon", "Spiritual Weapon", 2, "Evocation", listOf("Cleric"),
            "Floating weapon attacks. 1d8+WIS force.", "🗡️", "1d8+mod force", unlockLevel = 3),
        Spell("shatter", "Shatter", 2, "Evocation", listOf("Wizard", "Sorcerer", "Bard"),
            "Burst of sound. 3d8 thunder in area.", "💥", "3d8 thunder", unlockLevel = 3),

        // ----- LEVEL 3 -----
        Spell("fireball", "Fireball", 3, "Evocation", listOf("Wizard", "Sorcerer"),
            "Explosion of flame. 8d6 fire in 20ft radius.", "☄️", "8d6 fire", unlockLevel = 5),
        Spell("counterspell", "Counterspell", 3, "Abjuration", listOf("Wizard", "Sorcerer", "Warlock"),
            "Reaction: negate a spell being cast.", "🚫", unlockLevel = 5),
        Spell("revivify", "Revivify", 3, "Necromancy", listOf("Cleric", "Paladin", "Druid"),
            "Return a creature dead <1 min to life with 1 HP.", "🌟", "Revive", unlockLevel = 5),
        Spell("spirit-guardians", "Spirit Guardians", 3, "Conjuration", listOf("Cleric"),
            "Spectral spirits deal 3d8 radiant in area.", "👼", "3d8 radiant", unlockLevel = 5),
        Spell("lightning-bolt", "Lightning Bolt", 3, "Evocation", listOf("Wizard", "Sorcerer"),
            "Line of lightning. 8d6 lightning damage.", "⚡", "8d6 lightning", unlockLevel = 5)
    )

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
        1 to listOf(2, 2, 0, 0, 0, 0),
        2 to listOf(2, 3, 0, 0, 0, 0),
        3 to listOf(2, 4, 2, 0, 0, 0),
        4 to listOf(3, 4, 3, 0, 0, 0),
        5 to listOf(3, 4, 3, 2, 0, 0),
        6 to listOf(3, 4, 3, 3, 0, 0),
        7 to listOf(3, 4, 3, 3, 1, 0),
        8 to listOf(3, 4, 3, 3, 2, 0)
    )

    private val HALF_CASTER = mapOf(
        1 to listOf(0, 0, 0, 0, 0, 0),
        2 to listOf(0, 2, 0, 0, 0, 0),
        3 to listOf(0, 3, 0, 0, 0, 0),
        4 to listOf(0, 3, 0, 0, 0, 0),
        5 to listOf(0, 4, 2, 0, 0, 0),
        6 to listOf(0, 4, 2, 0, 0, 0),
        7 to listOf(0, 4, 3, 0, 0, 0),
        8 to listOf(0, 4, 3, 0, 0, 0)
    )

    private val FULL_CLASSES = setOf("Wizard", "Sorcerer", "Cleric", "Bard", "Druid", "Warlock")
    private val HALF_CLASSES = setOf("Paladin", "Ranger")

    fun slotsForLevel(cls: String, level: Int): List<Int> {
        val lv = level.coerceIn(1, 8)
        return when (cls) {
            in FULL_CLASSES -> FULL_CASTER[lv] ?: listOf(0, 0, 0, 0, 0, 0)
            in HALF_CLASSES -> HALF_CASTER[lv] ?: listOf(0, 0, 0, 0, 0, 0)
            else -> listOf(0, 0, 0, 0, 0, 0)
        }
    }

    /** Short rest — Fighter/Monk/Warlock abilities recharge. Long rest — full restore. */
    fun applyShortRest(ch: Character) {
        // Only Warlock slots refresh on short rest (pact magic). Others stay consumed.
        if (ch.cls == "Warlock") {
            ch.maxSpellSlots.forEach { (k, max) -> ch.spellSlots[k] = max }
        }
    }

    fun applyLongRest(ch: Character) {
        ch.hp = ch.maxHp
        ch.maxSpellSlots.forEach { (k, max) -> ch.spellSlots[k] = max }
    }
}
