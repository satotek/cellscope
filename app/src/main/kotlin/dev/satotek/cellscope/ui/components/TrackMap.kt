package dev.satotek.cellscope.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.replay.Track
import dev.satotek.cellscope.data.replay.TrackPoint
import dev.satotek.cellscope.data.snapshot.OsmTiles
import dev.satotek.cellscope.ui.theme.Palette
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.pow

/** Logging pauses longer than this leave a gap rather than a straight line across the map. */
private const val GAP_MS = 60_000L
/** OSM asks for modest use; a view never needs more tiles than this. */
private const val MAX_TILES = 48

private data class TileKey(val z: Int, val x: Int, val y: Int)

/**
 * The drive on OpenStreetMap tiles, coloured by serving RSRP, with cell changes marked. Two fingers
 * zoom and pan so one finger still scrolls the page, unless [panWithOneFinger] says the map has the
 * screen to itself; a tap picks the nearest sample. [resetKey] says when a pinched view is stale: a
 * live track grows every second and must not snap back each time. [live] marks the newest point.
 */
@Composable
fun TrackMap(
    points: List<TrackPoint>,
    events: List<CellEvent>,
    selectedT: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = points,
    live: Boolean = false,
    panWithOneFinger: Boolean = false,
    onExpand: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val good = Palette.good; val fair = Palette.fair; val poor = Palette.poor; val none = Palette.textDim
    val accent = Palette.accent
    val ground = MaterialTheme.colorScheme.surfaceContainerHighest
    val tiles = remember { mutableStateMapOf<TileKey, ImageBitmap>() }
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier.clip(RoundedCornerShape(12.dp)).background(ground)) {
        val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
        val pad = 24f
        // World units are dp at the fit zoom, so one tile is 256 dp before any pinch.
        val fresh = remember(points, wPx, hPx) {
            val z = Track.fitZoom(points, (wPx / density - 2 * pad).toDouble(), (hPx / density - 2 * pad).toDouble())
            val b = Track.bounds(points, z)
            z to Offset(((b[0] + b[2]) / 2).toFloat(), ((b[1] + b[3]) / 2).toFloat())
        }
        // Once the user has pinched or panned, refitting a growing track would yank the view away.
        val userMoved = scale != 1f || offset != Offset.Zero
        var held by remember(resetKey) { mutableStateOf(fresh) }
        SideEffect { if (!userMoved) held = fresh }
        val (baseZ, centre) = if (userMoved) held else fresh
        val world = remember(points, baseZ) { points.map { Track.project(it.lat, it.lon, baseZ) } }
        val viewCentre = Offset(wPx / 2, hPx / 2)
        fun toScreen(x: Double, y: Double) = Offset(
            viewCentre.x + offset.x + (x.toFloat() - centre.x) * scale * density,
            viewCentre.y + offset.y + (y.toFloat() - centre.y) * scale * density,
        )
        fun toWorld(s: Offset) = Offset(
            (s.x - viewCentre.x - offset.x) / (scale * density) + centre.x,
            (s.y - viewCentre.y - offset.y) / (scale * density) + centre.y,
        )

        // A pinch past each doubling switches to the next tile zoom, so the map stays sharp.
        val tileZ = (baseZ + floor(log2(scale.toDouble())).toInt()).coerceIn(baseZ, Track.MAX_ZOOM)
        val f = 2.0.pow(tileZ - baseZ)
        val tl = toWorld(Offset.Zero); val br = toWorld(Offset(wPx, hPx))
        val tx0 = floor(tl.x * f / Track.TILE).toInt(); val tx1 = floor(br.x * f / Track.TILE).toInt()
        val ty0 = floor(tl.y * f / Track.TILE).toInt(); val ty1 = floor(br.y * f / Track.TILE).toInt()
        LaunchedEffect(tileZ, tx0, tx1, ty0, ty1) {
            val wanted = buildList {
                for (x in tx0..tx1) for (y in ty0..ty1) TileKey(tileZ, x, y).takeIf { it !in tiles }?.let(::add)
            }
            if (wanted.size > MAX_TILES) return@LaunchedEffect
            val gate = Semaphore(4)
            coroutineScope {
                wanted.map { k ->
                    async { gate.withPermit { OsmTiles.tile(context, k.z, k.x, k.y)?.let { tiles[k] = it.asImageBitmap() } } }
                }.awaitAll()
            }
        }

        val colours = remember(points, good, fair, poor) {
            points.map { p -> p.rsrp?.let { if (it >= -90) good else if (it >= -105) fair else poor } ?: none }
        }
        fun nearestIndex(t: Long): Int {
            val i = points.binarySearchBy(t) { it.t }
            if (i >= 0) return i
            val ins = -i - 1
            return listOf(ins - 1, ins).filter { it in points.indices }.minByOrNull { kotlin.math.abs(points[it].t - t) } ?: 0
        }
        val eventIdx = remember(points, events) { events.map { nearestIndex(it.t) }.distinct() }

        Canvas(
            Modifier.fillMaxSize().pointerInput(world, centre, panWithOneFinger) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var moved = false
                    var lastCentroid = Offset.Unspecified
                    var lastSpan = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            moved = true
                            val c = Offset((pressed[0].position.x + pressed[1].position.x) / 2, (pressed[0].position.y + pressed[1].position.y) / 2)
                            val span = (pressed[0].position - pressed[1].position).getDistance().coerceAtLeast(1f)
                            if (lastCentroid.isSpecified) {
                                val next = (scale * span / lastSpan).coerceIn(1f, 64f)
                                val k = next / scale
                                // Keep the point under the fingers fixed while zooming, then follow the pan.
                                offset = (lastCentroid - viewCentre) - (lastCentroid - viewCentre - offset) * k + (c - lastCentroid)
                                scale = next
                            }
                            lastCentroid = c; lastSpan = span
                            pressed.fastForEach { it.consume() }
                        } else {
                            val p = pressed[0]
                            if ((p.position - down.position).getDistance() > viewConfiguration.touchSlop || p.isConsumed) moved = true
                            if (moved && panWithOneFinger && !p.isConsumed) {
                                offset += p.position - p.previousPosition
                                p.consume()
                            }
                        }
                    }
                    if (!moved && points.isNotEmpty()) {
                        val hit = world.indices.minBy { i -> (toScreen(world[i].first, world[i].second) - down.position).getDistance() }
                        val d = (toScreen(world[hit].first, world[hit].second) - down.position).getDistance()
                        if (d <= 32 * density) onSelect(points[hit].t)
                    }
                }
            },
        ) {
            // Coarser tiles first, so a fresh zoom level draws over the blurry one while it loads.
            tiles.entries.sortedBy { it.key.z }.forEach { (k, img) ->
                if (k.z > tileZ) return@forEach
                val size = Track.TILE / 2.0.pow(k.z - baseZ)
                val topLeft = toScreen(k.x * size, k.y * size)
                val px = (size * scale * density).toFloat()
                if (topLeft.x > wPx || topLeft.y > hPx || topLeft.x + px < 0 || topLeft.y + px < 0) return@forEach
                drawImage(
                    img, dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = IntSize(px.toInt() + 1, px.toInt() + 1),
                )
            }
            val stroke = 4 * density
            for (i in 1 until world.size) {
                if (points[i].t - points[i - 1].t > GAP_MS) continue
                drawLine(colours[i], toScreen(world[i - 1].first, world[i - 1].second), toScreen(world[i].first, world[i].second), stroke, StrokeCap.Round)
            }
            eventIdx.forEach { i ->
                val p = toScreen(world[i].first, world[i].second)
                drawCircle(Color.White, 4.5f * density, p)
                drawCircle(Color.Black, 4.5f * density, p, style = Stroke(1.5f * density))
            }
            if (live && world.isNotEmpty()) {
                val p = toScreen(world.last().first, world.last().second)
                drawCircle(Color.White, 8 * density, p)
                drawCircle(accent, 6 * density, p)
            }
            selectedT?.takeIf { points.isNotEmpty() }?.let { t ->
                val i = nearestIndex(t)
                val p = toScreen(world[i].first, world[i].second)
                drawCircle(accent, 9 * density, p, style = Stroke(3 * density))
            }
        }
        if (scale != 1f || offset != Offset.Zero) {
            // A filled button: plain text would vanish against the map tiles.
            FilledTonalButton(onClick = { scale = 1f; offset = Offset.Zero }, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.replay_map_fit))
            }
        }
        onExpand?.let {
            FilledTonalIconButton(onClick = it, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(36.dp)) {
                Icon(Icons.Default.OpenInFull, stringResource(R.string.map_expand), Modifier.size(18.dp))
            }
        }
        // The OSM tile licence requires visible attribution wherever its tiles are shown.
        Text(
            "© OpenStreetMap contributors", fontSize = 9.sp, color = Color.Black,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
