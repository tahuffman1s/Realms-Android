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

    fun effectiveMaxHp(ch: Character): Int =
        ch.maxHp + activeEffects(ch).filterIsInstance<ItemEffect.MaxHpBonus>().sumOf { it.amount }

    fun skillBonuses(ch: Character): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        activeEffects(ch).filterIsInstance<ItemEffect.SkillBonus>().forEach { e ->
            val trimmed = e.skill.trim()
            val key = if (trimmed.isEmpty()) ""
                else trimmed[0].uppercaseChar() + trimmed.substring(1).lowercase()
            out[key] = (out[key] ?: 0) + e.amount
        }
        return out
    }

    fun resistances(ch: Character): Set<String> =
        activeEffects(ch).filterIsInstance<ItemEffect.Resistance>()
            .map { it.damageType.lowercase() }.toSet()

    fun immunities(ch: Character): Set<String> =
        activeEffects(ch).filterIsInstance<ItemEffect.Immunity>()
            .map { it.damageType.lowercase() }.toSet()

    fun onHitRiders(ch: Character): List<ItemEffect.OnHit> =
        activeEffects(ch).filterIsInstance<ItemEffect.OnHit>()

    fun passiveTriggers(ch: Character): List<String> =
        activeEffects(ch).filterIsInstance<ItemEffect.PassiveTrigger>().map { it.text }

    fun promptSummary(ch: Character): String {
        val eq = ch.inventory.filter { it.equipped }
        val interesting = eq.filter { it.damage != null || it.ac != null || it.effects.isNotEmpty() }
        val res = resistances(ch)
        val imm = immunities(ch)
        if (interesting.isEmpty() && res.isEmpty() && imm.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("Equipped gear:")
        interesting.forEach { item ->
            sb.append("- ").append(item.name).append(" (").append(item.type)
            if (item.damage != null) sb.append(", ").append(item.damage)
            if (item.ac != null) sb.append(", AC ").append(item.ac)
            sb.append(")")
            val extras = mutableListOf<String>()
            item.effects.forEach { e ->
                when (e) {
                    is ItemEffect.AbilityBonus -> extras.add("${signed(e.amount)} ${e.stat.uppercase()} (applied)")
                    is ItemEffect.SkillBonus   -> extras.add("${signed(e.amount)} ${e.skill} checks")
                    is ItemEffect.Resistance   -> { /* rolled up below */ }
                    is ItemEffect.Immunity     -> { /* rolled up below */ }
                    is ItemEffect.OnHit        -> extras.add("on hit: +${e.dice} ${e.damageType}")
                    is ItemEffect.MaxHpBonus   -> extras.add("${signed(e.amount)} max HP")
                    is ItemEffect.PassiveTrigger -> extras.add("passive: ${e.text}")
                }
            }
            if (extras.isNotEmpty()) sb.append(" — ").append(extras.joinToString("; "))
            sb.append('\n')
        }
        if (res.isNotEmpty()) sb.appendLine("Resistances: ${res.sorted().joinToString(", ")}")
        if (imm.isNotEmpty()) sb.appendLine("Immunities: ${imm.sorted().joinToString(", ")}")
        return sb.toString().trimEnd()
    }

    private fun signed(n: Int): String = if (n >= 0) "+$n" else "$n"
}
