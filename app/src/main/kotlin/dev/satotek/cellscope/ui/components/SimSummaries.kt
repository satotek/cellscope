package dev.satotek.cellscope.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette

@Composable
fun SimSummaries(s: Snapshot, onSelect: (Int) -> Unit) {
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth.value >= 320 * scale) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.sims.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { sim ->
                        val summary = s.simSummaries[sim.subId]
                        val selected = s.sim?.subId == sim.subId
                        val nr = if (selected) s.network.nrState else summary?.nrState ?: "UNKNOWN"
                        Surface(modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton,
                            onClick = { onSelect(sim.subId) }),
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) Palette.surfaceHi else Palette.surface,
                            border = BorderStroke(1.dp, if (selected) Palette.accent else Palette.outline)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(sim.label, style = MaterialTheme.typography.titleMedium, color = Palette.text)
                                Text(listOfNotNull(summary?.operator?.takeIf { it != "—" },
                                    if (sim.isDds) stringResource(R.string.sim_data_badge) else null).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
                                Text(summary?.rat?.takeUnless { it == "UNKNOWN" } ?: "—",
                                    style = MaterialTheme.typography.labelLarge, color = Palette.textDim)
                                Text(summary?.rsrp?.let { "$it dBm" } ?: stringResource(R.string.cell_pending_short),
                                    fontFamily = Mono, style = MaterialTheme.typography.titleLarge,
                                    color = summary?.rsrp?.let { Palette.rsrp(it) } ?: Palette.textDim)
                                Text(nrStatusLabel(nr), style = MaterialTheme.typography.bodySmall,
                                    color = if (nr == "CONNECTED" || nr == "SA") Palette.nr else Palette.textDim)
                                Text(stringResource(if (selected) R.string.sim_summary_selected else R.string.sim_summary_select),
                                    style = MaterialTheme.typography.labelMedium, color = Palette.accent)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
