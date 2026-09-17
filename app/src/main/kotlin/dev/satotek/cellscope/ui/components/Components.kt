package dev.satotek.cellscope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.CellEvent
import dev.satotek.cellscope.data.model.Rat
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Panel(modifier: Modifier = Modifier, title: String? = null, accent: Color? = null, padding: androidx.compose.ui.unit.Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Palette.surface)
            .padding(padding)
    ) {
        if (title != null) {
            Row(Modifier.padding(horizontal = if (padding == 0.dp) 16.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (accent != null) Box(Modifier.size(8.dp).clip(CircleShape).background(accent)); if (accent != null) Spacer(Modifier.width(8.dp))
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
            }
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun Metric(label: String, value: String, unit: String? = null, color: Color = Palette.text, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = if (big) 26.sp else 17.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (unit != null) { Spacer(Modifier.width(3.dp)); Text(unit, style = MaterialTheme.typography.bodySmall, color = Palette.textDim, modifier = Modifier.padding(bottom = 2.dp)) }
        }
    }
}

@Composable
fun RatChip(rat: Rat, text: String = rat.label, filled: Boolean = true) {
    val c = Palette.rat(rat)
    Box(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (filled) c else c.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) { Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (filled) Palette.bg else c) }
}

@Composable
fun Tag(text: String, color: Color = Palette.textDim) {
    Box(Modifier.clip(MaterialTheme.shapes.small).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontFamily = Mono, fontSize = 11.sp, color = color)
    }
}

/** Horizontal level bar: value mapped into [min,max]. */
@Composable
fun LevelBar(value: Int?, min: Int, max: Int, color: Color, modifier: Modifier = Modifier) {
    val f = if (value == null) 0f else ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.outline)) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
fun KeyValueRow(k: String, v: String, vColor: Color = Palette.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
        Text(v, fontFamily = Mono, fontSize = 13.sp, color = vColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun fmt(v: Int?, suffix: String = ""): String = v?.let { "$it$suffix" } ?: "—"
fun fmt(v: Long?): String = v?.toString() ?: "—"
fun fmtMhz(v: Double?): String = v?.let { String.format("%.1f", it) } ?: "—"
fun fmtBw(khz: Int?): String = khz?.let { if (it % 1000 == 0) "${it / 1000} MHz" else "${it / 1000.0} MHz" } ?: "—"

@Composable
fun EventRow(e: CellEvent) {
    val fmtT = SimpleDateFormat("HH:mm:ss", Locale.US)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(fmtT.format(Date(e.t)), fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim)
        Spacer(Modifier.width(8.dp))
        Tag(e.kind, when (e.kind) { "RAT" -> Palette.wcdma; "BAND" -> Palette.lte; else -> Palette.textDim })
        Spacer(Modifier.width(8.dp))
        Text("${e.from.band} PCI ${fmt(e.from.pci)}", fontFamily = Mono, fontSize = 12.sp, color = Palette.rat(e.from.rat))
        Text("  →  ", fontFamily = Mono, fontSize = 12.sp, color = Palette.textDim)
        Text("${e.to.band} PCI ${fmt(e.to.pci)}", fontFamily = Mono, fontSize = 12.sp, color = Palette.rat(e.to.rat), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text("${fmt(e.from.rsrp)}→${fmt(e.to.rsrp)}", fontFamily = Mono, fontSize = 11.sp, color = Palette.textDim)
    }
}
