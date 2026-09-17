package dev.satotek.cellscope.ui.screens

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
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.RatChip
import dev.satotek.cellscope.ui.components.Tag
import dev.satotek.cellscope.ui.components.fmt
import dev.satotek.cellscope.ui.components.fmtBw
import dev.satotek.cellscope.ui.components.fmtMhz
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette

/** Every cell the modem reports, grouped by RAT, each expandable to the full field list. */
@Composable
fun CellsScreen(s: Snapshot) {
    val groups = s.cells.groupBy { it.rat }.toSortedMap(compareBy { it.ordinal })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${stringResource(R.string.tab_cells)} · ${s.cells.size}", style = MaterialTheme.typography.headlineMedium, color = Palette.text)
                Text(groups.entries.joinToString("  ") { "${it.key.name} ${it.value.size}" }, fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
            }
        }
        groups.forEach { (rat, cells) ->
            item { Text(rat.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.rat(rat), modifier = Modifier.padding(top = 8.dp)) }
            items(cells, key = { "${it.rat}-${it.pci}-${it.arfcn}-${it.cellId}" }) { CellCard(it, isScc = it in s.secondaries) }
        }
        if (s.cells.isEmpty()) item { Text("no cells", color = Palette.textDim) }
    }
}

@Composable
private fun CellCard(c: CellEntry, isScc: Boolean) {
    var open by remember { mutableStateOf(false) }
    Panel(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatChip(c.rat, c.rat.name, filled = c.registered)
            Spacer(Modifier.width(8.dp))
            Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Palette.rat(c.rat))
            Spacer(Modifier.width(8.dp))
            if (c.isPrimary) Tag("PCC", Palette.nr) else if (isScc) Tag("SCC", Palette.lte) else if (c.registered) Tag("REG", Palette.nr)
            Spacer(Modifier.weight(1f))
            Text(fmt(c.rsrp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.rsrp(c.rsrp))
            Text(" dBm", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        }
        Spacer(Modifier.height(6.dp))
        Text("PCI ${fmt(c.pci)} · ${fmt(c.arfcn)} · ${fmtMhz(c.dlMhz)}MHz · Q ${fmt(c.rsrq)} · SINR ${fmt(c.sinr)}", fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        LevelBar(c.rsrp, -140, -44, Palette.rsrp(c.rsrp), Modifier.fillMaxWidth())
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
                KeyValueRow("Age", "${(System.nanoTime() - c.timestampNanos).coerceAtLeast(0) / 1_000_000} ms")
            }
        }
    }
}
