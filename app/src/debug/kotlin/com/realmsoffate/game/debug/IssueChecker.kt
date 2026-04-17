package com.realmsoffate.game.debug

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.ui.semantics.SemanticsNode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class Issue(
    val severity: String,  // high, medium, low
    val type: String,      // contrast, touch-target, zero-size, off-screen
    val element: String,   // label of the element
    val bounds: Rect?,
    val detail: String
)

object IssueChecker {

    // ── WCAG 2.1 math ──────────────────────────────────────────────────────

    private fun linearize(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun relativeLuminance(color: Int): Double {
        val r = linearize(Color.red(color) / 255.0)
        val g = linearize(Color.green(color) / 255.0)
        val b = linearize(Color.blue(color) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(color1: Int, color2: Int): Double {
        val l1 = relativeLuminance(color1)
        val l2 = relativeLuminance(color2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // ── Pixel sampling helpers ─────────────────────────────────────────────

    /** Returns true if (x, y) is within bitmap bounds. */
    private fun Bitmap.contains(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height

    /** Squared Euclidean distance in RGB space. */
    private fun colorDistSq(a: Int, b: Int): Int {
        val dr = Color.red(a) - Color.red(b)
        val dg = Color.green(a) - Color.green(b)
        val db = Color.blue(a) - Color.blue(b)
        return dr * dr + dg * dg + db * db
    }

    /**
     * Sample a dense grid inside [bounds], then split pixels into background
     * (the dominant color cluster) and foreground (anything sufficiently
     * different). Returns `(foreground, background)` or null if sampling
     * failed or only one color cluster was found (meaning we couldn't
     * distinguish text from its background — skip the check instead of
     * reporting a false positive).
     *
     * The inner 60% of the bounds is sampled to avoid borders, shadows,
     * and edge anti-aliasing.
     */
    private fun sampleFgBg(bitmap: Bitmap, bounds: Rect): Pair<Int, Int>? {
        // Shrink to inner 60% to avoid borders/shadows
        val insetX = (bounds.width() * 0.2f).toInt()
        val insetY = (bounds.height() * 0.2f).toInt()
        val inner = Rect(
            max(0, bounds.left + insetX),
            max(0, bounds.top + insetY),
            min(bitmap.width, bounds.right - insetX),
            min(bitmap.height, bounds.bottom - insetY)
        )
        if (inner.isEmpty || inner.width() < 4 || inner.height() < 4) return null

        // Sample a grid of points (up to ~100 samples)
        val stepX = max(1, inner.width() / 10)
        val stepY = max(1, inner.height() / 10)
        val pixels = mutableListOf<Int>()
        var y = inner.top
        while (y < inner.bottom) {
            var x = inner.left
            while (x < inner.right) {
                if (bitmap.contains(x, y)) pixels.add(bitmap.getPixel(x, y))
                x += stepX
            }
            y += stepY
        }
        if (pixels.size < 4) return null

        // Find the most common color (background)
        val bg = pixels.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: return null

        // Color distance threshold: two colors must differ by at least this
        // much in RGB space to be considered distinct. 30^2 * 3 channels = 2700
        // catches subtle text but ignores sub-pixel anti-aliasing noise.
        val threshold = 2700

        // Find the most distant pixel from bg that appears at least twice
        // (single outlier pixels are noise from anti-aliasing)
        val nonBgPixels = pixels
            .filter { colorDistSq(it, bg) > threshold }
        if (nonBgPixels.isEmpty()) return null // only one color cluster — can't determine contrast

        // The foreground color is the most common non-background pixel
        val fg = nonBgPixels.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: return null

        // Sanity: if fg appeared only once, it's probably noise
        val fgCount = nonBgPixels.count { it == fg }
        if (fgCount < 2) return null

        return fg to bg
    }

    // ── Public API ─────────────────────────────────────────────────────────

    fun runChecks(nodes: List<SemanticsNode>, bitmap: Bitmap, density: Float): List<Issue> {
        val issues = mutableListOf<Issue>()
        val bitmapRect = Rect(0, 0, bitmap.width, bitmap.height)

        for (node in nodes) {
            val boundsInScreen = ComposeTreeHelper.boundsInWindow(node)
            val elemLabel = ComposeTreeHelper.nodeLabel(node).take(60)

            // ── Check 3: Zero-size elements ────────────────────────────────
            val hasContent = ComposeTreeHelper.nodeText(node) != null ||
                    ComposeTreeHelper.nodeContentDesc(node) != null
            if (hasContent && (boundsInScreen.width() == 0 || boundsInScreen.height() == 0)) {
                issues.add(
                    Issue(
                        severity = "low",
                        type = "zero-size",
                        element = elemLabel,
                        bounds = Rect(boundsInScreen),
                        detail = "Element has content but bounds are " +
                                "${boundsInScreen.width()}×${boundsInScreen.height()}px"
                    )
                )
                continue // no point running other checks on a zero-size node
            }

            val isInteractive = ComposeTreeHelper.isClickable(node)
            val isVisible = ComposeTreeHelper.isVisible(node)

            // ── Check 4: Off-screen interactive elements ───────────────────
            if (isInteractive && isVisible && !Rect.intersects(boundsInScreen, bitmapRect)) {
                issues.add(
                    Issue(
                        severity = "low",
                        type = "off-screen",
                        element = elemLabel,
                        bounds = Rect(boundsInScreen),
                        detail = "Interactive element is entirely outside the visible area " +
                                "(bounds: ${boundsInScreen.left},${boundsInScreen.top}," +
                                "${boundsInScreen.right},${boundsInScreen.bottom})"
                    )
                )
            }

            // ── Check 2: Touch target size ─────────────────────────────────
            if (isInteractive) {
                val widthDp  = boundsInScreen.width()  / density
                val heightDp = boundsInScreen.height() / density
                if (widthDp < 48f || heightDp < 48f) {
                    issues.add(
                        Issue(
                            severity = "medium",
                            type = "touch-target",
                            element = elemLabel,
                            bounds = Rect(boundsInScreen),
                            detail = "Touch target is %.1fdp × %.1fdp (minimum 48×48dp)".format(
                                widthDp, heightDp
                            )
                        )
                    )
                }
            }

            // ── Check 1: WCAG contrast (text nodes only) ───────────────────
            val nodeTextContent = ComposeTreeHelper.nodeText(node)
            // Skip emoji-only nodes — their colors are system-rendered, not ours
            val isEmojiOnly = nodeTextContent != null &&
                nodeTextContent.all { it.isSurrogate() || it.code > 0x2600 || it.isWhitespace() }
            if (nodeTextContent != null && !isEmojiOnly && isVisible &&
                Rect.intersects(boundsInScreen, bitmapRect)
            ) {
                val fgBg = sampleFgBg(bitmap, boundsInScreen)
                // fgBg is null when only one color cluster was found — meaning
                // we couldn't distinguish text from background. Skip rather
                // than report a false positive.
                if (fgBg != null) {
                    val (fg, bg) = fgBg
                    val ratio = contrastRatio(fg, bg)
                    // Two tiers:
                    // - Below 2.0:1 is genuinely unreadable (high) — catches real
                    //   bugs like white-on-white, invisible text, broken themes
                    // - 2.0–3.0:1 is marginal (medium) — might be intentional
                    //   styled text but worth a look
                    // - Above 3.0:1 is fine — WCAG 4.5:1 is too strict for a
                    //   game UI with intentionally colored/styled text
                    val severity = when {
                        ratio < 2.0 -> "high"
                        ratio < 3.0 -> "medium"
                        else -> null
                    }
                    if (severity != null) {
                        issues.add(
                            Issue(
                                severity = severity,
                                type = "contrast",
                                element = elemLabel,
                                bounds = Rect(boundsInScreen),
                                detail = ("Contrast ratio %.2f:1 (fg=#%06X bg=#%06X)").format(
                                            ratio,
                                            fg and 0xFFFFFF,
                                            bg and 0xFFFFFF
                                        )
                            )
                        )
                    }
                }
            }
        }

        return issues
    }

    // ── JSON serialisation ──────────────────────────────────────────────────

    fun issuesToJson(issues: List<Issue>): String {
        val highCount   = issues.count { it.severity == "high"   }
        val mediumCount = issues.count { it.severity == "medium" }
        val lowCount    = issues.count { it.severity == "low"    }

        val issuesJson = issues.joinToString(",\n    ") { issue ->
            val boundsJson = if (issue.bounds != null) {
                "[${issue.bounds.left},${issue.bounds.top},${issue.bounds.right},${issue.bounds.bottom}]"
            } else "null"
            val escapedElement = issue.element.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedDetail  = issue.detail.replace("\\", "\\\\").replace("\"", "\\\"")
            """{"severity":"${issue.severity}","type":"${issue.type}","element":"$escapedElement","bounds":$boundsJson,"detail":"$escapedDetail"}"""
        }

        val issuesArray = if (issues.isEmpty()) "[]" else "[\n    $issuesJson\n  ]"

        return """{
  "issues": $issuesArray,
  "summary": {"high":$highCount,"medium":$mediumCount,"low":$lowCount}
}"""
    }
}
