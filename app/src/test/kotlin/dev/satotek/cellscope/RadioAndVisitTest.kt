package dev.satotek.cellscope

import dev.satotek.cellscope.data.telephony.NrConnection
import dev.satotek.cellscope.data.visits.VisitedCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    private fun cell(key: String = "440-54:LTE:123", first: Long = 10, last: Long = 20,
                     best: Int? = -100, visits: Int = 2, note: String = "", edited: Long = last) =
        VisitedCell(key, "440-54", "LTE", 123, "B3", 10, 1300, first, last, best, visits,
            setOf("SIM"), note, edited)

    @Test fun overlappingBackupsKeepHistoryWithoutDoubleCountingVisits() {
        val combined = cell().merge(cell(first = 5, last = 30, best = -90, visits = 3))
        assertEquals(5L, combined.firstSeen)
        assertEquals(30L, combined.lastSeen)
        assertEquals(-90, combined.bestRsrp)
        assertEquals(3, combined.visits)
        assertEquals(combined, combined.merge(combined))
    }
    @Test fun removingANoteIsNotUndoneByAnOldBackup() {
        val old = cell(note = "old note", edited = 30)
        val cleared = cell(note = "", edited = 40)
        assertEquals("", old.merge(cleared).note)
        assertEquals("", cleared.merge(old).note)
    }
    @Test fun differentNetworkIdentitiesCannotBeMerged() {
        assertThrows(IllegalArgumentException::class.java) { cell().merge(cell(key = "440-20:LTE:123")) }
    }
}
