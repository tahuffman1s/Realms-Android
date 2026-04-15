package com.realmsoffate.game.ui.map

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.data.WorldMap
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.WorldGen
import com.realmsoffate.game.ui.theme.RealmsTheme
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Google-Maps-style world map rendered with Compose Canvas.
 *
 * Follows system dark / light theme — four palettes:
 *  (light, default), (light, terrain), (dark, default), (dark, terrain).
 *
 * Layers (bottom to top):
 *  1. Land + paper grid
 *  2. Faction territory washes (toggleable)
 *  3. Forest patches
 *  4. Mountain washes
 *  5. Water (lakes + rivers)
 *  6. Road casings + fills
 *  7. Road distance pills
 *  8. Mountain peak triangles
 *  9. Location pins with halo'd labels
 * 10. Player "you are here" dot
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    state: GameUiState,
    onClose: () -> Unit,
    onTravel: (MapLocation) -> Unit = {},
    onCancelTravel: () -> Unit = {},
    isTraveling: Boolean = false
) {
    val wm = state.worldMap ?: return
    val cur = wm.locations.getOrNull(state.currentLoc)
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val extended = RealmsTheme.colors

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var showFactions by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }
    var selectedLocation by remember { mutableStateOf<MapLocation?>(null) }

    val gm = remember(dark) { mapPalette(false, dark) }

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "pulse"
    )

    val mw = wm.width.toFloat()
    val mh = wm.height.toFloat()

    fun centerOnPlayer() {
        val px = state.playerPos?.x ?: cur?.x?.toFloat() ?: (mw / 2f)
        val py = state.playerPos?.y ?: cur?.y?.toFloat() ?: (mh / 2f)
        pan = Offset(mw / 2f - px, mh / 2f - py)
        zoom = 1.4f
    }

    // Chip surface colour — high-contrast against the map, adapts to dark/light.
    val chipBg = if (dark) Color(0xEE1F1D23) else Color(0xF2FFFFFF)
    val chipFg = if (dark) Color(0xFFE8E1F0) else Color(0xFF3C4043)

    Scaffold(
        containerColor = gm.land
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            MapCanvas(
                wm = wm,
                cur = cur,
                playerPos = state.playerPos,
                zoom = zoom,
                pan = pan,
                showFactions = showFactions,
                factions = state.worldLore?.factions.orEmpty(),
                palette = gm,
                playerDot = extended.playerDot,
                pulse = pulse,
                onSizeChanged = { canvasSize = it },
                onTransform = { panDelta, zoomDelta ->
                    pan = Offset(pan.x + panDelta.x / zoom, pan.y + panDelta.y / zoom)
                    zoom = (zoom * zoomDelta).coerceIn(0.5f, 4f)
                },
                onLocationTap = { selectedLocation = it }
            )
            // Right-edge controls
            Column(
                Modifier.align(Alignment.CenterEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapControl(onClick = { showFactions = !showFactions }, icon = Icons.Default.Flag, chipBg = chipBg, tint = if (showFactions) extended.goldAccent else chipFg)
                Spacer(Modifier.height(4.dp))
                MapControl(onClick = { zoom = min(4f, zoom * 1.3f) }, icon = Icons.Default.Add, chipBg = chipBg, tint = chipFg)
                MapControl(onClick = { zoom = max(0.5f, zoom / 1.3f) }, icon = Icons.Default.Remove, chipBg = chipBg, tint = chipFg)
            }
            // Compass — hovering top-right of the map area, away from the
            // controls column (which is centered on the right edge).
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding().padding(top = 76.dp, end = 12.dp)
                    .size(40.dp).clip(CircleShape)
                    .background(chipBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("N", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = extended.fumbleRed)
                    Text("\u25B2", fontSize = 11.sp, color = chipFg)
                }
            }
            // Bottom strip — scale bar, distance pills, and attribution all in a
            // single contained block at the bottom so they stop fighting the
            // right-edge controls.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Scale bar (compact, top of the strip)
                Row(
                    Modifier.padding(start = 12.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val scalePx = (100f * zoom * (canvasSize.width / mw)).coerceIn(40f, 160f)
                    Column {
                        Box(
                            Modifier
                                .width(with(density) { scalePx.toDp() })
                                .height(8.dp)
                        ) {
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(chipFg))
                            Box(Modifier.align(Alignment.BottomStart).width(2.dp).height(8.dp).background(chipFg))
                            Box(Modifier.align(Alignment.BottomEnd).width(2.dp).height(8.dp).background(chipFg))
                        }
                        Spacer(Modifier.height(2.dp))
                        Surface(color = chipBg, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "${(100f / 15f).toInt()} leagues",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = chipFg
                            )
                        }
                    }
                }
                // Distance pills row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WorldGen.connected(wm, state.currentLoc).forEach { (dest, dist) ->
                        Surface(
                            color = chipBg,
                            shape = RoundedCornerShape(18.dp),
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dest.icon, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(4.dp))
                                Text(dest.name, style = MaterialTheme.typography.labelMedium, color = chipFg)
                                Spacer(Modifier.width(4.dp))
                                Text("(${dist}lg)", style = MaterialTheme.typography.labelSmall, color = extended.fumbleRed, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Text(
                    "\u00A9 Realms of Fate \u00B7 Cartographer's Guild",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = chipFg.copy(alpha = 0.55f),
                    textAlign = TextAlign.End
                )
            }
            // Travel dialog
            selectedLocation?.let { loc ->
                val isCurrent = loc.id == state.currentLoc
                val currentLocObj = state.worldMap?.locations?.getOrNull(state.currentLoc)
                val road = state.worldMap?.roads?.firstOrNull {
                    (it.from == state.currentLoc && it.to == loc.id) ||
                    (it.to == state.currentLoc && it.from == loc.id)
                }
                AlertDialog(
                    onDismissRequest = { selectedLocation = null },
                    title = { Text("${loc.icon} ${loc.name}") },
                    text = {
                        Column {
                            Text("${loc.type.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelMedium)
                            if (isCurrent) {
                                Text("You are here.", style = MaterialTheme.typography.bodyMedium)
                            } else if (road != null) {
                                Text("Distance: ${road.dist} leagues by road", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text("No direct road from ${currentLocObj?.name ?: "here"}.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = {
                        if (!isCurrent && road != null) {
                            Button(onClick = {
                                onTravel(loc)
                                selectedLocation = null
                            }) { Text("Travel") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedLocation = null }) { Text("Close") }
                    }
                )
            }
            // Top bar overlay — floats above the map canvas
            Surface(
                modifier = Modifier.align(Alignment.TopCenter),
                color = Color.Transparent
            ) {
                Row(
                    Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = chipBg,
                            contentColor = chipFg
                        )
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Spacer(Modifier.weight(1f))
                    cur?.let {
                        Surface(
                            color = chipBg,
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(it.icon, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    it.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = chipFg
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledIconButton(
                        onClick = { centerOnPlayer() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = chipBg,
                            contentColor = extended.info
                        )
                    ) { Icon(Icons.Default.MyLocation, "Center") }
                }
            }
            // Traveling banner with cancel button
            if (isTraveling) {
                val travel = state.travelState
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp),
                    color = chipBg,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "TRAVELING",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = extended.goldAccent
                            )
                            if (travel != null) {
                                Text(
                                    "${travel.destName} — ${travel.leaguesTraveled}/${travel.totalLeagues} leagues",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = chipFg
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = onCancelTravel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapControl(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    chipBg: Color,
    tint: Color
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = chipBg,
            contentColor = tint
        )
    ) { Icon(icon, null) }
}

private data class MapPalette(
    val land: Color, val park: Color, val forest: Color, val mountain: Color,
    val water: Color, val waterDark: Color,
    val roadFill: Color, val roadCase: Color,
    val roadHighway: Color, val roadHighwayCase: Color,
    val roadDashed: Color, val label: Color, val labelHalo: Color,
    val pinRed: Color, val pinCur: Color, val pinFaint: Color,
    val grid: Color, val peak: Color, val distancePill: Color, val distanceText: Color
)

private fun mapPalette(terrain: Boolean, dark: Boolean): MapPalette = when {
    // ---- Dark + default: exact Google Maps Night style ----
    //   Base #242f3e, Roads #38414e (case #2b3544), Water #17263c,
    //   Parks/forest #263c3f, Road labels #9ca5b3, Teardrop pin #EA4335,
    //   Current-location amber #d59563, Player dot Google blue #4285F4 (handled
    //   by extended.playerDot).
    dark && !terrain -> MapPalette(
        land = Color(0xFF242F3E), park = Color(0xFF263C3F), forest = Color(0xFF1F3A3C),
        mountain = Color(0xFF3A3E4F), water = Color(0xFF17263C), waterDark = Color(0xFF0F1A2A),
        roadFill = Color(0xFF38414E), roadCase = Color(0xFF2B3544),
        roadHighway = Color(0xFF746855), roadHighwayCase = Color(0xFF1F2835),
        roadDashed = Color(0xFF4E5661), label = Color(0xFF9CA5B3), labelHalo = Color(0xFF17263C),
        pinRed = Color(0xFFEA4335), pinCur = Color(0xFFD59563),
        pinFaint = Color(0xFF4E5661), grid = Color(0xFF2B3544),
        peak = Color(0xFF4E5661), distancePill = Color(0xEE17263C), distanceText = Color(0xFF9CA5B3)
    )
    // ---- Dark + terrain: Google Night with warmer biome washes ----
    dark && terrain -> MapPalette(
        land = Color(0xFF1D2837), park = Color(0xFF2A4E50), forest = Color(0xFF1F3A3C),
        mountain = Color(0xFF463A30), water = Color(0xFF132036), waterDark = Color(0xFF0A1628),
        roadFill = Color(0xFF38414E), roadCase = Color(0xFF2B3544),
        roadHighway = Color(0xFF8F6B2A), roadHighwayCase = Color(0xFF2C2010),
        roadDashed = Color(0xFF4A5260), label = Color(0xFFB5BFD0), labelHalo = Color(0xFF101826),
        pinRed = Color(0xFFEA4335), pinCur = Color(0xFFD59563),
        pinFaint = Color(0xFF4E5661), grid = Color(0xFF26303F),
        peak = Color(0xFF6B5F48), distancePill = Color(0xEE17263C), distanceText = Color(0xFFB5BFD0)
    )
    // Light + terrain — muted cream like original
    terrain -> MapPalette(
        land = Color(0xFFE8E0CC), park = Color(0xFFB8D9A8), forest = Color(0xFF9EC78A),
        mountain = Color(0xFFC9BCA0), water = Color(0xFFA3CCFF), waterDark = Color(0xFF6FA8DC),
        roadFill = Color.White, roadCase = Color(0xFFB8B8B8),
        roadHighway = Color(0xFFFCE38A), roadHighwayCase = Color(0xFFE8B84A),
        roadDashed = Color(0xFFA89D85), label = Color(0xFF2B2B2B), labelHalo = Color(0xFFFFFFFF),
        pinRed = Color(0xFFE94F37), pinCur = Color(0xFFFF8C00),
        pinFaint = Color(0xFF8B8677), grid = Color(0xFFD8CFB8),
        peak = Color(0xFFA89988), distancePill = Color(0xDDFFFFFF), distanceText = Color(0xFF3C4043)
    )
    // Light + default — Google-Maps feel
    else -> MapPalette(
        land = Color(0xFFF2ECDC), park = Color(0xFFCFE6BF), forest = Color(0xFFB9DBA7),
        mountain = Color(0xFFDCD3C2), water = Color(0xFFA8D0F5), waterDark = Color(0xFF82B6E8),
        roadFill = Color.White, roadCase = Color(0xFFC9C9C9),
        roadHighway = Color(0xFFFFE58F), roadHighwayCase = Color(0xFFE6B94A),
        roadDashed = Color(0xFFB5AD98), label = Color(0xFF3C4043), labelHalo = Color(0xFFFFFFFF),
        pinRed = Color(0xFFEA4335), pinCur = Color(0xFFFF6D00),
        pinFaint = Color(0xFF9E9789), grid = Color(0xFFE4DCC4),
        peak = Color(0xFFA89988), distancePill = Color(0xDDFFFFFF), distanceText = Color(0xFF3C4043)
    )
}

@Composable
private fun MapCanvas(
    wm: WorldMap,
    cur: MapLocation?,
    playerPos: com.realmsoffate.game.data.PlayerPos?,
    zoom: Float,
    pan: Offset,
    showFactions: Boolean,
    factions: List<com.realmsoffate.game.data.Faction>,
    palette: MapPalette,
    playerDot: Color,
    pulse: Float,
    onSizeChanged: (Size) -> Unit,
    onTransform: (pan: Offset, zoom: Float) -> Unit,
    onLocationTap: (MapLocation) -> Unit = {}
) {
    val factionColors = remember {
        listOf(
            Color(0xFFC44040), Color(0xFF4A9E5E), Color(0xFF5B7FC7), Color(0xFFD4A843),
            Color(0xFF8B6CC7), Color(0xFF4AA8A8), Color(0xFFCC6633), Color(0xFFAA44AA)
        )
    }
    val factionBases = remember(factions, wm) {
        factions.mapIndexedNotNull { i, f ->
            val loc = wm.locations.firstOrNull { it.name == f.baseLoc } ?: return@mapIndexedNotNull null
            Triple(f, loc, factionColors[i % factionColors.size])
        }
    }

    val currentId = cur?.id ?: -1

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.land)
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    onTransform(panChange, zoomChange)
                }
            }
            .pointerInput(wm.locations, zoom, pan) {
                detectTapGestures { tapOffset ->
                    val mw = wm.width.toFloat()
                    val mh = wm.height.toFloat()
                    val scale = minOf(size.width / mw, size.height / mh) * zoom
                    val offX = size.width / 2f + pan.x * scale - mw / 2f * scale
                    val offY = size.height / 2f + pan.y * scale - mh / 2f * scale
                    val worldX = (tapOffset.x - offX) / scale
                    val worldY = (tapOffset.y - offY) / scale
                    val hitRadius = 25f
                    val hit = wm.locations.minByOrNull {
                        hypot((it.x - worldX).toDouble(), (it.y - worldY).toDouble())
                    }
                    if (hit != null) {
                        val dist = hypot((hit.x - worldX).toDouble(), (hit.y - worldY).toDouble())
                        if (dist < hitRadius) {
                            onLocationTap(hit)
                        }
                    }
                }
            }
    ) {
        onSizeChanged(size)
        val mw = wm.width.toFloat()
        val mh = wm.height.toFloat()
        val scale = minOf(size.width / mw, size.height / mh) * zoom
        val offX = size.width / 2f + (pan.x * scale) - (mw / 2f) * scale
        val offY = size.height / 2f + (pan.y * scale) - (mh / 2f) * scale

        fun tx(x: Float) = offX + x * scale
        fun ty(y: Float) = offY + y * scale

        // Layer 1b: paper grid
        val gridColor = palette.grid.copy(alpha = 0.4f)
        var i = -4
        while (i < mw / 60 + 4) {
            val x = tx(i * 60f)
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
            i++
        }
        i = -4
        while (i < mh / 60 + 4) {
            val y = ty(i * 60f)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
            i++
        }

        // Layer 2: faction washes
        if (showFactions) {
            factionBases.forEach { (faction, loc, color) ->
                val isDestroyed = faction.status == "destroyed"
                drawCircle(
                    color = if (isDestroyed) Color.Gray.copy(alpha = 0.12f) else color.copy(alpha = 0.25f),
                    center = Offset(tx(loc.x.toFloat()), ty(loc.y.toFloat())),
                    radius = 90f * scale
                )
                // Destroyed factions get a faint hatched border to signal collapse
                if (isDestroyed) {
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.30f),
                        center = Offset(tx(loc.x.toFloat()), ty(loc.y.toFloat())),
                        radius = 90f * scale,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.5f * scale,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(6f * scale, 5f * scale)
                            )
                        )
                    )
                }
            }
            // Faction name labels
            factionBases.forEach { (faction, loc, _) ->
                val cx = tx(loc.x.toFloat())
                val cy = ty(loc.y.toFloat()) - 95f * scale
                drawContext.canvas.nativeCanvas.apply {
                    val label = if (faction.status == "destroyed") "${faction.name} (Fallen)" else faction.name
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 10f * scale
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                    val haloPaint = android.graphics.Paint(paint).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f * scale
                        color = android.graphics.Color.BLACK
                    }
                    drawText(label, cx, cy, haloPaint)
                    drawText(label, cx, cy, paint)
                }
            }
            // Contested territories — check pairs of factions for overlap
            for (i in factionBases.indices) {
                for (j in i + 1 until factionBases.size) {
                    val (_, baseA, _) = factionBases[i]
                    val (_, baseB, _) = factionBases[j]
                    val dist = hypot((baseA.x - baseB.x).toDouble(), (baseA.y - baseB.y).toDouble())
                    if (dist < 120.0) {
                        val midX = tx((baseA.x + baseB.x) / 2f)
                        val midY = ty((baseA.y + baseB.y) / 2f)
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                textSize = 9f * scale
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            drawText("⚔ Contested", midX, midY, paint)
                        }
                    }
                }
            }
        }

        // Layer 6: roads (casings then fills)
        val highwayRoads = wm.roads.filter { rd ->
            val a = wm.locations[rd.from]; val b = wm.locations[rd.to]
            a.type == "city" || b.type == "city"
        }
        val minorRoads = wm.roads.filter { rd ->
            val a = wm.locations[rd.from]; val b = wm.locations[rd.to]
            !(a.type == "city" || b.type == "city")
        }
        fun drawRoad(rd: com.realmsoffate.game.data.MapRoad, casing: Color, fill: Color, w: Float, fillW: Float) {
            val a = wm.locations[rd.from]; val b = wm.locations[rd.to]
            val disc = a.discovered && b.discovered
            val p1 = Offset(tx(a.x.toFloat()), ty(a.y.toFloat()))
            val p2 = Offset(tx(b.x.toFloat()), ty(b.y.toFloat()))
            if (disc) {
                drawLine(casing, p1, p2, strokeWidth = w * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(fill, p1, p2, strokeWidth = fillW * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            } else {
                drawLine(
                    palette.roadDashed.copy(alpha = 0.7f),
                    p1, p2,
                    strokeWidth = (w * 0.5f) * scale,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f * scale, 6f * scale))
                )
            }
        }
        minorRoads.forEach { drawRoad(it, palette.roadCase, palette.roadFill, w = 5f, fillW = 3f) }
        highwayRoads.forEach { drawRoad(it, palette.roadHighwayCase, palette.roadHighway, w = 7.5f, fillW = 5f) }

        // Layer 7: road distance pills
        wm.roads.forEach { rd ->
            val a = wm.locations[rd.from]; val b = wm.locations[rd.to]
            if (!(a.discovered && b.discovered)) return@forEach
            val mx = tx(((a.x + b.x) / 2f))
            val my = ty(((a.y + b.y) / 2f))
            val label = "${rd.dist} lg"
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 9f * scale
                    textAlign = android.graphics.Paint.Align.CENTER
                    color = palette.distanceText.toArgb()
                }
                val bgPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = palette.distancePill.toArgb()
                }
                val w = paint.measureText(label) + 8f
                drawRoundRect(mx - w / 2f, my - 7f, mx + w / 2f, my + 7f, 7f, 7f, bgPaint)
                drawText(label, mx, my + 3f, paint)
            }
        }

        // Layer 9: location pins — all locations get full teardrop pins
        wm.locations.forEach { loc ->
            val cx = tx(loc.x.toFloat()); val cy = ty(loc.y.toFloat())
            val isCur = loc.id == currentId
            val disc = loc.discovered
            val pinColor = if (isCur) palette.pinCur else if (disc) palette.pinRed else palette.pinFaint
            if (isCur) {
                drawCircle(
                    color = palette.pinCur.copy(alpha = pulse * 0.4f),
                    center = Offset(cx, cy),
                    radius = 22f * scale,
                    style = Stroke(width = 1.5f * scale)
                )
            }
            // Teardrop pin for all locations
            val path = Path().apply {
                moveTo(cx, cy + 4f * scale)
                cubicTo(cx - 11f * scale, cy - 4f * scale, cx - 11f * scale, cy - 20f * scale, cx, cy - 22f * scale)
                cubicTo(cx + 11f * scale, cy - 20f * scale, cx + 11f * scale, cy - 4f * scale, cx, cy + 4f * scale)
                close()
            }
            drawPath(path, pinColor.copy(alpha = if (disc) 1f else 0.6f))
            drawCircle(palette.labelHalo, radius = 6f * scale, center = Offset(cx, cy - 14f * scale))
            // Icon emoji via native canvas
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 10f * scale
                    textAlign = android.graphics.Paint.Align.CENTER
                    alpha = if (disc) 255 else 153
                }
                drawText(loc.icon, cx, cy - 11f * scale, paint)
            }
            // Label with halo
            drawContext.canvas.nativeCanvas.apply {
                val label = loc.name
                val textSize = if (isCur) 11f * scale else 9f * scale
                val haloPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    this.textSize = textSize
                    textAlign = android.graphics.Paint.Align.CENTER
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 3f
                    color = palette.labelHalo.toArgb()
                    alpha = if (disc) 255 else 180
                }
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    this.textSize = textSize
                    textAlign = android.graphics.Paint.Align.CENTER
                    color = if (isCur) palette.pinCur.toArgb() else palette.label.toArgb()
                    isFakeBoldText = isCur
                    alpha = if (disc) 255 else 153
                }
                drawText(label, cx, cy + 16f * scale, haloPaint)
                drawText(label, cx, cy + 16f * scale, textPaint)
            }
        }

        // Layer 10: player "you are here" dot
        playerPos?.let { pp ->
            val px = tx(pp.x); val py = ty(pp.y)
            cur?.let {
                drawLine(
                    playerDot.copy(alpha = 0.45f),
                    Offset(tx(it.x.toFloat()), ty(it.y.toFloat())),
                    Offset(px, py),
                    strokeWidth = 1.2f * scale,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f * scale, 4f * scale))
                )
            }
            drawCircle(playerDot.copy(alpha = 0.18f * pulse), radius = 22f * scale, center = Offset(px, py))
            drawCircle(palette.labelHalo, radius = 9f * scale, center = Offset(px, py))
            drawCircle(playerDot, radius = 7f * scale, center = Offset(px, py))
            drawCircle(palette.labelHalo, radius = 2.2f * scale, center = Offset(px, py))
        }
    }
}
