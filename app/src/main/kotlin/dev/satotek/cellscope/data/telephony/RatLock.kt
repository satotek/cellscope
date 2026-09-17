package dev.satotek.cellscope.data.telephony

import android.annotation.SuppressLint
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_GPRS
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_GSM
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_HSPA
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_HSPAP
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_LTE
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_NR
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_TD_SCDMA
import android.telephony.TelephonyManager.NETWORK_TYPE_BITMASK_UMTS

/**
 * RAT lock via the "user" allowed-network-types slot. This is the only radio restriction Android exposes:
 * there is no API for band or cell locking (NSG does that through Qualcomm DIAG, which Shannon lacks).
 * Both get and set need MODIFY_PHONE_STATE / READ_PRIVILEGED_PHONE_STATE → priv-app mode only.
 */
object RatLock {
    const val GSM_MASK = NETWORK_TYPE_BITMASK_GSM or NETWORK_TYPE_BITMASK_GPRS or NETWORK_TYPE_BITMASK_EDGE
    const val WCDMA_MASK = NETWORK_TYPE_BITMASK_UMTS or NETWORK_TYPE_BITMASK_HSDPA or NETWORK_TYPE_BITMASK_HSUPA or
        NETWORK_TYPE_BITMASK_HSPA or NETWORK_TYPE_BITMASK_HSPAP or NETWORK_TYPE_BITMASK_TD_SCDMA
    const val LTE_MASK = NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_LTE_CA
    const val NR_MASK = NETWORK_TYPE_BITMASK_NR

    /** Presets shown in Settings. [mask] is what we write; the current mode is matched by which RAT families are present. */
    enum class Mode(val mask: Long) {
        AUTO(NR_MASK or LTE_MASK or WCDMA_MASK or GSM_MASK),
        NR_LTE(NR_MASK or LTE_MASK),
        NR_ONLY(NR_MASK),          // SA only: NSA needs an LTE anchor, so this drops EN-DC entirely
        LTE_ONLY(LTE_MASK),
        LTE_WCDMA(LTE_MASK or WCDMA_MASK),
        WCDMA_ONLY(WCDMA_MASK);

        companion object {
            fun of(mask: Long): Mode? {
                val nr = mask and NR_MASK != 0L; val lte = mask and LTE_MASK != 0L
                val w = mask and WCDMA_MASK != 0L; val g = mask and GSM_MASK != 0L
                return when {
                    nr && lte && (w || g) -> AUTO
                    nr && lte -> NR_LTE
                    nr && !lte && !w && !g -> NR_ONLY
                    !nr && lte && !w && !g -> LTE_ONLY
                    !nr && lte && w -> LTE_WCDMA
                    !nr && !lte && w -> WCDMA_ONLY
                    else -> null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun current(tm: TelephonyManager): Long? =
        runCatching { tm.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER) }.getOrNull()

    /** Returns null on success, else the failure. */
    @SuppressLint("MissingPermission")
    fun apply(tm: TelephonyManager, mode: Mode): String? =
        runCatching { tm.setAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, mode.mask) }
            .exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
}
