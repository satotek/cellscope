package dev.satotek.cellscope.ui.snapshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.CarrierComponent
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.model.ccLong
import dev.satotek.cellscope.data.model.ccSplit
import dev.satotek.cellscope.ui.components.ArcGauge
import dev.satotek.cellscope.ui.components.Metric
import dev.satotek.cellscope.ui.components.LevelBar
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.RatChip
import dev.satotek.cellscope.ui.components.Tag
import dev.satotek.cellscope.ui.components.fmt
import dev.satotek.cellscope.ui.components.fmtBw
import dev.satotek.cellscope.ui.theme.CellScopeTheme
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SnapshotCard(s: Snapshot, map: ImageBitmap?, takenAt: Long) {
    // Width is fixed at 1080 px; height follows the content so the PNG never has dead space.
    Column(Modifier.width(1080.dp).background(Palette.bg)) {
        MapBanner(s, map)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ServingBlock(s)
            CaBlock(s)
            NetworkBlock(s)
            NeighboursBlock(s)
            Footer(s, takenAt)
        }
    }
}

@Composable
private fun MapBanner(s: Snapshot, map: ImageBitmap?) {
    val attr = stringResource(R.string.osm_attribution)
    Box(Modifier.size(1080.dp, 520.dp).background(Palette.surface)) {
        if (map != null) {
            Image(map, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .clip(CircleShape)
                    .background(Palette.accent),
            )
            Text(
                formatLocation(s.location, s.locationAccuracyM),
                fontFamily = Mono, fontSize = 18.sp, color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Text(
                attr,
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
            val loc = s.location
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (loc != null) {
                    Text(String.format(Locale.US, "%.4f", loc.first), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Palette.text)
                    Text(String.format(Locale.US, "%.4f", loc.second), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Palette.text)
                    s.locationAccuracyM?.takeIf { it > 0f }?.let { Text(String.format(Locale.US, "±%.0f m", it), fontFamily = Mono, fontSize = 18.sp, color = Palette.textDim) }
                } else {
                    Text("—", fontFamily = Mono, fontSize = 44.sp, color = Palette.textDim)
                }
            }
            Text(
                attr,
                fontSize = 10.sp,
                color = Palette.textDim,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp, 8.dp),
            )
        }
    }
}

@Composable
private fun ServingBlock(s: Snapshot) {
    val c = s.serving
    val rat = c?.rat ?: Rat.UNKNOWN
    Panel(accent = Palette.rat(rat), title = "Serving") {
        if (c == null) {
            Text("—", fontFamily = Mono, color = Palette.textDim)
            return@Panel
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatChip(rat)
            Spacer(Modifier.width(12.dp))
            Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = Palette.rat(rat))
            Spacer(Modifier.weight(1f))
            Metric("PCI", fmt(c.pci), color = Palette.text)
            Spacer(Modifier.width(24.dp))
            Metric(
                "NR state",
                s.network.nrState,
                color = if (s.network.nrState.startsWith("CONNECTED") || s.network.nrState == "NR_ADV" || s.network.nrState == "SA") Palette.nr else Palette.text,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcGauge(c.rsrp, -140, -44, "RSRP", "dBm", Palette.rsrp(c.rsrp), sizeDp = 180)
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { Metric("RSRQ", fmt(c.rsrq), "dB", Palette.rsrq(c.rsrq), big = true) }
                    Box(Modifier.weight(1f)) { Metric(if (rat == Rat.LTE) "RSSNR" else "SINR", fmt(c.sinr), "dB", Palette.sinr(c.sinr), big = true) }
                    Box(Modifier.weight(1f)) { Metric("CQI", fmt(c.cqi), color = Palette.text, big = true) }
                }
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { Metric(if (rat == Rat.NR) "NR-ARFCN" else "EARFCN", fmt(c.arfcn)) }
                    Box(Modifier.weight(1f)) { Metric("DL", c.dlMhz?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "MHz") }
                    Box(Modifier.weight(1f)) { Metric("BW", (c.bandwidthKhz ?: s.root.physicalChannels.firstOrNull { it.isPrimary }?.dlKhz)?.let { fmtBw(it) } ?: "—") }
                }
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { Metric("TA", fmt(c.ta), if (rat == Rat.NR) "µs" else "") }
                    Box(Modifier.weight(1f)) { Metric("Distance", c.taDistanceMeters?.let { String.format(Locale.US, "%.2f", it / 1000) } ?: "—", "km") }
                    Box(Modifier.weight(1f)) { Metric("TAC", fmt(c.tac)) }
                }
            }
        }
    }
}

@Composable
private fun CaBlock(s: Snapshot) {
    val cc = s.root.physicalChannels
    if (cc.isNotEmpty()) {
        val total = cc.sumOf { it.dlKhz ?: 0 }
        val (nLte, nNr) = ccSplit(cc)
        Panel(title = "CA · ${ccLong(nLte, nNr)} · ${fmtBw(total)}", accent = Palette.lte) {
            cc.sortedByDescending { it.isPrimary }.forEachIndexed { i, c -> CaRow(c, i) }
        }
        return
    }
    val cells = listOfNotNull(s.serving) + s.secondaries
    if (cells.isEmpty()) return
    val total = cells.sumOf { it.bandwidthKhz ?: 0 }
    val nLte = cells.count { it.rat != Rat.NR }
    val nNr = cells.count { it.rat == Rat.NR }
    Panel(title = "CA · ${ccLong(nLte, nNr)} · ${fmtBw(total)}", accent = Palette.lte) {
        cells.forEachIndexed { i, c -> CellCaRow(c, i == 0, i) }
    }
}

