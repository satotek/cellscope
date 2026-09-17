package dev.satotek.cellscope.data

import android.content.Context
import dev.satotek.cellscope.data.model.NbSample
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.model.Snapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Appends one CSV row per serving-cell update, plus a parallel JSONL of SignalSample. */
class LogRecorder(private val context: Context) {
    private val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    var file: File? = null; private set
    var jsonl: File? = null; private set
    val active get() = file != null

    fun start() {
        val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir, "cellscope-$stamp.csv")
        f.writeText("time,lat,lon,rat,override,mcc,mnc,cellId,enb_gnb,sector,pci,tac,arfcn,band,dl_mhz,bw_khz,rsrp,rsrq,sinr,cqi,ta,level,cc_count,nr_state\n")
        val j = File(dir, "cellscope-$stamp.jsonl")
        j.writeText("")
        file = f
        jsonl = j
    }
    fun stop() { file = null; jsonl = null }

    fun append(s: Snapshot, sample: SignalSample) {
        val f = file ?: return
        val c = s.serving ?: return
        val cc = if (s.root.physicalChannels.isNotEmpty()) s.root.physicalChannels.size else 1 + s.secondaries.size
        val row = listOf(
            fmt.format(Date()), s.location?.first ?: "", s.location?.second ?: "",
            c.rat.name, s.network.overrideNetworkType, c.mcc ?: "", c.mnc ?: "", c.cellId ?: "", c.gnbOrEnb ?: "", c.sectorId ?: "",
            c.pci ?: "", c.tac ?: "", c.arfcn ?: "", c.bandLabel, c.dlMhz ?: "", c.bandwidthKhz ?: "",
            c.rsrp ?: "", c.rsrq ?: "", c.sinr ?: "", c.cqi ?: "", c.ta ?: "", c.level, cc, s.network.nrState,
        ).joinToString(",")
        f.appendText(row + "\n")
        jsonl?.appendText(sampleJson(s, sample) + "\n")
    }

    private fun sampleJson(s: Snapshot, smp: SignalSample): String = JSONObject().apply {
        put("t", smp.t)
        put("rat", smp.rat.name)
        put("band", smp.band)
        putOptInt("pci", smp.pci)
        putOptInt("rsrp", smp.rsrp)
        putOptInt("rsrq", smp.rsrq)
        putOptInt("sinr", smp.sinr)
        putOptInt("nrRsrp", smp.nrRsrp)
        putOptInt("nrRsrq", smp.nrRsrq)
        putOptInt("nrSinr", smp.nrSinr)
        putOptInt("nrPci", smp.nrPci)
        putOptLong("rxBps", smp.rxBps)
        putOptLong("txBps", smp.txBps)
        put("ccCount", smp.ccCount)
        put("ccLte", smp.ccLte)
        put("ccNr", smp.ccNr)
        smp.rttMs?.let { put("rttMs", it) }
        put("neighbours", JSONArray().also { arr -> smp.neighbours.forEach { arr.put(nbJson(it)) } })
        s.location?.let { put("lat", it.first); put("lon", it.second) }
    }.toString()

    private fun nbJson(n: NbSample) = JSONObject().apply {
        put("rat", n.rat.name)
        put("band", n.band)
        putOptInt("pci", n.pci)
        putOptInt("arfcn", n.arfcn)
        putOptInt("rsrp", n.rsrp)
        putOptInt("rsrq", n.rsrq)
        putOptInt("sinr", n.sinr)
    }

    private fun JSONObject.putOptInt(k: String, v: Int?) { if (v != null) put(k, v) }
    private fun JSONObject.putOptLong(k: String, v: Long?) { if (v != null) put(k, v) }
}
