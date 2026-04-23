# Tag System Overhaul — JSON Envelope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the dual-path (bracket-tag + JSON metadata) parser with a single strict-JSON envelope, delete all fallback paths, centralize tag definitions in sealed/@Serializable types, and enforce the schema client-side with DeepSeek's `response_format: json_object`.

**Architecture:** DeepSeek emits **one** JSON object per turn: `{scene, segments[], choices[], metadata{}}`. `segments` is an ordered polymorphic list (prose / aside / npc_dialog / etc.). No bracket tags, no tokenizer, no regex fallback. kotlinx.serialization polymorphic decoding validates shape; `response_format: {"type": "json_object"}` guarantees parseable JSON. The existing `ParsedReply` stays as the VM/reducer facade so downstream consumers don't churn.

**Tech Stack:** Kotlin 1.9+, kotlinx.serialization polymorphic sealed classes, OkHttp, DeepSeek Chat Completions API, Robolectric+JUnit for tests.

**Addresses all 8 improvements from the review:**
1. ✅ Retire REGEX_FALLBACK — envelope is required, missing/malformed = zero effects + telemetry log.
2. ✅ SCENE and CHOICES move into the envelope.
3. ✅ `response_format: json_object` on the DeepSeek call.
4. ✅ Delete `stripTagFragments`, `dialoguePattern`, `dialogueFallback`, prose-number regexes.
5. ✅ Close-tag recovery — moot (no bracket tags).
6. ✅ Vocabulary normalized to one JSON schema.
7. ✅ Tag definitions centralized as sealed-class registry (Segment + TurnEnvelope).
8. ✅ Fields schema-constrained via kotlinx.serialization enums / strict decoding.

---

## Phased structure & go/no-go

- **Phase 1 — Prep cleanups** (no behavior change, all reversible). Deletes dead code and tightens the existing code paths. Safe to ship alone.
- **Phase 2 — Spike & decision gate.** One device turn on the real DeepSeek API using a prototype envelope prompt. Evaluate voice/prose quality. If quality is unacceptable, fork to the Hybrid fallback plan (documented inline) instead of Phase 3.
- **Phase 3 — Envelope migration** (prompts, parser, request, deletion of old path). Only if spike passes.
- **Phase 4 — Deletion & telemetry.**

If Phase 2 gate fails, Phase 3 becomes "Hybrid fork": keep bracket-tag narrative, but still strict-validate METADATA JSON against the new schema with one retry on validation failure. Items #3 and #8 are still delivered in partial form; #5 (matching-aware recovery) gets implemented instead of being subsumed.

---

## File Structure

### Files created
- `app/src/main/kotlin/com/realmsoffate/game/data/TurnEnvelope.kt` — new @Serializable sealed types for the envelope and segments. Holds every tag definition.
- `app/src/main/kotlin/com/realmsoffate/game/data/EnvelopeParser.kt` — decodes envelope JSON → `ParsedReply`. Replaces most of `TagParser.kt`.
- `app/src/test/kotlin/com/realmsoffate/game/data/TurnEnvelopeTest.kt` — schema decoding + edge cases.
- `app/src/test/kotlin/com/realmsoffate/game/data/EnvelopeParserTest.kt` — envelope → ParsedReply mapping tests.

### Files modified
- `app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt` — stripped to a thin shim that calls `EnvelopeParser` + returns `ParsedReply`. Bracket-tag paths deleted. `ParseSource` collapses to `{JSON, INVALID}`.
- `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` — `TurnMetadata` spec classes move into `TurnEnvelope.kt` (via type-alias or direct relocation).
- `app/src/main/kotlin/com/realmsoffate/game/data/NarrativeTokens.kt` — **DELETED** after Phase 3.
- `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — `SYS`, `DS_PREFIX`, `PER_TURN_REMINDER` rewritten. One envelope schema, one worked example, voice guidance preserved.
- `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt` — add `response_format: {"type":"json_object"}` to `callDeepSeek`.
- `app/src/test/kotlin/com/realmsoffate/game/data/TagParserTest.kt` — retargeted at envelope JSON inputs; legacy-tag tests deleted.
- `app/src/test/kotlin/com/realmsoffate/game/ParsedReplyBuilder.kt` — drop `ParseSource` parameter (or update enum values).

### Files deleted
- `app/src/main/kotlin/com/realmsoffate/game/data/NarrativeTokens.kt` (after Phase 3).

### Files unchanged (by design)
- All reducers (`WorldReducer`, `CharacterReducer`, `QuestAndPartyReducer`, `NpcLogReducer`) — they consume `ParsedReply`, which keeps its public shape.
- UI layer (`NarrationBlock.kt`, `ChatFeed.kt`) — consumes `parsed.segments` the same way.

---

# Phase 1 — Prep Cleanups

### Task 1: Delete `stripTagFragments` dead code

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt:188-196, :162, :231-236, :257-270, :304-305`

