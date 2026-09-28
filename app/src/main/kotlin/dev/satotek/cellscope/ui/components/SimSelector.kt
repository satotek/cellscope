package dev.satotek.cellscope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Connected toggle, same shapes as Settings' segment row. Callers skip this when fewer than two SIMs are active. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SimSelector(sims: List<SimInfo>, selectedSubId: Int?, onSelect: (Int) -> Unit) {
    if (sims.size < 2) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        sims.forEachIndexed { i, sim ->
            ToggleButton(
                checked = sim.subId == selectedSubId,
                onCheckedChange = { if (sim.subId != selectedSubId) onSelect(sim.subId) },
                modifier = Modifier.weight(1f),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    sims.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sim.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (sim.isDds) {
                        val badge = LocalContentColor.current
                        Text(
                            stringResource(R.string.sim_data_badge),
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            color = badge,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(badge.copy(alpha = 0.18f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
