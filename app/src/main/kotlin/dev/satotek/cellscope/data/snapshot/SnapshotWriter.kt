package dev.satotek.cellscope.data.snapshot

import android.content.ClipData
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.CarrierComponent
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.DataCall
import dev.satotek.cellscope.data.model.NetworkState
import dev.satotek.cellscope.data.model.Snapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SnapshotWriter {
    const val AUTHORITY = "dev.satotek.cellscope.files"

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "snapshots").apply { mkdirs() }

    fun jsonFile(png: File): File = File(png.parentFile, png.nameWithoutExtension + ".json")

    fun write(context: Context, bitmap: Bitmap, snapshot: Snapshot, takenAt: Long): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(takenAt))
        val png = File(dir(context), "cellscope-$stamp.png")
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        jsonFile(png).writeText(toJson(snapshot, takenAt).toString(2))
        runCatching { publishToGallery(context, png, takenAt) }
        return png
    }

    /** Also drop a copy into Pictures/CellScope through MediaStore so it shows up in Photos (scoped storage, no permission). */
    private fun publishToGallery(context: Context, png: File, takenAt: Long) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, png.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CellScope")
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> png.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    fun listPng(context: Context): List<File> =
        dir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".png", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun delete(png: File) {
        jsonFile(png).delete()
        png.delete()
    }

    /**
     * ACTION_SEND carries exactly one Uri in EXTRA_STREAM — receivers (Files, Photos, …) reject an
     * ArrayList there. So the PNG is shared alone; the JSON goes out through [shareJson].
     */
    fun share(context: Context, png: File) = shareOne(context, png, "image/png")

    fun shareJson(context: Context, png: File) {
        val json = jsonFile(png)
        if (json.isFile) shareOne(context, json, "application/json")
    }

    private fun shareOne(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
    }

    fun toJson(s: Snapshot, takenAt: Long): JSONObject = JSONObject().apply {
        put("takenAt", takenAt)
        s.serving?.let { put("serving", cellJson(it)) }
        put("secondaries", JSONArray().also { arr -> s.secondaries.forEach { arr.put(cellJson(it)) } })
        put("neighbours", JSONArray().also { arr -> s.neighbours.forEach { arr.put(cellJson(it)) } })
        put("network", networkJson(s.network))
        put("root", JSONObject().apply {
            put("physicalChannels", JSONArray().also { arr -> s.root.physicalChannels.forEach { arr.put(ccJson(it)) } })
            put("dataCalls", JSONArray().also { arr -> s.root.dataCalls.forEach { arr.put(callJson(it)) } })
        })
        s.location?.let { loc ->
            put("location", JSONObject().apply {
                put("lat", loc.first)
                put("lon", loc.second)
                s.locationAccuracyM?.let { put("accuracyM", it.toDouble()) }
            })
        }
    }

    private fun cellJson(c: CellEntry) = JSONObject().apply {
        put("rat", c.rat.name)
        put("registered", c.registered)
        put("isPrimary", c.isPrimary)
        put("isSecondary", c.isSecondary)
        putOptStr("mcc", c.mcc)
        putOptStr("mnc", c.mnc)
        putOptStr("operator", c.operator)
        putOptLong("cellId", c.cellId)
        putOptLong("gnbOrEnb", c.gnbOrEnb)
        putOptInt("sectorId", c.sectorId)
        putOptInt("pci", c.pci)
        putOptInt("tac", c.tac)
        putOptInt("arfcn", c.arfcn)
        put("bands", JSONArray().also { a -> c.bands.forEach { b -> a.put(b) } })
        putOptInt("bandwidthKhz", c.bandwidthKhz)
        putOptDouble("dlMhz", c.dlMhz)
        putOptDouble("ulMhz", c.ulMhz)
        putOptInt("rssi", c.rssi)
        putOptInt("rsrp", c.rsrp)
        putOptInt("rsrq", c.rsrq)
        putOptInt("sinr", c.sinr)
        putOptInt("cqi", c.cqi)
        putOptInt("ta", c.ta)
        putOptInt("csiRsrp", c.csiRsrp)
        putOptInt("csiRsrq", c.csiRsrq)
        putOptInt("csiSinr", c.csiSinr)
        put("level", c.level)
        putOptInt("asuLevel", c.asuLevel)
        put("timestampNanos", c.timestampNanos)
    }

    private fun networkJson(n: NetworkState) = JSONObject().apply {
        put("serviceState", n.serviceState)
        put("dataNetworkType", n.dataNetworkType)
        put("overrideNetworkType", n.overrideNetworkType)
        put("nrState", n.nrState)
        put("nrStateSource", n.nrStateSource)
        putOptInt("nrAnchorPci", n.nrAnchorPci)
        put("nrFrequencyRange", n.nrFrequencyRange)
        putOptBool("isEnDcAvailable", n.isEnDcAvailable)
        putOptBool("isNrAvailable", n.isNrAvailable)
        putOptBool("isDcNrRestricted", n.isDcNrRestricted)
        putOptBool("vopsSupported", n.vopsSupported)
        putOptBool("usingCarrierAggregation", n.usingCarrierAggregation)
        put("cellBandwidthsKhz", JSONArray().also { a -> n.cellBandwidthsKhz.forEach { a.put(it) } })
        put("roaming", n.roaming)
        put("operatorName", n.operatorName)
        put("simOperator", n.simOperator)
        put("plmn", n.plmn)
        put("dataState", n.dataState)
        put("dataActivity", n.dataActivity)
        putOptBool("imsRegistered", n.imsRegistered)
        putOptStr("imsTransport", n.imsTransport)
        putOptBool("voNrEnabled", n.voNrEnabled)
        put("callState", n.callState)
        if (n.rawServiceState.isNotEmpty()) put("rawServiceState", n.rawServiceState)
    }

    private fun ccJson(c: CarrierComponent) = JSONObject().apply {
        put("status", c.status)
        put("rat", c.rat.name)
        putOptInt("band", c.band)
        putOptInt("dlArfcn", c.dlArfcn)
        putOptInt("ulArfcn", c.ulArfcn)
        putOptInt("dlKhz", c.dlKhz)
        putOptInt("ulKhz", c.ulKhz)
        putOptInt("dlFreqKhz", c.dlFreqKhz)
        putOptInt("ulFreqKhz", c.ulFreqKhz)
        putOptInt("pci", c.pci)
        putOptStr("frequencyRange", c.frequencyRange)
        put("contextIds", JSONArray().also { a -> c.contextIds.forEach { a.put(it) } })
    }

    private fun callJson(d: DataCall) = JSONObject().apply {
        put("apnName", d.apnName)
        put("apn", d.apn)
        put("types", d.types)
        put("state", d.state)
        put("transport", d.transport)
        put("networkType", d.networkType)
        putOptStr("iface", d.iface)
        put("addresses", JSONArray().also { a -> d.addresses.forEach { a.put(it) } })
        put("dns", JSONArray().also { a -> d.dns.forEach { a.put(it) } })
        put("pcscf", JSONArray().also { a -> d.pcscf.forEach { a.put(it) } })
        putOptInt("mtu", d.mtu)
        putOptStr("qos", d.qos)
        putOptStr("failCause", d.failCause)
        putOptInt("netId", d.netId)
    }

    private fun JSONObject.putOptStr(k: String, v: String?) { if (v != null) put(k, v) }
    private fun JSONObject.putOptInt(k: String, v: Int?) { if (v != null) put(k, v) }
    private fun JSONObject.putOptLong(k: String, v: Long?) { if (v != null) put(k, v) }
    private fun JSONObject.putOptDouble(k: String, v: Double?) { if (v != null && v.isFinite()) put(k, v) }
    private fun JSONObject.putOptBool(k: String, v: Boolean?) { if (v != null) put(k, v) }
}
