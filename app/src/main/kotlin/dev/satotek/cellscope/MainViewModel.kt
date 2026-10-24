package dev.satotek.cellscope

import dev.satotek.cellscope.data.model.withNrConnection
import kotlinx.coroutines.channels.Channel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.TrafficStats
import android.graphics.Bitmap
import dev.satotek.cellscope.data.HistoryStore
import dev.satotek.cellscope.data.Pinger
import dev.satotek.cellscope.data.model.NbSample
import dev.satotek.cellscope.data.model.NetworkState
import dev.satotek.cellscope.data.model.ccSplit
import dev.satotek.cellscope.data.model.PrivilegeLevel
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.root.PrivAppInstaller
import dev.satotek.cellscope.data.root.RootProbe
import dev.satotek.cellscope.data.root.RootShell
import dev.satotek.cellscope.data.speed.SpeedTestController
import dev.satotek.cellscope.data.speed.SpeedResult
import dev.satotek.cellscope.data.telephony.CellMapper
import dev.satotek.cellscope.data.telephony.RatLock
import dev.satotek.cellscope.data.telephony.SimInfo
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

/** The SIM list is event-driven; this is only the fallback re-read interval for a DDS switch. */
private const val SIM_REFRESH_MS = 10_000L

class MainViewModel(private val dependencies: AppDependencies) : ViewModel() {

    // Only the selected SIM requests scans; summary listeners are passive on all active SIMs.
    // subId is transient, so every process starts on the default data SIM.
    private val simsRepo = dependencies.sims
    private val summaryMonitor = dependencies.simSummaries
    private val _selectedSub = MutableStateFlow(dependencies.device.defaultDataSubId())
    val selectedSub: StateFlow<Int> = _selectedSub
    private var repo = dependencies.telephony(_selectedSub.value)
    private val repoJobs = ArrayList<Job>()
    private val recorder = dependencies.recorder
    private val locationSource = dependencies.location

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state
    private val visitedStore = dependencies.visits
    val visitedCells = visitedStore.entries
    val visitStoreFailed = visitedStore.failed
    private val visitSamples = Channel<Snapshot>(Channel.CONFLATED)
    fun reloadVisitedCells() = viewModelScope.launch { visitedStore.reload() }
    fun setVisitNote(key: String, note: String) = viewModelScope.launch { visitedStore.setNote(key, note) }

    private val settings = dependencies.settings

    // ---- update check --------------------------------------------------------------------------
    private val updates = dependencies.updates
    val update: StateFlow<dev.satotek.cellscope.data.update.UpdateState> = updates.state
    private val _updateAuto = MutableStateFlow(settings.updateAuto)
    val updateAuto: StateFlow<Boolean> = _updateAuto
    fun checkForUpdates() = viewModelScope.launch { updates.check() }

    private val installer = dependencies.updateInstaller
    val install: StateFlow<dev.satotek.cellscope.data.update.InstallState> = installer.state
    /** Debug builds are another package under another key; a release APK can never update them. */
    val canInstallUpdates = !BuildConfig.DEBUG
    fun installUpdate() {
        val release = (update.value as? dev.satotek.cellscope.data.update.UpdateState.Available)?.latest ?: return
        val route = when {
            _state.value.root.available != true -> dev.satotek.cellscope.data.update.InstallRoute.PACKAGE_INSTALLER
            _privApp.value == PrivAppInstaller.State.ACTIVE -> dev.satotek.cellscope.data.update.InstallRoute.ROOT_PRIVAPP
            else -> dev.satotek.cellscope.data.update.InstallRoute.ROOT
        }
        viewModelScope.launch { installer.install(release, route) }
    }
    fun dismissInstall() = installer.dismiss()
    fun installConfirmationShown() = installer.confirmationShown()
    fun setUpdateAuto(on: Boolean) {
        _updateAuto.value = on; settings.updateAuto = on
        if (on && updates.isDue()) checkForUpdates()
    }

