package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.replay.TrackPoint
import dev.satotek.cellscope.ui.components.Panel
import dev.satotek.cellscope.ui.components.TrackMap
import dev.satotek.cellscope.ui.theme.Palette

/**
 * The track map as a panel, with a wide view one tap away. The wide view hands the screen to the
 * map, so one finger pans there; picking a sample closes it so the caller's sheet is not hidden.
 */
@Composable
fun TrackPanel(
    title: String,
    points: List<TrackPoint>,
    events: List<CellEvent>,
    selectedT: Long?,
    onSelect: (Long) -> Unit,
    resetKey: Any? = points,
    live: Boolean = false,
) {
    var wide by rememberSaveable { mutableStateOf(false) }
    Panel {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
        Spacer(Modifier.height(6.dp))
        TrackMap(points, events, selectedT, onSelect, Modifier.fillMaxWidth().height(260.dp),
            resetKey = resetKey, live = live, onExpand = { wide = true })
        Spacer(Modifier.height(6.dp))
        TrackLegend(live)
    }
    if (wide) Dialog(onDismissRequest = { wide = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Palette.bg) {
            Column(Modifier.systemBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { wide = false }) { Icon(Icons.Default.CloseFullscreen, stringResource(R.string.map_collapse)) }
                }
                TrackMap(points, events, selectedT, onSelect = { wide = false; onSelect(it) },
                    Modifier.fillMaxWidth().weight(1f), resetKey = resetKey, live = live, panWithOneFinger = true)
                Spacer(Modifier.height(8.dp))
                TrackLegend(live)
            }
        }
    }
}

@Composable
private fun TrackLegend(live: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend(Palette.good, "≥ −90"); Legend(Palette.fair, "≥ −105"); Legend(Palette.poor, "< −105 dBm")
        Legend(Color.White, stringResource(R.string.replay_map_changes))
        if (live) Legend(Palette.accent, stringResource(R.string.map_now))
    }
}
