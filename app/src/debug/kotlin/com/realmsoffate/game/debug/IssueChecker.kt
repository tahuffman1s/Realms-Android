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

    /** Clamp the rect to bitmap bounds; returns null if entirely outside. */
    private fun Bitmap.clamp(r: Rect): Rect? {
        val clamped = Rect(
            max(0, r.left),
            max(0, r.top),
            min(width, r.right),
            min(height, r.bottom)
        )
        return if (clamped.isEmpty) null else clamped
    }

    /**
     * Sample the four corners of [bounds] and return the most common color
     * as the estimated background color.  Falls back to the first valid
     * corner if counts are tied.
     */
    private fun sampleBackground(bitmap: Bitmap, bounds: Rect): Int? {
        val clamped = bitmap.clamp(bounds) ?: return null
        val candidates = listOf(
            clamped.left  to clamped.top,
            clamped.right - 1 to clamped.top,
            clamped.left  to clamped.bottom - 1,
            clamped.right - 1 to clamped.bottom - 1
        ).filter { (x, y) -> bitmap.contains(x, y) }

        if (candidates.isEmpty()) return null

        val colors = candidates.map { (x, y) -> bitmap.getPixel(x, y) }
        return colors.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: colors.first()
    }

    /**
     * Sample a small cross pattern at the center of [bounds].
     * Returns the pixel that deviates most from [background] — that is
     * the text/foreground color.
     */
    private fun sampleTextColor(bitmap: Bitmap, bounds: Rect, background: Int): Int? {
        val cx = (bounds.left + bounds.right) / 2
        val cy = (bounds.top + bounds.bottom) / 2
        val radius = 3

        val samples = buildList {
            add(cx to cy)
            for (d in 1..radius) {
                add(cx + d to cy)
                add(cx - d to cy)
                add(cx to cy + d)
                add(cx to cy - d)
            }
        }.filter { (x, y) -> bitmap.contains(x, y) }

        if (samples.isEmpty()) return null

        return samples
            .map { (x, y) -> bitmap.getPixel(x, y) }
            .maxByOrNull { contrastRatio(it, background) }
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
            if (nodeTextContent != null && isVisible &&
                Rect.intersects(boundsInScreen, bitmapRect)
            ) {
                val bg = sampleBackground(bitmap, boundsInScreen)
                val fg = if (bg != null) sampleTextColor(bitmap, boundsInScreen, bg) else null

                if (bg != null && fg != null) {
                    val ratio = contrastRatio(fg, bg)
                    if (ratio < 4.5) {
                        issues.add(
                            Issue(
                                severity = "high",
                                type = "contrast",
                                element = elemLabel,
                                bounds = Rect(boundsInScreen),
                                detail = ("Contrast ratio %.2f:1 is below the WCAG 4.5:1 threshold " +
                                        "(fg=#%06X bg=#%06X)").format(
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
