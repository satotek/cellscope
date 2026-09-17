package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.ui.components.KeyValueRow
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette

/** Root-sourced raw views: RIL radio log, PhysicalChannelConfig line, modem props, ServiceState string. */
@Composable
fun RawScreen(s: Snapshot, showTitle: Boolean = true) {
    val r = s.root
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) item { Text(stringResource(R.string.more_raw), style = MaterialTheme.typography.headlineMedium, color = Palette.text) }
        item {
            Panel(title = "root", accent = if (r.available == true) Palette.nr else Palette.poor) {
                Text(when (r.available) { null -> "probing"; true -> if (r.enabled) "su · polling" else "su · off"; false -> "no su" }, fontFamily = Mono, color = Palette.textDim, style = MaterialTheme.typography.bodySmall)
                r.lastError?.let { Text(it, color = Palette.poor, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Panel(title = "logcat -b radio (filtered)", accent = Palette.lte) {
                if (r.radioLog.isEmpty()) Text("—", color = Palette.textDim)
                else Column(Modifier.horizontalScroll(rememberScrollState())) {
                    SelectionContainer { Column { r.radioLog.takeLast(60).forEach { Text(it, fontFamily = Mono, fontSize = 10.sp, color = Palette.text, softWrap = false) } } }
                }
            }
        }
        item {
            Panel(title = "mPhysicalChannelConfigs") {
                SelectionContainer { Text(r.physicalChannelsRaw.ifBlank { "—" }, fontFamily = Mono, fontSize = 10.sp, color = Palette.text) }
            }
        }
        item {
            Panel(title = "ServiceState.toString()") {
                SelectionContainer { Text(s.network.rawServiceState.ifBlank { "—" }, fontFamily = Mono, fontSize = 10.sp, color = Palette.text) }
            }
        }
        item {
            Panel(title = "modem / RIL props · ${r.modemProps.size}") {
                if (r.modemProps.isEmpty()) Text("—", color = Palette.textDim)
                r.modemProps.forEach { (k, v) -> KeyValueRow(k, v) }
            }
        }
    }
}
