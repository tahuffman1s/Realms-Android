package com.realmsoffate.game.debug

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import com.realmsoffate.game.game.GameViewModel

object DebugBridge {
    private const val TAG = "RealmsDebug"

    var activity: Activity? = null
    var viewModel: GameViewModel? = null

    fun start(app: Application) {
        isDebugInspectorInfoEnabled = true
        Log.i(TAG, """{"event":"bridgeStarted","port":8735}""")
        DebugEventBus.install()  // EventBus sets onAttach
        DebugServer.start()
    }

    fun requireVm(): GameViewModel =
        viewModel ?: throw IllegalStateException("ViewModel not attached yet")

    fun requireActivity(): Activity =
        activity ?: throw IllegalStateException("Activity not attached yet")
}
