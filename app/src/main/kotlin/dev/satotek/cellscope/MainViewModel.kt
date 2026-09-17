package dev.satotek.cellscope

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.TrafficStats
import android.graphics.Bitmap
import dev.satotek.cellscope.data.HistoryStore
import dev.satotek.cellscope.data.LogRecorder
import dev.satotek.cellscope.data.Pinger
import dev.satotek.cellscope.data.model.NbSample
import dev.satotek.cellscope.data.model.ccSplit
import dev.satotek.cellscope.data.model.PingStats
import dev.satotek.cellscope.data.model.PrivilegeLevel
import dev.satotek.cellscope.data.model.RootState
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.root.PrivAppInstaller
import dev.satotek.cellscope.data.root.RootProbe
import dev.satotek.cellscope.data.root.RootShell
import dev.satotek.cellscope.data.snapshot.OsmTiles
import dev.satotek.cellscope.data.snapshot.SnapshotWriter
import dev.satotek.cellscope.data.telephony.CellMapper
import dev.satotek.cellscope.data.telephony.RatLock
import dev.satotek.cellscope.data.telephony.TelephonyRepository
import dev.satotek.cellscope.overlay.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TelephonyRepository(app)
    private val recorder = LogRecorder(app)
    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _pollIntervalMs = MutableStateFlow(prefs.getLong("poll_ms", 1000L))
    val pollIntervalMs: StateFlow<Long> = _pollIntervalMs
    val recording get() = recorder.active
    val logFile get() = recorder.file

    private var pollJob: Job? = null
    private var rootJob: Job? = null
    private var lastRx = -1L; private var lastTx = -1L; private var lastTrafficT = 0L

    private val _pingHost = MutableStateFlow(prefs.getString("ping_host", null) ?: "1.1.1.1")
    val pingHost: StateFlow<String> = _pingHost
    private val _pingEnabled = MutableStateFlow(prefs.getBoolean("ping_on", true))
    val pingEnabled: StateFlow<Boolean> = _pingEnabled
    @Volatile private var lastRtt: Double? = null
    private var pingJob: Job? = null

    // ---- priv-app self-install ------------------------------------------------------------
    private val _privApp = MutableStateFlow<PrivAppInstaller.State?>(null)
    val privApp: StateFlow<PrivAppInstaller.State?> = _privApp
    private val _privAppError = MutableStateFlow<String?>(null)
    val privAppError: StateFlow<String?> = _privAppError

    fun refreshPrivApp() = viewModelScope.launch {
        if (_state.value.root.available == true) _privApp.value = PrivAppInstaller.state(repo.isPrivileged())
    }
    fun installPrivApp() = viewModelScope.launch {
        val v = runCatching { getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).versionName }.getOrNull() ?: "0"
        _privAppError.value = PrivAppInstaller.install(getApplication(), v)
        refreshPrivApp()
    }
    fun uninstallPrivApp() = viewModelScope.launch { _privAppError.value = PrivAppInstaller.uninstall(); refreshPrivApp() }
    fun rebootDevice() = viewModelScope.launch { PrivAppInstaller.reboot() }

    // ---- RAT lock (priv-app) & connectivity toggles (root) -------------------------------------
    private val _ratLock = MutableStateFlow<RatLock.Mode?>(null)
    val ratLock: StateFlow<RatLock.Mode?> = _ratLock
    private val _ratLockRaw = MutableStateFlow<Long?>(null)
    val ratLockRaw: StateFlow<Long?> = _ratLockRaw
    private val _ratLockError = MutableStateFlow<String?>(null)
    val ratLockError: StateFlow<String?> = _ratLockError

    fun refreshRatLock() {
        val raw = repo.allowedNetworkTypes()
        _ratLockRaw.value = raw; _ratLock.value = raw?.let { RatLock.Mode.of(it) }
    }
    fun setRatLock(mode: RatLock.Mode) = viewModelScope.launch {
        _ratLockError.value = repo.setRatLock(mode)
        refreshRatLock()
    }

    private val _wifiOn = MutableStateFlow(false)
    val wifiOn: StateFlow<Boolean> = _wifiOn
    private val _airplaneOn = MutableStateFlow(false)
    val airplaneOn: StateFlow<Boolean> = _airplaneOn
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting

    @Suppress("DEPRECATION")
    fun refreshConnectivity() {
        val app = getApplication<Application>()
        _wifiOn.value = runCatching { (app.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled }.getOrDefault(false)
        _airplaneOn.value = android.provider.Settings.Global.getInt(app.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    }
    fun setWifi(on: Boolean) = viewModelScope.launch {
        RootShell.run("svc wifi ${if (on) "enable" else "disable"}", 5); delay(500); refreshConnectivity()
    }
    fun setAirplane(on: Boolean) = viewModelScope.launch {
        RootShell.run("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}", 5); delay(500); refreshConnectivity()
    }
    /** Airplane on → off: forces a fresh registration so cell reselection / RAT lock effects can be observed. */
    fun reconnect() = viewModelScope.launch {
        _reconnecting.value = true
        RootShell.run("cmd connectivity airplane-mode enable", 5); delay(3000)
        RootShell.run("cmd connectivity airplane-mode disable", 5); delay(500)
        refreshConnectivity(); _reconnecting.value = false
    }

    fun setPingHost(h: String) { _pingHost.value = h.trim(); prefs.edit().putString("ping_host", h.trim()).apply() }
    fun setPingEnabled(on: Boolean) { _pingEnabled.value = on; prefs.edit().putBoolean("ping_on", on).apply(); if (on) startPing() else { pingJob?.cancel(); lastRtt = null } }

    /** Home gesture enters PiP. Default on; the header icon still works when this is off. */
    private val _pipAuto = MutableStateFlow(prefs.getBoolean("pip_auto", true))
    val pipAuto: StateFlow<Boolean> = _pipAuto
    fun setPipAuto(on: Boolean) { _pipAuto.value = on; prefs.edit().putBoolean("pip_auto", on).apply() }

    private val _overlayEnabled = MutableStateFlow(prefs.getBoolean("overlay", false))
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled
    fun setOverlayEnabled(on: Boolean) {
        _overlayEnabled.value = on
        prefs.edit().putBoolean("overlay", on).apply()
        val app = getApplication<Application>()
        if (on) OverlayService.start(app) else OverlayService.stop(app)
    }
    /** After returning from the overlay-permission screen: start if granted, otherwise drop the pref. */
    fun reconcileOverlay() {
        val app = getApplication<Application>()
        val want = prefs.getBoolean("overlay", false)
        val can = android.provider.Settings.canDrawOverlays(app)
        if (want && can) {
            _overlayEnabled.value = true
            OverlayService.start(app)
        } else if (want && !can) {
            _overlayEnabled.value = false
            prefs.edit().putBoolean("overlay", false).apply()
            OverlayService.stop(app)
        }
    }

    private val _hudPage = MutableStateFlow(HudPage.SERVING)
    val hudPage: StateFlow<HudPage> = _hudPage
    fun cycleHud() { _hudPage.value = _hudPage.value.next() }

    // ---- snapshot card ----------------------------------------------------------------------
    private val _snapshotState = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
    val snapshotState: StateFlow<SnapshotState> = _snapshotState
    private val _snapshotRender = MutableStateFlow<SnapshotRenderRequest?>(null)
    val snapshotRender: StateFlow<SnapshotRenderRequest?> = _snapshotRender

    fun takeSnapshot() {
        if (_snapshotState.value is SnapshotState.Capturing) return
        viewModelScope.launch {
            _snapshotState.value = SnapshotState.Capturing
            val snap = _state.value
            val takenAt = System.currentTimeMillis()
            val map = withTimeoutOrNull(5_000) {
                val loc = snap.location ?: return@withTimeoutOrNull null
                withContext(Dispatchers.IO) { OsmTiles.fetch(getApplication(), loc.first, loc.second) }
            }
            _snapshotRender.value = SnapshotRenderRequest(snap, map, takenAt)
        }
    }

    fun onSnapshotCaptured(bitmap: Bitmap) {
        val req = _snapshotRender.value ?: return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    SnapshotWriter.write(getApplication(), bitmap, req.snapshot, req.takenAt)
                }
            }
            _snapshotRender.value = null
            _snapshotState.value = result.fold(
                onSuccess = { SnapshotState.Done(it) },
                onFailure = { SnapshotState.Error(it.message ?: "failed") },
            )
        }
    }

    fun onSnapshotFailed(msg: String) {
        _snapshotRender.value = null
        _snapshotState.value = SnapshotState.Error(msg)
    }

    fun ackSnapshotState() {
        val s = _snapshotState.value
        if (s is SnapshotState.Done || s is SnapshotState.Error) _snapshotState.value = SnapshotState.Idle
    }

    /** gNB identifier length used to split NCI into gNB / cell (operator-specific, 22..32). */
    private val _gnbBits = MutableStateFlow(prefs.getInt("gnb_bits", 24))
    val gnbBits: StateFlow<Int> = _gnbBits
    fun setGnbBits(bits: Int) {
        val b = bits.coerceIn(22, 32)
        _gnbBits.value = b; CellMapper.gnbBits = b; prefs.edit().putInt("gnb_bits", b).apply()
        viewModelScope.launch { repo.refresh() }
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (isActive) {
                val t0 = System.currentTimeMillis()
                lastRtt = Pinger.probe(_pingHost.value)
                delay((_pollIntervalMs.value - (System.currentTimeMillis() - t0)).coerceAtLeast(200L))
            }
        }
    }

    init {
        CellMapper.gnbBits = _gnbBits.value
        viewModelScope.launch { repo.cells.collect { cells -> _state.update { it.copy(cells = cells) } } }
        viewModelScope.launch { repo.network.collect { n -> _state.update { it.copy(network = n) } } }
        viewModelScope.launch {
            repo.physicalChannels.collect { pcc ->
                if (pcc.isNotEmpty()) _state.update { it.copy(root = it.root.copy(physicalChannels = pcc), privilege = PrivilegeLevel.PRIV_APP) }
            }
        }
    }

    fun onPermissionsResult() {
        val ok = repo.hasPermissions()
        _state.update { it.copy(permissionsGranted = ok, privilege = if (repo.isPrivileged()) PrivilegeLevel.PRIV_APP else it.privilege) }
        if (ok) { repo.start(); startPolling(); startLocation(); if (_pingEnabled.value && pingJob == null) startPing() }
        refreshRatLock(); refreshConnectivity()
    }

    fun setPollInterval(ms: Long) { _pollIntervalMs.value = ms.coerceIn(500L, 10_000L); prefs.edit().putLong("poll_ms", _pollIntervalMs.value).apply(); startPolling() }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            // Sample on a fixed clock, not on change events – StateFlow dedups identical cell lists.
            while (isActive) { repo.refresh(); delay(_pollIntervalMs.value); onTick() }
        }
    }

    private fun onTick() {
        val s = _state.value
        val c = s.serving
        val now = System.currentTimeMillis()
        // NR SCC in NSA: the strongest NR cell that isn't the serving one.
        val nr = if (c?.rat == dev.satotek.cellscope.data.model.Rat.NR) null
            else (s.secondaries + s.neighbours).filter { it.rat == dev.satotek.cellscope.data.model.Rat.NR }
                .let { l -> l.firstOrNull { it in s.secondaries } ?: l.firstOrNull() }
        // Mobile throughput from TrafficStats deltas.
        var rx = TrafficStats.getMobileRxBytes(); var tx = TrafficStats.getMobileTxBytes()
        if (rx <= 0L) { val (r, t) = sysfsMobileBytes(); rx = r; tx = t }
        var rxBps: Long? = null; var txBps: Long? = null
        if (lastRx >= 0 && now > lastTrafficT) {
            val dt = (now - lastTrafficT) / 1000.0
            rxBps = ((rx - lastRx) * 8 / dt).toLong().coerceAtLeast(0); txBps = ((tx - lastTx) * 8 / dt).toLong().coerceAtLeast(0)
        }
        lastRx = rx; lastTx = tx; lastTrafficT = now
        val (ccLte, ccNr) = if (s.root.physicalChannels.isNotEmpty()) ccSplit(s.root.physicalChannels) else {
            val all = listOfNotNull(c) + s.secondaries
            all.count { it.rat != dev.satotek.cellscope.data.model.Rat.NR } to all.count { it.rat == dev.satotek.cellscope.data.model.Rat.NR }
        }
        val cc = (ccLte + ccNr).coerceAtLeast(1)
        val sample = SignalSample(
            t = now, rat = c?.rat ?: dev.satotek.cellscope.data.model.Rat.UNKNOWN, band = c?.bandLabel ?: "—", pci = c?.pci,
            rsrp = c?.rsrp, rsrq = c?.rsrq, sinr = c?.sinr,
            nrRsrp = nr?.rsrp, nrRsrq = nr?.rsrq, nrSinr = nr?.sinr, nrPci = nr?.pci,
            rxBps = rxBps, txBps = txBps, ccCount = cc, ccLte = ccLte, ccNr = ccNr,
            neighbours = (s.secondaries + s.neighbours).map { NbSample(it.rat, it.bandLabel, it.pci, it.arfcn, it.rsrp, it.rsrq, it.sinr) },
            rttMs = if (_pingEnabled.value) lastRtt else null,
        )
        HistoryStore.add(sample)
        _state.update { it.copy(history = HistoryStore.samples(), events = HistoryStore.events(), lastUpdate = now, updateCount = it.updateCount + 1) }
        if (recorder.active) recorder.append(_state.value, sample)
    }

    /** Fallback when TrafficStats reports nothing for mobile: sum the modem netdevs (Shannon = wwanN, others = rmnetN/ccmniN). */
    private fun sysfsMobileBytes(): Pair<Long, Long> {
        var rx = 0L; var tx = 0L
        java.io.File("/sys/class/net").listFiles()?.filter { it.name.startsWith("wwan") || it.name.startsWith("rmnet") || it.name.startsWith("ccmni") }?.forEach { d ->
            rx += runCatching { java.io.File(d, "statistics/rx_bytes").readText().trim().toLong() }.getOrDefault(0L)
            tx += runCatching { java.io.File(d, "statistics/tx_bytes").readText().trim().toLong() }.getOrDefault(0L)
        }
        return rx to tx
    }

    fun clearHistory() { HistoryStore.clear(); _state.update { it.copy(history = emptyList(), events = emptyList()) } }

    // ---- root -------------------------------------------------------------------------------

    fun probeRoot() = viewModelScope.launch {
        val ok = RootShell.available()
        _state.update { it.copy(root = it.root.copy(available = ok)) }
        if (ok) { setRootEnabled(true); refreshPrivApp() }
    }

    fun setRootEnabled(on: Boolean) {
        _state.update { it.copy(root = it.root.copy(enabled = on)) }
        rootJob?.cancel()
        if (!on) { _state.update { if (it.privilege == PrivilegeLevel.ROOT) it.copy(privilege = PrivilegeLevel.NORMAL) else it }; return }
        rootJob = viewModelScope.launch {
            val props = RootProbe.modemProps()
            _state.update { it.copy(root = it.root.copy(modemProps = props)) }
            var n = 0
            while (isActive) {
                runCatching {
                    val reg = RootProbe.telephonyRegistry()
                    val log = if (n % 2 == 0) RootProbe.radioLog() else null
                    _state.update {
                        it.copy(
                            root = it.root.copy(
                                physicalChannels = reg.components.ifEmpty { it.root.physicalChannels },
                                dataCalls = reg.dataCalls,
                                physicalChannelsRaw = reg.raw.lineSequence().firstOrNull { l -> l.contains("mPhysicalChannelConfigs") } ?: "",
                                radioLog = log?.lines ?: it.root.radioLog, lastError = null,
                            ),
                            network = it.network.copy(
                                imsRegistered = log?.imsTech?.let { t -> t >= 0 } ?: it.network.imsRegistered,
                                imsTransport = RootProbe.imsTechName(log?.imsTech) ?: it.network.imsTransport,
                                nrState = RootProbe.nrStateLabel(log?.nrStateMachine) ?: it.network.nrState,
                                nrStateSource = if (log?.nrStateMachine != null) "root" else it.network.nrStateSource,
                                nrAnchorPci = log?.anchorNrPci ?: it.network.nrAnchorPci,
                            ),
                            privilege = if (it.privilege == PrivilegeLevel.PRIV_APP) it.privilege else PrivilegeLevel.ROOT,
                        )
                    }
                }.onFailure { e -> _state.update { it.copy(root = it.root.copy(lastError = e.message)) } }
                n++
                delay((_pollIntervalMs.value * 2).coerceAtLeast(2000L))
            }
        }
    }

    // ---- recording / location ----------------------------------------------------------------

    private val _recordingFlow = MutableStateFlow(false)
    val recordingFlow: StateFlow<Boolean> = _recordingFlow
    fun toggleRecording(): Boolean {
        if (recorder.active) recorder.stop() else recorder.start()
        _recordingFlow.value = recorder.active
        return recorder.active
    }

    private val locListener = LocationListener { loc: Location ->
        _state.update {
            it.copy(
                location = loc.latitude to loc.longitude,
                locationAccuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
            )
        }
    }

    @SuppressLint("MissingPermission")
    /** Stop everything that would otherwise keep running after the activity is gone (process-wide VM). */
    fun shutdown() {
        pollJob?.cancel(); rootJob?.cancel(); pingJob?.cancel()
        pollJob = null; rootJob = null; pingJob = null
        if (recorder.active) { recorder.stop(); _recordingFlow.value = false }
        runCatching { lm.removeUpdates(locListener) }
        repo.stop()
        if (_overlayEnabled.value) setOverlayEnabled(false)
    }

    private fun startLocation() {
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, locListener)
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { l ->
                _state.update {
                    it.copy(
                        location = l.latitude to l.longitude,
                        locationAccuracyM = if (l.hasAccuracy()) l.accuracy else null,
                    )
                }
            }
        }
    }

    override fun onCleared() { repo.stop(); runCatching { lm.removeUpdates(locListener) } }
}

enum class HudPage { SERVING, THROUGHPUT;
    fun next() = if (this == SERVING) THROUGHPUT else SERVING
}

sealed class SnapshotState {
    data object Idle : SnapshotState()
    data object Capturing : SnapshotState()
    data class Done(val file: File) : SnapshotState()
    data class Error(val msg: String) : SnapshotState()
}

data class SnapshotRenderRequest(val snapshot: Snapshot, val map: Bitmap?, val takenAt: Long)
