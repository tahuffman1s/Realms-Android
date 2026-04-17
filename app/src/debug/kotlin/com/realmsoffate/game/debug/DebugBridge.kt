package com.realmsoffate.game.debug

import android.app.Activity
import android.app.Application
import android.util.Log
import com.realmsoffate.game.game.GameViewModel

object DebugBridge {
    private const val TAG = "RealmsDebug"

    var activity: Activity? = null
        private set
    var viewModel: GameViewModel? = null
        private set

    fun start(app: Application) {
        Log.i(TAG, """{"event":"bridgeStarted","port":8735}""")
        DebugHook.onAttach = { act, vm ->
            activity = act
            viewModel = vm
            Log.i(TAG, """{"event":"attached","screen":"${vm.screen.value}"}""")
        }
        DebugServer.start()
    }

    fun requireVm(): GameViewModel =
        viewModel ?: throw IllegalStateException("ViewModel not attached yet")

    fun requireActivity(): Activity =
        activity ?: throw IllegalStateException("Activity not attached yet")
}
