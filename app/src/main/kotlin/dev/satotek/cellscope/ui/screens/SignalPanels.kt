package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
                else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Legend(servingColor, "serving"); if (hasNr) Legend(Palette.nr, "NR SCC") }
            }
            if (subtitle == null) {
                if (hasNr) { Metric("NR", fmt(nrNow), unit, Palette.nr); Spacer(Modifier.width(16.dp)) }
                Text(fmt(now), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = color)
                Text(" $unit", style = MaterialTheme.typography.bodySmall, color = Palette.textDim, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        chart()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleSheet(samples: List<SignalSample>, events: List<CellEvent>, initialT: Long, onDismiss: () -> Unit) {
    var idx by remember { mutableIntStateOf(samples.indexOfFirst { it.t >= initialT }.takeIf { it >= 0 } ?: samples.lastIndex) }
    val smp = samples.getOrNull(idx.coerceIn(0, samples.lastIndex)) ?: return
    val fmtT = SimpleDateFormat("HH:mm:ss", Locale.US)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtT.format(Date(smp.t)), style = MaterialTheme.typography.headlineMedium, color = Palette.text)
                Spacer(Modifier.width(12.dp))
                RatChip(smp.rat, smp.rat.name)
                Spacer(Modifier.width(8.dp))
                Text("${smp.band} · PCI ${fmt(smp.pci)}", fontFamily = Mono, fontSize = 14.sp, color = Palette.rat(smp.rat))
                Spacer(Modifier.weight(1f))
                if (smp.ccCount > 1) Tag(ccShort(smp.ccLte, smp.ccNr), Palette.lte)
            }
            Slider(value = idx.toFloat(), onValueChange = { idx = it.toInt() }, valueRange = 0f..samples.lastIndex.toFloat().coerceAtLeast(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtT.format(Date(samples.first().t)), fontFamily = Mono, fontSize = 10.sp, color = Palette.textDim)
                Text("${idx + 1} / ${samples.size}", fontFamily = Mono, fontSize = 10.sp, color = Palette.textDim)
                Text(fmtT.format(Date(samples.last().t)), fontFamily = Mono, fontSize = 10.sp, color = Palette.textDim)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
                Metric("CC", ccShort(smp.ccLte, smp.ccNr)); Metric("Neighbours", smp.neighbours.size.toString())
            }
            val near = events.filter { kotlin.math.abs(it.t - smp.t) <= 30_000 }
            if (near.isNotEmpty()) {
                Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Palette.outline); Spacer(Modifier.height(6.dp))
                Text("CELL CHANGES ±30s", style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                near.forEach { EventRow(it) }
            }
            if (smp.neighbours.isNotEmpty()) {
                Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Palette.outline); Spacer(Modifier.height(6.dp))
                Text("NEIGHBOURS", style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
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