    private val _pollIntervalMs = MutableStateFlow(settings.pollIntervalMs)
    val pollIntervalMs: StateFlow<Long> = _pollIntervalMs
    val recording get() = recorder.active
    val logFile get() = recorder.file

    private var pollJob: Job? = null
    private var rootJob: Job? = null
    private var lastRx = -1L; private var lastTx = -1L; private var lastTrafficT = 0L
    private var lastSimRefresh = 0L

    private val _pingHost = MutableStateFlow(settings.pingHost)
    val pingHost: StateFlow<String> = _pingHost
    private val _pingEnabled = MutableStateFlow(settings.pingEnabled)
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
        _privAppError.value = dependencies.device.installPrivApp()
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

    fun refreshConnectivity() {
        val connectivity = dependencies.device.connectivity()
        _wifiOn.value = connectivity.wifiOn
        _airplaneOn.value = connectivity.airplaneOn
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

    fun setPingHost(h: String) { _pingHost.value = h.trim(); settings.pingHost = h.trim() }
    fun setPingEnabled(on: Boolean) { _pingEnabled.value = on; settings.pingEnabled = on; if (on) startPing() else { pingJob?.cancel(); lastRtt = null } }

    private val _speedServer = MutableStateFlow(settings.speedServer)
    val speedServer: StateFlow<String> = _speedServer
    fun setSpeedServer(s: String) { _speedServer.value = s.trim(); settings.speedServer = s.trim() }

    private val speed = SpeedTestController(
        viewModelScope, settings, { _state.value }, { _speedServer.value },
        android.os.SystemClock::elapsedRealtimeNanos,
        dependencies.device::measureSpeed,
    )
    val speedState = speed.speedState
    val speedResults = speed.speedResults
    fun startSpeedTest() = speed.start()
    fun cancelSpeedTest() = speed.cancel()
    fun deleteSpeedResult(result: SpeedResult) = speed.delete(result)
    fun writeSpeedCsv(dir: File) = speed.writeSpeedCsv(dir)

    /** "system" | "light" | "dark" — read by every CellScopeTheme host (activity, overlay). */
    private val _themeMode = MutableStateFlow(settings.themeMode)
    val themeMode: StateFlow<String> = _themeMode
    fun setThemeMode(mode: String) { _themeMode.value = mode; settings.themeMode = mode }

    /** Home gesture enters PiP. Default on; the header icon still works when this is off. */
    private val _pipAuto = MutableStateFlow(settings.pipAuto)
    val pipAuto: StateFlow<Boolean> = _pipAuto
    fun setPipAuto(on: Boolean) { _pipAuto.value = on; settings.pipAuto = on }

    private val _overlayEnabled = MutableStateFlow(settings.overlayEnabled)
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled
    fun setOverlayEnabled(on: Boolean) {
        _overlayEnabled.value = on
        settings.overlayEnabled = on
        dependencies.device.setOverlayEnabled(on)
    }
    /** After returning from the overlay-permission screen: start if granted, otherwise drop the pref. */
    fun reconcileOverlay() {
        val want = settings.overlayEnabled
        val can = dependencies.device.canDrawOverlays()
        if (want && can) {
            _overlayEnabled.value = true
            dependencies.device.setOverlayEnabled(true)
        } else if (want && !can) {
            _overlayEnabled.value = false
            settings.overlayEnabled = false
            dependencies.device.setOverlayEnabled(false)
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
                withContext(Dispatchers.IO) { dependencies.device.fetchMap(loc.first, loc.second) }
            }
            _snapshotRender.value = SnapshotRenderRequest(snap, map, takenAt)
        }
    }

