package dev.satotek.cellscope.data.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.telephony.PhysicalChannelConfig
import android.util.Log
import dev.satotek.cellscope.data.model.CarrierComponent
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.NetworkState
import dev.satotek.cellscope.data.model.Rat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executor

/**
 * Public-API data source. Everything here works with READ_PHONE_STATE +
 * ACCESS_FINE_LOCATION. Listeners that need READ_PRIVILEGED_PHONE_STATE
 * (PhysicalChannelConfig) are attempted and silently degrade on SecurityException.
 */
class TelephonyRepository(private val context: Context) {

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val mainExecutor: Executor = context.mainExecutor

    private val _cells = MutableStateFlow<List<CellEntry>>(emptyList())
    val cells: StateFlow<List<CellEntry>> = _cells

    private val _network = MutableStateFlow(NetworkState())
    val network: StateFlow<NetworkState> = _network

    /** Filled only when the app is privileged (priv-app build). */
    private val _physicalChannels = MutableStateFlow<List<CarrierComponent>>(emptyList())
    val physicalChannels: StateFlow<List<CarrierComponent>> = _physicalChannels

    private val _privileged = MutableStateFlow(false)
    val privileged: StateFlow<Boolean> = _privileged

    private var callback: TelephonyCallback? = null
    private var privCallback: TelephonyCallback? = null

