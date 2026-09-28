package dev.satotek.cellscope.data.root

import dev.satotek.cellscope.data.model.CarrierComponent
import dev.satotek.cellscope.data.model.DataCall
import dev.satotek.cellscope.data.model.Rat

/**
 * Root-only probes. The most valuable one is `dumpsys telephony.registry`, which prints
 * the framework's cached PhysicalChannelConfig list (per-carrier band / bandwidth /
 * PCC-SCC role) – the same data a priv-app gets from PhysicalChannelConfigListener.
 */
object RootProbe {

    data class RegistryDump(val raw: String, val components: List<CarrierComponent>, val imsRegistered: Boolean?, val imsTransport: String?, val dataCalls: List<DataCall>)

    /** [phoneId] is [dev.satotek.cellscope.data.telephony.SimInfo.slotIndex] (SubscriptionInfo.simSlotIndex). */
    suspend fun telephonyRegistry(phoneId: Int = 0): RegistryDump {
        val r = RootShell.run("dumpsys telephony.registry", 15)
        val raw = r.out.ifBlank { r.err }
        return RegistryDump(raw, parsePhysicalChannels(raw, phoneId), parseImsRegistered(raw, phoneId), parseImsTransport(raw, phoneId), parseDataCalls(raw, phoneId))
    }

    /**
     * mPreciseDataConnectionStates={Pair{1 [ApnSetting] povo IMS, 3, 44054, IMS, …}= state: CONNECTED, transport: WWAN,
     *   id: 403, netId: 104, network type: NR, APN Setting: [ApnSetting] povo IMS, 3, 44054, IMS, , null, , null, null, 0, ims, …,
     *   link properties: {InterfaceName: wwan3 LinkAddresses: [ 2001:…/64 ] DnsAddresses: [ ] PcscfAddresses: [ … ] … MTU: 1440 …},
     *   default QoS: NrQos { fiveQi=5 … }, fail cause: NONE(0x0), …
     * One line per phone. `Pair{1` is the transport, not the phone. The phone is the enclosing
     * `Phone Id=N` block, or an explicit `mPreciseDataConnectionStates[N]` on older builds.
     */
    fun parseDataCalls(raw: String, phoneId: Int = 0): List<DataCall> {
        val line = fieldForPhone(raw, "mPreciseDataConnectionStates", phoneId) ?: return emptyList()
        val chunks = line.split("= state: ").drop(1)
        return chunks.mapNotNull { c ->
            fun field(k: String) = Regex("$k: ([^,]+)").find(c)?.groupValues?.get(1)?.trim()
            val apnFields = Regex("APN Setting: \\[ApnSetting] ([^{]*?), link properties").find(c)?.groupValues?.get(1)
                ?.split(',')?.map { it.trim() } ?: return@mapNotNull null
            val lp = Regex("link properties: \\{(.*?)\\}, default QoS").find(c)?.groupValues?.get(1) ?: ""
            fun list(k: String) = Regex("$k: \\[([^\\]]*)]").find(lp)?.groupValues?.get(1)?.split(',')
                ?.map { it.trim().removePrefix("/") }?.filter { it.isNotBlank() } ?: emptyList()
            val qos = Regex("default QoS: (\\w+Qos \\{[^}]*\\})").find(c)?.groupValues?.get(1)
            DataCall(
                apnName = apnFields.getOrNull(0) ?: "?", apn = apnFields.getOrNull(3) ?: "", types = apnFields.getOrNull(10) ?: "",
                state = c.substringBefore(',').trim(), transport = field("transport") ?: "?",
                networkType = field("network type") ?: "?", netId = field("netId")?.toIntOrNull(),
                iface = Regex("InterfaceName: (\\S+)").find(lp)?.groupValues?.get(1),
                addresses = list("LinkAddresses"), dns = list("DnsAddresses"), pcscf = list("PcscfAddresses"),
                mtu = Regex("MTU: (\\d+)").find(lp)?.groupValues?.get(1)?.toIntOrNull(),
                qos = qos?.let { q ->
                    val id = Regex("(fiveQi|qci)=(\\d+)").find(q)?.let { "${it.groupValues[1]}=${it.groupValues[2]}" }
                    val dl = Regex("downlink=Bandwidth \\{ maxBitrateKbps=(\\d+)").find(q)?.groupValues?.get(1)
                    listOfNotNull(id, dl?.let { if (it.toLong() >= 1_000_000_000L) "maxDL ∞" else "maxDL ${it.toLong() / 1000} Mbps" }).joinToString(" · ")
                },
                failCause = field("fail cause")?.takeIf { !it.startsWith("NONE") },
            )
        }
    }

