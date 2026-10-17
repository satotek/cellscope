package dev.satotek.cellscope.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette

data class MeasurementValue(val label: String, val value: String, val unit: String? = null,
    val color: Color = Color.Unspecified, val prominent: Boolean = false)

/** Keep complete values visible when accessibility text sizes leave less room per column. */
@Composable
fun MeasurementGrid(values: List<MeasurementValue>, preferredColumns: Int = 4) {
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Column count depends on available space, never on a changing measurement value.
        val minimumWidth = 72f * scale
        val columns = when {
            maxWidth.value >= preferredColumns * minimumWidth + (preferredColumns - 1) * 12 -> preferredColumns
            maxWidth.value >= 2 * minimumWidth + 12 -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            values.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        val color = if (item.color == Color.Unspecified) Palette.text else item.color
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                            Text(item.label, style = MaterialTheme.typography.labelMedium, color = Palette.textDim)
                            Text(item.value, modifier = Modifier.heightIn(min = (if (item.prominent) 48f else 24f).times(scale).dp), fontFamily = Mono,
                                fontWeight = if (item.prominent) FontWeight.Bold else FontWeight.Medium,
                                fontSize = if (item.prominent) 36.sp else 16.sp, color = color)
                            item.unit?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.textDim) }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

