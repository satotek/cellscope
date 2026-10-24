package dev.satotek.cellscope.ui.screens

import dev.satotek.cellscope.ui.components.ChartWindows
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.ccShort
import dev.satotek.cellscope.ui.components.EventRow
import dev.satotek.cellscope.ui.components.LevelBar
import dev.satotek.cellscope.ui.components.Metric
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.RatChip
import dev.satotek.cellscope.ui.components.Tag
import dev.satotek.cellscope.ui.components.fmt
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import dev.satotek.cellscope.R

private fun fmtMs(v: Double?): String = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
private fun fmtMbps(b: Long?): String = b?.let { String.format(Locale.US, "%.1f", it / 1_000_000.0) } ?: "—"

@Composable
internal fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
    }
}

@Composable
internal fun ChartPanel(
    title: String, unit: String, now: Int?, color: Color, hasNr: Boolean, nrNow: Int?,
    subtitle: String? = null, servingColor: Color = Palette.lte, chart: @Composable () -> Unit,
) {
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                if (subtitle != null) Text(subtitle, fontFamily = Mono, fontSize = 12.sp, color = Palette.text)
                else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Legend(servingColor, stringResource(R.string.signal_serving)); if (hasNr) Legend(Palette.nr, "NR SCC") }
            }
            if (subtitle == null) {
                if (hasNr) { Metric("NR", fmt(nrNow), unit, Palette.nr); Spacer(Modifier.width(16.dp)) }
                Text(fmt(now), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = color)
                Text(" $unit", style = MaterialTheme.typography.bodySmall, color = Palette.textDim, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        chart()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleSheet(samples: List<SignalSample>, events: List<CellEvent>, initialT: Long, onDismiss: () -> Unit,
                initialMetric: String = "RSRP", live: Boolean = false, liveEdgeMs: Long = 0L,
                pinned: Boolean = false) {
    // On the newest sample the sheet follows the live history. Anything older is inspected on a
    // frozen copy: a live window would slide under the finger and could roll the sample out.
    // A tap near the right edge rarely lands on the very last sample; the caller sizes this tolerance
    // to its chart window so it stays about a fingertip wide at any zoom.
    var following by remember(initialT) { mutableStateOf(live && (samples.lastOrNull()?.t ?: 0L) - initialT <= liveEdgeMs) }
    var frozen by remember(initialT) { mutableStateOf(samples.toList() to events.toList()) }
    val inspectedSamples = if (following) samples else frozen.first
    val inspectedEvents = if (following) events else frozen.second
    var pickedT by remember(initialT) { mutableLongStateOf(initialT) }
    val selectedT = if (following) inspectedSamples.lastOrNull()?.t ?: pickedT else pickedT
    val smp = inspectedSamples.minByOrNull { kotlin.math.abs(it.t - selectedT) } ?: return
    var scrubbing by remember { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }
    var metric by remember(initialT) { mutableStateOf(initialMetric) }
    var minutes by remember(initialT) { mutableIntStateOf(ChartWindows.DEFAULT_MINUTES) }
    var anchor by remember(initialT) { mutableLongStateOf(initialT) }
    fun pause() {
        if (!following) return
        frozen = samples.toList() to events.toList()
        pickedT = smp.t; anchor = smp.t; following = false
    }
    fun resumeIfNewest() {
        if (live && !scrubbing && pickedT == frozen.first.lastOrNull()?.t) following = true
    }
    val chart: @Composable (Boolean) -> Unit = { full ->
        SampleGraph(inspectedSamples, inspectedEvents, smp, metric, minutes, if (following) smp.t else anchor, full,
            onMetric = { metric = it }, onMinutes = { minutes = it; anchor = smp.t },
            onSelect = { pause(); pickedT = it.t; resumeIfNewest() },
            onScrubbing = { active ->
                if (active) pause()
                scrubbing = active
                if (!active) resumeIfNewest()
            },
            onToggleFullScreen = { fullScreen = !fullScreen },
            live = if (live) following else null,
            onGoLive = { following = true })
    }
    if (fullScreen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            androidx.compose.material3.Surface(Modifier.fillMaxWidth().fillMaxHeight(), color = Palette.bg) {
                chart(true)
            }
        }
    } else ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // A pinned sheet must not move under the finger. With short details there is nothing for
        // the inner scroll to consume, so an enabled drag would collapse the sheet instead of
        // scrolling the log; the scrim, back, or a tap on the handle still dismiss it.
        sheetGesturesEnabled = !scrubbing && !pinned) {
        // Replay pins the sheet at the top: scrubbing selects samples with different neighbour
        // counts, and a content-sized sheet would move the chart under the finger as it resized.
        // The live sheet stays sized to its content so the chart behind remains in view; 92% is
        // only the cap there for a long neighbour list, which then scrolls on its own.
        val cap = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp
        val frame = if (pinned) Modifier.fillMaxHeight(0.92f) else Modifier.heightIn(max = cap)
        Column(Modifier.fillMaxWidth().then(frame).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            chart(false)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f, fill = pinned).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("RSRP", fmt(smp.rsrp), "dBm", Palette.rsrp(smp.rsrp), big = true)
                Metric("RSRQ", fmt(smp.rsrq), "dB", Palette.rsrq(smp.rsrq), big = true)
                Metric("SINR", fmt(smp.sinr), "dB", Palette.sinr(smp.sinr), big = true)
                Metric("RTT", smp.rttMs?.let { if (it < 0) "LOST" else it.toInt().toString() } ?: "—", "ms", big = true)
            }
            if (smp.nrRsrp != null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("NR SCC", "PCI ${fmt(smp.nrPci)}", color = Palette.nr)
                    Metric("SS-RSRP", fmt(smp.nrRsrp), "dBm", Palette.rsrp(smp.nrRsrp))
                    Metric("SS-RSRQ", fmt(smp.nrRsrq), "dB", Palette.rsrq(smp.nrRsrq))
                    Metric("SS-SINR", fmt(smp.nrSinr), "dB", Palette.sinr(smp.nrSinr))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("▼", fmtMbps(smp.rxBps), "Mbps", Palette.nr); Metric("▲", fmtMbps(smp.txBps), "Mbps", Palette.wcdma)
                Metric("CC", ccShort(smp.ccLte, smp.ccNr)); Metric(stringResource(R.string.signal_neighbours_short), smp.neighbours.size.toString())
            }
            val near = inspectedEvents.filter { kotlin.math.abs(it.t - smp.t) <= 30_000 }
            if (near.isNotEmpty()) {
                Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Palette.outline); Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.signal_changes_near), style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                near.forEach { EventRow(it) }
            }
            if (smp.neighbours.isNotEmpty()) {
                Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Palette.outline); Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.signal_neighbours_short), style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                smp.neighbours.sortedByDescending { it.rsrp ?: -999 }.forEach { nb ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RatChip(nb.rat, nb.rat.name, filled = false); Spacer(Modifier.width(8.dp))
                        Text("${nb.band} PCI ${fmt(nb.pci)}", fontFamily = Mono, fontSize = 13.sp, color = Palette.rat(nb.rat), modifier = Modifier.width(120.dp))
                        LevelBar(nb.rsrp, -140, -44, Palette.rsrp(nb.rsrp), Modifier.weight(1f)); Spacer(Modifier.width(8.dp))
                        Text("${fmt(nb.rsrp)} · ${fmt(nb.rsrq)} · ${fmt(nb.sinr)}", fontFamily = Mono, fontSize = 12.sp, color = Palette.text)
                    }
                }
            }
            }
        }
    }
}
