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
}
