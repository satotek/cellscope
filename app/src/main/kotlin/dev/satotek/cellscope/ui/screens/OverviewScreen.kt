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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.CarrierComponent
import dev.satotek.cellscope.data.model.ccLong
import dev.satotek.cellscope.data.model.ccSplit
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.PrivilegeLevel
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.speed.SpeedPhase
import dev.satotek.cellscope.data.speed.SpeedProgress
import dev.satotek.cellscope.data.speed.SpeedResult
import dev.satotek.cellscope.data.telephony.BandTables
import dev.satotek.cellscope.ui.components.ArcGauge
import dev.satotek.cellscope.ui.components.LevelBar
import dev.satotek.cellscope.ui.components.Metric
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.RatChip
import dev.satotek.cellscope.ui.components.Tag
import dev.satotek.cellscope.ui.components.fmt
import dev.satotek.cellscope.ui.components.fmtBw
import dev.satotek.cellscope.ui.components.fmtMhz
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewScreen(
    s: Snapshot,
    capturing: Boolean = false,
    onShowAllCells: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    onTakeSnapshot: () -> Unit = {},
    recording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    speedProgress: SpeedProgress? = null,
    lastSpeed: SpeedResult? = null,
    onStartSpeedTest: () -> Unit = {},
    onCancelSpeedTest: () -> Unit = {},
) {
    val serving = s.serving
    val n = s.network
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header(s, capturing, onEnterPip, onTakeSnapshot, recording, onToggleRecording) }
        item { ServingCard(serving, s) }
        item { NetworkCard(s, speedProgress, lastSpeed, onStartSpeedTest, onCancelSpeedTest) }
        val cc = s.root.physicalChannels
        if (cc.isNotEmpty()) item { CarrierAggregationCard(cc, s.privilege) }
        else if (s.secondaries.isNotEmpty()) item { SecondaryCard(s.secondaries) }
        if (s.root.dataCalls.isNotEmpty()) item { DataCallsCard(s.root.dataCalls) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("NEIGHBOURS · ${s.neighbours.size}", style = MaterialTheme.typography.labelSmall, color = Palette.textDim, modifier = Modifier.weight(1f))
                if (s.neighbours.size > 8) TextButton(onClick = onShowAllCells, contentPadding = ButtonDefaults.ExtraSmallContentPadding) { Text(stringResource(R.string.show_all)) }
            }
        }
        items(s.neighbours.take(8)) { NeighbourRow(it) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(s: Snapshot, capturing: Boolean, onEnterPip: () -> Unit, onTakeSnapshot: () -> Unit, recording: Boolean, onToggleRecording: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        if (s.root.available == null) { LoadingIndicator(Modifier.padding(end = 10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(s.network.operatorName, style = MaterialTheme.typography.headlineMedium, color = Palette.text)
            Text("${s.network.plmn} · SIM ${s.network.simOperator}", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        }
        // CSV recording toggle: red dot while a log is being written.
        IconButton(onClick = onToggleRecording) {
            Icon(
                if (recording) Icons.Filled.FiberManualRecord else Icons.Outlined.FiberManualRecord,
                stringResource(R.string.csv_log),
                tint = if (recording) Palette.poor else Palette.textDim,
            )
        }
        IconButton(onClick = onEnterPip) {
            Icon(Icons.Outlined.PictureInPictureAlt, stringResource(R.string.pip_enter), tint = Palette.textDim)
        }
        if (capturing) {
            LoadingIndicator(Modifier.padding(12.dp))
        } else {
            IconButton(onClick = onTakeSnapshot) {
                Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.snapshot_take), tint = Palette.textDim)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Tag(stringResource(s.privilege.labelRes), when (s.privilege) { PrivilegeLevel.PRIV_APP -> Palette.nr; PrivilegeLevel.ROOT -> Palette.lte; else -> Palette.textDim })
            Spacer(Modifier.height(4.dp))
            Text(if (s.lastUpdate > 0) SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(s.lastUpdate)) + " #" + s.updateCount else "—",
                fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
        }
    }
}

@Composable
private fun ServingCard(c: CellEntry?, s: Snapshot) {
    val rat = c?.rat ?: Rat.UNKNOWN
    Panel(accent = Palette.rat(rat), title = "Serving cell") {
        if (c == null) { Text("—", fontFamily = Mono, color = Palette.textDim); return@Panel }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatChip(rat)
            Spacer(Modifier.width(8.dp))
            val ov = s.network.overrideNetworkType
            if (ov != "NONE") Tag(ov, Palette.rat(if (ov.startsWith("NR")) Rat.NR else Rat.LTE))
            Spacer(Modifier.weight(1f))
            Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Palette.rat(rat))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ArcGauge(c.rsrp, -140, -44, "RSRP", "dBm", Palette.rsrp(c.rsrp), sizeDp = 168)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("RSRQ", fmt(c.rsrq), "dB", Palette.rsrq(c.rsrq)); LevelBar(c.rsrq, -20, -3, Palette.rsrq(c.rsrq), Modifier.fillMaxWidth())
                Metric(if (rat == Rat.LTE) "RSSNR" else "SINR", fmt(c.sinr), "dB", Palette.sinr(c.sinr)); LevelBar(c.sinr, -10, 30, Palette.sinr(c.sinr), Modifier.fillMaxWidth())
                Metric("CQI", fmt(c.cqi), null, Palette.text)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("PCI", fmt(c.pci), color = Palette.text)
            Metric(if (rat == Rat.NR) "NR-ARFCN" else if (rat == Rat.LTE) "EARFCN" else "ARFCN", fmt(c.arfcn))
            Metric("DL", fmtMhz(c.dlMhz), "MHz")
            Metric("BW", fmtBw(c.bandwidthKhz ?: s.network.cellBandwidthsKhz.firstOrNull()))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(if (rat == Rat.NR) "NCI" else if (rat == Rat.LTE) "ECI" else "CID", fmt(c.cellId))
            Metric(if (rat == Rat.NR) "gNB" else "eNB", fmt(c.gnbOrEnb))
            Metric("Sector", fmt(c.sectorId))
            Metric("TAC", fmt(c.tac))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("TA", fmt(c.ta), if (rat == Rat.NR) "µs" else null)
            Metric("Distance", c.taDistanceMeters?.let { if (it >= 1000) String.format("%.2f", it / 1000) else it.toInt().toString() } ?: "—", c.taDistanceMeters?.let { if (it >= 1000) "km" else "m" })
            Metric("RSSI", fmt(c.rssi), "dBm")
            Metric("ASU", fmt(c.asuLevel))
        }
        if (rat == Rat.NR && (c.csiRsrp != null || c.csiSinr != null)) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("CSI-RSRP", fmt(c.csiRsrp), "dBm"); Metric("CSI-RSRQ", fmt(c.csiRsrq), "dB"); Metric("CSI-SINR", fmt(c.csiSinr), "dB")
                Metric("Range", BandTables.nrRangeLabel(c.dlMhz))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NetworkCard(
    s: Snapshot,
    speedProgress: SpeedProgress?,
    lastSpeed: SpeedResult?,
    onStartSpeedTest: () -> Unit,
    onCancelSpeedTest: () -> Unit,
) {
    val n = s.network
    Panel(title = "Network") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Data", n.dataNetworkType, color = Palette.text)
            Metric("Override", n.overrideNetworkType)
            Metric("NR state", n.nrState, color = if (n.nrState.startsWith("CONNECTED") || n.nrState == "NR_ADV" || n.nrState == "SA") Palette.nr else Palette.text)
            Metric("NR anchor", n.nrAnchorPci?.let { "PCI $it" } ?: "—")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("EN-DC", n.isEnDcAvailable?.let { if (it) "AVAIL" else "no" } ?: "—", color = if (n.isEnDcAvailable == true) Palette.nr else Palette.text)
            Metric("NR avail", n.isNrAvailable?.let { if (it) "yes" else "no" } ?: "—")
            Metric("DCNR restr", n.isDcNrRestricted?.let { if (it) "YES" else "no" } ?: "—", color = if (n.isDcNrRestricted == true) Palette.poor else Palette.text)
            Metric("CA", n.usingCarrierAggregation?.let { if (it) "ON" else "off" } ?: "—", color = if (n.usingCarrierAggregation == true) Palette.lte else Palette.text)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Service", n.serviceState, color = if (n.serviceState == "IN_SERVICE") Palette.good else Palette.poor)
            Metric("Data", n.dataState)
            val last = s.history.lastOrNull()
            Metric("▼ Mbps", last?.rxBps?.let { String.format(java.util.Locale.US, "%.1f", it / 1e6) } ?: "—", color = Palette.nr)
            Metric("▲ Mbps", last?.txBps?.let { String.format(java.util.Locale.US, "%.1f", it / 1e6) } ?: "—", color = Palette.wcdma)
            val rtt = last?.rttMs
            Metric("RTT", when { rtt == null -> "—"; rtt < 0 -> "LOST"; else -> rtt.toInt().toString() }, "ms",
                when { rtt == null -> Palette.text; rtt < 0 -> Palette.poor; rtt < 50 -> Palette.good; rtt < 120 -> Palette.fair; else -> Palette.poor })
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("IMS", n.imsRegistered?.let { if (it) "REG" else "no" } ?: "—", color = if (n.imsRegistered == true) Palette.good else Palette.text)
            Metric("IMS via", n.imsTransport ?: "—")
            Metric("VoNR", n.voNrEnabled?.let { if (it) "on" else "off" } ?: "—")
            Metric("VoPS", n.vopsSupported?.let { if (it) "yes" else "no" } ?: "—")
        }
        if (n.cellBandwidthsKhz.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Cell bandwidths: " + n.cellBandwidthsKhz.joinToString(" + ") { fmtBw(it) }, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        }
        Spacer(Modifier.height(12.dp))
        SpeedBlock(speedProgress, lastSpeed, onStartSpeedTest, onCancelSpeedTest)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedBlock(
    progress: SpeedProgress?,
    last: SpeedResult?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    val running = progress != null && progress.phase != SpeedPhase.DONE && progress.phase != SpeedPhase.ERROR
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
            text = { Text(stringResource(R.string.speed_confirm_body)) },
            confirmButton = { Button(onClick = { confirm = false; onStart() }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.speed_test)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun fmtSpeed(mbps: Double): String =
    if (mbps >= 10) String.format(Locale.US, "%.1f", mbps) else String.format(Locale.US, "%.1f", mbps)

@Composable
fun CarrierAggregationCard(cc: List<CarrierComponent>, priv: PrivilegeLevel) {
    val total = cc.sumOf { it.dlKhz ?: 0 }
    val (nLte, nNr) = ccSplit(cc)
    Panel(title = "Carrier aggregation · ${ccLong(nLte, nNr)} · ${fmtBw(total)}", accent = Palette.lte) {
        cc.sortedByDescending { it.isPrimary }.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(if (c.isPrimary) "PCC" else "SCC$i", if (c.isPrimary) Palette.nr else Palette.lte)
                Spacer(Modifier.width(8.dp))
                RatChip(c.rat, c.rat.name, filled = false)
                Spacer(Modifier.width(8.dp))
                Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.rat(c.rat))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${fmtBw(c.dlKhz)}↓ ${c.ulKhz?.let { fmtBw(it) + "↑" } ?: ""}", fontFamily = Mono, fontSize = 12.sp, color = Palette.text)
                    Text("PCI ${fmt(c.pci)} · ARFCN ${fmt(c.dlArfcn)} · ${c.dlFreqKhz?.let { "${it / 1000} MHz" } ?: c.frequencyRange ?: ""}", fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
                }
            }
        }
    }
}

@Composable
private fun DataCallsCard(calls: List<dev.satotek.cellscope.data.model.DataCall>) {
    Panel(title = "Data calls · ${calls.size} PDN", accent = Palette.wcdma) {
        calls.forEachIndexed { i, d ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.apn.ifBlank { d.apnName }, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.text)
                Spacer(Modifier.width(8.dp))
                Tag(d.types.replace(" ", ""), Palette.textDim)
                Spacer(Modifier.weight(1f))
                Tag(d.state, if (d.state == "CONNECTED") Palette.nr else Palette.poor)
                Spacer(Modifier.width(6.dp))
                Tag("${d.networkType}/${d.transport}", Palette.lte)
            }
            Spacer(Modifier.height(4.dp))
            val addrs = d.addresses.joinToString("  ")
            Text("${d.iface ?: "?"} · MTU ${d.mtu ?: "—"}${d.qos?.let { " · $it" } ?: ""}", fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
            if (addrs.isNotBlank()) Text(addrs, fontFamily = Mono, fontSize = 11.sp, color = Palette.text)
            if (d.dns.isNotEmpty()) Text("DNS " + d.dns.joinToString(" "), fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
            if (d.pcscf.isNotEmpty()) Text("P-CSCF " + d.pcscf.joinToString(" "), fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
            d.failCause?.let { Text("fail: $it", fontFamily = Mono, fontSize = 11.sp, color = Palette.poor) }
        }
    }
}

@Composable
private fun SecondaryCard(cells: List<CellEntry>) {
    Panel(title = "Secondary serving (CellInfo)", accent = Palette.lte) {
        cells.forEach { NeighbourRow(it) }
    }
}

@Composable
fun NeighbourRow(c: CellEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RatChip(c.rat, c.rat.name, filled = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold, color = Palette.rat(c.rat))
                Spacer(Modifier.width(8.dp))
                Text("PCI ${fmt(c.pci)}", fontFamily = Mono, fontSize = 13.sp, color = Palette.text)
                Spacer(Modifier.width(8.dp))
                Text("${fmt(c.arfcn)} · ${fmtMhz(c.dlMhz)} MHz", fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim)
            }
            Spacer(Modifier.height(4.dp))
            LevelBar(c.rsrp, -140, -44, Palette.rsrp(c.rsrp), Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt(c.rsrp), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Palette.rsrp(c.rsrp))
            Text("${fmt(c.rsrq)} / ${fmt(c.sinr)}", fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
        }
    }
}
