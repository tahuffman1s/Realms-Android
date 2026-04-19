@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.realmsoffate.game.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream

object DescribeEndpoints {

    private val mainHandler = Handler(Looper.getMainLooper())

    private suspend fun <T> onMain(block: () -> T): T {
        val deferred = CompletableDeferred<T>()
        mainHandler.post {
            try { deferred.complete(block()) }
            catch (e: Exception) { deferred.completeExceptionally(e) }
        }
        return withTimeout(10_000) { deferred.await() }
    }

    // ── App-only screenshot (crops system bars) ────────────────────────

    private suspend fun captureAppBitmap(
        topInset: Int, bottomInset: Int, width: Int, height: Int
    ): Bitmap {
        val activity = DebugBridge.requireActivity()
        val cropHeight = height - topInset - bottomInset
        val bitmap = Bitmap.createBitmap(width, cropHeight, Bitmap.Config.ARGB_8888)
        val srcRect = Rect(0, topInset, width, topInset + cropHeight)

        val deferred = CompletableDeferred<Int>()
        withTimeout(10_000) {
            PixelCopy.request(
                activity.window, srcRect, bitmap,
                { deferred.complete(it) }, mainHandler
            )
            val result = deferred.await()
            if (result != PixelCopy.SUCCESS) {
                throw IllegalStateException("PixelCopy failed with code $result")
            }
        }
        return bitmap
    }

