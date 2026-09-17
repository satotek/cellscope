package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.ui.components.EventRow
import dev.satotek.cellscope.ui.components.Metric
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.util.Locale
import kotlin.math.min

private val windows = listOf(
    R.string.win_2m to 2 * 60_000L,
    R.string.win_5m to 5 * 60_000L,
    R.string.win_15m to 15 * 60_000L,
    R.string.win_60m to 60 * 60_000L,
)

private data class BandShare(val label: String, val rat: Rat, val ms: Long)
private data class DistBin(val mid: Int, val ms: Long)
private data class TripleStat(val max: Double?, val avg: Double?, val p95: Double?)
private data class StatsAgg(
    val bands: List<BandShare>,
    val bandTotal: Long,
    val kindCounts: Map<String, Int>,
    val recent: List<CellEvent>,
    val rsrp: List<DistBin>,
    val sinr: List<DistBin>,
    val caLte: LongArray, // 1CC, 2CC, 3CC, 4CC+
    val caNr: LongArray,  // none, 1CC, 2CC+
    val caTotal: Long,
    val rx: TripleStat,
    val tx: TripleStat,
    val rtt: TripleStat,
    val rttLossPct: Double?,
    val sampleCount: Int,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(s: Snapshot) {
    var win by rememberSaveable { mutableIntStateOf(1) }
    val windowMs = windows[win].second
    val agg = remember(s.history, s.events, windowMs) { aggregate(s.history, s.events, windowMs) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text(stringResource(R.string.tab_stats), style = MaterialTheme.typography.headlineMedium, color = Palette.text)
                Text(
                    "${agg.sampleCount} samples · ${fmtDur(agg.caTotal)}",
                    style = MaterialTheme.typography.bodySmall, color = Palette.textDim,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    windows.forEachIndexed { i, (label, _) ->
                        ToggleButton(
                            checked = win == i, onCheckedChange = { win = i }, modifier = Modifier.weight(1f),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                windows.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text(stringResource(label), fontFamily = Mono, fontSize = 12.sp) }
                    }
                }
            }
        }
        item { BandSharePanel(agg) }
        item { CellChangesPanel(agg) }
        item {
            DistributionPanel(
                title = stringResource(R.string.stats_rsrp), unit = "dBm", bins = agg.rsrp, min = -140, max = -44,
                poorBelow = -105, goodFrom = -90, colorOf = { Palette.rsrp(it) },
            )
        }
        item {
            DistributionPanel(
                title = stringResource(R.string.stats_sinr), unit = "dB", bins = agg.sinr, min = -10, max = 30,
                poorBelow = 0, goodFrom = 13, colorOf = { Palette.sinr(it) },
            )
        }
        item { CaPanel(agg) }
        item { RatePanel(agg) }
    }
}

// ---- panels ------------------------------------------------------------------------------------

