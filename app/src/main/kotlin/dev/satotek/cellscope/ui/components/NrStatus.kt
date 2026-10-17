package dev.satotek.cellscope.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.satotek.cellscope.R

@Composable
fun nrStatusLabel(state: String): String = stringResource(when (state) {
    "CONNECTED" -> R.string.nr_nsa_connected
    "SA" -> R.string.nr_sa_connected
    "NR_AVAIL", "NOT_RESTRICTED" -> R.string.nr_available
    "RESTRICTED" -> R.string.nr_restricted
    "NONE" -> R.string.nr_disconnected
    else -> R.string.nr_unknown
})
