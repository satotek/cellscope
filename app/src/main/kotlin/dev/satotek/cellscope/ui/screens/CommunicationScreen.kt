package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
import dev.satotek.cellscope.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.speed.SpeedPhase
import dev.satotek.cellscope.data.speed.SpeedProgress
import dev.satotek.cellscope.data.speed.SpeedResult
import dev.satotek.cellscope.ui.components.ChartWindows
import dev.satotek.cellscope.ui.components.Metric
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.standbyDdsLabel
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satotek.cellscope.MainViewModel
import dev.satotek.cellscope.data.model.PingStats
import dev.satotek.cellscope.ui.components.SignalChart
import dev.satotek.cellscope.ui.components.Series
import dev.satotek.cellscope.ui.components.Zone

@Composable
fun CommunicationScreen(vm: MainViewModel, s: Snapshot) {
    val progress by vm.speedState.collectAsStateWithLifecycle()
    val results by vm.speedResults.collectAsStateWithLifecycle()
    var history by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(ChartWindows.DEFAULT_MINUTES) }
    androidx.activity.compose.BackHandler(history) { history = false }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_communication), style = MaterialTheme.typography.headlineMedium,
                color = Palette.text, modifier = Modifier.weight(1f))
            TextButton(onClick = { history = !history }) {
                Text(stringResource(if (history) R.string.communication_live else R.string.communication_history))
            }
        }
        Box(Modifier.weight(1f)) {
            if (history) SpeedHistoryPane(vm, results)
            else {
                val h = s.history
                val windowMs = minutes * 60_000L
                val vis = h.filter { it.t >= (h.lastOrNull()?.t ?: 0L) - windowMs }
                val last = h.lastOrNull()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        val dds = s.sims.firstOrNull { it.isDds }
                        Text(stringResource(R.string.communication_source, dds?.label ?: "—"),
                            style = MaterialTheme.typography.bodyMedium, color = Palette.accent)
                    }
                    item { SpeedTestCard(s, progress, results.firstOrNull(), vm::startSpeedTest, vm::cancelSpeedTest) }
                    item { LiveCard(s) }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ChartWindows.minutes.forEach { m ->
                                androidx.compose.material3.FilterChip(selected = minutes == m,
                                    onClick = { minutes = m }, label = { Text(stringResource(R.string.signal_minutes, m)) })
                            }
                        }
                    }
        item {
            val maxMbps = (vis.maxOfOrNull { maxOf(it.rxBps ?: 0, it.txBps ?: 0) } ?: 0L) / 1_000_000.0
            val top = listOf(5, 10, 20, 50, 100, 200, 500, 1000, 2000).firstOrNull { it > maxMbps } ?: 4000
            ChartPanel("Throughput", "Mbps", last?.rxBps?.let { (it / 1_000_000).toInt() }, Palette.text, false, null, subtitle = "▼ ${fmtMbps(last?.rxBps)}  ▲ ${fmtMbps(last?.txBps)} Mbps") {
                SignalChart(h, s.events, listOf(
                    Series("rx", Palette.nr) { it.rxBps?.let { b -> (b / 100_000).toInt() } },
                    Series("tx", Palette.wcdma) { it.txBps?.let { b -> (b / 100_000).toInt() } },
                ), 0, top * 10, top * 10 / 5, emptyList(), windowMs, heightDp = 140, yFormat = { (it / 10).toString() })
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
                    0, top, top / 5, listOf(Zone(50, Palette.good.copy(alpha = 0.05f)), Zone(120, Palette.fair.copy(alpha = 0.06f)), Zone(top, Palette.poor.copy(alpha = 0.07f))), windowMs, heightDp = 140)
            }
        }
                }
            }
        }
    }
}

