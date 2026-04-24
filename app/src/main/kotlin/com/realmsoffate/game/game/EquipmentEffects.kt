package com.realmsoffate.game.game

import com.realmsoffate.game.data.Abilities
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.ItemEffect

object EquipmentEffects {

    fun activeEffects(ch: Character): List<ItemEffect> =
        ch.inventory.filter { it.equipped }.flatMap { it.effects }

    fun effectiveAbilities(ch: Character): Abilities {
        var str = ch.abilities.str
        var dex = ch.abilities.dex
        var con = ch.abilities.con
        var int = ch.abilities.int
        var wis = ch.abilities.wis
        var cha = ch.abilities.cha
        activeEffects(ch).forEach { e ->
            if (e is ItemEffect.AbilityBonus) {
                when (e.stat.uppercase()) {
                    "STR" -> str += e.amount
                    "DEX" -> dex += e.amount
                    "CON" -> con += e.amount
                    "INT" -> int += e.amount
                    "WIS" -> wis += e.amount
                    "CHA" -> cha += e.amount
                }
            }
        }
        return Abilities(str, dex, con, int, wis, cha)
    }

    private val HEAVY_ARMOR_TOKENS = listOf("Chain Mail", "Plate", "Ring Mail", "Splint")

    fun effectiveAc(ch: Character): Int {
        val eq = ch.inventory.filter { it.equipped }
        val armor = eq.firstOrNull { it.ac != null }
        val hasShield = eq.any { it.type.equals("shield", ignoreCase = true) }
        val effDex = effectiveAbilities(ch).dexMod
        val base = when {
            armor == null -> 10 + effDex
            HEAVY_ARMOR_TOKENS.any { armor.name.contains(it, ignoreCase = true) } -> armor.ac!!
            else -> armor.ac!! + effDex
        }
        return base + if (hasShield) 2 else 0
    }
}