    fun onSnapshotCaptured(bitmap: Bitmap) {
        val req = _snapshotRender.value ?: return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    dependencies.device.writeSnapshot(bitmap, req.snapshot, req.takenAt)
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
    private val _gnbBits = MutableStateFlow(settings.gnbBits)
    val gnbBits: StateFlow<Int> = _gnbBits
    fun setGnbBits(bits: Int) {
        val b = bits.coerceIn(22, 32)
        _gnbBits.value = b; CellMapper.gnbBits = b; settings.gnbBits = b
        viewModelScope.launch { repo.refresh() }
    }

    /**
     * A restored backup rewrites the prefs file underneath the StateFlows, which were seeded
     * once in the constructor. Push every value back through the normal setters so their side
     * effects run too (polling cadence, ping job, NCI split, overlay service).
     */
    fun reloadPrefs() {
        setPollInterval(settings.pollIntervalMs)
        setPingHost(settings.pingHost)
        setPingEnabled(settings.pingEnabled)
        setSpeedServer(settings.speedServer)
        setThemeMode(settings.themeMode)
        setPipAuto(settings.pipAuto)
        setGnbBits(settings.gnbBits)
        setUpdateAuto(settings.updateAuto)
        speed.reload()
        viewModelScope.launch { visitedStore.reload() }
        reconcileOverlay()   // permission-gated: never start the service blindly
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
        bindRepoFlows()
        viewModelScope.launch { simsRepo.sims.collect { onSims(it) } }
        viewModelScope.launch { summaryMonitor.summaries.collect { summaries -> _state.update { it.copy(simSummaries = summaries) } } }
        if (_updateAuto.value && updates.isDue()) checkForUpdates()
        viewModelScope.launch {
            visitedStore.reload()
            for (sample in visitSamples) visitedStore.observe(sample)
        }
    }

    /** Set once the user touches the selector: their pick then outranks a data-SIM change. */
    private var subPickedByUser = false

    /** Not persisted. Ignored when [subId] is not in the active list. */
    fun setSelectedSub(subId: Int) {
        if (subId == _selectedSub.value) return
        if (_state.value.sims.none { it.subId == subId }) return
        subPickedByUser = true
        applySub(subId)
    }

    private fun onSims(list: List<SimInfo>) {
        if (repo.hasPermissions()) summaryMonitor.sync(list)
        // An empty list is also what arrives before permissions; keep history until a real list says otherwise.
        if (list.isNotEmpty()) HistoryStore.retain(list.map { it.subId }.toSet())
        val selected = _selectedSub.value
        // Switching the data SIM in Settings does not restart this process, so an app the user has
        // not steered would otherwise keep watching the SIM that was default when it launched.
        val dds = list.firstOrNull { it.isDds }
        if (!subPickedByUser && dds != null && dds.subId != selected) {
            _state.update { it.copy(sims = list) }
            applySub(dds.subId)
            return
        }
        val current = list.firstOrNull { it.subId == selected }
        if (current != null) {
            _state.update { it.copy(sims = list, sim = current) }
            return
        }
        val fallback = list.firstOrNull { it.isDds }?.subId
            ?: list.firstOrNull()?.subId
            ?: dependencies.device.defaultDataSubId()
        _state.update { it.copy(sims = list) }
        if (fallback != repo.subId) applySub(fallback)
        else _state.update { it.copy(sim = list.firstOrNull { s -> s.subId == fallback }) }
    }

    private fun applySub(subId: Int) {
        val changed = repo.subId != subId
        _selectedSub.value = subId
        if (changed) rebindRepo(subId)
        val sim = _state.value.sims.firstOrNull { it.subId == subId }
        if (!changed) {
            _state.update { it.copy(sim = sim) }
            return
        }
        // Each SIM keeps its own history, so the chart never stitches two SIMs into a fake handover.
        _state.update {
            it.copy(
                cells = emptyList(),
                network = NetworkState(),
                history = HistoryStore.samples(subId),
                events = HistoryStore.events(subId),
                sim = sim,
                root = it.root.copy(physicalChannels = emptyList(), physicalChannelsRaw = "", physicalChannelsSource = "", physicalChannelsReadAt = 0L, dataCalls = emptyList()),
            )
        }
        if (repo.hasPermissions()) repo.start()
        refreshRatLock()
    }

    private fun rebindRepo(subId: Int) {
        repo.stop()
        repoJobs.forEach { it.cancel() }
        repoJobs.clear()
        repo = dependencies.telephony(subId)
        bindRepoFlows()
    }

    private fun bindRepoFlows() {
        val r = repo
        repoJobs += viewModelScope.launch { r.cells.collect { cells -> _state.update { it.copy(cells = cells).withNrConnection() } } }
        repoJobs += viewModelScope.launch {
            r.network.collect { n ->
                _state.update {
                    val previous = it.network
                    val sameRadio = previous.dataNetworkType == n.dataNetworkType &&
                        previous.overrideNetworkType == n.overrideNetworkType
                    // Public callbacks do not carry the fields supplied by the root probe.
                    it.copy(network = n.copy(
                        imsRegistered = n.imsRegistered ?: previous.imsRegistered,
                        imsTransport = n.imsTransport ?: previous.imsTransport,
                        nrAnchorPci = if (sameRadio) previous.nrAnchorPci else n.nrAnchorPci,
                    )).withNrConnection()
                }
            }
        }
        repoJobs += viewModelScope.launch {
            r.physicalChannels.collect { pcc ->
                if (r.physicalChannelCallbackActive) _state.update {
                    it.copy(root = it.root.copy(physicalChannels = pcc, physicalChannelsSource = "callback", physicalChannelsReadAt = android.os.SystemClock.elapsedRealtime()), privilege = PrivilegeLevel.PRIV_APP).withNrConnection()
                }
            }
        }
    }

    /**
     * Phone 0 is the wrong guess on a device whose only active SIM sits in slot 1 (an eSIM keeps
     * its own phone), so fall back to the data SIM's slot rather than to zero.
     */
    private fun phoneId(): Int = _state.value.sim?.slotIndex
        ?: dependencies.device.defaultDataSlot()
        ?: 0

    fun onPermissionsResult() {
        val ok = repo.hasPermissions()
        _state.update { it.copy(permissionsGranted = ok, privilege = if (repo.isPrivileged()) PrivilegeLevel.PRIV_APP else it.privilege) }
        if (ok) { simsRepo.start(); summaryMonitor.sync(simsRepo.sims.value); repo.start(); startPolling(); startLocation(); if (_pingEnabled.value && pingJob == null) startPing() }
        refreshRatLock(); refreshConnectivity()
    }

    /** The HUD can start from a Quick Settings tile with no activity, which is what normally starts sampling. */
    fun ensureSampling() { if (pollJob == null) onPermissionsResult() }

    fun setPollInterval(ms: Long) { _pollIntervalMs.value = ms.coerceIn(500L, 10_000L); settings.pollIntervalMs = _pollIntervalMs.value; startPolling() }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            // Sample on a fixed clock, not on change events – StateFlow dedups identical cell lists.
            while (isActive) {
                // OnSubscriptionsChangedListener covers add/remove; this slow re-read only catches a
                // default-data-SIM switch, which the listener does not always report.
                val now = System.currentTimeMillis()
                if (now - lastSimRefresh >= SIM_REFRESH_MS) { lastSimRefresh = now; simsRepo.refresh() }
                repo.refresh(); delay(_pollIntervalMs.value); onTick()
            }
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
        val sub = repo.subId
        HistoryStore.add(sub, sample)
        // Standby SIMs get a signal-only sample so switching to one shows its recent past.
        // Throughput and ping are device-wide, so they are copied rather than left as gaps.
        s.sims.filter { it.subId != sub }.forEach { sim ->
            val sum = s.simSummaries[sim.subId] ?: return@forEach
            HistoryStore.add(sim.subId, SignalSample(
                t = now, rat = dev.satotek.cellscope.data.model.Rat.entries.firstOrNull { it.name == sum.rat } ?: dev.satotek.cellscope.data.model.Rat.UNKNOWN,
                band = "—", pci = null, rsrp = sum.rsrp, rsrq = sum.rsrq, sinr = sum.sinr,
                nrRsrp = sum.nrRsrp, nrRsrq = sum.nrRsrq, nrSinr = sum.nrSinr,
                rxBps = rxBps, txBps = txBps, rttMs = sample.rttMs, passive = true,
            ))
        }
        _state.update { it.copy(history = HistoryStore.samples(sub), events = HistoryStore.events(sub), lastUpdate = now, updateCount = it.updateCount + 1).withNrConnection() }
        visitSamples.trySend(_state.value)
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
        if (!on) {
            _state.update {
                it.copy(privilege = if (it.privilege == PrivilegeLevel.ROOT) PrivilegeLevel.NORMAL else it.privilege,
                    root = if (repo.physicalChannelCallbackActive) it.root else it.root.copy(
                        physicalChannels = emptyList(), physicalChannelsSource = "", physicalChannelsReadAt = 0L),
                ).withNrConnection()
            }
            return
        }
        rootJob = viewModelScope.launch {
            val props = RootProbe.modemProps()
            _state.update { it.copy(root = it.root.copy(modemProps = props)) }
            var n = 0
            while (isActive) {
                runCatching {
                    val phoneId = phoneId()
                    val reg = RootProbe.telephonyRegistry(phoneId)
                    val log = if (n % 2 == 0) RootProbe.radioLog(phoneId = phoneId) else null
                    // The shell call outlived a SIM switch. Drop it instead of painting the other phone.
                    if (phoneId() != phoneId) return@runCatching
                    val hasChannelField = RootProbe.fieldForPhone(reg.raw, "mPhysicalChannelConfigs", phoneId) != null
                    val updateChannels = !repo.physicalChannelCallbackActive && hasChannelField
                    _state.update {
                        it.copy(
                            root = it.root.copy(
                                physicalChannels = if (updateChannels) reg.components else it.root.physicalChannels,
                                physicalChannelsSource = if (updateChannels) "root" else it.root.physicalChannelsSource,
                                physicalChannelsReadAt = if (updateChannels) android.os.SystemClock.elapsedRealtime() else it.root.physicalChannelsReadAt,
                                dataCalls = reg.dataCalls,
                                physicalChannelsRaw = RootProbe.fieldForPhone(reg.raw, "mPhysicalChannelConfigs", phoneId) ?: "",
                                radioLog = log?.lines ?: it.root.radioLog, lastError = null,
                            ),
                            network = it.network.copy(
                                imsRegistered = log?.imsTech?.let { t -> t >= 0 } ?: it.network.imsRegistered,
                                imsTransport = RootProbe.imsTechName(log?.imsTech) ?: it.network.imsTransport,
                                nrAnchorPci = log?.anchorNrPci ?: it.network.nrAnchorPci,
                            ),
                            privilege = if (it.privilege == PrivilegeLevel.PRIV_APP) it.privilege else PrivilegeLevel.ROOT,
                        ).withNrConnection()
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

    /** Stop everything that would otherwise keep running after the activity is gone (process-wide VM). */
    fun shutdown() {
        pollJob?.cancel(); rootJob?.cancel(); pingJob?.cancel(); speed.cancel()
        pollJob = null; rootJob = null; pingJob = null
        if (recorder.active) { recorder.stop(); _recordingFlow.value = false }
        locationSource.stop()
        simsRepo.stop()
        summaryMonitor.stop()
        repo.stop()
        if (_overlayEnabled.value) setOverlayEnabled(false)
    }

    private fun startLocation() {
        locationSource.start { location ->
            _state.update { it.copy(location = location.coordinates, locationAccuracyM = location.accuracyM) }
        }
    }

    override fun onCleared() {
        shutdown()
        repoJobs.forEach { it.cancel() }
    }
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
