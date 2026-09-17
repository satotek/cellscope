package dev.satotek.cellscope.data.stats

import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.data.model.SignalSample
import kotlin.math.min

data class BandShare(val label: String, val rat: Rat, val ms: Long)
data class DistBin(val mid: Int, val ms: Long)
data class TripleStat(val max: Double?, val avg: Double?, val p95: Double?)
data class Percentiles(val p10: Double, val median: Double, val p90: Double)
data class QualityShare(val poorMs: Long, val fairMs: Long, val goodMs: Long) {
    val total: Long get() = poorMs + fairMs + goodMs
}
data class NeighbourShare(val label: String, val avgRsrp: Double?, val ms: Long)

data class StatsAgg(
    val bands: List<BandShare>,
    val bandTotal: Long,
    val kindCounts: Map<String, Int>,
    val recent: List<CellEvent>,
    val rsrp: List<DistBin>,
    val sinr: List<DistBin>,
    val caLte: LongArray, // 1CC, 2CC, 3CC, 4CC+
    val caNr: LongArray,  // none, 1CC, 2CC+
    val caTotal: Long,
    val rx: TripleStat,
    val tx: TripleStat,
    val rtt: TripleStat,
    val rttLossPct: Double?,
    val sampleCount: Int,
    val rangeStart: Long,
    val rangeEnd: Long,
    val nrStateMs: Map<String, Long>,
    val rsrpPct: Percentiles?,
    val rsrqPct: Percentiles?,
    val sinrPct: Percentiles?,
    val nrRsrpPct: Percentiles?,
    val nrRsrqPct: Percentiles?,
    val nrSinrPct: Percentiles?,
    val rsrpQuality: QualityShare,
    val rsrqQuality: QualityShare,
    val sinrQuality: QualityShare,
    val neighbours: List<NeighbourShare>,
)

object StatsAggregator {
    fun aggregate(history: List<SignalSample>, events: List<CellEvent>, windowMs: Long): StatsAgg {
        val lastT = history.lastOrNull()?.t ?: 0L
        return aggregate(history, events, (lastT - windowMs)..lastT)
    }

