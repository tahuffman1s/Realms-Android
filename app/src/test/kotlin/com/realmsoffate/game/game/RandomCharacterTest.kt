package com.realmsoffate.game.game

import com.realmsoffate.game.data.PlayerNamePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RandomCharacterTest {

    @Test
    fun `generate produces non-blank name from pool`() {
        val r = RandomCharacter.generate(Random(1))
        assertTrue(r.name.isNotBlank())
        assertTrue("name '${r.name}' should come from pool", r.name in PlayerNamePool.NAMES)
    }

    @Test
    fun `generate picks a known race and class`() {
        val r = RandomCharacter.generate(Random(2))
        assertTrue(Races.find(r.race) != null)
        assertTrue(Classes.find(r.cls) != null)
    }

    @Test
    fun `generate uses class recommended preset for stats`() {
        val r = RandomCharacter.generate(Random(3))
        val def = Classes.find(r.cls)!!
        assertTrue(r.baseStats.contentEquals(def.recommended))
    }

    @Test
    fun `generate picks distinct primary and secondary bonus indices`() {
        repeat(20) { seed ->
            val r = RandomCharacter.generate(Random(seed.toLong()))
            assertNotEquals("seed=$seed", r.primaryBonus, r.secondaryBonus)
        }
    }

    @Test
    fun `generate fills every appearance and identity field`() {
        val r = RandomCharacter.generate(Random(4))
        assertTrue(r.skinTone.startsWith("#"))
        assertTrue(r.hairColor.startsWith("#"))
        assertTrue(r.hairStyle.isNotBlank())
        assertTrue(r.build.isNotBlank())
        assertTrue(r.gender.isNotBlank())
        assertTrue(r.ageBand.isNotBlank())
    }

    @Test
    fun `generate is non-deterministic across fresh entropy`() {
        val a = RandomCharacter.generate(Random(100))
        val b = RandomCharacter.generate(Random(200))
        val differs = a.name != b.name || a.race != b.race || a.cls != b.cls ||
            a.skinTone != b.skinTone || a.hairColor != b.hairColor ||
            a.hairStyle != b.hairStyle || a.build != b.build ||
            a.gender != b.gender || a.ageBand != b.ageBand
        assertTrue("two different seeds should not produce identical characters", differs)
    }

    @Test
    fun `generate is deterministic for a given seed`() {
        val a = RandomCharacter.generate(Random(42))
        val b = RandomCharacter.generate(Random(42))
        assertEquals(a, b)
    }
}
