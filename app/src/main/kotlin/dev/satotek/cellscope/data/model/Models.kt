package dev.satotek.cellscope.data.model

import dev.satotek.cellscope.R

/** Radio access technology of a cell. */
enum class Rat(val label: String) { NR("5G NR"), LTE("LTE"), WCDMA("WCDMA"), GSM("GSM"), TDSCDMA("TD-SCDMA"), UNKNOWN("?") }

/**
 * One measured cell (serving or neighbour). All values are nullable because
 * Android reports CellInfo.UNAVAILABLE (Int.MAX_VALUE) for anything the modem
 * did not fill in – we normalise those to null at the boundary.
 */
data class CellEntry(
    val rat: Rat,
    val registered: Boolean,
    val isPrimary: Boolean,          // CONNECTION_PRIMARY_SERVING on API 28+
    val isSecondary: Boolean,        // CONNECTION_SECONDARY_SERVING (SCC in CA)
    val mcc: String?, val mnc: String?,
    val operator: String?,
    val cellId: Long?,               // LTE ECI / NR NCI / WCDMA CID
    val gnbOrEnb: Long?,             // derived: eNB id (ECI >> 8) / gNB id
    val sectorId: Int?,              // derived: ECI & 0xFF
    val pci: Int?,                   // LTE/NR physical cell id, WCDMA PSC
    val tac: Int?,                   // TAC / LAC
    val arfcn: Int?,                 // EARFCN / NRARFCN / UARFCN / ARFCN
    val bands: List<Int>,
    val bandwidthKhz: Int?,
    val dlMhz: Double?,              // derived from ARFCN
    val ulMhz: Double?,
    // Signal
    val rssi: Int?, val rsrp: Int?, val rsrq: Int?, val sinr: Int?, val cqi: Int?,
    val ta: Int?,                    // timing advance (LTE) / micros (NR)
    val csiRsrp: Int?, val csiRsrq: Int?, val csiSinr: Int?,
    val level: Int,                  // 0..4
    val asuLevel: Int?,
    val timestampNanos: Long,
) {
    val bandLabel: String get() = when {
        bands.isEmpty() -> "—"
        rat == Rat.NR -> bands.joinToString("/") { "n$it" }
        rat == Rat.LTE -> bands.joinToString("/") { "B$it" }
        else -> bands.joinToString("/")
    }
    /** Estimated distance from TA: LTE TA unit = 78.12 m; NR reports microseconds (c/2 ≈ 150 m/µs). */
    val taDistanceMeters: Double? get() = ta?.let { if (rat == Rat.NR) it * 150.0 else it * 78.12 }
}

/** One carrier component reported through PhysicalChannelConfig (privileged / dumpsys). */
data class CarrierComponent(
    val status: String,      // PrimaryServing / SecondaryServing / Unknown
    val rat: Rat,
    val band: Int?,
    val dlArfcn: Int?, val ulArfcn: Int?,
    val dlKhz: Int?, val ulKhz: Int?,       // cell bandwidth
    val dlFreqKhz: Int?, val ulFreqKhz: Int?,
    val pci: Int?,
    val frequencyRange: String?,            // LOW / MID / HIGH / MMWAVE (NR)
    val contextIds: List<Int>,
) {
    val isPrimary get() = status.startsWith("Primary")
    val bandLabel get() = band?.let { if (rat == Rat.NR) "n$it" else "B$it" } ?: "—"
}

/** Non-cell state that comes from ServiceState / TelephonyDisplayInfo. */
data class NetworkState(
    val serviceState: String = "—",
    val dataNetworkType: String = "—",
    val overrideNetworkType: String = "NONE",   // NR_NSA / NR_ADVANCED / LTE_CA / LTE_ADVANCED_PRO
    val nrState: String = "—",                  // derived (public) or NetworkTypeController (root)
    val nrStateSource: String = "",
    val nrAnchorPci: Int? = null,
    val nrFrequencyRange: String = "—",
    val isEnDcAvailable: Boolean? = null,
    val isNrAvailable: Boolean? = null,
    val isDcNrRestricted: Boolean? = null,
    val vopsSupported: Boolean? = null,
    val usingCarrierAggregation: Boolean? = null,
    val cellBandwidthsKhz: List<Int> = emptyList(),
    val roaming: Boolean = false,
    val operatorName: String = "—",
    val simOperator: String = "—",
    val plmn: String = "—",
    val dataState: String = "—",
    val dataActivity: String = "—",
    val imsRegistered: Boolean? = null,
    val imsTransport: String? = null,
    val voNrEnabled: Boolean? = null,
    val callState: String = "IDLE",
    val rawServiceState: String = "",
)

