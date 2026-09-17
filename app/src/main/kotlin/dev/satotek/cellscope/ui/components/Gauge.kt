package dev.satotek.cellscope.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.ui.theme.Mono
import dev.satotek.cellscope.ui.theme.Palette

/** 270° arc gauge. `value` is mapped linearly into [min, max]. */
@Composable
fun ArcGauge(value: Int?, min: Int, max: Int, label: String, unit: String, color: Color, modifier: Modifier = Modifier, sizeDp: Int = 180) {
    val target = if (value == null) 0f else ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    val frac by animateFloatAsState(target, animationSpec = tween(400), label = "gauge")
    val track = Palette.outline
    Box(modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(track, 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0f) drawArc(color, 135f, 270f * frac, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // tick marks at quartiles
            for (i in 0..4) {
                val a = Math.toRadians((135 + 270.0 * i / 4))
                val r1 = size.width / 2 - inset - stroke / 2 - 6.dp.toPx(); val r2 = r1 - 6.dp.toPx()
                val c = Offset(size.width / 2, size.height / 2)
                drawLine(track, Offset(c.x + r1 * Math.cos(a).toFloat(), c.y + r1 * Math.sin(a).toFloat()),
                    Offset(c.x + r2 * Math.cos(a).toFloat(), c.y + r2 * Math.sin(a).toFloat()), 2.dp.toPx())
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.toString() ?: "—", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = (sizeDp / 4.2).sp, color = color, letterSpacing = (-1).sp)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = Palette.textDim)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.textDim)
        }
    }
}
