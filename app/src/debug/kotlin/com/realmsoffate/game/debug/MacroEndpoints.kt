package com.realmsoffate.game.debug

import android.os.Handler
import android.os.Looper
import com.realmsoffate.game.game.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

object MacroEndpoints {

    private val mainHandler = Handler(Looper.getMainLooper())

    private suspend fun <T> onMain(block: () -> T): T {
        val deferred = CompletableDeferred<T>()
        mainHandler.post {
            try { deferred.complete(block()) }
            catch (e: Exception) { deferred.completeExceptionally(e) }
        }
        return withTimeout(10_000) { deferred.await() }
    }

    fun register() {

        // POST /macro/new-game — skip character creation and land in game screen
        DebugServer.route("POST", "/macro/new-game") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body")

            val name = body["name"]?.jsonPrimitive?.content ?: "Test"
            val cls = body["class"]?.jsonPrimitive?.content ?: "fighter"
            val race = body["race"]?.jsonPrimitive?.content ?: "human"
            val skipFirstTurn = body["skipFirstTurn"]?.jsonPrimitive?.content?.toBoolean() ?: true

            val vm = DebugBridge.requireVm()

            onMain {
                vm.debugCreateCharacter(name, cls, race)
                if (skipFirstTurn) {
                    vm.debugInjectFirstTurn()
                }
            }

            val screen = vm.screen.value
            val turn = vm.ui.value.turns
            HttpResponse.json("""{"ok":true,"screen":"${screen.name.lowercase()}","turn":$turn}""")
        }

        // POST /macro/death — trigger character death
        DebugServer.route("POST", "/macro/death") { _ ->
            val vm = DebugBridge.requireVm()

            onMain {
                val state = vm.ui.value
                val ch = state.character
                if (ch != null) {
                    val deadChar = ch.copy(hp = 0)
                    vm.debugInjectState(state.copy(character = deadChar))
                }
                vm.setScreen(Screen.Death)
            }

            HttpResponse.json("""{"ok":true,"screen":"death"}""")
        }

        // POST /macro/advance — auto-play N turns with canned responses
        DebugServer.route("POST", "/macro/advance") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body")

            val turns = body["turns"]?.jsonPrimitive?.int ?: 1
            val mode = body["mode"]?.jsonPrimitive?.content ?: "canned"

            if (mode != "canned") {
                return@route HttpResponse.error(400, "Only mode='canned' is supported")
            }

            if (turns < 1 || turns > 20) {
                return@route HttpResponse.error(400, "turns must be between 1 and 20")
            }

            val vm = DebugBridge.requireVm()

            // Ensure we're in game screen with a character
            if (vm.screen.value != Screen.Game) {
                return@route HttpResponse.error(409, "Must be on game screen to advance turns")
            }
            if (vm.ui.value.character == null) {
                return@route HttpResponse.error(409, "No active character")
            }

            val startTurn = vm.ui.value.turns

            onMain {
                for (i in 0 until turns) {
                    vm.debugInjectCannedTurn(i)
                }
            }

            val finalTurn = vm.ui.value.turns
            HttpResponse.json("""{"ok":true,"turnsPlayed":$turns,"finalTurn":$finalTurn}""")
        }

        /**
         * POST /macro/simulate-gameplay — replace state with a dense fake mid-campaign snapshot
         * (world + lore, chat feed, quests, NPCs, party, merchants, merchants stock).
         * Ensures a character exists; switches to [Screen.Game]. Does not call the AI.
         */
        DebugServer.route("POST", "/macro/simulate-gameplay") { _ ->
            val vm = DebugBridge.requireVm()
            var turns = 0
            var messageCount = 0
            onMain {
                if (vm.ui.value.character == null) {
                    vm.debugCreateCharacter("Riven Ashmark", "rogue", "half-elf")
                }
                vm.setScreen(Screen.Game)
                vm.debugSimulateGameplay()
                val s = vm.ui.value
                turns = s.turns
                messageCount = s.messages.size
            }
            HttpResponse.json("""{"ok":true,"turns":$turns,"messages":$messageCount}""")
        }
    }
}
