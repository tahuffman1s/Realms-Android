package com.realmsoffate.game.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnvelopeParserTest {
    @Test
    fun `full envelope produces populated ParsedReply`() {
        val raw = """
            {
              "scene": {"type":"tavern","desc":"A dim tavern."},
              "segments": [
                {"kind":"prose","text":"Rain hammers the windows."},
                {"kind":"aside","text":"Oh, this'll be good."},
                {"kind":"npc_dialog","name":"vesper","text":"Another rat."}
              ],
              "choices": [
                {"text":"Order a drink","skill":"Persuasion"},
                {"text":"Watch the door","skill":"Perception"},
                {"text":"Leave","skill":"Stealth"},
                {"text":"Start a fight","skill":"Intimidation"}
              ],
              "metadata": {
                "damage": 3,
                "gold_gained": 5,
                "npcs_met": [
                  {"id":"vesper","name":"Vesper","race":"human","role":"innkeeper"}
                ]
              }
            }
        """.trimIndent()
        val p = EnvelopeParser.parse(raw, currentTurn = 7)
        assertEquals(ParseSource.JSON, p.source)
        assertEquals("tavern", p.scene)
        assertEquals(3, p.segments.size)
        assertEquals(4, p.choices.size)
        assertEquals(3, p.damage)
        assertEquals(5, p.goldGained)
        assertEquals(1, p.npcsMet.size)
        assertEquals("vesper", p.npcsMet[0].id)
        assertEquals(7, p.npcsMet[0].metTurn)
    }

    @Test
    fun `malformed json returns INVALID with zero effects`() {
        val p = EnvelopeParser.parse("not json", currentTurn = 1)
        assertEquals(ParseSource.INVALID, p.source)
        assertEquals(0, p.damage)
        assertEquals(0, p.goldGained)
        assertTrue(p.segments.isEmpty())
        assertTrue(p.choices.isEmpty())
    }

    @Test
    fun `unknown metadata key is ignored`() {
        val raw = """{"metadata":{"damage":2,"futureField":42}}"""
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals(ParseSource.JSON, p.source)
        assertEquals(2, p.damage)
    }

    @Test
    fun `segments preserve order`() {
        val raw = """
            {"segments":[
              {"kind":"aside","text":"1"},
              {"kind":"prose","text":"2"},
              {"kind":"aside","text":"3"}
            ]}
        """.trimIndent()
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals(listOf("1","2","3"), p.segments.map {
            when (it) {
                is NarrationSegmentData.Prose -> it.text
                is NarrationSegmentData.Aside -> it.text
                else -> ""
            }
        })
    }

    @Test
    fun `blank choices are filtered out`() {
        val raw = """
            {"choices":[
              {"text":"Wait","skill":"Insight"},
              {"text":"","skill":"Stealth"},
              {"text":"  ","skill":"Persuasion"},
              {"text":"Fight","skill":"Athletics"}
            ]}
        """.trimIndent()
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals("blank-text choices dropped", 2, p.choices.size)
        assertEquals("Wait", p.choices[0].text)
        assertEquals("Fight", p.choices[1].text)
    }

    @Test
    fun `npc_action and npc_dialog carry name through to ParsedReply`() {
        val raw = """
            {"segments":[
              {"kind":"npc_action","name":"vesper","text":"leans in."},
              {"kind":"npc_dialog","name":"vesper","text":"Another rat."}
            ]}
        """.trimIndent()
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals(1, p.npcActions.size)
        assertEquals("vesper", p.npcActions[0].first)
        assertEquals("leans in.", p.npcActions[0].second)
        assertEquals(1, p.npcDialogs.size)
        assertEquals("vesper", p.npcDialogs[0].first)
    }

    @Test
    fun `partial check object decodes and is filtered to null`() {
        val raw = """{"metadata":{"check":{"skill":"Stealth","ability":"DEX","dc":10}}}"""
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals(ParseSource.JSON, p.source)
        // Check was partial; parser nulls it out so no dice UI fires.
        assertTrue("partial check should be dropped", p.checks.isEmpty())
    }

    @Test
    fun `full check object decodes intact`() {
        val raw = """{"metadata":{"check":{"skill":"Stealth","ability":"DEX","dc":10,"passed":false,"total":6}}}"""
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals(ParseSource.JSON, p.source)
        assertEquals(1, p.checks.size)
        assertEquals("Stealth", p.checks[0].skill)
        assertEquals(6, p.checks[0].total)
    }

    @Test
    fun `npc_met entry missing both id and name is dropped`() {
        val raw = """{"metadata":{"npcs_met":[{"race":"elf"},{"id":"vesper","name":"Vesper"}]}}"""
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals(1, p.npcsMet.size)
        assertEquals("vesper", p.npcsMet[0].id)
    }

    @Test
    fun `blank-name item is dropped from items_gained`() {
        val raw = """{"metadata":{"items_gained":[{"name":"","desc":"mystery"},{"name":"Sword","desc":"sharp"}]}}"""
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals(1, p.itemsGained.size)
        assertEquals("Sword", p.itemsGained[0].name)
    }

    @Test
    fun `quest_start missing title is dropped`() {
        val raw = """{"metadata":{"quest_starts":[{"desc":"No title"},{"title":"Real Quest","desc":"present"}]}}"""
        val p = EnvelopeParser.parse(raw, 1)
        assertEquals(1, p.questStarts.size)
        assertEquals("Real Quest", p.questStarts[0].title)
    }
}