Background: the docstring at `:183-187` already says this function "becomes a no-op and can be deleted" once Phase B (tokenizer) lands. Phase B *is* live (`tokenizeNarrative`/`buildSegments`), but the Phase-A hotfix still runs on every clean function. This task removes it.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/kotlin/com/realmsoffate/game/data/TagParserTest.kt`:

```kotlin
@Test
fun `tokenizer handles mismatched close tag without needing stripTagFragments`() {
    // Input the Phase-A hotfix was designed to fix: NPC_ACTION opens, NARRATOR_PROSE closes.
    val raw = """
        [NPC_ACTION:vesper]leans in.[/NARRATOR_PROSE]
        [NARRATOR_PROSE]The room holds its breath.[/NARRATOR_PROSE]
        [METADATA]{}[/METADATA]
        [CHOICES]
        1. Stay. [Insight]
        2. Leave. [Stealth]
        3. Speak. [Persuasion]
        4. Fight. [Athletics]
        [/CHOICES]
    """.trimIndent()
    val parsed = TagParser.parse(raw, currentTurn = 1)
    // No bracketed tag syntax should leak into rendered segment text.
    parsed.segments.forEach { seg ->
        val text = when (seg) {
            is NarrationSegmentData.Prose -> seg.text
            is NarrationSegmentData.Aside -> seg.text
            is NarrationSegmentData.NpcAction -> seg.text
            is NarrationSegmentData.NpcDialog -> seg.text
            is NarrationSegmentData.PlayerAction -> seg.text
            is NarrationSegmentData.PlayerDialog -> seg.text
        }
        assertFalse("Segment should not contain raw tag syntax: '$text'",
            text.contains(Regex("""\[/?(NARRATOR_PROSE|NPC_ACTION)""")))
    }
}
```

- [ ] **Step 2: Run test to verify it currently passes (baseline)**

Run: `gradle test --tests "*TagParserTest.tokenizer handles mismatched*"`
Expected: PASS (hotfix is doing its job — we're about to prove the tokenizer covers it alone).

- [ ] **Step 3: Delete the hotfix function and its call sites**

In `TagParser.kt`, delete the `private fun stripTagFragments` declaration at `:188-196`. Then at its four call sites, remove the wrapper:

```kotlin
// Before (line :162):
var t = stripTagFragments(raw).trim()
// After:
var t = raw.trim()

// Before (line :231):
val t = stripTagFragments(raw).trim()
// After:
val t = raw.trim()

// Before (line :258):
val sanitized = stripTagFragments(raw)
// After:
val sanitized = raw

// Before (line :305):
private fun cleanAsideContent(raw: String): String = stripWrappingEmphasis(stripTagFragments(raw))
// After:
private fun cleanAsideContent(raw: String): String = stripWrappingEmphasis(raw)
```

- [ ] **Step 4: Run the test again and the full TagParser suite**

Run: `gradle test --tests "*TagParserTest*"`
Expected: all PASS. If any previously-passing test now fails, the tokenizer isn't covering some case — revert and diagnose before proceeding.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt app/src/test/kotlin/com/realmsoffate/game/data/TagParserTest.kt
git commit -m "refactor(parser): delete Phase-A stripTagFragments hotfix

Phase-B tokenizer already handles mismatched tags without needing
the post-pass fragment stripper. Comment at the top of the function
flagged it for deletion once Phase-B landed."
```

---

### Task 2: Delete dialogue markdown scrapers

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt:584-594, :990-1008`

`dialoguePattern` and `dialogueFallback` regex over markdown `**Name:** > "quote"` — pre-dates `[NPC_DIALOG:id]` and now double-captures. Every new turn already emits `[NPC_DIALOG:id]`, so these scrapers only fire on ancient save logs.

- [ ] **Step 1: Write a test asserting no markdown scraping on modern input**

```kotlin
@Test
fun `no dialogue regex scraping on NPC_DIALOG tag input`() {
    val raw = """
        [NPC_DIALOG:vesper]Another drowned rat. Wonderful.[/NPC_DIALOG]
        [METADATA]{}[/METADATA]
    """.trimIndent()
    val parsed = TagParser.parse(raw, currentTurn = 1)
    assertEquals(1, parsed.npcDialogs.size)
    assertEquals("vesper", parsed.npcDialogs[0].first)
    // dialogues map is the legacy markdown-scraper output — should stay empty.
    assertTrue("dialogues map should not double-capture from tagged input",
        parsed.dialogues.isEmpty())
}
```

- [ ] **Step 2: Run test to see current (failing) behavior**

Run: `gradle test --tests "*TagParserTest.no dialogue regex scraping*"`
Expected: may FAIL if the scraper currently double-captures a markdown rendering of the tag; PASS otherwise. Either way, proceed.

- [ ] **Step 3: Delete `dialoguePattern`, `dialogueFallback`, and the dialogues map extraction**

In `TagParser.kt`:
- Delete the declaration of `dialoguePattern` at `:584-588`.
- Delete the declaration of `dialogueFallback` at `:590-594`.
- Delete the `val dialogues = mutableMapOf<...>()` and the two `findAll().forEach` blocks at `:990-1008`.
- In the `ParsedReply(...)` constructor call at `:1014-1039`, remove the `dialogues = dialogues,` argument.
- In `ParsedReply` (declaration at `:81`), remove the `dialogues` field entirely.

Then scan for any consumers of `parsed.dialogues`:

Run: `grep -rn "parsed\.dialogues\|\.dialogues\b" app/src/main/kotlin app/src/test`

If any exist (reducers, tests), migrate them to read from `npcDialogs` instead — it carries the same information, keyed by NPC ref.

- [ ] **Step 4: Run the test suite**

Run: `gradle test --tests "*TagParserTest*" --tests "*NpcLogReducer*"`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(parser): drop dialogue markdown scrapers

NPC_DIALOG tags have fully replaced the **Name:** > \"quote\"
markdown pattern; the legacy scrapers only double-captured."
```

---

