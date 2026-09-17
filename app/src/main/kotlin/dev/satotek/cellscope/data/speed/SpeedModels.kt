package dev.satotek.cellscope.data.speed

import org.json.JSONObject

enum class SpeedPhase { LOCATE, DOWNLOAD, UPLOAD, DONE, ERROR }

data class SpeedProgress(
    val phase: SpeedPhase,
    val elapsedMs: Long = 0L,
    val mbps: Double? = null,
    val avgMbps: Double? = null,
    val minRttMs: Double? = null,
    val server: String? = null,
    val viaCellular: Boolean = true,
    val error: String? = null,
    val result: SpeedResult? = null,
)

data class SpeedResult(
    val t: Long,
    val dlMbps: Double,
    val ulMbps: Double,
    val minRttMs: Double?,
    val server: String,
    val rat: String,
    val band: String,
    val pci: Int?,
    val rsrp: Int?,
    val viaCellular: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", t)
        put("dlMbps", dlMbps)
        put("ulMbps", ulMbps)
        if (minRttMs != null) put("minRttMs", minRttMs)
        put("server", server)
        put("rat", rat)
        put("band", band)
        if (pci != null) put("pci", pci)
        if (rsrp != null) put("rsrp", rsrp)
        put("viaCellular", viaCellular)
    }

    companion object {
        fun fromJson(o: JSONObject) = SpeedResult(
            t = o.getLong("t"),
            dlMbps = o.getDouble("dlMbps"),
            ulMbps = o.getDouble("ulMbps"),
            minRttMs = if (o.has("minRttMs")) o.getDouble("minRttMs") else null,
            server = o.optString("server", ""),
            rat = o.optString("rat", "—"),
            band = o.optString("band", "—"),
            pci = if (o.has("pci")) o.getInt("pci") else null,
            rsrp = if (o.has("rsrp")) o.getInt("rsrp") else null,
            viaCellular = o.optBoolean("viaCellular", true),
        )
    }
}
