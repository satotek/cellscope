package dev.satotek.cellscope.data.visits

/** A cell identity is network-scoped, not a subscriber or a temporary Android subId. */
data class VisitedCell(
    val key: String, val plmn: String, val rat: String, val cellId: Long,
    val band: String, val pci: Int?, val arfcn: Int?,
    val firstSeen: Long, val lastSeen: Long, val bestRsrp: Int?, val visits: Int,
    val simLabels: Set<String>, val note: String = "", val modifiedAt: Long = lastSeen,
) {
    /** Backups may contain overlapping observations; do not count the same visit twice. */
    fun merge(other: VisitedCell): VisitedCell {
        require(key == other.key)
        val latest = if (modifiedAt >= other.modifiedAt) this else other
        return latest.copy(firstSeen = minOf(firstSeen, other.firstSeen), lastSeen = maxOf(lastSeen, other.lastSeen),
            bestRsrp = listOfNotNull(bestRsrp, other.bestRsrp).maxOrNull(), visits = maxOf(visits, other.visits),
            simLabels = simLabels + other.simLabels)
    }
}
