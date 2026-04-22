package com.realmsoffate.game.data

import android.content.Context
import com.realmsoffate.game.data.db.RealmsDb
import com.realmsoffate.game.data.db.RealmsDbHolder
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

/** Manifest persisted as `manifest.json` inside each `.rofsave` zip. */
@Serializable
data class SaveManifest(
    val version: Int = 3,
    val slot: String,
    val characterName: String,
    val race: String,
    val cls: String,
    val level: Int,
    val turns: Int,
    val worldName: String,
    val scene: String,
    val savedAt: String
) {
    fun toMeta(): SaveSlotMeta = SaveSlotMeta(
        slot = slot, characterName = characterName, race = race, cls = cls,
        level = level, turns = turns, worldName = worldName, scene = scene, savedAt = savedAt
    )
}

/**
 * Save-slot store.
 *
 * Active format (v3) is a `.rofsave` zip with `manifest.json` + `save.json`.
 * Legacy `.json` files (v2) still load; the next write for that slot upgrades
 * to a `.rofsave` and removes the old JSON.
 *
 * Graveyard tombstones continue to live as standalone `.json` files — they
 * are small and read-only after burial, so no zip wrapping is needed.
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
    internal const val AUTOSAVE_KEY = "autosave"

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

    private fun rofFile(slot: String) = File(saveDir(), "slot_${slot}.rofsave")
    private fun legacyJsonFile(slot: String) = File(saveDir(), "slot_${slot}.json")
    private fun legacyBackup(slot: String) = File(saveDir(), "slot_${slot}.v2.bak.json")
    private fun graveFile(name: String) = File(graveDir(), "grave_${name}.json")

    /** Sanitises a character name for use as a slot key. */
    fun slotKeyFor(characterName: String): String =
        characterName.trim().lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .ifBlank { "adventurer" }

    // ---------------- SAVE ----------------

    suspend fun write(slot: String, data: SaveData): Unit = withContext(Dispatchers.IO) {
        val rof = rofFile(slot)
        val manifest = buildManifest(slot, data)
        val manifestJson = json.encodeToString(manifest)
        val saveJson = json.encodeToString(data)

        // Collect DB sibling files. RealmsDbHolder.switchTo was called (A2) before
        // write runs, so currentDbFile() already points at the correct per-character file.
        val dbFile = RealmsDbHolder.currentDbFile()
        val walFile = File(dbFile.absolutePath + "-wal")
        val shmFile = File(dbFile.absolutePath + "-shm")
        val dbEntries = mutableMapOf<String, File>()
        if (dbFile.exists()) dbEntries[SaveRofZip.REALMS_DB] = dbFile
        if (walFile.exists()) dbEntries[SaveRofZip.REALMS_DB_WAL] = walFile
        if (shmFile.exists()) dbEntries[SaveRofZip.REALMS_DB_SHM] = shmFile

        // Write to a .tmp sibling first so an interrupted write can't corrupt the slot.
        val tmp = File(rof.parentFile, rof.name + ".tmp")
        SaveRofZip.writeMixed(
            out = tmp,
            textEntries = mapOf(
                SaveRofZip.MANIFEST to manifestJson,
                SaveRofZip.SAVE_JSON to saveJson
            ),
            fileEntries = dbEntries
        )
        if (rof.exists()) rof.delete()
        if (!tmp.renameTo(rof)) {
            tmp.copyTo(rof, overwrite = true)
            tmp.delete()
        }
        // Kill any stale v2 JSON so we never double-list this slot.
        legacyJsonFile(slot).delete()
    }

    suspend fun read(slot: String): SaveData? = withContext(Dispatchers.IO) {
        val rof = rofFile(slot)
        if (rof.exists()) {
            val body = SaveRofZip.readTextEntry(rof, SaveRofZip.SAVE_JSON)
                ?: return@withContext null
            val parsed = runCatching {
                json.decodeFromString<SaveData>(body)
            }.getOrNull()?.let { migrateIds(it) } ?: return@withContext null

            // Extract DB payload if present (legacy saves have no db entry — skip silently).
            SaveRofZip.readBinaryEntry(rof, SaveRofZip.REALMS_DB)?.let { dbBytes ->
                val targetSlot = slotKeyFor(parsed.character.name)
                // Release Room's hold on the target file before overwriting it.
                RealmsDbHolder.closeSlotIfOpen(targetSlot)
                val target = File(ctx().filesDir, RealmsDb.fileNameForSlot(targetSlot))
                target.outputStream().use { it.write(dbBytes) }
                SaveRofZip.readBinaryEntry(rof, SaveRofZip.REALMS_DB_WAL)?.let { b ->
                    File(target.absolutePath + "-wal").outputStream().use { it.write(b) }
                }
                SaveRofZip.readBinaryEntry(rof, SaveRofZip.REALMS_DB_SHM)?.let { b ->
                    File(target.absolutePath + "-shm").outputStream().use { it.write(b) }
                }
                // If sidecars were absent in the zip but exist on disk, remove them so
                // SQLite doesn't attempt to reconcile against stale WAL content.
                if (SaveRofZip.readBinaryEntry(rof, SaveRofZip.REALMS_DB_WAL) == null) {
                    File(target.absolutePath + "-wal").delete()
                }
                if (SaveRofZip.readBinaryEntry(rof, SaveRofZip.REALMS_DB_SHM) == null) {
                    File(target.absolutePath + "-shm").delete()
                }
            }

            return@withContext parsed
        }
        // Legacy v2 fallback. The next write will upgrade this slot to .rofsave.
        val legacy = legacyJsonFile(slot)
        if (legacy.exists()) {
            return@withContext runCatching {
                json.decodeFromString<SaveData>(legacy.readText())
            }.getOrNull()?.let { migrateIds(it) }
        }
        null
    }

    /**
     * Remove a save slot and every variant (active .rofsave, legacy .json,
     * migration backup, plus an autosave sibling if it belongs to the same
     * character). This fixes the "save reappears after delete" bug where each
     * save wrote both a character-keyed slot AND the autosave slot; deleting
     * only one left the other orphaned.
     */
    suspend fun delete(slot: String): Unit = withContext(Dispatchers.IO) {
        val characterName = peekCharacterName(slot)
        deleteAllFor(slot)
        if (characterName.isNullOrBlank()) return@withContext
        // Sweep the sibling slot that shares this character.
        val sibling = if (slot == AUTOSAVE_KEY) slotKeyFor(characterName) else AUTOSAVE_KEY
        if (sibling != slot && peekCharacterName(sibling) == characterName) {
            deleteAllFor(sibling)
        }
        // Both JSON slots for this character have been swept (delete() always pairs them).
        // Drop the per-character narrative DB.
        RealmsDbHolder.deleteSlotDb(slotKeyFor(characterName))
    }

    private fun deleteAllFor(slot: String) {
        rofFile(slot).delete()
        legacyJsonFile(slot).delete()
        legacyBackup(slot).delete()
    }

    /**
     * Cheap read of the character name for a slot without deserialising
     * full [SaveData]. Uses manifest.json for v3; reads the JSON only for v2.
     */
    private fun peekCharacterName(slot: String): String? {
        val rof = rofFile(slot)
        if (rof.exists()) {
            val manifestText = SaveRofZip.readTextEntry(rof, SaveRofZip.MANIFEST)
            if (!manifestText.isNullOrBlank()) {
                val m = runCatching { json.decodeFromString<SaveManifest>(manifestText) }.getOrNull()
                if (m != null) return m.characterName
            }
            // Fall through to save.json if manifest is missing (pre-v3 archive).
            val body = SaveRofZip.readTextEntry(rof, SaveRofZip.SAVE_JSON) ?: return null
            return runCatching { json.decodeFromString<SaveData>(body).character.name }.getOrNull()
        }
        val legacy = legacyJsonFile(slot)
        if (legacy.exists()) {
            return runCatching { json.decodeFromString<SaveData>(legacy.readText()).character.name }.getOrNull()
        }
        return null
    }

    /** Returns slot metadata for the title-screen Load menu, newest first. */
    suspend fun listSlots(): List<SaveSlotMeta> = withContext(Dispatchers.IO) {
        val files = saveDir().listFiles()?.filter { it.isFile && it.name.startsWith("slot_") }.orEmpty()
        val bySlot = mutableMapOf<String, SaveSlotMeta>()
        val v2Slots = mutableMapOf<String, SaveSlotMeta>()
        for (f in files) {
            when {
                f.name.endsWith(".rofsave") -> {
                    val slot = f.name.removePrefix("slot_").removeSuffix(".rofsave")
                    if (slot.isBlank()) continue
                    val manifestText = SaveRofZip.readTextEntry(f, SaveRofZip.MANIFEST) ?: continue
                    val manifest = runCatching { json.decodeFromString<SaveManifest>(manifestText) }.getOrNull()
                        ?: continue
                    bySlot[slot] = manifest.toMeta()
                }
                f.name.endsWith(".json") && !f.name.endsWith(".v2.bak.json") -> {
                    val slot = f.name.removePrefix("slot_").removeSuffix(".json")
                    if (slot.isBlank()) continue
                    val data = runCatching { json.decodeFromString<SaveData>(f.readText()) }.getOrNull()
                        ?: continue
                    v2Slots[slot] = SaveSlotMeta(
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
                }
            }
        }
        // v3 wins over v2 for the same slot key.
        for ((slot, meta) in v2Slots) bySlot.putIfAbsent(slot, meta)
        bySlot.values.sortedByDescending { it.savedAt }
    }

    private fun buildManifest(slot: String, data: SaveData): SaveManifest = SaveManifest(
        version = 3,
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

    // ---------------- GRAVEYARD ----------------

    suspend fun bury(entry: GraveyardEntry): Unit = withContext(Dispatchers.IO) {
        val name = slotKeyFor(entry.characterName) + "_" + System.currentTimeMillis()
        graveFile(name).writeText(json.encodeToString(entry))
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
