package dev.satotek.cellscope.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.ai.MeasurementDigest
import dev.satotek.cellscope.data.model.CarrierComponent
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.DataCall
import dev.satotek.cellscope.data.model.NbSample
import dev.satotek.cellscope.data.model.NetworkState
import dev.satotek.cellscope.data.model.PrivilegeLevel
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.RootState
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.HudPage
import dev.satotek.cellscope.data.root.PrivAppInstaller
import dev.satotek.cellscope.ui.screens.CellsScreen
import dev.satotek.cellscope.ui.screens.MoreScreen
import dev.satotek.cellscope.ui.screens.OverviewScreen
import dev.satotek.cellscope.ui.screens.PipHud
import dev.satotek.cellscope.ui.screens.RawScreen
import dev.satotek.cellscope.ui.screens.SignalScreen
import dev.satotek.cellscope.data.HistoryStore
import dev.satotek.cellscope.data.replay.ReplayData
import dev.satotek.cellscope.ui.screens.ReplayScreen
import dev.satotek.cellscope.ui.screens.StatsScreen
import dev.satotek.cellscope.ui.snapshot.SnapshotCard
import dev.satotek.cellscope.ui.theme.CellScopeTheme
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.util.Locale
import kotlin.math.sin

/** Fake but realistic Snapshot (povo / KDDI, LTE B1 + B3 + n77 NSA) for Compose previews. Debug source set only. */
object PreviewData {
    private fun cell(rat: Rat, band: Int, pci: Int, arfcn: Int, rsrp: Int, primary: Boolean = false, reg: Boolean = false) = CellEntry(
        rat = rat, registered = reg, isPrimary = primary, isSecondary = false, mcc = "440", mnc = "54", operator = "KDDI",
        cellId = 123456789, gnbOrEnb = 482253, sectorId = 5, pci = pci, tac = 12345, arfcn = arfcn, bands = listOf(band),
        bandwidthKhz = 20000, dlMhz = if (rat == Rat.NR) 3709.9 else 1815.0, ulMhz = 1720.0,
        rssi = -77, rsrp = rsrp, rsrq = -11, sinr = 7, cqi = null, ta = 7, csiRsrp = null, csiRsrq = null, csiSinr = null,
        level = 3, asuLevel = 51, timestampNanos = 0,
    )
    val cells = listOf(
        cell(Rat.LTE, 3, 179, 1300, -89, primary = true, reg = true),
        cell(Rat.LTE, 1, 163, 100, -95), cell(Rat.LTE, 18, 6, 5925, -91), cell(Rat.LTE, 18, 198, 5925, -93),
        cell(Rat.NR, 77, 220, 647328, -108), cell(Rat.NR, 77, 685, 647328, -111),
    )
    val cc = listOf(
        CarrierComponent("PrimaryServing", Rat.LTE, 3, 1300, 19300, 20000, 20000, 1815000, 1720000, 179, "MID", listOf(301)),
        CarrierComponent("SecondaryServing", Rat.LTE, 1, 100, null, 20000, null, 2120000, null, 163, "MID", listOf(301)),
        CarrierComponent("SecondaryServing", Rat.NR, 77, 647328, 646728, 100000, 100000, 3709920, 3700920, 220, "HIGH", listOf(301)),
    )
    val calls = listOf(
        DataCall("povo IMS", "IMS", "ims", "CONNECTED", "WWAN", "NR", "wwan3", listOf("2001:268:d608:bf6c::1/64"), emptyList(), listOf("2001:268:7003:120e::1"), 1440, "fiveQi=5 · maxDL ∞", null, 104),
        DataCall("povo", "povo.jp", "supl | hipri | default", "CONNECTED", "WWAN", "NR", "wwan1", listOf("10.95.42.47/32"), listOf("111.87.221.145"), emptyList(), 1440, "fiveQi=9 · maxDL 4194 Mbps", null, 103),
    )
    val history: List<SignalSample> = (0 until 300).map { i ->
        val t = 1_800_000_000_000L + i * 1000L
        val ho = i >= 180
        SignalSample(
            t = t, rat = Rat.LTE, band = if (ho) "B1" else "B3", pci = if (ho) 163 else 179,
            rsrp = (-90 + 6 * sin(i / 15.0)).toInt(), rsrq = (-11 + 2 * sin(i / 9.0)).toInt(), sinr = (7 + 5 * sin(i / 20.0)).toInt(),
            nrRsrp = (-108 + 5 * sin(i / 12.0)).toInt(), nrRsrq = -13, nrSinr = (1 + 4 * sin(i / 7.0)).toInt(), nrPci = 220,
            rxBps = if (i in 100..130) 120_000_000L else 300_000L, txBps = 200_000L,
            ccCount = when { i < 80 -> 2; i < 220 -> 3; else -> 4 },
            neighbours = listOf(NbSample(Rat.LTE, "B18", 6, 5925, (-92 + 3 * sin(i / 10.0)).toInt(), -13, 0), NbSample(Rat.NR, "n77", 685, 647328, (-111 + 4 * sin(i / 8.0)).toInt(), -15, -3)),
            rttMs = if (i % 37 == 0) -1.0 else 30.0 + 10 * sin(i / 5.0),
        )
    }
    val snapshot = Snapshot(
        cells = cells,
        network = NetworkState(
            serviceState = "IN_SERVICE", dataNetworkType = "LTE", overrideNetworkType = "NR_ADVANCED", nrState = "NR_ADV", nrStateSource = "root",
            nrAnchorPci = 220, nrFrequencyRange = "HIGH", isEnDcAvailable = true, isNrAvailable = true, isDcNrRestricted = false, vopsSupported = true,
            usingCarrierAggregation = true, cellBandwidthsKhz = listOf(20000, 20000, 100000), roaming = false, operatorName = "KDDI", simOperator = "povo",
            plmn = "44054", dataState = "CONNECTED", dataActivity = "▲▼ INOUT", imsRegistered = true, imsTransport = "NR", voNrEnabled = null,
            callState = "IDLE", rawServiceState = "{mVoiceRegState=0(IN_SERVICE) …}",
        ),
        history = history,
        events = listOf(CellEvent(history[180].t, history[179], history[180])),
        root = RootState(true, true, cc, "mPhysicalChannelConfigs=[…]", listOf("09-17 10:00:00.000 D NetworkTypeController: [0] NrConnectedAdvancedState: process EVENT_…"), mapOf("gsm.version.baseband" to "g6-…"), calls),
        privilege = PrivilegeLevel.ROOT, permissionsGranted = true, lastUpdate = history.last().t, updateCount = 300,
        location = 35.6812 to 139.7671, locationAccuracyM = 8f  // Tokyo Station — fake data must not carry a real location,
    )
}

