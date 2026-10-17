package dev.satotek.cellscope.data.model

import android.os.SystemClock
import dev.satotek.cellscope.data.telephony.NrConnection

/** Root radio logs can describe old transitions; only live radio evidence proves a connection. */
fun Snapshot.withNrConnection(): Snapshot {
    val now = SystemClock.elapsedRealtimeNanos()
    val nrCell = cells.any { it.rat == Rat.NR && (it.registered || it.isPrimary || it.isSecondary) &&
        it.timestampNanos > 0 && now - it.timestampNanos in 0L until 30_000_000_000L }
    val channelsCurrent = root.physicalChannelsSource == "callback" ||
        (root.physicalChannelsSource == "root" && SystemClock.elapsedRealtime() - root.physicalChannelsReadAt in 0L until 30_000L)
    val nrChannel = channelsCurrent && root.physicalChannels.any { it.rat == Rat.NR &&
        (it.status.startsWith("Primary") || it.status.startsWith("Secondary")) }
    val nr = NrConnection.state(network.dataNetworkType, network.overrideNetworkType, network.isEnDcAvailable,
        network.isNrAvailable, network.isDcNrRestricted, nrCell || nrChannel,
        network.serviceState !in listOf("OUT_OF_SERVICE", "POWER_OFF", "EMERGENCY_ONLY"))
    return copy(network = network.copy(nrState = nr, nrStateSource = when {
        nr == "SA" -> "data_rat"
        nrChannel -> "physical_channel"
        nrCell -> "cell_info"
        nr == "UNKNOWN" -> "unknown"
        else -> "availability"
    }, nrAnchorPci = network.nrAnchorPci.takeIf { nr == "CONNECTED" || nr == "SA" }))
}
