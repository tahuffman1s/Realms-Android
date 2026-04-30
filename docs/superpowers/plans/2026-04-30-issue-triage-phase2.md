# Phase 2 — NPC + Skill-Check Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close issues #22 (choice hints aren't 5e skill names — everything resolves to STR), #23 (NPC tracker duplicates on name reveal instead of renaming), and #26 (NPC tracking picks up nameless characters and non-NPCs). Pure correctness work — no new user-visible features.

**Architecture:** Three orthogonal fixes wired through pure utilities + reducer changes. Add a `SkillCanon` object that canonicalizes incoming skill labels (or returns empty for non-skill verbs); call it at the envelope-parse boundary so `Choice.skill` is always canonical-or-empty downstream. Add `aliases: List<String>` to `LogNpc` and a rename-detection pass in `NpcLogReducer` that promotes a recent same-location descriptor-style entry instead of creating a duplicate when a canonical-named NPC arrives. Tighten `AutoTagUnknownNpcs` with a location-tail blocklist and a descriptor-tail blocklist so place names and unnamed characters stop entering the journal. Update `Prompts.kt` to reinforce canonical-skill-only choice hints and emit `[NPC_RENAME:old→new]` intent when appropriate. Heavy parser / reducer test coverage — every behavior change ships with a regression test.

**Tech Stack:** Kotlin · JUnit4 · Robolectric · Gradle (Kotlin DSL) · existing `EnvelopeParser` / `NpcLogReducer` / `AutoTagUnknownNpcs` / `Prompts.kt`.

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/realmsoffate/game/data/SkillCanon.kt` — pure utility: canonical 5e skill list, alias map (verbs → skill), `canonicalize(raw: String): String` returning canonical name or `""`.
- `app/src/test/kotlin/com/realmsoffate/game/data/SkillCanonTest.kt` — unit tests for the utility.

**Modify:**
- `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` — add `aliases: List<String> = emptyList()` to `LogNpc`.
- `app/src/main/kotlin/com/realmsoffate/game/data/EnvelopeParser.kt` — call `SkillCanon.canonicalize` on each `ChoiceSpec.skill` when building `Choice` (around line 71–73).
- `app/src/main/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcs.kt` — extend filtering with `NON_NPC_TAIL_WORDS` (locations) and `DESCRIPTOR_TAIL_WORDS` (unnamed characters).
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt` — add rename-detection pass; resolve refs against `aliases`; mergeNpcEntries preserves union of aliases.
- `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt` — tighten choice-skill instructions; document `[NPC_RENAME]` intent.
- `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` — strengthen `localClassifyAction` and inline a SkillCanon call before submitting a tagged choice (defense-in-depth — even if EnvelopeParser canon fails, classification is consistent).

**Test (modify):**
- `app/src/test/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcsTest.kt` — add cases for new blocklists.
- `app/src/test/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducerTest.kt` — add rename-promotion + alias-resolution cases.
- `app/src/test/kotlin/com/realmsoffate/game/data/EnvelopeParserTest.kt` — add cases that prove choice-skill canonicalization.

---

## Task 1: SkillCanon utility — failing tests first

