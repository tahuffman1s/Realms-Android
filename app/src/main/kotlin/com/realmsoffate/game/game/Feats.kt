package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character

data class Feat(
    val name: String,
    val description: String,
    val icon: String,
    val apply: (Character) -> Unit
)

object Feats {
    val list = listOf(
        Feat("Lucky", "3x per long rest, reroll a d20. +1 to all saves.", "\uD83C\uDFB2") { ch ->
            ch.abilities.con += 1
            ch.maxHp += 1
            ch.hp += 1
        },
        Feat("Tough", "+2 HP per level (retroactive). You're harder to kill.", "\uD83D\uDCAA") { ch ->
            ch.maxHp += ch.level * 2
            ch.hp += ch.level * 2
        },
        Feat("Sharpshooter", "Ranged attacks ignore cover. +2 DEX.", "\uD83C\uDFF9") { ch ->
            ch.abilities.dex += 2
        },
        Feat("War Caster", "Advantage on concentration saves. +2 to spellcasting ability.", "\uD83D\uDD2E") { ch ->
            val cls = Classes.find(ch.cls)
            when (cls?.spellAbility) {
                "INT" -> ch.abilities.int += 2
                "WIS" -> ch.abilities.wis += 2
                "CHA" -> ch.abilities.cha += 2
                else -> ch.abilities.int += 2
            }
        },
        Feat("Great Weapon Master", "Heavy weapon crits deal +10 damage. +2 STR.", "\u2694\uFE0F") { ch ->
            ch.abilities.str += 2
        },
        Feat("Sentinel", "Enemies you hit can't disengage. +1 AC.", "\uD83D\uDEE1\uFE0F") { ch ->
            ch.ac += 1
        },
        Feat("Alert", "Can't be surprised. +5 to initiative. +2 DEX.", "\uD83D\uDC41\uFE0F") { ch ->
            ch.abilities.dex += 2
        },
        Feat("Resilient", "+2 CON, proficiency in CON saves.", "\u2764\uFE0F") { ch ->
            ch.abilities.con += 2
            ch.maxHp += 2
            ch.hp += 2
        },
        Feat("Observant", "+5 passive Perception. +2 WIS.", "\uD83D\uDD0D") { ch ->
            ch.abilities.wis += 2
        },
        Feat("Actor", "Advantage on Deception. +2 CHA.", "\uD83C\uDFAD") { ch ->
            ch.abilities.cha += 2
        },
        Feat("Tavern Brawler", "Improvised weapons deal 1d4+STR. +2 STR.", "\uD83C\uDF7A") { ch ->
            ch.abilities.str += 2
        },
        Feat("Magic Initiate", "Learn 2 cantrips and 1 spell from any class. +2 INT.", "\u2728") { ch ->
            ch.abilities.int += 2
        }
    )

    fun find(name: String) = list.firstOrNull { it.name.equals(name, true) }
}
