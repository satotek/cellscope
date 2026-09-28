package dev.satotek.cellscope.data.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Active subscriptions only. ICCID and the phone number are never read: logs from this app are
 * shared and sent to an AI, so a subscriber identifier must not enter state.
 *
 * [subId] is not a stable preference. The list is rebuilt whenever SIMs change, and the UI
 * starts on the default data subscription each process start.
 */
data class SimInfo(
    val subId: Int,
    val slotIndex: Int,
    val portIndex: Int,
    val label: String,
    val isEmbedded: Boolean,
    val isDds: Boolean,
    val isDefaultVoice: Boolean,
)

class SimRepository(private val context: Context) {
    private val sm: SubscriptionManager? = context.getSystemService(SubscriptionManager::class.java)
    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims

    private val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() { refresh() }
    }
    private var listening = false

    fun start() {
        refresh()
        val sm = sm ?: return
        if (listening) return
        sm.addOnSubscriptionsChangedListener(context.mainExecutor, listener)
        listening = true
    }

    fun stop() {
        if (!listening) return
        sm?.removeOnSubscriptionsChangedListener(listener)
        listening = false
    }

    /** Safe to call from the poll loop. A SecurityException keeps the previous list. */
    @SuppressLint("MissingPermission")
    fun refresh() {
        val sm = sm ?: return
        if (!hasPermission()) return
        val infos = try {
            sm.activeSubscriptionInfoList
        } catch (_: SecurityException) {
            return
        } ?: emptyList()
        val dds = SubscriptionManager.getDefaultDataSubscriptionId()
        val voice = SubscriptionManager.getDefaultVoiceSubscriptionId()
        val built = infos.map { info ->
            SimInfo(
                subId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                portIndex = info.portIndex,
                label = baseLabel(info),
                isEmbedded = info.isEmbedded,
                isDds = info.subscriptionId == dds,
                isDefaultVoice = info.subscriptionId == voice,
            )
        }.sortedWith(compareBy(SimInfo::slotIndex, SimInfo::portIndex, SimInfo::subId))
        _sims.value = withDistinctLabels(built)
    }

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun baseLabel(info: SubscriptionInfo): String {
        val display = info.displayName?.toString()?.trim().orEmpty()
        if (display.isNotEmpty()) return display
        val carrier = info.carrierName?.toString()?.trim().orEmpty()
        if (carrier.isNotEmpty()) return carrier
        return "SIM ${info.simSlotIndex + 1}"
    }
}

/**
 * Two profiles often share a carrier name (physical + eSIM, or two eSIM ports).
 * Suffix only the copies so a unique name stays as the user set it.
 */
internal fun withDistinctLabels(sims: List<SimInfo>): List<SimInfo> {
    if (sims.size < 2) return sims
    fun suffix(s: SimInfo) = if (s.isEmbedded) "eSIM ${s.portIndex + 1}" else "SIM ${s.slotIndex + 1}"
    val counts = sims.groupingBy { it.label }.eachCount()
    val once = sims.map { s ->
        if ((counts[s.label] ?: 0) < 2) s else s.copy(label = "${s.label} (${suffix(s)})")
    }
    val again = once.groupingBy { it.label }.eachCount()
    if (again.none { it.value > 1 }) return once
    return once.map { s ->
        if ((again[s.label] ?: 0) < 2) s else s.copy(label = "${s.label} · ${s.slotIndex + 1}")
    }
}