**Files:**
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/SkillCanonTest.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/SkillCanon.kt`

This is the foundation for issue #22. Pure function, easy to test. Canonical skill names follow the list in `Prompts.kt:215`. Any unrecognized verb returns `""` (empty), which the downstream pre-roll treats as "no check / Perception default" rather than auto-defaulting to STR.

- [ ] **Step 1.1: Write the failing tests**

```kotlin
// app/src/test/kotlin/com/realmsoffate/game/data/SkillCanonTest.kt
package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillCanonTest {

    @Test
    fun `canonical skill names pass through unchanged`() {
        assertEquals("Investigation", SkillCanon.canonicalize("Investigation"))
        assertEquals("Persuasion", SkillCanon.canonicalize("Persuasion"))
        assertEquals("Sleight of Hand", SkillCanon.canonicalize("Sleight of Hand"))
        assertEquals("Animal Handling", SkillCanon.canonicalize("Animal Handling"))
        assertEquals("Attack", SkillCanon.canonicalize("Attack"))
    }

    @Test
    fun `case-insensitive canonical match`() {
        assertEquals("Investigation", SkillCanon.canonicalize("investigation"))
        assertEquals("Persuasion", SkillCanon.canonicalize("PERSUASION"))
        assertEquals("Sleight of Hand", SkillCanon.canonicalize("sleight of hand"))
    }

    @Test
    fun `verb aliases map to canonical skills`() {
        assertEquals("Investigation", SkillCanon.canonicalize("investigate"))
        assertEquals("Investigation", SkillCanon.canonicalize("search"))
        assertEquals("Intimidation", SkillCanon.canonicalize("threaten"))
        assertEquals("Intimidation", SkillCanon.canonicalize("threaten back"))
        assertEquals("Persuasion", SkillCanon.canonicalize("persuade"))
        assertEquals("Persuasion", SkillCanon.canonicalize("convince"))
        assertEquals("Stealth", SkillCanon.canonicalize("sneak"))
        assertEquals("Stealth", SkillCanon.canonicalize("hide"))
        assertEquals("Perception", SkillCanon.canonicalize("look"))
        assertEquals("Perception", SkillCanon.canonicalize("listen"))
        assertEquals("Deception", SkillCanon.canonicalize("lie"))
        assertEquals("Acrobatics", SkillCanon.canonicalize("dodge"))
        assertEquals("Athletics", SkillCanon.canonicalize("climb"))
        assertEquals("Attack", SkillCanon.canonicalize("attack"))
    }

    @Test
    fun `travel and dialogue transitions return empty`() {
        assertEquals("", SkillCanon.canonicalize("head to Hunter's Rest now"))
        assertEquals("", SkillCanon.canonicalize("speak to Jiro Gorethane first"))
        assertEquals("", SkillCanon.canonicalize("travel to the inn"))
        assertEquals("", SkillCanon.canonicalize("go north"))
        assertEquals("", SkillCanon.canonicalize("walk away"))
        assertEquals("", SkillCanon.canonicalize(""))
        assertEquals("", SkillCanon.canonicalize("   "))
    }

    @Test
    fun `unknown freeform verbs return empty rather than defaulting to STR`() {
        assertEquals("", SkillCanon.canonicalize("ponder"))
        assertEquals("", SkillCanon.canonicalize("wait"))
        assertEquals("", SkillCanon.canonicalize("rest"))
    }

    @Test
    fun `canonical list exposes all 5e skills plus Attack`() {
        val expected = setOf(
            "Athletics", "Acrobatics", "Sleight of Hand", "Stealth",
            "Arcana", "History", "Investigation", "Nature", "Religion",
            "Animal Handling", "Insight", "Medicine", "Perception",
            "Survival", "Deception", "Intimidation", "Performance", "Persuasion",
            "Attack"
        )
        assertEquals(expected, SkillCanon.CANONICAL_SKILLS)
    }
}
```

- [ ] **Step 1.2: Run tests to verify they fail**

Run: `gradle test --tests "com.realmsoffate.game.data.SkillCanonTest"`
Expected: FAIL — `Unresolved reference: SkillCanon`.

- [ ] **Step 1.3: Implement SkillCanon**

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/data/SkillCanon.kt
package com.realmsoffate.game.data

/**
 * Canonical 5e skill list and freeform-verb-to-skill alias map. Use
 * [canonicalize] at every boundary where a free-text "skill hint" enters the
 * game state (envelope choice parse, classify-action fallback). Returns the
 * canonical name on match, or `""` when the input is a travel/dialogue
 * transition or genuinely unknown verb. Empty is the right answer for
 * "no skill check warranted" — the caller decides the default (the current
 * pre-roll path falls back to Perception when skill is null/blank).
 */
object SkillCanon {
    val CANONICAL_SKILLS: Set<String> = setOf(
        "Athletics", "Acrobatics", "Sleight of Hand", "Stealth",
        "Arcana", "History", "Investigation", "Nature", "Religion",
        "Animal Handling", "Insight", "Medicine", "Perception",
        "Survival", "Deception", "Intimidation", "Performance", "Persuasion",
        "Attack"
    )

    private val CANONICAL_BY_LOWER: Map<String, String> =
        CANONICAL_SKILLS.associateBy { it.lowercase() }

    /** Verb stems → canonical skill. Order matters only for documentation. */
    private val VERB_ALIASES: List<Pair<String, String>> = listOf(
        // Investigation
        "investigate" to "Investigation", "search" to "Investigation",
        "examine" to "Investigation", "inspect" to "Investigation",
        "study" to "Investigation", "analyze" to "Investigation",
        "deduce" to "Investigation", "clue" to "Investigation",
        // Perception
        "look" to "Perception", "listen" to "Perception",
        "watch" to "Perception", "observe" to "Perception",
        "scan" to "Perception", "notice" to "Perception",
        "spot" to "Perception",
        // Intimidation
        "intimidate" to "Intimidation", "threaten" to "Intimidation",
        "scare" to "Intimidation", "menace" to "Intimidation",
        "growl" to "Intimidation",
        // Persuasion
        "persuade" to "Persuasion", "convince" to "Persuasion",
        "negotiate" to "Persuasion", "charm" to "Persuasion",
        "flatter" to "Persuasion", "plead" to "Persuasion",
        // Deception
        "lie" to "Deception", "deceive" to "Deception",
        "bluff" to "Deception", "trick" to "Deception",
        "pretend" to "Deception", "disguise" to "Deception",
        // Stealth
        "sneak" to "Stealth", "hide" to "Stealth",
        "creep" to "Stealth", "shadow" to "Stealth",
        // Acrobatics
        "dodge" to "Acrobatics", "flip" to "Acrobatics",
        "tumble" to "Acrobatics", "balance" to "Acrobatics",
        "leap" to "Acrobatics",
        // Athletics
        "climb" to "Athletics", "jump" to "Athletics",
        "lift" to "Athletics", "push" to "Athletics",
        "pull" to "Athletics", "swim" to "Athletics",
        "grapple" to "Athletics", "shove" to "Athletics",
        // Sleight of Hand
        "pickpocket" to "Sleight of Hand", "lockpick" to "Sleight of Hand",
        "pilfer" to "Sleight of Hand", "sleight" to "Sleight of Hand",
        // Arcana
        "cast" to "Arcana", "spell" to "Arcana",
        "arcane" to "Arcana", "enchant" to "Arcana",
        "ritual" to "Arcana",
        // Medicine
        "heal" to "Medicine", "bandage" to "Medicine",
        "treat" to "Medicine", "medicine" to "Medicine",
        // Survival
        "track" to "Survival", "forage" to "Survival",
        "hunt" to "Survival", "navigate" to "Survival",
        "camp" to "Survival", "survive" to "Survival",
        // Religion
        "pray" to "Religion", "bless" to "Religion",
        "divine" to "Religion", "worship" to "Religion",
        // Nature
        "nature" to "Nature", "plant" to "Nature",
        "herb" to "Nature", "beast" to "Nature",
        // Animal Handling
        "tame" to "Animal Handling", "soothe" to "Animal Handling",
        "calm" to "Animal Handling",
        // Insight
        "insight" to "Insight", "sense motive" to "Insight",
        // Performance
        "sing" to "Performance", "perform" to "Performance",
        "dance" to "Performance", "entertain" to "Performance",
        // History
        "history" to "History", "lore" to "History",
        "recall" to "History",
        // Attack
        "attack" to "Attack", "strike" to "Attack",
        "stab" to "Attack", "slash" to "Attack",
        "shoot" to "Attack", "punch" to "Attack",
        "kick" to "Attack", "fight" to "Attack",
        "kill" to "Attack"
    )

    /** Travel / dialogue transitions that explicitly carry no skill check. */
    private val TRANSITION_PREFIXES = listOf(
        "head to", "head toward", "head back", "go to", "go back", "go north",
        "go south", "go east", "go west", "travel to", "travel back",
        "walk to", "walk away", "walk back", "return to", "leave",
        "speak to", "speak with", "talk to", "talk with",
        "approach", "follow", "wait", "rest", "stay", "stand"
    )

    /**
     * Canonicalize a freeform skill label or action verb.
     *
     * Returns the canonical 5e skill name when [raw] matches a canonical name
     * (case-insensitive) or a known verb alias, `""` otherwise.
     *
     * Empty is intentional — callers that want a default should pick one
     * themselves (e.g. Perception). It MUST NOT silently default to STR/Athletics.
     */
    fun canonicalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase()

        CANONICAL_BY_LOWER[lower]?.let { return it }

        if (TRANSITION_PREFIXES.any { lower == it || lower.startsWith("$it ") }) {
            return ""
        }

        for ((stem, canonical) in VERB_ALIASES) {
            if (lower == stem || lower.startsWith("$stem ") || lower.contains(" $stem ") ||
                lower.endsWith(" $stem")
            ) {
                return canonical
            }
        }

        return ""
    }
}
```

