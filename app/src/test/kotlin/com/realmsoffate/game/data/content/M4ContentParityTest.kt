package com.realmsoffate.game.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.game.Spell
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parity test pinning Phase 6 M4 JSON content (the 34-entry spell / class-ability
 * database) to the historical Kotlin literal it replaced. Each expected value is
 * a verbatim copy of the literal that lived in `Spells.list` before migration.
 * Failures indicate JSON drift, not desired changes.
 */
@RunWith(RobolectricTestRunner::class)
class M4ContentParityTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun init() {
        ContentRepository.initialize(ctx)
    }

    @Test fun `spells match historical`() {
        assertEquals(EXPECTED_SPELLS, ContentRepository.spells)
    }

    private companion object {
        val EXPECTED_SPELLS: List<Spell> = listOf(
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
    }
}