@Preview(name = "Home", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 1600)
@Composable fun PreviewOverview() = CellScopeTheme { OverviewScreen(PreviewData.snapshot) }

@Preview(name = "Cells", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 1400)
@Composable fun PreviewCells() = CellScopeTheme { CellsScreen(PreviewData.snapshot) }

@Preview(name = "Signal", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 2400)
@Composable fun PreviewSignal() = CellScopeTheme { SignalScreen(PreviewData.snapshot) }

@Preview(name = "Raw", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 1200)
@Composable fun PreviewRaw() = CellScopeTheme { RawScreen(PreviewData.snapshot) }

@Preview(name = "Stats", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 1800)
@Composable fun PreviewStats() = CellScopeTheme { StatsScreen(PreviewData.snapshot) }

@Preview(name = "More", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 800)
@Composable fun PreviewMore() = CellScopeTheme { MoreScreen(PreviewData.snapshot, PrivAppInstaller.State.ACTIVE) }

@Preview(name = "PiP", showBackground = true, backgroundColor = 0xFF0B0F14, widthDp = 320, heightDp = 180)
@Composable fun PreviewPip() = CellScopeTheme { PipHud(PreviewData.snapshot, HudPage.SERVING) }

@Preview(name = "PiP throughput", showBackground = true, backgroundColor = 0xFF0B0F14, widthDp = 320, heightDp = 180)
@Composable fun PreviewPipThru() = CellScopeTheme { PipHud(PreviewData.snapshot, HudPage.THROUGHPUT) }

@Preview(name = "PiP expanded", showBackground = true, backgroundColor = 0xFF0B0F14, widthDp = 400, heightDp = 360)
@Composable fun PreviewPipExpanded() = CellScopeTheme { PipHud(PreviewData.snapshot) }

@Preview(name = "Snapshot card", showBackground = true, backgroundColor = 0xFF0B0F14, widthDp = 1080, heightDp = 1620)
@Composable fun PreviewSnapshotCard() = CellScopeTheme(darkTheme = true) {
    SnapshotCard(PreviewData.snapshot, map = null, takenAt = PreviewData.snapshot.lastUpdate)
}

@Preview(name = "Replay", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 2200)
@Composable fun PreviewReplay() = CellScopeTheme {
    val h = PreviewData.snapshot.history
    ReplayScreen(ReplayData(h, HistoryStore.eventsFrom(h), emptyList()), "cellscope-preview.jsonl")
}

@Preview(name = "AI digest", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 900)
@Composable fun PreviewAiDigest() = CellScopeTheme {
    val h = PreviewData.snapshot.history
    val range = h.first().t..h.last().t
    Column(Modifier.padding(16.dp)) {
        Text(
            MeasurementDigest.build(LocalContext.current, h, HistoryStore.eventsFrom(h), range, Locale.JAPAN),
            fontFamily = Mono, fontSize = 12.sp, color = Palette.text,
        )
    }
}
