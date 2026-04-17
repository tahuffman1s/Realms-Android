# Debug Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a debug-only HTTP server + event streaming system that gives Claude Code full visibility into the app's visual state, game state, and UI layout, with command/macro APIs for automated testing.

**Architecture:** A `DebugServer` (raw `ServerSocket` on port 8735) in `app/src/debug/` routes HTTP requests to the `GameViewModel`. A `DebugHook` object in the main source set provides nullable connection points (theme override, attach callback) that are no-ops in release builds. An `IssueChecker` samples rendered pixels from the screen bitmap to compute real contrast ratios. Events stream via logcat under the `RealmsDebug` tag.

**Tech Stack:** `java.net.ServerSocket` (HTTP), `kotlinx.serialization` (JSON), `PixelCopy` (screenshots), `SemanticsNode` tree (UI structure), Android logcat (events). Zero new dependencies.

**Spec:** `docs/superpowers/specs/2026-04-17-debug-bridge-design.md`

---

## File Map

### New files (all in `app/src/debug/kotlin/com/realmsoffate/game/debug/`)

| File | Responsibility |
|------|---------------|
| `DebugBridge.kt` | Singleton holding references to Activity + ViewModel. Entry point for the debug system. |
| `DebugServer.kt` | HTTP server on port 8735. Parses requests, routes to handlers, writes responses. |
| `StateEndpoints.kt` | Handlers for `/state`, `/state/diff`, `/state/overlay` |
| `CommandEndpoints.kt` | Handlers for `/input`, `/confirm`, `/cancel`, `/navigate`, `/scroll`, `/tap` |
| `ScreenshotEndpoints.kt` | Handlers for `/screenshot`, `/screenshot/both` |
| `ThemeEndpoints.kt` | Handlers for `/theme`, `/font-scale` |
| `InjectionEndpoints.kt` | Handlers for `/inject`, `/inject/messages`, `/inject/reset` |
| `MacroEndpoints.kt` | Handlers for `/macro/*` |
| `IssueChecker.kt` | Automated visual/accessibility checks. Pixel-sampling for contrast. |
| `DebugEventBus.kt` | Pushes structured JSON events to logcat. Crash handler. |
| `DebugSerializer.kt` | JSON serialization helpers for GameUiState, Character, etc. |

### New file in main source set

| File | Responsibility |
|------|---------------|
| `app/src/main/.../debug/DebugHook.kt` | Nullable connection points: theme override, font scale override, attach callback. No-op in release. |

### Modified files

| File | Change |
|------|--------|
| `app/src/main/.../RealmsApp.kt` | Add `BuildConfig.DEBUG` guard to start DebugBridge |
| `app/src/main/.../MainActivity.kt` | Call `DebugHook.onAttach` to pass Activity + ViewModel to debug bridge |
| `app/src/main/.../ui/theme/Theme.kt` | Read `DebugHook.themeOverride` for forced theme switching |
| `app/src/main/.../ui/game/GameScreen.kt` | Read `DebugHook.fontScaleOverride` for forced font scale |

### Build files

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Register `checkHardcodedColors` Gradle task |

---

## Task 1: Foundation — DebugHook + DebugBridge + Wiring

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/debug/DebugHook.kt`
- Create: `app/src/debug/kotlin/com/realmsoffate/game/debug/DebugBridge.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/RealmsApp.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/theme/Theme.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create DebugHook in main source set**

This is the only main-source-set file for the debug bridge. It provides nullable connection points that the debug source set populates. In release builds, these are always null/unused and ProGuard strips the dead code.

```kotlin
// app/src/main/kotlin/com/realmsoffate/game/debug/DebugHook.kt
package com.realmsoffate.game.debug

import android.app.Activity
import com.realmsoffate.game.game.GameViewModel
import kotlinx.coroutines.flow.MutableStateFlow

object DebugHook {
    /** Debug source set sets this to bridge Activity + ViewModel to the debug server. */
    var onAttach: ((Activity, GameViewModel) -> Unit)? = null

    /** When non-null, forces light (false) or dark (true) theme. Null = follow system. */
    val themeOverride = MutableStateFlow<Boolean?>(null)

    /** When non-null, overrides the in-app font scale. Null = use ViewModel's setting. */
    val fontScaleOverride = MutableStateFlow<Float?>(null)
}
```

- [ ] **Step 2: Create debug source set directory**

```bash
mkdir -p app/src/debug/kotlin/com/realmsoffate/game/debug
```

- [ ] **Step 3: Create DebugBridge singleton**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/DebugBridge.kt
package com.realmsoffate.game.debug

import android.app.Activity
import android.app.Application
import android.util.Log
import com.realmsoffate.game.game.GameViewModel

object DebugBridge {
    private const val TAG = "RealmsDebug"

    var activity: Activity? = null
        private set
    var viewModel: GameViewModel? = null
        private set

    fun start(app: Application) {
        Log.i(TAG, """{"event":"bridgeStarted","port":8735}""")
        DebugHook.onAttach = { act, vm ->
            activity = act
            viewModel = vm
            Log.i(TAG, """{"event":"attached","screen":"${vm.screen.value}"}""")
        }
        DebugEventBus.install()
        DebugServer.start()
    }

    fun requireVm(): GameViewModel =
        viewModel ?: throw IllegalStateException("ViewModel not attached yet")

    fun requireActivity(): Activity =
        activity ?: throw IllegalStateException("Activity not attached yet")
}
```

- [ ] **Step 4: Modify RealmsApp.kt to start the bridge**

In `RealmsApp.kt`, add the import and the `BuildConfig.DEBUG` guard after `SaveStore.init(this)`:

```kotlin
// Add import at top:
import com.realmsoffate.game.BuildConfig
import com.realmsoffate.game.debug.DebugBridge  // only resolves in debug builds

// In onCreate(), after SaveStore.init(this):
if (BuildConfig.DEBUG) {
    DebugBridge.start(this)
}
```

Note: Since `DebugBridge` lives in the debug source set, this import only resolves in debug builds. In release builds with `isMinifyEnabled = true`, the dead `if` block is stripped by R8. To avoid a compile error in release, use reflection instead:

```kotlin
// In onCreate(), after SaveStore.init(this):
if (BuildConfig.DEBUG) {
    try {
        val bridge = Class.forName("com.realmsoffate.game.debug.DebugBridge")
        val start = bridge.getMethod("start", Application::class.java)
        start.invoke(bridge.kotlin.objectInstance, this)
    } catch (_: Exception) { /* release build — class doesn't exist */ }
}
```

- [ ] **Step 5: Modify MainActivity to attach ViewModel**

In `MainActivity.kt`, after the ViewModel is obtained, call the hook:

```kotlin
// In MainActivity, after: private val viewModel: GameViewModel by viewModels { GameViewModel.Factory }
// Add to onCreate() or wherever setContent is called, before setContent:
com.realmsoffate.game.debug.DebugHook.onAttach?.invoke(this, viewModel)
```

- [ ] **Step 6: Wire theme override into Theme.kt**

In `Theme.kt`, modify the `RealmsTheme` composable to read the debug override:

```kotlin
// At the top of the RealmsTheme function body, before the colorScheme logic:
val debugTheme by com.realmsoffate.game.debug.DebugHook.themeOverride.collectAsState()
val effectiveDarkTheme = debugTheme ?: darkTheme
// Then replace all uses of `darkTheme` with `effectiveDarkTheme` in the function body
```

Add import: `import androidx.compose.runtime.collectAsState`

- [ ] **Step 7: Wire font scale override into GameScreen.kt**

In `GameScreen.kt`, where `fontScale` is read (line 54):

```kotlin
// Change:
val fontScale by vm.fontScale.collectAsState()
// To:
val debugFontScale by com.realmsoffate.game.debug.DebugHook.fontScaleOverride.collectAsState()
val fontScale = debugFontScale ?: vm.fontScale.collectAsState().value
```

- [ ] **Step 8: Verify build compiles**

```bash
gradle assembleDebug 2>&1 | tail -5
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 9: Commit**

```bash
git add -A app/src/debug/ app/src/main/kotlin/com/realmsoffate/game/debug/
git add app/src/main/kotlin/com/realmsoffate/game/RealmsApp.kt
git add app/src/main/kotlin/com/realmsoffate/game/MainActivity.kt
git add app/src/main/kotlin/com/realmsoffate/game/ui/theme/Theme.kt
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "feat: add Debug Bridge foundation — DebugHook, DebugBridge, wiring"
```

---

## Task 2: HTTP Server Core

**Files:**
- Create: `app/src/debug/kotlin/com/realmsoffate/game/debug/DebugServer.kt`