@Composable
private fun BandSharePanel(agg: StatsAgg) {
    val colors = agg.bands.map { Palette.rat(it.rat) }
    Panel(title = stringResource(R.string.stats_rat_band), accent = Palette.accent) {
        if (agg.bandTotal <= 0L) { Text("—", fontFamily = Mono, color = Palette.textDim); return@Panel }
        StackedBar(agg.bands.map { it.ms.toFloat() / agg.bandTotal }, colors)
        Spacer(Modifier.height(10.dp))
        agg.bands.take(6).forEachIndexed { i, b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                Spacer(Modifier.width(10.dp))
                Text(b.label, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors[i], modifier = Modifier.width(64.dp))
                Text(b.rat.label, style = MaterialTheme.typography.bodySmall, color = Palette.textDim, modifier = Modifier.weight(1f))
                Text(fmtDur(b.ms), fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim)
                Spacer(Modifier.width(14.dp))
                Text(pct(b.ms, agg.bandTotal), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.text, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun CellChangesPanel(agg: StatsAgg) {
    val total = agg.kindCounts.values.sum()
    Panel(title = stringResource(R.string.stats_cell_changes) + " · $total", accent = Palette.lte) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { Metric(stringResource(R.string.stats_kind_ho), (agg.kindCounts["HO"] ?: 0).toString(), big = true) }
            Box(Modifier.weight(1f)) { Metric(stringResource(R.string.stats_kind_band), (agg.kindCounts["BAND"] ?: 0).toString(), big = true) }
            Box(Modifier.weight(1f)) { Metric(stringResource(R.string.stats_kind_rat), (agg.kindCounts["RAT"] ?: 0).toString(), big = true) }
        }
        if (agg.recent.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Palette.outline)
            Spacer(Modifier.height(4.dp))
            agg.recent.forEach { EventRow(it) }
        }
    }
}

/**
 * Histogram over quality zones: the poor / fair / good bands are painted behind the bars like the
 * Signal charts, bars only where there is data, and a share bar underneath answers "how much of the
 * time was it good?" directly.
 */
@Composable
private fun DistributionPanel(
    title: String, unit: String, bins: List<DistBin>, min: Int, max: Int, poorBelow: Int, goodFrom: Int,
    colorOf: @Composable (Int) -> Color,
) {
    val total = bins.sumOf { it.ms }
    val poorMs = bins.filter { it.mid < poorBelow }.sumOf { it.ms }
    val goodMs = bins.filter { it.mid >= goodFrom }.sumOf { it.ms }
    val fairMs = total - poorMs - goodMs
    val barColors = bins.map { colorOf(it.mid) }
    val good = Palette.good; val fair = Palette.fair; val poor = Palette.poor
    val track = Palette.outline
    val step = if (bins.size > 1) (max - min) / bins.size else 1
    Panel(title = title, accent = if (total <= 0L) Palette.textDim else if (goodMs >= total / 2) good else if (poorMs >= total / 2) poor else fair) {
        if (total <= 0L) { Text("—", fontFamily = Mono, color = Palette.textDim); return@Panel }
        val maxMs = bins.maxOf { it.ms }.coerceAtLeast(1L)
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val w = size.width; val h = size.height
            fun x(v: Int) = ((v - min).toFloat() / (max - min)) * w
            // zone backgrounds
            drawRect(poor.copy(alpha = 0.08f), Offset(0f, 0f), Size(x(poorBelow), h))
            drawRect(fair.copy(alpha = 0.08f), Offset(x(poorBelow), 0f), Size(x(goodFrom) - x(poorBelow), h))
            drawRect(good.copy(alpha = 0.08f), Offset(x(goodFrom), 0f), Size(w - x(goodFrom), h))
            // baseline
            drawLine(track, Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
            // bars
            val gap = 2.dp.toPx()
            val bw = w / bins.size
            bins.forEachIndexed { i, b ->
                if (b.ms <= 0L) return@forEachIndexed
                val bh = (b.ms.toFloat() / maxMs) * (h - 6.dp.toPx())
                drawRoundRect(barColors[i], Offset(i * bw + gap / 2, h - bh), Size(bw - gap, bh), CornerRadius(3.dp.toPx()))
            }
        }
        Spacer(Modifier.height(4.dp))
        // axis: min and max at the edges, zone boundaries centred on their x position
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            val w = maxWidth
            Text("$min", fontFamily = Mono, fontSize = 10.sp, color = Palette.textDim, modifier = Modifier.align(Alignment.CenterStart))
            Text("$max", fontFamily = Mono, fontSize = 10.sp, color = Palette.textDim, modifier = Modifier.align(Alignment.CenterEnd))
            listOf(poorBelow, goodFrom).forEach { v ->
                val frac = (v - min).toFloat() / (max - min)
                Text(
                    "$v", fontFamily = Mono, fontSize = 10.sp, color = Palette.textDim,
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = w * frac - 12.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        StackedBar(listOf(poorMs, fairMs, goodMs).map { it.toFloat() / total }, listOf(poor, fair, good))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            ShareLabel(stringResource(R.string.stats_poor), poorMs, total, poor)
            Spacer(Modifier.width(16.dp))
            ShareLabel(stringResource(R.string.stats_fair), fairMs, total, fair)
            Spacer(Modifier.width(16.dp))
            ShareLabel(stringResource(R.string.stats_good), goodMs, total, good)
            Spacer(Modifier.weight(1f))
            Text(unit, fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
        }
    }
}

@Composable
private fun ShareLabel(label: String, ms: Long, total: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        Spacer(Modifier.width(4.dp))
        Text(pct(ms, total), fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun CaPanel(agg: StatsAgg) {
    val accent = Palette.accent
    val nr = Palette.nr
    val caColors = listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.55f), accent.copy(alpha = 0.75f), accent)
    val nrColors = listOf(Palette.outline, nr.copy(alpha = 0.6f), nr)
    val lteLabels = listOf(R.string.stats_cc1, R.string.stats_cc2, R.string.stats_cc3, R.string.stats_cc4p).map { stringResource(it) }
    val nrLabels = listOf("—", stringResource(R.string.stats_cc1), stringResource(R.string.stats_cc2p))
    Panel(title = stringResource(R.string.stats_ca), accent = Palette.lte) {
        if (agg.caTotal <= 0L) { Text("—", fontFamily = Mono, color = Palette.textDim); return@Panel }
        CaRow("LTE", agg.caLte.toList(), lteLabels, caColors, agg.caTotal)
        if (agg.caNr[1] + agg.caNr[2] > 0) {
            Spacer(Modifier.height(12.dp))
            CaRow("NR", agg.caNr.toList(), nrLabels, nrColors, agg.caTotal)
        }
    }
}

@Composable
private fun CaRow(rat: String, ms: List<Long>, labels: List<String>, colors: List<Color>, total: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rat, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.text, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            StackedBar(ms.map { it.toFloat() / total }, colors)
            Spacer(Modifier.height(6.dp))
            Row {
                labels.zip(ms).zip(colors).filter { it.first.second > 0 }.forEach { (lm, c) ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c).align(Alignment.CenterVertically))
                    Spacer(Modifier.width(5.dp))
                    Text("${lm.first} ${pct(lm.second, total)}", fontFamily = Mono, fontSize = 12.sp, color = Palette.text)
                    Spacer(Modifier.width(14.dp))
                }
            }
        }
    }
}