    fun aggregate(history: List<SignalSample>, events: List<CellEvent>, range: LongRange): StatsAgg {
        val weighted = timeWeighted(history, range)
        val visEvents = events.filter { it.t in range }

        val bandMs = LinkedHashMap<String, Pair<Rat, Long>>()
        val rsrp = LongArray(rsrpBins)
        val sinr = LongArray(sinrBins)
        val caLte = LongArray(4)
        val caNr = LongArray(3)
        var caTotalMs = 0L
        val rx = ArrayList<Pair<Double, Long>>()
        val tx = ArrayList<Pair<Double, Long>>()
        val rttOk = ArrayList<Pair<Double, Long>>()
        var rttSent = 0L
        var rttLost = 0L
        val nrStateMs = linkedMapOf("NSA" to 0L, "SA" to 0L, "LTE" to 0L)
        val rsrpVals = ArrayList<Pair<Double, Long>>()
        val rsrqVals = ArrayList<Pair<Double, Long>>()
        val sinrVals = ArrayList<Pair<Double, Long>>()
        val nrRsrpVals = ArrayList<Pair<Double, Long>>()
        val nrRsrqVals = ArrayList<Pair<Double, Long>>()
        val nrSinrVals = ArrayList<Pair<Double, Long>>()
        var rsrpPoor = 0L; var rsrpFair = 0L; var rsrpGood = 0L
        var rsrqPoor = 0L; var rsrqFair = 0L; var rsrqGood = 0L
        var sinrPoor = 0L; var sinrFair = 0L; var sinrGood = 0L
        val nbMs = LinkedHashMap<String, Long>()
        val nbRsrpSum = HashMap<String, Double>()
        val nbRsrpMs = HashMap<String, Long>()

        for ((smp, ms) in weighted) {
            val prev = bandMs[smp.band]
            bandMs[smp.band] = smp.rat to ((prev?.second ?: 0L) + ms)
            smp.rsrp?.let {
                rsrp[binIndex(it, -140, -44, 10)] += ms
                rsrpVals += it.toDouble() to ms
                when {
                    it < -105 -> rsrpPoor += ms
                    it < -90 -> rsrpFair += ms
                    else -> rsrpGood += ms
                }
            }
            smp.rsrq?.let {
                rsrqVals += it.toDouble() to ms
                when {
                    it < -15 -> rsrqPoor += ms
                    it < -10 -> rsrqFair += ms
                    else -> rsrqGood += ms
                }
            }
            smp.sinr?.let {
                sinr[binIndex(it, -10, 30, 5)] += ms
                sinrVals += it.toDouble() to ms
                when {
                    it < 0 -> sinrPoor += ms
                    it < 13 -> sinrFair += ms
                    else -> sinrGood += ms
                }
            }
            smp.nrRsrp?.let { nrRsrpVals += it.toDouble() to ms }
            smp.nrRsrq?.let { nrRsrqVals += it.toDouble() to ms }
            smp.nrSinr?.let { nrSinrVals += it.toDouble() to ms }
            val lte = if (smp.ccLte + smp.ccNr > 0) smp.ccLte else smp.ccCount
            val nr = if (smp.ccLte + smp.ccNr > 0) smp.ccNr else 0
            if (lte > 0) caLte[min(lte, 4) - 1] += ms
            caNr[min(nr, 2)] += ms
            caTotalMs += ms
            smp.rxBps?.let { rx += it.toDouble() to ms }
            smp.txBps?.let { tx += it.toDouble() to ms }
            val rtt = smp.rttMs
            if (rtt != null) {
                rttSent += ms
                if (rtt < 0) rttLost += ms else rttOk += rtt to ms
            }
            val st = nrStateOf(smp)
            nrStateMs[st] = (nrStateMs[st] ?: 0L) + ms
            smp.neighbours.forEach { nb ->
                val label = "${nb.band} PCI ${nb.pci ?: "—"}"
                nbMs[label] = (nbMs[label] ?: 0L) + ms
                nb.rsrp?.let {
                    nbRsrpSum[label] = (nbRsrpSum[label] ?: 0.0) + it * ms
                    nbRsrpMs[label] = (nbRsrpMs[label] ?: 0L) + ms
                }
            }
        }

        val bands = bandMs.entries
            .map { BandShare(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.ms }
        val bandTotal = bands.sumOf { it.ms }
        val neighbours = nbMs.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { (label, ms) ->
                val w = nbRsrpMs[label] ?: 0L
                NeighbourShare(label, if (w > 0) nbRsrpSum[label]!! / w else null, ms)
            }

        return StatsAgg(
            bands = bands,
            bandTotal = bandTotal,
            kindCounts = visEvents.groupingBy { it.kind }.eachCount(),
            recent = visEvents.asReversed().take(10),
            rsrp = List(rsrpBins) { i -> DistBin(binMid(i, -140, -44, 10), rsrp[i]) },
            sinr = List(sinrBins) { i -> DistBin(binMid(i, -10, 30, 5), sinr[i]) },
            caLte = caLte,
            caNr = caNr,
            caTotal = caTotalMs,
            rx = triple(rx),
            tx = triple(tx),
            rtt = triple(rttOk),
            rttLossPct = if (rttSent == 0L) null else rttLost * 100.0 / rttSent,
            sampleCount = weighted.size,
            rangeStart = range.first,
            rangeEnd = range.last,
            nrStateMs = nrStateMs,
            rsrpPct = percentiles(rsrpVals),
            rsrqPct = percentiles(rsrqVals),
            sinrPct = percentiles(sinrVals),
            nrRsrpPct = percentiles(nrRsrpVals),
            nrRsrqPct = percentiles(nrRsrqVals),
            nrSinrPct = percentiles(nrSinrVals),
            rsrpQuality = QualityShare(rsrpPoor, rsrpFair, rsrpGood),
            rsrqQuality = QualityShare(rsrqPoor, rsrqFair, rsrqGood),
            sinrQuality = QualityShare(sinrPoor, sinrFair, sinrGood),
            neighbours = neighbours,
        )
    }

    /** Weight = elapsed ms until the next sample; last sample contributes 0. Interval clipped to [range]. */
    fun timeWeighted(samples: List<SignalSample>, range: LongRange): List<Pair<SignalSample, Long>> {
        if (samples.size < 2) return emptyList()
        val lo = range.first
        val hi = range.last
        val out = ArrayList<Pair<SignalSample, Long>>(samples.size)
        for (i in 0 until samples.lastIndex) {
            val a = samples[i]
            val end = samples[i + 1].t
            if (end <= lo || a.t >= hi) continue
            val dt = minOf(end, hi) - maxOf(a.t, lo)
            if (dt > 0) out += a to dt
        }
        return out
    }

    fun weightedPercentile(values: List<Pair<Double, Long>>, p: Double): Double? {
        val total = values.sumOf { it.second }
        if (total <= 0L) return values.maxOfOrNull { it.first }
        val sorted = values.sortedBy { it.first }
        val target = total * p
        var acc = 0L
        for ((v, w) in sorted) {
            acc += w
            if (acc >= target) return v
        }
        return sorted.last().first
    }

    fun nrStateOf(smp: SignalSample): String = when {
        smp.rat == Rat.NR && smp.ccLte > 0 -> "NSA"
        smp.rat == Rat.NR -> "SA"
        smp.ccNr > 0 || smp.nrRsrp != null -> "NSA"
        smp.rat == Rat.LTE -> "LTE"
        else -> smp.rat.name
    }
}

private const val rsrpBins = 10 // (-140..-44] / 10 dB
private const val sinrBins = 8  // [-10..30] / 5 dB

private fun binIndex(v: Int, min: Int, max: Int, step: Int): Int {
    val c = v.coerceIn(min, max)
    val n = (max - min + step - 1) / step
    return ((c - min) / step).coerceAtMost(n - 1)
}

private fun binMid(i: Int, min: Int, max: Int, step: Int): Int {
    val start = min + i * step
    val end = minOf(start + step, max)
    return (start + end) / 2
}

private fun triple(values: List<Pair<Double, Long>>): TripleStat {
    if (values.isEmpty()) return TripleStat(null, null, null)
    val max = values.maxOf { it.first }
    val wsum = values.sumOf { it.second }
    val avg = if (wsum > 0) values.sumOf { it.first * it.second } / wsum else null
    return TripleStat(max, avg, StatsAggregator.weightedPercentile(values, 0.95))
}

private fun percentiles(values: List<Pair<Double, Long>>): Percentiles? {
    if (values.isEmpty()) return null
    val p10 = StatsAggregator.weightedPercentile(values, 0.10) ?: return null
    val med = StatsAggregator.weightedPercentile(values, 0.50) ?: return null
    val p90 = StatsAggregator.weightedPercentile(values, 0.90) ?: return null
    return Percentiles(p10, med, p90)
}