- [ ] **Step 1: Create DebugServer with routing**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/DebugServer.kt
package com.realmsoffate.game.debug

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

private const val PORT = 8735
private const val TAG = "RealmsDebug"

data class HttpRequest(
    val method: String,       // GET, POST
    val path: String,         // /state, /input, etc.
    val query: Map<String, String>,  // ?format=base64 etc.
    val body: String          // POST body
) {
    fun jsonBody(): JsonObject? = try {
        Json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) { null }
}

data class HttpResponse(
    val status: Int = 200,
    val contentType: String = "application/json",
    val body: ByteArray = ByteArray(0)
) {
    companion object {
        fun json(obj: String, status: Int = 200) = HttpResponse(
            status = status,
            contentType = "application/json; charset=utf-8",
            body = obj.toByteArray(Charsets.UTF_8)
        )

        fun png(bytes: ByteArray) = HttpResponse(
            contentType = "image/png",
            body = bytes
        )

        fun error(status: Int, message: String) = json(
            """{"error":"$message"}""", status
        )

        fun ok(detail: String = "ok") = json("""{"ok":true,"detail":"$detail"}""")
    }
}

typealias RouteHandler = suspend (HttpRequest) -> HttpResponse

object DebugServer {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val routes = mutableMapOf<Pair<String, String>, RouteHandler>()

    fun route(method: String, path: String, handler: RouteHandler) {
        routes[method.uppercase() to path] = handler
    }

    fun start() {
        // Register all endpoint groups
        StateEndpoints.register()
        CommandEndpoints.register()
        ScreenshotEndpoints.register()
        ThemeEndpoints.register()
        InjectionEndpoints.register()
        MacroEndpoints.register()
        IssueCheckerEndpoints.register()

        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, """{"event":"serverStarted","port":$PORT}""")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, """{"event":"serverError","error":"${e.message}"}""")
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val (path, queryString) = if ("?" in fullPath) {
                fullPath.substringBefore("?") to fullPath.substringAfter("?")
            } else fullPath to ""

            val query = queryString.split("&").filter { it.isNotBlank() }.associate {
                val (k, v) = it.split("=", limit = 2) + ""
                k to v
            }

            // Read headers
            var contentLength = 0
            var line = reader.readLine()
            while (line != null && line.isNotBlank()) {
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                line = reader.readLine()
            }

            // Read body
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n == -1) break
                    read += n
                }
                String(buf, 0, read)
            } else ""

            val request = HttpRequest(method, path, query, body)
            val handler = routes[method to path]
            val response = if (handler != null) {
                try {
                    handler(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Handler error: ${e.message}", e)
                    HttpResponse.error(500, e.message ?: "Internal error")
                }
            } else {
                HttpResponse.error(404, "Not found: $method $path")
            }

            writeResponse(socket.getOutputStream(), response)
        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun writeResponse(out: OutputStream, response: HttpResponse) {
        val statusText = when (response.status) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 500 -> "Internal Server Error"
            else -> "Unknown"
        }
        val header = buildString {
            append("HTTP/1.1 ${response.status} $statusText\r\n")
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(response.body)
        out.flush()
    }
}
```

- [ ] **Step 2: Verify build**

```bash
gradle assembleDebug 2>&1 | tail -5
# Expected: BUILD SUCCESSFUL (server references endpoint classes that don't exist yet — 
# this step will fail until all endpoint files exist. Create stubs or comment out the 
# register() calls until each endpoint task is complete.)
```

To unblock, create stubs for all endpoint objects that just have an empty `fun register() {}`. These will be filled in by subsequent tasks.

- [ ] **Step 3: Create endpoint stubs**

Create these stub files in `app/src/debug/kotlin/com/realmsoffate/game/debug/`:

```kotlin
// StateEndpoints.kt
package com.realmsoffate.game.debug
object StateEndpoints { fun register() {} }

// CommandEndpoints.kt
package com.realmsoffate.game.debug
object CommandEndpoints { fun register() {} }

// ScreenshotEndpoints.kt
package com.realmsoffate.game.debug
object ScreenshotEndpoints { fun register() {} }

// ThemeEndpoints.kt
package com.realmsoffate.game.debug
object ThemeEndpoints { fun register() {} }

// InjectionEndpoints.kt
package com.realmsoffate.game.debug
object InjectionEndpoints { fun register() {} }

// MacroEndpoints.kt
package com.realmsoffate.game.debug
object MacroEndpoints { fun register() {} }

// IssueCheckerEndpoints.kt
package com.realmsoffate.game.debug
object IssueCheckerEndpoints { fun register() {} }

// DebugEventBus.kt
package com.realmsoffate.game.debug
object DebugEventBus { fun install() {} }
```

- [ ] **Step 4: Verify build and deploy**

```bash
gradle assembleDebug 2>&1 | tail -5
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```bash
git add app/src/debug/
git commit -m "feat: add DebugServer HTTP core with routing and endpoint stubs"
```

---

## Task 3: State Endpoints

**Files:**
- Create: `app/src/debug/kotlin/com/realmsoffate/game/debug/DebugSerializer.kt`
- Replace stub: `app/src/debug/kotlin/com/realmsoffate/game/debug/StateEndpoints.kt`

- [ ] **Step 1: Create DebugSerializer**

Converts GameUiState into a clean JSON structure without leaking the full internal representation. Uses `kotlinx.serialization`'s `buildJsonObject`.

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/DebugSerializer.kt
package com.realmsoffate.game.debug

import com.realmsoffate.game.data.Character
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.game.Screen
import kotlinx.serialization.json.*

object DebugSerializer {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun serializeState(vm: GameViewModel): String {
        val state = vm.ui.value
        val screen = vm.screen.value
        return buildJsonObject {
            put("screen", screen.name.lowercase())
            put("turn", state.turns)
            put("isGenerating", state.isGenerating)
            put("error", state.error?.let { JsonPrimitive(it) } ?: JsonNull)
            put("activeOverlay", overlayName(state))
            put("scene", buildJsonObject {
                put("type", state.currentScene)
                put("description", state.currentSceneDesc)
            })
            state.character?.let { put("character", serializeCharacter(it, state)) }
            put("recentMessages", serializeMessages(state.messages.takeLast(10)))
            put("totalMessages", state.messages.size)
            put("choices", buildJsonArray {
                state.currentChoices.forEach { c ->
                    add(buildJsonObject {
                        put("n", c.n)
                        put("text", c.text)
                        put("skill", c.skill)
                    })
                }
            })
            state.combat?.let { combat ->
                put("combat", buildJsonObject {
                    put("inCombat", true)
                    put("round", combat.round)
                    put("enemies", buildJsonArray {
                        combat.enemies.forEach { e ->
                            add(buildJsonObject {
                                put("name", e.name)
                                put("hp", e.hp)
                                put("maxHp", e.maxHp)
                            })
                        }
                    })
                })
            }
            put("npcLog", buildJsonArray {
                state.npcLog.take(20).forEach { npc ->
                    add(buildJsonObject {
                        put("id", npc.id)
                        put("name", npc.name)
                        put("disposition", npc.disposition)
                    })
                }
            })
            put("quests", buildJsonArray {
                state.quests.forEach { q ->
                    add(buildJsonObject {
                        put("name", q.name)
                        put("status", q.status)
                    })
                }
            })
        }.let { json.encodeToString(JsonElement.serializer(), it) }
    }

    fun serializeOverlay(vm: GameViewModel): String {
        val state = vm.ui.value
        return buildJsonObject {
            val name = overlayName(state)
            put("activeOverlay", name?.let { JsonPrimitive(it) } ?: JsonNull)
            when {
                state.preRoll != null -> {
                    val pr = state.preRoll!!
                    put("data", buildJsonObject {
                        put("action", pr.action)
                        put("skill", pr.skill ?: "none")
                        put("d20", pr.roll.d20)
                        put("modifier", pr.roll.modifier)
                        put("total", pr.roll.total)
                        put("label", pr.roll.label)
                    })
                    put("actions", buildJsonArray { add("confirm"); add("cancel") })
                }
                state.combat != null -> {
                    put("actions", buildJsonArray { add("attack"); add("defend"); add("flee") })
                }
                state.deathSave != null -> {
                    put("data", buildJsonObject {
                        put("successes", state.deathSave!!.successes)
                        put("failures", state.deathSave!!.failures)
                    })
                    put("actions", buildJsonArray { add("roll") })
                }
                else -> {
                    put("actions", buildJsonArray {})
                }
            }
        }.let { json.encodeToString(JsonElement.serializer(), it) }
    }