/** max / avg / p95 as a proper table: rows are the quantities, columns the statistics. */
@Composable
private fun RatePanel(agg: StatsAgg) {
    Panel(title = stringResource(R.string.stats_throughput) + " · " + stringResource(R.string.stats_rtt), accent = Palette.nr) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(96.dp))
            listOf(R.string.stats_max, R.string.stats_avg, R.string.stats_p95).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = Palette.textDim, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(6.dp))
        RateRow("▼ Mbps", Palette.nr, listOf(agg.rx.max, agg.rx.avg, agg.rx.p95).map { fmtMbps(it) })
        RateRow("▲ Mbps", Palette.wcdma, listOf(agg.tx.max, agg.tx.avg, agg.tx.p95).map { fmtMbps(it) })
        RateRow("RTT ms", Palette.text, listOf(agg.rtt.max, agg.rtt.avg, agg.rtt.p95).map { fmtMs(it) })
        agg.rttLossPct?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stats_loss), style = MaterialTheme.typography.labelSmall, color = Palette.textDim, modifier = Modifier.width(96.dp))
                Text(String.format(Locale.US, "%.1f %%", it), fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (it > 5) Palette.poor else if (it > 0) Palette.fair else Palette.good)
            }
        }
    }
}

@Composable
private fun RateRow(label: String, color: Color, values: List<String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.width(96.dp))
        values.forEach { Text(it, fontFamily = Mono, fontSize = 15.sp, color = Palette.text, modifier = Modifier.weight(1f), textAlign = TextAlign.End) }
    }
}

@Composable
private fun StackedBar(fractions: List<Float>, colors: List<Color>) {
    Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        var x = 0f
        fractions.zip(colors).forEach { (f, c) ->
            val w = size.width * f.coerceAtLeast(0f)
            if (w > 0f) drawRect(c, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
        }
    }
}

private fun fmtDur(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%dh %02dm", s / 3600, s % 3600 / 60)
    else if (s >= 60) String.format(Locale.US, "%dm %02ds", s / 60, s % 60)
    else "${s}s"
}

