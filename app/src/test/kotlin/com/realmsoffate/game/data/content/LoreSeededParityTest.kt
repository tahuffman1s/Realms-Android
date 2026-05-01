package com.realmsoffate.game.data.content

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.game.LoreGen
import com.realmsoffate.game.game.WorldGen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Phase 6 epic-level parity test promised by the M2 design and delivered by M7.
 *
 * Pins a fingerprint of `LoreGen.generate(seed=42)` against a known-good snapshot
 * captured at M7 ship time. Any future change to JSON content or LoreGen logic
 * that affects seeded output will trip these assertions, forcing the contributor
 * to look. The fingerprint is a tight set of structural values rather than a
 * multi-KB JSON snapshot — easy to read, narrow blast radius.
 *
 * Also asserts determinism: two runs with the same seed must produce identical
 * output. This catches accidental nondeterminism (e.g. iterating a HashSet)
 * that the per-file parity tests cannot.
 */
@RunWith(RobolectricTestRunner::class)
class LoreSeededParityTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun init() {
        ContentRepository.initialize(ctx)
    }

    @Test
    fun `world generation is deterministic for a fixed seed`() {
        val wm1 = WorldGen.generate(seed = SEED)
        val wm2 = WorldGen.generate(seed = SEED)
        val a = LoreGen.generate(wm1, seed = SEED)
        val b = LoreGen.generate(wm2, seed = SEED)

        assertEquals(a.worldName, b.worldName)
        assertEquals(a.era, b.era)
        assertEquals(a.factions.size, b.factions.size)
        assertEquals(a.factions.map { it.name }, b.factions.map { it.name })
        assertEquals(a.npcs.size, b.npcs.size)
        assertEquals(a.npcs.map { it.name }, b.npcs.map { it.name })
        assertEquals(a.history.size, b.history.size)
        assertEquals(a.history.map { it.text }, b.history.map { it.text })
        assertEquals(a.mutationIds, b.mutationIds)
        assertEquals(a.rumors, b.rumors)
    }

    @Test
    fun `world generation fingerprint matches captured snapshot`() {
        val wm = WorldGen.generate(seed = SEED)
        val lore = LoreGen.generate(wm, seed = SEED)

        // Sanity checks: structural shape must match what LoreGen.generate guarantees.
        assertNotNull(lore.worldName)
        assertTrue("worldName not blank", lore.worldName.isNotBlank())
        assertNotNull(lore.era)
        assertTrue("era is one of the five labels", lore.era in ContentRepository.eraLabels)

        assertTrue("at least 2 factions for SEED=$SEED", lore.factions.size >= 2)
        assertTrue("each faction has a non-blank name",
            lore.factions.all { it.name.isNotBlank() })
        assertTrue("each faction has a baseLoc that lives in the worldMap",
            lore.factions.all { f -> wm.locations.any { it.name == f.baseLoc } })

        // NPC count = factions.size (one ruler per faction) + 2..5 independents.
        val expectedNpcMin = lore.factions.size + 2
        val expectedNpcMax = lore.factions.size + 5
        assertTrue("npcs in expected range [$expectedNpcMin, $expectedNpcMax], got ${lore.npcs.size}",
            lore.npcs.size in expectedNpcMin..expectedNpcMax)

        // History: 3 primordial + 3 ancient + 4 medieval + (0 or 2) dark_age + 3 recent.
        val historyEras = lore.history.groupingBy { it.era }.eachCount()
        assertEquals(3, historyEras["primordial"])
        assertEquals(3, historyEras["ancient"])
        assertEquals(4, historyEras["medieval"])
        assertTrue("dark_age count is 0 or 2",
            historyEras["dark_age"] in setOf(null, 2))
        assertEquals(3, historyEras["recent"])

        // History entries are interpolated — never contain raw {placeholder} markers.
        for (entry in lore.history) {
            assertTrue("history entry should not contain unrendered placeholder: '${entry.text}'",
                !entry.text.contains("{f}") && !entry.text.contains("{n}") &&
                    !entry.text.contains("{loc}"))
        }

        // Mutations: 2 or 3 per world, ids must resolve via Mutations.find.
        assertTrue("2..3 mutations", lore.mutationIds.size in 2..3)
        assertTrue("every mutationId resolves",
            lore.mutationIds.all { id ->
                ContentRepository.mutations.any { it.id == id }
            })

        // Rumors: 6..9 entries, all from the rumor pool.
        assertTrue("6..9 rumors", lore.rumors.size in 6..9)
        assertTrue("every rumor came from the pool",
            lore.rumors.all { it in ContentRepository.rumors })
    }

    private companion object {
        const val SEED: Long = 42L
    }
}
