package dev.satotek.cellscope.ui.screens

import dev.satotek.cellscope.ui.components.nrStatusLabel
import dev.satotek.cellscope.ui.components.SimSummaries
import dev.satotek.cellscope.ui.components.MeasurementGrid
import dev.satotek.cellscope.ui.components.MeasurementValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.satotek.cellscope.ui.components.CellFreshness
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
import dev.satotek.cellscope.ui.components.LevelBar
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
    onSelectSub: (Int) -> Unit = {},
    onEnterPip: () -> Unit = {},
    onTakeSnapshot: () -> Unit = {},
    recording: Boolean = false,
    onToggleRecording: () -> Unit = {},
) {
    val serving = s.serving
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (s.sims.size >= 2) 88.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") { Header(s, capturing, onEnterPip, onTakeSnapshot, recording, onToggleRecording) }
        if (s.sims.size >= 2) item(key = "sim_summaries") { SimSummaries(s, onSelectSub) }
        item(key = "serving") { ServingCard(serving, s) }
        val cc = s.root.physicalChannels
        // With nothing to list, the "CA" field under network details already says enough.
        if (cc.isNotEmpty() || s.secondaries.isNotEmpty()) item(key = "ca") {
            if (cc.isNotEmpty()) CarrierAggregationCard(cc, s.privilege) else SecondaryCard(s.secondaries)
        }
        item(key = "network") { NetworkDetails(s) }
        item(key = "cells_link") {
            TextButton(onClick = onShowAllCells, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_cells_link, s.cells.size, s.neighbours.size))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(s: Snapshot, capturing: Boolean, onEnterPip: () -> Unit, onTakeSnapshot: () -> Unit, recording: Boolean, onToggleRecording: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.network.operatorName, style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold, color = Palette.text)
                Text(listOfNotNull(s.sim?.label ?: s.network.simOperator,
                    if (s.sim?.isDds == true) stringResource(R.string.sim_data_badge) else null,
                    s.network.plmn.takeIf { it != "—" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.textDim)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onTakeSnapshot, enabled = !capturing, modifier = Modifier.size(48.dp)) {
                if (capturing) LoadingIndicator(Modifier.size(24.dp))
                else Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.snapshot_take))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.dashboard_actions), tint = Palette.textDim)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(if (recording) R.string.dashboard_stop_recording else R.string.dashboard_start_recording)) },
                        onClick = { menuOpen = false; onToggleRecording() },
                        leadingIcon = { Icon(if (recording) Icons.Filled.FiberManualRecord else Icons.Outlined.FiberManualRecord, null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.pip_enter)) },
                        onClick = { menuOpen = false; onEnterPip() },
                        leadingIcon = { Icon(Icons.Outlined.PictureInPictureAlt, null) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dashboard_updated,
                if (s.lastUpdate > 0) SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date(s.lastUpdate)) else "—"),
                style = MaterialTheme.typography.bodySmall, fontFamily = Mono, color = Palette.textDim,
                modifier = Modifier.weight(1f))
            if (recording) Tag(stringResource(R.string.csv_recording), Palette.poor)
        }
    }
}

@Composable
private fun ServingCard(c: CellEntry?, s: Snapshot) {
    val rat = c?.rat ?: Rat.UNKNOWN
    Panel(accent = Palette.rat(rat), title = stringResource(R.string.dashboard_serving),
        titleTrailing = { CellFreshness(c, standby = s.sim?.isDds == false, compact = true) },
    ) {
        if (c == null) {
            CellFreshness(null, standby = s.sim?.isDds == false)
            return@Panel
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatChip(rat)
            Spacer(Modifier.width(8.dp))
            val override = s.network.overrideNetworkType
            if (override != "NONE") Tag(override.replace("_", " "), Palette.accent)
            Spacer(Modifier.weight(1f))
            Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium, color = Palette.rat(rat))
        }
        Spacer(Modifier.height(16.dp))
        MeasurementGrid(listOf(
            MeasurementValue("RSRP", fmt(c.rsrp), "dBm", Palette.rsrp(c.rsrp), prominent = true),
            MeasurementValue("RSRQ", fmt(c.rsrq), "dB", Palette.rsrq(c.rsrq)),
            MeasurementValue(if (rat == Rat.LTE) "RSSNR" else "SINR", fmt(c.sinr), "dB", Palette.sinr(c.sinr)),
        ), preferredColumns = 3)
        Spacer(Modifier.height(8.dp))
        LevelBar(c.rsrp, -140, -44, Palette.rsrp(c.rsrp), Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        MeasurementGrid(listOf(
            MeasurementValue("PCI", fmt(c.pci)),
            MeasurementValue(if (rat == Rat.NR) "NR-ARFCN" else if (rat == Rat.LTE) "EARFCN" else "ARFCN", fmt(c.arfcn)),
            MeasurementValue(stringResource(R.string.dashboard_frequency), fmtMhz(c.dlMhz), "MHz"),
            (c.bandwidthKhz ?: s.network.cellBandwidthsKhz.firstOrNull()).let { bw ->
                MeasurementValue(stringResource(R.string.dashboard_bandwidth), fmtBw(bw).removeSuffix(" MHz"), bw?.let { "MHz" })
            },
        ))
        Spacer(Modifier.height(12.dp))
        MeasurementGrid(listOf(
            MeasurementValue(if (rat == Rat.NR) "NCI" else if (rat == Rat.LTE) "ECI" else "CID", fmt(c.cellId)),
            MeasurementValue(if (rat == Rat.NR) "gNB" else "eNB", fmt(c.gnbOrEnb)),
            MeasurementValue(stringResource(R.string.dashboard_sector), fmt(c.sectorId)),
            MeasurementValue("TAC", fmt(c.tac)),
        ), preferredColumns = if (rat == Rat.NR) 2 else 4)
        Spacer(Modifier.height(12.dp))
        MeasurementGrid(listOf(
            MeasurementValue("TA", fmt(c.ta), if (rat == Rat.NR) "µs" else null),
            MeasurementValue(stringResource(R.string.dashboard_distance), c.taDistanceMeters?.let { if (it >= 1000) String.format(Locale.US, "%.2f", it / 1000) else it.toInt().toString() } ?: "—",
                c.taDistanceMeters?.let { if (it >= 1000) "km" else "m" }),
            MeasurementValue("RSSI", fmt(c.rssi), "dBm"),
            MeasurementValue("CQI / ASU", "${fmt(c.cqi)} / ${fmt(c.asuLevel)}"),
        ))
        if (rat == Rat.NR && (c.csiRsrp != null || c.csiSinr != null)) {
            Spacer(Modifier.height(12.dp))
            MeasurementGrid(listOf(MeasurementValue("CSI-RSRP", fmt(c.csiRsrp), "dBm"),
                MeasurementValue("CSI-RSRQ", fmt(c.csiRsrq), "dB"), MeasurementValue("CSI-SINR", fmt(c.csiSinr), "dB")), preferredColumns = 3)
        }
    }
}

