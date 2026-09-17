package dev.satotek.cellscope.data.replay

import dev.satotek.cellscope.data.HistoryStore
import dev.satotek.cellscope.data.model.NbSample
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.SignalSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class ReplayData(
    val samples: List<SignalSample>,
    val events: List<dev.satotek.cellscope.data.model.CellEvent>,
    val positions: List<Triple<Long, Double, Double>>,
)

object LogReader {
    private val csvTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    suspend fun load(file: File): ReplayData = withContext(Dispatchers.IO) {
        val jsonl = when {
            file.extension.equals("jsonl", true) -> file
            else -> File(file.parentFile, file.nameWithoutExtension + ".jsonl")
        }
        if (jsonl.isFile) loadJsonl(jsonl) else loadCsv(file)
    }

    private fun loadJsonl(file: File): ReplayData {
        val samples = ArrayList<SignalSample>()
        val positions = ArrayList<Triple<Long, Double, Double>>()
        file.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            runCatching {
                val o = JSONObject(line)
                val smp = parseSample(o)
                samples.add(smp)
                if (o.has("lat") && o.has("lon")) {
                    positions.add(Triple(smp.t, o.getDouble("lat"), o.getDouble("lon")))
                }
            }
        }
        return ReplayData(samples, HistoryStore.eventsFrom(samples), positions)
    }

    private fun parseSample(o: JSONObject): SignalSample {
        val nbs = o.optJSONArray("neighbours")
        val neighbours = buildList {
            if (nbs != null) for (i in 0 until nbs.length()) {
                val n = nbs.optJSONObject(i) ?: continue
                add(NbSample(
                    rat = ratOf(n.optString("rat")),
                    band = n.optString("band", "—"),
                    pci = n.optIntOrNull("pci"),
                    arfcn = n.optIntOrNull("arfcn"),
                    rsrp = n.optIntOrNull("rsrp"),
                    rsrq = n.optIntOrNull("rsrq"),
                    sinr = n.optIntOrNull("sinr"),
                ))
            }
        }
        return SignalSample(
            t = o.getLong("t"),
            rat = ratOf(o.optString("rat")),
            band = o.optString("band", "—"),
            pci = o.optIntOrNull("pci"),
            rsrp = o.optIntOrNull("rsrp"),
            rsrq = o.optIntOrNull("rsrq"),
            sinr = o.optIntOrNull("sinr"),
            nrRsrp = o.optIntOrNull("nrRsrp"),
            nrRsrq = o.optIntOrNull("nrRsrq"),
            nrSinr = o.optIntOrNull("nrSinr"),
            nrPci = o.optIntOrNull("nrPci"),
            rxBps = o.optLongOrNull("rxBps"),
            txBps = o.optLongOrNull("txBps"),
            ccCount = o.optInt("ccCount", 1),
            ccLte = o.optInt("ccLte", 0),
            ccNr = o.optInt("ccNr", 0),
            neighbours = neighbours,
            rttMs = if (o.has("rttMs")) o.getDouble("rttMs") else null,
        )
    }

    private fun loadCsv(file: File): ReplayData {
        val samples = ArrayList<SignalSample>()
        val positions = ArrayList<Triple<Long, Double, Double>>()
        var header = true
        file.forEachLine { raw ->
            if (header) { header = false; return@forEachLine }
            val cols = raw.split(',')
            if (cols.size < 23) return@forEachLine
            runCatching {
                val t = csvTime.parse(cols[0].trim())?.time ?: return@runCatching
                val lat = cols[1].trim().toDoubleOrNull()
                val lon = cols[2].trim().toDoubleOrNull()
                val smp = SignalSample(
                    t = t,
                    rat = ratOf(cols[3].trim()),
                    band = cols[13].trim().ifEmpty { "—" },
                    pci = cols[10].trim().toIntOrNull(),
                    rsrp = cols[16].trim().toIntOrNull(),
                    rsrq = cols[17].trim().toIntOrNull(),
                    sinr = cols[18].trim().toIntOrNull(),
                    ccCount = cols[22].trim().toIntOrNull() ?: 1,
                )
                samples.add(smp)
                if (lat != null && lon != null) positions.add(Triple(t, lat, lon))
            }
        }
        return ReplayData(samples, HistoryStore.eventsFrom(samples), positions)
    }

    private fun ratOf(name: String): Rat = runCatching { Rat.valueOf(name) }.getOrDefault(Rat.UNKNOWN)

    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
}
