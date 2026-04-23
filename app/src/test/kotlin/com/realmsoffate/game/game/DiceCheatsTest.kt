package com.realmsoffate.game.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceCheatsTest {
    @After fun tearDown() { Cheats.reset() }

    @Test fun `forceCrit makes d20 always return 20`() {
        Cheats.forceCrit = true
        repeat(100) { assertEquals(20, Dice.d20()) }
    }

    @Test fun `forceFail makes d20 always return 1`() {
        Cheats.forceFail = true
        repeat(100) { assertEquals(1, Dice.d20()) }
    }

    @Test fun `forceCrit also overrides d(20)`() {
        Cheats.forceCrit = true
        repeat(50) { assertEquals(20, Dice.d(20)) }
    }

    @Test fun `forceFail also overrides d(20)`() {
        Cheats.forceFail = true
        repeat(50) { assertEquals(1, Dice.d(20)) }
    }

    @Test fun `no flags - d20 stays random in range`() {
        val results = (1..200).map { Dice.d20() }
        results.forEach { assertTrue(it in 1..20) }
        assertTrue("should see more than one outcome", results.toSet().size > 1)
    }

    @Test fun `forceCrit does NOT affect non-d20 sizes`() {
        Cheats.forceCrit = true
        val d6s = (1..200).map { Dice.d(6) }
        d6s.forEach { assertTrue(it in 1..6) }
        assertTrue(d6s.toSet().size > 1)
    }
}