- [ ] **Step 1.4: Run tests to verify they pass**

Run: `gradle test --tests "com.realmsoffate.game.data.SkillCanonTest"`
Expected: PASS — all six tests green.

- [ ] **Step 1.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/SkillCanon.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/SkillCanonTest.kt
git commit -m "feat(skills): add SkillCanon utility for canonical 5e skill mapping"
```

---

## Task 2: Wire SkillCanon into EnvelopeParser

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/EnvelopeParser.kt:71-73`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/data/EnvelopeParserTest.kt`

`EnvelopeParser.parse` currently propagates `ChoiceSpec.skill` verbatim into `Choice.skill`. Sanitize it through SkillCanon at this boundary so all downstream code (pre-roll dice, prompt builder, UI pill) sees only canonical-or-empty values.

- [ ] **Step 2.1: Add the failing parser test**

Append to `app/src/test/kotlin/com/realmsoffate/game/data/EnvelopeParserTest.kt`:

```kotlin
    @Test
    fun `choice skill canonicalizes freeform verb to canonical skill name`() {
        val raw = """
            {
              "scene":{"type":"default","desc":""},
              "segments":[{"kind":"prose","text":"You arrive."}],
              "choices":[
                {"text":"Look around","skill":"investigate"},
                {"text":"Threaten the guard","skill":"threaten back"},
                {"text":"Head to the inn","skill":"head to Hunter's Rest now"},
                {"text":"Sneak past","skill":"Stealth"}
              ],
              "metadata":{"check":{"skill":"Perception","ability":"WIS","dc":10,"passed":true,"total":12}}
            }
        """.trimIndent()
        val parsed = EnvelopeParser.parse(raw, currentTurn = 1)
        val skills = parsed.choices.map { it.skill }
        assertEquals(listOf("Investigation", "Intimidation", "", "Stealth"), skills)
    }
```

- [ ] **Step 2.2: Run the test to verify failure**

Run: `gradle test --tests "com.realmsoffate.game.data.EnvelopeParserTest.choice skill canonicalizes freeform verb to canonical skill name"`
Expected: FAIL — actual list has `["investigate", "threaten back", "head to Hunter's Rest now", "Stealth"]`.

- [ ] **Step 2.3: Apply SkillCanon at the parse boundary**

Locate the `mapIndexed { i, c -> Choice(...) }` call (around line 71–73 of `EnvelopeParser.kt`) and edit:

```kotlin
// before
.mapIndexed { i, c -> Choice(n = i + 1, text = c.text, skill = c.skill) },

