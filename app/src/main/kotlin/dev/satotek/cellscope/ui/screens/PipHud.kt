package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.HudPage
import dev.satotek.cellscope.data.model.CarrierComponent
import dev.satotek.cellscope.data.model.ccShort
import dev.satotek.cellscope.data.model.ccSplit
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.ui.components.EventRow
import dev.satotek.cellscope.ui.components.RatChip
import dev.satotek.cellscope.ui.components.Tag
import dev.satotek.cellscope.ui.components.fmt
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.util.Locale

/** Compact HUD for PiP / overlay; extra rows appear as the window gets taller. */
@Composable
fun PipHud(s: Snapshot, page: HudPage = HudPage.SERVING) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.bg)) {
        val h = maxHeight
        // Tiny window: keep the cycle pages. Anything overlay-sized or larger fills leftover space.
        if (h < 150.dp) {
            if (page == HudPage.THROUGHPUT) ThroughputHud(s) else ServingOnly(s)
            return@BoxWithConstraints
        }
        val ca = caOf(s)
        val last = s.history.lastOrNull()
        val showPcc = h >= 200.dp && ca.pcc.isNotEmpty()
        val showSpark = h >= 250.dp
        val showEvents = h >= 230.dp
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)
                .then(if (showPcc || showEvents) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ServingHeader(s, ca)
            if (showPcc) {
                ca.pcc.forEachIndexed { i, c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Tag(if (c.isPrimary) "PCC" else "SCC$i", if (c.isPrimary) Palette.nr else Palette.lte)
                        Spacer(Modifier.width(8.dp))
                        RatChip(c.rat, filled = false)
                        Spacer(Modifier.width(8.dp))
                        Text(c.bandLabel, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.rat(c.rat))
                        Spacer(Modifier.weight(1f))
                        Text("PCI ${fmt(c.pci)}  ·  ${c.dlKhz?.let { mhz(it) + " MHz" } ?: "—"}", fontFamily = Mono, fontSize = 12.sp, color = Palette.text)
                    }
                }
            } else {
                ca.summary?.let { Text(it, fontFamily = Mono, fontSize = 12.sp, color = Palette.lte, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("▼ ${mbps(last?.rxBps)}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.nr, modifier = Modifier.weight(1f), maxLines = 1)
                Text("▲ ${mbps(last?.txBps)}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.wcdma, modifier = Modifier.weight(1f), maxLines = 1)
                RttBlock(last, big = false)
            }
            if (showSpark) RsrpSpark(s.history, Modifier.fillMaxWidth().height(72.dp))
            if (showEvents) {
                val ev = s.events.takeLast(3).asReversed()
                if (ev.isEmpty()) Text("—", fontFamily = Mono, color = Palette.textDim)
                else ev.forEach { EventRow(it) }
            }
        }
    }
}

@Composable
private fun ServingOnly(s: Snapshot) {
    val ca = caOf(s)
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
        ServingHeader(s, ca)
        ca.summary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontFamily = Mono, fontSize = 12.sp, color = Palette.lte, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ThroughputHud(s: Snapshot) {
    val last = s.history.lastOrNull()
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("▼  ${mbps(last?.rxBps)}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Palette.nr, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text("▲  ${mbps(last?.txBps)}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Palette.wcdma, maxLines = 1)
        }
        RttBlock(last)
    }
}

