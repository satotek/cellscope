package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.satotek.cellscope.MainViewModel
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.visits.VisitedCell
import dev.satotek.cellscope.ui.components.KeyValueRow
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VisitedCellsScreen(vm: MainViewModel) {
    val all by vm.visitedCells.collectAsStateWithLifecycle()
    val failed by vm.visitStoreFailed.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val rows = remember(all, query) { all.filter { e ->
        listOf(e.plmn, e.rat, e.cellId.toString(), e.band, e.pci?.toString().orEmpty(),
            e.arfcn?.toString().orEmpty(), e.note, e.simLabels.joinToString()).any { it.contains(query.trim(), ignoreCase = true) }
    } }
    val date = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "search") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.visits_scope), style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text(stringResource(R.string.visits_search)) })
                Text(stringResource(R.string.cells_count, rows.size, all.size), color = Palette.textDim)
                if (failed) Text(stringResource(R.string.visits_failed), color = Palette.poor)
            }
        }
        if (rows.isEmpty()) item(key = "empty") {
            Text(stringResource(if (all.isEmpty()) R.string.visits_empty else R.string.cells_filter_empty), color = Palette.textDim)
        }
        items(rows, key = { it.key }) { entry ->
            VisitCard(entry, date, onEdit = { editing = entry.key; note = entry.note })
        }
    }
    if (editing != null) AlertDialog(onDismissRequest = { editing = null },
        title = { Text(stringResource(R.string.visits_note)) },
        text = { OutlinedTextField(value = note, onValueChange = { note = it }, minLines = 3,
            label = { Text(stringResource(R.string.visits_note)) }) },
        confirmButton = { TextButton(onClick = { editing?.let { vm.setVisitNote(it, note) }; editing = null }) {
            Text(stringResource(R.string.visits_save))
        } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun VisitCard(e: VisitedCell, date: SimpleDateFormat, onEdit: () -> Unit) {
    Panel(Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.visits_note), onClick = onEdit)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${e.rat} · ${e.band}", style = MaterialTheme.typography.titleMedium, color = Palette.text)
            Text(e.bestRsrp?.let { "$it dBm" } ?: "—", fontFamily = Mono, color = Palette.rsrp(e.bestRsrp))
        }
        Text("${e.plmn} · ${e.simLabels.joinToString()}", style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        Spacer(Modifier.height(8.dp))
        KeyValueRow(if (e.rat == "NR") "NCI" else "Cell ID", e.cellId.toString())
        KeyValueRow("PCI / ARFCN", "${e.pci ?: "—"} / ${e.arfcn ?: "—"}")
        KeyValueRow(stringResource(R.string.visits_count), e.visits.toString())
        Text(stringResource(R.string.visits_best_rsrp), style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
        Text(stringResource(R.string.visits_first, date.format(Date(e.firstSeen))), style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        Text(stringResource(R.string.visits_last, date.format(Date(e.lastSeen))), style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        Spacer(Modifier.height(8.dp))
        Text(e.note.ifBlank { stringResource(R.string.visits_add_note) }, style = MaterialTheme.typography.bodyMedium,
            color = if (e.note.isBlank()) Palette.accent else Palette.text)
    }
}