// after
.mapIndexed { i, c -> Choice(n = i + 1, text = c.text, skill = SkillCanon.canonicalize(c.skill)) },
```

If the file has a fallback path (around line 472) that also constructs `ChoiceSpec` from raw JSON, leave that decode in place — it goes through the same `.mapIndexed` block in normal flow. Verify by searching the file for any other `Choice(` constructor and ensure each routed-through-parser path uses `SkillCanon.canonicalize`.

- [ ] **Step 2.4: Run the test to verify pass**

Run: `gradle test --tests "com.realmsoffate.game.data.EnvelopeParserTest.choice skill canonicalizes freeform verb to canonical skill name"`
Expected: PASS.

- [ ] **Step 2.5: Run the full EnvelopeParser test suite to confirm no regression**

Run: `gradle test --tests "com.realmsoffate.game.data.EnvelopeParserTest"`
Expected: PASS — all existing tests still green.

- [ ] **Step 2.6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/EnvelopeParser.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/EnvelopeParserTest.kt
git commit -m "fix(parser): canonicalize choice skill via SkillCanon (#22)"
```

---

## Task 3: Defense-in-depth — sanitize at submitAction

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:1126`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:793` (submitAction skill arg)

A choice may also originate from a saved game predating Task 2, or a debug-bridge macro injecting a raw string. Treat the `skill` arg into `submitAction` as untrusted text and canonicalize it; an empty result triggers the existing classifier path.

- [ ] **Step 3.1: Locate `pickChoice` and `submitAction` skill-tagged branch**

Read `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` around lines 793–880. Note the two branches: `skill == null` (classifier) and tagged (line 856 onwards).

- [ ] **Step 3.2: Add a unit test that proves the tagged path canonicalizes**

Create or extend a unit test in an appropriate file. If `GameViewModel` integration tests use Robolectric, prefer adding a small targeted test. Otherwise, this step is covered by Task 2's parser-level test — the runtime guard remains a code review checkpoint. **If no existing test class covers the tagged branch in isolation, skip the test add and proceed to step 3.3.**

- [ ] **Step 3.3: Sanitize `skill` at the top of submitAction**

In `submitAction(action: String, skill: String? = null, seed: Boolean = false)`, immediately after the Konami intercept block, normalize the skill:

```kotlin
        // Sanitize incoming skill — any free-text verb leftover from old saves
        // or debug macros gets canonicalized; unknown verbs become null and
        // fall through the classifier path below (rather than the tagged path).
        val normalizedSkill: String? = skill
            ?.let { com.realmsoffate.game.data.SkillCanon.canonicalize(it) }
            ?.takeIf { it.isNotBlank() }
```

Then replace every later reference to the `skill` parameter inside the function body with `normalizedSkill`. The existing parameter is used at:
- line 811 `dispatchToAi(action, skill, seed = true, preRolled = ...)`
- line 818 `if (skill == null) {`
- line 859 `val ability = skillToAbility(skill)`
- line 862 `prof = if (classProficient(char.cls, skill))`
- line 867 `skill = skill,`
- line 915 (inside dispatchToAi closure — leave alone if outside this fn)

**Search for other reads of `skill` within `fun submitAction` (the body ends at line 876 with the closing brace of the tagged branch) and update each to `normalizedSkill`.**

- [ ] **Step 3.4: Build to confirm no compile errors**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.5: Run all tests**

Run: `gradle test`
Expected: PASS — all green.

- [ ] **Step 3.6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "fix(game): sanitize submitAction skill via SkillCanon (#22)"
```

---

## Task 4: Tighten Prompts.kt — canonical-skill-only choice hints + RENAME tag

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt`

The narrator currently emits parentheticals like `(speak to Jiro first)` because the prompt's only constraint is the skill list. Strengthen with explicit "or empty" guidance for travel/dialogue, and remove the implicit pressure to fill `skill` for every choice.

Also document the new `[NPC_RENAME:old-name-or-slug→New Canonical Name]` intent the narrator can emit when a previously-anonymous NPC reveals their real name. The reducer in Task 6 will accept it.

- [ ] **Step 4.1: Update the choices schema example (around lines 215–216)**

Find this block in `Prompts.kt`:

```
  "choices": [
    {"text":"Short action", "skill":"Insight|Persuasion|Stealth|Perception|Investigation|Athletics|Acrobatics|Arcana|History|Nature|Religion|Animal Handling|Insight|Medicine|Survival|Deception|Intimidation|Performance|Sleight of Hand|Attack"}
```

Change the schema comment to admit empty:

```
  "choices": [
    {"text":"Short action", "skill":"Insight|Persuasion|Stealth|Perception|Investigation|Athletics|Acrobatics|Arcana|History|Nature|Religion|Animal Handling|Medicine|Survival|Deception|Intimidation|Performance|Sleight of Hand|Attack|"}
```

(Trailing `|` after `Attack` — explicit empty-string option. Note: also remove the duplicate `Insight` if the original line has it twice.)

- [ ] **Step 4.2: Update the choices instruction block (around lines 335–343)**

Find:

```
choices array (REQUIRED — exactly 4 entries every response):
[
  {"text":"Action under 10 words","skill":"Persuasion"},
  {"text":"Different approach","skill":"Deception"},
  {"text":"Exploration or environmental option","skill":"Perception"},
  {"text":"Creative, risky, or unexpected option","skill":"Athletics"}
]

Make choice text SHORT — each 1 line max. Mix combat/social/stealth. Include one bad idea.
```

Replace with:

```
choices array (REQUIRED — exactly 4 entries every response):
[
  {"text":"Action under 10 words","skill":"Persuasion"},
  {"text":"Different approach","skill":"Deception"},
  {"text":"Exploration or environmental option","skill":"Perception"},
  {"text":"Head to the inn","skill":""}
]

skill MUST be one of the canonical 5e skill names listed in the schema, OR an
empty string "". Use empty for pure travel/movement/transition choices ("head
to X", "go back", "leave", "wait", "speak to X first" with no manipulation).
NEVER invent verbs like "investigate", "threaten back", "speak to Jiro" — map
those to canonical skills (Investigation, Intimidation, Persuasion) or use "".

Make choice text SHORT — each 1 line max. Mix combat/social/stealth. Include one bad idea.
```

- [ ] **Step 4.3: Update the "Exactly 4 entries in choices" rule (around line 479)**

Find: `4. Exactly 4 entries in "choices". Each: {"text":"...","skill":"SkillName"}.`

Replace with: `4. Exactly 4 entries in "choices". Each: {"text":"...","skill":"SkillName-or-empty"}. Empty is for travel/transition only — never a substitute for a real check.`

- [ ] **Step 4.4: Add NPC_RENAME documentation**

Search Prompts.kt for the NPC_MET / NPC_UPDATE block. Add a sibling instruction. Use grep first to find the right insertion point:

Run: `grep -n "NPC_MET\|NPC_UPDATE\|npcs_met" app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt | head -10`

After the existing NPC instructions, add:

```
When a previously-anonymous NPC reveals their real name in dialogue or
narration (e.g. "Grey Cloak Hunter" turns out to be "Voss Ironhand"), set
`metadata.npcs_renamed` to a list of `{"from":"Grey Cloak Hunter","to":"Voss
Ironhand"}` objects so the journal updates the existing entry in place
instead of creating a duplicate. ALSO emit the canonical name in
`metadata.npcs_met` for that turn so the new identity is recorded — but the
rename pair is what tells the journal not to fork. Use this whenever a name
reveal happens; the player should never see two journal entries for the
same character.
```

- [ ] **Step 4.5: Verify the whole file still compiles**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt
git commit -m "feat(prompts): require canonical-or-empty choice skill, document NPC_RENAME (#22, #23)"
```

---

## Task 5: Add `aliases` to LogNpc + plumb through reducer resolver

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducerTest.kt`

`aliases: List<String>` keeps prior names + slugs of an NPC. Old saves and prompt references that mention the descriptor name still resolve after a rename. Default empty list keeps the change backwards-compatible — the existing kotlinx.serialization model treats new fields with defaults as optional.

- [ ] **Step 5.1: Add the field to LogNpc**

Read `app/src/main/kotlin/com/realmsoffate/game/data/Models.kt` and locate `data class LogNpc(`. Add a new field at the end of the constructor (or grouped with other list fields like `dialogueHistory`):

```kotlin
    val aliases: List<String> = emptyList(),
```

Place it adjacent to `dialogueHistory` / `memorableQuotes` for cohesion. Do not change any other fields.

- [ ] **Step 5.2: Write a failing alias-resolution test**

Append to `app/src/test/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducerTest.kt`:

```kotlin
    @Test
    fun `resolveNpcIdx matches an alias`() {
        val npcs = listOf(
            LogNpc(
                id = "voss-ironhand",
                name = "Voss Ironhand",
                aliases = listOf("grey-cloak-hunter", "Grey Cloak Hunter"),
                metTurn = 1,
                lastSeenTurn = 5
            )
        )
        val byOldSlug = NpcLogReducer.resolveNpcIdx("grey-cloak-hunter", npcs)
        val byOldName = NpcLogReducer.resolveNpcIdx("Grey Cloak Hunter", npcs)
        assertEquals(0, byOldSlug)
        assertEquals(0, byOldName)
    }
```

- [ ] **Step 5.3: Run test to verify failure**

Run: `gradle test --tests "com.realmsoffate.game.game.reducers.NpcLogReducerTest.resolveNpcIdx matches an alias"`
Expected: FAIL — returns -1 because aliases aren't consulted yet.

- [ ] **Step 5.4: Extend resolveNpcIdx to consult aliases**

Replace the body of `internal fun resolveNpcIdx` in `NpcLogReducer.kt` (around line 281):

```kotlin
    internal fun resolveNpcIdx(ref: String, npcLog: List<LogNpc>): Int {
        if (ref.isBlank()) return -1
        val byId = npcLog.indexOfFirst { it.id == ref }
        if (byId >= 0) return byId
        val refKey = IdGen.nameKey(ref)
        val byName = npcLog.indexOfFirst { IdGen.nameKey(it.name) == refKey }
        if (byName >= 0) return byName
        return npcLog.indexOfFirst { npc ->
            npc.aliases.any { alias ->
                alias == ref || IdGen.nameKey(alias) == refKey
            }
        }
    }
```

- [ ] **Step 5.5: Run test to verify pass + run the full reducer suite**

Run: `gradle test --tests "com.realmsoffate.game.game.reducers.NpcLogReducerTest"`
Expected: PASS — all existing + new tests green.

- [ ] **Step 5.6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Models.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducerTest.kt
git commit -m "feat(npc): add aliases field, resolve refs against aliases (#23)"
```

---

## Task 6: Rename detection in NpcLogReducer (the core #23 fix)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducerTest.kt`

When an `npcsMet` entry has a canonical-looking name, look for a recent same-location descriptor-style entry that has not seen a real name yet. If matched, rename in place — keep dialogueHistory + memorableQuotes, append the old name+slug to `aliases`. This handles "Grey Cloak Hunter → Voss Ironhand".

A two-word descriptor is anything where the first word is in a small `DESCRIPTOR_FIRST_WORDS` set (Hooded, Cloaked, Masked, Grey, Old, Young, Tall, Short, ...) OR the last word is in `DESCRIPTOR_ROLE_WORDS` (Hunter, Stranger, Traveler, Guard, Soldier, Knight, Cloak, Mercenary, Beggar, Child, Woman, Man, Drifter, Wanderer, Merchant, Captain, Sergeant). Combined with "no canonical race set yet" and "lastSeenTurn within 3 turns" the false-positive rate stays low.

- [ ] **Step 6.1: Write the failing rename test**

Append to `NpcLogReducerTest.kt`:

```kotlin
    @Test
    fun `npcsMet rename promotes a recent descriptor-style NPC instead of duplicating`() {
        val existing = npc(id = "grey-cloak-hunter", name = "Grey Cloak Hunter")
            .copy(
                lastLocation = "Nightbriar",
                lastSeenTurn = 5,
                dialogueHistory = mutableListOf(
                    "T2: \"Name's Voss. I've been tracking your double for three months.\"",
                    "T5: \"Name's Voss, by the way — Voss Ironhand.\""
                )
            )
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(name = "Voss Ironhand", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed, turn = 5, loc = "Nightbriar")

        assertEquals("Should rename in place, not duplicate", 1, result.npcLog.size)
        val merged = result.npcLog[0]
        assertEquals("Voss Ironhand", merged.name)
        assertEquals("grey-cloak-hunter", merged.id)
        assertEquals("Human", merged.race)
        assertEquals(2, merged.dialogueHistory.size)
        assertTrue("Old slug should be preserved as alias", "grey-cloak-hunter" in merged.aliases)
        assertTrue("Old name should be preserved as alias", merged.aliases.any { it.equals("Grey Cloak Hunter", true) })
    }

    @Test
    fun `npcsMet does not rename when no descriptor-style entry is present`() {
        val existing = npc(id = "mira-cole", name = "Mira Cole", race = "Human")
            .copy(lastLocation = "Hightower", lastSeenTurn = 5)
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(name = "Voss Ironhand", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed, turn = 5, loc = "Hightower")

        assertEquals("Mira Cole is not a descriptor and should remain", 2, result.npcLog.size)
        assertTrue(result.npcLog.any { it.name == "Mira Cole" })
        assertTrue(result.npcLog.any { it.name == "Voss Ironhand" })
    }

    @Test
    fun `npcsMet does not rename when descriptor entry is in a different location`() {
        val existing = npc(id = "grey-cloak-hunter", name = "Grey Cloak Hunter")
            .copy(lastLocation = "Whispering Marsh", lastSeenTurn = 5)
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(name = "Voss Ironhand", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed, turn = 5, loc = "Nightbriar")

        assertEquals("Different location => no rename", 2, result.npcLog.size)
    }

    @Test
    fun `npcsMet does not rename when descriptor entry is too old`() {
        val existing = npc(id = "grey-cloak-hunter", name = "Grey Cloak Hunter")
            .copy(lastLocation = "Nightbriar", lastSeenTurn = 1) // current=5, gap=4 > 3
        val parsed = ParsedReplyBuilder()
            .addNpcMet(npc(name = "Voss Ironhand", race = "Human"))
            .build()

        val result = applyWith(npcLog = listOf(existing), parsed = parsed, turn = 5, loc = "Nightbriar")

        assertEquals("Stale descriptor => no rename", 2, result.npcLog.size)
    }
```

- [ ] **Step 6.2: Run the tests to verify failure**

Run: `gradle test --tests "com.realmsoffate.game.game.reducers.NpcLogReducerTest"`
Expected: FAIL on the four new tests (assertEquals 1 vs 2 / etc.) — others still pass.

- [ ] **Step 6.3: Add the descriptor heuristic and rename pass**

In `NpcLogReducer.kt`, add at the top of the object (just inside `object NpcLogReducer {`):

```kotlin
    /**
     * Descriptor-style first words — adjectives or colour/condition modifiers
     * that almost never appear in real personal names.
     */
    private val DESCRIPTOR_FIRST_WORDS = setOf(
        "Hooded", "Cloaked", "Masked", "Grey", "Gray", "Old", "Young", "Tall",
        "Short", "Pale", "Dark", "Bright", "Silent", "Bearded", "Drunk",
        "One-Eyed", "Scarred", "Wounded"
    )

    /**
     * Descriptor-style last words — generic roles or attire that the narrator
     * uses before revealing a name. Voss starts as a "Grey Cloak Hunter"; after
     * the reveal the journal should show "Voss Ironhand" with the old name in
     * aliases.
     */
    private val DESCRIPTOR_ROLE_WORDS = setOf(
        "Hunter", "Stranger", "Traveler", "Traveller", "Guard", "Soldier",
        "Knight", "Cloak", "Mercenary", "Beggar", "Child", "Woman", "Man",
        "Drifter", "Wanderer", "Merchant", "Captain", "Sergeant", "Peasant",
        "Priest", "Monk", "Rogue", "Hooded", "Figure", "Acolyte"
    )

    private const val RENAME_RECENT_TURN_GAP = 3

    /**
     * Heuristic — does [name] look like a placeholder descriptor rather than
     * a personal name? True when first word is a known descriptor-prefix OR
     * last word is a generic role/attire word.
     */
    private fun isDescriptorLikeName(name: String): Boolean {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return false
        val first = parts.first()
        val last = parts.last()
        return first in DESCRIPTOR_FIRST_WORDS || last in DESCRIPTOR_ROLE_WORDS
    }

    /**
     * Find an existing same-location, recently-seen, descriptor-named entry
     * that the incoming [incomingName] should rename rather than duplicate.
     * Returns -1 if no plausible match.
     */
    private fun findRenameTarget(
        workingLog: List<LogNpc>,
        incomingName: String,
        currentTurn: Int,
        currentLocName: String
    ): Int {
        if (incomingName.isBlank() || currentLocName.isBlank()) return -1
        if (isDescriptorLikeName(incomingName)) return -1 // incoming itself is a descriptor — not a rename
        return workingLog.indexOfFirst { existing ->
            existing.name.isNotBlank() &&
                isDescriptorLikeName(existing.name) &&
                existing.lastLocation.equals(currentLocName, ignoreCase = true) &&
                (currentTurn - existing.lastSeenTurn) <= RENAME_RECENT_TURN_GAP
        }
    }
```

Now in the `npcsMet` merge loop in `apply` (between lines 59 and 119), inside the **legacy-name branch** (the `else` at line 101) and the ID-first **no-existing-match branch** (the `else` at line 92), insert a rename probe BEFORE creating the new entry. Refactor the body to:

```kotlin
        parsed.npcsMet.forEach { n ->
            val cleanName = when {
                n.name.isBlank() -> n.name
                IdGen.isSlug(n.name) && (n.id.isBlank() || n.name.equals(n.id, ignoreCase = true)) ->
                    IdGen.slugToDisplay(n.name)
                else -> n.name
            }
            if (n.id.isNotBlank()) {
                val byId = workingLog.indexOfFirst { it.id == n.id }
                val existing = if (byId >= 0) byId else workingLog.indexOfFirst {
                    cleanName.isNotBlank() && IdGen.nameKey(it.name) == IdGen.nameKey(cleanName)
                }
                if (existing >= 0) {
                    val old = workingLog[existing]
                    val keepOldName = cleanName.isBlank() ||
                        (IdGen.isSlug(cleanName) && !IdGen.isSlug(old.name))
                    workingLog[existing] = old.copy(
                        name = if (keepOldName) old.name else cleanName,
                        race = n.race.ifBlank { old.race },
                        role = n.role.ifBlank { old.role },
                        age = n.age.ifBlank { old.age },
                        relationship = n.relationship.ifBlank { old.relationship },
                        appearance = n.appearance.ifBlank { old.appearance },
                        personality = n.personality.ifBlank { old.personality },
                        thoughts = n.thoughts.ifBlank { old.thoughts },
                        lastSeenTurn = currentTurn,
                        lastLocation = currentLocName.ifBlank { old.lastLocation }
                    )
                } else {
                    val renameIdx = findRenameTarget(workingLog, cleanName, currentTurn, currentLocName)
                    if (renameIdx >= 0) {
                        val old = workingLog[renameIdx]
                        val newAliases = (old.aliases + listOf(old.name, old.id))
                            .filter { it.isNotBlank() && it != cleanName }
                            .distinct()
                        workingLog[renameIdx] = old.copy(
                            name = cleanName,
                            race = n.race.ifBlank { old.race },
                            role = n.role.ifBlank { old.role },
                            age = n.age.ifBlank { old.age },
                            relationship = n.relationship.ifBlank { old.relationship },
                            appearance = n.appearance.ifBlank { old.appearance },
                            personality = n.personality.ifBlank { old.personality },
                            thoughts = n.thoughts.ifBlank { old.thoughts },
                            aliases = newAliases,
                            lastSeenTurn = currentTurn,
                            lastLocation = currentLocName.ifBlank { old.lastLocation }
                        )
                    } else {
                        val safeId = if (n.id !in existingNpcIds) {
                            n.id
                        } else {
                            IdGen.forName(cleanName, existingNpcIds)
                        }
                        existingNpcIds.add(safeId)
                        workingLog.add(n.copy(id = safeId, name = cleanName, lastLocation = currentLocName))
                    }
                }
            } else {
                val existing = workingLog.indexOfFirst {
                    IdGen.nameKey(it.name) == IdGen.nameKey(cleanName)
                }
                if (existing >= 0) {
                    val old = workingLog[existing]
                    workingLog[existing] = old.copy(
                        relationship = n.relationship.ifBlank { old.relationship },
                        lastSeenTurn = currentTurn,
                        lastLocation = currentLocName.ifBlank { old.lastLocation }
                    )
                } else {
                    val renameIdx = findRenameTarget(workingLog, cleanName, currentTurn, currentLocName)
                    if (renameIdx >= 0) {
                        val old = workingLog[renameIdx]
                        val newAliases = (old.aliases + listOf(old.name, old.id))
                            .filter { it.isNotBlank() && it != cleanName }
                            .distinct()
                        workingLog[renameIdx] = old.copy(
                            name = cleanName,
                            race = n.race.ifBlank { old.race },
                            relationship = n.relationship.ifBlank { old.relationship },
                            aliases = newAliases,
                            lastSeenTurn = currentTurn,
                            lastLocation = currentLocName.ifBlank { old.lastLocation }
                        )
                    } else {
                        val newId = IdGen.forName(cleanName, existingNpcIds)
                        existingNpcIds.add(newId)
                        workingLog.add(n.copy(id = newId, name = cleanName, lastLocation = currentLocName))
                    }
                }
            }
        }
```

- [ ] **Step 6.4: Run the rename tests to verify pass**

Run: `gradle test --tests "com.realmsoffate.game.game.reducers.NpcLogReducerTest"`
Expected: PASS — all original tests + the four new rename tests green.

- [ ] **Step 6.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducer.kt \
        app/src/test/kotlin/com/realmsoffate/game/game/reducers/NpcLogReducerTest.kt
git commit -m "fix(npc): rename in place when descriptor-named NPC reveals real name (#23)"
```

---

## Task 7: AutoTagUnknownNpcs — block locations and unnamed-character descriptors

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcs.kt`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcsTest.kt`

The proper-noun regex picks up location names ("Iron Forge", "Old Tower") and unnamed-character descriptors ("Hooded Stranger") that should not enter the journal. Add two blocklists and a "all words are dictionary words" guard.

- [ ] **Step 7.1: Write failing tests**

Append to `AutoTagUnknownNpcsTest.kt`:

```kotlin
    @Test
    fun `skips location-style names ending in geography word`() {
        val parsed = parsedWithSegments(
            NarrationSegmentData.Prose(
                "You climb Iron Forge and survey the Whispering Marsh. The Old Tower looms. Mira Cole waves."
            )
        )
        val specs = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = emptyList(),
            currentLoc = "Hightower",
            turn = 5
        )
        val names = specs.map { it.name }
        assertTrue("Iron Forge is a location: $names", "Iron Forge" !in names)
        assertTrue("Whispering Marsh is a location: $names", "Whispering Marsh" !in names)
        assertTrue("Old Tower is a location: $names", "Old Tower" !in names)
        assertTrue("Mira Cole should still be detected: $names", "Mira Cole" in names)
    }

    @Test
    fun `skips descriptor-only character names that have no personal name`() {
        val parsed = parsedWithSegments(
            NarrationSegmentData.Prose(
                "A Hooded Stranger watches from the corner. The Old Beggar shuffles past. Voss Ironhand approaches."
            )
        )
        val specs = AutoTagUnknownNpcs.scan(
            parsed = parsed,
            existingNpcs = emptyList(),
            currentLoc = "Nightbriar",
            turn = 5
        )
        val names = specs.map { it.name }
        assertTrue("Hooded Stranger is descriptor-only: $names", "Hooded Stranger" !in names)
        assertTrue("Old Beggar is descriptor-only: $names", "Old Beggar" !in names)
        assertTrue("Voss Ironhand should be detected: $names", "Voss Ironhand" in names)
    }
```

- [ ] **Step 7.2: Run the tests to verify failure**

Run: `gradle test --tests "com.realmsoffate.game.data.AutoTagUnknownNpcsTest"`
Expected: FAIL — both new cases assert items absent that are currently captured.

- [ ] **Step 7.3: Extend the filtering**

Edit `AutoTagUnknownNpcs.kt`. Add new sets next to `ITEM_TAIL_WORDS`:

```kotlin
    /** If the candidate's *last* word is a geography/structure word, it's a place, not an NPC. */
    private val LOCATION_TAIL_WORDS = setOf(
        "tower", "forge", "gate", "bridge", "road", "hill", "mountain", "river",
        "valley", "fort", "fortress", "castle", "manor", "hall", "temple", "shrine",
        "market", "tavern", "inn", "village", "city", "town", "keep", "watch",
        "spire", "vault", "den", "cave", "caverns", "cavern", "marsh", "swamp",
        "lake", "sea", "bay", "harbor", "harbour", "port", "ridge", "peak", "pass",
        "wood", "woods", "forest", "grove", "field", "fields", "plains", "moor",
        "wastes", "barrow", "barrows", "ruin", "ruins", "tomb", "crypt", "abbey",
        "monastery", "chapel", "garden", "gardens", "yard", "court", "courtyard"
    )

    /** Last word is a generic role/attire descriptor — treat as unnamed character. */
    private val DESCRIPTOR_TAIL_WORDS = setOf(
        "stranger", "traveler", "traveller", "guard", "soldier", "knight",
        "mercenary", "beggar", "child", "woman", "man", "drifter", "wanderer",
        "merchant", "captain", "sergeant", "peasant", "priest", "monk", "rogue",
        "figure", "acolyte", "hunter", "cloak", "hood", "hooded"
    )

    /** First word is a colour/condition adjective — descriptor-only candidate. */
    private val DESCRIPTOR_HEAD_WORDS = setOf(
        "hooded", "cloaked", "masked", "grey", "gray", "old", "young", "tall",
        "short", "pale", "dark", "bright", "silent", "bearded", "drunk",
        "scarred", "wounded"
    )
```

Then in `scan`, replace the per-candidate filter chain inside the `for (m in PROPER_NOUN.findAll(narrationText))` loop with:

```kotlin
        for (m in PROPER_NOUN.findAll(narrationText)) {
            val name = m.groupValues[1]
            val firstWord = name.substringBefore(' ').lowercase()
            val lastWord = name.substringAfterLast(' ').lowercase()
            if (firstWord.replaceFirstChar { it.uppercase() } in COMMON) continue
            if (lastWord in ITEM_TAIL_WORDS) continue
            if (lastWord in LOCATION_TAIL_WORDS) continue
            // Descriptor-only candidate when *both* head adjective and tail role
            // are generic. "Hooded Stranger" both. "Voss Ironhand" — neither.
            // "Old Tower" already filtered by LOCATION_TAIL_WORDS.
            if (firstWord in DESCRIPTOR_HEAD_WORDS && lastWord in DESCRIPTOR_TAIL_WORDS) continue
            // Pure descriptor tail with no recognised personal-name structure: skip.
            // Two-word: any tail descriptor is suspect. Voss Ironhand uses "Ironhand"
            // (compound surname, not in the descriptor set), so it survives.
            if (lastWord in DESCRIPTOR_TAIL_WORDS) continue
            val key = IdGen.nameKey(name)
            if (key in existingKeys || key in seenKeys) continue
            if (key in itemKeys || key in spellKeys) continue
            seenKeys += key
            out += LogNpc(
                id = "",
                name = name,
                relationship = "neutral",
                lastLocation = currentLoc,
                metTurn = turn,
                lastSeenTurn = turn
            )
        }
```

- [ ] **Step 7.4: Run the tests to verify pass + full AutoTag suite**

Run: `gradle test --tests "com.realmsoffate.game.data.AutoTagUnknownNpcsTest"`
Expected: PASS — all 7 (existing) + 2 (new) cases green.

- [ ] **Step 7.5: Run the full test suite to catch downstream regressions**

Run: `gradle test`
Expected: PASS.

- [ ] **Step 7.6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcs.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/AutoTagUnknownNpcsTest.kt
git commit -m "fix(npc): block location and descriptor-only names from auto-tag (#26)"
```

---

## Task 8: Strengthen localClassifyAction to use SkillCanon for unmapped verbs

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:725-790`

The current local fallback hard-codes verb→skill keywords inline. Route any unmatched action through `SkillCanon.canonicalize` before falling through to the catch-all Perception. This ensures verbs added to SkillCanon's alias map automatically apply here too.

- [ ] **Step 8.1: Read the current `localClassifyAction` (lines 725–790) to confirm signature**

The function returns `String?` and is called from line 828.

- [ ] **Step 8.2: Add a SkillCanon fallback before the final `else -> "Perception"`**

Find the final clause in the `when {` block:

```kotlin
            // Default: if it sounds like an action, use Perception as catch-all
            a.contains("try") || a.contains("attempt") -> "Perception"
            else -> "Perception"
```

Replace with:

```kotlin
            // Default: if it sounds like an action, use Perception as catch-all
            a.contains("try") || a.contains("attempt") -> "Perception"
            else -> com.realmsoffate.game.data.SkillCanon.canonicalize(a)
                .ifBlank { "Perception" }
```

- [ ] **Step 8.3: Build to confirm no compile errors**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8.4: Run the full test suite**

Run: `gradle test`
Expected: PASS.

- [ ] **Step 8.5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt
git commit -m "refactor(skills): route localClassifyAction through SkillCanon (#22)"
```

---

## Task 9: Final verification — lint + assemble + deploy P0/P1

**Files:** none (verification step).

- [ ] **Step 9.1: Lint**

Run: `gradle lint`
Expected: BUILD SUCCESSFUL with no new errors. Note any pre-existing warnings are fine; new ones must be addressed before commit.

- [ ] **Step 9.2: Assemble release to surface any R8 issues**

Run: `gradle assembleRelease`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9.3: Install + smoke test against the emulator (the project deploy step)**

Run: `gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735`
Expected: Install OK, app launches, debug bridge reachable on tcp:8735.

- [ ] **Step 9.4: Run debug-bridge P0 + P1 verification**

See `.cursor/rules/debug-bridge-test-procedures.mdc` P0 (boot smoke) and P1 (basic turn). The deploy gate from `CLAUDE.md` applies — runtime verification is required, not just compile-clean.

- [ ] **Step 9.5: Final commit with summary if any docs were touched**

If anything in `README.md` or other docs changed, commit. Otherwise, skip.

```bash
git status
# if no changes, skip; else:
git add <changed docs>
git commit -m "docs: note phase 2 fixes in changelog"
```

---

## Self-Review Notes

- **Spec coverage:** Tasks 1–3 + 4 + 8 cover #22. Tasks 4 + 5 + 6 cover #23. Task 7 covers #26. Task 9 is the deploy/verify gate the CLAUDE.md workflow requires.
- **Out of scope (from phase 1 plan list):** #18 (cheats overlay perf), #20 (random character), #25 (prose degradation — needs diagnose), #27 (memory of past actions — needs diagnose), #29 (lore reactivity), #24 (content-to-JSON epic), #28 (auto-update epic). These remain on Phase 3 / future plans.
- **Type consistency check:** `LogNpc.aliases` is referenced in Task 5 (`resolveNpcIdx`), Task 6 (rename target), and the existing reducer's `mergeNpcEntries` does not currently union aliases — if the rename-fallback path inside `npcUpdates` is ever exercised with two entries that both carry aliases, the result currently keeps `primary.aliases` only. That's acceptable for now (rename-fallback is a legacy non-ID path); add a follow-up TODO if a test surfaces a regression.
- **Placeholder scan:** All steps include exact code, exact file paths, exact commands. No "TBD" / "implement later" / "similar to X" found.
- **Frequent commits:** 8 commits across 9 tasks (Task 9 may add a 9th).
