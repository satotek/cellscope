package dev.satotek.cellscope.data.telephony

import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.data.model.Rat

/** Converts Android's CellInfo hierarchy into our flat, null-normalised CellEntry. */
object CellMapper {
    /** gNB identifier length in bits (TS 38.413: 22..32). Set from Settings; 24 is the usual choice in JP/EU, 22 in the US. */
    @Volatile var gnbBits: Int = 24
        set(v) { field = v.coerceIn(22, 32) }


    private fun Int.orNull(): Int? = if (this == CellInfo.UNAVAILABLE || this == Int.MAX_VALUE || this == Int.MIN_VALUE) null else this
    private fun Long.orNull(): Long? = if (this == CellInfo.UNAVAILABLE_LONG || this == Long.MAX_VALUE) null else this

    fun map(info: CellInfo): CellEntry? = when (info) {
        is CellInfoNr -> nr(info)
        is CellInfoLte -> lte(info)
        is CellInfoWcdma -> wcdma(info)
        is CellInfoGsm -> gsm(info)
        is CellInfoTdscdma -> tdscdma(info)
        else -> null
    }

    private fun connection(info: CellInfo): Pair<Boolean, Boolean> {
        val s = info.cellConnectionStatus
        return (s == CellInfo.CONNECTION_PRIMARY_SERVING) to (s == CellInfo.CONNECTION_SECONDARY_SERVING)
    }

    private fun nr(info: CellInfoNr): CellEntry {
        val id = info.cellIdentity as CellIdentityNr
        val ss = info.cellSignalStrength as CellSignalStrengthNr
        val (p, s) = connection(info)
        val arfcn = id.nrarfcn.orNull()
        val f = BandTables.nr(arfcn)
        val bands = id.bands.toList().ifEmpty { listOfNotNull(f.band) }
        val nci = id.nci.orNull()
        // NCI = gNB id (22..32 bits, operator-specific) + cell id (the remaining 36 - n bits).
        val cellBits = 36 - gnbBits
        val gnb = nci?.let { it shr cellBits }
        val sector = nci?.let { (it and ((1L shl cellBits) - 1)).toInt() }
        val ta = runCatching { ss.timingAdvanceMicros.orNull() }.getOrNull()
        return CellEntry(
            rat = Rat.NR, registered = info.isRegistered, isPrimary = p, isSecondary = s,
            mcc = id.mccString, mnc = id.mncString, operator = id.operatorAlphaLong?.toString(),
            cellId = nci, gnbOrEnb = gnb, sectorId = sector,
            pci = id.pci.orNull(), tac = id.tac.orNull(), arfcn = arfcn, bands = bands,
            bandwidthKhz = null, dlMhz = f.dlMhz, ulMhz = null,
            rssi = null, rsrp = ss.ssRsrp.orNull(), rsrq = ss.ssRsrq.orNull(), sinr = ss.ssSinr.orNull(),
            cqi = runCatching { ss.csiCqiReport.firstOrNull()?.orNull() }.getOrNull(),
            ta = ta,
            csiRsrp = ss.csiRsrp.orNull(), csiRsrq = ss.csiRsrq.orNull(), csiSinr = ss.csiSinr.orNull(),
            level = ss.level, asuLevel = ss.asuLevel.orNull(), timestampNanos = info.timestampMillis * 1_000_000,
        )
    }

    private fun lte(info: CellInfoLte): CellEntry {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val (p, s) = connection(info)
        val earfcn = id.earfcn.orNull()
        val f = BandTables.lte(earfcn)
        val bands = id.bands.toList().ifEmpty { listOfNotNull(f.band) }
        val eci = id.ci.orNull()?.toLong()
        return CellEntry(
            rat = Rat.LTE, registered = info.isRegistered, isPrimary = p, isSecondary = s,
            mcc = id.mccString, mnc = id.mncString, operator = id.operatorAlphaLong?.toString(),
            cellId = eci, gnbOrEnb = eci?.let { it shr 8 }, sectorId = eci?.let { (it and 0xFF).toInt() },
            pci = id.pci.orNull(), tac = id.tac.orNull(), arfcn = earfcn, bands = bands,
            bandwidthKhz = id.bandwidth.orNull(), dlMhz = f.dlMhz, ulMhz = f.ulMhz,
            rssi = ss.rssi.orNull(), rsrp = ss.rsrp.orNull(), rsrq = ss.rsrq.orNull(), sinr = ss.rssnr.orNull(),
            cqi = ss.cqi.orNull(), ta = ss.timingAdvance.orNull(),
            csiRsrp = null, csiRsrq = null, csiSinr = null,
            level = ss.level, asuLevel = ss.asuLevel.orNull(), timestampNanos = info.timestampMillis * 1_000_000,
        )
    }