    private fun overlayName(state: GameUiState): String? = when {
        state.preRoll != null -> "preRoll"
        state.deathSave != null -> "deathSave"
        state.combat != null -> "combat"
        state.travelState != null -> "travel"
        else -> null
    }

    private fun serializeCharacter(c: Character, state: GameUiState) = buildJsonObject {
        put("name", c.name)
        put("race", c.race)
        put("class", c.cls)
        put("level", c.level)
        put("hp", c.hp)
        put("maxHp", c.maxHp)
        put("ac", c.ac)
        put("xp", c.xp)
        put("gold", c.gold)
        put("location", state.worldMap?.locations?.getOrNull(state.currentLoc)?.name ?: "unknown")
        put("conditions", buildJsonArray { c.conditions.forEach { add(it) } })
        put("inventory", buildJsonArray { c.inventory.forEach { add(it.name) } })
        put("stats", buildJsonObject {
            put("str", c.abilities.str)
            put("dex", c.abilities.dex)
            put("con", c.abilities.con)
            put("int", c.abilities.int_)
            put("wis", c.abilities.wis)
            put("cha", c.abilities.cha)
        })
        put("morality", state.morality)
        put("factionRep", buildJsonObject {
            state.factionRep.forEach { (k, v) -> put(k, v) }
        })
    }

    fun serializeMessages(messages: List<DisplayMessage>) = buildJsonArray {
        messages.forEach { msg ->
            add(buildJsonObject {
                when (msg) {
                    is DisplayMessage.Player -> {
                        put("type", "player")
                        put("text", msg.text)
                    }
                    is DisplayMessage.Narration -> {
                        put("type", "narration")
                        put("text", msg.text)
                        put("scene", msg.scene)
                    }
                    is DisplayMessage.Event -> {
                        put("type", "event")
                        put("title", msg.title)
                        put("text", msg.text)
                    }
                    is DisplayMessage.System -> {
                        put("type", "system")
                        put("text", msg.text)
                    }
                }
            })
        }
    }

    /** Diff computation: compares two states and returns changed fields. */
    fun computeDiff(old: GameUiState, new: GameUiState): String {
        return buildJsonObject {
            put("changes", buildJsonObject {
                if (old.character?.hp != new.character?.hp) {
                    put("character.hp", buildJsonObject {
                        put("from", old.character?.hp ?: 0)
                        put("to", new.character?.hp ?: 0)
                    })
                }
                if (old.character?.gold != new.character?.gold) {
                    put("character.gold", buildJsonObject {
                        put("from", old.character?.gold ?: 0)
                        put("to", new.character?.gold ?: 0)
                    })
                }
                if (old.character?.level != new.character?.level) {
                    put("character.level", buildJsonObject {
                        put("from", old.character?.level ?: 0)
                        put("to", new.character?.level ?: 0)
                    })
                }
                if (old.character?.xp != new.character?.xp) {
                    put("character.xp", buildJsonObject {
                        put("from", old.character?.xp ?: 0)
                        put("to", new.character?.xp ?: 0)
                    })
                }
                if (old.turns != new.turns) {
                    put("turn", buildJsonObject {
                        put("from", old.turns)
                        put("to", new.turns)
                    })
                }
                if (old.currentScene != new.currentScene) {
                    put("scene", buildJsonObject {
                        put("from", old.currentScene)
                        put("to", new.currentScene)
                    })
                }
                if (old.isGenerating != new.isGenerating) {
                    put("isGenerating", buildJsonObject {
                        put("from", old.isGenerating)
                        put("to", new.isGenerating)
                    })
                }
                val newMsgs = new.messages.size - old.messages.size
                if (newMsgs > 0) {
                    put("messagesAdded", serializeMessages(new.messages.takeLast(newMsgs)))
                }
                if (old.combat?.let { true } != new.combat?.let { true }) {
                    put("combat", buildJsonObject {
                        put("from", if (old.combat != null) "active" else "none")
                        put("to", if (new.combat != null) "active" else "none")
                    })
                }
                if (overlayName(old) != overlayName(new)) {
                    put("activeOverlay", buildJsonObject {
                        put("from", overlayName(old)?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("to", overlayName(new)?.let { JsonPrimitive(it) } ?: JsonNull)
                    })
                }
            })
        }.let { Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), it) }
    }
}
```

- [ ] **Step 2: Implement StateEndpoints**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/StateEndpoints.kt
package com.realmsoffate.game.debug

import com.realmsoffate.game.game.GameUiState

object StateEndpoints {
    private var lastState: GameUiState? = null

    fun register() {
        DebugServer.route("GET", "/state") { _ ->
            val vm = DebugBridge.requireVm()
            lastState = vm.ui.value
            HttpResponse.json(DebugSerializer.serializeState(vm))
        }

        DebugServer.route("GET", "/state/diff") { _ ->
            val vm = DebugBridge.requireVm()
            val current = vm.ui.value
            val previous = lastState ?: current
            lastState = current
            HttpResponse.json(DebugSerializer.computeDiff(previous, current))
        }

        DebugServer.route("GET", "/state/overlay") { _ ->
            val vm = DebugBridge.requireVm()
            HttpResponse.json(DebugSerializer.serializeOverlay(vm))
        }
    }
}
```

- [ ] **Step 3: Build and verify**

```bash
gradle assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/debug/
git commit -m "feat: add /state, /state/diff, /state/overlay endpoints"
```

---

## Task 4: Command Endpoints

**Files:**
- Replace stub: `app/src/debug/kotlin/com/realmsoffate/game/debug/CommandEndpoints.kt`

- [ ] **Step 1: Implement CommandEndpoints**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/CommandEndpoints.kt
package com.realmsoffate.game.debug

import android.os.Handler
import android.os.Looper
import com.realmsoffate.game.game.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

object CommandEndpoints {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Run a block on the main thread and await its result. */
    private suspend fun <T> onMain(block: () -> T): T {
        val deferred = CompletableDeferred<T>()
        mainHandler.post {
            try {
                deferred.complete(block())
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return withTimeout(10_000) { deferred.await() }
    }

    fun register() {
        DebugServer.route("POST", "/input") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val text = body["text"]?.jsonPrimitive?.content
                ?: return@route HttpResponse.error(400, "Missing 'text' field")
            onMain { DebugBridge.requireVm().submitAction(text) }
            // Check if a pre-roll was triggered
            val state = DebugBridge.requireVm().ui.value
            if (state.preRoll != null) {
                HttpResponse.json("""{"ok":true,"triggered":"preRoll","detail":"${state.preRoll!!.skill ?: "general"} check queued, waiting for confirm"}""")
            } else {
                HttpResponse.json("""{"ok":true,"triggered":"dispatch","detail":"sent directly to AI"}""")
            }
        }

        DebugServer.route("POST", "/confirm") { _ ->
            val vm = DebugBridge.requireVm()
            val preRoll = vm.ui.value.preRoll
                ?: return@route HttpResponse.error(400, "No pre-roll active")
            val roll = preRoll.roll
            onMain { vm.confirmPreRoll() }
            HttpResponse.json(buildJsonObject {
                put("ok", true)
                put("roll", buildJsonObject {
                    put("d20", roll.d20)
                    put("modifier", roll.modifier)
                    put("total", roll.total)
                })
                put("detail", "dispatching to AI")
            }.toString())
        }

        DebugServer.route("POST", "/cancel") { _ ->
            val vm = DebugBridge.requireVm()
            val overlay = when {
                vm.ui.value.preRoll != null -> "preRoll"
                else -> return@route HttpResponse.error(400, "No overlay to cancel")
            }
            onMain { vm.cancelPreRoll() }
            HttpResponse.json("""{"ok":true,"dismissed":"$overlay"}""")
        }

        DebugServer.route("POST", "/navigate") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")

            // Screen navigation
            body["screen"]?.jsonPrimitive?.content?.let { screenName ->
                val screen = Screen.entries.firstOrNull { it.name.equals(screenName, ignoreCase = true) }
                    ?: return@route HttpResponse.error(400, "Unknown screen: $screenName. Valid: ${Screen.entries.joinToString { it.name }}")
                onMain { DebugBridge.requireVm().setScreen(screen) }
                return@route HttpResponse.json("""{"ok":true,"screen":"${screen.name.lowercase()}"}""")
            }

            // Tab navigation (within game screen)
            body["tab"]?.jsonPrimitive?.content?.let { tabName ->
                // Tabs are handled by the Compose UI, not the ViewModel.
                // We can't navigate tabs from here — return instructions.
                return@route HttpResponse.error(400, "Tab navigation not supported via /navigate. Use /tap with the tab label instead.")
            }

            HttpResponse.error(400, "Provide 'screen' or 'tab'")
        }

        DebugServer.route("POST", "/tap") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val contentDesc = body["contentDesc"]?.jsonPrimitive?.content
            val text = body["text"]?.jsonPrimitive?.content
            val target = contentDesc ?: text
                ?: return@route HttpResponse.error(400, "Provide 'contentDesc' or 'text'")

            // Use accessibility node info to find and click the element
            val activity = DebugBridge.requireActivity()
            val rootView = activity.window.decorView
            val nodeInfo = rootView.createAccessibilityNodeInfo()

            fun findNode(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.contentDescription?.toString() == target || node.text?.toString() == target) return node
                for (i in 0 until node.childCount) {
                    val found = findNode(node.getChild(i))
                    if (found != null) return found
                }
                return null
            }

