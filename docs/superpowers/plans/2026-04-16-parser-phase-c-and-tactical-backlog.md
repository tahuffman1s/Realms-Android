# Parser Phase C + Tactical Backlog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substitute NPC display names in narration text, fix 4 user-visible bugs from playtests (quest near-dupes, merchant pruning, travel pace, NPC naming prompt).

**Architecture:** All 5 changes are independent. Each modifies a single file (except the NPC name substitution which touches GameViewModel only). Tests go in ApplyParsedIntegrationTest using the existing fixture/builder pattern.

**Tech Stack:** Kotlin, Robolectric, JUnit 4

---

### Task 1: Travel Progress Fixed Pace

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:1110-1111`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/game/ApplyParsedIntegrationTest.kt:258-270`

- [ ] **Step 1: Update the existing travel test to expect fixed 3 leagues**

In `ApplyParsedIntegrationTest.kt`, replace the travel test assertions (around lines 258-270):

```kotlin
        // roll=5 → fixed 3 leagues per turn. newTraveled = 3 + 3 = 6.
        val parsed = ParsedReplyBuilder().narration("You travel on.").build()
        val result = vm.applyParsed(state, char, parsed, "I travel", roll = 5, mod = 0, prof = 0)

        // Still traveling (6 < 10), travelState should exist with more leagues
        assertNotNull("travelState should still be active", result.travelState)
        assertTrue(
            "leaguesTraveled should have increased from 3",
            (result.travelState?.leaguesTraveled ?: 0) > 3
        )
        // Fixed pace: 3 leagues per turn → 3 + 3 = 6 total
        assertEquals(6, result.travelState?.leaguesTraveled)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"`
Expected: FAIL — test expects 6 but gets 7 (old formula: `2 + (5 % 3) = 4`, total `3 + 4 = 7`)

- [ ] **Step 3: Fix the travel formula**

In `GameViewModel.kt` around line 1110-1111, replace:

```kotlin
            // Progress travel by ~2-4 leagues per turn (road travel pace)
            val leaguesThisTurn = 2 + (roll % 3) // 2-4 leagues based on dice
```

With:

```kotlin
            val leaguesThisTurn = 3
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt app/src/test/kotlin/com/realmsoffate/game/game/ApplyParsedIntegrationTest.kt
git commit -m "Fix travel pace to constant 3 leagues/turn"
```

---

### Task 2: Quest Objective Substring Dedup

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/reducers/QuestAndPartyReducer.kt:44-53`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/game/ApplyParsedIntegrationTest.kt` (append 2 tests)

- [ ] **Step 1: Write failing test — superset objective replaces existing**

Append to `ApplyParsedIntegrationTest.kt`:

```kotlin
    // -------------------------------------------------------------------------
    // Quest objective substring dedup
    // -------------------------------------------------------------------------

    @Test fun `quest objective superset replaces existing and marks complete`() {
        val quest = Quest(
            title = "The Rift",
            objectives = mutableListOf("Find someone who studies rifts"),
            completed = mutableListOf(false),
            status = "active",
            location = "Testtown"
        )
        val state = GameStateFixture.baseState(
            character = GameStateFixture.character(),
            quests = listOf(quest)
        )
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder()
            .narration("You learn more.")
            .addQuestUpdate("The Rift", "Find someone who studies rifts — the scroll merchant might point you")
            .build()

        val result = vm.applyParsed(state, char, parsed, "I ask around", roll = 10, mod = 0, prof = 0)

        val q = result.quests.first { it.title == "The Rift" }
        assertEquals("should still have 1 objective, not 2", 1, q.objectives.size)
        assertTrue("objective should be marked complete", q.completed[0])
        assertEquals(
            "objective text should be replaced with the longer version",
            "Find someone who studies rifts — the scroll merchant might point you",
            q.objectives[0]
        )
    }

    @Test fun `quest objective subset matches existing without adding duplicate`() {
        val quest = Quest(
            title = "The Rift",
            objectives = mutableListOf("Find someone who studies rifts — the scroll merchant might point you"),
            completed = mutableListOf(false),
            status = "active",
            location = "Testtown"
        )
        val state = GameStateFixture.baseState(
            character = GameStateFixture.character(),
            quests = listOf(quest)
        )
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder()
            .narration("You learn more.")
            .addQuestUpdate("The Rift", "Find someone who studies rifts")
            .build()

        val result = vm.applyParsed(state, char, parsed, "I ask around", roll = 10, mod = 0, prof = 0)

        val q = result.quests.first { it.title == "The Rift" }
        assertEquals("should still have 1 objective, not 2", 1, q.objectives.size)
        assertTrue("objective should be marked complete", q.completed[0])
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"`
Expected: FAIL — first test fails because 2 objectives exist (no substring dedup), second test fails because a duplicate is appended.

