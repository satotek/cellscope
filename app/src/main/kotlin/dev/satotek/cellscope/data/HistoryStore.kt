package dev.satotek.cellscope.data

import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.SignalSample

/** Process-lifetime ring buffer so the chart survives Activity / ViewModel recreation. */
object HistoryStore {
    private const val MAX = 3600 // 1 h at 1 s
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
        buf.addLast(s); while (buf.size > MAX) buf.removeFirst()
    }
    @Synchronized fun samples(): List<SignalSample> = buf.toList()
    @Synchronized fun events(): List<CellEvent> = evs.toList()
    @Synchronized fun clear() { buf.clear(); evs.clear() }
}
