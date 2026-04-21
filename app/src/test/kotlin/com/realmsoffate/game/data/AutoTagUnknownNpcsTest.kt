package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTagUnknownNpcsTest {
    @Test
    fun `detects two-word proper noun`() {
        val specs = AutoTagUnknownNpcs.scan(
            narration = "You meet Mira Cole at the inn.",
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 5
        )
        assertEquals(1, specs.size)
        assertEquals("Mira Cole", specs[0].name)
    }

    @Test
    fun `skips existing NPCs`() {
        val specs = AutoTagUnknownNpcs.scan(
            narration = "Mira Cole smiles.",
            existingNpcs = listOf(LogNpc(id = "m", name = "Mira Cole", metTurn = 1, lastSeenTurn = 1)),
            currentLoc = "Hightower",
            turn = 5
        )
        assertTrue(specs.isEmpty())
    }

    @Test
    fun `skips common words at start`() {
        val specs = AutoTagUnknownNpcs.scan(
            narration = "The Queen rules here.",
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 5
        )
        assertTrue(specs.none { it.name.startsWith("The ") })
    }
}
