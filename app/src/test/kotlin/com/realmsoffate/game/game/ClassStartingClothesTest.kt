package com.realmsoffate.game.game

import com.realmsoffate.game.data.content.ContentRepository
import com.realmsoffate.game.data.content.TestContentInit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClassStartingClothesTest {
    @Before fun init() = TestContentInit.ensureLoaded()

    @Test fun `every class has a clothes item in starting gear`() {
        val clothesType = "clothes"
        val missing = ContentRepository.classes.filter { cls ->
            cls.startingItems.none { it.type.equals(clothesType, ignoreCase = true) }
        }
        assertTrue(
            "Classes missing clothes in starting gear: ${missing.map { it.name }}",
            missing.isEmpty()
        )
    }

    @Test fun `clothes start equipped`() {
        val clothesType = "clothes"
        ContentRepository.classes.forEach { cls ->
            val clothes = cls.startingItems.firstOrNull { it.type.equals(clothesType, true) }
            requireNotNull(clothes) { "${cls.name} has no clothes" }
            assertTrue("${cls.name} clothes not equipped", clothes.equipped)
        }
    }
}
