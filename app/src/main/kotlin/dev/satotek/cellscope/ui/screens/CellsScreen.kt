package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import dev.satotek.cellscope.ui.components.MeasurementGrid
import dev.satotek.cellscope.ui.components.MeasurementValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.satotek.cellscope.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.satotek.cellscope.ui.components.CellFreshness
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.ui.components.KeyValueRow
import dev.satotek.cellscope.ui.components.LevelBar
import dev.satotek.cellscope.ui.components.standbyDdsLabel
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.RatChip
import dev.satotek.cellscope.ui.components.Tag
import dev.satotek.cellscope.ui.components.fmt
import dev.satotek.cellscope.ui.components.fmtBw
import dev.satotek.cellscope.ui.components.fmtMhz
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette

/** Connection role first; measurements never reorder a cell while the user reads it. */
private fun cellKey(c: CellEntry) = "${c.rat}-${c.mcc}-${c.mnc}-${c.cellId}-${c.arfcn}-${c.pci}"

@Composable
fun CellsScreen(s: Snapshot) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val standby = standbyDdsLabel(s) != null
    val secondary = s.secondaries.toSet()
    val visible = s.cells.distinctBy(::cellKey).filter { filter == "ALL" || it.rat.name == filter }
        .sortedWith(compareBy<CellEntry> { it.rat.ordinal }.thenBy { it.arfcn ?: Int.MAX_VALUE }
            .thenBy { it.pci ?: Int.MAX_VALUE }.thenBy { it.cellId ?: Long.MAX_VALUE })
    fun role(c: CellEntry) = when {
        c.isPrimary || (c.registered && c !in secondary) -> 0
        c in secondary -> 1
        else -> 2
    }
    val sections = listOf(R.string.cells_connected, R.string.cells_secondary, R.string.cell_neighbour)
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = if (s.sims.size >= 2) 88.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.tab_cells), style = MaterialTheme.typography.headlineLarge, color = Palette.text)
                Text(listOfNotNull(s.sim?.label, stringResource(R.string.cells_count, visible.size, s.cells.size)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.textDim)
            }
        }
        item(key = "filter") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" },
                    label = { Text(stringResource(R.string.cells_all)) })
                Rat.entries.filter { rat -> s.cells.any { it.rat == rat } || filter == rat.name }.forEach { rat ->
                    FilterChip(selected = filter == rat.name, onClick = { filter = rat.name }, label = { Text(rat.label) })
                }
            }
        }
        if (standby) item(key = "standby") {
            Text(stringResource(R.string.sim_standby), style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        }
        sections.forEachIndexed { index, title ->
            val cells = visible.filter { role(it) == index }
            if (cells.isNotEmpty()) {
                item(key = "section-$index") {
                    Text("${stringResource(title)} · ${cells.size}", style = MaterialTheme.typography.titleSmall,
                        color = Palette.textDim, modifier = Modifier.padding(top = 8.dp))
                }
                items(cells, key = ::cellKey) { c -> CellCard(c, role = index) }
            }
        }
        if (visible.isEmpty()) item(key = "empty") {
            if (s.cells.isEmpty()) CellFreshness(null, standby)
            else Text(stringResource(R.string.cells_filter_empty), color = Palette.textDim)
        }
    }
}

@Composable
private fun CellCard(c: CellEntry, role: Int) {
    var open by rememberSaveable { mutableStateOf(false) }
    val action = stringResource(if (open) R.string.cells_close_details else R.string.cells_open_details)
    Panel(Modifier.clickable(role = Role.Button, onClickLabel = action) { open = !open }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RatChip(c.rat, c.rat.name, filled = role == 0)
                Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge, color = Palette.rat(c.rat))
                if (c.isPrimary) Tag("PCC", Palette.nr)
                else if (role == 1) Tag("SCC", Palette.lte)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(c.rsrp), fontFamily = Mono, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall, color = Palette.rsrp(c.rsrp))
                Text("dBm", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        MeasurementGrid(listOf(
            MeasurementValue(if (c.rat == Rat.WCDMA) "PSC" else if (c.rat == Rat.GSM) "BSIC" else "PCI", fmt(c.pci)),
            MeasurementValue(if (c.rat == Rat.NR) "NR-ARFCN" else if (c.rat == Rat.LTE) "EARFCN" else "ARFCN", fmt(c.arfcn)),
            MeasurementValue("RSRQ", fmt(c.rsrq), "dB", Palette.rsrq(c.rsrq)),
            MeasurementValue(if (c.rat == Rat.LTE) "RSSNR" else "SINR", fmt(c.sinr), "dB", Palette.sinr(c.sinr)),
        ))
        Spacer(Modifier.height(8.dp))
        LevelBar(c.rsrp, -140, -44, Palette.rsrp(c.rsrp), Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CellFreshness(c, compact = true) }
            Text(stringResource(R.string.cell_details), style = MaterialTheme.typography.labelMedium, color = Palette.accent)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Palette.accent)
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Column {
                KeyValueRow("Registered / status", "${c.registered} · " + when { c.isPrimary -> "PRIMARY"; c.isSecondary -> "SECONDARY"; else -> "NONE" })
                KeyValueRow("PLMN", "${c.mcc ?: "—"}-${c.mnc ?: "—"} ${c.operator ?: ""}")
                KeyValueRow(if (c.rat == Rat.NR) "NCI" else "Cell ID", c.cellId?.let { "$it (0x${it.toString(16)})" } ?: "—")
                KeyValueRow(if (c.rat == Rat.NR) "gNB / cell" else "eNB / sector", "${fmt(c.gnbOrEnb)} / ${fmt(c.sectorId)}")
                KeyValueRow("PCI / PSC / BSIC", fmt(c.pci))
                KeyValueRow("TAC / LAC", c.tac?.let { "$it (0x${it.toString(16)})" } ?: "—")
                KeyValueRow("ARFCN", fmt(c.arfcn))
                KeyValueRow("Bands", c.bandLabel)
                KeyValueRow("DL / UL freq", "${fmtMhz(c.dlMhz)} / ${fmtMhz(c.ulMhz)} MHz")
                KeyValueRow("Bandwidth", fmtBw(c.bandwidthKhz))
                KeyValueRow("RSSI", fmt(c.rssi, " dBm"))
                KeyValueRow("RSRP", fmt(c.rsrp, " dBm"), Palette.rsrp(c.rsrp))
                KeyValueRow("RSRQ", fmt(c.rsrq, " dB"), Palette.rsrq(c.rsrq))
                KeyValueRow(if (c.rat == Rat.LTE) "RSSNR" else "SS-SINR", fmt(c.sinr, " dB"), Palette.sinr(c.sinr))
                if (c.rat == Rat.NR) {
                    KeyValueRow("CSI-RSRP / RSRQ / SINR", "${fmt(c.csiRsrp)} / ${fmt(c.csiRsrq)} / ${fmt(c.csiSinr)}")
                }
                if (c.rat == Rat.WCDMA) KeyValueRow("Ec/No", fmt(c.csiSinr, " dB"))
                KeyValueRow("CQI", fmt(c.cqi))
                KeyValueRow("Timing advance", c.ta?.let { "$it" + (if (c.rat == Rat.NR) " µs" else "") + c.taDistanceMeters?.let { d -> " ≈ ${d.toInt()} m" } } ?: "—")
                KeyValueRow("Level / ASU", "${c.level} / ${fmt(c.asuLevel)}")
                KeyValueRow("Age", "${(android.os.SystemClock.elapsedRealtimeNanos() - c.timestampNanos).coerceAtLeast(0) / 1_000_000} ms")
            }
        }
    }
}