### Task 3: Delete prose-number fallback regexes

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt:557-576, :953-987`

`proseDamage`, `proseHeal`, `proseGoldGain`, `proseGoldLost`, `proseXp` match numbers in arbitrary sentences. When a METADATA JSON block is absent, they produce wrong numbers for sentences like "ten years ago" or "the tavern was 20 steps away." They only run on the regex-fallback path — post-Phase-3 that path is gone, but even now they're a liability.

- [ ] **Step 1: Write a test showing the false positive**

```kotlin
@Test
fun `no prose-number fallback captures scene-setting numbers`() {
    // Legacy path: no METADATA block, so the fallback regexes would otherwise fire.
    val raw = """
        [SCENE:tavern|A dim tavern.]
        She had been waiting 20 years. The door was 3 paces away.
        [CHOICES]
        1. Wait. [Insight]
        2. Knock. [Persuasion]
        3. Walk away. [Stealth]
        4. Shout. [Intimidation]
        [/CHOICES]
    """.trimIndent()
    val parsed = TagParser.parse(raw, currentTurn = 1)
    assertEquals("no damage from scene-setting number", 0, parsed.damage)
    assertEquals("no gold from scene-setting number", 0, parsed.goldGained)
}
```

- [ ] **Step 2: Run test to verify it may fail pre-fix**

Run: `gradle test --tests "*TagParserTest.no prose-number fallback*"`
Expected: likely FAIL (fallback grabs `20` as damage or gold depending on surrounding verb).

- [ ] **Step 3: Delete the prose regex declarations and the fallback block**

In `TagParser.kt`:
- Delete `proseDamage`, `proseHeal`, `proseGoldGain`, `proseGoldLost`, `proseXp` at `:557-576`.
- Delete the entire `if (metadata == null) { ... }` block at `:956-987`.

- [ ] **Step 4: Run tests**

Run: `gradle test --tests "*TagParserTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt app/src/test/kotlin/com/realmsoffate/game/data/TagParserTest.kt
git commit -m "refactor(parser): drop prose-number fallback regexes

Brittle — matched any digit near a combat/wealth verb. With the
envelope migration coming, the legacy path dies entirely; this
removes the worst footgun preemptively."
```

---

# Phase 2 — Envelope Spike (Go/No-Go Gate)

> **SKIPPED 2026-04-23 by user decision.** Voice-quality risk accepted; Task 11 (device verification) is now the sole gate. If prose quality degrades there, the plan rolls back and forks to the Hybrid appendix.

### Task 4: Prototype envelope prompt and run one real DeepSeek turn

**Files:**
- Create (temporary): `scripts/spike_envelope.sh` — shell script that POSTs a single request.

- [ ] **Step 1: Write the spike script**

```bash
#!/usr/bin/env bash
# Spike: does DeepSeek produce acceptable Narrator voice inside a JSON envelope?
# Usage: DEEPSEEK_API_KEY=... ./scripts/spike_envelope.sh
set -euo pipefail
: "${DEEPSEEK_API_KEY:?set DEEPSEEK_API_KEY}"

read -r -d '' SYSTEM <<'EOF' || true
You ARE the Narrator from Baldur's Gate 3 — sardonic, omniscient, darkly amused.
Return EXACTLY one JSON object of the form:
{
  "scene": {"type": "tavern|forest|battle|...", "desc": "short evocative line"},
  "segments": [
    {"kind": "prose", "text": "world description, no dialogue, no actions"},
    {"kind": "aside", "text": "narrator snark, 1-2 sentences"},
    {"kind": "player_action", "text": "what the player physically does"},
    {"kind": "player_dialog", "text": "what the player says"},
    {"kind": "npc_action", "name": "vesper", "text": "leans across the bar"},
    {"kind": "npc_dialog", "name": "vesper", "text": "Another drowned rat."}
  ],
  "choices": [
    {"text": "Short description", "skill": "Insight"},
    ...4 total
  ],
  "metadata": {
    "damage": 0, "heal": 0, "xp": 0, "gold_gained": 0, "gold_lost": 0,
    "npcs_met": [], "quest_starts": [], "check": null
  }
}
RULES:
- Return only the JSON, no markdown, no prose outside.
- Keep the Narrator voice ALIVE inside segment text.
- At least one prose, two asides, one npc_dialog.
EOF

curl -sS https://api.deepseek.com/v1/chat/completions \
  -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -H "Content-Type: application/json" \
  -d "$(jq -n \
    --arg sys "$SYSTEM" \
    '{
      model: "deepseek-chat",
      max_tokens: 1800,
      temperature: 1.0,
      top_p: 0.95,
      response_format: {type: "json_object"},
      messages: [
        {role: "system", content: $sys},
        {role: "user", content: "I push open the tavern door and step inside."}
      ]
    }')" | jq '.choices[0].message.content' -r | jq .
