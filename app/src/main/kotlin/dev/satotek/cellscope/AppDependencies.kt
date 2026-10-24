package dev.satotek.cellscope

import android.content.Context
import dev.satotek.cellscope.data.DeviceRepository
import dev.satotek.cellscope.data.LogRecorder
import dev.satotek.cellscope.data.telephony.SimRepository
import dev.satotek.cellscope.data.telephony.SimSummaryMonitor
import dev.satotek.cellscope.data.telephony.TelephonyRepository
import dev.satotek.cellscope.data.visits.VisitedCellsStore
import dev.satotek.cellscope.data.location.LocationRepository
import dev.satotek.cellscope.data.settings.SettingsStore
import dev.satotek.cellscope.data.update.UpdateRepository

/** Owns shared data sources independently of Compose and activity recreation. */
class AppDependencies(context: Context) {
    private val app = context.applicationContext
    val device = DeviceRepository(app)
    val sims = SimRepository(app)
    val simSummaries = SimSummaryMonitor(app)
    val recorder = LogRecorder(app)
    val visits = VisitedCellsStore(app)
    fun telephony(subId: Int) = TelephonyRepository(app, subId)

    val settings = SettingsStore(app)
    val location = LocationRepository(app)
    val updates = UpdateRepository(app)
    val updateInstaller = dev.satotek.cellscope.data.update.UpdateInstaller(app)
}
