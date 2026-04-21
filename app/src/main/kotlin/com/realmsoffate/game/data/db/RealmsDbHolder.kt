package com.realmsoffate.game.data.db

import android.content.Context
import com.realmsoffate.game.data.RoomEntityRepository
import java.io.File

/**
 * App-scoped singleton for the narrative [RealmsDb]. Initialized from
 * [com.realmsoffate.game.RealmsApp.onCreate] so the ViewModel factory can
 * reach the repository without threading it through constructors.
 */
object RealmsDbHolder {
    @Volatile private var _db: RealmsDb? = null
    @Volatile private var _repo: RoomEntityRepository? = null

    fun init(context: Context) {
        if (_db != null) return
        synchronized(this) {
            if (_db != null) return
            val dbFile = File(context.filesDir, RealmsDb.FILE_NAME)
            val opened = RealmsDb.open(context.applicationContext, dbFile)
            _db = opened
            _repo = RoomEntityRepository(opened)
        }
    }

    val db: RealmsDb get() = _db ?: error("RealmsDb not initialized — call RealmsDbHolder.init(context) first")
    val repo: RoomEntityRepository get() = _repo ?: error("RealmsDbHolder not initialized")
}