private fun aggregate(history: List<SignalSample>, events: List<CellEvent>, windowMs: Long): StatsAgg {
    val weighted = timeWeighted(history, windowMs)
    val lastT = history.lastOrNull()?.t ?: 0L
    val cutoff = lastT - windowMs
    val visEvents = events.filter { it.t >= cutoff }

    val bandMs = LinkedHashMap<String, Pair<Rat, Long>>()
    val rsrp = LongArray(rsrpBins)
    val sinr = LongArray(sinrBins)
    val caLte = LongArray(4)
    val caNr = LongArray(3)
    var caTotalMs = 0L
    val rx = ArrayList<Pair<Double, Long>>()
    val tx = ArrayList<Pair<Double, Long>>()
    val rttOk = ArrayList<Pair<Double, Long>>()
    var rttSent = 0L
    var rttLost = 0L

    for ((smp, ms) in weighted) {
        val prev = bandMs[smp.band]
        bandMs[smp.band] = (smp.rat) to ((prev?.second ?: 0L) + ms)
        smp.rsrp?.let { rsrp[binIndex(it, -140, -44, 10)] += ms }
        smp.sinr?.let { sinr[binIndex(it, -10, 30, 5)] += ms }
        // Samples from before the split carry only ccCount; treat those as LTE-side for bucketing.
        val lte = if (smp.ccLte + smp.ccNr > 0) smp.ccLte else smp.ccCount
        val nr = if (smp.ccLte + smp.ccNr > 0) smp.ccNr else 0
        if (lte > 0) caLte[min(lte, 4) - 1] += ms   // SA (no LTE leg) contributes nothing here
        caNr[min(nr, 2)] += ms
        caTotalMs += ms
        smp.rxBps?.let { rx += it.toDouble() to ms }
        smp.txBps?.let { tx += it.toDouble() to ms }
        val rtt = smp.rttMs
        if (rtt != null) {
            rttSent += ms
            if (rtt < 0) rttLost += ms else rttOk += rtt to ms
        }
    }

    val bands = bandMs.entries
        .map { BandShare(it.key, it.value.first, it.value.second) }
        .sortedByDescending { it.ms }
    val bandTotal = bands.sumOf { it.ms }

    return StatsAgg(
        bands = bands,
        bandTotal = bandTotal,
        kindCounts = visEvents.groupingBy { it.kind }.eachCount(),
        recent = visEvents.asReversed().take(10),
        rsrp = List(rsrpBins) { i -> DistBin(binMid(i, -140, -44, 10), rsrp[i]) },
        sinr = List(sinrBins) { i -> DistBin(binMid(i, -10, 30, 5), sinr[i]) },
        caLte = caLte,
        caNr = caNr,
        caTotal = caTotalMs,
        rx = triple(rx),
        tx = triple(tx),
        rtt = triple(rttOk),
        rttLossPct = if (rttSent == 0L) null else rttLost * 100.0 / rttSent,
        sampleCount = weighted.size,
    )
}

/** Weight = elapsed ms until the next sample; last sample contributes 0. Interval clipped to the window. */
private fun timeWeighted(samples: List<SignalSample>, windowMs: Long): List<Pair<SignalSample, Long>> {
    if (samples.size < 2) return emptyList()
    val cutoff = samples.last().t - windowMs
    val out = ArrayList<Pair<SignalSample, Long>>(samples.size)
    for (i in 0 until samples.lastIndex) {
        val a = samples[i]
        val end = samples[i + 1].t
        if (end <= cutoff) continue
        val dt = end - maxOf(a.t, cutoff)
        if (dt > 0) out += a to dt
    }
    return out
}

private const val rsrpBins = 10 // (-140..-44] / 10 dB
private const val sinrBins = 8  // [-10..30] / 5 dB

private fun binIndex(v: Int, min: Int, max: Int, step: Int): Int {
    val c = v.coerceIn(min, max)
    val n = (max - min + step - 1) / step
    return ((c - min) / step).coerceAtMost(n - 1)
}

private fun binMid(i: Int, min: Int, max: Int, step: Int): Int {
    val start = min + i * step
    val end = minOf(start + step, max)
    return (start + end) / 2
}

private fun triple(values: List<Pair<Double, Long>>): TripleStat {
    if (values.isEmpty()) return TripleStat(null, null, null)
    val max = values.maxOf { it.first }
    val wsum = values.sumOf { it.second }
    val avg = if (wsum > 0) values.sumOf { it.first * it.second } / wsum else null
    return TripleStat(max, avg, weightedP95(values))
}

private fun weightedP95(values: List<Pair<Double, Long>>): Double? {
    val total = values.sumOf { it.second }
    if (total <= 0L) return values.maxOfOrNull { it.first }
    val sorted = values.sortedBy { it.first }
    val target = total * 0.95
    var acc = 0L
    for ((v, w) in sorted) {
        acc += w
        if (acc >= target) return v
    }
    return sorted.last().first
}

private fun pct(part: Long, total: Long): String =
    if (total <= 0L) "—" else String.format(Locale.US, "%.0f%%", part * 100.0 / total)

private fun fmtMbps(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it / 1_000_000.0) } ?: "—"
private fun fmtMs(v: Double?): String = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
