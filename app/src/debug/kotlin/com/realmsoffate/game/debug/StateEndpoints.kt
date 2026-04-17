package com.realmsoffate.game.debug

import com.realmsoffate.game.game.GameUiState

object StateEndpoints {

    // Holds the last snapshot taken by GET /state for diff computation
    @Volatile
    private var lastSnapshot: GameUiState? = null

    fun register() {
        // GET /state — full game state snapshot
        DebugServer.route("GET", "/state") { _ ->
            val vm = DebugBridge.requireVm()
            val json = DebugSerializer.serializeState(vm)
            lastSnapshot = vm.ui.value
            HttpResponse.json(json)
        }

        // GET /state/diff — changes since the last /state call
        DebugServer.route("GET", "/state/diff") { _ ->
            val vm = DebugBridge.requireVm()
            val old = lastSnapshot
                ?: return@route HttpResponse.error(409, "No baseline snapshot — call GET /state first")
            val new = vm.ui.value
            val json = DebugSerializer.computeDiff(old, new)
            HttpResponse.json(json)
        }

        // GET /state/overlay — active overlay and available actions
        DebugServer.route("GET", "/state/overlay") { _ ->
            val vm = DebugBridge.requireVm()
            val json = DebugSerializer.serializeOverlay(vm)
            HttpResponse.json(json)
        }
    }
}
