package com.realmsoffate.game.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Thumbnail of a save slot — used to populate the title screen's Load menu
 * without deserializing the full SaveData for each entry.
 */
@Serializable
data class SaveSlotMeta(
    val slot: String,
    val characterName: String,
    val race: String,
    val cls: String,
    val level: Int,
    val turns: Int,
    val worldName: String,
    val scene: String,
    val savedAt: String
)

/**
 * Tombstone for dead characters — shown on the graveyard screen with enough
 * detail to recreate the BitLife-style death view without keeping the full
 * save file around.
 */
@Serializable
data class GraveyardEntry(
    val characterName: String,
    val race: String,
    val cls: String,
    val level: Int,
    val turns: Int,
    val xp: Int,
    val gold: Int,
    val morality: Int,
    val worldName: String,
    val mutations: List<String>,
    val companions: List<String>,
    val backstoryText: String?,
    val causeOfDeath: String,
    val timeline: List<TimelineEntry>,
    val diedAt: String
)

@Serializable
data class TimelineEntry(
    val turn: Int,
    val category: String, // "birth", "levelup", "quest", "travel", "event", "death"
    val text: String
)

/**
 * JSON-file save store. Writes live in app-private files/saves/ and graveyard/.
 * Active save is always the "autosave" slot + character-named slot; death moves
 * a slot into the graveyard directory (capped at 20 entries).
 */
object SaveStore {
    private var appContext: Context? = null
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private const val MAX_SLOTS = 10
    private const val MAX_GRAVES = 20
    private const val AUTOSAVE_KEY = "autosave"

    fun init(ctx: Context) { appContext = ctx.applicationContext }

    private fun ctx(): Context = appContext ?: error("SaveStore not initialized")

