package dev.satotek.cellscope

import dev.satotek.cellscope.data.HistoryStore
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.ui.components.ChartWindows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {
    private fun sample(t: Long, pci: Int? = 1, passive: Boolean = false) =
        SignalSample(t, Rat.LTE, "B3", pci, -90, -10, 10, passive = passive)

    @After fun clear() = HistoryStore.clear()

    @Test fun fastPollingStillKeepsTheFullHour() {
        // 500 ms is the shortest poll interval; a count cap of 3600 used to cut this to 30 minutes.
        for (i in 0..7200) HistoryStore.add(1, sample(i * 500L))
        val kept = HistoryStore.samples(1)
        assertEquals(HistoryStore.RETENTION_MS, kept.last().t - kept.first().t)
    }

    @Test fun samplesAndEventsOlderThanTheRetentionAreDropped() {
        HistoryStore.add(1, sample(0, pci = 1))
        HistoryStore.add(1, sample(1_000, pci = 2))
        HistoryStore.add(1, sample(1_000 + HistoryStore.RETENTION_MS + 1, pci = 2))
        assertEquals(1, HistoryStore.samples(1).size)
        assertEquals(0, HistoryStore.events(1).size)
    }

    @Test fun eachSimKeepsItsOwnSamplesAndEvents() {
        HistoryStore.add(1, sample(0, pci = 1))
        HistoryStore.add(2, sample(0, pci = 9))
        HistoryStore.add(1, sample(1_000, pci = 1))
        // Interleaving SIMs must not read as a cell change on either one.
        assertEquals(2, HistoryStore.samples(1).size)
        assertEquals(1, HistoryStore.samples(2).size)
        assertTrue(HistoryStore.events(1).isEmpty() && HistoryStore.events(2).isEmpty())
    }

    @Test fun passiveStretchesNeverProduceCellChanges() {
        HistoryStore.add(1, sample(0, pci = 1))
        HistoryStore.add(1, sample(1_000, pci = null, passive = true))
        HistoryStore.add(1, sample(2_000, pci = 1))
        assertTrue(HistoryStore.events(1).isEmpty())
    }

    @Test fun removedSimsLoseTheirHistory() {
        HistoryStore.add(1, sample(0))
        HistoryStore.add(2, sample(0))
        HistoryStore.retain(setOf(2))
        assertTrue(HistoryStore.samples(1).isEmpty())
        assertEquals(1, HistoryStore.samples(2).size)
    }

    @Test fun longestChartRangeMatchesTheRetention() {
        assertEquals(HistoryStore.RETENTION_MS, ChartWindows.minutes.last() * 60_000L)
    }
}
