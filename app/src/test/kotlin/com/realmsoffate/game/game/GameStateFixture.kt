package com.realmsoffate.game.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.realmsoffate.game.data.AiRepository
import com.realmsoffate.game.data.Character
import com.realmsoffate.game.data.Faction
import com.realmsoffate.game.data.Item
import com.realmsoffate.game.data.Lake
import com.realmsoffate.game.data.LogNpc
import com.realmsoffate.game.data.LoreNpc
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.data.MapRoad
import com.realmsoffate.game.data.PartyCompanion
import com.realmsoffate.game.data.PreferencesStore
import com.realmsoffate.game.data.Quest
import com.realmsoffate.game.data.WorldLore
import com.realmsoffate.game.data.WorldMap
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test-only fixture helpers for GameViewModel integration tests.
 *
 * The reflective state injection is intentionally scoped to [injectState]
 * so test bodies stay clean. All reflection lives here, not in the tests.
 */
object GameStateFixture {

    fun character(
        name: String = "Test Hero",
        race: String = "Human",
        cls: String = "Fighter",
        level: Int = 1,
        hp: Int = 10,
        maxHp: Int = 10,
        ac: Int = 13,
        gold: Int = 25,
        xp: Int = 0,
        inventory: List<Item> = emptyList(),
        conditions: List<String> = emptyList()
    ): Character = Character(
        name = name,
        race = race,
        cls = cls,
        level = level,
        hp = hp,
        maxHp = maxHp,
        ac = ac,
        gold = gold,
        xp = xp,
        inventory = inventory.toMutableList(),
        conditions = conditions.toMutableList()
    )

    fun baseState(
        character: Character = character(),
        currentLoc: Int = 0,
        npcLog: List<LogNpc> = emptyList(),
        quests: List<Quest> = emptyList(),
        party: List<PartyCompanion> = emptyList(),
        factions: List<Faction> = emptyList(),
        turns: Int = 0,
        morality: Int = 0,
        messages: List<DisplayMessage> = emptyList()
    ): GameUiState {
        val worldMap = singleLocationWorldMap()
        val worldLore = minimalLore(factions = factions)
        return GameUiState(
            character = character,
            worldMap = worldMap,
            currentLoc = currentLoc,
            worldLore = worldLore,
            npcLog = npcLog,
            quests = quests,
            party = party,
            turns = turns,
            morality = morality,
            messages = messages
        )
    }

    /**
     * Instantiates a real GameViewModel under Robolectric with [initialState]
     * pre-loaded via reflection on the private [_ui] MutableStateFlow.
     *
     * Using a real VM (not a mock) is intentional — we need applyParsed's actual
     * side-effects on [pendingLevelUp], [_pendingFeat], and [_pendingStatPoints].
     *
     * The reflective injection is the only way to seed state without calling the
     * full startNewGame() path (which involves coroutines, WorldGen, LoreGen, etc.).
     */
    fun viewModelWithState(initialState: GameUiState): GameViewModel {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesStore(ctx)
        val ai = AiRepository()
        val vm = GameViewModel(ai, prefs, com.realmsoffate.game.data.CheatsStore(ctx))
        injectState(vm, initialState)
        return vm
    }

    fun singleLocationWorldMap(locName: String = "Testtown", type: String = "town"): WorldMap {
        val loc = MapLocation(
            id = 0,
            name = locName,
            type = type,
            icon = "🏘",
            x = 100,
            y = 100,
            discovered = true
        )
        return WorldMap(
            locations = mutableListOf(loc),
            roads = emptyList<MapRoad>(),
            startId = 0,
            terrain = emptyList(),
            rivers = emptyList(),
            lakes = emptyList<Lake>()
        )
    }

    fun minimalLore(
        factions: List<Faction> = emptyList(),
        npcs: List<LoreNpc> = emptyList()
    ): WorldLore = WorldLore(
        factions = factions,
        npcs = npcs,
        primordial = emptyList(),
        mutations = emptyList(),
        worldName = "Testworld",
        era = "Present"
    )

    // -------------------------------------------------------------------------
    // Reflection helpers — ONLY used here, never directly in test bodies.
    // -------------------------------------------------------------------------

    /**
     * Reflectively injects [state] into the VM's private [_ui] MutableStateFlow.
     * This is the cost of testing a real ViewModel without the full startNewGame()
     * pipeline. Scoped here so no test body ever calls getDeclaredField directly.
     */
    fun injectState(vm: GameViewModel, state: GameUiState) {
        val field = GameViewModel::class.java.getDeclaredField("_ui")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as MutableStateFlow<GameUiState>).value = state
    }

    /**
     * Reads the [pendingLevelUp] backing property value via reflection.
     * pendingLevelUp is a private var backed by _pendingLevelUp: MutableStateFlow<Int?>.
     */
    fun getPendingLevelUp(vm: GameViewModel): Int? {
        val field = GameViewModel::class.java.getDeclaredField("_pendingLevelUp")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(vm) as MutableStateFlow<Int?>).value
    }

    /**
     * Reads [_pendingFeat] via reflection.
     */
    fun getPendingFeat(vm: GameViewModel): Boolean {
        val field = GameViewModel::class.java.getDeclaredField("_pendingFeat")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(vm) as MutableStateFlow<Boolean>).value
    }

    /**
     * Reads [_pendingStatPoints] via reflection.
     */
    fun getPendingStatPoints(vm: GameViewModel): Int {
        val field = GameViewModel::class.java.getDeclaredField("_pendingStatPoints")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(vm) as MutableStateFlow<Int>).value
    }
}