            val found = findNode(nodeInfo)
                ?: return@route HttpResponse.error(404, "Element not found: $target")

            val bounds = android.graphics.Rect()
            found.getBoundsInScreen(bounds)
            val x = bounds.centerX()
            val y = bounds.centerY()

            // Dispatch tap via instrumentation
            onMain {
                val downTime = android.os.SystemClock.uptimeMillis()
                val down = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
                val up = android.view.MotionEvent.obtain(downTime, downTime + 50, android.view.MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
                rootView.dispatchTouchEvent(down)
                rootView.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
            }

            HttpResponse.json("""{"ok":true,"tapped":"$target","bounds":[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]}""")
        }

        DebugServer.route("POST", "/scroll") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val direction = body["direction"]?.jsonPrimitive?.content ?: "bottom"
            // Scroll commands require the actual scrollable view — this is best done via adb
            return@route HttpResponse.error(501, "Scroll not yet implemented. Use: adb shell input swipe 540 1500 540 500 300")
        }
    }
}
```

Note: `cancelPreRoll()` and `setScreen()` must exist on GameViewModel. Check that they do. `setScreen` is the setter for the `_screen` MutableStateFlow. If it's private, add a public method. `cancelPreRoll` should clear the `preRoll` field on `_ui`. If these don't exist, add them to the ViewModel as part of this task.

- [ ] **Step 2: Build and verify**

```bash
gradle assembleDebug 2>&1 | tail -5
```

Fix any missing ViewModel methods (add `setScreen(s: Screen)` and `cancelPreRoll()` if needed).

- [ ] **Step 3: Commit**

```bash
git add app/src/debug/ app/src/main/
git commit -m "feat: add /input, /confirm, /cancel, /navigate, /tap command endpoints"
```

---

## Task 5: Screenshot Endpoints

**Files:**
- Replace stub: `app/src/debug/kotlin/com/realmsoffate/game/debug/ScreenshotEndpoints.kt`

- [ ] **Step 1: Implement ScreenshotEndpoints**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/ScreenshotEndpoints.kt
package com.realmsoffate.game.debug

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

object ScreenshotEndpoints {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun captureScreenshot(): ByteArray {
        val activity = DebugBridge.requireActivity()
        val window = activity.window
        val view = window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        val deferred = CompletableDeferred<Int>()
        PixelCopy.request(window, bitmap, { result ->
            deferred.complete(result)
        }, mainHandler)

        val result = withTimeout(10_000) { deferred.await() }
        if (result != PixelCopy.SUCCESS) {
            throw RuntimeException("PixelCopy failed with result $result")
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    fun register() {
        DebugServer.route("GET", "/screenshot") { req ->
            val bytes = captureScreenshot()
            if (req.query["format"] == "base64") {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                HttpResponse.json("""{"image":"$b64"}""")
            } else {
                HttpResponse.png(bytes)
            }
        }

        DebugServer.route("GET", "/screenshot/both") { _ ->
            // Capture current theme
            val originalTheme = DebugHook.themeOverride.value

            // Light
            DebugHook.themeOverride.value = false
            delay(500) // wait for recomposition
            val lightBytes = captureScreenshot()
            val lightB64 = Base64.encodeToString(lightBytes, Base64.NO_WRAP)

            // Dark
            DebugHook.themeOverride.value = true
            delay(500)
            val darkBytes = captureScreenshot()
            val darkB64 = Base64.encodeToString(darkBytes, Base64.NO_WRAP)

            // Restore
            DebugHook.themeOverride.value = originalTheme

            HttpResponse.json("""{"light":"$lightB64","dark":"$darkB64"}""")
        }
    }
}
```

- [ ] **Step 2: Build, deploy, and test**

```bash
gradle installDebug 2>&1 | tail -5
adb -s emulator-5554 forward tcp:8735 tcp:8735
curl localhost:8735/screenshot > /tmp/test_screenshot.png
# Open /tmp/test_screenshot.png to verify
```

- [ ] **Step 3: Commit**

```bash
git add app/src/debug/
git commit -m "feat: add /screenshot and /screenshot/both endpoints"
```

---

## Task 6: Theme & Font Scale Endpoints

**Files:**
- Replace stub: `app/src/debug/kotlin/com/realmsoffate/game/debug/ThemeEndpoints.kt`

- [ ] **Step 1: Implement ThemeEndpoints**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/ThemeEndpoints.kt
package com.realmsoffate.game.debug

import kotlinx.serialization.json.jsonPrimitive

object ThemeEndpoints {
    fun register() {
        DebugServer.route("POST", "/theme") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val mode = body["mode"]?.jsonPrimitive?.content
                ?: return@route HttpResponse.error(400, "Missing 'mode' field")
            when (mode.lowercase()) {
                "light" -> DebugHook.themeOverride.value = false
                "dark" -> DebugHook.themeOverride.value = true
                "system" -> DebugHook.themeOverride.value = null
                else -> return@route HttpResponse.error(400, "Invalid mode: $mode. Use: light, dark, system")
            }
            val active = when (DebugHook.themeOverride.value) {
                false -> "light"; true -> "dark"; null -> "system"
            }
            HttpResponse.json("""{"ok":true,"activeTheme":"$active"}""")
        }

