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
import java.io.File
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.stats.BandShare
import dev.satotek.cellscope.data.stats.DistBin
import dev.satotek.cellscope.data.stats.StatsAgg
import dev.satotek.cellscope.data.stats.StatsAggregator
import dev.satotek.cellscope.ui.components.EventRow
import dev.satotek.cellscope.ui.components.Metric
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.util.Locale

private val windows = listOf(
    R.string.win_2m to 2 * 60_000L,
    R.string.win_5m to 5 * 60_000L,
    R.string.win_15m to 15 * 60_000L,
    R.string.win_60m to 60 * 60_000L,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(s: Snapshot, logFile: File? = null) {
    var win by rememberSaveable { mutableIntStateOf(1) }
    val windowMs = windows[win].second
    val lastT = s.history.lastOrNull()?.t ?: 0L
    val range = (lastT - windowMs)..lastT
    val agg = remember(s.history, s.events, windowMs) { StatsAggregator.aggregate(s.history, s.events, range) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tab_stats), style = MaterialTheme.typography.headlineMedium, color = Palette.text)
                        Text(
                            "${agg.sampleCount} samples · ${fmtDur(agg.caTotal)}",
                            style = MaterialTheme.typography.bodySmall, color = Palette.textDim,
                        )
                    }
                    AiDigestButton(s.history, s.events, range, logFile)
                }
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

private fun pct(part: Long, total: Long): String =
    if (total <= 0L) "—" else String.format(Locale.US, "%.0f%%", part * 100.0 / total)

private fun fmtMbps(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it / 1_000_000.0) } ?: "—"
private fun fmtMs(v: Double?): String = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