```

- [ ] **Step 2: Run the spike**

```bash
chmod +x scripts/spike_envelope.sh
DEEPSEEK_API_KEY=sk-xxxx ./scripts/spike_envelope.sh | tee /tmp/spike.json
```

- [ ] **Step 3: Evaluate — YOU decide**

Read the output. Decision criteria:

| Check | Pass if |
|-------|---------|
| Valid JSON, parses cleanly with `jq` | Yes |
| `scene.desc` has a vivid, evocative line | Not mechanical, not formulaic |
| At least 2 asides with snark / personality | Narrator voice intact |
| `prose` segments are 2-5 sentences, not 1 word | Prose quality preserved |
| `npc_dialog.text` has voice (dialect, tics), not generic | Character voice intact |
| No obvious "AI mode-collapsed" symptoms (repetitive, flat) | Creative output survives |

If at least 5/6 pass: continue to Phase 3.

If 3 or fewer pass: **stop and fork to the Hybrid plan** in the appendix at the bottom of this file.

- [ ] **Step 4: Record the verdict in the plan**

Edit this task, replace this step with one of:
- `✅ GATE PASS — sample saved to /tmp/spike.json (or commit a redacted snippet). Proceed to Phase 3.`
- `❌ GATE FAIL — voice degraded. Switching to Hybrid plan (see appendix).`

- [ ] **Step 5: Delete the spike script**

```bash
git rm scripts/spike_envelope.sh
git commit -m "chore: remove envelope spike script (decision recorded in plan)"
```

---

# Phase 3 — Envelope Migration (ONLY IF PHASE 2 PASSED)

### Task 5: Define the envelope types

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/TurnEnvelope.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.realmsoffate.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The single authoritative per-turn payload format. The AI returns exactly one
 * JSON object of this shape; EnvelopeParser decodes it into ParsedReply.
 *
 * IMPORTANT: every new per-turn datum goes HERE, not into a new bracket tag.
 * The envelope is the tag registry. One place to edit, one schema to maintain.
 */
@Serializable
data class TurnEnvelope(
    val scene: SceneInfo = SceneInfo(),
    val segments: List<Segment> = emptyList(),
    val choices: List<ChoiceSpec> = emptyList(),
    val metadata: TurnMetadata = TurnMetadata()
)

@Serializable
data class SceneInfo(
    val type: String = "default",
    val desc: String = ""
)

@Serializable
data class ChoiceSpec(
    val text: String,
    val skill: String
)

/**
 * Ordered narration segments. Polymorphic on "kind". Rendered in order by the UI.
 * Every segment type this game supports is declared below — adding a new one means
 * adding a new subclass here, nowhere else.
 */
@Serializable
sealed class Segment {
    @Serializable @SerialName("prose")
    data class Prose(val text: String) : Segment()

    @Serializable @SerialName("aside")
    data class Aside(val text: String) : Segment()

    @Serializable @SerialName("player_action")
    data class PlayerAction(val text: String) : Segment()

    @Serializable @SerialName("player_dialog")
    data class PlayerDialog(val text: String) : Segment()

    @Serializable @SerialName("npc_action")
    data class NpcAction(val name: String, val text: String) : Segment()

    @Serializable @SerialName("npc_dialog")
    data class NpcDialog(val name: String, val text: String) : Segment()
}
```

- [ ] **Step 2: Write a decoding test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/TurnEnvelopeTest.kt`:

```kotlin
package com.realmsoffate.game.data

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class TurnEnvelopeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        classDiscriminator = "kind"
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
    fun `unknown segment kind is rejected (serializer throws)`() {
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
}
```

- [ ] **Step 3: Run**

Run: `gradle test --tests "*TurnEnvelopeTest*"`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/TurnEnvelope.kt app/src/test/kotlin/com/realmsoffate/game/data/TurnEnvelopeTest.kt
git commit -m "feat(parser): introduce TurnEnvelope — single per-turn JSON shape

Sealed Segment hierarchy + scene/choices/metadata fields.
This is the new tag registry — every tag-like datum is a case in
Segment or a field in TurnMetadata; bracket tags go away next."
```

---

### Task 6: Build the envelope parser

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/EnvelopeParser.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/EnvelopeParserTest.kt`

- [ ] **Step 1: Write the parser**

```kotlin
package com.realmsoffate.game.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Decodes a DeepSeek turn response (a single JSON envelope) into ParsedReply.
 * This is the ONLY parse path. On decode failure, returns an empty ParsedReply
 * with ParseSource.INVALID so the reducer layer applies zero mechanical effects.
 */
object EnvelopeParser {
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

