package dev.satotek.cellscope.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Series(val label: String, val color: Color, val pick: (SignalSample) -> Int?)

/** Background quality zones: everything below `upTo` is painted with `color`. Listed ascending. */
data class Zone(val upTo: Int, val color: Color)

/**
 * Time-based signal chart: fixed window (latest `windowMs`), y axis with labels,
 * quality zones, per-minute time ticks, and dashed markers on cell changes.
 */
@Composable
fun SignalChart(
    samples: List<SignalSample>, events: List<CellEvent>, series: List<Series>,
    yMin: Int, yMax: Int, yStep: Int, zones: List<Zone>,
    windowMs: Long = 5 * 60_000L, heightDp: Int = 190, modifier: Modifier = Modifier,
    yFormat: (Int) -> String = { it.toString() },
    pillsFor: Int = Int.MAX_VALUE,   // how many leading series get a current-value pill
    cursorT: Long? = null,           // draws a vertical cursor at this sample time
    onTapSample: ((SignalSample) -> Unit)? = null,
    range: LongRange? = null,        // when set, x-axis is [start, end] and windowMs is ignored
    scrubEnabled: Boolean = false,
    onScrubbing: (Boolean) -> Unit = {},
) {
    val tm = rememberTextMeasurer()
    val grid = Palette.outline; val dim = Palette.textDim; val hoColor = Palette.wcdma
    val axisStyle = TextStyle(fontFamily = Mono, fontSize = 9.sp, color = dim)
    val pillStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = Palette.bg)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
    val timeFmtSec = SimpleDateFormat("HH:mm:ss", Locale.US)

    val cursorColor = Palette.accent
    val selectSample = rememberUpdatedState(onTapSample)
    val scrubbing = rememberUpdatedState(onScrubbing)
    val tapMod = if (onTapSample == null) Modifier else Modifier.pointerInput(samples, windowMs, range, scrubEnabled) {
        fun select(x: Float) {
            val now = samples.lastOrNull()?.t ?: return
            val leftPad = 34.dp.toPx(); val rightPad = 40.dp.toPx()
            val w = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val t0 = range?.first ?: (now - windowMs)
            val span = range?.let { (it.last - it.first).coerceAtLeast(1) } ?: windowMs
            val t = t0 + (((x - leftPad) / w).coerceIn(0f, 1f) * span).toLong()
            val pool = samples.filter { it.t in t0..(t0 + span) }
            pool.minByOrNull { kotlin.math.abs(it.t - t) }?.let { selectSample.value?.invoke(it) }
        }
        if (scrubEnabled) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                scrubbing.value(true)
                try {
                    select(down.position.x)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull { it.id == down.id && it.pressed }?.let { select(it.position.x) }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                } finally {
                    scrubbing.value(false)
                }
            }
        } else {
            detectTapGestures { select(it.x) }
        }
    }
    Canvas(modifier.fillMaxWidth().height(heightDp.dp).then(tapMod)) {
        val leftPad = 34.dp.toPx(); val rightPad = 40.dp.toPx(); val bottomPad = 16.dp.toPx(); val topPad = 6.dp.toPx()
        val plot = Rect(leftPad, topPad, size.width - rightPad, size.height - bottomPad)
        val now = samples.lastOrNull()?.t ?: System.currentTimeMillis()
        val t0: Long
        val t1: Long
        val span: Long
        if (range != null) {
            t0 = range.first; t1 = range.last; span = (t1 - t0).coerceAtLeast(1)
        } else {
            t1 = now; t0 = now - windowMs; span = windowMs
        }
        fun x(t: Long) = plot.left + ((t - t0).toFloat() / span) * plot.width
        fun y(v: Float) = plot.bottom - ((v - yMin) / (yMax - yMin)).coerceIn(0f, 1f) * plot.height

        // quality zones
        var lower = yMin
        zones.forEach { z ->
            val top = y(z.upTo.toFloat()); val bottom = y(lower.toFloat())
            drawRect(z.color, Offset(plot.left, top), Size(plot.width, bottom - top))
            lower = z.upTo
        }
        // horizontal grid + y labels
        var g = yMin
        while (g <= yMax) {
            val yy = y(g.toFloat())
            drawLine(grid, Offset(plot.left, yy), Offset(plot.right, yy), 1f)
            val txt = tm.measure(yFormat(g), axisStyle)
            drawText(txt, topLeft = Offset(plot.left - txt.size.width - 4.dp.toPx(), yy - txt.size.height / 2))
            g += yStep
        }
        // Keep time labels legible for both live and replay windows.
        val tickStep = listOf(1L, 5L, 15L, 30L, 60L, 120L, 300L, 900L, 1800L, 3600L, 21600L)
            .map { it * 1_000L }.firstOrNull { it >= span / 5 } ?: (span / 5).coerceAtLeast(1L)
        val tickFmt = if (tickStep < 60_000L) timeFmtSec else timeFmt
        var tick = (t0 / tickStep + 1) * tickStep
        while (tick <= t1) {
            val xx = x(tick)
            drawLine(grid, Offset(xx, plot.top), Offset(xx, plot.bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            val txt = tm.measure(tickFmt.format(Date(tick)), axisStyle)
            drawText(txt, topLeft = Offset(xx - txt.size.width / 2, plot.bottom + 2.dp.toPx()))
            tick += tickStep
        }
        // cell-change markers
        events.filter { it.t >= t0 && it.t <= t1 }.forEach { e ->
            val xx = x(e.t)
            val col = if (e.kind == "HO") dim else hoColor
            drawLine(col, Offset(xx, plot.top), Offset(xx, plot.bottom), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
            val txt = tm.measure(e.to.band, TextStyle(fontFamily = Mono, fontSize = 8.sp, color = col))
            drawText(txt, topLeft = Offset(xx + 2.dp.toPx(), plot.top))
        }
        // cursor
        cursorT?.takeIf { it >= t0 && it <= t1 }?.let { ct ->
            val xx = x(ct)
            drawLine(cursorColor, Offset(xx, plot.top), Offset(xx, plot.bottom), 2.dp.toPx())
        }
        // series
        val visible = samples.filter { it.t >= t0 && it.t <= t1 }
        series.forEachIndexed { idx, s ->
            val path = Path(); val fill = Path(); var pen = false; var firstX = 0f; var lastX = 0f; var last: Int? = null
            visible.forEach { smp ->
                val v = s.pick(smp)
                if (v == null) { pen = false; return@forEach }
                val p = Offset(x(smp.t), y(v.toFloat()))
                if (!pen) { path.moveTo(p.x, p.y); if (idx == 0 && fill.isEmpty) { fill.moveTo(p.x, plot.bottom); firstX = p.x }; if (idx == 0) fill.lineTo(p.x, p.y); pen = true }
                else { path.lineTo(p.x, p.y); if (idx == 0) fill.lineTo(p.x, p.y) }
                lastX = p.x; last = v
            }
            if (idx == 0 && !fill.isEmpty) {
                fill.lineTo(lastX, plot.bottom); fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.25f), s.color.copy(alpha = 0f)), startY = plot.top, endY = plot.bottom))
            }
            drawPath(path, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // current-value pill at the right edge
            last?.let { v ->
                val yy = y(v.toFloat())
                drawCircle(s.color, 3.5.dp.toPx(), Offset(lastX, yy))
                if (idx >= pillsFor) return@let
                val txt = tm.measure(yFormat(v), pillStyle)
                val w = txt.size.width + 8.dp.toPx(); val h = txt.size.height + 2.dp.toPx()
                val top = (yy - h / 2 + idx * (h + 2.dp.toPx())).coerceIn(plot.top, plot.bottom - h)
                drawRoundRect(s.color, Offset(plot.right + 4.dp.toPx(), top), Size(w, h), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                drawText(txt, topLeft = Offset(plot.right + 8.dp.toPx(), top + 1.dp.toPx()))
            }
        }
    }
}