    /**
     * Lines look like (Android 14–17), inside `Phone Id=N` or indexed as `mPhysicalChannelConfigs[N]`:
     * mPhysicalChannelConfigs[0]=[{mConnectionStatus=PrimaryServing,mCellBandwidthDownlinkKhz=20000,
     *   mCellBandwidthUplinkKhz=20000,mNetworkType=LTE,mFrequencyRange=UNKNOWN,mDownlinkChannelNumber=1850,
     *   mUplinkChannelNumber=19850,mContextIds=[1],mPhysicalCellId=123,mBand=3,mDownlinkFrequency=1860000,
     *   mUplinkFrequency=1765000}, {…}]
     * N is the phoneId. A standby SIM often has an empty list; that is not a parse failure.
     */
    fun parsePhysicalChannels(raw: String, phoneId: Int = 0): List<CarrierComponent> {
        val line = fieldForPhone(raw, "mPhysicalChannelConfigs", phoneId) ?: return emptyList()
        val body = line.substringAfter('=', "")
        val objs = Regex("\\{([^{}]*)\\}").findAll(body).map { it.groupValues[1] }.toList()
        return objs.mapNotNull { obj ->
            val kv = obj.split(',').mapNotNull { part ->
                val i = part.indexOf('='); if (i < 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            // mContextIds=[1, 2] gets split on ',' – recover it separately.
            val ctx = Regex("mContextIds=\\[([^\\]]*)]").find(obj)?.groupValues?.get(1)
                ?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
            fun int(k: String) = kv[k]?.toIntOrNull()?.takeIf { it > 0 }
            val status = kv["mConnectionStatus"] ?: return@mapNotNull null
            CarrierComponent(
                status = status,
                rat = when (kv["mNetworkType"]) { "NR" -> Rat.NR; "LTE" -> Rat.LTE; "UMTS", "HSPA", "HSPAP" -> Rat.WCDMA; "GSM", "EDGE" -> Rat.GSM; else -> Rat.UNKNOWN },
                band = int("mBand"),
                dlArfcn = int("mDownlinkChannelNumber"), ulArfcn = int("mUplinkChannelNumber"),
                dlKhz = int("mCellBandwidthDownlinkKhz"), ulKhz = int("mCellBandwidthUplinkKhz"),
                dlFreqKhz = int("mDownlinkFrequency"), ulFreqKhz = int("mUplinkFrequency"),
                pci = kv["mPhysicalCellId"]?.toIntOrNull()?.takeIf { it in 0..1007 },
                frequencyRange = kv["mFrequencyRange"]?.takeIf { it != "UNKNOWN" },
                contextIds = ctx,
            )
        }
    }

    private fun parseImsRegistered(raw: String, phoneId: Int = 0): Boolean? =
        imsToken(raw, phoneId, "mImsReg(?:istration)?State", "\\d+|REGISTERED|NOT_REGISTERED|REGISTERING")?.let {
            it == "2" || it == "REGISTERED"
        }

    private fun parseImsTransport(raw: String, phoneId: Int = 0): String? =
        imsToken(raw, phoneId, "mImsTransportType", "\\d+|\\w+")?.let {
            when (it) { "1" -> "WWAN"; "2" -> "WLAN"; else -> it }
        }

    /** Phone 0 still accepts the historical unindexed line. Any other phone must match its own block. */
    private fun imsToken(raw: String, phoneId: Int, key: String, value: String): String? {
        Regex("$key\\[$phoneId]\\s*=\\s*($value)").find(raw)?.let { return it.groupValues[1] }
        val block = phoneBlock(raw.lineSequence().toList(), phoneId)
        if (block != null) {
            Regex("$key\\s*=\\s*($value)").find(block.joinToString("\n"))?.let { return it.groupValues[1] }
        }
        if (phoneId != 0) return null
        return Regex("$key(?:\\[0])?\\s*=\\s*($value)").find(raw)?.groupValues?.get(1)
    }

    /**
     * Pick the per-phone line. Current AOSP prints `Phone Id=N` then an unindexed key.
     * Some builds print `key[N]=` instead. A single unindexed line is phone 0, matching the
     * old "first occurrence" parser so a one-SIM dump does not change.
     */
    fun fieldForPhone(raw: String, key: String, phoneId: Int): String? {
        val lines = raw.lineSequence().toList()
        val indexed = Regex("${Regex.escape(key)}\\[$phoneId]=")
        lines.firstOrNull { indexed.containsMatchIn(it) }?.let { return it }
        if (Regex("${Regex.escape(key)}\\[\\d+]=").containsMatchIn(raw)) return null
        val block = phoneBlock(lines, phoneId)
        if (block != null) return block.firstOrNull { it.trimStart().startsWith(key) }
        if (lines.any { phoneHeader.matchEntire(it.trim()) != null }) return null
        return lines.filter { it.trimStart().startsWith(key) }.getOrNull(phoneId)
    }

    private fun phoneBlock(lines: List<String>, phoneId: Int): List<String>? {
        val start = lines.indexOfFirst {
            phoneHeader.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() == phoneId
        }
        if (start < 0) return null
        val out = ArrayList<String>()
        for (i in start + 1 until lines.size) {
            if (phoneHeader.matchEntire(lines[i].trim()) != null) break
            out += lines[i]
        }
        return out
    }

    private val phoneHeader = Regex("""Phone Id=(\d+)\s*""")

    /** Lines tagged with this phone win. Untagged lines stay visible for phone 0 only. */
    private fun lastForPhone(lines: List<String>, phoneId: Int): String? {
        val mark = "[$phoneId]"
        val scoped = lines.filter { it.contains(mark) || it.contains("phoneId=$phoneId") || it.contains("phoneId = $phoneId") }
        if (scoped.isNotEmpty()) return scoped.last()
        return if (phoneId == 0) lines.lastOrNull() else null
    }

    /**
     * What the framework's radio log tells us that the redacted ServiceState no longer does:
     * the NSA state machine (NetworkTypeController) and the IMS registration RAT.
     */
    data class RadioLog(val lines: List<String>, val nrStateMachine: String?, val anchorNrPci: Int?, val nrBands: List<Int>, val imsTech: Int?)

    /** The last N lines of the radio log buffer, filtered to the interesting RIL / framework events. */
    suspend fun radioLog(lines: Int = 1500, phoneId: Int = 0): RadioLog {
        val r = RootShell.run("logcat -b radio -d -t $lines -v time", 15)
        val all = r.out.lines().filter { it.isNotBlank() }
        // NetworkTypeController: [0] NrConnectedAdvancedState: process EVENT_…
        // The bracket is the phoneId. Phone 0 keeps the old match; another phone must be its own.
        val ntc = Regex("NetworkTypeController: \\[$phoneId] (\\w+State): ")
        val state = all.lastOrNull { ntc.containsMatchIn(it) }?.let { ntc.find(it)?.groupValues?.get(1) }
        val anchorLine = lastForPhone(all.filter { it.contains("anchorNrCell=") }, phoneId)
        val anchor = anchorLine?.let { Regex("anchorNrCell=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 0..1007 }
        val nrBands = anchorLine?.let { Regex("nrBands=\\[([^\\]]*)]").find(it)?.groupValues?.get(1) }
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val ims = lastForPhone(all.filter { it.contains("getImsRegistrationTechnology") }, phoneId)
            ?.let { Regex("getImsRegistrationTechnology\\s*=\\s*(-?\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val keys = listOf("UNSOL", "SIGNAL_STRENGTH", "CELL_INFO", "PHYSICAL_CHANNEL", "NetworkTypeController", "ImsRegistration", "DATA_CALL", "RADIO_STATE", "NETWORK_STATE", "getImsRegistrationTechnology", "DcTracker", "DataNetwork")
        val filtered = all.filter { l -> keys.any { l.contains(it, ignoreCase = true) } }
            .filterNot { it.contains("ServiceState updated") || it.contains("onServiceStateChanged") }
        return RadioLog(filtered.ifEmpty { all }.takeLast(200), state, anchor, nrBands, ims)
    }

    /** ImsRegistrationImplBase.REGISTRATION_TECH_* */
    fun imsTechName(t: Int?): String? = when (t) { null -> null; -1 -> "NONE"; 0 -> "LTE"; 1 -> "IWLAN"; 2 -> "CROSS_SIM"; 3 -> "NR"; else -> "T$t" }

    /** NetworkTypeController state → human label (same semantics as the hidden ServiceState.nrState). */
    fun nrStateLabel(machine: String?): String? = when (machine) {
        null -> null
        "NrConnectedAdvancedState" -> "NR_ADV"
        "NrConnectedState" -> "CONNECTED"
        "NotRestrictedRrcConState" -> "NOT_RESTR/CON"
        "NotRestrictedRrcIdleState" -> "NOT_RESTR/IDLE"
        "RestrictedState" -> "RESTRICTED"
        "LegacyState" -> "NONE"
        else -> machine.removeSuffix("State")
    }

    /** Modem / RIL identity props. Shannon (Tensor) exposes these under gsm.* and ro.vendor.*. */
    suspend fun modemProps(): Map<String, String> {
        val r = RootShell.run("getprop | grep -E '^\\[(gsm\\.|ro\\.vendor\\.radio|ro\\.radio|vendor\\.radio|persist\\.radio|ro\\.baseband|ril\\.|vendor\\.ril|ro\\.boot\\.hardware|ro\\.soc)'", 10)
        return r.out.lines().mapNotNull { l ->
            Regex("\\[(.+?)]: \\[(.*)]").find(l)?.let { it.groupValues[1] to it.groupValues[2] }
        }.filter { it.second.isNotBlank() }.sortedBy { it.first }.toMap()
    }
}
