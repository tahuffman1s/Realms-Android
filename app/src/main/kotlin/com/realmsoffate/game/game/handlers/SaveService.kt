package com.realmsoffate.game.game.handlers

import com.realmsoffate.game.data.DebugTurn
import com.realmsoffate.game.data.GraveyardEntry
import com.realmsoffate.game.data.SaveData
import com.realmsoffate.game.data.sanitizeDisplayName
import com.realmsoffate.game.data.SaveSlotMeta
import com.realmsoffate.game.data.SaveStore
import com.realmsoffate.game.data.SerializedBuyback
import com.realmsoffate.game.data.TimelineEntry
import com.realmsoffate.game.game.*
import com.realmsoffate.game.ui.overlays.BuybackEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SaveService(
    private val ui: MutableStateFlow<GameUiState>,
    private val screen: MutableStateFlow<Screen>,
    private val saveSlots: MutableStateFlow<List<SaveSlotMeta>>,
    private val graveyard: MutableStateFlow<List<GraveyardEntry>>,
    private val buybackStocks: MutableStateFlow<Map<String, List<BuybackEntry>>>,
    private val debugLog: MutableList<DebugTurn>,
    private val timeline: MutableList<TimelineEntry>,
    private val scope: CoroutineScope,
    // Callbacks for clearing ephemeral overlays owned by other handlers
    private val clearOverlays: () -> Unit,
    /** Provides the current arc summaries at save time (pulled from Room). */
    private val arcSummaryProvider: suspend () -> List<com.realmsoffate.game.data.ArcSummary> = { emptyList() }
) {

    val saveSlotsMeta: StateFlow<List<SaveSlotMeta>> = saveSlots.asStateFlow()
    val graveyardEntries: StateFlow<List<GraveyardEntry>> = graveyard.asStateFlow()

    fun saveToSlot(slot: String = "autosave") {
        scope.launch {
            SaveStore.write(slot, snapshotSaveData() ?: return@launch)
        }
    }

    /**
     * Captures every piece of state the player should see when reloading:
     * character (with conditions), world, NPC dialogue history,
     * timeline, conditions, in-flight shop/buyback, weather, accumulator, the
     * full chat-feed display messages, and the AI conversation history.
     */
    private suspend fun snapshotSaveData(): SaveData? {
        val s = ui.value
        val ch = s.character ?: return null
        val wm = s.worldMap ?: return null
        val buyback = buybackStocks.value.mapValues { entry ->
            entry.value.map { SerializedBuyback(item = it.item, price = it.price) }
        }
        return SaveData(
            character = ch,
            morality = s.morality,
            factionRep = s.factionRep,
            worldMap = wm,
            currentLoc = s.currentLoc,
            playerPos = s.playerPos,
            worldLore = s.worldLore,
            worldEvents = s.worldEvents,
            lastEventTurn = s.lastEventTurn,
            npcLog = s.npcLog,
            party = s.party,
            quests = s.quests,
            hotbar = s.hotbar,
            history = s.history,
            turns = s.turns,
            scene = s.currentScene,
            savedAt = java.time.Instant.now().toString(),
            sceneDesc = s.currentSceneDesc,
            merchantStocks = s.merchantStocks,
            availableMerchants = s.availableMerchants,
            buybackStocks = buyback,
            currentChoices = s.currentChoices,
            timeline = timeline.toList(),
            displayMessages = s.messages,
            deathSave = s.deathSave,
            debugLog = debugLog.takeLast(50),
            sceneSummaries = s.sceneSummaries,
            arcSummaries = runCatching { arcSummaryProvider() }.getOrDefault(emptyList())
        )
    }

    fun loadSlot(slot: String = "autosave") {
        scope.launch {
            val d = SaveStore.read(slot) ?: return@launch
            // Restore the in-memory timeline so death-screen reads the full life.
            timeline.clear()
            timeline.addAll(d.timeline)
            // Restore buyback stocks from the persisted form.
            buybackStocks.value = d.buybackStocks.mapValues { e ->
                e.value.map { BuybackEntry(item = it.item, price = it.price) }
            }
            // Restore the debug log so reload → dump still shows historical cache/source data.
            debugLog.clear()
            debugLog.addAll(d.debugLog)
            ui.value = GameUiState(
                character = d.character,
                worldMap = d.worldMap,
                currentLoc = d.currentLoc,
                playerPos = d.playerPos,
                worldLore = d.worldLore,
                worldEvents = d.worldEvents,
                lastEventTurn = d.lastEventTurn,
                npcLog = d.npcLog.map { it.sanitizeDisplayName() },
                party = d.party,
                quests = d.quests,
                hotbar = d.hotbar,
                morality = d.morality,
                factionRep = d.factionRep,
                history = d.history,
                turns = d.turns,
                currentScene = d.scene,
                currentSceneDesc = d.sceneDesc,
                merchantStocks = d.merchantStocks,
                availableMerchants = d.availableMerchants,
                currentChoices = d.currentChoices,
                // Restore the rendered chat feed if the save has it; otherwise show
                // a single "loaded" line so the player has context.
                messages = if (d.displayMessages.isNotEmpty()) {
                    d.displayMessages + DisplayMessage.System("Loaded — ${d.character.name}, turn ${d.turns}")
                } else {
                    listOf(DisplayMessage.System("Loaded — ${d.character.name}, turn ${d.turns}"))
                },
                turnStartIndex = if (d.displayMessages.isNotEmpty()) d.displayMessages.lastIndex else 0,
                // Restore in-flight death-save tracker so reload picks the player up
                // exactly where they fell.
                deathSave = d.deathSave,
                sceneSummaries = d.sceneSummaries
            )
            // Clear any stale ephemeral overlays so they don't leak into the loaded run.
            clearOverlays()
            screen.value = Screen.Game
        }
    }

    /** Import a SaveData that came from a shared JSON file. */
    fun importSave(json: String) {
        scope.launch {
            val data = SaveStore.fromJson(json) ?: return@launch
            val slot = SaveStore.slotKeyFor(data.character.name)
            SaveStore.write(slot, data)
            refreshSlots()
        }
    }

    /** Serialises the live in-memory state to a JSON string for export. */
    fun exportCurrentJson(): String? = kotlinx.coroutines.runBlocking {
        snapshotSaveData()?.let { SaveStore.toJson(it) }
    }

    /** Suggested filename for a save export, e.g. `Kaelis_L4_turn31.json`. */
    fun exportFilename(): String {
        val ch = ui.value.character ?: return "save.json"
        val safe = SaveStore.slotKeyFor(ch.name)
        return "${safe}_L${ch.level}_turn${ui.value.turns}.json"
    }

    fun debugDumpFilename(): String {
        val ch = ui.value.character
        val name = ch?.let { SaveStore.slotKeyFor(it.name) } ?: "debug"
        return "debug_${name}_T${ui.value.turns}_${System.currentTimeMillis() / 1000}.txt"
    }

    fun refreshSlots() {
        scope.launch {
            saveSlots.value = SaveStore.listSlots()
            graveyard.value = SaveStore.listGraves()
        }
    }

    fun deleteSlot(slot: String) {
        scope.launch {
            SaveStore.delete(slot)
            refreshSlots()
        }
    }

    fun exhumeGrave(entry: GraveyardEntry) {
        scope.launch {
            SaveStore.exhume(entry)
            refreshSlots()
        }
    }
}
