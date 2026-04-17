package com.realmsoffate.game.debug

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

object IssueCheckerEndpoints {

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

        // GET /checks — run visual/accessibility checks on the current screen
        DebugServer.route("GET", "/checks") { _ ->
            val activity = DebugBridge.requireActivity()

            // Capture screenshot → Bitmap
            val pngBytes = ScreenshotEndpoints.captureScreenshot()
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                ?: return@route HttpResponse.error(500, "Failed to decode screenshot bitmap")

            val owner = onMain { ComposeTreeHelper.findSemanticsOwner(activity.window.decorView) }
                ?: return@route HttpResponse.error(500, "Could not find Compose semantics owner")
            val nodes = onMain { ComposeTreeHelper.getAllNodes(owner) }

            val density = activity.resources.displayMetrics.density

            val issues = IssueChecker.runChecks(nodes, bitmap, density)
            bitmap.recycle()

            HttpResponse.json(IssueChecker.issuesToJson(issues))
        }

        // GET /checks/both — run checks in both light and dark themes
        DebugServer.route("GET", "/checks/both") { _ ->
            val activity = DebugBridge.requireActivity()
            val density = activity.resources.displayMetrics.density
            val originalTheme = DebugHook.themeOverride.value

            // ── Light theme ──────────────────────────────────────────────
            DebugHook.themeOverride.value = false
            delay(500)

            val lightBytes = ScreenshotEndpoints.captureScreenshot()
            val lightBitmap = BitmapFactory.decodeByteArray(lightBytes, 0, lightBytes.size)
                ?: return@route HttpResponse.error(500, "Failed to decode light-theme screenshot")
            val lightOwner = onMain { ComposeTreeHelper.findSemanticsOwner(activity.window.decorView) }
                ?: return@route HttpResponse.error(500, "Could not find Compose semantics owner (light)")
            val lightNodes = onMain { ComposeTreeHelper.getAllNodes(lightOwner) }
            val lightIssues = IssueChecker.runChecks(lightNodes, lightBitmap, density)
            lightBitmap.recycle()

            // ── Dark theme ───────────────────────────────────────────────
            DebugHook.themeOverride.value = true
            delay(500)

            val darkBytes = ScreenshotEndpoints.captureScreenshot()
            val darkBitmap = BitmapFactory.decodeByteArray(darkBytes, 0, darkBytes.size)
                ?: return@route HttpResponse.error(500, "Failed to decode dark-theme screenshot")
            val darkOwner = onMain { ComposeTreeHelper.findSemanticsOwner(activity.window.decorView) }
                ?: return@route HttpResponse.error(500, "Could not find Compose semantics owner (dark)")
            val darkNodes = onMain { ComposeTreeHelper.getAllNodes(darkOwner) }
            val darkIssues = IssueChecker.runChecks(darkNodes, darkBitmap, density)
            darkBitmap.recycle()

            // ── Restore original theme ───────────────────────────────────
            DebugHook.themeOverride.value = originalTheme

            val lightJson = IssueChecker.issuesToJson(lightIssues)
            val darkJson  = IssueChecker.issuesToJson(darkIssues)

            HttpResponse.json("""{"light":$lightJson,"dark":$darkJson}""")
        }
    }
}
