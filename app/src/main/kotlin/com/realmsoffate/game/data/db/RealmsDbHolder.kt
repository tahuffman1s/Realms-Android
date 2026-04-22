package com.realmsoffate.game.data.db

import android.content.Context
import com.realmsoffate.game.data.RoomEntityRepository
import java.io.File

/**
 * App-scoped singleton for the narrative [RealmsDb]. Initialized once from
 * [com.realmsoffate.game.RealmsApp.onCreate]. [switchTo] must be called before
 * each save load / new-game commit / save-slot delete so the per-slot file is
 * active. [deleteSlotDb] is safe on any slot (including the current one).
 *
 * Thread contract: [switchTo] and [deleteSlotDb] are serialized via an internal
 * monitor. [db] / [repo] getters are not safe to read concurrently with
 * [switchTo] on the same call site — treat a swap as a barrier and re-read
 * after it completes.
 */
object RealmsDbHolder {
    @Volatile private var appCtx: Context? = null
    @Volatile private var _db: RealmsDb? = null
    @Volatile private var _repo: RoomEntityRepository? = null
    @Volatile private var _currentSlot: String = "default"
    @Volatile private var _currentFile: File? = null

    fun init(context: Context) {
        if (appCtx != null) return
        synchronized(this) {
            if (appCtx != null) return
            appCtx = context.applicationContext
            switchToLocked("default")
        }
    }

    fun switchTo(slot: String) {
        synchronized(this) { switchToLocked(slot) }
    }

    private fun switchToLocked(slot: String) {
        val ctx = appCtx ?: error("RealmsDbHolder.init must be called first")
        if (slot == _currentSlot && _db != null) return
        _db?.close()
        val file = File(ctx.filesDir, RealmsDb.fileNameForSlot(slot))
        val opened = RealmsDb.open(ctx, file)
        _db = opened
        _repo = RoomEntityRepository(opened)
        _currentSlot = slot
        _currentFile = file
    }

    fun currentSlot(): String = _currentSlot
    fun currentDbFile(): File = _currentFile ?: error("RealmsDbHolder not initialized")

    fun deleteSlotDb(slot: String) {
        val ctx = appCtx ?: return
        val file = File(ctx.filesDir, RealmsDb.fileNameForSlot(slot))
        synchronized(this) {
            if (file.absolutePath == _currentFile?.absolutePath) {
                _db?.close(); _db = null; _repo = null
            }
        }
        file.delete()
        File(file.absolutePath + "-shm").delete()
        File(file.absolutePath + "-wal").delete()
    }

    /** Returns the current live [RealmsDb]. Not safe to read concurrently with [switchTo]. */
    val db: RealmsDb get() = _db ?: error("RealmsDb not initialized — call RealmsDbHolder.init(context) first")

    /** Returns the current live [RoomEntityRepository]. Not safe to read concurrently with [switchTo]. */
    val repo: RoomEntityRepository get() = _repo ?: error("RealmsDbHolder not initialized")

    @androidx.annotation.VisibleForTesting
    internal fun resetForTest() {
        synchronized(this) {
            _db?.close()
            _db = null
            _repo = null
            _currentFile = null
            _currentSlot = "default"
            appCtx = null
        }
    }
}
