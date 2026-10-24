package dev.satotek.cellscope.data.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** No active cell scans: the detailed repository alone requests scans on the selected SIM. */
data class SimSummary(
    val subId: Int,
    val operator: String = "—",
    val rat: String = "UNKNOWN",
    val override: String = "NONE",
    val service: String = "—",
    val nrState: String = "UNKNOWN",
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    /** NSA: the NR leg reported alongside an LTE anchor. */
    val nrRsrp: Int? = null,
    val nrRsrq: Int? = null,
    val nrSinr: Int? = null,
)

class SimSummaryMonitor(private val context: Context) {
    private val _summaries = MutableStateFlow<Map<Int, SimSummary>>(emptyMap())
    val summaries: StateFlow<Map<Int, SimSummary>> = _summaries
    private val listeners = mutableMapOf<Int, Pair<TelephonyManager, Listener>>()

    @SuppressLint("MissingPermission")
    fun sync(sims: List<SimInfo>) {
        val ids = sims.map { it.subId }.toSet()
        listeners.keys.filter { it !in ids }.forEach { id ->
            listeners.remove(id)?.let { (tm, callback) -> runCatching { tm.unregisterTelephonyCallback(callback) } }
        }
        _summaries.update { it.filterKeys { id -> id in ids } }
        sims.filter { it.subId !in listeners }.forEach { sim ->
            val tm = context.getSystemService(TelephonyManager::class.java).createForSubscriptionId(sim.subId)
            val listener = Listener(sim.subId)
            _summaries.update { it + (sim.subId to SimSummary(sim.subId)) }
            runCatching {
                tm.registerTelephonyCallback(context.mainExecutor, listener)
                listeners[sim.subId] = tm to listener
                runCatching { tm.serviceState }.getOrNull()?.let(listener::onServiceStateChanged)
                runCatching { tm.signalStrength }.getOrNull()?.let(listener::onSignalStrengthsChanged)
            }.onFailure {
                runCatching { tm.unregisterTelephonyCallback(listener) }
                listeners.remove(sim.subId)
            }
        }
    }

    fun stop() {
        listeners.values.forEach { (tm, callback) -> runCatching { tm.unregisterTelephonyCallback(callback) } }
        listeners.clear()
        _summaries.value = emptyMap()
    }

    private inner class Listener(private val subId: Int) : TelephonyCallback(),
        TelephonyCallback.SignalStrengthsListener, TelephonyCallback.ServiceStateListener,
        TelephonyCallback.DisplayInfoListener {
        private var rat = "UNKNOWN"
        private var override = "NONE"
        private var service = "—"
        private var operator = "—"
        private var lte: CellSignalStrengthLte? = null
        private var nr: CellSignalStrengthNr? = null
        private var endc: Boolean? = null
        private var available: Boolean? = null
        private var restricted: Boolean? = null

        private fun publish() {
            if (listeners[subId]?.second !== this) return
            val inService = service == "IN_SERVICE"
            val lteRsrp = lte?.rsrp?.takeIf { it in -140..-43 }
            val nrRsrp = nr?.ssRsrp?.takeIf { it in -156..-31 }
            val lteRsrq = lte?.rsrq?.takeIf { it in -34..3 }
            val lteSinr = lte?.rssnr?.takeIf { it in -20..30 }
            val nrRsrq = nr?.ssRsrq?.takeIf { it in -43..20 }
            val nrSinr = nr?.ssSinr?.takeIf { it in -23..40 }
            val summary = when {
                !inService -> SimSummary(subId, operator, rat, override, service,
                    NrConnection.state(rat, override, endc, available, restricted, inService = false))
                rat == "NR" -> SimSummary(subId, operator, rat, override, service,
                    NrConnection.state(rat, override, endc, available, restricted, inService = true),
                    nrRsrp, nrRsrq, nrSinr)
                rat == "LTE" -> SimSummary(subId, operator, rat, override, service,
                    NrConnection.state(rat, override, endc, available, restricted, inService = true),
                    lteRsrp, lteRsrq, lteSinr, nrRsrp, nrRsrq, nrSinr)
                else -> SimSummary(subId, operator, rat, override, service,
                    NrConnection.state(rat, override, endc, available, restricted, inService = true))
            }
            _summaries.update { it + (subId to summary) }
        }

        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            rat = TelephonyRepository.networkTypeName(info.networkType)
            override = TelephonyRepository.overrideName(info.overrideNetworkType)
            publish()
        }
        override fun onServiceStateChanged(state: ServiceState) {
            service = when (state.state) {
                ServiceState.STATE_IN_SERVICE -> "IN_SERVICE"
                ServiceState.STATE_POWER_OFF -> "POWER_OFF"
                ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY_ONLY"
                else -> "OUT_OF_SERVICE"
            }
            operator = try { state.operatorAlphaLong?.takeIf { it.isNotBlank() } ?: "—" }
                catch (_: SecurityException) { "—" }
            val raw = state.toString()
            fun flag(key: String) = Regex("$key\\s*=\\s*(true|false)").find(raw)?.groupValues?.get(1)?.toBoolean()
            endc = flag("isEnDcAvailable"); available = flag("isNrAvailable"); restricted = flag("isDcNrRestricted")
            publish()
        }
        override fun onSignalStrengthsChanged(signal: SignalStrength) {
            lte = signal.cellSignalStrengths.filterIsInstance<CellSignalStrengthLte>().firstOrNull()
            nr = signal.cellSignalStrengths.filterIsInstance<CellSignalStrengthNr>().firstOrNull()
            publish()
        }
    }
}