- [ ] **Step 3: Implement substring dedup in QuestAndPartyReducer**

In `QuestAndPartyReducer.kt`, replace lines 44-53:

```kotlin
        parsed.questUpdates.forEach { (title, obj) ->
            val idx = result.indexOfFirst { it.title.equals(title, true) && it.status == "active" }
            if (idx >= 0) {
                val q = result[idx]
                val oi = q.objectives.indexOfFirst { it.equals(obj, true) }
                if (oi >= 0) q.completed[oi] = true else {
                    q.objectives.add(obj)
                    q.completed.add(true)
                }
            }
        }
```

With:

```kotlin
        parsed.questUpdates.forEach { (title, obj) ->
            val idx = result.indexOfFirst { it.title.equals(title, true) && it.status == "active" }
            if (idx >= 0) {
                val q = result[idx]
                val exactIdx = q.objectives.indexOfFirst { it.equals(obj, true) }
                if (exactIdx >= 0) {
                    q.completed[exactIdx] = true
                } else {
                    val subIdx = q.objectives.indexOfFirst {
                        it.contains(obj, true) || obj.contains(it, true)
                    }
                    if (subIdx >= 0) {
                        // If the new objective is longer (more detailed), replace the text
                        if (obj.length > q.objectives[subIdx].length) {
                            q.objectives[subIdx] = obj
                        }
                        q.completed[subIdx] = true
                    } else {
                        q.objectives.add(obj)
                        q.completed.add(true)
                    }
                }
            }
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/reducers/QuestAndPartyReducer.kt app/src/test/kotlin/com/realmsoffate/game/game/ApplyParsedIntegrationTest.kt
git commit -m "Add substring dedup for quest objectives"
```

---

### Task 3: Merchant Pruning on Scene Change

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:1201-1208`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/game/ApplyParsedIntegrationTest.kt` (append 1 test)

- [ ] **Step 1: Write failing test — scene change clears merchants**

Append to `ApplyParsedIntegrationTest.kt`:

```kotlin
    // -------------------------------------------------------------------------
    // Merchant pruning on scene change
    // -------------------------------------------------------------------------

    @Test fun `scene change clears availableMerchants`() {
        val state = GameStateFixture.baseState(
            character = GameStateFixture.character()
        ).copy(
            currentScene = "old market",
            availableMerchants = listOf("Old Merchant"),
            merchantStocks = mapOf("Old Merchant" to mapOf("Sword" to 50))
        )
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        // New scene with a new shop
        val parsed = ParsedReplyBuilder()
            .scene("tavern", "A cozy tavern")
            .narration("You enter the tavern.")
            .addShop("Barkeep", mapOf("Ale" to 2))
            .build()

        val result = vm.applyParsed(state, char, parsed, "I enter the tavern", roll = 10, mod = 0, prof = 0)

        assertEquals(
            "only the new merchant should remain after scene change",
            listOf("Barkeep"),
            result.availableMerchants
        )
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"`
Expected: FAIL — result contains both "Old Merchant" and "Barkeep"

- [ ] **Step 3: Implement merchant pruning**

In `GameViewModel.kt`, replace lines 1201-1203:

```kotlin
        // Merchant stocks — save stock and expose button; do NOT auto-open the overlay.
        val merchants = state.merchantStocks.toMutableMap()
        val newMerchants = state.availableMerchants.toMutableList()
```

With:

```kotlin
        // Merchant stocks — save stock and expose button; do NOT auto-open the overlay.
        // Clear merchant list on scene change — stale shops from previous locations
        // shouldn't persist. New [MERCHANT_AVAILABLE:] tags in this turn repopulate.
        val sceneChanged = parsed.scene != null && parsed.scene != state.currentScene
        val merchants = state.merchantStocks.toMutableMap()
        val newMerchants = if (sceneChanged) mutableListOf() else state.availableMerchants.toMutableList()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt app/src/test/kotlin/com/realmsoffate/game/game/ApplyParsedIntegrationTest.kt
git commit -m "Prune availableMerchants on scene change"
```

---

### Task 4: NPC Name Substitution in Narration (Parser Phase C)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt:1082-1094`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/game/ApplyParsedIntegrationTest.kt` (append 2 tests)

- [ ] **Step 1: Write failing tests — slug ID substituted, clean narration unchanged**

Append to `ApplyParsedIntegrationTest.kt`:

