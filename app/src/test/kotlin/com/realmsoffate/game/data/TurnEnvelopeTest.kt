package com.realmsoffate.game.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.Assert.*
import org.junit.Test

class TurnEnvelopeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        classDiscriminator = "kind"
        serializersModule = SerializersModule {
            polymorphic(Segment::class) {
                subclass(Segment.Prose::class)
                subclass(Segment.Aside::class)
                subclass(Segment.PlayerAction::class)
                subclass(Segment.PlayerDialog::class)
                subclass(Segment.NpcAction::class)
                subclass(Segment.NpcDialog::class)
            }
        }
    }

    @Test
    fun `minimal envelope decodes with defaults`() {
        val env = json.decodeFromString<TurnEnvelope>("""{}""")
        assertEquals("default", env.scene.type)
        assertTrue(env.segments.isEmpty())
        assertTrue(env.choices.isEmpty())
        assertEquals(0, env.metadata.damage)
    }

    @Test
    fun `polymorphic segments decode by kind`() {
        val raw = """
            {
              "segments": [
                {"kind":"prose","text":"The door opens."},
                {"kind":"aside","text":"Oh, this'll be good."},
                {"kind":"npc_dialog","name":"vesper","text":"Another rat."}
              ]
            }
        """.trimIndent()
        val env = json.decodeFromString<TurnEnvelope>(raw)
        assertEquals(3, env.segments.size)
        assertTrue(env.segments[0] is Segment.Prose)
        assertTrue(env.segments[1] is Segment.Aside)
        assertEquals("vesper", (env.segments[2] as Segment.NpcDialog).name)
    }

    @Test
    fun `unknown segment kind is rejected`() {
        val raw = """{"segments":[{"kind":"SMOKE","text":"?"}]}"""
        try {
            json.decodeFromString<TurnEnvelope>(raw)
            fail("expected decode to throw on unknown kind")
        } catch (_: Exception) { /* expected */ }
    }

    @Test
    fun `choices and metadata decode in single pass`() {
        val raw = """
            {
              "choices": [{"text":"Wait","skill":"Insight"}],
              "metadata": {"damage": 5, "gold_gained": 10}
            }
        """.trimIndent()
        val env = json.decodeFromString<TurnEnvelope>(raw)
        assertEquals(1, env.choices.size)
        assertEquals(5, env.metadata.damage)
        assertEquals(10, env.metadata.goldGained)
    }

    @Test
    fun `all six segment kinds round-trip`() {
        val raw = """
            {
              "segments": [
                {"kind":"prose","text":"p"},
                {"kind":"aside","text":"a"},
                {"kind":"player_action","text":"pa"},
                {"kind":"player_dialog","text":"pd"},
                {"kind":"npc_action","name":"v","text":"na"},
                {"kind":"npc_dialog","name":"v","text":"nd"}
              ]
            }
        """.trimIndent()
        val env = json.decodeFromString<TurnEnvelope>(raw)
        assertEquals(6, env.segments.size)
        assertTrue(env.segments[0] is Segment.Prose)
        assertTrue(env.segments[1] is Segment.Aside)
        assertTrue(env.segments[2] is Segment.PlayerAction)
        assertTrue(env.segments[3] is Segment.PlayerDialog)
        assertTrue(env.segments[4] is Segment.NpcAction)
        assertTrue(env.segments[5] is Segment.NpcDialog)
    }
}
