package com.realmsoffate.game.debug

import com.realmsoffate.game.data.content.ContentException
import com.realmsoffate.game.data.content.ContentInfo
import com.realmsoffate.game.data.content.ContentRepository

/**
 * Phase 6 M6 — debug-only endpoints for the JSON-content hot-reload system.
 *
 *  GET  /content/info      Snapshot of schema version, override-enabled flag,
 *                          override root path, and per-file source map (each
 *                          shipped path resolved as either "asset" or "override").
 *
 *  POST /content/reload    Re-runs ContentRepository.initialize(allowOverride=true)
 *                          against the current Application. Atomic: if the new
 *                          bundle fails to parse / validate, the prior bundle
 *                          stays live and the endpoint returns 409 with the
 *                          underlying ContentException message.
 */
object ContentEndpoints {
    fun register() {
        DebugServer.route("GET", "/content/info") { _ ->
            HttpResponse.json(ContentRepository.info().toJson())
        }

        DebugServer.route("POST", "/content/reload") { _ ->
            val app = DebugBridge.requireActivity().application
            try {
                ContentRepository.initialize(app, allowOverride = true)
                HttpResponse.json("""{"ok":true,"info":${ContentRepository.info().toJson()}}""")
            } catch (e: ContentException) {
                HttpResponse.error(409, "reload failed: ${e.message?.replace("\"", "\\\"") ?: e::class.simpleName}")
            }
        }
    }
}

private fun ContentInfo.toJson(): String = buildString {
    append("{")
    append("\"schemaVersion\":").append(schemaVersion).append(',')
    append("\"overrideEnabled\":").append(overrideEnabled).append(',')
    append("\"overrideRoot\":")
    if (overrideRoot == null) append("null") else append('"').append(overrideRoot.jsonEscape()).append('"')
    append(',')
    append("\"sources\":{")
    sources.entries.forEachIndexed { i, (path, src) ->
        if (i > 0) append(',')
        append('"').append(path.jsonEscape()).append("\":\"").append(src).append('"')
    }
    append("}}")
}

private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"")
