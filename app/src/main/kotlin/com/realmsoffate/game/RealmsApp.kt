package com.realmsoffate.game

import android.app.Application
import com.realmsoffate.game.data.SaveStore

class RealmsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        SaveStore.init(this)
    }

    companion object {
        @Volatile private var INSTANCE: RealmsApp? = null
        val instance: RealmsApp
            get() = INSTANCE ?: error("RealmsApp not created")
    }
}
