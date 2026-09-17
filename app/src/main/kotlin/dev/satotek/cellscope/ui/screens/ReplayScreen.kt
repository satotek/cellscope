package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.PingStats
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.replay.LogReader
import dev.satotek.cellscope.data.replay.ReplayData
import dev.satotek.cellscope.ui.components.EventRow
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.RatStrip
import dev.satotek.cellscope.ui.components.Series
import dev.satotek.cellscope.ui.components.SignalChart
import dev.satotek.cellscope.ui.components.Zone
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReplayScreen(file: File) {
    var data by remember(file) { mutableStateOf<ReplayData?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.replay_failed)
    LaunchedEffect(file) {
        data = null; error = null
        runCatching { LogReader.load(file) }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: failed }
    }
    when {
        error != null -> Text(error!!, fontFamily = Mono, color = Palette.poor, modifier = Modifier.padding(16.dp))
        data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        else -> ReplayBody(data!!, file.name)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReplayScreen(data: ReplayData, fileName: String) {
    ReplayBody(data, fileName)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReplayBody(data: ReplayData, fileName: String) {
    val h = data.samples
    val events = data.events
    val dataStart = h.firstOrNull()?.t ?: 0L
    val dataEnd = h.lastOrNull()?.t ?: 0L
    val viewStart = remember { mutableLongStateOf(dataStart) }
    val viewEnd = remember { mutableLongStateOf(if (dataEnd > dataStart) dataEnd else dataStart + 10_000L) }
    LaunchedEffect(dataStart, dataEnd) {
        viewStart.longValue = dataStart
        viewEnd.longValue = if (dataEnd > dataStart) dataEnd else dataStart + 10_000L
    }
    val vs = viewStart.longValue
    val ve = viewEnd.longValue
    val range = vs..ve
    var selectedT by remember { mutableStateOf<Long?>(null) }
    val onTap: (SignalSample) -> Unit = { selectedT = it.t }
    val vis = h.filter { it.t in range }
    val last = h.lastOrNull()
    val hasNr = vis.any { it.nrRsrp != null }
    val servingColor = Palette.rat(last?.rat ?: Rat.LTE)
    val fmtT = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val spanMin = 10_000L
    val spanMax = (dataEnd - dataStart).coerceAtLeast(spanMin)

    val zonesRsrp = listOf(Zone(-105, Palette.poor.copy(alpha = 0.07f)), Zone(-90, Palette.fair.copy(alpha = 0.06f)), Zone(-44, Palette.good.copy(alpha = 0.05f)))
    val zonesRsrq = listOf(Zone(-15, Palette.poor.copy(alpha = 0.07f)), Zone(-10, Palette.fair.copy(alpha = 0.06f)), Zone(0, Palette.good.copy(alpha = 0.05f)))
    val zonesSinr = listOf(Zone(0, Palette.poor.copy(alpha = 0.07f)), Zone(13, Palette.fair.copy(alpha = 0.06f)), Zone(40, Palette.good.copy(alpha = 0.05f)))

    fun clampView(start: Long, end: Long) {
        var w = (end - start).coerceIn(spanMin, spanMax)
        var s = start
        var e = s + w
        if (s < dataStart) { s = dataStart; e = s + w }
        if (e > dataEnd) { e = dataEnd; s = (e - w).coerceAtLeast(dataStart); w = e - s }
        viewStart.longValue = s
        viewEnd.longValue = e.coerceAtLeast(s + 1)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // The file name is already the sub-screen title; this row is the data summary + current view.
            val zoomed = vs > dataStart || ve < dataEnd
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (h.isNotEmpty()) {
                        Text(
                            stringResource(R.string.replay_meta, h.size, events.size, fmtT.format(Date(dataStart)), fmtT.format(Date(dataEnd))),
                            style = MaterialTheme.typography.bodySmall, color = Palette.textDim,
                        )
                    }
                    Text(
                        if (zoomed) "${fmtT.format(Date(vs))} – ${fmtT.format(Date(ve))} · ${fmtSpan(ve - vs)}" else fmtSpan(dataEnd - dataStart),
                        fontFamily = Mono, fontSize = 13.sp, color = if (zoomed) Palette.accent else Palette.text,
                    )
                }
                AiDigestButton(h, events, range)
                if (zoomed) {
                    TextButton(onClick = { clampView(dataStart, dataEnd) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.replay_all), fontFamily = Mono)
                    }
                }
            }
        }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(dataStart, dataEnd, spanMax) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val slop = viewConfiguration.touchSlop
                            var dragging = false
                            var zooming = false
                            var lastCentroid = down.position
                            var lastSpan = 0f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    var sx = 0f; var sy = 0f
                                    pressed.fastForEach { sx += it.position.x; sy += it.position.y }
                                    val c = Offset(sx / pressed.size, sy / pressed.size)
                                    val span = (pressed[0].position - pressed[1].position).getDistance().coerceAtLeast(1f)
                                    if (!zooming) {
                                        zooming = true; dragging = true; lastCentroid = c; lastSpan = span
                                    } else {
                                        val zoom = (span / lastSpan).coerceIn(0.5f, 2f)
                                        val wpx = size.width.toFloat().coerceAtLeast(1f)
                                        val frac = (c.x / wpx).coerceIn(0f, 1f)
                                        val curS = viewStart.longValue
                                        val curE = viewEnd.longValue
                                        val w = (curE - curS).coerceAtLeast(1)
                                        val anchor = curS + (w * frac).toLong()
                                        val newW = (w / zoom).toLong().coerceIn(spanMin, spanMax)
                                        clampView(anchor - (newW * frac).toLong(), anchor - (newW * frac).toLong() + newW)
                                        lastCentroid = c; lastSpan = span
                                    }
                                    pressed.fastForEach { it.consume() }
                                } else {
                                    val ch = pressed[0]
                                    val total = hypot(
                                        (ch.position.x - down.position.x).toDouble(),
                                        (ch.position.y - down.position.y).toDouble(),
                                    ).toFloat()
                                    if (!dragging && !zooming) {
                                        if (total > slop) {
                                            val dx = abs(ch.position.x - down.position.x)
                                            val dy = abs(ch.position.y - down.position.y)
                                            if (dx > dy) {
                                                dragging = true
                                                lastCentroid = ch.position
                                            } else break
                                        }
                                    }
                                    if (dragging && !zooming) {
                                        val wpx = size.width.toFloat().coerceAtLeast(1f)
                                        val w = (viewEnd.longValue - viewStart.longValue).coerceAtLeast(1)
                                        val dt = (-(ch.position.x - lastCentroid.x) / wpx * w).toLong()
                                        clampView(viewStart.longValue + dt, viewEnd.longValue + dt)
                                        lastCentroid = ch.position
                                        ch.consume()
                                    }
                                }
                            }
                        }
                    },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        RatStrip(h, range = range, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Legend(Palette.nr, "NR"); Legend(Palette.lte, "LTE"); Legend(Palette.wcdma, "3G")
                            Spacer(Modifier.width(6.dp)); Legend(Palette.accent, "CA")
                        }
                    }
                    ChartPanel("RSRP", "dBm", last?.rsrp, Palette.rsrp(last?.rsrp), hasNr, last?.nrRsrp, servingColor = servingColor) {
                        SignalChart(h, events, listOfNotNull(
                            Series("serving", servingColor) { it.rsrp },
                            if (hasNr) Series("NR SCC", Palette.nr) { it.nrRsrp } else null,
                        ), -140, -60, 20, zonesRsrp, cursorT = selectedT, onTapSample = onTap, range = range)
                    }
                    ChartPanel("RSRQ", "dB", last?.rsrq, Palette.rsrq(last?.rsrq), hasNr, last?.nrRsrq, servingColor = servingColor) {
                        SignalChart(h, events, listOfNotNull(
                            Series("serving", servingColor) { it.rsrq },
                            if (hasNr) Series("NR SCC", Palette.nr) { it.nrRsrq } else null,
                        ), -25, 0, 5, zonesRsrq, heightDp = 150, cursorT = selectedT, onTapSample = onTap, range = range)
                    }
                    ChartPanel("SINR", "dB", last?.sinr, Palette.sinr(last?.sinr), hasNr, last?.nrSinr, servingColor = servingColor) {
                        SignalChart(h, events, listOfNotNull(
                            Series("serving", servingColor) { it.sinr },
                            if (hasNr) Series("NR SCC", Palette.nr) { it.nrSinr } else null,
                        ), -10, 30, 10, zonesSinr, heightDp = 150, cursorT = selectedT, onTapSample = onTap, range = range)
                    }
                    run {
                        val maxMbps = (vis.maxOfOrNull { maxOf(it.rxBps ?: 0, it.txBps ?: 0) } ?: 0L) / 1_000_000.0
                        val top = listOf(5, 10, 20, 50, 100, 200, 500, 1000, 2000).firstOrNull { it > maxMbps } ?: 4000
                        ChartPanel("Throughput", "Mbps", last?.rxBps?.let { (it / 1_000_000).toInt() }, Palette.text, false, null, subtitle = "▼ ${fmtMbps(last?.rxBps)}  ▲ ${fmtMbps(last?.txBps)} Mbps") {
                            SignalChart(h, events, listOf(
                                Series("rx", Palette.nr) { it.rxBps?.let { b -> (b / 100_000).toInt() } },
                                Series("tx", Palette.wcdma) { it.txBps?.let { b -> (b / 100_000).toInt() } },
                            ), 0, top * 10, top * 10 / 5, emptyList(), heightDp = 140, yFormat = { (it / 10).toString() }, cursorT = selectedT, onTapSample = onTap, range = range)
                        }
                    }
                    run {
                        val counts = HashMap<String, Int>()
                        vis.forEach { smp -> smp.neighbours.forEach { nb -> if (nb.rsrp != null) counts[nb.key] = (counts[nb.key] ?: 0) + 1 } }
                        val keys = counts.entries.sortedByDescending { it.value }.take(6).map { it.key }
                        val palette = listOf(Palette.wcdma, Color(0xFFB39DFF), Color(0xFFFF8AD8), Color(0xFF6EE7F0), Color(0xFFFFA36E), Color(0xFFC7F464))
                        Panel {
                            Text("NEIGHBOURS RSRP", style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                                Legend(servingColor, "serving")
                                keys.forEachIndexed { i, k -> Legend(palette[i], k) }
                            }
                            Spacer(Modifier.height(6.dp))
                            SignalChart(h, events,
                                listOf(Series("serving", servingColor) { it.rsrp }) + keys.mapIndexed { i, k ->
                                    Series(k, palette[i]) { smp -> smp.neighbours.firstOrNull { it.key == k }?.rsrp }
                                }, -140, -60, 20, zonesRsrp, heightDp = 200, pillsFor = 1, cursorT = selectedT, onTapSample = onTap, range = range)
                        }
                    }
                    run {
                        val rtts = vis.mapNotNull { it.rttMs }
                        val ok = rtts.filter { it >= 0 }
                        val st = PingStats(rtts.size, rtts.count { it < 0 }, ok.minOrNull(), ok.average().takeIf { ok.isNotEmpty() }, ok.maxOrNull(),
                            jitter = if (ok.size > 1) ok.zipWithNext { a, b -> abs(a - b) }.average() else null)
                        val top = listOf(50, 100, 200, 500, 1000, 2000).firstOrNull { it > (st.max ?: 0.0) } ?: 5000
                        val lastRtt = last?.rttMs
                        Panel {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("PING · RTT ms", style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                                    Text("min ${fmtMs(st.min)} · avg ${fmtMs(st.avg)} · max ${fmtMs(st.max)} · jitter ${fmtMs(st.jitter)} · loss ${String.format(Locale.US, "%.1f", st.lossPct)}%", fontFamily = Mono, fontSize = 11.sp, color = Palette.text)
                                }
                                val c = when { lastRtt == null -> Palette.textDim; lastRtt < 0 -> Palette.poor; lastRtt < 50 -> Palette.good; lastRtt < 120 -> Palette.fair; else -> Palette.poor }
                                Text(if (lastRtt == null) "—" else if (lastRtt < 0) "LOST" else lastRtt.toInt().toString(), fontFamily = Mono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 28.sp, color = c)
                                Text(" ms", style = MaterialTheme.typography.bodySmall, color = Palette.textDim, modifier = Modifier.padding(top = 10.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            SignalChart(h, events, listOf(Series("rtt", Palette.nr) { it.rttMs?.takeIf { r -> r >= 0 }?.toInt() }),
                                0, top, top / 5, listOf(Zone(50, Palette.good.copy(alpha = 0.05f)), Zone(120, Palette.fair.copy(alpha = 0.06f)), Zone(top, Palette.poor.copy(alpha = 0.07f))), heightDp = 140, cursorT = selectedT, onTapSample = onTap, range = range)
                        }
                    }
                }
            }
        }
        item { Text("CELL CHANGES", style = MaterialTheme.typography.labelSmall, color = Palette.textDim, modifier = Modifier.padding(top = 4.dp)) }
        if (events.isEmpty()) item { Text("—", fontFamily = Mono, color = Palette.textDim) }
        items(events.asReversed().take(30)) { EventRow(it) }
    }
    selectedT?.let { t -> if (h.isNotEmpty()) SampleSheet(h, events, t, onDismiss = { selectedT = null }) }
}

private fun fmtMs(v: Double?): String = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
private fun fmtMbps(b: Long?): String = b?.let { String.format(Locale.US, "%.1f", it / 1_000_000.0) } ?: "—"

private fun fmtSpan(ms: Long): String {
    val sec = ms / 1000
    return if (sec >= 3600) String.format(Locale.US, "%dh %02dm", sec / 3600, sec % 3600 / 60)
    else if (sec >= 60) String.format(Locale.US, "%dm %02ds", sec / 60, sec % 60)
    else "${sec}s"
}
