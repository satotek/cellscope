package dev.satotek.cellscope.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.CellEntry
import dev.satotek.cellscope.ui.theme.Palette
import kotlinx.coroutines.delay

/** CellInfo timestamps describe the modem sample, unlike the UI's polling timestamp. */
@Composable
fun cellAgeSeconds(cell: CellEntry?): Long? {
    val timestamp = cell?.timestampNanos
    val now by produceState(SystemClock.elapsedRealtimeNanos()) {
        while (true) {
            value = SystemClock.elapsedRealtimeNanos()
            delay(1_000)
        }
    }
    // A new modem sample can arrive between timer ticks; compare against the clock now,
    // rather than briefly treating that sample as having a future timestamp.
    val current = maxOf(now, SystemClock.elapsedRealtimeNanos())
    return timestamp?.takeIf { it > 0 && it <= current }?.let { (current - it) / 1_000_000_000L }
}

@Composable
fun CellFreshness(cell: CellEntry?, standby: Boolean = false, compact: Boolean = false) {
    val age = cellAgeSeconds(cell)
    val label = when {
        cell == null && compact -> stringResource(R.string.cell_pending_short)
        cell == null -> stringResource(if (standby) R.string.cell_pending_standby else R.string.cell_pending)
        age == null -> stringResource(if (compact) R.string.cell_age_unknown_short else R.string.cell_age_unknown)
        age >= 30 -> stringResource(R.string.cell_stale)
        else -> ""
    }
    Text(label, modifier = if (compact) Modifier.widthIn(max = 140.dp) else Modifier,
        maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = if (age != null && age >= 30) Palette.fair else Palette.textDim)
}
