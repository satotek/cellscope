package dev.satotek.cellscope.ui.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.Snapshot
import dev.satotek.cellscope.data.telephony.SimInfo

/**
 * Label of the default data SIM when the screen is showing a different subscription.
 * Null for one SIM, or when the data SIM itself is selected — those layouts stay as they were.
 */
fun standbyDdsLabel(s: Snapshot): String? {
    if (s.sims.size < 2 || s.sim?.isDds != false) return null
    return s.sims.firstOrNull { it.isDds }?.label
}

/** Kept outside scrolling content so the observed SIM stays visible. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SimSelector(sims: List<SimInfo>, selectedSubId: Int?, onSelect: (Int) -> Unit) {
    if (sims.size < 2) return
    var expanded by remember { mutableStateOf(false) }
    val selected = sims.firstOrNull { it.subId == selectedSubId }
    androidx.compose.material3.Surface(
        onClick = { expanded = true },
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.widthIn(max = 240.dp).heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(Icons.Outlined.SimCard, stringResource(R.string.sim_select))
            Text(selected?.label ?: stringResource(R.string.sim_select), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            androidx.compose.material3.Icon(Icons.Filled.ExpandMore, null)
        }
    }
    if (expanded) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { expanded = false }) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                Text(stringResource(R.string.sim_select),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                sims.forEach { sim ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = sim.subId == selectedSubId,
                            role = androidx.compose.ui.semantics.Role.RadioButton,
                            onClick = { onSelect(sim.subId); expanded = false },
                        ).padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        androidx.compose.material3.RadioButton(selected = sim.subId == selectedSubId, onClick = null)
                        Text(sim.label, modifier = Modifier.weight(1f))
                        if (sim.isDds) Text(stringResource(R.string.sim_data_badge),
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