    private suspend fun captureAppScreenshot(
        topInset: Int, bottomInset: Int, width: Int, height: Int
    ): String {
        val bitmap = captureAppBitmap(topInset, bottomInset, width, height)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ── Annotation colors ────────────────────────────────────────────────

    private fun typeColor(type: String): Int = when (type) {
        "button"      -> 0xFF4287F5.toInt()
        "text"        -> 0xFF4CAF50.toInt()
        "input"       -> 0xFFFF9800.toInt()
        "image"       -> 0xFF9C27B0.toInt()
        "icon-button" -> 0xFFE040FB.toInt()
        "clickable"   -> 0xFF00BCD4.toInt()
        "checkbox", "switch", "radio" -> 0xFFFFEB3B.toInt()
        "tab"         -> 0xFF009688.toInt()
        "dropdown"    -> 0xFFFF5722.toInt()
        else          -> 0xFF9E9E9E.toInt()
    }

    private fun typeAbbrev(type: String): String = when (type) {
        "button"      -> "btn"
        "text"        -> "txt"
        "input"       -> "inp"
        "image"       -> "img"
        "icon-button" -> "icn"
        "clickable"   -> "click"
        "checkbox"    -> "chk"
        "switch"      -> "sw"
        "radio"       -> "rad"
        "dropdown"    -> "drop"
        "container"   -> "box"
        else          -> type
    }

    // ── Source location resolution (build-time index) ──────────────────

    /** Look up source file:line for an element using the build-time SourceIndex.
     *  Tries exact match first, then longest substring match. */
    private fun findSource(text: String?, desc: String?): String? {
        // Exact match
        text?.let { SourceIndex.textToSource[it] }?.let { return it }
        desc?.let { SourceIndex.textToSource[it] }?.let { return it }

        // Substring: find longest indexed key contained in element text
        fun bestSubstring(target: String): String? {
            var best: String? = null
            var bestLen = 2 // minimum match length
            for ((key, value) in SourceIndex.textToSource) {
                if (key.length > bestLen && target.contains(key, ignoreCase = true)) {
                    bestLen = key.length; best = value
                }
            }
            return best
        }
        text?.let { bestSubstring(it) }?.let { return it }
        desc?.let { bestSubstring(it) }?.let { return it }
        return null
    }

    // ── Type inference ───────────────────────────────────────────────────

    private fun inferType(node: SemanticsNode): String {
        val role = node.config.getOrNull(SemanticsProperties.Role)
        if (role != null) return when (role) {
            Role.Button -> "button"
            Role.Checkbox -> "checkbox"
            Role.Switch -> "switch"
            Role.RadioButton -> "radio"
            Role.Tab -> "tab"
            Role.Image -> "image"
            Role.DropdownList -> "dropdown"
            else -> role.toString().lowercase()
        }
        val hasText = ComposeTreeHelper.nodeText(node) != null
        val hasDesc = ComposeTreeHelper.nodeContentDesc(node) != null
        val clickable = ComposeTreeHelper.isClickable(node)
        val hasEditable = node.config.getOrNull(SemanticsProperties.EditableText) != null
        return when {
            hasEditable -> "input"
            hasText && clickable -> "button"
            hasText -> "text"
            hasDesc && clickable -> "icon-button"
            hasDesc -> "image"
            clickable -> "clickable"
            else -> "container"
        }
    }

    // ── Value formatting ─────────────────────────────────────────────────

    private fun colorToHex(c: Color): String {
        if (c == Color.Transparent) return "transparent"
        if (c == Color.Unspecified) return "unspecified"
        val r = (c.red * 255).toInt().coerceIn(0, 255)
        val g = (c.green * 255).toInt().coerceIn(0, 255)
        val b = (c.blue * 255).toInt().coerceIn(0, 255)
        val a = (c.alpha * 255).toInt().coerceIn(0, 255)
        return if (a == 255) "#%02X%02X%02X".format(r, g, b)
        else "#%02X%02X%02X%02X".format(a, r, g, b)
    }

    private fun formatShape(v: Any?): String {
        val s = v.toString()
        return when {
            s == "RectangleShape" -> "rect"
            s.contains("CircleShape") -> "circle"
            s.contains("RoundedCornerShape") -> {
                val pctVals = Regex("""CornerSize\(size = ([\d.]+)%\)""").findAll(s)
                    .map { it.groupValues[1] }.toList()
                if (pctVals.isNotEmpty() && pctVals.all { it == pctVals[0] }) {
                    val pct = pctVals[0].toDoubleOrNull()
                    return if (pct != null && pct >= 50.0) "pill" else "rounded(${pctVals[0]}%)"
                }
                val dpVals = Regex("""([\d.]+)\.dp""").findAll(s)
                    .map { it.groupValues[1] }.toList()
                if (dpVals.isNotEmpty() && dpVals.all { it == dpVals[0] }) {
                    val dp = dpVals[0].toDoubleOrNull()
                    if (dp != null) {
                        val label = if (dp == dp.toInt().toDouble()) dp.toInt().toString() else dp.toString()
                        "rounded(${label}dp)"
                    } else s
                } else if (dpVals.size == 4) {
                    "rounded(${dpVals.joinToString(",") { "${it}dp" }})"
                } else s
            }
            else -> s
        }
    }

    private fun InspectableValue.prop(name: String): Any? =
        inspectableElements.firstOrNull { it.name == name }?.value

    // ── Modifier inspection ──────────────────────────────────────────────

    private val VISUAL_MODIFIERS = setOf(
        "background", "border", "clip", "padding", "shadow",
        "alpha", "size", "width", "height", "requiredSize",
        "fillMaxSize", "fillMaxWidth", "fillMaxHeight",
        "defaultMinSize", "minimumInteractiveComponentSize"
    )

    private fun collectModifiers(node: SemanticsNode): List<String> {
        val props = mutableListOf<String>()
        var layout: LayoutInfo? = node.layoutInfo
        var depth = 0

        while (layout != null && depth < 6) {
            try {
                for (modInfo in layout.getModifierInfo()) {
                    val info = modInfo.modifier as? InspectableValue ?: continue
                    val name = info.nameFallback ?: continue
                    if (name !in VISUAL_MODIFIERS) continue

                    val elements = try {
                        info.inspectableElements.toList()
                    } catch (_: Exception) { continue }

                    when (name) {
                        "background" -> {
                            val color = elements.firstOrNull { it.name == "color" }?.value
                            val shape = elements.firstOrNull { it.name == "shape" }?.value
                            if (color is Color && color != Color.Transparent && color != Color.Unspecified) {
                                props.add("bg=${colorToHex(color)}")
                            }
                            if (shape != null) {
                                val s = formatShape(shape)
                                if (s != "rect") props.add("shape=$s")
                            }
                        }
                        "border" -> {
                            val parts = mutableListOf<String>()
                            elements.firstOrNull { it.name == "width" }?.value?.let {
                                parts.add(it.toString().replace(".0.dp", "dp"))
                            }
                            (elements.firstOrNull { it.name == "color" }?.value as? Color)?.let {
                                parts.add(colorToHex(it))
                            }
                            elements.firstOrNull { it.name == "shape" }?.value?.let {
                                val s = formatShape(it)
                                if (s != "rect") parts.add(s)
                            }
                            if (parts.isNotEmpty()) props.add("border(${parts.joinToString(",")})")
                        }
                        "clip" -> {
                            val shape = elements.firstOrNull { it.name == "shape" }?.value
                                ?: info.valueOverride
                            if (shape != null && props.none { it.startsWith("shape=") }) {
                                val s = formatShape(shape)
                                if (s != "rect") props.add("shape=$s")
                            }
                        }
                        "padding" -> {
                            val h = elements.firstOrNull { it.name == "horizontal" }?.value
                            val v = elements.firstOrNull { it.name == "vertical" }?.value
                            val all = elements.firstOrNull { it.name == "all" }?.value
                            val start = elements.firstOrNull { it.name == "start" }?.value
                            when {
                                all != null -> props.add("pad=$all")
                                h != null && v != null -> props.add("pad=${h}h,${v}v")
                                start != null -> {
                                    val t = elements.firstOrNull { it.name == "top" }?.value
                                    val e = elements.firstOrNull { it.name == "end" }?.value
                                    val b = elements.firstOrNull { it.name == "bottom" }?.value
                                    props.add("pad=$start,$t,$e,$b")
                                }
                            }
                        }
                        "alpha" -> {
                            val alpha = info.valueOverride
                                ?: elements.firstOrNull { it.name == "alpha" }?.value
                            if (alpha is Float && alpha < 1f) props.add("alpha=${"%.2f".format(alpha)}")
                        }
                    }
                }
            } catch (_: Exception) {}

            layout = layout.parentInfo
            depth++
        }

        return props.distinct()
    }

    // ── Shared element data ──────────────────────────────────────────────

    private data class ElemData(
        val type: String,
        val text: String?,
        val desc: String?,
        val bounds: android.graphics.Rect,
        val clickable: Boolean,
        val focused: Boolean,
        val selected: Boolean,
        val modifiers: List<String>
    )

    private fun collectElements(
        node: SemanticsNode, screenW: Int, screenH: Int, out: MutableList<ElemData>
    ) {
        if (!ComposeTreeHelper.isVisible(node)) return
        val bounds = ComposeTreeHelper.boundsInWindow(node)
        if (bounds.right <= 0 || bounds.bottom <= 0 ||
            bounds.left >= screenW || bounds.top >= screenH
        ) return
        val text = ComposeTreeHelper.nodeText(node)
        val desc = ComposeTreeHelper.nodeContentDesc(node)
        val clickable = ComposeTreeHelper.isClickable(node)
        if (text != null || desc != null || clickable) {
            out.add(
                ElemData(
                    type = inferType(node),
                    text = text,
                    desc = desc,
                    bounds = bounds,
                    clickable = clickable,
                    focused = node.config.getOrNull(SemanticsProperties.Focused) == true,
                    selected = node.config.getOrNull(SemanticsProperties.Selected) == true,
                    modifiers = collectModifiers(node)
                )
            )
        }
        for (child in node.children) collectElements(child, screenW, screenH, out)
    }

    // ── Tree text rendering ──────────────────────────────────────────────

    private fun renderNode(
        node: SemanticsNode, screenW: Int, screenH: Int,
        indent: String, isLast: Boolean, out: StringBuilder
    ) {
        if (!ComposeTreeHelper.isVisible(node)) return
        val bounds = ComposeTreeHelper.boundsInWindow(node)
        if (bounds.right <= 0 || bounds.bottom <= 0 ||
            bounds.left >= screenW || bounds.top >= screenH
        ) return

        val text = ComposeTreeHelper.nodeText(node)
        val desc = ComposeTreeHelper.nodeContentDesc(node)
        val clickable = ComposeTreeHelper.isClickable(node)
        val focused = node.config.getOrNull(SemanticsProperties.Focused) == true
        val selected = node.config.getOrNull(SemanticsProperties.Selected) == true
        if (text == null && desc == null && !clickable && node.children.isEmpty()) return

        val prefix = if (indent.isEmpty()) "" else "$indent${if (isLast) "\u2514 " else "\u251C "}"
        val childIndent = if (indent.isEmpty()) "" else "$indent${if (isLast) "  " else "\u2502 "}"

        val label = buildString {
            when {
                text != null && desc != null -> append("\"${text.take(50)}\" [$desc]")
                text != null -> append("\"${text.take(60)}\"")
                desc != null -> append("[$desc]")
                else -> append("(anon)")
            }
            append(" ${inferType(node)}")
            if (clickable) append(" \u2B21")
            if (selected) append(" selected")
            if (focused) append(" focused")
        }
        val b = "[${bounds.left},${bounds.top}\u2192${bounds.right},${bounds.bottom}]"
        val mods = collectModifiers(node)
        val modStr = if (mods.isNotEmpty()) " | ${mods.joinToString(" ")}" else ""
        out.appendLine("$prefix$label $b$modStr")

        val visibleChildren = node.children.filter { child ->
            ComposeTreeHelper.isVisible(child) &&
                    (ComposeTreeHelper.nodeText(child) != null ||
                            ComposeTreeHelper.nodeContentDesc(child) != null ||
                            ComposeTreeHelper.isClickable(child) ||
                            child.children.isNotEmpty())
        }
        visibleChildren.forEachIndexed { i, child ->
            renderNode(child, screenW, screenH, childIndent, i == visibleChildren.lastIndex, out)
        }
    }

    // ── Route registration ───────────────────────────────────────────────

    fun register() {
        // GET /describe — text tree (default) or JSON (?format=json)
        DebugServer.route("GET", "/describe") { req ->
            val activity = DebugBridge.requireActivity()
            val vm = DebugBridge.requireVm()
            val screen = vm.screen.value.name
            val density = activity.resources.displayMetrics.density
            val format = req.query["format"] ?: "text"

            val owner = onMain { ComposeTreeHelper.findSemanticsOwner(activity.window.decorView) }
                ?: return@route HttpResponse.error(500, "Could not find Compose semantics owner")

            if (format == "json") {
                val (screenW, screenH, elements) = onMain {
                    val dv = activity.window.decorView
                    val elems = mutableListOf<ElemData>()
                    collectElements(owner.rootSemanticsNode, dv.width, dv.height, elems)
                    Triple(dv.width, dv.height, elems)
                }
                val (topInset, bottomInset) = onMain {
                    val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    val bars = insets?.getInsets(WindowInsetsCompat.Type.systemBars())
                    Pair(bars?.top ?: 0, bars?.bottom ?: 0)
                }
                val screenshotBase64 = captureAppScreenshot(
                    topInset, bottomInset, screenW, screenH
                )
                val json = buildJsonObject {
                    put("screen", screen)
                    putJsonObject("viewport") {
                        put("width", screenW)
                        put("height", screenH)
                        put("density", density)
                    }
                    putJsonObject("contentInsets") {
                        put("top", topInset)
                        put("bottom", bottomInset)
                    }
                    put("elementCount", elements.size)
                    putJsonArray("elements") {
                        for (el in elements) {
                            add(buildJsonObject {
                                put("type", el.type)
                                if (el.text != null) put("text", el.text)
                                if (el.desc != null) put("desc", el.desc)
                                putJsonArray("bounds") {
                                    add(JsonPrimitive(el.bounds.left))
                                    add(JsonPrimitive(el.bounds.top))
                                    add(JsonPrimitive(el.bounds.right))
                                    add(JsonPrimitive(el.bounds.bottom))
                                }
                                if (el.clickable) put("clickable", true)
                                if (el.selected) put("selected", true)
                                if (el.focused) put("focused", true)
                                if (el.modifiers.isNotEmpty()) {
                                    putJsonArray("modifiers") {
                                        el.modifiers.forEach { add(JsonPrimitive(it)) }
                                    }
                                }
                            })
                        }
                    }
                    put("screenshot", screenshotBase64)
                }
                return@route HttpResponse.json(json.toString())
            }

            // Default: text tree
            val tree = onMain {
                val dv = activity.window.decorView
                val w = dv.width; val h = dv.height
                val root = owner.rootSemanticsNode
                val out = StringBuilder()
                out.appendLine("$screen [${w}\u00D7${h} @${density}x]")
                out.appendLine()
                val children = root.children.filter { ComposeTreeHelper.isVisible(it) }
                children.forEachIndexed { i, child ->
                    renderNode(child, w, h, "", i == children.lastIndex, out)
                }
                out.toString()
            }
            HttpResponse(200, "text/plain; charset=utf-8", tree.toByteArray(Charsets.UTF_8))
        }

        // GET /describe/annotated — screenshot with element bounds and labels
        DebugServer.route("GET", "/describe/annotated") { _ ->
            val activity = DebugBridge.requireActivity()

            val owner = onMain { ComposeTreeHelper.findSemanticsOwner(activity.window.decorView) }
                ?: return@route HttpResponse.error(500, "Could not find Compose semantics owner")

            val (screenW, screenH, elements) = onMain {
                val dv = activity.window.decorView
                val elems = mutableListOf<ElemData>()
                collectElements(owner.rootSemanticsNode, dv.width, dv.height, elems)
                Triple(dv.width, dv.height, elems)
            }
            val (topInset, bottomInset) = onMain {
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                val bars = insets?.getInsets(WindowInsetsCompat.Type.systemBars())
                Pair(bars?.top ?: 0, bars?.bottom ?: 0)
            }

            val bitmap = captureAppBitmap(topInset, bottomInset, screenW, screenH)
            val canvas = Canvas(bitmap)
            val bitmapH = bitmap.height
            val bitmapW = bitmap.width

            val borderPaint = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true
            }
            val fillPaint = Paint().apply {
                style = Paint.Style.FILL; isAntiAlias = true
            }
            val textPaint = Paint().apply {
                color = 0xFFFFFFFF.toInt(); textSize = 28f
                isAntiAlias = true; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val srcTextPaint = Paint().apply {
                color = 0xFF88DDFF.toInt(); textSize = 20f
                isAntiAlias = true; typeface = Typeface.MONOSPACE
            }
            val labelH1 = 32f
            val srcH = 24f

            // Track placed label regions (y, height) to nudge overlapping labels
            val placedLabels = mutableListOf<Pair<Float, Float>>()

            for ((i, el) in elements.withIndex()) {
                val l = el.bounds.left.toFloat()
                val t = (el.bounds.top - topInset).toFloat()
                val r = el.bounds.right.toFloat()
                val b = (el.bounds.bottom - topInset).toFloat()
                if (b <= 0f || t >= bitmapH) continue

                val color = typeColor(el.type)

                // Element border
                borderPaint.color = color
                canvas.drawRect(l, t, r, b, borderPaint)

                // Semi-transparent fill for interactive elements
                if (el.clickable) {
                    fillPaint.color = (color and 0x00FFFFFF) or 0x18000000
                    canvas.drawRect(l, t, r, b, fillPaint)
                }

                // Source location from build-time index
                val source = findSource(el.text, el.desc)

                // Line 1: element type + text
                val line1 = buildString {
                    append("${i}:${typeAbbrev(el.type)}")
                    el.text?.take(20)?.let { append(" \"$it\"") }
                        ?: el.desc?.take(20)?.let { append(" [$it]") }
                }
                val line1W = textPaint.measureText(line1)
                val line2W = if (source != null) srcTextPaint.measureText(source) else 0f
                val totalLabelW = (maxOf(line1W, line2W) + 16f).coerceAtMost(bitmapW - l)
                val thisLabelH = if (source != null) labelH1 + srcH else labelH1

                // Find non-overlapping y position
                var labelY = if (t - thisLabelH - 2f >= 0f) t - thisLabelH - 2f else t + 2f
                for ((ey, eh) in placedLabels) {
                    if (labelY < ey + eh && labelY + thisLabelH > ey) {
                        labelY = ey + eh + 2f
                    }
                }
                labelY = labelY.coerceIn(0f, (bitmapH - thisLabelH))
                placedLabels.add(Pair(labelY, thisLabelH))

                // Label background
                fillPaint.color = 0xEE111111.toInt()
                canvas.drawRect(l, labelY, l + totalLabelW, labelY + thisLabelH, fillPaint)

                // Color stripe
                fillPaint.color = color
                canvas.drawRect(l, labelY, l + 5f, labelY + thisLabelH, fillPaint)

                // White outline
                borderPaint.color = 0x66FFFFFF.toInt()
                borderPaint.strokeWidth = 1.5f
                canvas.drawRect(l, labelY, l + totalLabelW, labelY + thisLabelH, borderPaint)
                borderPaint.strokeWidth = 4f

                // Line 1: element info (white, bold)
                canvas.drawText(line1, l + 10f, labelY + labelH1 - 9f, textPaint)

                // Line 2: source location (cyan)
                if (source != null) {
                    canvas.drawText(source, l + 10f, labelY + labelH1 + srcH - 7f, srcTextPaint)
                }
            }

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            HttpResponse.png(out.toByteArray())
        }

        // GET /describe/ui — visual inspector page
        DebugServer.route("GET", "/describe/ui") { _ ->
            HttpResponse(200, "text/html; charset=utf-8", VIEWER_HTML.toByteArray(Charsets.UTF_8))
        }
    }

    // ── Viewer HTML ──────────────────────────────────────────────────────

    private val VIEWER_HTML = """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Realms UI Inspector</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font:13px/1.4 'SF Mono',Menlo,monospace;background:#1a1a2e;color:#ddd;display:flex;height:100vh;overflow:hidden}
#phone-wrap{flex:0 0 420px;padding:12px;overflow:auto;display:flex;align-items:start;justify-content:center}
#phone{position:relative;width:396px;border-radius:24px;overflow:hidden;box-shadow:0 4px 40px rgba(0,0,0,.6);border:3px solid #333}
#canvas{position:relative;width:100%;overflow:hidden}
.el{position:absolute;overflow:hidden;display:flex;align-items:center;justify-content:center;cursor:pointer;transition:box-shadow .12s}
.el:hover,.el.active{box-shadow:0 0 0 2px #4287f5;z-index:200!important}
.el-text{font-size:9px;color:#333;text-align:center;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:0 2px;pointer-events:none;max-width:100%}
.el-text.light{color:#eee}
#side{flex:1;display:flex;flex-direction:column;overflow:hidden;min-width:0}
#bar{padding:6px 12px;background:#16213e;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
#bar button{background:#0f3460;color:#ddd;border:1px solid #1a4080;padding:3px 10px;cursor:pointer;border-radius:3px;font:inherit}
#bar button:hover{background:#1a4080}
#bar label{font-size:12px;cursor:pointer;user-select:none}
#bar span{font-size:11px;color:#888}
#detail{flex:0 0 auto;max-height:260px;overflow:auto;padding:8px 12px;background:#0f1a2e;border-bottom:1px solid #1a3050;white-space:pre-wrap;font-size:12px;line-height:1.6;color:#9ab}
#detail .mod{color:#7ec;display:block}
#detail .hdr{color:#e8e;font-weight:bold}
#tree{flex:1;overflow:auto;padding:8px 12px;white-space:pre;font-size:11px;line-height:1.55;color:#aaa}
</style></head><body>
<div id="phone-wrap"><div id="phone"><div id="canvas"></div></div></div>
<div id="side">
<div id="bar">
<button onclick="refresh()">&#8635; Refresh</button>
<label><input type="checkbox" id="chkText" checked onchange="draw()"> Text</label>
<label><input type="checkbox" id="chkOutline" onchange="draw()"> Outlines</label>
<span id="status"></span>
</div>
<div id="detail">Click an element to inspect</div>
<div id="tree"></div>
</div>
<script>
const PW=396;let D=null,sel=-1;
function mod(el,prefix){return(el.modifiers||[]).find(m=>m.startsWith(prefix))}
function modVal(el,prefix){const m=mod(el,prefix);return m?m.slice(prefix.length):null}
function firstBg(el){const v=modVal(el,'bg=');return(v&&v!=='transparent'&&v!=='unspecified')?v:null}
function lum(hex){const r=parseInt(hex.slice(1,3),16),g=parseInt(hex.slice(3,5),16),b=parseInt(hex.slice(5,7),16);return(r*299+g*587+b*114)/1000}
function shapeRadius(el,sc){
 const s=modVal(el,'shape=');if(!s)return'0';
 if(s==='pill'||s==='circle')return'50%';
 const dp=s.match(/rounded\((\d+)/);return dp?(parseFloat(dp[1])*sc)+'px':'0';
}
function parseBorder(el,sc){
 const m=mod(el,'border(');if(!m)return null;
 const inner=m.slice(7,-1);const parts=inner.split(',');
 let w='1px',c='#888',r='0';
 for(const p of parts){const t=p.trim();if(t.endsWith('dp'))w=(parseFloat(t)*sc)+'px';else if(t.startsWith('#'))c=t;else if(t.startsWith('rounded')||t==='pill'){const dp=t.match(/(\d+)/);r=dp?(parseFloat(dp[1])*sc)+'px':t==='pill'?'50%':'0';}}
 return{width:w,color:c,radius:r};
}
async function refresh(){
 document.getElementById('status').textContent='Loading\u2026';
 try{
  const[jR,tR]=await Promise.all([fetch('/describe?format=json'),fetch('/describe')]);
  D=await jR.json();document.getElementById('tree').textContent=await tR.text();
  sel=-1;draw();document.getElementById('status').textContent=D.screen+' \u00b7 '+D.elementCount+' elements';
 }catch(e){document.getElementById('status').textContent='Error: '+e.message;}
}
function draw(){
 if(!D)return;
 const cv=document.getElementById('canvas');cv.innerHTML='';
 const sc=PW/D.viewport.width;const ph=D.viewport.height*sc;
 cv.style.height=ph+'px';
 const rootBg=D.elements.length?firstBg(D.elements[0])||'#F5F5F5':'#F5F5F5';
 cv.style.background=rootBg;
 const showText=document.getElementById('chkText').checked;
 const showOutline=document.getElementById('chkOutline').checked;
 D.elements.forEach((el,i)=>{
  const[l,t,r,b]=el.bounds;
  const w=(r-l)*sc,h=(b-t)*sc;
  if(w<2||h<2)return;
  const d=document.createElement('div');d.className='el';
  d.style.left=(l*sc)+'px';d.style.top=(t*sc)+'px';d.style.width=w+'px';d.style.height=h+'px';
  d.style.zIndex=100+i;
  const bg=firstBg(el);
  if(bg)d.style.backgroundColor=bg;
  d.style.borderRadius=shapeRadius(el,sc);
  const br=parseBorder(el,sc);
  if(br){d.style.border=br.width+' solid '+br.color;if(br.radius!=='0')d.style.borderRadius=br.radius;}
  else if(showOutline){d.style.outline='1px solid rgba(100,100,100,.25)';}
  if(showText&&(el.text||el.desc)){
   const sp=document.createElement('span');sp.className='el-text';
   if(bg&&lum(bg)<128)sp.classList.add('light');
   sp.textContent=el.text?el.text.slice(0,30):el.desc;
   d.appendChild(sp);
  }
  d.onmouseenter=()=>showDetail(i);
  d.onclick=e=>{e.stopPropagation();sel=i;draw();showDetail(i);};
  if(i===sel)d.classList.add('active');
  cv.appendChild(d);
 });
}
function showDetail(i){
 const el=D.elements[i],ms=el.modifiers||[];
 const label=el.text?'"'+el.text.slice(0,50)+'"':el.desc?'['+el.desc+']':'(anon)';
 const det=document.getElementById('detail');
 let h='<span class="hdr">'+esc(label)+' '+el.type+'</span>\n';
 h+='bounds: ['+el.bounds.join(', ')+']\n';
 const w=el.bounds[2]-el.bounds[0],ht=el.bounds[3]-el.bounds[1];
 h+='size: '+w+'x'+ht+'px ('+(w/D.viewport.density).toFixed(0)+'x'+(ht/D.viewport.density).toFixed(0)+'dp)\n';
 if(el.clickable)h+='clickable\n';
 if(ms.length)h+='\n'+ms.map(m=>'<span class="mod">'+esc(m)+'</span>').join('');
 det.innerHTML=h;
}
function esc(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
refresh();
</script></body></html>
""".trimIndent()
}
