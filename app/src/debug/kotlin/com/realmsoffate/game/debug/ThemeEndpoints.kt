package com.realmsoffate.game.debug

import kotlinx.serialization.json.jsonPrimitive

object ThemeEndpoints {

    fun register() {

        // POST /theme — switch theme mode: {"mode": "light"} | {"mode": "dark"} | {"mode": "system"}
        DebugServer.route("POST", "/theme") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body with 'mode' field")
            val mode = body["mode"]?.jsonPrimitive?.content
                ?: return@route HttpResponse.error(400, "Missing 'mode' field")

            val override: Boolean? = when (mode.lowercase()) {
                "light" -> false
                "dark" -> true
                "system" -> null
                else -> return@route HttpResponse.error(
                    400, "Invalid mode '$mode'. Valid values: light, dark, system"
                )
            }

            DebugHook.themeOverride.value = override
            val activeTheme = when (override) {
                false -> "light"
                true -> "dark"
                null -> "system"
            }

            HttpResponse.json("""{"ok":true,"activeTheme":"$activeTheme"}""")
        }

        // POST /font-scale — change in-app font scale: {"scale": 1.5}
        DebugServer.route("POST", "/font-scale") { req ->
            val body = req.jsonBody()
                ?: return@route HttpResponse.error(400, "Expected JSON body with 'scale' field")
            val scaleStr = body["scale"]?.jsonPrimitive?.content
                ?: return@route HttpResponse.error(400, "Missing 'scale' field")
            val scale = scaleStr.toFloatOrNull()
                ?: return@route HttpResponse.error(400, "Invalid scale value '$scaleStr': must be a number")

            if (scale < 0.5f || scale > 3.0f) {
                return@route HttpResponse.error(400, "Scale $scale out of range: must be between 0.5 and 3.0")
            }

            DebugHook.fontScaleOverride.value = scale

            HttpResponse.json("""{"ok":true,"scale":$scale}""")
        }
    }
}
