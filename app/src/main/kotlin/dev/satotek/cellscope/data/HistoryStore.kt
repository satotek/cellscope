package dev.satotek.cellscope.data

import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.SignalSample

/**
 * Process-lifetime ring buffers, one per SIM, so the chart survives Activity / ViewModel
 * recreation and switching SIMs shows that SIM's own past instead of starting over.
 */
object HistoryStore {
    /** Kept by time, not count, so the longest chart range stays full at any poll interval. */
    const val RETENTION_MS = 60 * 60_000L
    // Backstop if timestamps stop advancing; the fastest poll (500 ms) needs 7200 for an hour.
    private const val MAX = 10_000

    private class Buffer {
        val samples = ArrayDeque<SignalSample>()
        val events = ArrayDeque<CellEvent>()
    }
    private val buffers = HashMap<Int, Buffer>()

    /** PCI / band / RAT change between two consecutive samples. Pure; used by live add and log replay. */
    fun eventOrNull(prev: SignalSample, next: SignalSample): CellEvent? {
        if (prev.pci != null && next.pci != null && (prev.pci != next.pci || prev.band != next.band || prev.rat != next.rat)) {
            return CellEvent(next.t, prev, next)
        }
        return null
    }

    fun eventsFrom(samples: List<SignalSample>): List<CellEvent> = buildList {
        samples.zipWithNext { a, b -> eventOrNull(a, b)?.let { add(it) } }
    }

    @Synchronized fun add(subId: Int, s: SignalSample) {
        val b = buffers.getOrPut(subId) { Buffer() }
        val prev = b.samples.lastOrNull()
        if (prev != null) eventOrNull(prev, s)?.let {
            b.events.addLast(it); while (b.events.size > 200) b.events.removeFirst()
        }
        b.samples.addLast(s)
        val cutoff = s.t - RETENTION_MS
        while (b.samples.size > MAX || b.samples.first().t < cutoff) b.samples.removeFirst()
        while (b.events.isNotEmpty() && b.events.first().t < cutoff) b.events.removeFirst()
    }
    @Synchronized fun samples(subId: Int): List<SignalSample> = buffers[subId]?.samples?.toList().orEmpty()
    @Synchronized fun events(subId: Int): List<CellEvent> = buffers[subId]?.events?.toList().orEmpty()
    /** Drops the history of SIMs that are no longer active. */
    @Synchronized fun retain(subIds: Set<Int>) { buffers.keys.retainAll(subIds) }
    @Synchronized fun clear() { buffers.clear() }
}
