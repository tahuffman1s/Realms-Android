package com.realmsoffate.game.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.realmsoffate.game.data.db.dao.ArcSummaryDao
import com.realmsoffate.game.data.db.dao.FactionDao
import com.realmsoffate.game.data.db.dao.LocationDao
import com.realmsoffate.game.data.db.dao.NpcDao
import com.realmsoffate.game.data.db.dao.QuestDao
import com.realmsoffate.game.data.db.dao.SceneSummaryDao
import com.realmsoffate.game.data.db.entities.ArcSummaryEntity
import com.realmsoffate.game.data.db.entities.FactionEntity
import com.realmsoffate.game.data.db.entities.LocationEntity
import com.realmsoffate.game.data.db.entities.NpcEntity
import com.realmsoffate.game.data.db.entities.QuestEntity
import com.realmsoffate.game.data.db.entities.SceneSummaryEntity

@Database(
    entities = [
        NpcEntity::class,
        QuestEntity::class,
        FactionEntity::class,
        LocationEntity::class,
        SceneSummaryEntity::class,
        ArcSummaryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RealmsDb : RoomDatabase() {
    abstract fun npcDao(): NpcDao
    abstract fun questDao(): QuestDao
    abstract fun factionDao(): FactionDao
    abstract fun locationDao(): LocationDao
    abstract fun sceneSummaryDao(): SceneSummaryDao
    abstract fun arcSummaryDao(): ArcSummaryDao

    companion object {
        private const val DEFAULT_PREFIX = "realms"
        const val DEFAULT_FILE_NAME = "realms.db"

        fun fileNameForSlot(slot: String): String {
            val safe = slot.lowercase().replace(Regex("[^a-z0-9_-]"), "_").ifBlank { "default" }
            return "${DEFAULT_PREFIX}_$safe.db"
        }

        fun open(context: Context, dbFile: java.io.File): RealmsDb =
            Room.databaseBuilder(context, RealmsDb::class.java, dbFile.absolutePath)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        /** In-memory factory for unit tests (Robolectric). */
        fun inMemory(context: Context): RealmsDb =
            Room.inMemoryDatabaseBuilder(context, RealmsDb::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
