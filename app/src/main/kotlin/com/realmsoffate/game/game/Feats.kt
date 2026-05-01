package com.realmsoffate.game.game

import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.content.ContentRepository
import com.realmsoffate.game.data.content.FeatMeta

data class Feat(
    val name: String,
    val description: String,
    val icon: String,
    val apply: (Character) -> Unit
)

object Feats {
    private val applyRegistry: Map<String, (Character) -> Unit> = mapOf(
        "Lucky" to { ch ->
            ch.abilities.con += 1
            ch.maxHp += 1
            ch.hp += 1
        },
        "Tough" to { ch ->
            ch.maxHp += ch.level * 2
            ch.hp += ch.level * 2
        },
        "Sharpshooter" to { ch -> ch.abilities.dex += 2 },
        "War Caster" to { ch ->
            val cls = Classes.find(ch.cls)
            when (cls?.spellAbility) {
                "INT" -> ch.abilities.int += 2
                "WIS" -> ch.abilities.wis += 2
                "CHA" -> ch.abilities.cha += 2
                else -> ch.abilities.int += 2
            }
        },
        "Great Weapon Master" to { ch -> ch.abilities.str += 2 },
        "Sentinel" to { ch -> ch.ac += 1 },
        "Alert" to { ch -> ch.abilities.dex += 2 },
        "Resilient" to { ch ->
            ch.abilities.con += 2
            ch.maxHp += 2
            ch.hp += 2
        },
        "Observant" to { ch -> ch.abilities.wis += 2 },
        "Actor" to { ch -> ch.abilities.cha += 2 },
        "Tavern Brawler" to { ch -> ch.abilities.str += 2 },
        "Magic Initiate" to { ch -> ch.abilities.int += 2 },
    )

    val list: List<Feat> get() = ContentRepository.featMetas.map { meta -> meta.toFeat() }

    fun find(name: String) = list.firstOrNull { it.name.equals(name, true) }

    private fun FeatMeta.toFeat(): Feat {
        val apply = applyRegistry[name]
            ?: error("Feat registry missing apply for '$name'")
        return Feat(name, description, icon, apply)
    }
}
