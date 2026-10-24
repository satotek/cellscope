package dev.satotek.cellscope.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.satotek.cellscope.R

/** [short] drops the "5G" and "connected" words for places already headed "NR", such as a grid cell. */
@Composable
fun nrStatusLabel(state: String, short: Boolean = false): String = stringResource(when (state) {
    "CONNECTED" -> if (short) R.string.nr_nsa_short else R.string.nr_nsa_connected
    "SA" -> if (short) R.string.nr_sa_short else R.string.nr_sa_connected
    "NR_AVAIL", "NOT_RESTRICTED" -> if (short) R.string.nr_available_short else R.string.nr_available
    "RESTRICTED" -> if (short) R.string.nr_restricted_short else R.string.nr_restricted
    "NONE" -> if (short) R.string.nr_disconnected_short else R.string.nr_disconnected
    else -> if (short) R.string.nr_unknown_short else R.string.nr_unknown
})