```kotlin
    // -------------------------------------------------------------------------
    // Parser Phase C: NPC name substitution in narration
    // -------------------------------------------------------------------------

    @Test fun `narration with NPC slug ID gets display name substituted`() {
        val npc = LogNpc(id = "vesper-saltblood", name = "Vesper Saltblood", race = "Human", role = "Bartender")
        val state = GameStateFixture.baseState(
            character = GameStateFixture.character(),
            npcLog = listOf(npc)
        )
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder()
            .narration("vesper-saltblood stares at the coins on the counter.")
            .build()

        val result = vm.applyParsed(state, char, parsed, "I look", roll = 10, mod = 0, prof = 0)

        val narrationMsg = result.messages.filterIsInstance<DisplayMessage.Narration>().last()
        assertEquals(
            "Vesper Saltblood stares at the coins on the counter.",
            narrationMsg.text
        )
    }

    @Test fun `narration without NPC refs is unchanged`() {
        val npc = LogNpc(id = "vesper-saltblood", name = "Vesper Saltblood", race = "Human", role = "Bartender")
        val state = GameStateFixture.baseState(
            character = GameStateFixture.character(),
            npcLog = listOf(npc)
        )
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder()
            .narration("The tavern is quiet tonight.")
            .build()

        val result = vm.applyParsed(state, char, parsed, "I look", roll = 10, mod = 0, prof = 0)

        val narrationMsg = result.messages.filterIsInstance<DisplayMessage.Narration>().last()
        assertEquals("The tavern is quiet tonight.", narrationMsg.text)
    }
```

- [ ] **Step 2: Run tests to verify the first fails**

Run: `gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"`
Expected: First test FAIL (slug ID not substituted), second test PASS (no change needed)

- [ ] **Step 3: Implement NPC name substitution**

In `GameViewModel.kt`, just before the `DisplayMessage.Narration` construction (before line 1082), add the substitution pass. Replace:

```kotlin
            add(DisplayMessage.Narration(
                parsed.narration, parsed.scene, parsed.sceneDesc,
```

With:

```kotlin
            // Parser Phase C: substitute NPC slug IDs with display names
            var resolvedNarration = parsed.narration
            for (npc in state.npcLog) {
                if (npc.id.isNotEmpty() && resolvedNarration.contains(npc.id, ignoreCase = true)) {
                    resolvedNarration = resolvedNarration.replace(npc.id, npc.name, ignoreCase = true)
                }
            }

            add(DisplayMessage.Narration(
                resolvedNarration, parsed.scene, parsed.sceneDesc,
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt app/src/test/kotlin/com/realmsoffate/game/game/ApplyParsedIntegrationTest.kt
git commit -m "Parser Phase C: substitute NPC display names in narration"
```

---

### Task 5: NPC Naming Prompt Fix

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt:535-537`

- [ ] **Step 1: Add BAD/GOOD example for generic slug IDs**

In `Prompts.kt`, find the NPC_DIALOG BAD/GOOD block (around lines 535-537):

```kotlin
   [NPC_DIALOG:<id>]EVERY word an NPC speaks. Slot = stable slug id. Speech only, no quote marks.
     BAD:  [NPC_DIALOG:vesper]*She leans forward.* "Another drowned rat."[/NPC_DIALOG]  ← body language + quotes inside tag
     GOOD: [NPC_ACTION:vesper]leans forward.[/NPC_ACTION] then [NPC_DIALOG:vesper]Another drowned rat.[/NPC_DIALOG]
```

Add after the existing GOOD line:

```
     BAD:  [NPC_DIALOG:guard-1]Halt! Who goes there?[/NPC_DIALOG]  ← generic slug, not a real name
     GOOD: [NPC_DIALOG:harlan-voss]Halt! Who goes there?[/NPC_DIALOG]  ← real name as slug
```

- [ ] **Step 2: Run full test suite to verify no regressions**

Run: `gradle test`
Expected: PASS (prompt text changes don't affect tests)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/Prompts.kt
git commit -m "Add BAD/GOOD prompt example for NPC slug naming"
```

---

### Task 6: Update ROADMAP and Final Verification

**Files:**
- Modify: `ROADMAP.md`

- [ ] **Step 1: Update ROADMAP**

Mark Parser Phase C as shipped. Remove the 4 tactical backlog items that are now fixed. Update the test count.

- [ ] **Step 2: Run full test suite**

Run: `gradle test`
Expected: All tests pass.

- [ ] **Step 3: Run release build**

Run: `gradle assembleRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add ROADMAP.md
git commit -m "Update ROADMAP: Parser Phase C shipped, 4 tactical backlog items resolved"
```
