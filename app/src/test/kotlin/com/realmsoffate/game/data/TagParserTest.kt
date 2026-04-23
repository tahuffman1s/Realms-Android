package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Thin smoke-tests for the TagParser shim.
 *
 * TagParser now delegates entirely to EnvelopeParser. Comprehensive behaviour
 * coverage lives in EnvelopeParserTest. These tests prove the shim wires up
 * correctly and that ParseSource.LEGACY_TAGS no longer exists.
 */
@RunWith(RobolectricTestRunner::class)
class TagParserTest {

    @Test
    fun `delegates envelope to EnvelopeParser — scene and source`() {
        val raw = """{"scene":{"type":"tavern","desc":"A dim tavern."}}"""
        val p = TagParser.parse(raw, currentTurn = 1)
        assertEquals(ParseSource.JSON, p.source)
        assertEquals("tavern", p.scene)
        assertEquals("A dim tavern.", p.sceneDesc)
    }

    @Test
    fun `invalid input yields INVALID source and zero effects`() {
        val p = TagParser.parse("not json", 1)
        assertEquals(ParseSource.INVALID, p.source)
        assertEquals(0, p.damage)
        assertEquals(0, p.goldGained)
        assertTrue(p.segments.isEmpty())
    }

    @Test
    fun `segments are populated from envelope`() {
        val raw = """
            {"segments":[
              {"kind":"prose","text":"Rain hammers the windows."},
              {"kind":"npc_dialog","name":"vesper","text":"Another rat."}
            ]}
        """.trimIndent()
        val p = TagParser.parse(raw, currentTurn = 3)
        assertEquals(2, p.segments.size)
        assertTrue(p.segments[0] is NarrationSegmentData.Prose)
        assertTrue(p.segments[1] is NarrationSegmentData.NpcDialog)
    }

    @Test
    fun `metadata fields flow through from envelope`() {
        val raw = """
            {"metadata":{"damage":5,"xp":10,"gold_gained":3}}
        """.trimIndent()
        val p = TagParser.parse(raw, currentTurn = 1)
        assertEquals(ParseSource.JSON, p.source)
        assertEquals(5, p.damage)
        assertEquals(10, p.xp)
        assertEquals(3, p.goldGained)
    }

    @Test
    fun `choices populated from envelope`() {
        val raw = """
            {"choices":[
              {"text":"Wait","skill":"Insight"},
              {"text":"Fight","skill":"Athletics"}
            ]}
        """.trimIndent()
        val p = TagParser.parse(raw, currentTurn = 1)
        assertEquals(2, p.choices.size)
        assertEquals("Wait", p.choices[0].text)
        assertEquals("Fight", p.choices[1].text)
    }
}
