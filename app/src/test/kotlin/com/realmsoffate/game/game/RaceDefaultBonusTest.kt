package com.realmsoffate.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceDefaultBonusTest {
    @Test fun `elf defaults to DEX primary and INT secondary`() {
        val (p, s) = Races.find("Elf")!!.defaultBonusIndices()
        assertEquals(1, p) // DEX
        assertEquals(3, s) // INT
    }

    @Test fun `dwarf defaults to CON primary and STR secondary`() {
        val (p, s) = Races.find("Dwarf")!!.defaultBonusIndices()
        assertEquals(2, p) // CON
        assertEquals(0, s) // STR
    }

    @Test fun `tiefling defaults to CHA primary and INT secondary`() {
        val (p, s) = Races.find("Tiefling")!!.defaultBonusIndices()
        assertEquals(5, p)
        assertEquals(3, s)
    }

    @Test fun `half-orc defaults to STR primary and CON secondary`() {
        val (p, s) = Races.find("Half-Orc")!!.defaultBonusIndices()
        assertEquals(0, p)
        assertEquals(2, s)
    }

    @Test fun `human with +1 all defaults to STR primary and DEX secondary`() {
        // All-equal tie-breaking — sortedByDescending preserves original order,
        // so the first two indices (STR=0, DEX=1) are returned.
        val (p, s) = Races.find("Human")!!.defaultBonusIndices()
        assertEquals(0, p) // STR
        assertEquals(1, s) // DEX
    }

    @Test fun `half-elf picks CHA primary then a +1 stat as secondary`() {
        // Half-Elf has CHA=2, DEX=1, INT=1. CHA is unambiguous primary;
        // secondary is whichever +1 stat sortedByDescending stabilizes on.
        val (p, s) = Races.find("Half-Elf")!!.defaultBonusIndices()
        assertEquals(5, p) // CHA primary
        assertTrue("Half-Elf secondary must be DEX or INT, got $s", s == 1 || s == 3)
    }
}