    private fun wcdma(info: CellInfoWcdma): CellEntry {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val (p, s) = connection(info)
        val uarfcn = id.uarfcn.orNull()
        val f = BandTables.wcdma(uarfcn)
        val cid = id.cid.orNull()?.toLong()
        return CellEntry(
            rat = Rat.WCDMA, registered = info.isRegistered, isPrimary = p, isSecondary = s,
            mcc = id.mccString, mnc = id.mncString, operator = id.operatorAlphaLong?.toString(),
            cellId = cid, gnbOrEnb = cid?.let { it shr 16 }, sectorId = cid?.let { (it and 0xFFFF).toInt() },
            pci = id.psc.orNull(), tac = id.lac.orNull(), arfcn = uarfcn, bands = listOfNotNull(f.band),
            bandwidthKhz = 5000, dlMhz = f.dlMhz, ulMhz = null,
            rssi = null, rsrp = ss.dbm.orNull(), rsrq = null, sinr = null,
            cqi = null, ta = null, csiRsrp = null, csiRsrq = null,
            csiSinr = runCatching { ss.ecNo.orNull() }.getOrNull(),
            level = ss.level, asuLevel = ss.asuLevel.orNull(), timestampNanos = info.timestampMillis * 1_000_000,
        )
    }

    private fun gsm(info: CellInfoGsm): CellEntry {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val (p, s) = connection(info)
        return CellEntry(
            rat = Rat.GSM, registered = info.isRegistered, isPrimary = p, isSecondary = s,
            mcc = id.mccString, mnc = id.mncString, operator = id.operatorAlphaLong?.toString(),
            cellId = id.cid.orNull()?.toLong(), gnbOrEnb = null, sectorId = null,
            pci = id.bsic.orNull(), tac = id.lac.orNull(), arfcn = id.arfcn.orNull(), bands = emptyList(),
            bandwidthKhz = 200, dlMhz = null, ulMhz = null,
            rssi = ss.rssi.orNull(), rsrp = ss.dbm.orNull(), rsrq = null, sinr = null,
            cqi = null, ta = ss.timingAdvance.orNull(), csiRsrp = null, csiRsrq = null, csiSinr = null,
            level = ss.level, asuLevel = ss.asuLevel.orNull(), timestampNanos = info.timestampMillis * 1_000_000,
        )
    }

    private fun tdscdma(info: CellInfoTdscdma): CellEntry {
        val id = info.cellIdentity
        val ss = info.cellSignalStrength
        val (p, s) = connection(info)
        return CellEntry(
            rat = Rat.TDSCDMA, registered = info.isRegistered, isPrimary = p, isSecondary = s,
            mcc = id.mccString, mnc = id.mncString, operator = id.operatorAlphaLong?.toString(),
            cellId = id.cid.orNull()?.toLong(), gnbOrEnb = null, sectorId = null,
            pci = id.cpid.orNull(), tac = id.lac.orNull(), arfcn = id.uarfcn.orNull(), bands = emptyList(),
            bandwidthKhz = 1600, dlMhz = null, ulMhz = null,
            rssi = null, rsrp = ss.dbm.orNull(), rsrq = null, sinr = null,
            cqi = null, ta = null, csiRsrp = null, csiRsrq = null, csiSinr = null,
            level = ss.level, asuLevel = ss.asuLevel.orNull(), timestampNanos = info.timestampMillis * 1_000_000,
        )
    }
}
