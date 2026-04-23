package com.realmsoffate.game

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.realmsoffate.game.BuildConfig
import com.realmsoffate.game.data.SaveStore
import com.realmsoffate.game.data.db.RealmsDbHolder
import java.util.concurrent.Executors

class RealmsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        EmojiCompat.init(BundledEmojiCompatConfig(this, Executors.newSingleThreadExecutor()))
        SaveStore.init(this)
        RealmsDbHolder.init(this)
        if (BuildConfig.DEBUG) {
            try {
                val bridge = Class.forName("com.realmsoffate.game.debug.DebugBridge")
                val instance = bridge.getDeclaredField("INSTANCE").also { it.isAccessible = true }.get(null)
                val start = bridge.getMethod("start", Application::class.java)
                start.invoke(instance, this)
            } catch (_: Exception) { }
        }
    }

    companion object {
        @Volatile private var INSTANCE: RealmsApp? = null
        val instance: RealmsApp
            get() = INSTANCE ?: error("RealmsApp not created")
    }
}
