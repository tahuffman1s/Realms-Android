package com.realmsoffate.game.debug

import android.util.Log
import com.realmsoffate.game.game.GameViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DebugEventBus {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        installCrashHandler()

        DebugHook.onAttach = { activity, vm ->
            DebugBridge.activity = activity
            DebugBridge.viewModel = vm
            observeState(vm)
            log("""{"event":"attached","screen":"${vm.screen.value}"}""")
        }
    }

    private fun observeState(vm: GameViewModel) {
        // Screen changes
        var prevScreen = vm.screen.value
        scope.launch {
            vm.screen.collect { newScreen ->
                if (newScreen != prevScreen) {
                    log("""{"event":"screenChange","from":"$prevScreen","to":"$newScreen"}""")
                    prevScreen = newScreen
                }
            }
        }

        // HP changes
        var prevHp = vm.ui.value.character?.hp
        scope.launch {
            vm.ui.collect { state ->
                val hp = state.character?.hp
                if (hp != null && hp != prevHp && prevHp != null) {
                    log("""{"event":"stateChange","field":"character.hp","from":$prevHp,"to":$hp}""")
                }
                prevHp = hp
            }
        }

        // Gold changes
        var prevGold = vm.ui.value.character?.gold
        scope.launch {
            vm.ui.collect { state ->
                val gold = state.character?.gold
                if (gold != null && gold != prevGold && prevGold != null) {
                    log("""{"event":"stateChange","field":"character.gold","from":$prevGold,"to":$gold}""")
                }
                prevGold = gold
            }
        }

        // Turn changes
        var prevTurns = vm.ui.value.turns
        scope.launch {
            vm.ui.collect { state ->
                val turns = state.turns
                if (turns != prevTurns) {
                    log("""{"event":"stateChange","field":"turns","from":$prevTurns,"to":$turns}""")
                    prevTurns = turns
                }
            }
        }

        // isGenerating changes
        var prevGenerating = vm.ui.value.isGenerating
        scope.launch {
            vm.ui.collect { state ->
                val generating = state.isGenerating
                if (generating != prevGenerating) {
                    val status = if (generating) "start" else "complete"
                    log("""{"event":"generating","status":"$status"}""")
                    prevGenerating = generating
                }
            }
        }

        // New messages
        var prevMessageCount = vm.ui.value.messages.size
        scope.launch {
            vm.ui.collect { state ->
                val count = state.messages.size
                if (count > prevMessageCount) {
                    val newest = state.messages.lastOrNull()
                    val preview = when (newest) {
                        is com.realmsoffate.game.game.DisplayMessage.Narration ->
                            newest.text.take(60).replace("\"", "\\\"")
                        is com.realmsoffate.game.game.DisplayMessage.Player ->
                            newest.text.take(60).replace("\"", "\\\"")
                        is com.realmsoffate.game.game.DisplayMessage.Event ->
                            newest.title.take(60).replace("\"", "\\\"")
                        is com.realmsoffate.game.game.DisplayMessage.System ->
                            newest.text.take(60).replace("\"", "\\\"")
                        null -> ""
                    }
                    log("""{"event":"messageAdded","count":$count,"preview":"$preview"}""")
                    prevMessageCount = count
                }
            }
        }

        // Overlay (preRoll) changes
        var prevPreRoll = vm.ui.value.preRoll
        scope.launch {
            vm.ui.collect { state ->
                val preRoll = state.preRoll
                if (preRoll != null && prevPreRoll == null) {
                    log("""{"event":"overlayShown","overlay":"preRoll"}""")
                } else if (preRoll == null && prevPreRoll != null) {
                    log("""{"event":"overlayDismissed"}""")
                }
                prevPreRoll = preRoll
            }
        }

        // Error changes
        var prevError: String? = null
        scope.launch {
            vm.ui.collect { state ->
                val error = state.error
                if (error != null && error != prevError) {
                    val msg = error.take(200).replace("\"", "\\\"")
                    log("""{"event":"error","message":"$msg"}""")
                }
                prevError = error
            }
        }
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = throwable.stackTraceToString().take(500)
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                val msg = throwable.message?.take(200)?.replace("\"", "\\\"") ?: ""
                Log.e("RealmsDebug", """{"event":"crash","exception":"${throwable.javaClass.simpleName}","message":"$msg","stackTrace":"$trace"}""")
            } catch (_: Exception) {}
            default?.uncaughtException(thread, throwable)
        }
    }

    fun log(json: String) {
        Log.d("RealmsDebug", json)
    }
}
