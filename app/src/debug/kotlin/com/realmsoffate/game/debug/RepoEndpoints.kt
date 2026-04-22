package com.realmsoffate.game.debug

import com.realmsoffate.game.data.db.RealmsDbHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Phase-2 narrative memory endpoints: expose Room contents and arc summaries
 * for manual verification from the HTTP debug bridge.
 */
object RepoEndpoints {
    private val json = Json { prettyPrint = true }

    fun register() {
        DebugServer.route("GET", "/repo/npcs") { _ ->
            val repo = RealmsDbHolder.repo
            val all = runBlocking { withContext(Dispatchers.IO) { repo.snapshotForReducers().npcs } }
            val arr = buildJsonArray {
                for (n in all) {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(n.id))
                        put("name", JsonPrimitive(n.name))
                        put("status", JsonPrimitive(n.status))
                        put("relationship", JsonPrimitive(n.relationship))
                        put("lastLocation", JsonPrimitive(n.lastLocation))
                        put("lastSeenTurn", JsonPrimitive(n.lastSeenTurn))
                    })
                }
            }
            HttpResponse.json(json.encodeToString(JsonArray.serializer(), arr))
        }

        DebugServer.route("GET", "/repo/stats") { _ ->
            val repo = RealmsDbHolder.repo
            val obj = runBlocking {
                withContext(Dispatchers.IO) {
                    val snap = repo.snapshotForReducers()
                    val unrolled = repo.countUnrolledScenes()
                    val arcs = repo.allArcSummaries()
                    buildJsonObject {
                        put("npcs", JsonPrimitive(snap.npcs.size))
                        put("quests", JsonPrimitive(snap.quests.size))
                        put("factions", JsonPrimitive(snap.factions.size))
                        put("locations", JsonPrimitive(snap.locations.size))
                        put("unrolledScenes", JsonPrimitive(unrolled))
                        put("arcSummaries", JsonPrimitive(arcs.size))
                    }
                }
            }
            HttpResponse.json(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj))
        }

        DebugServer.route("GET", "/repo/arcs") { _ ->
            val repo = RealmsDbHolder.repo
            val arcs = runBlocking { withContext(Dispatchers.IO) { repo.allArcSummaries() } }
            val arr = buildJsonArray {
                for (a in arcs) {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(a.id))
                        put("turnStart", JsonPrimitive(a.turnStart))
                        put("turnEnd", JsonPrimitive(a.turnEnd))
                        put("summary", JsonPrimitive(a.summary))
                        put("createdAt", JsonPrimitive(a.createdAt))
                    })
                }
            }
            HttpResponse.json(json.encodeToString(JsonArray.serializer(), arr))
        }

        DebugServer.route("GET", "/repo/contradictions") { _ ->
            val items = com.realmsoffate.game.data.ContradictionQueue.snapshot()
            val arr = buildJsonArray { for (s in items) add(JsonPrimitive(s)) }
            HttpResponse.json(json.encodeToString(JsonArray.serializer(), arr))
        }
    }
}
