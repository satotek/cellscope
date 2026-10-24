package dev.satotek.cellscope.ui.components

import dev.satotek.cellscope.data.HistoryStore

/** Time ranges offered by every live chart and by Stats. The longest is what HistoryStore retains. */
object ChartWindows {
    val minutes: List<Int> = listOf(1, 2, 5, 15, (HistoryStore.RETENTION_MS / 60_000L).toInt())
    const val DEFAULT_MINUTES = 5
}