    private fun saveDir(): File {
        val dir = File(ctx().filesDir, "saves")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun graveDir(): File {
        val dir = File(ctx().filesDir, "graveyard")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun slotFile(slot: String) = File(saveDir(), "slot_${slot}.json")
    private fun graveFile(name: String) = File(graveDir(), "grave_${name}.json")

    /** Sanitises a character name for use as a slot key. */
    fun slotKeyFor(characterName: String): String =
        characterName.trim().lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .ifBlank { "adventurer" }

    // ---------------- SAVE ----------------

    suspend fun write(slot: String, data: SaveData): Unit = withContext(Dispatchers.IO) {
        slotFile(slot).writeText(json.encodeToString(data))
    }

    suspend fun read(slot: String): SaveData? = withContext(Dispatchers.IO) {
        val f = slotFile(slot)
        if (!f.exists()) return@withContext null
        runCatching { json.decodeFromString<SaveData>(f.readText()) }.getOrNull()?.let { migrateIds(it) }
    }

    /** Remove a save slot (also clears autosave if the slot names match). */
    suspend fun delete(slot: String): Unit = withContext(Dispatchers.IO) {
        slotFile(slot).delete()
    }

    /** Returns slot metadata for the title-screen Load menu, newest first. */
    suspend fun listSlots(): List<SaveSlotMeta> = withContext(Dispatchers.IO) {
        val files = saveDir().listFiles()?.filter {
            it.isFile && it.name.startsWith("slot_") && it.name.endsWith(".json")
        }.orEmpty()
        files.mapNotNull { f ->
            val slot = f.name.removePrefix("slot_").removeSuffix(".json")
            if (slot.isBlank()) return@mapNotNull null
            val data = runCatching { json.decodeFromString<SaveData>(f.readText()) }.getOrNull()
                ?: return@mapNotNull null
            SaveSlotMeta(
                slot = slot,
                characterName = data.character.name,
                race = data.character.race,
                cls = data.character.cls,
                level = data.character.level,
                turns = data.turns,
                worldName = data.worldMap.locations.firstOrNull()?.name.orEmpty(),
                scene = data.scene,
                savedAt = data.savedAt
            )
        }.sortedByDescending { it.savedAt }
    }

    // ---------------- GRAVEYARD ----------------

    suspend fun bury(entry: GraveyardEntry): Unit = withContext(Dispatchers.IO) {
        val name = slotKeyFor(entry.characterName) + "_" + System.currentTimeMillis()
        graveFile(name).writeText(json.encodeToString(entry))
        // Cap graveyard size
        val graves = graveDir().listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        graves.drop(MAX_GRAVES).forEach { it.delete() }
    }

    suspend fun listGraves(): List<GraveyardEntry> = withContext(Dispatchers.IO) {
        val files = graveDir().listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
        files.mapNotNull { f ->
            runCatching { json.decodeFromString<GraveyardEntry>(f.readText()) }.getOrNull()
        }.sortedByDescending { it.diedAt }
    }

    suspend fun exhume(entry: GraveyardEntry): Unit = withContext(Dispatchers.IO) {
        val files = graveDir().listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
        files.forEach { f ->
            val loaded = runCatching { json.decodeFromString<GraveyardEntry>(f.readText()) }.getOrNull()
            if (loaded?.characterName == entry.characterName && loaded.diedAt == entry.diedAt) {
                f.delete()
            }
        }
    }

    // ---------------- IMPORT / EXPORT ----------------

    /** Serialises a save to a JSON string (for share / export). */
    fun toJson(data: SaveData): String = json.encodeToString(data)

    /** Parses a JSON string into a SaveData; null on parse failure. */
    fun fromJson(s: String): SaveData? =
        runCatching { json.decodeFromString<SaveData>(s) }.getOrNull()?.let { migrateIds(it) }

    /**
     * Assigns stable IDs to any [Faction], [LoreNpc], or [LogNpc] whose id is blank.
     * Idempotent: entries that already have an ID are left untouched.
     * Collision-safe: uses separate namespaces for factions and NPCs.
     * Name-matching: npcLog entries whose name matches a worldLore.npcs entry
     * (case-insensitive) reuse that entry's ID instead of generating a new one.
     */
    private fun migrateIds(data: SaveData): SaveData {
        val lore = data.worldLore

        // --- 1. Factions (separate namespace) ---
        val factionIds = mutableSetOf<String>()
        val migratedFactions = lore?.factions?.map { faction ->
            if (faction.id.isNotBlank()) {
                factionIds += faction.id
                faction
            } else {
                val newId = IdGen.forName(faction.name, factionIds)
                factionIds += newId
                faction.copy(id = newId)
            }
        }

        // --- 2. LoreNpcs (separate namespace) ---
        val loreNpcIds = mutableSetOf<String>()
        // Map from lowercase name -> assigned ID, for log matching below
        val loreNpcNameToId = mutableMapOf<String, String>()
        val migratedLoreNpcs = lore?.npcs?.map { npc ->
            if (npc.id.isNotBlank()) {
                loreNpcIds += npc.id
                loreNpcNameToId[npc.name.lowercase()] = npc.id
                npc
            } else {
                val newId = IdGen.forName(npc.name, loreNpcIds)
                loreNpcIds += newId
                loreNpcNameToId[npc.name.lowercase()] = newId
                npc.copy(id = newId)
            }
        }

        // --- 3. LogNpcs (namespace shared with loreNpcIds for dedup) ---
        val logNpcIds = mutableSetOf<String>()
        val migratedLogNpcs = data.npcLog.map { logNpc ->
            if (logNpc.id.isNotBlank()) {
                logNpcIds += logNpc.id
                logNpc
            } else {
                // Reuse the lore NPC id if names match (case-insensitive)
                val existingLoreId = loreNpcNameToId[logNpc.name.lowercase()]
                val newId = existingLoreId
                    ?: IdGen.forName(logNpc.name, loreNpcIds + logNpcIds)
                logNpcIds += newId
                logNpc.copy(id = newId)
            }
        }

        val migratedLore = lore?.copy(
            factions = migratedFactions ?: lore.factions,
            npcs = migratedLoreNpcs ?: lore.npcs
        )

        return data.copy(
            worldLore = migratedLore,
            npcLog = migratedLogNpcs
        )
    }
}
