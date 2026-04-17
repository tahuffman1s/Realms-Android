package com.realmsoffate.game.debug

import android.graphics.BitmapFactory
import kotlinx.coroutines.delay

object IssueCheckerEndpoints {

    fun register() {

        // GET /checks — run visual/accessibility checks on the current screen
        DebugServer.route("GET", "/checks") { _ ->
            val activity = DebugBridge.requireActivity()

            // Capture screenshot → Bitmap
            val pngBytes = ScreenshotEndpoints.captureScreenshot()
            val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                ?: return@route HttpResponse.error(500, "Failed to decode screenshot bitmap")

            // Accessibility tree root
            val rootNode = activity.window.decorView.createAccessibilityNodeInfo()
                ?: return@route HttpResponse.error(500, "Could not obtain accessibility node info")

            val density = activity.resources.displayMetrics.density

            val issues = IssueChecker.runChecks(rootNode, bitmap, density)
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
            val lightRoot = activity.window.decorView.createAccessibilityNodeInfo()
                ?: return@route HttpResponse.error(500, "Could not obtain accessibility node info (light)")
            val lightIssues = IssueChecker.runChecks(lightRoot, lightBitmap, density)
            lightBitmap.recycle()

            // ── Dark theme ───────────────────────────────────────────────
            DebugHook.themeOverride.value = true
            delay(500)

            val darkBytes = ScreenshotEndpoints.captureScreenshot()
            val darkBitmap = BitmapFactory.decodeByteArray(darkBytes, 0, darkBytes.size)
                ?: return@route HttpResponse.error(500, "Failed to decode dark-theme screenshot")
            val darkRoot = activity.window.decorView.createAccessibilityNodeInfo()
                ?: return@route HttpResponse.error(500, "Could not obtain accessibility node info (dark)")
            val darkIssues = IssueChecker.runChecks(darkRoot, darkBitmap, density)
            darkBitmap.recycle()

            // ── Restore original theme ───────────────────────────────────
            DebugHook.themeOverride.value = originalTheme

            val lightJson = IssueChecker.issuesToJson(lightIssues)
            val darkJson  = IssueChecker.issuesToJson(darkIssues)

            HttpResponse.json("""{"light":$lightJson,"dark":$darkJson}""")
        }
    }
}