    fun hasPermissions(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isPrivileged(): Boolean =
        context.checkSelfPermission("android.permission.READ_PRIVILEGED_PHONE_STATE") == PackageManager.PERMISSION_GRANTED

    fun allowedNetworkTypes(): Long? = if (isPrivileged()) RatLock.current(tm) else null
    fun setRatLock(mode: RatLock.Mode): String? = if (isPrivileged()) RatLock.apply(tm, mode) else "priv-app required"

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermissions()) return
        stop()
        _privileged.value = isPrivileged()
        val cb = Listener()
        callback = cb
        runCatching { tm.registerTelephonyCallback(mainExecutor, cb) }
            .onFailure { Log.w(TAG, "registerTelephonyCallback failed", it) }
        // A callback set that includes a privileged listener is rejected as a whole
        // (SecurityException: listen), so the privileged one lives in its own object.
        // PhysicalChannelConfigListener sits in TelephonyRegistry's READ_PRECISE_PHONE_STATE group, not the
        // READ_PRIVILEGED one — without it the registration is rejected after the initial notifyNow delivery.
        val precise = context.checkSelfPermission("android.permission.READ_PRECISE_PHONE_STATE") == PackageManager.PERMISSION_GRANTED
        if (_privileged.value && precise) {
            val pcb = PrivListener()
            privCallback = pcb
            runCatching { tm.registerTelephonyCallback(mainExecutor, pcb) }
                .onFailure { Log.w(TAG, "privileged callback rejected", it); privCallback = null; _privileged.value = false }
        }
        refresh()
    }

    fun stop() {
        callback?.let { runCatching { tm.unregisterTelephonyCallback(it) } }
        privCallback?.let { runCatching { tm.unregisterTelephonyCallback(it) } }
        callback = null; privCallback = null
    }

    /** Force the modem to re-scan; result arrives in onCellInfo. Also pulls the cached list right away. */
    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!hasPermissions()) return
        runCatching {
            tm.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfo: MutableList<CellInfo>) = publishCells(cellInfo)
                override fun onError(errorCode: Int, detail: Throwable?) {
                    Log.w(TAG, "requestCellInfoUpdate error=$errorCode", detail)
                    tm.allCellInfo?.let { publishCells(it) }
                }
            })
        }.onFailure { tm.allCellInfo?.let { l -> publishCells(l) } }
        publishStatic()
    }

    private fun publishCells(list: List<CellInfo>) {
        val mapped = list.mapNotNull { CellMapper.map(it) }
            .sortedWith(compareByDescending<CellEntry> { it.isPrimary }
                .thenByDescending { it.registered }
                .thenByDescending { it.isSecondary }
                .thenByDescending { it.rsrp ?: -200 })
        _cells.value = mapped
    }

    @SuppressLint("MissingPermission")
    private fun publishStatic() {
        val ss: ServiceState? = runCatching { tm.serviceState }.getOrNull()
        _network.update { n ->
            n.copy(
                operatorName = tm.networkOperatorName.ifBlank { "—" },
                simOperator = tm.simOperatorName.ifBlank { "—" },
                plmn = tm.networkOperator.ifBlank { "—" },
                dataNetworkType = networkTypeName(runCatching { tm.dataNetworkType }.getOrDefault(0)),
                dataState = dataStateName(tm.dataState),
                dataActivity = dataActivityName(tm.dataActivity),
                // @SystemApi – only resolvable via reflection, and only answers when privileged.
                voNrEnabled = if (_privileged.value) runCatching { tm.javaClass.getMethod("isVoNrEnabled").invoke(tm) as? Boolean }.getOrNull() else null,
                roaming = tm.isNetworkRoaming,
            ).let { if (ss != null) applyServiceState(it, ss) else it }
        }
    }

    /**
     * ServiceState.toString() leaks a lot of @hide fields (nrState, isEnDcAvailable,
     * mNrFrequencyRange, isUsingCarrierAggregation…). Parsing the string is the only
     * non-privileged way to get them.
     */
    private fun applyServiceState(n: NetworkState, ss: ServiceState): NetworkState {
        val raw = ss.toString()
        fun bool(key: String): Boolean? = Regex("$key\\s*=\\s*(true|false)").find(raw)?.groupValues?.get(1)?.toBoolean()
        fun str(key: String): String? = Regex("$key\\s*=\\s*([A-Za-z0-9_]+)").find(raw)?.groupValues?.get(1)
        // Android 17 redacts nrState=**** in toString(); derive it from what is still visible.
        val endc = bool("isEnDcAvailable"); val nrAvail = bool("isNrAvailable"); val restricted = bool("isDcNrRestricted")
        val nrState = if (n.nrStateSource == "root") n.nrState
            else deriveNrState(n.dataNetworkType, n.overrideNetworkType, endc, nrAvail, restricted)
        val range = str("mNrFrequencyRange")?.let { r ->
            when (r) { "0" -> "UNKNOWN"; "1" -> "LOW"; "2" -> "MID"; "3" -> "HIGH"; "4" -> "MMWAVE"; else -> r }
        } ?: "—"
        return n.copy(
            serviceState = when (ss.state) {
                ServiceState.STATE_IN_SERVICE -> "IN_SERVICE"
                ServiceState.STATE_OUT_OF_SERVICE -> "OUT_OF_SERVICE"
                ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY_ONLY"
                ServiceState.STATE_POWER_OFF -> "POWER_OFF"
                else -> "?"
            },
            roaming = ss.roaming,
            cellBandwidthsKhz = ss.cellBandwidths.toList(),
            nrState = nrState,
            nrFrequencyRange = range,
            isEnDcAvailable = endc,
            isNrAvailable = nrAvail,
            isDcNrRestricted = restricted,
            // LteVopsSupportInfo prints "mVopsSupport = 2" (1 = not supported, 2 = supported).
            vopsSupported = Regex("mVopsSupport\\s*=\\s*(\\d)").find(raw)?.groupValues?.get(1)?.let { it == "2" } ?: bool("isVopsSupported"),
            usingCarrierAggregation = bool("mIsUsingCarrierAggregation") ?: bool("isUsingCarrierAggregation"),
            operatorName = ss.operatorAlphaLong?.takeIf { it.isNotBlank() } ?: n.operatorName,
            rawServiceState = raw,
        )
    }

    private fun deriveNrState(dataRat: String, override: String, endc: Boolean?, nrAvail: Boolean?, restricted: Boolean?) = when {
        dataRat == "NR" -> "SA"
        override == "NR_ADVANCED" -> "NR_ADV"
        override == "NR_NSA" -> "CONNECTED"
        restricted == true -> "RESTRICTED"
        endc == true && nrAvail == true -> "NOT_RESTRICTED"
        nrAvail == true -> "NR_AVAIL"
        else -> "NONE"
    }

    private inner class Listener : TelephonyCallback(),
        TelephonyCallback.CellInfoListener,
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.ServiceStateListener,
        TelephonyCallback.DisplayInfoListener,
        TelephonyCallback.DataActivityListener,
        TelephonyCallback.DataConnectionStateListener,
        TelephonyCallback.CallStateListener {

        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) = publishCells(cellInfo)
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) { refreshCached() }
        override fun onServiceStateChanged(serviceState: ServiceState) {
            _network.update { applyServiceState(it, serviceState) }
        }
        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            _network.update {
                val rat = networkTypeName(telephonyDisplayInfo.networkType)
                val ov = overrideName(telephonyDisplayInfo.overrideNetworkType)
                it.copy(
                    dataNetworkType = rat, overrideNetworkType = ov,
                    nrState = if (it.nrStateSource == "root") it.nrState else deriveNrState(rat, ov, it.isEnDcAvailable, it.isNrAvailable, it.isDcNrRestricted),
                )
            }
        }
        override fun onDataActivity(direction: Int) { _network.update { it.copy(dataActivity = dataActivityName(direction)) } }
        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
            _network.update { it.copy(dataState = dataStateName(state), dataNetworkType = networkTypeName(networkType)) }
        }
        override fun onCallStateChanged(state: Int) {
            _network.update { it.copy(callState = when (state) { 1 -> "RINGING"; 2 -> "OFFHOOK"; else -> "IDLE" }) }
        }
    }

    /** Only registered when we hold READ_PRIVILEGED_PHONE_STATE (priv-app build). */
    /**
     * CellInfoListener rides along on purpose: TelephonyRegistry only evaluates location access for
     * records that include a location-gated event, and without it PhysicalChannelConfig arrives with
     * the PCI sanitized to PHYSICAL_CELL_ID_UNKNOWN.
     */
    private inner class PrivListener : TelephonyCallback(), TelephonyCallback.PhysicalChannelConfigListener,
        TelephonyCallback.CellInfoListener {
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) { /* handled by Listener */ }
        override fun onPhysicalChannelConfigChanged(configs: MutableList<PhysicalChannelConfig>) {
            _physicalChannels.value = configs.map { c ->
                CarrierComponent(
                    status = when (c.connectionStatus) {
                        PhysicalChannelConfig.CONNECTION_PRIMARY_SERVING -> "PrimaryServing"
                        PhysicalChannelConfig.CONNECTION_SECONDARY_SERVING -> "SecondaryServing"
                        else -> "Unknown"
                    },
                    rat = ratFromNetworkType(c.networkType),
                    band = c.band.takeIf { it > 0 },
                    dlArfcn = c.downlinkChannelNumber.takeIf { it > 0 }, ulArfcn = c.uplinkChannelNumber.takeIf { it > 0 },
                    dlKhz = c.cellBandwidthDownlinkKhz.takeIf { it > 0 }, ulKhz = c.cellBandwidthUplinkKhz.takeIf { it > 0 },
                    dlFreqKhz = c.downlinkFrequencyKhz.takeIf { it > 0 }, ulFreqKhz = c.uplinkFrequencyKhz.takeIf { it > 0 },
                    pci = c.physicalCellId.takeIf { it != PhysicalChannelConfig.PHYSICAL_CELL_ID_UNKNOWN },
                    frequencyRange = null, contextIds = emptyList(),
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshCached() { runCatching { tm.allCellInfo }.getOrNull()?.let { publishCells(it) } }

    companion object {
        private const val TAG = "CellScope/Tel"

        fun networkTypeName(t: Int): String = when (t) {
            TelephonyManager.NETWORK_TYPE_NR -> "NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "T$t"
        }
        fun overrideName(o: Int): String = when (o) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "NR_NSA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "NR_ADVANCED"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE_CA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE_ADV_PRO"
            else -> "NONE"
        }
        fun dataStateName(s: Int) = when (s) {
            TelephonyManager.DATA_CONNECTED -> "CONNECTED"
            TelephonyManager.DATA_CONNECTING -> "CONNECTING"
            TelephonyManager.DATA_DISCONNECTED -> "DISCONNECTED"
            TelephonyManager.DATA_SUSPENDED -> "SUSPENDED"
            TelephonyManager.DATA_DISCONNECTING -> "DISCONNECTING"
            TelephonyManager.DATA_HANDOVER_IN_PROGRESS -> "HANDOVER"
            else -> "?"
        }
        fun dataActivityName(a: Int) = when (a) {
            TelephonyManager.DATA_ACTIVITY_IN -> "▼ IN"
            TelephonyManager.DATA_ACTIVITY_OUT -> "▲ OUT"
            TelephonyManager.DATA_ACTIVITY_INOUT -> "▲▼ INOUT"
            TelephonyManager.DATA_ACTIVITY_DORMANT -> "DORMANT"
            else -> "NONE"
        }
        fun ratFromNetworkType(t: Int): Rat = when (t) {
            TelephonyManager.NETWORK_TYPE_NR -> Rat.NR
            TelephonyManager.NETWORK_TYPE_LTE -> Rat.LTE
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> Rat.WCDMA
            TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> Rat.GSM
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> Rat.TDSCDMA
            else -> Rat.UNKNOWN
        }
    }
}
