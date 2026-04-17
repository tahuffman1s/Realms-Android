package com.realmsoffate.game.debug

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
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

            if (targetText == null && targetDesc == null) {
                return@route HttpResponse.error(400, "Provide 'text' or 'contentDesc' to identify the element")
            }

            val activity = DebugBridge.requireActivity()

            // Walk the accessibility node tree on the main thread
            data class TapTarget(val label: String, val bounds: Rect)

            val target: TapTarget? = onMain {
                val rootView = activity.window.decorView
                val rootNode = rootView.createAccessibilityNodeInfo()
                    ?: return@onMain null

                fun findNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                    // Check this node
                    val nodeText = node.text?.toString()
                    val nodeDesc = node.contentDescription?.toString()
                    if (targetText != null && nodeText != null && nodeText.contains(targetText, ignoreCase = true)) return node
                    if (targetDesc != null && nodeDesc != null && nodeDesc.contains(targetDesc, ignoreCase = true)) return node
                    // Recurse into children
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        val found = findNode(child)
                        if (found != null) return found
                    }
                    return null
                }

                val found = findNode(rootNode) ?: return@onMain null
                val bounds = Rect()
                found.getBoundsInScreen(bounds)
                val label = targetText ?: targetDesc ?: "unknown"
                TapTarget(label, bounds)
            }

            if (target == null) {
                val query = targetText ?: targetDesc
                return@route HttpResponse.error(404, "Element not found: '$query'")
            }

            val cx = target.bounds.centerX().toFloat()
            val cy = target.bounds.centerY().toFloat()
            val b = target.bounds

            // Dispatch touch events on the main thread
            onMain {
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0)
                val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, cx, cy, 0)
                activity.window.decorView.dispatchTouchEvent(down)
                activity.window.decorView.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
            }

            HttpResponse.json(
                """{"ok":true,"tapped":"${target.label}","bounds":[${b.left},${b.top},${b.right},${b.bottom}]}"""
            )
        }
    }
}
