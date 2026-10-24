package dev.satotek.cellscope.data

import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.SignalSample

/** Process-lifetime ring buffer so the chart survives Activity / ViewModel recreation. */
object HistoryStore {
    /** Kept by time, not count, so the longest chart range stays full at any poll interval. */
    const val RETENTION_MS = 60 * 60_000L
    // Backstop if timestamps stop advancing; the fastest poll (500 ms) needs 7200 for an hour.
    private const val MAX = 10_000
    private val buf = ArrayDeque<SignalSample>()
    private val evs = ArrayDeque<CellEvent>()

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

    @Synchronized fun add(s: SignalSample) {
        val prev = buf.lastOrNull()
        if (prev != null) eventOrNull(prev, s)?.let {
            evs.addLast(it); while (evs.size > 200) evs.removeFirst()
        }
        buf.addLast(s)
        val cutoff = s.t - RETENTION_MS
        while (buf.size > MAX || buf.first().t < cutoff) buf.removeFirst()
        while (evs.isNotEmpty() && evs.first().t < cutoff) evs.removeFirst()
    }
    @Synchronized fun samples(): List<SignalSample> = buf.toList()
    @Synchronized fun events(): List<CellEvent> = evs.toList()
    @Synchronized fun clear() { buf.clear(); evs.clear() }
}
