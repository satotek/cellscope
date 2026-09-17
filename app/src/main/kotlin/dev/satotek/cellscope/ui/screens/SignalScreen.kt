package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.satotek.cellscope.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.PingStats
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.ui.components.EventRow
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.RatStrip
import dev.satotek.cellscope.ui.components.Series
import dev.satotek.cellscope.ui.components.SignalChart
import dev.satotek.cellscope.ui.components.Zone
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.util.Locale

private val windows = listOf("2m" to 2 * 60_000L, "5m" to 5 * 60_000L, "15m" to 15 * 60_000L, "60m" to 60 * 60_000L)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignalScreen(s: Snapshot) {
    val h = s.history
    var win by rememberSaveable { mutableIntStateOf(1) }
    // Tapped sample → inspector sheet. Kept as a time so it survives new samples arriving.
    var selectedT by remember { mutableStateOf<Long?>(null) }
    var eventsExpanded by rememberSaveable { mutableStateOf(false) }
    val onTap: (SignalSample) -> Unit = { selectedT = it.t }
    val windowMs = windows[win].second
    val vis = h.filter { it.t >= (h.lastOrNull()?.t ?: 0L) - windowMs }
    val last = h.lastOrNull()
    val hasNr = vis.any { it.nrRsrp != null }
    val servingColor = Palette.rat(last?.rat ?: Rat.LTE)

    val zonesRsrp = listOf(Zone(-105, Palette.poor.copy(alpha = 0.07f)), Zone(-90, Palette.fair.copy(alpha = 0.06f)), Zone(-44, Palette.good.copy(alpha = 0.05f)))
    val zonesRsrq = listOf(Zone(-15, Palette.poor.copy(alpha = 0.07f)), Zone(-10, Palette.fair.copy(alpha = 0.06f)), Zone(0, Palette.good.copy(alpha = 0.05f)))
    val zonesSinr = listOf(Zone(0, Palette.poor.copy(alpha = 0.07f)), Zone(13, Palette.fair.copy(alpha = 0.06f)), Zone(40, Palette.good.copy(alpha = 0.05f)))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text(stringResource(R.string.tab_signal), style = MaterialTheme.typography.headlineMedium, color = Palette.text)
                Text("${h.size} samples · ${s.events.size} cell changes", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
                Spacer(Modifier.height(10.dp))
                // Expressive connected button group: the selected segment morphs into a pill.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    windows.forEachIndexed { i, (label, _) ->
                        ToggleButton(
                            checked = win == i, onCheckedChange = { win = i }, modifier = Modifier.weight(1f),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                windows.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text(label, fontFamily = Mono, fontSize = 12.sp) }
                    }
                }
            }
        }
        item {
            // RAT / CA strip with legend
            Column {
                RatStrip(h, windowMs, Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend(Palette.nr, "NR"); Legend(Palette.lte, "LTE"); Legend(Palette.wcdma, "3G")
                    Spacer(Modifier.width(6.dp)); Legend(Palette.accent, "CA")
                }
            }
        }
        item {
            ChartPanel("RSRP", "dBm", last?.rsrp, Palette.rsrp(last?.rsrp), hasNr, last?.nrRsrp, servingColor = servingColor) {
                SignalChart(h, s.events, listOfNotNull(
                    Series("serving", servingColor) { it.rsrp },
                    if (hasNr) Series("NR SCC", Palette.nr) { it.nrRsrp } else null,
                ), -140, -60, 20, zonesRsrp, windowMs, cursorT = selectedT, onTapSample = onTap)
            }
        }
        item {
            ChartPanel("RSRQ", "dB", last?.rsrq, Palette.rsrq(last?.rsrq), hasNr, last?.nrRsrq, servingColor = servingColor) {
                SignalChart(h, s.events, listOfNotNull(
                    Series("serving", servingColor) { it.rsrq },
                    if (hasNr) Series("NR SCC", Palette.nr) { it.nrRsrq } else null,
                ), -25, 0, 5, zonesRsrq, windowMs, heightDp = 150, cursorT = selectedT, onTapSample = onTap)
            }
        }
        item {
            ChartPanel("SINR", "dB", last?.sinr, Palette.sinr(last?.sinr), hasNr, last?.nrSinr, servingColor = servingColor) {
                SignalChart(h, s.events, listOfNotNull(
                    Series("serving", servingColor) { it.sinr },
                    if (hasNr) Series("NR SCC", Palette.nr) { it.nrSinr } else null,
                ), -10, 30, 10, zonesSinr, windowMs, heightDp = 150, cursorT = selectedT, onTapSample = onTap)
            }
        }
        item {
            val maxMbps = (vis.maxOfOrNull { maxOf(it.rxBps ?: 0, it.txBps ?: 0) } ?: 0L) / 1_000_000.0
            val top = listOf(5, 10, 20, 50, 100, 200, 500, 1000, 2000).firstOrNull { it > maxMbps } ?: 4000
            ChartPanel("Throughput", "Mbps", last?.rxBps?.let { (it / 1_000_000).toInt() }, Palette.text, false, null, subtitle = "▼ ${fmtMbps(last?.rxBps)}  ▲ ${fmtMbps(last?.txBps)} Mbps") {
                SignalChart(h, s.events, listOf(
                    Series("rx", Palette.nr) { it.rxBps?.let { b -> (b / 100_000).toInt() } },
                    Series("tx", Palette.wcdma) { it.txBps?.let { b -> (b / 100_000).toInt() } },
                ), 0, top * 10, top * 10 / 5, emptyList(), windowMs, heightDp = 140, yFormat = { (it / 10).toString() }, cursorT = selectedT, onTapSample = onTap)
            }
        }
        item {
            // Neighbour time-series: the 6 cells seen most often in the window, keyed by band/PCI.
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
                SignalChart(h, s.events,
                    listOf(Series("serving", servingColor) { it.rsrp }) + keys.mapIndexed { i, k ->
                        Series(k, palette[i]) { smp -> smp.neighbours.firstOrNull { it.key == k }?.rsrp }
                    }, -140, -60, 20, zonesRsrp, windowMs, heightDp = 200, pillsFor = 1, cursorT = selectedT, onTapSample = onTap)
                
            }
        }
        item {
            val rtts = vis.mapNotNull { it.rttMs }
            val ok = rtts.filter { it >= 0 }
            val st = PingStats(rtts.size, rtts.count { it < 0 }, ok.minOrNull(), ok.average().takeIf { ok.isNotEmpty() }, ok.maxOrNull(),
                jitter = if (ok.size > 1) ok.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average() else null)
            val top = listOf(50, 100, 200, 500, 1000, 2000).firstOrNull { it > (st.max ?: 0.0) } ?: 5000
            val lastRtt = last?.rttMs
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PING · RTT ms", style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                        Text("min ${fmtMs(st.min)} · avg ${fmtMs(st.avg)} · max ${fmtMs(st.max)} · jitter ${fmtMs(st.jitter)} · loss ${String.format(Locale.US, "%.1f", st.lossPct)}%", fontFamily = Mono, fontSize = 11.sp, color = Palette.text)
                    }
                    val c = when { lastRtt == null -> Palette.textDim; lastRtt < 0 -> Palette.poor; lastRtt < 50 -> Palette.good; lastRtt < 120 -> Palette.fair; else -> Palette.poor }
                    Text(if (lastRtt == null) "—" else if (lastRtt < 0) "LOST" else lastRtt.toInt().toString(), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = c)
                    Text(" ms", style = MaterialTheme.typography.bodySmall, color = Palette.textDim, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(6.dp))
                SignalChart(h, s.events, listOf(Series("rtt", Palette.nr) { it.rttMs?.takeIf { r -> r >= 0 }?.toInt() }),
                    0, top, top / 5, listOf(Zone(50, Palette.good.copy(alpha = 0.05f)), Zone(120, Palette.fair.copy(alpha = 0.06f)), Zone(top, Palette.poor.copy(alpha = 0.07f))), windowMs, heightDp = 140, cursorT = selectedT, onTapSample = onTap)
            }
        }
        // Cell changes: newest first, 5 rows by default so the tab doesn't grow without bound; expand on demand.
        val shown = if (eventsExpanded) s.events.asReversed().take(50) else s.events.asReversed().take(5)
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("CELL CHANGES · ${s.events.size}", style = MaterialTheme.typography.labelSmall, color = Palette.textDim, modifier = Modifier.weight(1f))
                if (s.events.size > 5) TextButton(onClick = { eventsExpanded = !eventsExpanded }, contentPadding = ButtonDefaults.ExtraSmallContentPadding) {
                    Text(if (eventsExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_all))
                }
            }
        }
        if (s.events.isEmpty()) item { Text("—", fontFamily = Mono, color = Palette.textDim) }
        items(shown) { EventRow(it) }
    }
    selectedT?.let { t -> if (h.isNotEmpty()) SampleSheet(h, s.events, t, onDismiss = { selectedT = null }) }
}

private fun fmtMs(v: Double?): String = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
private fun fmtMbps(b: Long?): String = b?.let { String.format(Locale.US, "%.1f", it / 1_000_000.0) } ?: "—"


