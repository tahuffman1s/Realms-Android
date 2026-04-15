package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase B regression suite — tests the tokenizer + stack-based segment parser
 * indirectly through TagParser.parse(raw, turn).segments.
 *
 * Scaffolding note: parse() requires at least a minimal raw string. The
 * tokenizer and buildSegments run identically for both JSON-metadata and
 * regex-fallback paths, so we can pass a bare narrative string (no METADATA
 * block) and test the segments field directly.
 */
class TagParserTest {

    /** Convenience: parse a raw string at turn 1, return the segments list. */
    private fun segs(raw: String): List<NarrationSegmentData> =
        TagParser.parse(raw, 1).segments

    // ---- Test 1: Well-formed single tag ----
    @Test
    fun `well-formed single NARRATOR_PROSE tag produces one Prose segment`() {
        val result = segs("[NARRATOR_PROSE]Hello.[/NARRATOR_PROSE]")
        assertEquals(1, result.size)
        val seg = result[0] as NarrationSegmentData.Prose
        assertEquals("Hello.", seg.text)
    }

    // ---- Test 2: Multiple well-formed tags in sequence ----
    @Test
    fun `multiple well-formed tags in sequence produce segments in source order`() {
        val raw = "[NARRATOR_PROSE]The tavern hums.[/NARRATOR_PROSE]" +
            "[PLAYER_ACTION]You glance around.[/PLAYER_ACTION]" +
            "[PLAYER_DIALOG]Any rooms available?[/PLAYER_DIALOG]"
        val result = segs(raw)
        assertEquals(3, result.size)
        assertTrue(result[0] is NarrationSegmentData.Prose)
        assertTrue(result[1] is NarrationSegmentData.PlayerAction)
        assertTrue(result[2] is NarrationSegmentData.PlayerDialog)
        assertEquals("The tavern hums.", (result[0] as NarrationSegmentData.Prose).text)
        assertEquals("Any rooms available?", (result[2] as NarrationSegmentData.PlayerDialog).text)
    }

    // ---- Test 3: Bug #1 regression — mismatched close tag ----
    @Test
    fun `mismatched close tag does not leak tag syntax into segment text`() {
        // Real-world bug: PLAYER_ACTION opened, closed by NARRATOR_PROSE — then prose
        // content before its own close, then a correct PLAYER_ACTION block.
        val raw = "[PLAYER_ACTION]Text A.[/NARRATOR_PROSE]Stray prose content.[/NARRATOR_PROSE]\n\n" +
            "[PLAYER_ACTION]Text B.[/PLAYER_ACTION]"
        val result = segs(raw)

        // No segment should contain literal bracket-tag syntax
        for (seg in result) {
            val text = when (seg) {
                is NarrationSegmentData.Prose -> seg.text
                is NarrationSegmentData.PlayerAction -> seg.text
                is NarrationSegmentData.PlayerDialog -> seg.text
                is NarrationSegmentData.Aside -> seg.text
                is NarrationSegmentData.NpcDialog -> seg.text
                is NarrationSegmentData.NpcAction -> seg.text
            }
            assertFalse("Segment text must not contain '[': $text", text.contains('['))
            assertFalse("Segment text must not contain ']': $text", text.contains(']'))
        }

        // Check we see the three expected content pieces
        val playerActions = result.filterIsInstance<NarrationSegmentData.PlayerAction>()
        assertTrue("Should have at least one PlayerAction", playerActions.isNotEmpty())
        assertTrue("Text A should appear", playerActions.any { "Text A." in it.text })
        assertTrue("Text B should appear", playerActions.any { "Text B." in it.text })
    }

    // ---- Test 4: Stray close tag with empty stack ----
    @Test
    fun `stray close tag with empty stack is dropped silently`() {
        // Close tag comes before any open — should be silently dropped.
        // Gap text before and after should both land in gap buffer → Prose.
        val raw = "Preface text.[/PLAYER_ACTION]Followup."
        val result = segs(raw)
        // All text should end up in one or two Prose segments (gap is accumulated).
        val allText = result.joinToString("") {
            when (it) {
                is NarrationSegmentData.Prose -> it.text
                else -> ""
            }
        }
        assertTrue("Should contain 'Preface text.'", "Preface text." in allText)
        assertTrue("Should contain 'Followup.'", "Followup." in allText)
        // No tag syntax should appear
        assertFalse("No bracket syntax in text", allText.contains('['))
    }

    // ---- Test 5: Unclosed tag at EOF ----
    @Test
    fun `unclosed tag at EOF is auto-closed and produces a segment`() {
        val result = segs("[PLAYER_ACTION]Unclosed content.")
        val actions = result.filterIsInstance<NarrationSegmentData.PlayerAction>()
        assertEquals(1, actions.size)
        assertEquals("Unclosed content.", actions[0].text)
    }

    // ---- Test 6: Empty body ----
    @Test
    fun `empty tag body produces zero segments`() {
        val result = segs("[NARRATOR_PROSE][/NARRATOR_PROSE]")
        assertTrue("Empty body should produce no segments", result.isEmpty())
    }

