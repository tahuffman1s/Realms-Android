package com.realmsoffate.game.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.game.ClassDef
import com.realmsoffate.game.game.Feats
import com.realmsoffate.game.game.RaceDef
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parity tests pinning Phase 6 M3 JSON content (races, classes, feat metadata)
 * to the historical Kotlin literals they replaced. Each expected value is a
 * verbatim copy of the literal that lived in source before the migration.
 * Failures indicate JSON drift, not desired changes.
 */
@RunWith(RobolectricTestRunner::class)
class M3ContentParityTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun init() {
        ContentRepository.initialize(ctx)
    }

    @Test fun `races match historical`() {
        assertEquals(EXPECTED_RACES, ContentRepository.races)
    }

    @Test fun `classes match historical`() {
        assertEquals(EXPECTED_CLASSES, ContentRepository.classes)
    }

    @Test fun `feat metas match historical`() {
        assertEquals(EXPECTED_FEAT_METAS, ContentRepository.featMetas)
    }

    @Test fun `feats list resolves all apply lambdas`() {
        // Forces every JSON-listed name to be present in the Feats apply registry.
        val resolved = Feats.list
        assertEquals(EXPECTED_FEAT_METAS.size, resolved.size)
        EXPECTED_FEAT_METAS.zip(resolved).forEach { (meta, feat) ->
            assertEquals(meta.name, feat.name)
            assertEquals(meta.description, feat.description)
            assertEquals(meta.icon, feat.icon)
        }
    }

    private companion object {
        val EXPECTED_RACES: List<RaceDef> = listOf(
            RaceDef("Human", strBonus = 1, dexBonus = 1, conBonus = 1, intBonus = 1, wisBonus = 1, chaBonus = 1,
                traits = listOf("Versatile", "Extra language"),
                heightRange = "5'0\" – 6'4\"",
                physiqueTemplate = "Human build, average height. NPCs treat you as ordinary — you blend in easily."),
            RaceDef("Elf", dexBonus = 2, intBonus = 1,
                traits = listOf("Darkvision", "Keen senses", "Fey ancestry"),
                heightRange = "5'4\" – 6'2\"",
                physiqueTemplate = "Tall and slender, pointed ears, almond eyes. Your voice is melodic. NPCs sense ancient patience."),
            RaceDef("Dwarf", conBonus = 2, strBonus = 1,
                traits = listOf("Darkvision", "Poison resistance", "Stonecunning"),
                heightRange = "4'0\" – 5'0\"",
                physiqueTemplate = "Stocky, broad-shouldered, ~4 feet. You look UP at most NPCs. Your voice is a low rumble. Braided beard (if male)."),
            RaceDef("Halfling", dexBonus = 2, chaBonus = 1,
                traits = listOf("Lucky", "Brave", "Halfling nimbleness"),
                heightRange = "3'0\" – 3'6\"",
                physiqueTemplate = "Small, ~3 feet. You must reach UP for tavern tables. NPCs lean down to hear you. Curly hair, bare feet."),
            RaceDef("Gnome", intBonus = 2, dexBonus = 1,
                traits = listOf("Darkvision", "Gnome cunning", "Small"),
                heightRange = "3'0\" – 3'6\"",
                physiqueTemplate = "Small, ~3 feet. Wild hair, bright eyes. NPCs often underestimate you. Your voice is quick and sharp."),
            RaceDef("Half-Elf", chaBonus = 2, dexBonus = 1, intBonus = 1,
                traits = listOf("Darkvision", "Fey ancestry", "Two skills"),
                heightRange = "5'0\" – 6'0\"",
                physiqueTemplate = "Tall, graceful, subtly pointed ears. Neither elves nor humans fully accept you."),
            RaceDef("Half-Orc", strBonus = 2, conBonus = 1,
                traits = listOf("Darkvision", "Relentless endurance", "Savage attacks"),
                heightRange = "6'0\" – 7'0\"",
                physiqueTemplate = "Towering 6'6\"+, tusks, scarred. NPCs step back. You duck doorways. Your voice is guttural."),
            RaceDef("Tiefling", chaBonus = 2, intBonus = 1,
                traits = listOf("Darkvision", "Infernal resistance", "Thaumaturgy"),
                heightRange = "5'0\" – 6'4\"",
                physiqueTemplate = "Horns, tail, unusual skin. NPCs recoil or make signs. Merchants refuse service. Your voice carries a subtle echo."),
            RaceDef("Dragonborn", strBonus = 2, chaBonus = 1,
                traits = listOf("Breath weapon", "Draconic resistance"),
                heightRange = "6'0\" – 7'0\"",
                physiqueTemplate = "Scaled, tall, draconic. NPCs are wary. Your voice rumbles like distant thunder."),
            RaceDef("Drow", dexBonus = 2, chaBonus = 1, intBonus = 1,
                traits = listOf("Superior darkvision", "Sunlight sensitivity", "Drow magic"),
                heightRange = "5'0\" – 5'10\"",
                physiqueTemplate = "Ebony skin, white hair. Surface-dwellers distrust you openly. You squint in sunlight."),
            RaceDef("Githyanki", strBonus = 2, intBonus = 1,
                traits = listOf("Astral knowledge", "Psionic blade", "Misty Step (1/long rest)"),
                heightRange = "5'10\" – 6'8\"",
                physiqueTemplate = "Tall, gaunt, yellow-green skin, sharp eyes the colour of old bronze. NPCs sense an alien stillness — the kind that carries silver swords and flies on red dragons in dreams.")
        )

        val EXPECTED_CLASSES: List<ClassDef> = listOf(
            ClassDef(
                name = "Fighter", hitDie = 10, primary = "STR",
                savingThrows = listOf("STR", "CON"),
                proficiencies = listOf("Athletics", "Intimidation"),
                startingItems = listOf(
                    Item("Longsword", "+1d8 slashing", "weapon", "common", equipped = true, damage = "1d8"),
                    Item("Chain Mail", "AC 16", "armor", "common", equipped = true, ac = 16),
                    Item("Shield", "+2 AC", "shield", "common", equipped = true),
                    Item("Healing Potion", "Restore 2d4+2 HP", "consumable", "common", qty = 2),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = false,
                recommended = listOf(15, 14, 14, 8, 12, 8)
            ),
            ClassDef(
                name = "Wizard", hitDie = 6, primary = "INT",
                savingThrows = listOf("INT", "WIS"),
                proficiencies = listOf("Arcana", "Investigation"),
                startingItems = listOf(
                    Item("Quarterstaff", "+1d6 bludgeoning", "weapon", "common", equipped = true, damage = "1d6"),
                    Item("Spellbook", "Contains your known spells", "book", "uncommon"),
                    Item("Component Pouch", "For spellcasting", "pouch", "common"),
                    Item("Scroll of Shield", "Reaction, +5 AC", "scroll", "common"),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = true, spellAbility = "INT",
                recommended = listOf(8, 14, 14, 15, 12, 8)
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
                    Item("Daggers", "+1d4 piercing", "weapon", "common", qty = 3),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = false,
                recommended = listOf(8, 15, 14, 14, 12, 8)
            ),
            ClassDef(
                name = "Cleric", hitDie = 8, primary = "WIS",
                savingThrows = listOf("WIS", "CHA"),
                proficiencies = listOf("Medicine", "Religion", "Insight"),
                startingItems = listOf(
                    Item("Mace", "+1d6 bludgeoning", "weapon", "common", equipped = true, damage = "1d6"),
                    Item("Scale Mail", "AC 14+DEX", "armor", "common", equipped = true, ac = 14),
                    Item("Holy Symbol", "For divine spells", "focus", "common"),
                    Item("Shield", "+2 AC", "shield", "common", equipped = true),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = true, spellAbility = "WIS",
                recommended = listOf(14, 8, 14, 8, 15, 12)
            ),
            ClassDef(
                name = "Ranger", hitDie = 10, primary = "DEX",
                savingThrows = listOf("STR", "DEX"),
                proficiencies = listOf("Survival", "Nature", "Perception", "Stealth"),
                startingItems = listOf(
                    Item("Longbow", "+1d8 piercing ranged", "weapon", "common", equipped = true, damage = "1d8"),
                    Item("Shortsword", "+1d6 piercing", "weapon", "common", equipped = true, damage = "1d6"),
                    Item("Leather Armor", "AC 11+DEX", "armor", "common", equipped = true, ac = 11),
                    Item("Quiver", "20 arrows", "gear", "common"),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = true, spellAbility = "WIS",
                recommended = listOf(8, 15, 14, 12, 14, 8)
            ),
            ClassDef(
                name = "Barbarian", hitDie = 12, primary = "STR",
                savingThrows = listOf("STR", "CON"),
                proficiencies = listOf("Athletics", "Survival", "Intimidation"),
                startingItems = listOf(
                    Item("Greataxe", "+1d12 slashing", "weapon", "common", equipped = true, damage = "1d12"),
                    Item("Handaxes", "+1d6 slashing", "weapon", "common", qty = 2),
                    Item("Hide Armor", "AC 12+DEX", "armor", "common", equipped = true, ac = 12),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = false,
                recommended = listOf(15, 14, 14, 8, 12, 8)
            ),
            ClassDef(
                name = "Bard", hitDie = 8, primary = "CHA",
                savingThrows = listOf("DEX", "CHA"),
                proficiencies = listOf("Performance", "Persuasion", "Deception", "Insight"),
                startingItems = listOf(
                    Item("Rapier", "+1d8 piercing", "weapon", "common", equipped = true, damage = "1d8"),
                    Item("Lute", "Performance focus", "instrument", "common"),
                    Item("Leather Armor", "AC 11+DEX", "armor", "common", equipped = true, ac = 11),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = true, spellAbility = "CHA",
                recommended = listOf(8, 14, 14, 8, 12, 15)
            ),
            ClassDef(
                name = "Paladin", hitDie = 10, primary = "STR",
                savingThrows = listOf("WIS", "CHA"),
                proficiencies = listOf("Athletics", "Religion", "Persuasion"),
                startingItems = listOf(
                    Item("Warhammer", "+1d8 bludgeoning", "weapon", "common", equipped = true, damage = "1d8"),
                    Item("Chain Mail", "AC 16", "armor", "common", equipped = true, ac = 16),
                    Item("Shield", "+2 AC", "shield", "common", equipped = true),
                    Item("Holy Symbol", "For divine spells", "focus", "common"),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = true, spellAbility = "CHA",
                recommended = listOf(15, 8, 14, 8, 12, 14)
            ),
            ClassDef(
                name = "Sorcerer", hitDie = 6, primary = "CHA",
                savingThrows = listOf("CON", "CHA"),
                proficiencies = listOf("Arcana", "Persuasion"),
                startingItems = listOf(
                    Item("Dagger", "+1d4 piercing", "weapon", "common", equipped = true, damage = "1d4"),
                    Item("Arcane Focus", "Crystal orb", "focus", "common"),
                    Item("Component Pouch", "For spellcasting", "pouch", "common"),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = true, spellAbility = "CHA",
                recommended = listOf(8, 14, 14, 8, 12, 15)
            ),
            ClassDef(
                name = "Warlock", hitDie = 8, primary = "CHA",
                savingThrows = listOf("WIS", "CHA"),
                proficiencies = listOf("Arcana", "Deception", "Intimidation"),
                startingItems = listOf(
                    Item("Light Crossbow", "+1d8 piercing ranged", "weapon", "common", equipped = true, damage = "1d8"),
                    Item("Leather Armor", "AC 11+DEX", "armor", "common", equipped = true, ac = 11),
                    Item("Pact Weapon Focus", "Arcane channel", "focus", "uncommon"),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = true, spellAbility = "CHA",
                recommended = listOf(8, 14, 14, 8, 12, 15)
            ),
            ClassDef(
                name = "Monk", hitDie = 8, primary = "DEX",
                savingThrows = listOf("STR", "DEX"),
                proficiencies = listOf("Acrobatics", "Stealth", "Athletics"),
                startingItems = listOf(
                    Item("Quarterstaff", "+1d6 bludgeoning", "weapon", "common", equipped = true, damage = "1d6"),
                    Item("Darts", "+1d4 piercing", "weapon", "common", qty = 10),
                    Item("Monk's Robes", "AC 10+DEX+WIS", "armor", "common", equipped = true),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = false,
                recommended = listOf(8, 15, 14, 8, 14, 12)
            ),
            ClassDef(
                name = "Druid", hitDie = 8, primary = "WIS",
                savingThrows = listOf("INT", "WIS"),
                proficiencies = listOf("Nature", "Animal Handling", "Survival"),
                startingItems = listOf(
                    Item("Scimitar", "+1d6 slashing", "weapon", "common", equipped = true, damage = "1d6"),
                    Item("Wooden Shield", "+2 AC", "shield", "common", equipped = true),
                    Item("Druidic Focus", "Carved staff", "focus", "common"),
                    Item("Healing Berries", "+1d4 HP", "consumable", "common", qty = 3),
                    Item("Traveler's Clothes", "Sturdy travel garments", "clothes", "common", equipped = true)
                ),
                isCaster = true, spellAbility = "WIS",
                recommended = listOf(8, 14, 14, 12, 15, 8)
            )
        )

        val EXPECTED_FEAT_METAS: List<FeatMeta> = listOf(
            FeatMeta("Lucky", "3x per long rest, reroll a d20. +1 to all saves.", "🎲"),
            FeatMeta("Tough", "+2 HP per level (retroactive). You're harder to kill.", "💪"),
            FeatMeta("Sharpshooter", "Ranged attacks ignore cover. +2 DEX.", "🏹"),
            FeatMeta("War Caster", "Advantage on concentration saves. +2 to spellcasting ability.", "🔮"),
            FeatMeta("Great Weapon Master", "Heavy weapon crits deal +10 damage. +2 STR.", "⚔️"),
            FeatMeta("Sentinel", "Enemies you hit can't disengage. +1 AC.", "🛡️"),
            FeatMeta("Alert", "Can't be surprised. +5 to initiative. +2 DEX.", "👁️"),
            FeatMeta("Resilient", "+2 CON, proficiency in CON saves.", "❤️"),
            FeatMeta("Observant", "+5 passive Perception. +2 WIS.", "🔍"),
            FeatMeta("Actor", "Advantage on Deception. +2 CHA.", "🎭"),
            FeatMeta("Tavern Brawler", "Improvised weapons deal 1d4+STR. +2 STR.", "🍺"),
            FeatMeta("Magic Initiate", "Learn 2 cantrips and 1 spell from any class. +2 INT.", "✨")
        )
    }
}