@Composable
private fun CaRow(c: CarrierComponent, index: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(if (c.isPrimary) "PCC" else "SCC$index", if (c.isPrimary) Palette.nr else Palette.lte)
        Spacer(Modifier.width(8.dp))
        RatChip(c.rat, c.rat.name, filled = false)
        Spacer(Modifier.width(8.dp))
        Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.rat(c.rat))
        Spacer(Modifier.weight(1f))
        Text(fmtBw(c.dlKhz), fontFamily = Mono, fontSize = 14.sp, color = Palette.text)
        Spacer(Modifier.width(16.dp))
        Text("PCI ${fmt(c.pci)}", fontFamily = Mono, fontSize = 14.sp, color = Palette.textDim)
    }
}

@Composable
private fun CellCaRow(c: CellEntry, primary: Boolean, index: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(if (primary) "PCC" else "SCC$index", if (primary) Palette.nr else Palette.lte)
        Spacer(Modifier.width(8.dp))
        RatChip(c.rat, c.rat.name, filled = false)
        Spacer(Modifier.width(8.dp))
        Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.rat(c.rat))
        Spacer(Modifier.weight(1f))
        Text(fmtBw(c.bandwidthKhz), fontFamily = Mono, fontSize = 14.sp, color = Palette.text)
        Spacer(Modifier.width(16.dp))
        Text("PCI ${fmt(c.pci)}", fontFamily = Mono, fontSize = 14.sp, color = Palette.textDim)
    }
}

@Composable
private fun NetworkBlock(s: Snapshot) {
    val n = s.network
    val last = s.history.lastOrNull()
    val rtt = last?.rttMs
    Panel(title = "Network") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Operator", n.operatorName, color = Palette.text)
            Metric("PLMN", n.plmn)
            Metric("IMS", n.imsRegistered?.let { if (it) "REG" else "no" } ?: "—", color = if (n.imsRegistered == true) Palette.good else Palette.text)
            Metric("IMS via", n.imsTransport ?: "—")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("VoNR", n.voNrEnabled?.let { if (it) "on" else "off" } ?: "—")
            Metric("▼ Mbps", last?.rxBps?.let { String.format(Locale.US, "%.1f", it / 1e6) } ?: "—", color = Palette.nr)
            Metric("▲ Mbps", last?.txBps?.let { String.format(Locale.US, "%.1f", it / 1e6) } ?: "—", color = Palette.wcdma)
            Metric(
                "RTT",
                when {
                    rtt == null -> "—"
                    rtt < 0 -> "LOST"
                    else -> rtt.toInt().toString()
                },
                "ms",
                when {
                    rtt == null -> Palette.text
                    rtt < 0 -> Palette.poor
                    rtt < 50 -> Palette.good
                    rtt < 120 -> Palette.fair
                    else -> Palette.poor
                },
            )
        }
    }
}

@Composable
private fun NeighboursBlock(s: Snapshot) {
    val nbs = s.neighbours.sortedByDescending { it.rsrp ?: -999 }.take(6)
    if (nbs.isEmpty()) return
    Panel(title = "Neighbours · ${s.neighbours.size}") {
        nbs.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RatChip(c.rat, c.rat.name, filled = false)
                Spacer(Modifier.width(10.dp))
                Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.rat(c.rat), modifier = Modifier.width(64.dp))
                Text("PCI ${fmt(c.pci)}", fontFamily = Mono, fontSize = 13.sp, color = Palette.text, modifier = Modifier.width(110.dp))
                LevelBar(c.rsrp, -140, -44, Palette.rsrp(c.rsrp), Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text("${fmt(c.rsrp)} · ${fmt(c.rsrq)} · ${fmt(c.sinr)}", fontFamily = Mono, fontSize = 13.sp, color = Palette.text)
            }
        }
    }
}

@Composable
private fun Footer(s: Snapshot, takenAt: Long) {
    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(takenAt))
    Text(
        "$ts · CellScope · ${stringResource(s.privilege.labelRes)}",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.textDim,
        modifier = Modifier.padding(top = 4.dp),
    )
}

internal fun formatLocation(loc: Pair<Double, Double>?, accuracyM: Float?): String {
    if (loc == null) return "—"
    val coord = String.format(Locale.US, "%.4f, %.4f", loc.first, loc.second)
    val acc = accuracyM?.takeIf { it > 0f }?.let { String.format(Locale.US, " · ±%.0f m", it) } ?: ""
    return coord + acc
}

/** Off-screen 1080 px wide capture of [SnapshotCard] (height follows content). Density(1f) so 1 dp = 1 px. */
@Composable
fun SnapshotCaptureHost(
    snapshot: Snapshot,
    map: ImageBitmap?,
    takenAt: Long,
    onCaptured: (ImageBitmap) -> Unit,
    onFailed: (String) -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val failed = stringResource(R.string.snapshot_failed)
    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1f)) {
        Box(
            Modifier
                .width(1080.dp)
                .wrapContentHeight()
                .alpha(0.01f)
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                },
        ) {
            CellScopeTheme(darkTheme = true) {
                SnapshotCard(snapshot, map, takenAt)
            }
        }
    }
    LaunchedEffect(snapshot, map, takenAt) {
        repeat(8) { withFrameNanos { } }
        runCatching {
            val img = graphicsLayer.toImageBitmap()
            if (img.width < 100 || img.height < 100) error(failed)
            img
        }.onSuccess(onCaptured).onFailure { onFailed(it.message ?: failed) }
    }
}
