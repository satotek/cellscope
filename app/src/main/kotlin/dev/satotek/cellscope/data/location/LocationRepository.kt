package dev.satotek.cellscope.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager

data class LocationReading(val coordinates: Pair<Double, Double>, val accuracyM: Float?)

class LocationRepository(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    fun start(onLocation: (LocationReading) -> Unit) {
        stop()
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        fun deliver(location: Location) = onLocation(LocationReading(
            location.latitude to location.longitude,
            if (location.hasAccuracy()) location.accuracy else null,
        ))
        val next = LocationListener { deliver(it) }
        listener = next
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, next)
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { deliver(it) }
        } catch (_: SecurityException) {
            stop()
        } catch (_: IllegalArgumentException) {
            // GPS is optional on devices that otherwise provide telephony data.
            stop()
        }
    }

    fun stop() {
        listener?.let { current -> runCatching { manager.removeUpdates(current) } }
        listener = null
    }
}
