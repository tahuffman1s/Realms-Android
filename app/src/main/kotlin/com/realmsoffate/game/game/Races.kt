package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities

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
    val list = listOf(
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
            physiqueTemplate = "Ebony skin, white hair. Surface-dwellers distrust you openly. You squint in sunlight.")
    )
    fun find(name: String) = list.firstOrNull { it.name.equals(name, true) }
}
