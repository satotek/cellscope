package dev.satotek.cellscope.data

import dev.satotek.cellscope.BuildConfig
import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.root.PrivAppInstaller
import dev.satotek.cellscope.data.snapshot.OsmTiles
import dev.satotek.cellscope.data.snapshot.SnapshotWriter
import dev.satotek.cellscope.data.speed.Ndt7Client
import dev.satotek.cellscope.overlay.OverlayService

/** Keeps Android context and platform entry points out of the presentation layer. */
class DeviceRepository(context: Context) {
    private val app = context.applicationContext

    data class Connectivity(val wifiOn: Boolean, val airplaneOn: Boolean)

    fun connectivity() = Connectivity(
        runCatching { app.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false),
        Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
    )

    suspend fun installPrivApp(): String? {
        return PrivAppInstaller.install(app, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
    }

    fun canDrawOverlays() = Settings.canDrawOverlays(app)
    fun setOverlayEnabled(on: Boolean) {
        if (on) OverlayService.start(app) else OverlayService.stop(app)
    }

    fun defaultDataSubId(): Int = runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }
        .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
    fun defaultDataSlot(): Int? = SubscriptionManager.getSlotIndex(defaultDataSubId()).takeIf { it >= 0 }

    fun measureSpeed(server: String?, serving: CellEntry?, subId: Int?) = Ndt7Client.run(app, server, serving, subId)
    suspend fun fetchMap(latitude: Double, longitude: Double) = OsmTiles.fetch(app, latitude, longitude)
    fun writeSnapshot(bitmap: Bitmap, snapshot: Snapshot, takenAt: Long) = SnapshotWriter.write(app, bitmap, snapshot, takenAt)
}
