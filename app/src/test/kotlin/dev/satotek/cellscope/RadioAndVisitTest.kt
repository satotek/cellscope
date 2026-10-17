package dev.satotek.cellscope

import dev.satotek.cellscope.data.telephony.NrConnection
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioAndVisitTest {
    @Test fun displayOverridesDoNotProveAnNrConnection() {
        for (display in listOf("NR_NSA", "NR_ADVANCED")) {
            assertEquals("NR_AVAIL", NrConnection.state("LTE", display, true, true, false))
        }
    }
    @Test fun liveRadioEvidenceDistinguishesNsaAndSa() {
        assertEquals("CONNECTED", NrConnection.state("LTE", "NR_NSA", true, true, false, servingNr = true))
        assertEquals("SA", NrConnection.state("NR", "NONE", null, null, null))
    }
    @Test fun absentEvidenceRemainsUnknown() {
        assertEquals("UNKNOWN", NrConnection.state("LTE", "NONE", null, null, null))
        assertEquals("NONE", NrConnection.state("LTE", "NONE", false, false, false))
    }
    @Test fun outOfServiceAndRestrictedAreNotShownAsConnected() {
        assertEquals("NONE", NrConnection.state("NR", "NR_NSA", true, true, false, true, inService = false))
        assertEquals("RESTRICTED", NrConnection.state("LTE", "NR_NSA", true, true, true))
    }
}
