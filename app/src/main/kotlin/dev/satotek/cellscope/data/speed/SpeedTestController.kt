package dev.satotek.cellscope.data.speed

import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

interface SpeedResultStore {
    fun load(): List<SpeedResult>
    fun save(results: List<SpeedResult>)
}

/** Shares one measurement session across the activity and overlay hosts. */
class SpeedTestController(
    private val scope: CoroutineScope,
    private val store: SpeedResultStore,
    private val snapshot: () -> Snapshot,
    private val server: () -> String,
    private val elapsedRealtimeNanos: () -> Long,
    private val measure: (String?, CellEntry?, Int?) -> Flow<SpeedProgress>,
) {
    private val _speedState = MutableStateFlow<SpeedProgress?>(null)
    val speedState: StateFlow<SpeedProgress?> = _speedState
    private val _speedResults = MutableStateFlow(store.load())
    val speedResults: StateFlow<List<SpeedResult>> = _speedResults
    private var speedJob: Job? = null

    fun start() {
        if (speedJob?.isActive == true) return
        val startSnapshot = snapshot()
        val serving = startSnapshot.serving?.takeIf {
            it.timestampNanos > 0 && elapsedRealtimeNanos() - it.timestampNanos in 0L until 30_000_000_000L
        }
        val server = server().ifBlank { null }
        speedJob = scope.launch {
            measure(server, serving, startSnapshot.sim?.subId).collect { p ->
                _speedState.value = p
                p.result?.let { r ->
                    val measuredSim = startSnapshot.sims.firstOrNull { it.subId == r.subId }
                        ?: snapshot().sims.firstOrNull { it.subId == r.subId }
                    val stamped = r.copy(simLabel = measuredSim?.label)
                    _speedState.value = p.copy(result = stamped)
                    val next = (listOf(stamped) + _speedResults.value).take(50)
                    _speedResults.value = next
                    store.save(next)
                }
            }
        }
    }
    fun cancel() {
        speedJob?.cancel()
        speedJob = null
        _speedState.value = null
    }
    fun delete(r: SpeedResult) {
        val next = _speedResults.value.filter { it.t != r.t }
        _speedResults.value = next
        store.save(next)
    }
    fun writeSpeedCsv(dir: File): File {
        val f = File(dir, "speed.csv")
        val rows = _speedResults.value
        val sb = StringBuilder("time,dl_mbps,ul_mbps,min_rtt_ms,server,rat,band,pci,rsrp,via_cellular,sub,sim_label\n")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        rows.asReversed().forEach { r ->
            sb.append(fmt.format(java.util.Date(r.t))).append(',')
            sb.append(r.dlMbps).append(',').append(r.ulMbps).append(',')
            sb.append(r.minRttMs ?: "").append(',')
            sb.append('"').append(r.server.replace("\"", "'")).append('"').append(',')
            sb.append(r.rat).append(',').append(r.band).append(',')
            sb.append(r.pci ?: "").append(',').append(r.rsrp ?: "").append(',')
            sb.append(r.viaCellular).append(',')
            sb.append(r.subId ?: "").append(',')
            sb.append(csvField(r.simLabel ?: "")).append('\n')
        }
        f.writeText(sb.toString())
        return f
    }
    fun reload() { _speedResults.value = store.load() }

    private fun csvField(s: String): String {
        if (s.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }
}