    fun parse(raw: String, currentTurn: Int): ParsedReply {
        val envelope = runCatching { json.decodeFromString<TurnEnvelope>(raw.trim()) }
            .getOrElse { return invalidReply(it.message.orEmpty()) }

        val segments: List<NarrationSegmentData> = envelope.segments.map { it.toSegmentData() }
        return ParsedReply(
            scene = envelope.scene.type,
            sceneDesc = envelope.scene.desc,
            narration = "",  // envelope puts all narration in segments; legacy field kept empty
            choices = envelope.choices.mapIndexed { i, c -> Choice(n = i + 1, text = c.text, skill = c.skill) },
            damage = envelope.metadata.damage,
            heal = envelope.metadata.heal,
            xp = envelope.metadata.xp,
            goldGained = envelope.metadata.goldGained,
            goldLost = envelope.metadata.goldLost,
            itemsGained = envelope.metadata.itemsGained.map { Item(it.name, it.desc, it.type, it.rarity) },
            checks = envelope.metadata.check?.let {
                listOf(CheckResult(it.skill, it.ability, it.dc, it.passed, it.total))
            } ?: emptyList(),
            npcsMet = envelope.metadata.npcsMet.map { spec ->
                LogNpc(
                    id = spec.id, name = spec.name, race = spec.race, role = spec.role,
                    age = spec.age, relationship = spec.relationship,
                    appearance = spec.appearance, personality = spec.personality,
                    thoughts = spec.thoughts, metTurn = currentTurn, lastSeenTurn = currentTurn
                )
            },
            questStarts = envelope.metadata.questStarts.mapIndexed { i, q ->
                Quest(
                    id = "q_${System.currentTimeMillis()}_$i",
                    title = q.title, type = q.type, desc = q.desc, giver = q.giver,
                    location = "", objectives = q.objectives.toMutableList(),
                    reward = q.reward, turnStarted = currentTurn
                )
            },
            questUpdates = envelope.metadata.questUpdates.map { it.title to it.objective },
            questComplete = envelope.metadata.questCompletes,
            questFails = envelope.metadata.questFails,
            shops = envelope.metadata.shops.map { it.merchant to it.items },
            travelTo = envelope.metadata.travelTo,
            partyJoins = envelope.metadata.partyJoins.map { spec ->
                PartyCompanion(
                    name = spec.name, race = spec.race, role = spec.role,
                    level = spec.level, maxHp = spec.maxHp, hp = spec.maxHp,
                    appearance = spec.appearance, personality = spec.personality,
                    joinedTurn = currentTurn
                )
            },
            timeOfDay = envelope.metadata.timeOfDay,
            moralDelta = envelope.metadata.moralDelta,
            repDeltas = envelope.metadata.repDeltas.map { it.faction to it.delta },
            worldEventHook = null,
            conditionsAdded = envelope.metadata.conditionsAdded,
            conditionsRemoved = envelope.metadata.conditionsRemoved,
            itemsRemoved = envelope.metadata.itemsRemoved,
            partyLeaves = envelope.metadata.partyLeaves,
            enemies = envelope.metadata.enemies.map { Triple(it.name, it.hp, it.maxHp) },
            factionUpdates = envelope.metadata.factionUpdates.map { Triple(it.id, it.field, it.value) },
            npcDeaths = envelope.metadata.npcDeaths,
            npcUpdates = envelope.metadata.npcUpdates.map { Triple(it.id, it.field, it.value) },
            loreEntries = envelope.metadata.loreEntries,
            npcQuotes = envelope.metadata.npcQuotes.map { it.id to it.quote },
            narratorProse = segments.filterIsInstance<NarrationSegmentData.Prose>().map { it.text },
            narratorAsides = segments.filterIsInstance<NarrationSegmentData.Aside>().map { it.text },
            playerDialogs = segments.filterIsInstance<NarrationSegmentData.PlayerDialog>().map { it.text },
            npcDialogs = segments.filterIsInstance<NarrationSegmentData.NpcDialog>().map { it.name to it.text },
            playerActions = segments.filterIsInstance<NarrationSegmentData.PlayerAction>().map { it.text },
            npcActions = segments.filterIsInstance<NarrationSegmentData.NpcAction>().map { it.name to it.text },
            segments = segments,
            source = ParseSource.JSON
        )
    }

    private fun Segment.toSegmentData(): NarrationSegmentData = when (this) {
        is Segment.Prose -> NarrationSegmentData.Prose(text)
        is Segment.Aside -> NarrationSegmentData.Aside(text)
        is Segment.PlayerAction -> NarrationSegmentData.PlayerAction(text)
        is Segment.PlayerDialog -> NarrationSegmentData.PlayerDialog(text)
        is Segment.NpcAction -> NarrationSegmentData.NpcAction(name, text)
        is Segment.NpcDialog -> NarrationSegmentData.NpcDialog(name, text)
    }

    private fun invalidReply(reason: String): ParsedReply {
        android.util.Log.w("EnvelopeParser", "invalid envelope: $reason")
        return ParsedReply(
            scene = "default", sceneDesc = "", narration = "",
            choices = emptyList(),
            damage = 0, heal = 0, xp = 0, goldGained = 0, goldLost = 0,
            itemsGained = emptyList(), checks = emptyList(), npcsMet = emptyList(),
            questStarts = emptyList(), questUpdates = emptyList(),
            questComplete = emptyList(), questFails = emptyList(),
            shops = emptyList(), travelTo = null, partyJoins = emptyList(),
            timeOfDay = null, moralDelta = 0, repDeltas = emptyList(),
            worldEventHook = null,
            source = ParseSource.INVALID
        )
    }
}
```

- [ ] **Step 2: Add the `INVALID` enum case**

In `TagParser.kt`, update the `ParseSource` enum:

```kotlin
enum class ParseSource {
    /** Envelope decoded successfully. */
    JSON,
    /** Envelope was malformed or missing — zero effects applied this turn. */
    INVALID
}
```

Delete `REGEX_FALLBACK`.

- [ ] **Step 3: Write parser tests**

```kotlin
package com.realmsoffate.game.data

import org.junit.Test
import org.junit.Assert.*

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
}
```

- [ ] **Step 4: Run**

Run: `gradle test --tests "*EnvelopeParserTest*"`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/EnvelopeParser.kt app/src/test/kotlin/com/realmsoffate/game/data/EnvelopeParserTest.kt app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt
git commit -m "feat(parser): add EnvelopeParser — decodes TurnEnvelope to ParsedReply

Strict decode with polymorphic kotlinx.serialization. Invalid input
returns ParseSource.INVALID with zero effects; no regex fallback."
```

---

### Task 7: Rewrite the DeepSeek prompt to emit the envelope

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — replace the bracket-tag schema sections in `SYS` and `DS_PREFIX`.

- [ ] **Step 1: Draft the new SYS / DS_PREFIX**