// Live traffic and the speed test sit in separate cards: during an upload phase the live ▼ drops
// to ACK traffic, which reads as a broken download result when both share one card.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedTestCard(
    s: Snapshot,
    speedProgress: SpeedProgress?,
    lastSpeed: SpeedResult?,
    onStartSpeedTest: () -> Unit,
    onCancelSpeedTest: () -> Unit,
) {
    Panel(title = stringResource(R.string.speed_test)) {
        Text(stringResource(R.string.speed_scope), style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        Spacer(Modifier.height(12.dp))
        SpeedBlock(
            speedProgress, lastSpeed, onStartSpeedTest, onCancelSpeedTest,
            ddsNote = standbyDdsLabel(s)?.let { stringResource(R.string.sim_speed_on_dds, it) },
        )
    }
}

@Composable
private fun LiveCard(s: Snapshot) {
    Panel(title = stringResource(R.string.communication_live)) {
        Text(stringResource(R.string.communication_scope), style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        standbyDdsLabel(s)?.let { dds ->
            Text(stringResource(R.string.sim_metrics_on_dds, dds), fontSize = 12.sp, color = Palette.accent)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val last = s.history.lastOrNull()
            Metric("▼ Mbps", last?.rxBps?.let { String.format(java.util.Locale.US, "%.1f", it / 1e6) } ?: "—", color = Palette.nr)
            Metric("▲ Mbps", last?.txBps?.let { String.format(java.util.Locale.US, "%.1f", it / 1e6) } ?: "—", color = Palette.wcdma)
            val rtt = last?.rttMs
            Metric("RTT", when { rtt == null -> "—"; rtt < 0 -> "LOST"; else -> rtt.toInt().toString() }, "ms",
                when { rtt == null -> Palette.text; rtt < 0 -> Palette.poor; rtt < 50 -> Palette.good; rtt < 120 -> Palette.fair; else -> Palette.poor })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedBlock(
    progress: SpeedProgress?,
    last: SpeedResult?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    ddsNote: String? = null,
) {
    var confirm by remember { mutableStateOf(false) }
    val running = progress != null && progress.phase != SpeedPhase.DONE && progress.phase != SpeedPhase.ERROR
    if (ddsNote != null) {
        Text(ddsNote, fontSize = 12.sp, color = Palette.accent, modifier = Modifier.padding(bottom = 8.dp))
    }
    if (running && progress != null) {
        val phaseLabel = when (progress.phase) {
            SpeedPhase.LOCATE -> stringResource(R.string.speed_locate)
            SpeedPhase.DOWNLOAD -> stringResource(R.string.speed_download)
            SpeedPhase.UPLOAD -> stringResource(R.string.speed_upload)
            else -> ""
        }
        val shownMbps = if (progress.elapsedMs >= 2_000L) progress.avgMbps ?: progress.mbps else progress.mbps
        Text(
            shownMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
            style = MaterialTheme.typography.displayLarge,
            fontFamily = Mono,
            color = Palette.text,
        )
        Text("Mbps · $phaseLabel", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        Spacer(Modifier.height(8.dp))
        if (progress.phase == SpeedPhase.LOCATE) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        } else {
            val frac = (progress.elapsedMs / 10_000f).coerceIn(0f, 1f)
            LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
        }
        progress.server?.let { Text(it, fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
    } else {
        FilledTonalButton(onClick = { confirm = true }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.ExtraSmallContentPadding) {
            Icon(Icons.Outlined.Speed, null, Modifier.height(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.speed_test))
        }
        if (progress?.phase == SpeedPhase.ERROR) {
            Text(progress.error ?: "—", fontFamily = Mono, fontSize = 12.sp, color = Palette.poor, modifier = Modifier.padding(top = 6.dp))
        }
        val shown = progress?.result ?: last
        shown?.let { r ->
            val tm = SimpleDateFormat("HH:mm", Locale.US).format(Date(r.t))
            Text(
                "▼ ${fmtSpeed(r.dlMbps)} Mbps · ▲ ${fmtSpeed(r.ulMbps)} Mbps · RTT ${r.minRttMs?.let { String.format(Locale.US, "%.0f", it) } ?: "—"} ms · $tm",
                fontFamily = Mono, fontSize = 12.sp, color = Palette.text, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.speed_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.speed_confirm_body) +
                        if (ddsNote != null) "\n\n$ddsNote" else "",
                )
            },
            confirmButton = { Button(onClick = { confirm = false; onStart() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.speed_test)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun fmtSpeed(mbps: Double): String =
    if (mbps >= 10) String.format(Locale.US, "%.1f", mbps) else String.format(Locale.US, "%.1f", mbps)


private fun fmtMs(v: Double?): String = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
private fun fmtMbps(b: Long?): String = b?.let { String.format(Locale.US, "%.1f", it / 1_000_000.0) } ?: "—"