        DebugServer.route("POST", "/font-scale") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val scale = body["scale"]?.jsonPrimitive?.content?.toFloatOrNull()
                ?: return@route HttpResponse.error(400, "Missing or invalid 'scale' field")
            if (scale < 0.5f || scale > 3.0f) {
                return@route HttpResponse.error(400, "Scale must be between 0.5 and 3.0")
            }
            DebugHook.fontScaleOverride.value = scale
            HttpResponse.json("""{"ok":true,"scale":$scale}""")
        }
    }
}
```

- [ ] **Step 2: Build and verify**

```bash
gradle assembleDebug 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add app/src/debug/
git commit -m "feat: add /theme and /font-scale endpoints"
```

---

## Task 7: State Injection Endpoints

**Files:**
- Replace stub: `app/src/debug/kotlin/com/realmsoffate/game/debug/InjectionEndpoints.kt`

- [ ] **Step 1: Implement InjectionEndpoints**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/InjectionEndpoints.kt
package com.realmsoffate.game.debug

import android.os.Handler
import android.os.Looper
import com.realmsoffate.game.game.DisplayMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

object InjectionEndpoints {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var savedState: com.realmsoffate.game.game.GameUiState? = null

    private suspend fun <T> onMain(block: () -> T): T {
        val deferred = CompletableDeferred<T>()
        mainHandler.post {
            try { deferred.complete(block()) }
            catch (e: Exception) { deferred.completeExceptionally(e) }
        }
        return withTimeout(10_000) { deferred.await() }
    }

    fun register() {
        DebugServer.route("POST", "/inject") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val vm = DebugBridge.requireVm()
            var count = 0

            onMain {
                // Save original state for reset
                if (savedState == null) savedState = vm.ui.value

                val state = vm.ui.value
                var char = state.character ?: return@onMain
                var newState = state

                body.forEach { (key, value) ->
                    val v = value.jsonPrimitive
                    when (key) {
                        "character.hp" -> { char = char.copy().also { it.hp = v.int }; count++ }
                        "character.maxHp" -> { char = char.copy().also { it.maxHp = v.int }; count++ }
                        "character.gold" -> { char = char.copy().also { it.gold = v.int }; count++ }
                        "character.level" -> { char = char.copy().also { it.level = v.int }; count++ }
                        "character.xp" -> { char = char.copy().also { it.xp = v.int }; count++ }
                        "character.ac" -> { char = char.copy().also { it.ac = v.int }; count++ }
                        "character.name" -> { char = char.copy().also { it.name = v.content }; count++ }
                        "character.conditions" -> {
                            char = char.copy().also {
                                it.conditions.clear()
                                value.jsonArray.forEach { c -> it.conditions.add(c.jsonPrimitive.content) }
                            }
                            count++
                        }
                        "turns" -> { newState = newState.copy(turns = v.int); count++ }
                        "isGenerating" -> { newState = newState.copy(isGenerating = v.boolean); count++ }
                        "currentScene" -> { newState = newState.copy(currentScene = v.content); count++ }
                        "morality" -> { newState = newState.copy(morality = v.int); count++ }
                    }
                }

                vm.debugInjectState(newState.copy(character = char))
            }

            HttpResponse.json("""{"ok":true,"fieldsSet":$count}""")
        }

        DebugServer.route("POST", "/inject/messages") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val messagesJson = body["messages"]?.jsonArray
                ?: return@route HttpResponse.error(400, "Missing 'messages' array")

            val messages = messagesJson.map { elem ->
                val obj = elem.jsonObject
                val type = obj["type"]?.jsonPrimitive?.content ?: "system"
                when (type) {
                    "player" -> DisplayMessage.Player(
                        text = obj["text"]?.jsonPrimitive?.content ?: ""
                    )
                    "npcDialog", "narration" -> DisplayMessage.Narration(
                        text = obj["text"]?.jsonPrimitive?.content ?: "",
                        scene = obj["scene"]?.jsonPrimitive?.content ?: "default",
                        sceneDesc = "",
                        hpBefore = 0, hpAfter = 0, maxHp = 0,
                        goldBefore = 0, goldAfter = 0, xpGained = 0,
                        conditionsAdded = emptyList(), conditionsRemoved = emptyList(),
                        itemsGained = emptyList(), itemsRemoved = emptyList(),
                        moralDelta = 0, repDeltas = emptyList(),
                        segments = emptyList()
                    )
                    "event" -> DisplayMessage.Event(
                        icon = obj["icon"]?.jsonPrimitive?.content ?: "⚡",
                        title = obj["title"]?.jsonPrimitive?.content ?: "",
                        text = obj["text"]?.jsonPrimitive?.content ?: ""
                    )
                    else -> DisplayMessage.System(
                        text = obj["text"]?.jsonPrimitive?.content ?: ""
                    )
                }
            }

            val vm = DebugBridge.requireVm()
            onMain {
                if (savedState == null) savedState = vm.ui.value
                val state = vm.ui.value
                vm.debugInjectState(state.copy(messages = state.messages + messages))
            }

            HttpResponse.json("""{"ok":true,"injected":${messages.size}}""")
        }

        DebugServer.route("POST", "/inject/reset") { req ->
            val vm = DebugBridge.requireVm()
            val saved = savedState ?: return@route HttpResponse.json("""{"ok":true,"restored":false,"detail":"no saved state"}""")
            onMain { vm.debugInjectState(saved) }
            savedState = null
            HttpResponse.json("""{"ok":true,"restored":true}""")
        }
    }
}
```

Note: This requires adding a `debugInjectState(state: GameUiState)` method to `GameViewModel`:

```kotlin
// Add to GameViewModel.kt:
fun debugInjectState(state: GameUiState) {
    _ui.value = state
}
```

This method should only be used by the debug bridge. In release builds, nothing calls it.

- [ ] **Step 2: Add debugInjectState to GameViewModel**

In `GameViewModel.kt`, add after the existing `_ui` declaration area:

```kotlin
/** Debug bridge only: replace the entire UI state for testing edge cases. */
fun debugInjectState(state: GameUiState) {
    _ui.value = state
}
```

- [ ] **Step 3: Build and verify**

```bash
gradle assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/debug/ app/src/main/
git commit -m "feat: add /inject, /inject/messages, /inject/reset state injection endpoints"
```

---

## Task 8: IssueChecker

**Files:**
- Create: `app/src/debug/kotlin/com/realmsoffate/game/debug/IssueChecker.kt`
- Replace stub: `app/src/debug/kotlin/com/realmsoffate/game/debug/IssueCheckerEndpoints.kt`

- [ ] **Step 1: Create IssueChecker**

The checker walks the accessibility node tree and samples actual rendered pixels from a screen bitmap to compute real contrast ratios.

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/IssueChecker.kt
package com.realmsoffate.game.debug

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.json.*
import kotlin.math.max
import kotlin.math.pow

data class Issue(
    val severity: String,  // high, medium, low
    val type: String,
    val element: String,
    val bounds: Rect?,
    val detail: String
)

object IssueChecker {
    private const val MIN_TOUCH_TARGET_DP = 48
    private const val WCAG_AA_NORMAL = 4.5
    private const val WCAG_AA_LARGE = 3.0
    private const val LARGE_TEXT_SP = 18

    fun runChecks(rootNode: AccessibilityNodeInfo, bitmap: Bitmap, density: Float): List<Issue> {
        val issues = mutableListOf<Issue>()
        walkTree(rootNode, bitmap, density, issues)
        return issues
    }

