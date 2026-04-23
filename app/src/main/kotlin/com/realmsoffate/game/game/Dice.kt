package com.realmsoffate.game.game

import kotlin.random.Random

object Dice {
    fun d20(): Int = when {
        Cheats.forceCrit -> 20
        Cheats.forceFail -> 1
        else -> Random.nextInt(1, 21)
    }

    fun d(n: Int): Int = when {
        n == 20 && Cheats.forceCrit -> 20
        n == 20 && Cheats.forceFail -> 1
        else -> Random.nextInt(1, n + 1)
    }

    fun roll(formula: String): Int {
        // Supports "NdM(+/-K)" e.g. "1d8+2", "2d6"
        val m = Regex("(\\d+)d(\\d+)([+-]\\d+)?").matchEntire(formula.replace(" ", "")) ?: return 0
        val n = m.groupValues[1].toInt()
        val size = m.groupValues[2].toInt()
        val bonus = m.groupValues[3].ifBlank { "0" }.toInt()
        var total = bonus
        repeat(n) { total += Random.nextInt(1, size + 1) }
        return total
    }
}
