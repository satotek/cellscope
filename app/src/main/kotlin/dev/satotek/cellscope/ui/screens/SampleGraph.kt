package dev.satotek.cellscope.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.satotek.cellscope.R
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.SignalSample
import dev.satotek.cellscope.ui.components.Series
import dev.satotek.cellscope.ui.components.SignalChart
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date

/** The same frozen timeline is used in the sheet and the full-screen inspector. */
@Composable
internal fun SampleGraph(
    samples: List<SignalSample>, events: List<CellEvent>, selected: SignalSample,
    metric: String, minutes: Int, anchor: Long, fullScreen: Boolean,
    onMetric: (String) -> Unit, onMinutes: (Int) -> Unit,
    onSelect: (SignalSample) -> Unit, onScrubbing: (Boolean) -> Unit,
    onToggleFullScreen: () -> Unit,
) {
    val span = minOf(minutes * 60_000L, (samples.last().t - samples.first().t).coerceAtLeast(1_000L))
    val end = (anchor + span / 2).coerceIn(samples.first().t + span, maxOf(samples.last().t, samples.first().t + span))
    val range = (end - span)..end
    val unit = if (metric == "RSRP") "dBm" else "dB"
    val pick: (SignalSample) -> Int? = when (metric) {
        "RSRQ" -> { s -> s.rsrq }
        "SINR" -> { s -> s.sinr }
        else -> { s -> s.rsrp }
    }
    val nrPick: (SignalSample) -> Int? = when (metric) {
        "RSRQ" -> { s -> s.nrRsrq }
        "SINR" -> { s -> s.nrSinr }
        else -> { s -> s.nrRsrp }
    }
    val hasNr = samples.any { it.t in range && nrPick(it) != null }
    val time = SimpleDateFormat("HH:mm:ss", LocalConfiguration.current.locales[0]).format(Date(selected.t))
    val description = stringResource(R.string.signal_inspect_chart, metric, time)
    val previous = stringResource(R.string.signal_previous_sample)
    val next = stringResource(R.string.signal_next_sample)
    val idx = samples.indexOf(selected)
    Column(if (fullScreen) Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp) else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, style = MaterialTheme.typography.titleLarge, fontFamily = Mono, color = Palette.text)
                Text("${selected.rat.label} · ${selected.band} · PCI ${selected.pci ?: "—"}",
                    style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
            }
            Text("${pick(selected) ?: "—"} $unit", style = MaterialTheme.typography.titleLarge,
                fontFamily = Mono, color = Palette.text)
            IconButton(onClick = onToggleFullScreen) {
                Icon(if (fullScreen) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                    contentDescription = stringResource(if (fullScreen) R.string.signal_exit_fullscreen else R.string.signal_fullscreen))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("RSRP", "RSRQ", "SINR").forEach { name ->
                FilterChip(selected = metric == name, onClick = { onMetric(name) },
                    modifier = Modifier.weight(1f), label = { Text(name) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 5, 15, 60).forEach { value ->
                FilterChip(selected = minutes == value, onClick = { onMinutes(value) },
                    modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.signal_minutes, value)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(Palette.rat(selected.rat), stringResource(R.string.signal_serving))
            if (hasNr) Legend(Palette.nr, "NR · ${nrPick(selected) ?: "—"} $unit")
        }
        BoxWithConstraints(if (fullScreen) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()) {
            SignalChart(samples, events, listOfNotNull(
                Series("serving", Palette.rat(selected.rat), pick),
                if (hasNr) Series("NR", Palette.nr, nrPick) else null),
                yMin = when (metric) { "RSRQ" -> -25; "SINR" -> -10; else -> -140 },
                yMax = when (metric) { "RSRQ" -> 0; "SINR" -> 40; else -> -40 },
                yStep = when (metric) { "RSRQ" -> 5; "SINR" -> 10; else -> 20 },
                zones = emptyList(), range = range,
                heightDp = if (fullScreen) maxHeight.value.toInt().coerceAtLeast(60) else 190,
                cursorT = selected.t, pillsFor = 0, onTapSample = onSelect,
                scrubEnabled = true, onScrubbing = onScrubbing,
                modifier = Modifier.semantics {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction(previous) { samples.getOrNull(idx - 1)?.let(onSelect); idx > 0 },
                        CustomAccessibilityAction(next) { samples.getOrNull(idx + 1)?.let(onSelect); idx < samples.lastIndex })
                })
        }
        Text(stringResource(R.string.signal_frozen_hint), style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
    }
}