/** Horizontal strip coloured by RAT / band per sample – a compact "what was I on" timeline. */
@Composable
fun RatStrip(samples: List<SignalSample>, windowMs: Long = 5 * 60_000L, modifier: Modifier = Modifier, range: LongRange? = null) {
    val colors = dev.satotek.cellscope.data.model.Rat.entries.associateWith { Palette.rat(it) }
    val track = Palette.outline
    val ca = Palette.accent
    // Two lanes: top = RAT (hue), bottom = carrier aggregation (opacity grows with the number of CCs).
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val topH = size.height * 0.6f
        val gap = 2.dp.toPx()
        drawRect(track, Offset.Zero, Size(size.width, topH))
        drawRect(track, Offset(0f, topH + gap), Size(size.width, size.height - topH - gap))
        val now = samples.lastOrNull()?.t ?: return@Canvas
        val t0: Long
        val span: Long
        if (range != null) {
            t0 = range.first; span = (range.last - range.first).coerceAtLeast(1)
        } else {
            t0 = now - windowMs; span = windowMs
        }
        val t1 = t0 + span
        val vis = samples.filter { it.t >= t0 && it.t <= t1 }
        vis.forEachIndexed { i, s ->
            val x1 = ((s.t - t0).toFloat() / span) * size.width
            val x2 = if (i + 1 < vis.size) ((vis[i + 1].t - t0).toFloat() / span) * size.width else size.width
            val w = (x2 - x1).coerceAtLeast(1f)
            drawRect(colors.getValue(s.rat), Offset(x1, 0f), Size(w, topH))
            if (s.ccCount > 1) drawRect(ca.copy(alpha = (0.35f + 0.2f * (s.ccCount - 1)).coerceAtMost(1f)), Offset(x1, topH + gap), Size(w, size.height - topH - gap))
        }
    }
}
