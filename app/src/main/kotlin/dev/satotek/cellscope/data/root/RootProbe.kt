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

    suspend fun telephonyRegistry(): RegistryDump {
        val r = RootShell.run("dumpsys telephony.registry", 15)
        val raw = r.out.ifBlank { r.err }
        return RegistryDump(raw, parsePhysicalChannels(raw), parseImsRegistered(raw), parseImsTransport(raw), parseDataCalls(raw))
    }

    /**
     * mPreciseDataConnectionStates={Pair{1 [ApnSetting] povo IMS, 3, 44054, IMS, …}= state: CONNECTED, transport: WWAN,
     *   id: 403, netId: 104, network type: NR, APN Setting: [ApnSetting] povo IMS, 3, 44054, IMS, , null, , null, null, 0, ims, …,
     *   link properties: {InterfaceName: wwan3 LinkAddresses: [ 2001:…/64 ] DnsAddresses: [ ] PcscfAddresses: [ … ] … MTU: 1440 …},
     *   default QoS: NrQos { fiveQi=5 … }, fail cause: NONE(0x0), …
     * Only the slot-0 line is parsed (first occurrence).
     */
    fun parseDataCalls(raw: String): List<DataCall> {
        val line = raw.lineSequence().firstOrNull { it.trimStart().startsWith("mPreciseDataConnectionStates") } ?: return emptyList()
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
     * Lines look like (Android 14–17):
     * mPhysicalChannelConfigs[0]=[{mConnectionStatus=PrimaryServing,mCellBandwidthDownlinkKhz=20000,
     *   mCellBandwidthUplinkKhz=20000,mNetworkType=LTE,mFrequencyRange=UNKNOWN,mDownlinkChannelNumber=1850,
     *   mUplinkChannelNumber=19850,mContextIds=[1],mPhysicalCellId=123,mBand=3,mDownlinkFrequency=1860000,
     *   mUplinkFrequency=1765000}, {…}]
     */
    fun parsePhysicalChannels(raw: String): List<CarrierComponent> {
        val line = raw.lineSequence().firstOrNull { it.trimStart().startsWith("mPhysicalChannelConfigs") } ?: return emptyList()
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

    private fun parseImsRegistered(raw: String): Boolean? =
        Regex("mImsReg(?:istration)?State(?:\\[0])?\\s*=\\s*(\\d+|REGISTERED|NOT_REGISTERED|REGISTERING)").find(raw)?.groupValues?.get(1)?.let {
            it == "2" || it == "REGISTERED"
        }

    private fun parseImsTransport(raw: String): String? =
        Regex("mImsTransportType(?:\\[0])?\\s*=\\s*(\\d+|\\w+)").find(raw)?.groupValues?.get(1)?.let {
            when (it) { "1" -> "WWAN"; "2" -> "WLAN"; else -> it }
        }

    /**
     * What the framework's radio log tells us that the redacted ServiceState no longer does:
     * the NSA state machine (NetworkTypeController) and the IMS registration RAT.
     */
    data class RadioLog(val lines: List<String>, val nrStateMachine: String?, val anchorNrPci: Int?, val nrBands: List<Int>, val imsTech: Int?)

    /** The last N lines of the radio log buffer, filtered to the interesting RIL / framework events. */
    suspend fun radioLog(lines: Int = 1500): RadioLog {
        val r = RootShell.run("logcat -b radio -d -t $lines -v time", 15)
        val all = r.out.lines().filter { it.isNotBlank() }
        // NetworkTypeController: [0] NrConnectedAdvancedState: process EVENT_…
        val ntc = Regex("NetworkTypeController: \\[0] (\\w+State): ")
        val state = all.lastOrNull { ntc.containsMatchIn(it) }?.let { ntc.find(it)?.groupValues?.get(1) }
        val anchorLine = all.lastOrNull { it.contains("anchorNrCell=") }
        val anchor = anchorLine?.let { Regex("anchorNrCell=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 0..1007 }
        val nrBands = anchorLine?.let { Regex("nrBands=\\[([^\\]]*)]").find(it)?.groupValues?.get(1) }
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val ims = all.lastOrNull { it.contains("getImsRegistrationTechnology") }
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
