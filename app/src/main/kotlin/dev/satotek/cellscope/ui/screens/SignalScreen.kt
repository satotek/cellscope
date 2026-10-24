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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.ui.components.EventRow
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.standbyDdsLabel
import dev.satotek.cellscope.ui.components.RatStrip
import dev.satotek.cellscope.ui.components.Series
import dev.satotek.cellscope.ui.components.SignalChart
import dev.satotek.cellscope.ui.components.Zone
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import dev.satotek.cellscope.ui.components.nrStatusLabel
import androidx.compose.ui.text.style.TextOverflow
import dev.satotek.cellscope.ui.components.ChartWindows

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignalScreen(s: Snapshot) {
    val h = s.history
    var minutes by rememberSaveable { mutableIntStateOf(ChartWindows.DEFAULT_MINUTES) }
    // Tapped sample → inspector sheet. Kept as a time so it survives new samples arriving.
    var selectedT by remember { mutableStateOf<Long?>(null) }
    var selectedMetric by remember { mutableStateOf("RSRP") }
    var eventsExpanded by rememberSaveable { mutableStateOf(false) }
    val onTap: (SignalSample) -> Unit = { selectedT = it.t }
    val windowMs = minutes * 60_000L
    val vis = h.filter { it.t >= (h.lastOrNull()?.t ?: 0L) - windowMs }
    val last = h.lastOrNull()
    val hasNr = vis.any { it.nrRsrp != null }
    val servingColor = Palette.rat(last?.rat ?: Rat.LTE)

    val zonesRsrp = listOf(Zone(-105, Palette.poor.copy(alpha = 0.07f)), Zone(-90, Palette.fair.copy(alpha = 0.06f)), Zone(-44, Palette.good.copy(alpha = 0.05f)))
    val zonesRsrq = listOf(Zone(-15, Palette.poor.copy(alpha = 0.07f)), Zone(-10, Palette.fair.copy(alpha = 0.06f)), Zone(0, Palette.good.copy(alpha = 0.05f)))
    val zonesSinr = listOf(Zone(0, Palette.poor.copy(alpha = 0.07f)), Zone(13, Palette.fair.copy(alpha = 0.06f)), Zone(40, Palette.good.copy(alpha = 0.05f)))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (s.sims.size >= 2) 88.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.sims.size >= 2) item {
            Column {
                standbyDdsLabel(s)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sim_standby), fontSize = 12.sp, color = Palette.textDim)
                }
            }
        }
        item {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tab_signal), style = MaterialTheme.typography.headlineMedium,
                        color = Palette.text, modifier = Modifier.weight(1f))
                    dev.satotek.cellscope.ui.components.CellFreshness(s.serving, s.sim?.isDds == false, compact = true)
                }
                Text(listOfNotNull(s.sim?.label, s.serving?.rat?.label, s.serving?.bandLabel,
                    s.serving?.pci?.let { "PCI $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall, color = Palette.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(nrStatusLabel(s.network.nrState), style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
                Spacer(Modifier.height(10.dp))
                // Expressive connected button group: the selected segment morphs into a pill.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ChartWindows.minutes.forEachIndexed { i, m ->
                        ToggleButton(
                            checked = minutes == m, onCheckedChange = { minutes = m }, modifier = Modifier.weight(1f),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                ChartWindows.minutes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text(stringResource(R.string.signal_minutes, m), fontSize = 12.sp) }
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
                ), -140, -60, 20, zonesRsrp, windowMs, heightDp = 140, cursorT = selectedT, onTapSample = { selectedMetric = "RSRP"; onTap(it) })
            }
        }
        item {
            ChartPanel("RSRQ", "dB", last?.rsrq, Palette.rsrq(last?.rsrq), hasNr, last?.nrRsrq, servingColor = servingColor) {
                SignalChart(h, s.events, listOfNotNull(
                    Series("serving", servingColor) { it.rsrq },
                    if (hasNr) Series("NR SCC", Palette.nr) { it.nrRsrq } else null,
                ), -25, 0, 5, zonesRsrq, windowMs, heightDp = 120, cursorT = selectedT, onTapSample = { selectedMetric = "RSRQ"; onTap(it) })
            }
        }
        item {
            ChartPanel("SINR", "dB", last?.sinr, Palette.sinr(last?.sinr), hasNr, last?.nrSinr, servingColor = servingColor) {
                SignalChart(h, s.events, listOfNotNull(
                    Series("serving", servingColor) { it.sinr },
                    if (hasNr) Series("NR SCC", Palette.nr) { it.nrSinr } else null,
                ), -10, 30, 10, zonesSinr, windowMs, heightDp = 120, cursorT = selectedT, onTapSample = { selectedMetric = "SINR"; onTap(it) })
            }
        }
        item {
            // Neighbour time-series: the 6 cells seen most often in the window, keyed by band/PCI.
            val counts = HashMap<String, Int>()
            vis.forEach { smp -> smp.neighbours.forEach { nb -> if (nb.rsrp != null) counts[nb.key] = (counts[nb.key] ?: 0) + 1 } }
            val keys = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(6).map { it.key }
            val palette = listOf(Palette.wcdma, Color(0xFFB39DFF), Color(0xFFFF8AD8), Color(0xFF6EE7F0), Color(0xFFFFA36E), Color(0xFFC7F464))
            // Identity-derived colours remain stable when the window's ranking changes.
            fun neighbourColor(key: String) = palette[Math.floorMod(key.hashCode(), palette.size)]
            val neighbours = vis.flatMap { it.neighbours }.associateBy { it.key }
            Panel {
                Text(stringResource(R.string.signal_neighbours), style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                    Legend(servingColor, stringResource(R.string.signal_serving))
                    keys.forEach { k -> Legend(neighbourColor(k), neighbours[k]?.let { "${it.band} · PCI ${it.pci ?: "—"}" } ?: k) }
                }
                if (keys.isEmpty()) Text(stringResource(R.string.signal_neighbours_empty),
                    style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
                Spacer(Modifier.height(6.dp))
                SignalChart(h, s.events,
                    listOf(Series("serving", servingColor) { it.rsrp }) + keys.map { k ->
                        Series(k, neighbourColor(k)) { smp -> smp.neighbours.firstOrNull { it.key == k }?.rsrp }
                    }, -140, -60, 20, zonesRsrp, windowMs, heightDp = 150, pillsFor = 1, cursorT = selectedT, onTapSample = onTap)
                
            }
        }
        // Cell changes: newest first, 5 rows by default so the tab doesn't grow without bound; expand on demand.
        val shown = if (eventsExpanded) s.events.asReversed().take(50) else s.events.asReversed().take(5)
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.signal_changes, s.events.size), style = MaterialTheme.typography.labelSmall, color = Palette.textDim, modifier = Modifier.weight(1f))
                if (s.events.size > 5) TextButton(onClick = { eventsExpanded = !eventsExpanded }, contentPadding = ButtonDefaults.ExtraSmallContentPadding) {
                    Text(if (eventsExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_all))
                }
            }
        }
        if (s.events.isEmpty()) item { Text("—", fontFamily = Mono, color = Palette.textDim) }
        items(shown, key = { "${it.t}:${it.to.rat}:${it.to.band}:${it.to.pci}" }) { event ->
            TextButton(onClick = { selectedT = event.t }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                EventRow(event)
            }
        }
    }
    selectedT?.let { t -> if (h.isNotEmpty()) SampleSheet(h, s.events, t, onDismiss = { selectedT = null }, initialMetric = selectedMetric, live = true, liveEdgeMs = windowMs / 30) }
}

