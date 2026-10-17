package dev.satotek.cellscope.data.telephony

/** A 5G display override advertises availability; it is not evidence of an NR connection. */
object NrConnection {
    fun state(dataRat: String, override: String, endc: Boolean?, available: Boolean?, restricted: Boolean?,
              servingNr: Boolean = false, inService: Boolean = true): String = when {
        !inService -> "NONE"
        dataRat == "NR" -> "SA"
        servingNr -> "CONNECTED"
        restricted == true -> "RESTRICTED"
        override == "NR_NSA" || override == "NR_ADVANCED" || available == true || endc == true -> "NR_AVAIL"
        available == false && endc == false -> "NONE"
        else -> "UNKNOWN"
    }
}
