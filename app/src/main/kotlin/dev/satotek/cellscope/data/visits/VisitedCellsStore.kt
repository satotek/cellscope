package dev.satotek.cellscope.data.visits

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import dev.satotek.cellscope.data.model.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VisitedCellsStore(context: Context) {
    private val dir = File(context.getExternalFilesDir(null), "visits")
    private val file get() = AtomicFile(File(dir, "visited-cells.json"))
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<VisitedCell>>(emptyList())
    val entries: StateFlow<List<VisitedCell>> = _entries
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed
    private var loaded = false
    private var lastKey: String? = null
    private var lastSample: Pair<String, Long>? = null

    suspend fun reload() = withContext(Dispatchers.IO) { mutex.withLock {
        loaded = false
        runCatching { loadLocked() }.onFailure { _failed.value = true }
    } }

    private fun loadLocked() {
        if (loaded) return
        val merged = linkedMapOf<String, VisitedCell>()
        // Restore may add a second archive file. Merge identities rather than duplicating cells.
        dir.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { input ->
            val root = JSONObject(AtomicFile(input).openRead().bufferedReader().use { it.readText() })
            val rows = root.getJSONArray("cells")
            for (i in 0 until rows.length()) {
                val o = rows.getJSONObject(i)
                val labels = o.getJSONArray("simLabels")
                val entry = VisitedCell(o.getString("key"), o.getString("plmn"), o.getString("rat"), o.getLong("cellId"),
                    o.getString("band"), o.optIntOrNull("pci"), o.optIntOrNull("arfcn"), o.getLong("firstSeen"),
                    o.getLong("lastSeen"), o.optIntOrNull("bestRsrp"), o.getInt("visits"),
                    (0 until labels.length()).map { labels.getString(it) }.toSet(), o.optString("note", ""),
                    o.optLong("modifiedAt", o.getLong("lastSeen")))
                merged[entry.key] = merged[entry.key]?.merge(entry) ?: entry
            }
        }
        _entries.value = merged.values.sortedByDescending { it.lastSeen }
        loaded = true
        _failed.value = false
    }

    /** Only the selected SIM's fresh, registered cell is recorded; neighbours are not visits. */
    suspend fun observe(s: Snapshot) = withContext(Dispatchers.IO) { mutex.withLock {
        runCatching {
            loadLocked()
            val c = s.serving
            val age = c?.let { SystemClock.elapsedRealtimeNanos() - it.timestampNanos }
            if (c?.cellId == null || s.sim == null || s.network.serviceState != "IN_SERVICE" ||
                age == null || age !in 0L until 30_000_000_000L || c.timestampNanos <= 0 || c.mcc == null || c.mnc == null) {
                lastKey = null
                return@runCatching
            }
            val plmn = "${c.mcc}-${c.mnc}"
            val key = "$plmn:${c.rat.name}:${c.cellId}"
            val token = key to c.timestampNanos
            if (lastSample == token && lastKey == key) return@runCatching
            val now = System.currentTimeMillis()
            val old = _entries.value.firstOrNull { it.key == key }
            val next = VisitedCell(key, plmn, c.rat.name, c.cellId, c.bandLabel, c.pci, c.arfcn,
                old?.firstSeen ?: now, now, listOfNotNull(old?.bestRsrp, c.rsrp).maxOrNull(),
                (old?.visits ?: 0) + if (lastKey != key) 1 else 0,
                old?.simLabels.orEmpty() + s.sim.label, old?.note.orEmpty(), now)
            val rows = (listOf(next) + _entries.value.filterNot { it.key == key }).sortedByDescending { it.lastSeen }
            saveLocked(rows)
            _entries.value = rows
            lastKey = key; lastSample = token
            _failed.value = false
        }.onFailure { _failed.value = true }
    } }

    suspend fun setNote(key: String, note: String) = withContext(Dispatchers.IO) { mutex.withLock {
        runCatching {
            loadLocked()
            val rows = _entries.value.map { if (it.key == key) it.copy(note = note.trim(), modifiedAt = System.currentTimeMillis()) else it }
            saveLocked(rows)
            _entries.value = rows
            _failed.value = false
        }.onFailure { _failed.value = true }
    } }

    private fun saveLocked(rows: List<VisitedCell>) {
        dir.mkdirs()
        val json = JSONObject().put("format", 1).put("cells", JSONArray().apply {
            rows.forEach { e -> put(JSONObject().apply {
                put("key", e.key); put("plmn", e.plmn); put("rat", e.rat); put("cellId", e.cellId)
                put("band", e.band); put("pci", e.pci); put("arfcn", e.arfcn)
                put("firstSeen", e.firstSeen); put("lastSeen", e.lastSeen); put("bestRsrp", e.bestRsrp)
                put("visits", e.visits); put("simLabels", JSONArray(e.simLabels.toList())); put("note", e.note)
                put("modifiedAt", e.modifiedAt)
            }) }
        })
        val out = file.startWrite()
        try { out.write(json.toString().toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
}