    private fun walkTree(
        node: AccessibilityNodeInfo,
        bitmap: Bitmap,
        density: Float,
        issues: MutableList<Issue>
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val label = node.text?.toString()
            ?: node.contentDescription?.toString()
            ?: node.className?.toString()?.substringAfterLast(".")
            ?: "unknown"

        // Check: Touch target size for interactive elements
        if (node.isClickable || node.isCheckable) {
            val widthDp = (bounds.width() / density)
            val heightDp = (bounds.height() / density)
            if (widthDp < MIN_TOUCH_TARGET_DP || heightDp < MIN_TOUCH_TARGET_DP) {
                issues.add(Issue(
                    severity = "medium",
                    type = "touch-target",
                    element = label,
                    bounds = bounds,
                    detail = "Size ${widthDp.toInt()}x${heightDp.toInt()}dp, minimum ${MIN_TOUCH_TARGET_DP}x${MIN_TOUCH_TARGET_DP}dp"
                ))
            }
        }

        // Check: Contrast ratio for text elements
        if (node.text != null && node.text.isNotBlank() && boundsInBitmap(bounds, bitmap)) {
            val textColor = sampleTextColor(bitmap, bounds)
            val bgColor = sampleBackgroundColor(bitmap, bounds)
            if (textColor != null && bgColor != null) {
                val ratio = contrastRatio(textColor, bgColor)
                val threshold = WCAG_AA_NORMAL // conservative — treat all as normal text
                if (ratio < threshold) {
                    issues.add(Issue(
                        severity = "high",
                        type = "contrast",
                        element = label,
                        bounds = bounds,
                        detail = "Contrast ratio %.1f:1 (min %.1f:1), text=#%06X on bg=#%06X".format(
                            ratio, threshold, textColor and 0xFFFFFF, bgColor and 0xFFFFFF
                        )
                    ))
                }
            }
        }

        // Check: Zero-size elements
        if (bounds.width() == 0 || bounds.height() == 0) {
            if (node.text != null || node.contentDescription != null) {
                issues.add(Issue(
                    severity = "low",
                    type = "zero-size",
                    element = label,
                    bounds = bounds,
                    detail = "Element has content but ${bounds.width()}x${bounds.height()} bounds"
                ))
            }
        }

        // Check: Off-screen elements with content
        if (bounds.bottom < 0 || bounds.top > bitmap.height || bounds.right < 0 || bounds.left > bitmap.width) {
            if (node.isClickable && node.isVisibleToUser) {
                issues.add(Issue(
                    severity = "low",
                    type = "off-screen",
                    element = label,
                    bounds = bounds,
                    detail = "Interactive element is off-screen"
                ))
            }
        }

        // Check: Overlapping interactive elements (compare with siblings)
        // This is O(n^2) but the tree is small enough that it's fine

        // Recurse
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                walkTree(child, bitmap, density, issues)
            }
        }
    }

    /** Sample the most common color in the center of the text bounds (likely the text color). */
    private fun sampleTextColor(bitmap: Bitmap, bounds: Rect): Int? {
        val cx = bounds.centerX().coerceIn(0, bitmap.width - 1)
        val cy = bounds.centerY().coerceIn(0, bitmap.height - 1)
        // Sample a small cross pattern at the center
        val pixels = mutableListOf<Int>()
        for (dx in -2..2) {
            for (dy in -2..2) {
                val x = (cx + dx).coerceIn(0, bitmap.width - 1)
                val y = (cy + dy).coerceIn(0, bitmap.height - 1)
                pixels.add(bitmap.getPixel(x, y))
            }
        }
        // The text color is likely the darkest or lightest pixel (depending on theme)
        // Return the pixel that differs most from the background
        val bgColor = sampleBackgroundColor(bitmap, bounds) ?: return null
        return pixels.maxByOrNull { colorDistance(it, bgColor) }
    }

    /** Sample the background color from the edges of the bounds. */
    private fun sampleBackgroundColor(bitmap: Bitmap, bounds: Rect): Int? {
        val samples = mutableListOf<Int>()
        // Sample from corners and edges (outside text area, inside bounds)
        val margin = 2
        val corners = listOf(
            bounds.left + margin to bounds.top + margin,
            bounds.right - margin to bounds.top + margin,
            bounds.left + margin to bounds.bottom - margin,
            bounds.right - margin to bounds.bottom - margin,
        )
        for ((x, y) in corners) {
            val sx = x.coerceIn(0, bitmap.width - 1)
            val sy = y.coerceIn(0, bitmap.height - 1)
            samples.add(bitmap.getPixel(sx, sy))
        }
        // Most common color among samples (background is usually uniform)
        return samples.groupBy { it }.maxByOrNull { it.value.size }?.key
    }

    private fun boundsInBitmap(bounds: Rect, bitmap: Bitmap): Boolean {
        return bounds.left >= 0 && bounds.top >= 0 &&
            bounds.right <= bitmap.width && bounds.bottom <= bitmap.height &&
            bounds.width() > 4 && bounds.height() > 4
    }

    /** WCAG 2.1 relative luminance contrast ratio. */
    fun contrastRatio(color1: Int, color2: Int): Double {
        val l1 = relativeLuminance(color1)
        val l2 = relativeLuminance(color2)
        val lighter = max(l1, l2)
        val darker = if (lighter == l1) l2 else l1
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        val r = linearize(Color.red(color) / 255.0)
        val g = linearize(Color.green(color) / 255.0)
        val b = linearize(Color.blue(color) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun colorDistance(c1: Int, c2: Int): Double {
        val dr = Color.red(c1) - Color.red(c2)
        val dg = Color.green(c1) - Color.green(c2)
        val db = Color.blue(c1) - Color.blue(c2)
        return (dr * dr + dg * dg + db * db).toDouble()
    }

    fun issuesToJson(issues: List<Issue>): String {
        var high = 0; var medium = 0; var low = 0
        issues.forEach { when (it.severity) { "high" -> high++; "medium" -> medium++; else -> low++ } }
        return buildJsonObject {
            put("issues", buildJsonArray {
                issues.forEach { issue ->
                    add(buildJsonObject {
                        put("severity", issue.severity)
                        put("type", issue.type)
                        put("element", issue.element)
                        issue.bounds?.let { b ->
                            put("bounds", buildJsonArray {
                                add(b.left); add(b.top); add(b.right); add(b.bottom)
                            })
                        }
                        put("detail", issue.detail)
                    })
                }
            })
            put("summary", buildJsonObject {
                put("high", high); put("medium", medium); put("low", low)
                put("passed", "n/a") // we don't count passed checks individually
            })
        }.let { Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), it) }
    }
}
```

- [ ] **Step 2: Implement IssueCheckerEndpoints**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/IssueCheckerEndpoints.kt
package com.realmsoffate.game.debug

import android.graphics.Bitmap
import kotlinx.coroutines.delay

object IssueCheckerEndpoints {
    fun register() {
        DebugServer.route("GET", "/checks") { _ ->
            val activity = DebugBridge.requireActivity()
            val bitmap = screenshotBitmap()
            val density = activity.resources.displayMetrics.density
            val rootNode = activity.window.decorView.createAccessibilityNodeInfo()

            val issues = IssueChecker.runChecks(rootNode, bitmap, density)
            bitmap.recycle()

            HttpResponse.json(IssueChecker.issuesToJson(issues))
        }

        DebugServer.route("GET", "/checks/both") { _ ->
            val activity = DebugBridge.requireActivity()
            val density = activity.resources.displayMetrics.density
            val original = DebugHook.themeOverride.value

            // Light
            DebugHook.themeOverride.value = false
            delay(500)
            val lightBitmap = screenshotBitmap()
            val lightRoot = activity.window.decorView.createAccessibilityNodeInfo()
            val lightIssues = IssueChecker.runChecks(lightRoot, lightBitmap, density)
            lightBitmap.recycle()

            // Dark
            DebugHook.themeOverride.value = true
            delay(500)
            val darkBitmap = screenshotBitmap()
            val darkRoot = activity.window.decorView.createAccessibilityNodeInfo()
            val darkIssues = IssueChecker.runChecks(darkRoot, darkBitmap, density)
            darkBitmap.recycle()

            // Restore
            DebugHook.themeOverride.value = original

            val json = """{"light":${IssueChecker.issuesToJson(lightIssues)},"dark":${IssueChecker.issuesToJson(darkIssues)}}"""
            HttpResponse.json(json)
        }
    }

    private suspend fun screenshotBitmap(): Bitmap {
        val bytes = ScreenshotEndpoints.captureScreenshot()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
```

- [ ] **Step 3: Build and verify**

```bash
gradle assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/debug/
git commit -m "feat: add IssueChecker with pixel-sampling contrast and /checks endpoints"
```

---

## Task 9: Event Bus + Crash Handler

**Files:**
- Replace stub: `app/src/debug/kotlin/com/realmsoffate/game/debug/DebugEventBus.kt`

- [ ] **Step 1: Implement DebugEventBus**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/DebugEventBus.kt
package com.realmsoffate.game.debug

import android.util.Log
import com.realmsoffate.game.game.GameUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "RealmsDebug"

object DebugEventBus {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        installCrashHandler()
        // State observation starts when ViewModel is attached
        DebugHook.onAttach = { activity, vm ->
            DebugBridge.activity = activity
            DebugBridge.viewModel = vm
            observeState(vm)
            Log.i(TAG, """{"event":"attached","screen":"${vm.screen.value}"}""")
        }
    }

    private fun observeState(vm: com.realmsoffate.game.game.GameViewModel) {
        // Screen changes
        scope.launch {
            var prev = vm.screen.value
            vm.screen.collect { screen ->
                if (screen != prev) {
                    log("""{"event":"screenChange","from":"${prev.name.lowercase()}","to":"${screen.name.lowercase()}"}""")
                    prev = screen
                }
            }
        }

        // Key state changes
        scope.launch {
            var prevState: GameUiState? = null
            vm.ui.collect { state ->
                val prev = prevState
                if (prev != null) {
                    if (prev.character?.hp != state.character?.hp) {
                        log("""{"event":"stateChange","field":"character.hp","from":${prev.character?.hp},"to":${state.character?.hp}}""")
                    }
                    if (prev.character?.gold != state.character?.gold) {
                        log("""{"event":"stateChange","field":"character.gold","from":${prev.character?.gold},"to":${state.character?.gold}}""")
                    }
                    if (prev.turns != state.turns) {
                        log("""{"event":"stateChange","field":"turn","from":${prev.turns},"to":${state.turns}}""")
                    }
                    if (prev.isGenerating != state.isGenerating) {
                        log("""{"event":"generating","status":"${if (state.isGenerating) "start" else "complete"}"}""")
                    }
                    if (prev.messages.size != state.messages.size) {
                        val newCount = state.messages.size - prev.messages.size
                        if (newCount > 0) {
                            val lastMsg = state.messages.last()
                            val preview = when (lastMsg) {
                                is com.realmsoffate.game.game.DisplayMessage.Player -> lastMsg.text.take(60)
                                is com.realmsoffate.game.game.DisplayMessage.Narration -> lastMsg.text.take(60)
                                is com.realmsoffate.game.game.DisplayMessage.Event -> lastMsg.title
                                is com.realmsoffate.game.game.DisplayMessage.System -> lastMsg.text.take(60)
                            }
                            log("""{"event":"messageAdded","count":$newCount,"preview":"${preview.replace("\"", "\\\"").replace("\n", " ")}"}""")
                        }
                    }
                    val prevOverlay = overlayName(prev)
                    val newOverlay = overlayName(state)
                    if (prevOverlay != newOverlay) {
                        if (newOverlay != null) log("""{"event":"overlayShown","overlay":"$newOverlay"}""")
                        else log("""{"event":"overlayDismissed","overlay":"$prevOverlay"}""")
                    }
                    if (prev.error != state.error && state.error != null) {
                        log("""{"event":"error","message":"${state.error!!.replace("\"", "\\\"").take(200)}"}""")
                    }
                }
                prevState = state
            }
        }

        // Theme changes
        scope.launch {
            DebugHook.themeOverride.collect { mode ->
                val name = when (mode) { true -> "dark"; false -> "light"; null -> "system" }
                log("""{"event":"themeChange","mode":"$name"}""")
            }
        }
    }

    private fun overlayName(state: GameUiState): String? = when {
        state.preRoll != null -> "preRoll"
        state.deathSave != null -> "deathSave"
        state.combat != null -> "combat"
        state.travelState != null -> "travel"
        else -> null
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = throwable.stackTraceToString().take(500).replace("\"", "\\\"").replace("\n", "\\n")
                Log.e(TAG, """{"event":"crash","exception":"${throwable.javaClass.simpleName}","message":"${throwable.message?.take(200)?.replace("\"", "\\\"")}","stackTrace":"$trace"}""")
            } catch (_: Exception) {}
            default?.uncaughtException(thread, throwable)
        }
    }

    fun log(json: String) {
        Log.d(TAG, json)
    }
}
```

- [ ] **Step 2: Update DebugBridge.start() to not double-set onAttach**

The EventBus's `install()` now sets `onAttach`. Update `DebugBridge.start()` to let EventBus handle it:

```kotlin
// In DebugBridge.start(), change order:
fun start(app: Application) {
    Log.i(TAG, """{"event":"bridgeStarted","port":8735}""")
    DebugEventBus.install()  // sets onAttach with state observation
    DebugServer.start()
}
```

Remove the duplicate `DebugHook.onAttach = { ... }` from `DebugBridge.start()`.

- [ ] **Step 3: Build and verify**

```bash
gradle assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/debug/
git commit -m "feat: add DebugEventBus with state streaming, crash handler, and logcat push"
```

---

## Task 10: Macros

**Files:**
- Replace stub: `app/src/debug/kotlin/com/realmsoffate/game/debug/MacroEndpoints.kt`

- [ ] **Step 1: Implement MacroEndpoints**

```kotlin
// app/src/debug/kotlin/com/realmsoffate/game/debug/MacroEndpoints.kt
package com.realmsoffate.game.debug