@Composable
private fun ServingHeader(s: Snapshot, ca: CaInfo) {
    val c = s.serving
    val rat = c?.rat ?: Rat.UNKNOWN
    Column(Modifier.fillMaxWidth()) {
        // Row 1: RAT + band on the left, RSRP on the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RatChip(rat)
            Spacer(Modifier.width(8.dp))
            Text(
                c?.bandLabel ?: "—",
                fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = Palette.rat(rat), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(fmt(c?.rsrp), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Palette.rsrp(c?.rsrp), maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text("dBm", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        }
        Spacer(Modifier.height(4.dp))
        // Row 2 gets the full width so "PCI · SINR" never truncates next to the CC tag.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (ca.cc > 1) {
                Tag(ccShort(ca.ccLte, ca.ccNr), Palette.lte)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                buildString {
                    append("PCI ${fmt(c?.pci)} · SINR ${fmt(c?.sinr)}")
                    if (ca.summary == null) ca.bwCompact?.let { append(" · $it MHz") }
                },
                modifier = Modifier.weight(1f),
                fontFamily = Mono, fontSize = 13.sp, color = Palette.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RttBlock(last: SignalSample?, big: Boolean = true) {
    val rtt = last?.rttMs
    val text = when {
        rtt == null -> "—"
        rtt < 0 -> "LOST"
        else -> rtt.toInt().toString()
    }
    val color = when {
        rtt == null -> Palette.textDim
        rtt < 0 -> Palette.poor
        rtt < 50 -> Palette.good
        rtt < 120 -> Palette.fair
        else -> Palette.poor
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (big) 36.sp else 20.sp, color = color, maxLines = 1)
        Text("ms", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
    }
}

@Composable
private fun RsrpSpark(history: List<SignalSample>, modifier: Modifier) {
    val lastT = history.lastOrNull()?.t ?: return
    val pts = history.mapNotNull { s -> s.rsrp?.let { s.t to it } }.filter { it.first >= lastT - 120_000L }
    if (pts.size < 2) return
    val stroke = Palette.rsrp(pts.last().second)
    val track = Palette.outline
    Canvas(modifier) {
        drawRect(track.copy(alpha = 0.25f))
        val minV = -140f
        val maxV = -44f
        val w = size.width
        val h = size.height
        val t0 = pts.first().first
        val span = (lastT - t0).coerceAtLeast(1L).toFloat()
        fun x(t: Long) = ((t - t0) / span) * w
        fun y(v: Int) = h - ((v - minV) / (maxV - minV)).coerceIn(0f, 1f) * h
        val path = Path()
        pts.forEachIndexed { i, (t, v) ->
            val p = Offset(x(t), y(v))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, stroke, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private data class CaInfo(
    val cc: Int,
    val ccLte: Int, val ccNr: Int,
    val pcc: List<CarrierComponent>,
    val bwCompact: String?,
    val summary: String?,
)

private fun caOf(s: Snapshot): CaInfo {
    val c = s.serving
    val pcc = s.root.physicalChannels.sortedByDescending { it.isPrimary }
    val last = s.history.lastOrNull()
    val (ccLte, ccNr) = when {
        pcc.isNotEmpty() -> ccSplit(pcc)
        last != null && last.ccLte + last.ccNr > 0 -> last.ccLte to last.ccNr
        else -> (listOfNotNull(c) + s.secondaries).let { all -> all.count { it.rat != Rat.NR } to all.count { it.rat == Rat.NR } }
    }
    val cc = (ccLte + ccNr).coerceAtLeast(1)
    val bands = when {
        pcc.isNotEmpty() -> pcc.map { it.bandLabel }
        else -> listOfNotNull(c?.bandLabel?.takeIf { it != "—" })
    }
    val bws = when {
        pcc.any { it.dlKhz != null } -> pcc.mapNotNull { it.dlKhz }
        s.network.cellBandwidthsKhz.isNotEmpty() -> s.network.cellBandwidthsKhz
        else -> listOfNotNull(c?.bandwidthKhz)
    }
    // Total bandwidth, not the per-CC list: "20+20+15+100" never fits a PiP window.
    val bwCompact = if (bws.isEmpty()) null else mhz(bws.sum())
    val summary = if (cc > 1 && (bands.size > 1 || bwCompact != null)) {
        listOfNotNull(bands.takeIf { it.size > 1 }?.joinToString("+"), bwCompact?.let { "$it MHz" }).joinToString(" · ")
    } else null
    return CaInfo(cc, ccLte, ccNr, pcc, bwCompact, summary)
}

private fun mhz(khz: Int): String = if (khz % 1000 == 0) "${khz / 1000}" else String.format(Locale.US, "%.1f", khz / 1000.0)
private fun mbps(b: Long?): String = b?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) } ?: "—"