This is a full prompt rewrite; keep all voice/personality content verbatim (the `YOUR VOICE`, `PLAYER AGENCY`, `TONE EXAMPLES` sections stay). Replace the FORMAT / TAGS / METADATA sections with:

```kotlin
// In Prompts.kt, replace the FORMAT section of SYS with:
"""
OUTPUT FORMAT — STRICT JSON ENVELOPE:
Every response MUST be exactly one valid JSON object. No markdown, no prose before or after. The word "json" appears in this prompt to enable DeepSeek's JSON mode. Schema:

{
  "scene": {"type":"tavern|forest|battle|city|road|...", "desc":"one evocative line"},
  "segments": [
    {"kind":"prose", "text":"The world — environment, atmosphere, sensory detail. No dialogue, no character actions."},
    {"kind":"aside", "text":"YOUR snark — short, punchy reaction. 1-2 sentences."},
    {"kind":"player_action", "text":"What the player physically does. Written as 'You...'"},
    {"kind":"player_dialog", "text":"Verbatim player speech. No quote marks."},
    {"kind":"npc_action", "name":"stable-slug-id", "text":"Bare verb phrase — 'leans across the bar', no 'He/She'"},
    {"kind":"npc_dialog", "name":"stable-slug-id", "text":"NPC speech. No quote marks."}
  ],
  "choices": [
    {"text":"Short action", "skill":"Insight|Persuasion|Stealth|..."},
    ... EXACTLY 4 choices ...
  ],
  "metadata": {
    "damage": 0, "heal": 0, "xp": 0,
    "gold_gained": 0, "gold_lost": 0, "moral_delta": 0,
    "items_gained": [{"name":"...","desc":"...","type":"weapon|armor|consumable|item","rarity":"common|uncommon|rare|epic|legendary"}],
    "items_removed": ["..."],
    "conditions_added": ["..."], "conditions_removed": ["..."],
    "npcs_met": [{"id":"slug","name":"Display","race":"","role":"","age":"","relationship":"neutral","appearance":"","personality":"","thoughts":""}],
    "npc_updates": [{"id":"slug","field":"relationship|role|faction|location|status|name","value":"..."}],
    "npc_deaths": ["slug"],
    "npc_quotes": [{"id":"slug","quote":"..."}],
    "quest_starts": [{"title":"","type":"main|side|bounty","desc":"","giver":"","objectives":[],"reward":""}],
    "quest_updates": [{"title":"","objective":""}],
    "quest_completes": ["title"], "quest_fails": ["title"],
    "enemies": [{"name":"","hp":10,"max_hp":10}],
    "faction_updates": [{"id":"","field":"status|ruler|disposition|mood|description|type|name","value":""}],
    "rep_deltas": [{"faction":"id","delta":0}],
    "lore_entries": ["..."],
    "check": {"skill":"","ability":"STR|DEX|CON|INT|WIS|CHA","dc":10,"passed":true,"total":15},
    "travel_to": null,
    "time_of_day": null,
    "shops": [{"merchant":"","items":{"bread":2}}],
    "party_joins": [{"name":"","race":"","role":"","level":1,"max_hp":10,"appearance":"","personality":""}],
    "party_leaves": ["name"]
  }
}

WORKED EXAMPLE (copy this shape exactly):
{
  "scene": {"type":"tavern","desc":"A feast hall turned charnel house."},
  "segments": [
    {"kind":"prose","text":"The scent of bitter almonds hangs thick over the banquet hall. Lord Corwin slumps forward in his high-backed chair."},
    {"kind":"aside","text":"Poison. Not even a subtle one. The wine practically announces itself."},
    {"kind":"npc_action","name":"prosper-saltblood","text":"sets his napkin aside with deliberate care."},
    {"kind":"npc_dialog","name":"prosper-saltblood","text":"Observation is the first tool of survival, Master...?"},
    {"kind":"aside","text":"He's already three steps ahead of you. I like him."}
  ],
  "choices": [
    {"text":"Examine Lord Corwin's cup","skill":"Investigation"},
    {"text":"Question Prosper","skill":"Insight"},
    {"text":"Secure the room","skill":"Athletics"},
    {"text":"Slip out quietly","skill":"Stealth"}
  ],
  "metadata": {
    "npcs_met": [{"id":"prosper-saltblood","name":"Prosper Saltblood","race":"human","role":"spymaster","age":"40s","relationship":"neutral","appearance":"sharp-eyed, black coat","personality":"observant, patient","thoughts":"Watches you watch him."}],
    "check": {"skill":"Investigation","ability":"INT","dc":12,"passed":true,"total":16}
  }
}

RULES:
- Return ONLY the JSON object. No markdown fences, no prose outside.
- At least 1 prose segment, 2 asides, exactly 4 choices per response.
- Every NPC who speaks or acts must have a stable slug id ("vesper", "prosper-saltblood") in `name`. Same NPC across turns = same id.
- ZERO numbers in segment text ("bleeds 6 damage" is WRONG). All mechanics go in metadata.
- Empty lists/nulls are fine; omit optional fields entirely if unused.
"""
```

Full rewrite: delete every section in `SYS` that references bracket tags (`[NARRATOR_PROSE]`, `[METADATA]`, `[SCENE:type|desc]`, `[CHOICES]`, etc.) and replace with the above. Keep the voice/personality prose sections untouched.

- [ ] **Step 2: Shrink DS_PREFIX**

`DS_PREFIX` (the DeepSeek structural shim) currently re-teaches the bracket syntax. With envelope mode it only needs to say "output JSON object with keys scene/segments/choices/metadata — see schema below." Rewrite in full (target: ≤40 lines).

