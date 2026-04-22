package com.realmsoffate.game.data.db

import android.content.Context
import com.realmsoffate.game.data.RoomEntityRepository
import java.io.File

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
        if (file.absolutePath == _currentFile?.absolutePath) {
            _db?.close(); _db = null; _repo = null
        }
        file.delete()
        File(file.absolutePath + "-shm").delete()
        File(file.absolutePath + "-wal").delete()
    }

    val db: RealmsDb get() = _db ?: error("RealmsDb not initialized — call RealmsDbHolder.init(context) first")
    val repo: RoomEntityRepository get() = _repo ?: error("RealmsDbHolder not initialized")
}
