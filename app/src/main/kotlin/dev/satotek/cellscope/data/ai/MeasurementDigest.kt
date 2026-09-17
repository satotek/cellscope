package dev.satotek.cellscope.data.ai

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.data.stats.Percentiles
import dev.satotek.cellscope.data.stats.QualityShare
import dev.satotek.cellscope.data.stats.StatsAggregator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MeasurementDigest {
    private const val maxChars = 2500

    fun build(
        context: Context,
        samples: List<SignalSample>,
        events: List<CellEvent>,
        range: LongRange,
        locale: Locale,
    ): String {
        val prompt = AiPrefs.load(context).resolvedPrompt(context)
        val body = buildBody(context, samples, events, range, locale)
        val budget = maxChars - prompt.length - 2
        val trimmed = if (body.length <= budget) body else {
            val cut = body.lastIndexOf('\n', budget.coerceAtMost(body.lastIndex).coerceAtLeast(0))
            (if (cut > 40) body.take(cut) else body.take(budget.coerceAtLeast(0))) + "\n…"
        }
        return trimmed + "\n\n" + prompt
    }

    private fun buildBody(
        context: Context,
        samples: List<SignalSample>,
        events: List<CellEvent>,
        range: LongRange,
        locale: Locale,
    ): String {
        val agg = StatsAggregator.aggregate(samples, events, range)
        val vis = samples.filter { it.t in range }
        val t0 = vis.firstOrNull()?.t ?: range.first
        val t1 = vis.lastOrNull()?.t ?: range.last
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)
        val span = compactDur((t1 - t0).coerceAtLeast(0))
        val n = vis.size
        val device = deviceLabel()
        val operator = operatorLabel(context)
        val headerBits = buildList {
            add(context.getString(R.string.digest_period, fmt.format(Date(t0)), fmt.format(Date(t1)), span, n))
            add(context.getString(R.string.digest_device, device))
            if (operator != null) add(context.getString(R.string.digest_operator, operator))
        }

        val sb = StringBuilder()
        sb.append("# ").append(context.getString(R.string.ai_digest_title)).append('\n')
        sb.append(headerBits.joinToString(" · ")).append('\n')

        sb.append("## ").append(context.getString(R.string.digest_camping)).append('\n')
        sb.append(campingLine(agg)).append('\n')

        sb.append("## ").append(context.getString(R.string.digest_quality)).append('\n')
        sb.append(qualityLine("RSRP", "dBm", agg.rsrpQuality, agg.rsrpPct, context)).append('\n')
        sb.append(qualityLine("SINR", "dB", agg.sinrQuality, agg.sinrPct, context)).append('\n')
        sb.append(qualityLine("RSRQ", "dB", agg.rsrqQuality, agg.rsrqPct, context)).append('\n')
        if (agg.nrRsrpPct != null) {
            sb.append(context.getString(R.string.digest_nr_scc)).append(": ")
            sb.append("RSRP ").append(context.getString(R.string.digest_median)).append(' ')
            sb.append(fmtNum(agg.nrRsrpPct.median)).append(" dBm")
            sb.append(" · p10 ").append(fmtNum(agg.nrRsrpPct.p10))
            sb.append(" · p90 ").append(fmtNum(agg.nrRsrpPct.p90))
            agg.nrSinrPct?.let {
                sb.append(" · SINR ").append(context.getString(R.string.digest_median)).append(' ')
                sb.append(fmtNum(it.median)).append(" dB")
            }
            sb.append('\n')
        }

        sb.append("## ").append(context.getString(R.string.digest_ca)).append('\n')
        sb.append(caLine(agg)).append('\n')

        val totalChanges = agg.kindCounts.values.sum()
        sb.append("## ").append(context.getString(R.string.digest_changes, totalChanges)).append('\n')
        sb.append("HO ").append(agg.kindCounts["HO"] ?: 0)
        sb.append(" · BAND ").append(agg.kindCounts["BAND"] ?: 0)
        sb.append(" · RAT ").append(agg.kindCounts["RAT"] ?: 0).append('\n')
        val evFmt = SimpleDateFormat("HH:mm:ss", locale)
        agg.recent.forEach { e ->
            sb.append(evFmt.format(Date(e.t))).append(' ').append(e.kind).append("  ")
            sb.append(e.from.band).append(" PCI ").append(e.from.pci ?: "—")
            sb.append(" → ").append(e.to.band).append(" PCI ").append(e.to.pci ?: "—")
            sb.append(" (").append(e.from.rsrp ?: "—").append("→").append(e.to.rsrp ?: "—").append(')')
            sb.append('\n')
        }

        sb.append("## ").append(context.getString(R.string.digest_throughput)).append('\n')
        sb.append("▼ max ").append(fmtMbps(agg.rx.max)).append(" / avg ").append(fmtMbps(agg.rx.avg))
        sb.append(" / p95 ").append(fmtMbps(agg.rx.p95)).append(" Mbps")
        sb.append(" · ▲ max ").append(fmtMbps(agg.tx.max)).append(" / avg ").append(fmtMbps(agg.tx.avg))
        sb.append(" / p95 ").append(fmtMbps(agg.tx.p95)).append(" Mbps")
        sb.append(" · RTT max ").append(fmtMs(agg.rtt.max)).append(" / avg ").append(fmtMs(agg.rtt.avg))
        sb.append(" / p95 ").append(fmtMs(agg.rtt.p95)).append(" ms")
        agg.rttLossPct?.let { sb.append(" · loss ").append(String.format(Locale.US, "%.1f%%", it)) }
        sb.append('\n')

        sb.append("## ").append(context.getString(R.string.digest_neighbours)).append('\n')
        if (agg.neighbours.isEmpty()) sb.append("—\n")
        else sb.append(agg.neighbours.joinToString(" · ") { nb ->
            val avg = nb.avgRsrp?.let { " (${context.getString(R.string.digest_avg)} ${fmtNum(it)})" } ?: ""
            "${nb.label}$avg"
        }).append('\n')

        return sb.toString().trimEnd()
    }

    private fun campingLine(agg: dev.satotek.cellscope.data.stats.StatsAgg): String {
        val bands = if (agg.bandTotal <= 0L) "—" else agg.bands.take(6).mapIndexed { i, b ->
            val p = pct(b.ms, agg.bandTotal)
            if (i == 0) "${b.label} $p (${compactDur(b.ms)})" else "${b.label} $p"
        }.joinToString(" · ")
        val stTotal = agg.nrStateMs.values.sum()
        val nsa = pct(agg.nrStateMs["NSA"] ?: 0L, stTotal)
        val sa = pct(agg.nrStateMs["SA"] ?: 0L, stTotal)
        val lte = pct(agg.nrStateMs["LTE"] ?: 0L, stTotal)
        return "$bands · NR state: NSA $nsa / SA $sa / LTE $lte"
    }

    private fun qualityLine(
        name: String,
        unit: String,
        q: QualityShare,
        pcts: Percentiles?,
        context: Context,
    ): String {
        val shares = if (q.total <= 0L) "—" else {
            val good = context.getString(R.string.stats_good)
            val fair = context.getString(R.string.stats_fair)
            val poor = context.getString(R.string.stats_poor)
            "$good ${pct(q.goodMs, q.total)} / $fair ${pct(q.fairMs, q.total)} / $poor ${pct(q.poorMs, q.total)}"
        }
        val extra = pcts?.let {
            " · ${context.getString(R.string.digest_median)} ${fmtNum(it.median)} $unit · p10 ${fmtNum(it.p10)} · p90 ${fmtNum(it.p90)}"
        } ?: ""
        return "$name: $shares$extra"
    }

    private fun caLine(agg: dev.satotek.cellscope.data.stats.StatsAgg): String {
        if (agg.caTotal <= 0L) return "—"
        val lteLabels = listOf("1CC", "2CC", "3CC", "4CC+")
        val lte = agg.caLte.mapIndexed { i, ms -> i to ms }
            .filter { it.second > 0 }
            .sortedByDescending { it.first }
            .joinToString(" / ") { "${lteLabels[it.first]} ${pct(it.second, agg.caTotal)}" }
        val nrLabels = listOf("—", "1CC", "2CC+")
        val nr = agg.caNr.mapIndexed { i, ms -> i to ms }
            .filter { it.first > 0 && it.second > 0 }
            .sortedByDescending { it.first }
            .joinToString(" / ") { "${nrLabels[it.first]} ${pct(it.second, agg.caTotal)}" }
        val parts = ArrayList<String>()
        if (lte.isNotEmpty()) parts += "LTE $lte"
        if (nr.isNotEmpty()) parts += "NR $nr"
        return parts.joinToString(" · ").ifEmpty { "—" }
    }

    private fun deviceLabel(): String {
        val model = Build.MODEL.ifBlank { Build.DEVICE }
        val soc = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN && !it.equals(model, true) }
        return if (soc != null) "$model ($soc)" else model
    }

    @SuppressLint("MissingPermission")
    private fun operatorLabel(context: Context): String? {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        val name = runCatching { tm.networkOperatorName }.getOrNull()?.trim().orEmpty()
        val plmn = runCatching { tm.networkOperator }.getOrNull()?.trim().orEmpty()
        return when {
            name.isNotEmpty() && plmn.isNotEmpty() -> "$name $plmn"
            name.isNotEmpty() -> name
            plmn.isNotEmpty() -> plmn
            else -> null
        }
    }

    private fun pct(part: Long, total: Long): String =
        if (total <= 0L) "—" else String.format(Locale.US, "%.0f%%", part * 100.0 / total)

    private fun compactDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return when {
            s >= 3600 -> String.format(Locale.US, "%dh%02dm", s / 3600, s % 3600 / 60)
            s >= 60 -> String.format(Locale.US, "%dm%02ds", s / 60, s % 60)
            else -> "${s}s"
        }
    }

    private fun fmtNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

    private fun fmtMbps(v: Double?): String {
        if (v == null) return "—"
        val m = v / 1_000_000.0
        return if (m >= 10) String.format(Locale.US, "%.0f", m) else String.format(Locale.US, "%.1f", m)
    }

    private fun fmtMs(v: Double?): String = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
}
