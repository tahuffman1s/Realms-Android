package com.realmsoffate.game.debug

import android.os.Handler
import android.os.Looper
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.DisplayMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object InjectionEndpoints {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Snapshot of the state as it was before the first /inject call. */
    @Volatile
    private var savedState: GameUiState? = null

    private suspend fun <T> onMain(block: () -> T): T {
        val deferred = CompletableDeferred<T>()
        mainHandler.post {
            try { deferred.complete(block()) }
            catch (e: Exception) { deferred.completeExceptionally(e) }
        }
        return withTimeout(10_000) { deferred.await() }
    }

    /** Returns the int value of a JSON field, or null if absent or not a valid int. */
    private fun kotlinx.serialization.json.JsonObject.intField(key: String): Int? =
        this[key]?.jsonPrimitive?.content?.toIntOrNull()

    /** Returns the boolean value of a JSON field, or null if absent or not a valid boolean. */
    private fun kotlinx.serialization.json.JsonObject.boolField(key: String): Boolean? =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()

    /** Returns the string value of a JSON field, or null if absent. */
    private fun kotlinx.serialization.json.JsonObject.strField(key: String): String? =
        this[key]?.jsonPrimitive?.content

    fun register() {

        // POST /inject — override specific game state fields
        DebugServer.route("POST", "/inject") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body")

            val vm = DebugBridge.requireVm()
            val current = vm.ui.value

            // Save the original state on first inject so /inject/reset can restore it
            if (savedState == null) {
                savedState = current
            }

            val ch = current.character
            var fieldsSet = 0

            // Apply top-level GameUiState fields
            var turns = current.turns
            var isGenerating = current.isGenerating
            var currentScene = current.currentScene
            var morality = current.morality

            body.intField("turns")?.let { turns = it; fieldsSet++ }
            body.boolField("isGenerating")?.let { isGenerating = it; fieldsSet++ }
            body.strField("currentScene")?.let { currentScene = it; fieldsSet++ }
            body.intField("morality")?.let { morality = it; fieldsSet++ }

            // Apply character fields (mutate in-place since Character has var fields)
            if (ch != null) {
                body.intField("character.hp")?.let { ch.hp = it; fieldsSet++ }
                body.intField("character.maxHp")?.let { ch.maxHp = it; fieldsSet++ }
                body.intField("character.gold")?.let { ch.gold = it; fieldsSet++ }
                body.intField("character.level")?.let { ch.level = it; fieldsSet++ }
                body.intField("character.xp")?.let { ch.xp = it; fieldsSet++ }
                body.intField("character.ac")?.let { ch.ac = it; fieldsSet++ }
                body.strField("character.name")?.let { ch.name = it; fieldsSet++ }
                body["character.conditions"]?.jsonArray?.let { arr ->
                    ch.conditions.clear()
                    arr.forEach { el -> ch.conditions.add(el.jsonPrimitive.content) }
                    fieldsSet++
                }
            }

            val newState = current.copy(
                turns = turns,
                isGenerating = isGenerating,
                currentScene = currentScene,
                morality = morality
                // character is mutated in-place; its reference inside current is already updated
            )

            onMain { vm.debugInjectState(newState) }

            HttpResponse.json("""{"ok":true,"fieldsSet":$fieldsSet}""")
        }

        // POST /inject/messages — inject display messages into the chat feed
        DebugServer.route("POST", "/inject/messages") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body with 'messages' array")
            val arr = body["messages"]?.jsonArray
                ?: return@route HttpResponse.error(400, "Missing 'messages' array")

            val vm = DebugBridge.requireVm()
            val current = vm.ui.value

            val injected = mutableListOf<DisplayMessage>()
            for (el in arr) {
                val obj = try { el.jsonObject } catch (_: Exception) { continue }
                val type = obj["type"]?.jsonPrimitive?.content ?: continue
                val text = obj["text"]?.jsonPrimitive?.content ?: continue
                val msg: DisplayMessage = when (type.lowercase()) {
                    "player"    -> DisplayMessage.Player(text)
                    "narration" -> DisplayMessage.Narration(
                        text = text,
                        scene = current.currentScene,
                        sceneDesc = current.currentSceneDesc,
                        hpBefore = 0, hpAfter = 0, maxHp = 0,
                        goldBefore = 0, goldAfter = 0,
                        xpGained = 0
                    )
                    "event"     -> DisplayMessage.Event(
                        icon = "⚡",
                        title = "Event",
                        text = text
                    )
                    "system"    -> DisplayMessage.System(text)
                    else        -> continue
                }
                injected.add(msg)
            }

            val newState = current.copy(messages = current.messages + injected)
            onMain { vm.debugInjectState(newState) }

            HttpResponse.json("""{"ok":true,"injected":${injected.size}}""")
        }

        // POST /inject/reset — restore the state that existed before any /inject calls
        DebugServer.route("POST", "/inject/reset") { _ ->
            val original = savedState
                ?: return@route HttpResponse.json("""{"ok":true,"restored":false,"detail":"No injection has been made; state is already clean"}""")

            val vm = DebugBridge.requireVm()
            onMain { vm.debugInjectState(original) }
            savedState = null

            HttpResponse.json("""{"ok":true,"restored":true}""")
        }
    }
}