- [ ] **Step 3: Update `PER_TURN_REMINDER`**

Replace bracket-tag reminders with:

```kotlin
const val PER_TURN_REMINDER: String = "\n\n[Remember: output ONE json object. Scene, segments, 4 choices, metadata. No prose outside the JSON.]"
```

- [ ] **Step 4: Compile & run all prompt-related tests**

Run: `gradle compileDebugKotlin && gradle test --tests "*Prompts*" --tests "*TagParser*" --tests "*EnvelopeParser*"`
Expected: compiles; existing prompt tests PASS (they check substring presence — update substrings if needed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt
git commit -m "feat(prompts): rewrite for strict JSON envelope output

Drops every bracket-tag reference; introduces one schema + one
worked example. Preserves Narrator voice sections verbatim."
```

---

### Task 8: Wire `response_format: json_object` into the DeepSeek request

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt:150-158`

- [ ] **Step 1: Add the parameter to `callDeepSeek`'s request body**

```kotlin
val body = buildJsonObject {
    put("model", "deepseek-chat")
    put("max_tokens", 1800)
    put("temperature", 1.0)
    put("top_p", 0.95)
    put("frequency_penalty", 0.3)
    put("presence_penalty", 0.1)
    put("messages", messages)
    put("response_format", buildJsonObject { put("type", "json_object") })
}
```

Leave `classifyAction`, `summarize`, `arcSummary` alone — they already ask for JSON and don't need the structural enforcement.

- [ ] **Step 2: Compile**

Run: `gradle compileDebugKotlin`
Expected: compiles.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/AiRepository.kt
git commit -m "feat(api): enable DeepSeek JSON mode for the turn generator

response_format={type:json_object} guarantees parseable JSON.
The prompt already includes the word 'json' and a worked example."
```

---

### Task 9: Swap the parser entry point

**Pre-work for this task:** `AutoTagUnknownNpcs.scan()` in `app/src/main/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcs.kt` consumes `parsed.narration`. The envelope parser sets this to `""`. Before deleting the bracket-tag path, either:
(a) rewrite `AutoTagUnknownNpcs.scan()` to read segment text (e.g., concatenate all `parsed.segments.filterIsInstance<NarrationSegmentData.Prose>().map { it.text }` plus dialog/action text), OR
(b) have `EnvelopeParser.parse()` synthesize `narration` from segments (concatenated prose + dialog) so downstream consumers stay unchanged.

Option (b) is the smaller change; (a) is cleaner long-term. Pick whichever the Task 9 implementer prefers; add a regression test covering NPC auto-tagging on envelope-parsed turns.

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt` — strip to a thin shim.

- [ ] **Step 1: Replace `TagParser.parse` with a delegating call**

Nuke everything inside `object TagParser { ... }` except the `parse` method, and rewrite `parse` as:

```kotlin
object TagParser {
    fun parse(raw: String, currentTurn: Int): ParsedReply =
        EnvelopeParser.parse(raw, currentTurn)
}
```

Delete from `TagParser.kt`:
- `tagPattern`, `scenePattern`, `choicesPattern`, `choiceLinePattern`, `metadataBlockPattern`, `metadataJson`
- `cleanDialogContent`, `stripWrappingEmphasis`, `cleanPlayerActionContent`, `formatNpcActionContent`, `cleanAsideContent`
- `tokenizeNarrative`, `buildSegments`, `PendingBlock`, `makeSegment`
- `normalizeJsonQuotes`, `isSlugId`
- `narratorProsePattern`, `narratorAsidePattern`, `playerDialogPattern`, `npcDialogPattern`, `playerActionPattern`, `npcActionPattern`
- Everything in the old `parse()` implementation

Keep `ParsedReply`, `CheckResult`, `ParseSource`, `NarrationSegmentData` — those are the VM-facing types.

- [ ] **Step 2: Run the full test suite**

Run: `gradle test`
Expected: PASS. Any remaining tests that directly probed the old bracket-tag parsing internals (tokenizer, regex patterns) will fail — delete them; their coverage has moved to EnvelopeParserTest + TurnEnvelopeTest.

- [ ] **Step 3: Delete obsolete tests**

In `app/src/test/kotlin/com/realmsoffate/game/data/TagParserTest.kt`:
- Keep tests that feed a full response and check `ParsedReply` fields — rewrite their inputs as JSON envelopes.
- Delete tests that probe `tokenizeNarrative`, `buildSegments`, or `stripTagFragments` directly.

Example rewrite:

```kotlin
// Before:
@Test
fun `parses NPC dialog tag`() {
    val raw = "[NPC_DIALOG:vesper]hello[/NPC_DIALOG]\n[METADATA]{}[/METADATA]"
    val p = TagParser.parse(raw, 1)
    assertEquals("vesper", p.npcDialogs[0].first)
}
// After:
@Test
fun `parses NPC dialog from envelope`() {
    val raw = """{"segments":[{"kind":"npc_dialog","name":"vesper","text":"hello"}]}"""
    val p = TagParser.parse(raw, 1)
    assertEquals("vesper", p.npcDialogs[0].first)
}
```

- [ ] **Step 4: Run tests again**

Run: `gradle test`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/TagParser.kt app/src/test/kotlin/com/realmsoffate/game/data/TagParserTest.kt
git commit -m "refactor(parser): collapse TagParser to thin EnvelopeParser shim

Deletes tokenizer, stack parser, all bracket-tag regexes, and the
regex-fallback path. TagParser.parse now delegates to EnvelopeParser
for a single-path envelope decode."
```

---

### Task 10: Delete `NarrativeTokens.kt`

**Files:**
- Delete: `app/src/main/kotlin/com/realmsoffate/game/data/NarrativeTokens.kt`

- [ ] **Step 1: Confirm no remaining references**

Run: `grep -rn "NarrativeTagType\|NarrativeToken" app/src`
Expected: no hits outside the file itself.

- [ ] **Step 2: Delete the file**

```bash
git rm app/src/main/kotlin/com/realmsoffate/game/data/NarrativeTokens.kt
```

- [ ] **Step 3: Run the full build**

Run: `gradle clean compileDebugKotlin test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor(parser): delete NarrativeTokens — tokenizer is gone"
```

---

### Task 11: Device verification (required before declaring done)

This is non-negotiable per `CLAUDE.md` — no claiming success from compile-only.

- [ ] **Step 1: Build & install**

```bash
gradle installDebug && \
  adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && \
  adb -s emulator-5554 forward tcp:8735 tcp:8735
```

- [ ] **Step 2: Run P0 + P1 verification from `.cursor/rules/debug-bridge-test-procedures.mdc`**

Plus contextual steps for the parser. At minimum:
- Start a new game, take a turn, verify the response renders segments correctly (prose, asides, NPC dialog bubbles, player action).
- Verify at least one mechanical effect fires (HP change, item gain, or quest start).
- Verify the scene badge updates.
- Verify 4 choices render.
- Capture one full turn's raw response via the debug bridge `/describe` endpoint and eyeball that it's valid JSON.

- [ ] **Step 3: Stress test — run 5 consecutive turns**

Watch for:
- Any turn that falls back to `ParseSource.INVALID` (log tag `EnvelopeParser`) — if >10% do, voice quality may be fine but schema compliance isn't. Investigate.
- Prose quality — does the Narrator voice survive through JSON-mode constraint?
- Latency — `response_format` adds ~0ms in our experience, but confirm.

- [ ] **Step 4: If any regressions — file them inline in this plan, don't commit**

Record in an `## Observed Issues` section at the bottom of this file before continuing.

- [ ] **Step 5: Final commit + end**

```bash
git add -A
git commit -m "feat(parser): complete envelope migration

All 8 improvements from the tag-system review landed:
1. REGEX_FALLBACK retired (now just ParseSource.INVALID telemetry).
2. SCENE/CHOICES moved into the envelope.
3. DeepSeek JSON mode enforced via response_format.
4. Dead code deleted: stripTagFragments, dialogue markdown scrapers,
   prose-number fallbacks, bracket-tag tokenizer.
5. Matching-aware close recovery — moot (no bracket tags).
6. One normalized schema — TurnEnvelope.
7. Tag registry centralized as sealed Segment + TurnMetadata.
8. Fields schema-constrained via kotlinx.serialization."
```

---

# Appendix — Hybrid Plan Fork (only if Phase 2 gate failed)

If DeepSeek's writing quality degrades under full JSON envelope, fall back to this:

- **Keep** bracket-tag narrative layer (tokenizer stays).
- **Move** SCENE and CHOICES into METADATA JSON anyway (still delivers #2).
- **Delete** REGEX_FALLBACK for *mechanical* tags only — if METADATA parse fails, no mechanics apply (but bracket narrative still renders for prose recovery).
- **Implement** matching-aware close-tag recovery in `buildSegments` (delivers #5 for real now).
- **Add** a strict schema validation pass over METADATA with one retry — re-send the turn with a "your previous reply had malformed metadata, here's the schema again" system-message prepend.
- **Centralize** tag definitions as a `TagDef` registry (name + regex + reducer function) in a new `TagRegistry.kt` (delivers #7).
- **Normalize** vocabulary: map all the `[MORAL]` / `[REP]` / etc. to their canonical JSON-style names even in bracket form.

This fork is a bigger engineering effort than Phase 3 — paradoxically — because the dual-path architecture stays. Prefer Phase 3 unless voice quality truly tanks.

---

## Self-Review (done before handoff)

**1. Spec coverage:**
| # | Item | Task(s) |
|---|------|---------|
| 1 | Retire REGEX_FALLBACK | Task 9 (delete), Task 6 (INVALID path) |
| 2 | SCENE/CHOICES in metadata | Task 5 (envelope shape), Task 7 (prompt) |
| 3 | Structured output | Task 8 (response_format) |
| 4 | Delete dead code | Tasks 1, 2, 3, 9, 10 |
| 5 | Matching-aware close | Subsumed by envelope; fork handles otherwise |
| 6 | Normalized vocabulary | Task 5 (one schema) |
| 7 | Centralized tag defs | Task 5 (sealed Segment + TurnMetadata as registry) |
| 8 | Field constraints | Task 5/6 (kotlinx.serialization strict decode) |

**2. Placeholder scan:** no TBDs, "add error handling," or "similar to Task N" references. Every code step shows the actual code.

**3. Type consistency:** `TurnEnvelope`, `Segment`, `SceneInfo`, `ChoiceSpec`, `TurnMetadata` names used consistently across Tasks 5, 6, 7, 9. `ParseSource.JSON` / `ParseSource.INVALID` names consistent in Tasks 6 + 9. `ParsedReply` field names (`narratorProse`, `npcDialogs`, etc.) unchanged from existing code.
