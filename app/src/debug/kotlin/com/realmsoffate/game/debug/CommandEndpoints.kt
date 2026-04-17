package com.realmsoffate.game.debug

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.realmsoffate.game.game.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonPrimitive

object CommandEndpoints {

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

        // POST /input — submit player text input
        DebugServer.route("POST", "/input") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body with 'text' field")
            val text = body["text"]?.jsonPrimitive?.content
                ?: return@route HttpResponse.error(400, "Missing 'text' field")

            val vm = DebugBridge.requireVm()

            // Capture state before submit to detect what happens
            val stateBefore = vm.ui.value
            val preRollBefore = stateBefore.preRoll

            onMain { vm.submitAction(text) }

            // Give the classifier a short window to run (it's async on viewModelScope)
            kotlinx.coroutines.delay(500)

            val stateAfter = vm.ui.value
            val preRollAfter = stateAfter.preRoll

            val (triggered, detail) = when {
                // A new preRoll appeared — the classifier queued a dice check
                preRollAfter != null && preRollAfter != preRollBefore -> {
                    val pr = preRollAfter
                    "preRoll" to "${pr.skill ?: "freeform"} check queued (${pr.ability} d20=${pr.roll} mod=${pr.mod} total=${pr.total})"
                }
                // No preRoll — went straight to AI (seed or skill-tagged)
                else -> "dispatch" to "sent directly to AI"
            }

            HttpResponse.json("""{"ok":true,"triggered":"$triggered","detail":"$detail"}""")
        }

        // POST /confirm — confirm the active pre-roll dice overlay
        DebugServer.route("POST", "/confirm") { _ ->
            val vm = DebugBridge.requireVm()
            val pre = vm.ui.value.preRoll
                ?: return@route HttpResponse.error(409, "No active preRoll to confirm")

            onMain { vm.confirmPreRoll() }

            HttpResponse.json(
                """{"ok":true,"roll":{"d20":${pre.roll},"modifier":${pre.mod + pre.prof},"total":${pre.total}},"detail":"dispatching to AI"}"""
            )
        }

        // POST /cancel — dismiss the active overlay
        DebugServer.route("POST", "/cancel") { _ ->
            val vm = DebugBridge.requireVm()
            val state = vm.ui.value

            return@route when {
                state.preRoll != null -> {
                    onMain { vm.cancelPreRoll() }
                    HttpResponse.json("""{"ok":true,"dismissed":"preRoll"}""")
                }
                else -> HttpResponse.error(409, "No active overlay to dismiss")
            }
        }

        // POST /navigate — switch screens
        DebugServer.route("POST", "/navigate") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body with 'screen' field")
            val screenName = body["screen"]?.jsonPrimitive?.content
                ?: return@route HttpResponse.error(400, "Missing 'screen' field")

            val screen = when (screenName.lowercase()) {
                "apisetup" -> Screen.ApiSetup
                "title" -> Screen.Title
                "charactercreation" -> Screen.CharacterCreation
                "game" -> Screen.Game
                "death" -> Screen.Death
                else -> return@route HttpResponse.error(400, "Unknown screen '$screenName'. Valid values: apiSetup, title, characterCreation, game, death")
            }

            val vm = DebugBridge.requireVm()
            onMain { vm.setScreen(screen) }

            HttpResponse.json("""{"ok":true,"screen":"$screenName"}""")
        }

        // POST /tap — tap a UI element by text label or content description
        DebugServer.route("POST", "/tap") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body with 'text' or 'contentDesc' field")

            val targetText = body["text"]?.jsonPrimitive?.content
            val targetDesc = body["contentDesc"]?.jsonPrimitive?.content
            val target = targetText ?: targetDesc
                ?: return@route HttpResponse.error(400, "Provide 'text' or 'contentDesc' to identify the element")

            val activity = DebugBridge.requireActivity()

            // Find Compose semantics tree
            val owner = onMain { ComposeTreeHelper.findSemanticsOwner(activity.window.decorView) }
                ?: return@route HttpResponse.error(500, "Could not find Compose semantics owner")

            val allNodes = onMain { ComposeTreeHelper.getAllNodes(owner) }

            // Search for matching node (case-insensitive contains match)
            val targetLower = target.lowercase()
            val matchedNode = allNodes.firstOrNull { node ->
                val nodeText = ComposeTreeHelper.nodeText(node)?.lowercase()
                val nodeDesc = ComposeTreeHelper.nodeContentDesc(node)?.lowercase()
                (nodeText != null && targetLower in nodeText) || (nodeDesc != null && targetLower in nodeDesc)
            } ?: return@route HttpResponse.error(404, "Element not found: '$target'")

            val bounds = onMain { ComposeTreeHelper.boundsInWindow(matchedNode) }
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()

            // Dispatch touch events on the main thread
            onMain {
                val rootView = activity.window.decorView
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0)
                val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, cx, cy, 0)
                rootView.dispatchTouchEvent(down)
                rootView.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
            }

            val label = ComposeTreeHelper.nodeLabel(matchedNode)
            HttpResponse.json(
                """{"ok":true,"tapped":"$label","bounds":[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]}"""
            )
        }
    }
}