/** Time-series sample for the chart. Serving-cell metrics plus the NR SCC (NSA) and throughput. */
data class SignalSample(
    val t: Long,
    val rat: Rat, val band: String, val pci: Int?,
    val rsrp: Int?, val rsrq: Int?, val sinr: Int?,
    val nrRsrp: Int? = null, val nrRsrq: Int? = null, val nrSinr: Int? = null, val nrPci: Int? = null,
    val rxBps: Long? = null, val txBps: Long? = null,
    val ccCount: Int = 1,            // total component carriers (LTE + NR), kept for statistics
    val ccLte: Int = 0, val ccNr: Int = 0,
    val neighbours: List<NbSample> = emptyList(),
    val rttMs: Double? = null,       // null = no probe yet, -1.0 = timeout / loss
)

/** Compact per-neighbour measurement kept in every sample for the neighbour time-series. */
data class NbSample(val rat: Rat, val band: String, val pci: Int?, val arfcn: Int?, val rsrp: Int?, val rsrq: Int?, val sinr: Int?) {
    val key get() = "$band/$pci"
}

/** One PDN / data call parsed from dumpsys telephony.registry (root). */
data class DataCall(
    val apnName: String, val apn: String, val types: String, val state: String, val transport: String,
    val networkType: String, val iface: String?, val addresses: List<String>, val dns: List<String>, val pcscf: List<String>,
    val mtu: Int?, val qos: String?, val failCause: String?, val netId: Int?,
)

/** Rolling latency statistics over the visible window. */
data class PingStats(val sent: Int, val lost: Int, val min: Double?, val avg: Double?, val max: Double?, val jitter: Double?) {
    val lossPct get() = if (sent == 0) 0.0 else lost * 100.0 / sent
}

/** A serving-cell change detected between two consecutive samples. */
data class CellEvent(val t: Long, val from: SignalSample, val to: SignalSample) {
    val kind: String get() = when {
        from.rat != to.rat -> "RAT"
        from.band != to.band -> "BAND"
        else -> "HO"
    }
}

/** Everything that requires root. */
data class RootState(
    val available: Boolean? = null,       // null = not probed yet
    val enabled: Boolean = false,
    val physicalChannels: List<CarrierComponent> = emptyList(),
    val physicalChannelsRaw: String = "",
    val radioLog: List<String> = emptyList(),
    val modemProps: Map<String, String> = emptyMap(),
    val dataCalls: List<DataCall> = emptyList(),
    val lastError: String? = null,
)

enum class PrivilegeLevel(val labelRes: Int) { NORMAL(R.string.priv_normal), ROOT(R.string.priv_root), PRIV_APP(R.string.priv_privapp) }

data class Snapshot(
    val cells: List<CellEntry> = emptyList(),
    val network: NetworkState = NetworkState(),
    val history: List<SignalSample> = emptyList(),
    val events: List<CellEvent> = emptyList(),
    val root: RootState = RootState(),
    val privilege: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val permissionsGranted: Boolean = false,
    val lastUpdate: Long = 0L,
    val updateCount: Long = 0L,
    val location: Pair<Double, Double>? = null,
    val locationAccuracyM: Float? = null,
) {
    val serving: CellEntry? get() = cells.firstOrNull { it.isPrimary } ?: cells.firstOrNull { it.registered }
    /** CellInfo rarely flags SCCs; correlate with PhysicalChannelConfig by (PCI, ARFCN) instead. */
    private val sccKeys: Set<Pair<Int?, Int?>> get() = root.physicalChannels.filter { !it.isPrimary }.map { it.pci to it.dlArfcn }.toSet()
    val secondaries: List<CellEntry> get() = cells.filter { it.isSecondary || (it.pci to it.arfcn) in sccKeys }
    val neighbours: List<CellEntry> get() = cells.filter { !it.registered && !it.isSecondary && (it.pci to it.arfcn) !in sccKeys }
}

/**
 * CC count labels. EN-DC has two independent aggregations (LTE side + NR side), so "6CC" for
 * LTE 4CC + NR 2CC is meaningless — show them separately.
 */
fun ccShort(lte: Int, nr: Int): String = if (lte > 0 && nr > 0) "${lte}+${nr}CC" else "${lte + nr}CC"
fun ccLong(lte: Int, nr: Int): String = when {
    lte > 0 && nr > 0 -> "LTE ${lte}CC + NR ${nr}CC"
    nr > 0 -> "NR ${nr}CC"
    else -> "LTE ${lte}CC"
}
/** Split a PhysicalChannelConfig list (or fallback cell list) into (lte, nr) carrier counts. */
fun ccSplit(components: List<CarrierComponent>): Pair<Int, Int> =
    components.count { it.rat != Rat.NR } to components.count { it.rat == Rat.NR }