    // ---- Test 7: NPC_DIALOG with id ----
    @Test
    fun `NPC_DIALOG tag with id produces NpcDialog segment`() {
        val result = segs("[NPC_DIALOG:vesper]Another drowned rat.[/NPC_DIALOG]")
        val dialogs = result.filterIsInstance<NarrationSegmentData.NpcDialog>()
        assertEquals(1, dialogs.size)
        assertEquals("vesper", dialogs[0].name)
        assertEquals("Another drowned rat.", dialogs[0].text)
    }

    // ---- Test 8: NPC_ACTION with speaker prepending ----
    @Test
    fun `NPC_ACTION tag with id produces NpcAction segment with speaker prepended`() {
        val result = segs("[NPC_ACTION:vesper]leans across the bar.[/NPC_ACTION]")
        val actions = result.filterIsInstance<NarrationSegmentData.NpcAction>()
        assertEquals(1, actions.size)
        assertEquals("vesper", actions[0].name)
        // formatNpcActionContent prepends the speaker name to bare verb phrases
        assertTrue(
            "Text should start with 'vesper' (speaker name): '${actions[0].text}'",
            actions[0].text.startsWith("vesper")
        )
    }

    // ---- Test 9: Opaque blocks are skipped ----
    @Test
    fun `opaque SCENE and CHOICES blocks do not leak into segments`() {
        val raw = "[SCENE:town|A market square buzzes with activity]" +
            "[NARRATOR_PROSE]The vendor shouts.[/NARRATOR_PROSE]" +
            "[CHOICES]1. Approach [PERSUASION]\n2. Leave [NONE][/CHOICES]"
        val result = segs(raw)
        // Only the NARRATOR_PROSE should produce a segment
        assertEquals(1, result.size)
        assertTrue(result[0] is NarrationSegmentData.Prose)
        val text = (result[0] as NarrationSegmentData.Prose).text
        assertFalse("SCENE content must not leak: $text", "market square" in text)
        assertFalse("CHOICES content must not leak: $text", "Approach" in text)
        assertEquals("The vendor shouts.", text)
    }

    // ---- Test 10: Gap text between tags becomes Prose ----
    @Test
    fun `gap text between tags becomes a Prose segment`() {
        val raw = "[NARRATOR_ASIDE]quip[/NARRATOR_ASIDE]orphan text[NARRATOR_PROSE]prose[/NARRATOR_PROSE]"
        val result = segs(raw)
        assertEquals(3, result.size)
        assertTrue("First should be Aside", result[0] is NarrationSegmentData.Aside)
        assertTrue("Second should be Prose (gap)", result[1] is NarrationSegmentData.Prose)
        assertTrue("Third should be Prose", result[2] is NarrationSegmentData.Prose)
        assertEquals("quip", (result[0] as NarrationSegmentData.Aside).text)
        assertEquals("orphan text", (result[1] as NarrationSegmentData.Prose).text)
        assertEquals("prose", (result[2] as NarrationSegmentData.Prose).text)
    }

    // ---- Test 11: Unknown tag name treated as text ----
    @Test
    fun `unknown tag name is treated as literal text`() {
        val raw = "[FOOBAR]content[/FOOBAR]"
        val result = segs(raw)
        // The entire thing becomes gap text → Prose
        assertTrue("Should produce at least one segment", result.isNotEmpty())
        val allText = result.joinToString("") {
            when (it) {
                is NarrationSegmentData.Prose -> it.text
                else -> ""
            }
        }
        // The brackets and tag text should appear literally
        assertTrue("Unknown tag brackets should be literal text: '$allText'",
            "FOOBAR" in allText || "content" in allText)
    }

    // ---- Test 12: Deep mismatch recovery ----
    @Test
    fun `deep mismatch recovery force-closes top of stack on wrong close tag`() {
        // [NARRATOR_PROSE] opened, then [PLAYER_ACTION] opened.
        // [/NARRATOR_PROSE] force-closes PLAYER_ACTION (top of stack), emitting
        // PlayerAction("inner"). "trailing" then goes into NARRATOR_PROSE gap body.
        // [/PLAYER_ACTION] force-closes NARRATOR_PROSE, emitting Prose("trailing").
        val raw = "[NARRATOR_PROSE][PLAYER_ACTION]inner[/NARRATOR_PROSE]trailing[/PLAYER_ACTION]"
        val result = segs(raw)
        assertEquals("Expected exactly 2 segments: $result", 2, result.size)

        // First: PlayerAction("inner") — the PLAYER_ACTION block was on top of stack
        // when [/NARRATOR_PROSE] arrived and got force-closed.
        val first = result[0]
        assertTrue("First segment should be PlayerAction, got: $first",
            first is NarrationSegmentData.PlayerAction)
        assertEquals("inner", (first as NarrationSegmentData.PlayerAction).text)

        // Second: Prose("trailing") — "trailing" accumulated in the open NARRATOR_PROSE
        // and was then force-closed by [/PLAYER_ACTION].
        val second = result[1]
        assertTrue("Second segment should be Prose, got: $second",
            second is NarrationSegmentData.Prose)
        assertEquals("trailing", (second as NarrationSegmentData.Prose).text)
    }
}
