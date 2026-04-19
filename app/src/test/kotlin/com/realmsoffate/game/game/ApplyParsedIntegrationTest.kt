package com.realmsoffate.game.game

import com.realmsoffate.game.data.Faction
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.Quest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase I integration tests for [GameViewModel.applyParsed].
 *
 * Goal: capture current behavior as a snapshot so Phase II domain-reducer
 * extraction can verify it doesn't change anything.
 *
 * All tests use a real VM + real PreferencesStore (via Robolectric) and call
 * the now-internal [applyParsed] directly. No network calls are made.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplyParsedIntegrationTest {

    // -------------------------------------------------------------------------
    // Test 1: Level-up triggers when XP crosses threshold
    // -------------------------------------------------------------------------
    @Test
    fun `level-up triggers when xp crosses threshold`() {
        // Level 2 threshold is 300 XP. Start at 280, grant 20 → should hit 300 and level up.
        val char = GameStateFixture.character(level = 1, xp = 280, hp = 10, maxHp = 10)
        val state = GameStateFixture.baseState(character = char, turns = 5)
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder().xp(20).build()
        val result = vm.applyParsed(state, char, parsed, "I rest", roll = 10, mod = 0, prof = 0)

        // Character should have leveled up to 2
        assertEquals(2, result.character?.level)
        // XP should be 300
        assertEquals(300, result.character?.xp)
        // maxHp should have increased (Fighter hitDie=10, conMod=0 → +10)
        assertTrue("maxHp should increase on level up", (result.character?.maxHp ?: 0) > 10)
        // The VM's pendingLevelUp side-effect should be set to 2
        assertEquals(2, GameStateFixture.getPendingLevelUp(vm))
    }

    // -------------------------------------------------------------------------
    // Test 2: NPC_MET adds entry with id and displayName
    // -------------------------------------------------------------------------
    @Test
    fun `NPC_MET adds entry with id and displayName`() {
        val state = GameStateFixture.baseState(npcLog = emptyList(), turns = 4)
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val newNpc = LogNpc(
            id = "vesper-the-lightless",
            name = "Vesper the Lightless",
            race = "Elf",
            role = "Mage",
            metTurn = 5,
            lastSeenTurn = 5
        )
        val parsed = ParsedReplyBuilder().addNpcMet(newNpc).build()
        val result = vm.applyParsed(state, char, parsed, "I approach", roll = 10, mod = 0, prof = 0)

        assertEquals(1, result.npcLog.size)
        val logged = result.npcLog[0]
        assertEquals("vesper-the-lightless", logged.id)
        assertEquals("Vesper the Lightless", logged.name)
        // metTurn and lastSeenTurn should be state.turns + 1 = 5
        assertEquals(5, logged.metTurn)
        assertEquals(5, logged.lastSeenTurn)
    }

    // -------------------------------------------------------------------------
    // Test 3: NPC rename via NPC_UPDATE:name updates display without duplicating
    // -------------------------------------------------------------------------
    @Test
    fun `NPC rename via NPC_UPDATE name updates display without duplicating`() {
        val existingNpc = LogNpc(
            id = "hooded-figure",
            name = "Hooded Figure",
            race = "Human",
            role = "Stranger",
            metTurn = 1,
            lastSeenTurn = 1
        )
        val state = GameStateFixture.baseState(npcLog = listOf(existingNpc), turns = 3)
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder()
            .addNpcUpdate("hooded-figure", "name", "Veran Nightwhisper")
            .build()
        val result = vm.applyParsed(state, char, parsed, "I unmask them", roll = 15, mod = 0, prof = 0)

        // Still exactly one NPC — no duplication
        assertEquals(1, result.npcLog.size)
        val updated = result.npcLog[0]
        // ID must remain unchanged
        assertEquals("hooded-figure", updated.id)
        // Name must be updated
        assertEquals("Veran Nightwhisper", updated.name)
    }

    // -------------------------------------------------------------------------
    // Test 4: NPC death marks entry with status=dead and generates System message
    // -------------------------------------------------------------------------
    @Test
    fun `NPC death marks entry with status=dead and generates a System message`() {
        val npc = LogNpc(
            id = "prosper-saltblood",
            name = "Prosper Saltblood",
            race = "Human",
            role = "Merchant",
            status = "alive",
            metTurn = 1,
            lastSeenTurn = 2
        )
        val state = GameStateFixture.baseState(npcLog = listOf(npc), turns = 5)
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder()
            .addNpcDeath("prosper-saltblood")
            .build()
        val result = vm.applyParsed(state, char, parsed, "I strike", roll = 20, mod = 3, prof = 2)

        // NPC log entry should be marked dead
        assertEquals(1, result.npcLog.size)
        assertEquals("dead", result.npcLog[0].status)

        // A System message containing the display name should be in messages
        val systemMessages = result.messages.filterIsInstance<DisplayMessage.System>()
        assertTrue(
            "Expected a System message containing 'Prosper Saltblood'",
            systemMessages.any { it.text.contains("Prosper Saltblood") }
        )
    }

    // -------------------------------------------------------------------------
    // Test 5: Quest lifecycle — start, update, complete
    // -------------------------------------------------------------------------
    @Test
    fun `quest lifecycle start update complete`() {
        // --- Phase 1: Start ---
        val state0 = GameStateFixture.baseState(quests = emptyList(), turns = 0)
        val char0 = state0.character!!
        val vm = GameStateFixture.viewModelWithState(state0)

        val newQuest = Quest(
            id = "q-find-the-cup",
            title = "Find the Cup",
            type = "side",
            desc = "Retrieve the sacred cup.",
            giver = "Elder",
            location = "Testtown",
            objectives = mutableListOf("Locate the cup"),
            reward = "50 gold",
            turnStarted = 1
        )
        val parsedStart = ParsedReplyBuilder().addQuestStart(newQuest).build()
        val state1 = vm.applyParsed(state0, char0, parsedStart, "I accept", roll = 10, mod = 0, prof = 0)

        assertEquals(1, state1.quests.size)
        assertEquals("active", state1.quests[0].status)

        // --- Phase 2: Update ---
        GameStateFixture.injectState(vm, state1)
        val char1 = state1.character!!

        val parsedUpdate = ParsedReplyBuilder()
            .addQuestUpdate("Find the Cup", "Visit the old ruins")
            .build()
        val state2 = vm.applyParsed(state1, char1, parsedUpdate, "I investigate", roll = 12, mod = 0, prof = 0)

        // The new objective should be appended and marked complete
        val quest2 = state2.quests[0]
        assertTrue("Quest should contain the new objective",
            quest2.objectives.any { it.equals("Visit the old ruins", true) })

        // --- Phase 3: Complete ---
        GameStateFixture.injectState(vm, state2)
        val char2 = state2.character!!

        val parsedComplete = ParsedReplyBuilder()
            .addQuestComplete("Find the Cup")
            .build()
        val state3 = vm.applyParsed(state2, char2, parsedComplete, "I return it", roll = 10, mod = 0, prof = 0)

        assertEquals("completed", state3.quests[0].status)
    }

    // -------------------------------------------------------------------------
    // Test 6: Shop open via parsed.shops populates availableMerchants
    // -------------------------------------------------------------------------
    @Test
    fun `shop open via parsed shops populates availableMerchants`() {
        val state = GameStateFixture.baseState(turns = 2)
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val items = mapOf("Iron Sword" to 15, "Health Potion" to 8)
        val parsed = ParsedReplyBuilder()
            .addShop("Vesper's Wares", items)
            .build()
        val result = vm.applyParsed(state, char, parsed, "I browse", roll = 10, mod = 0, prof = 0)

        // Current behavior: shop is added to availableMerchants and merchantStocks
        assertTrue("availableMerchants should contain the shop",
            result.availableMerchants.contains("Vesper's Wares"))
        assertNotNull(result.merchantStocks["Vesper's Wares"])
        assertEquals(15, result.merchantStocks["Vesper's Wares"]?.get("Iron Sword"))
    }

    // -------------------------------------------------------------------------
    // Test 7: TRAVEL tag updates current location when world map is present
    // -------------------------------------------------------------------------
    @Test
    fun `TRAVEL tag updates current location`() {
        val loc0 = com.realmsoffate.game.data.MapLocation(0, "Testtown", "town", "🏘", 100, 100, discovered = true)
        val loc1 = com.realmsoffate.game.data.MapLocation(1, "Farkeep", "town", "🏰", 300, 300, discovered = false)
        val worldMap = com.realmsoffate.game.data.WorldMap(
            locations = mutableListOf(loc0, loc1),
            roads = emptyList(),
            startId = 0,
            terrain = emptyList(),
            rivers = emptyList(),
            lakes = emptyList()
        )

        val state = GameUiState(
            character = GameStateFixture.character(),
            worldMap = worldMap,
            currentLoc = 0,
            turns = 3
        )
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder()
            .narration("You arrive at the gates.")
            .travelTo("Farkeep")
            .build()
        val result = vm.applyParsed(state, char, parsed, "I go to Farkeep", roll = 5, mod = 0, prof = 0)

        assertEquals(1, result.currentLoc)
        assertTrue(result.worldMap!!.locations[1].discovered)
        assertEquals(300f, result.playerPos!!.x)
        assertEquals(300f, result.playerPos!!.y)
    }

    // -------------------------------------------------------------------------
    // Test 8: Faction update changes faction field
    // -------------------------------------------------------------------------
    @Test
    fun `faction update changes faction field`() {
        val faction = Faction(
            id = "the-cobalt-eclipse",
            name = "The Cobalt Eclipse",
            type = "guild",
            description = "A secretive guild.",
            baseLoc = "Testtown",
            disposition = "neutral"
        )
        val state = GameStateFixture.baseState(factions = listOf(faction), turns = 2)
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        val parsed = ParsedReplyBuilder()
            .addFactionUpdate("the-cobalt-eclipse", "disposition", "oppressive")
            .build()
        val result = vm.applyParsed(state, char, parsed, "I witness their cruelty", roll = 10, mod = 0, prof = 0)

        val updatedFaction = result.worldLore?.factions?.firstOrNull { it.id == "the-cobalt-eclipse" }
        assertNotNull("Faction should still exist", updatedFaction)
        // Current behavior: disposition field is updated by the faction_update handler
        assertEquals("oppressive", updatedFaction?.disposition)
    }

    // -------------------------------------------------------------------------
    // Quest objective substring dedup
    // -------------------------------------------------------------------------

    @Test fun `quest objective superset replaces existing and marks complete`() {
        val quest = Quest(
            id = "q-the-rift",
            title = "The Rift",
            desc = "Investigate the rift.",
            giver = "Scholar",
            reward = "50 gold",
            turnStarted = 1,
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
            id = "q-the-rift",
            title = "The Rift",
            desc = "Investigate the rift.",
            giver = "Scholar",
            reward = "50 gold",
            turnStarted = 1,
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

    // -------------------------------------------------------------------------
    // Parser Phase C: NPC name substitution in narration
    // -------------------------------------------------------------------------

    @Test fun `narration with NPC slug ID gets display name substituted`() {
        val npc = LogNpc(id = "vesper-saltblood", name = "Vesper Saltblood", race = "Human", role = "Bartender", metTurn = 1, lastSeenTurn = 1)
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

    // -------------------------------------------------------------------------
    // Level-up preserves damage taken in same turn
    // -------------------------------------------------------------------------

    @Test fun `level-up preserves damage taken in same turn`() {
        val state = GameStateFixture.baseState(
            character = GameStateFixture.character(
                hp = 10, maxHp = 10, xp = 290, level = 1
            )
        )
        val char = state.character!!
        val vm = GameStateFixture.viewModelWithState(state)

        // Deal 5 damage AND grant enough XP to level up (threshold for level 2 is 300)
        val parsed = ParsedReplyBuilder()
            .narration("You take a hit but grow stronger.")
            .damage(5)
            .xp(20)
            .build()

        val result = vm.applyParsed(state, char, parsed, "I fight", roll = 10, mod = 0, prof = 0)

        val c = result.character!!
        assertEquals("should be level 2", 2, c.level)
        assertTrue("maxHp should have increased", c.maxHp > 10)
        assertTrue("hp should NOT be at maxHp due to damage", c.hp < c.maxHp)
        // hp = (10 - 5) + hpGain = 5 + hpGain, which is less than maxHp = 10 + hpGain
        assertEquals("hp should be maxHp minus 5 damage", c.maxHp - 5, c.hp)
    }

    @Test fun `narration without NPC refs is unchanged`() {
        val npc = LogNpc(id = "vesper-saltblood", name = "Vesper Saltblood", race = "Human", role = "Bartender", metTurn = 1, lastSeenTurn = 1)
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
}
