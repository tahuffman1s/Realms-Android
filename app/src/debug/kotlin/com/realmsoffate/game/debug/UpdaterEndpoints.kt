package com.realmsoffate.game.debug

import com.realmsoffate.game.data.updater.UpdateRepositoryHolder
import com.realmsoffate.game.data.updater.UpdateState

/**
 * Phase 7 — debug-only endpoints for the auto-update system.
 *
 *  POST /updater/check    Forces UpdateRepository.check(force = true), bypassing
 *                         the 6h cache. Returns immediately with the prior state
 *                         tag; the actual check is asynchronous and reflected in
 *                         /describe once it lands.
 *
 *  GET  /updater/state    Snapshot of the current UpdateState as a small JSON
 *                         object (kind + tag where applicable).
 */
object UpdaterEndpoints {
    fun register() {
        DebugServer.route("POST", "/updater/check") { _ ->
            UpdateRepositoryHolder.instance.check(force = true)
            HttpResponse.json("""{"ok":true,"prior":${currentStateJson()}}""")
        }

        DebugServer.route("GET", "/updater/state") { _ ->
            HttpResponse.json(currentStateJson())
        }
    }

    private fun currentStateJson(): String {
        return when (val s = UpdateRepositoryHolder.instance.state.value) {
            UpdateState.Idle -> """{"kind":"Idle"}"""
            UpdateState.Checking -> """{"kind":"Checking"}"""
            UpdateState.UpToDate -> """{"kind":"UpToDate"}"""
            is UpdateState.Available -> """{"kind":"Available","tag":"${s.release.tag.jsonEscape()}"}"""
            is UpdateState.Downloading -> """{"kind":"Downloading","tag":"${s.release.tag.jsonEscape()}","progress":${s.progress}}"""
            is UpdateState.ReadyToInstall -> """{"kind":"ReadyToInstall","tag":"${s.release.tag.jsonEscape()}"}"""
            is UpdateState.Error -> """{"kind":"Error","reason":"${s.reason.jsonEscape()}","recoverable":${s.recoverable}}"""
        }
    }
}

private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"")
