package dev.satotek.cellscope.data.replay

import dev.satotek.cellscope.data.model.SignalSample
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.tan

/** One logged position with the serving RSRP measured at that moment. */
data class TrackPoint(val t: Long, val lat: Double, val lon: Double, val rsrp: Int?)

/** Where a drive was, in Web Mercator "world" units: 256 per tile edge at the given zoom. */
object Track {
    const val TILE = 256.0
    const val MAX_ZOOM = 18

    fun points(data: ReplayData): List<TrackPoint> = points(data.samples)

    fun points(samples: List<SignalSample>): List<TrackPoint> = samples.mapNotNull { s ->
        val lat = s.lat ?: return@mapNotNull null
        TrackPoint(s.t, lat, s.lon ?: return@mapNotNull null, s.rsrp)
    }

    fun project(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val n = (1 shl zoom) * TILE
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        return (lon + 180.0) / 360.0 * n to (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
    }

    /** The deepest zoom at which the whole track fits in [width] × [height] world units. */
    fun fitZoom(points: List<TrackPoint>, width: Double, height: Double): Int {
        if (points.size < 2) return 16
        val (x0, y0, x1, y1) = bounds(points, 0)
        val spanX = (x1 - x0).coerceAtLeast(1e-9); val spanY = (y1 - y0).coerceAtLeast(1e-9)
        // Each zoom level doubles the span, so the fit is the log of the room left over.
        val z = floor(minOf(log2(width / spanX), log2(height / spanY))).toInt()
        return z.coerceIn(2, MAX_ZOOM)
    }

    /** minX, minY, maxX, maxY in world units at [zoom]. */
    fun bounds(points: List<TrackPoint>, zoom: Int): List<Double> {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (p in points) {
            val (x, y) = project(p.lat, p.lon, zoom)
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        return listOf(minX, minY, maxX, maxY)
    }
}