@Composable
private fun NetworkDetails(s: Snapshot) {
    val n = s.network
    val yes = stringResource(R.string.dashboard_available)
    val no = stringResource(R.string.dashboard_unavailable)
    val on = stringResource(R.string.dashboard_on)
    val off = stringResource(R.string.dashboard_off)
    val connected = stringResource(R.string.dashboard_connected)
    val disconnected = stringResource(R.string.dashboard_disconnected)
    fun available(value: Boolean?) = value?.let { if (it) yes else no } ?: "—"
    fun enabled(value: Boolean?) = value?.let { if (it) on else off } ?: "—"
    Panel(title = stringResource(R.string.home_network_details)) {
        MeasurementGrid(listOf(
            MeasurementValue(stringResource(R.string.dashboard_data_rat), n.dataNetworkType),
            MeasurementValue("NR", nrStatusLabel(n.nrState, short = true), color = if (n.nrState == "CONNECTED" || n.nrState == "SA") Palette.nr else Palette.text),
            MeasurementValue("EN-DC", available(n.isEnDcAvailable)),
            MeasurementValue("CA", enabled(n.usingCarrierAggregation)),
            MeasurementValue(stringResource(R.string.dashboard_service), when(n.serviceState) {
                "IN_SERVICE" -> stringResource(R.string.dashboard_in_service)
                "OUT_OF_SERVICE" -> stringResource(R.string.dashboard_out_of_service)
                "POWER_OFF" -> off
                "EMERGENCY_ONLY" -> stringResource(R.string.dashboard_emergency)
                else -> n.serviceState
            }),
            MeasurementValue(stringResource(R.string.dashboard_data_state), when(n.dataState) {
                "CONNECTED" -> connected; "DISCONNECTED" -> disconnected
                "CONNECTING" -> stringResource(R.string.dashboard_connecting); else -> n.dataState
            }),
            MeasurementValue("VoPS", available(n.vopsSupported)),
            // "15+15+15" over a shared unit stays on one line where "15 MHz + 15 MHz + …" took three.
            MeasurementValue(stringResource(R.string.dashboard_bandwidth),
                n.cellBandwidthsKhz.takeIf { it.isNotEmpty() }?.joinToString("+") { fmtBw(it).removeSuffix(" MHz") } ?: "—",
                unit = if (n.cellBandwidthsKhz.isNotEmpty()) "MHz" else null),
        ) + listOfNotNull(
            // Root and priv-app fill these; without them each would be a permanent "—".
            n.imsRegistered?.let { MeasurementValue("IMS", stringResource(if (it) R.string.dashboard_registered else R.string.dashboard_unregistered)) },
            n.imsTransport?.let { MeasurementValue(stringResource(R.string.dashboard_ims_transport), it) },
            n.voNrEnabled?.let { MeasurementValue("VoNR", enabled(it)) },
            n.nrAnchorPci?.let { MeasurementValue(stringResource(R.string.dashboard_nr_anchor), it.toString()) },
        ))
    }
}

@Composable
fun CarrierAggregationCard(cc: List<CarrierComponent>, priv: PrivilegeLevel) {
    val total = cc.sumOf { it.dlKhz ?: 0 }
    val (nLte, nNr) = ccSplit(cc)
    Panel(title = "${stringResource(R.string.ca_title)} · ${ccLong(nLte, nNr)} · ${fmtBw(total)}", accent = Palette.lte) {
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
private fun SecondaryCard(cells: List<CellEntry>) {
    Panel(title = stringResource(R.string.dashboard_secondary), accent = Palette.lte) {
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