import android.os.Handler
import android.os.Looper
import com.realmsoffate.game.game.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

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
        DebugServer.route("POST", "/macro/new-game") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val name = body["name"]?.jsonPrimitive?.content ?: "Test"
            val cls = body["class"]?.jsonPrimitive?.content ?: "fighter"
            val race = body["race"]?.jsonPrimitive?.content ?: "human"
            val skipFirstTurn = body["skipFirstTurn"]?.jsonPrimitive?.boolean ?: false

            val vm = DebugBridge.requireVm()

            // Navigate to character creation and set up the character
            onMain { vm.setScreen(Screen.CharacterCreation) }
            delay(200)

            // Use the ViewModel's character creation methods
            onMain {
                vm.debugCreateCharacter(name, cls, race)
            }
            delay(200)

            if (skipFirstTurn) {
                // Inject a canned first turn instead of calling AI
                onMain {
                    vm.debugInjectFirstTurn()
                }
            } else {
                // Actually start the game (will call AI)
                onMain { vm.startGame() }
            }

            delay(500)
            val state = vm.ui.value
            HttpResponse.json("""{"ok":true,"screen":"${vm.screen.value.name.lowercase()}","turn":${state.turns},"character":"${state.character?.name}"}""")
        }

        DebugServer.route("POST", "/macro/death") { _ ->
            val vm = DebugBridge.requireVm()
            onMain {
                val state = vm.ui.value
                state.character?.let { it.hp = 0 }
                vm.debugInjectState(state.copy(character = state.character))
                vm.setScreen(Screen.Death)
            }
            HttpResponse.json("""{"ok":true,"screen":"death"}""")
        }

        DebugServer.route("POST", "/macro/advance") { req ->
            val body = req.jsonBody() ?: return@route HttpResponse.error(400, "JSON body required")
            val turns = body["turns"]?.jsonPrimitive?.int ?: 1
            val mode = body["mode"]?.jsonPrimitive?.content ?: "canned"

            if (mode == "canned") {
                val vm = DebugBridge.requireVm()
                repeat(turns) { i ->
                    onMain { vm.debugInjectCannedTurn(i) }
                    delay(100)
                }
                val state = vm.ui.value
                HttpResponse.json("""{"ok":true,"turnsPlayed":$turns,"finalTurn":${state.turns}}""")
            } else {
                // Live mode: actually call AI for each turn
                HttpResponse.error(501, "Live advance not yet implemented. Use mode=canned.")
            }
        }
    }
}
```

Note: This requires adding helper methods to `GameViewModel`:
- `debugCreateCharacter(name, cls, race)` — sets up a character directly
- `debugInjectFirstTurn()` — injects a canned first turn
- `debugInjectCannedTurn(index)` — injects a canned turn
- `startGame()` — if not already public

These are debug-only entry points. Add them to the ViewModel with `// Debug bridge` comments. The canned turn data should be hardcoded placeholder narration — the point is speed, not realism.

- [ ] **Step 2: Add debug helper methods to GameViewModel**

Add to `GameViewModel.kt`:

```kotlin
// ── Debug bridge helpers ──────────────────────────────────────

fun debugCreateCharacter(name: String, cls: String, race: String) {
    val char = Character(name = name, race = race, cls = cls)
    _ui.value = _ui.value.copy(character = char)
    _screen.value = Screen.Game
}

fun debugInjectFirstTurn() {
    val state = _ui.value
    _ui.value = state.copy(
        turns = 1,
        currentScene = "town",
        currentSceneDesc = "Debug test scenario",
        messages = state.messages + DisplayMessage.Narration(
            text = "You arrive in the test town. The streets are quiet. A merchant waves from a stall, and a guard eyes you from the gate.",
            scene = "town", sceneDesc = "Debug test scenario",
            hpBefore = state.character?.hp ?: 10, hpAfter = state.character?.hp ?: 10,
            maxHp = state.character?.maxHp ?: 10,
            goldBefore = state.character?.gold ?: 25, goldAfter = state.character?.gold ?: 25,
            xpGained = 0, conditionsAdded = emptyList(), conditionsRemoved = emptyList(),
            itemsGained = emptyList(), itemsRemoved = emptyList(),
            moralDelta = 0, repDeltas = emptyList(), segments = emptyList()
        ),
        currentChoices = listOf(
            Choice(1, "Approach the merchant", "CHA"),
            Choice(2, "Talk to the guard", ""),
            Choice(3, "Explore the side streets", "DEX")
        )
    )
}

fun debugInjectCannedTurn(index: Int) {
    val state = _ui.value
    _ui.value = state.copy(
        turns = state.turns + 1,
        messages = state.messages + DisplayMessage.Narration(
            text = "Debug turn ${state.turns + 1}: The story continues. Nothing of note happens in this canned turn.",
            scene = state.currentScene, sceneDesc = state.currentSceneDesc,
            hpBefore = state.character?.hp ?: 10, hpAfter = state.character?.hp ?: 10,
            maxHp = state.character?.maxHp ?: 10,
            goldBefore = state.character?.gold ?: 25, goldAfter = state.character?.gold ?: 25,
            xpGained = 10, conditionsAdded = emptyList(), conditionsRemoved = emptyList(),
            itemsGained = emptyList(), itemsRemoved = emptyList(),
            moralDelta = 0, repDeltas = emptyList(), segments = emptyList()
        )
    )
}
```

Also ensure `setScreen` is public. If the `_screen` MutableStateFlow setter is private, add:

```kotlin
fun setScreen(s: Screen) { _screen.value = s }
```

And ensure `cancelPreRoll` exists:

```kotlin
fun cancelPreRoll() {
    _ui.value = _ui.value.copy(preRoll = null)
    isGenerating.set(false)
}
```

- [ ] **Step 3: Build and verify**

```bash
gradle assembleDebug 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add app/src/debug/ app/src/main/
git commit -m "feat: add /macro/new-game, /macro/death, /macro/advance endpoints"
```

---

## Task 11: Hardcoded Color Scanner (Gradle Task)

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add checkHardcodedColors task**

Add to the bottom of `app/build.gradle.kts`:

```kotlin
tasks.register("checkHardcodedColors") {
    description = "Scans UI composables for hardcoded Color usage instead of theme tokens"
    group = "verification"

    doLast {
        val uiDir = file("src/main/kotlin/com/realmsoffate/game/ui")
        if (!uiDir.exists()) {
            println("UI directory not found: $uiDir")
            return@doLast
        }

        val hardcodedPatterns = listOf(
            Regex("""Color\s*\.\s*(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)"""),
            Regex("""Color\s*\(\s*0x[0-9A-Fa-f]+\s*\)"""),
            Regex("""Color\s*\(\s*\d+\s*,\s*\d+\s*,\s*\d+"""),
        )
        // Patterns that are OK (theme-derived)
        val allowedPatterns = listOf(
            Regex("""MaterialTheme\.colorScheme\."""),
            Regex("""RealmsTheme\.colors\."""),
            Regex("""Color\.Transparent"""),
            Regex("""Color\.Unspecified"""),
        )

        var totalIssues = 0
        uiDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { lineNum, line ->
                    // Skip comments and imports
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("import")) return@forEachIndexed

                    for (pattern in hardcodedPatterns) {
                        if (pattern.containsMatchIn(line)) {
                            // Check it's not in an allowed context
                            val isAllowed = allowedPatterns.any { it.containsMatchIn(line) }
                            if (!isAllowed) {
                                println("WARNING: ${file.relativeTo(projectDir)}:${lineNum + 1}: Hardcoded color: ${line.trim()}")
                                totalIssues++
                            }
                        }
                    }
                }
            }

        if (totalIssues > 0) {
            println("\n$totalIssues hardcoded color(s) found. Use MaterialTheme.colorScheme.* or RealmsTheme.colors.* instead.")
        } else {
            println("No hardcoded colors found.")
        }
    }
}
```

- [ ] **Step 2: Run the scanner**

```bash
gradle checkHardcodedColors 2>&1
# Expected: list of any remaining hardcoded colors in ui/ files
```

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "feat: add checkHardcodedColors Gradle task for build-time color lint"
```

---

## Task 12: Integration — CLAUDE.md, Fish Function, Port Forwarding

**Files:**
- Modify: `CLAUDE.md`
- Create: `~/.config/fish/functions/debug.fish`

- [ ] **Step 1: Add Debug Bridge section to CLAUDE.md**

Add after the "Debugging workflow" section in CLAUDE.md:

```markdown
## Debug Bridge (debug builds only)

An HTTP server on port 8735 that gives Claude Code full access to the app's state, UI, and commands. Only active in debug builds.

### Setup (once per session)

```bash
adb -s emulator-5554 forward tcp:8735 tcp:8735
```

To start listening for real-time events:
```bash
adb -s emulator-5554 logcat -s RealmsDebug:V
```

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/state` | GET | Full game state as JSON |
| `/state/diff` | GET | Changes since last query |
| `/state/overlay` | GET | Active overlay + available actions |
| `/screenshot` | GET | PNG screenshot (`?format=base64` for JSON) |
| `/screenshot/both` | GET | Screenshots in both light and dark themes |
| `/checks` | GET | Automated visual/accessibility issue scan |
| `/checks/both` | GET | Issue scan in both themes |
| `/input` | POST | Submit player action: `{"text": "..."}` |
| `/confirm` | POST | Confirm active dice roll |
| `/cancel` | POST | Dismiss active overlay |
| `/navigate` | POST | Switch screen: `{"screen": "game"}` |
| `/tap` | POST | Tap element by label: `{"text": "Continue"}` or `{"contentDesc": "Settings"}` |
| `/theme` | POST | Switch theme: `{"mode": "dark"}` |
| `/font-scale` | POST | Set font scale: `{"scale": 1.5}` |
| `/inject` | POST | Override state fields: `{"character.hp": 0}` |
| `/inject/messages` | POST | Inject test messages |
| `/inject/reset` | POST | Restore real game state |
| `/macro/new-game` | POST | Skip to game: `{"name":"Test","class":"fighter","race":"human","skipFirstTurn":true}` |
| `/macro/advance` | POST | Auto-play turns: `{"turns":3,"mode":"canned"}` |
| `/macro/death` | POST | Trigger death screen |

### Post-deploy testing workflow

After any UI change:
1. `curl -X POST localhost:8735/macro/new-game -d '{"name":"Test","class":"fighter","race":"human","skipFirstTurn":true}'`
2. `curl localhost:8735/screenshot > screenshot.png` — visual check
3. `curl localhost:8735/checks/both` — automated issue scan
4. Fix any flagged issues
5. Test edge cases: `curl -X POST localhost:8735/inject -d '{"character.hp":0}'`

### Build-time color check

```bash
gradle checkHardcodedColors
```

Scans `ui/` composables for hardcoded `Color.XXX` instead of theme tokens.
```

- [ ] **Step 2: Create debug fish function**

```fish
# ~/.config/fish/functions/debug.fish
function debug --description "Interact with Realms debug bridge"
    set -l port 8735
    set -l base "http://localhost:$port"

    # Ensure port forwarding is active
    if not adb forward --list 2>/dev/null | string match -q "*$port*"
        adb forward tcp:$port tcp:$port 2>/dev/null
    end

    switch $argv[1]
        case state
            curl -s "$base/state" | python3 -m json.tool 2>/dev/null || curl -s "$base/state"
        case diff
            curl -s "$base/state/diff" | python3 -m json.tool 2>/dev/null || curl -s "$base/state/diff"
        case overlay
            curl -s "$base/state/overlay" | python3 -m json.tool 2>/dev/null || curl -s "$base/state/overlay"
        case screenshot
            set -l ts (date +%Y%m%d_%H%M%S)
            set -l out_dir (git rev-parse --show-toplevel 2>/dev/null; or pwd)"/screenshots"
            mkdir -p "$out_dir"
            set -l file "$out_dir/debug_$ts.png"
            curl -s "$base/screenshot" > "$file"
            echo "$file"
        case checks
            curl -s "$base/checks" | python3 -m json.tool 2>/dev/null || curl -s "$base/checks"
        case checks-both
            curl -s "$base/checks/both" | python3 -m json.tool 2>/dev/null || curl -s "$base/checks/both"
        case input
            curl -s -X POST "$base/input" -d "{\"text\":\"$argv[2..-1]\"}"
        case confirm
            curl -s -X POST "$base/confirm"
        case cancel
            curl -s -X POST "$base/cancel"
        case navigate
            curl -s -X POST "$base/navigate" -d "{\"screen\":\"$argv[2]\"}"
        case tap
            curl -s -X POST "$base/tap" -d "{\"text\":\"$argv[2..-1]\"}"
        case theme
            curl -s -X POST "$base/theme" -d "{\"mode\":\"$argv[2]\"}"
        case font-scale
            curl -s -X POST "$base/font-scale" -d "{\"scale\":$argv[2]}"
        case inject
            curl -s -X POST "$base/inject" -d "{\"$argv[2]\":$argv[3]}"
        case reset
            curl -s -X POST "$base/inject/reset"
        case new-game
            curl -s -X POST "$base/macro/new-game" -d '{"name":"Test","class":"fighter","race":"human","skipFirstTurn":true}'
        case death
            curl -s -X POST "$base/macro/death"
        case advance
            set -l n (test -n "$argv[2]"; and echo $argv[2]; or echo 1)
            curl -s -X POST "$base/macro/advance" -d "{\"turns\":$n,\"mode\":\"canned\"}"
        case events
            adb logcat -s RealmsDebug:V
        case '*'
            echo "Usage: debug <command>"
            echo ""
            echo "State:      state | diff | overlay"
            echo "Visual:     screenshot | checks | checks-both"
            echo "Commands:   input <text> | confirm | cancel | navigate <screen> | tap <label>"
            echo "Theme:      theme <light|dark|system> | font-scale <n>"
            echo "Injection:  inject <field> <value> | reset"
            echo "Macros:     new-game | death | advance [n]"
            echo "Events:     events (streams logcat)"
    end
end
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md ~/.config/fish/functions/debug.fish
git commit -m "feat: add Debug Bridge documentation and fish function"
```

---

## Final Verification

- [ ] **Step 1: Full build + deploy + smoke test**

```bash
gradle assembleDebug 2>&1 | tail -5
gradle installDebug
adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity
adb -s emulator-5554 forward tcp:8735 tcp:8735

# Smoke test each endpoint
curl localhost:8735/state
curl localhost:8735/screenshot > /tmp/test.png
curl -X POST localhost:8735/macro/new-game -d '{"name":"Test","class":"fighter","race":"human","skipFirstTurn":true}'
curl localhost:8735/state
curl localhost:8735/checks
curl -X POST localhost:8735/theme -d '{"mode":"dark"}'
curl localhost:8735/screenshot > /tmp/dark.png
curl -X POST localhost:8735/theme -d '{"mode":"system"}'
curl -X POST localhost:8735/inject -d '{"character.hp":0}'
curl localhost:8735/state/diff
curl -X POST localhost:8735/inject/reset
```

- [ ] **Step 2: Run tests to ensure no regressions**

```bash
gradle test 2>&1 | tail -10
```

- [ ] **Step 3: Run hardcoded color scanner**

```bash
gradle checkHardcodedColors
```

- [ ] **Step 4: Final commit if any fixes were needed**

```bash
git add -A && git commit -m "fix: address issues found during Debug Bridge smoke testing"
```
