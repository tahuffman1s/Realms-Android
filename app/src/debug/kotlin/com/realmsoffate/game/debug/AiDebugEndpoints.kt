package com.realmsoffate.game.debug

import android.os.Handler
import android.os.Looper
import com.realmsoffate.game.data.DebugTurn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Phase 4 diagnostic: exposes the in-memory DebugTurn log over HTTP so replay
 * scripts can capture full prompt + response payloads off-device.
 *
 * Routes:
 *   GET /ai/debug-log         — full log as JSON list of DebugTurn
 *   GET /ai/debug-log?last=N  — last N entries only (default: all)
 *
 * The log is capped at 50 entries by GameViewModel; we don't add gzip or paging
 * because 50×~5KB envelopes is well under any reasonable HTTP body limit.
 */
object AiDebugEndpoints {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private suspend fun <T> onMain(block: () -> T): T {
        val deferred = CompletableDeferred<T>()
        mainHandler.post {
            try { deferred.complete(block()) }
            catch (e: Exception) { deferred.completeExceptionally(e) }
        }
        return withTimeout(10_000) { deferred.await() }
    }

    fun register() {
        DebugServer.route("GET", "/ai/debug-log") { req ->
            val vm = DebugBridge.requireVm()
            val log = onMain { vm.snapshotDebugLog() }
            val nParam = req.query["last"]?.toIntOrNull()
            val sliced = if (nParam != null && nParam > 0) log.takeLast(nParam) else log
            val body = json.encodeToString(ListSerializer(DebugTurn.serializer()), sliced)
            HttpResponse.json(body)
        }
    }
}
