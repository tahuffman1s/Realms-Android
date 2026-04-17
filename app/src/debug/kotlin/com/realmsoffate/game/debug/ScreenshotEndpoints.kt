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

    suspend fun captureScreenshot(): ByteArray {
        val activity = DebugBridge.requireActivity()
        val window = activity.window
        val decorView = window.decorView

        val width: Int
        val height: Int
        val deferred = CompletableDeferred<Int>()

        // Read view dimensions on main thread
        val dimDeferred = CompletableDeferred<Pair<Int, Int>>()
        Handler(Looper.getMainLooper()).post {
            dimDeferred.complete(decorView.width to decorView.height)
        }
        val (w, h) = withTimeout(5_000) { dimDeferred.await() }
        width = w
        height = h

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        withTimeout(10_000) {
            PixelCopy.request(
                window,
                bitmap,
                { result -> deferred.complete(result) },
                Handler(Looper.getMainLooper())
            )
            val result = deferred.await()
            if (result != PixelCopy.SUCCESS) {
                throw IllegalStateException("PixelCopy failed with code $result")
            }
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    fun register() {

        // GET /screenshot — capture current screen as PNG (or base64 JSON with ?format=base64)
        DebugServer.route("GET", "/screenshot") { req ->
            val pngBytes = captureScreenshot()

            if (req.query["format"] == "base64") {
                val encoded = Base64.encodeToString(pngBytes, Base64.NO_WRAP)
                HttpResponse.json("""{"image":"$encoded"}""")
            } else {
                HttpResponse.png(pngBytes)
            }
        }

        // GET /screenshot/both — capture in light and dark themes, return both as base64 JSON
        DebugServer.route("GET", "/screenshot/both") { _ ->
            val originalTheme = DebugHook.themeOverride.value

            // Capture light
            DebugHook.themeOverride.value = false
            delay(500)
            val lightBytes = captureScreenshot()
            val lightEncoded = Base64.encodeToString(lightBytes, Base64.NO_WRAP)

            // Capture dark
            DebugHook.themeOverride.value = true
            delay(500)
            val darkBytes = captureScreenshot()
            val darkEncoded = Base64.encodeToString(darkBytes, Base64.NO_WRAP)

            // Restore original theme
            DebugHook.themeOverride.value = originalTheme

            HttpResponse.json("""{"light":"$lightEncoded","dark":"$darkEncoded"}""")
        }
    }
}
