package dev.satotek.cellscope.data.snapshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * OpenStreetMap raster tiles for the snapshot card. Fetches a 5×3 neighbourhood around
 * the current location at z=16, recentres on the lat/lon, and crops to 1080×540.
 * 5 columns because the composite is scaled ×1.4 to 1080 wide: with the location anywhere
 * in the centre tile the crop still needs ≥540 px on each side (3 columns leave a gap).
 */
object OsmTiles {
    private const val ZOOM = 16
    private const val TILE = 256
    private const val COLS = 5
    private const val ROWS = 3
    private const val COMPOSITE_W = TILE * COLS
    private const val COMPOSITE_H = TILE * ROWS
    const val OUT_W = 1080
    const val OUT_H = 540
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val TIMEOUT_MS = 5_000

    suspend fun fetch(context: Context, lat: Double, lon: Double): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val ua = userAgent(context)
            val (xf, yf) = latLonToTile(lat, lon, ZOOM)
            val tx = floor(xf).toInt()
            val ty = floor(yf).toInt()
            val tiles = coroutineScope {
                List(COLS * ROWS) { i ->
                    val dx = i % COLS - COLS / 2
                    val dy = i / COLS - ROWS / 2
                    async { loadTile(context, ZOOM, tx + dx, ty + dy, ua) }
                }.awaitAll()
            }
            if (tiles.any { it == null }) return@withContext null
            val composite = Bitmap.createBitmap(COMPOSITE_W, COMPOSITE_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(composite)
            tiles.forEachIndexed { i, bmp ->
                val dx = i % COLS
                val dy = i / COLS
                canvas.drawBitmap(bmp!!, (dx * TILE).toFloat(), (dy * TILE).toFloat(), null)
            }
            val locX = (COLS / 2) * TILE + (xf - tx) * TILE
            val locY = (ROWS / 2) * TILE + (yf - ty) * TILE
            val scale = OUT_W.toFloat() / (TILE * 3)   // same magnification as before (3 tiles → 1080 px)
            val out = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
            val outCanvas = Canvas(out)
            outCanvas.drawColor(0xFF1C1B1F.toInt())
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(OUT_W / 2f - locX.toFloat() * scale, OUT_H / 2f - locY.toFloat() * scale)
            }
            outCanvas.drawBitmap(composite, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            composite.recycle()
            out
        }.getOrNull()
    }

    private fun userAgent(context: Context): String {
        val v = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"
        return "CellScope/$v (dev.satotek.cellscope)"
    }

    private fun loadTile(context: Context, z: Int, x: Int, y: Int, ua: String): Bitmap? {
        val n = 1 shl z
        if (y < 0 || y >= n) return null
        val wx = ((x % n) + n) % n
        val cache = File(File(context.cacheDir, "osm/$z/$wx"), "$y.png")
        if (cache.isFile && System.currentTimeMillis() - cache.lastModified() < TTL_MS) {
            BitmapFactory.decodeFile(cache.absolutePath)?.let { return it }
        }
        val url = "https://tile.openstreetmap.org/$z/$wx/$y.png"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept", "image/png")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            cache.parentFile?.mkdirs()
            val tmp = File(cache.parentFile, "${cache.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(cache)) {
                cache.writeBytes(bytes)
                tmp.delete()
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun latLonToTile(lat: Double, lon: Double, z: Int): Pair<Double, Double> {
        val n = (1 shl z).toDouble()
        val x = (lon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
        return x to y
    }
}
