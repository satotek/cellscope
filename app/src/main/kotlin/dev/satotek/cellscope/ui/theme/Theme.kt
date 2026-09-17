package dev.satotek.cellscope.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.satotek.cellscope.data.model.Rat

/**
 * Chrome (background / surfaces / text) comes from the wallpaper-derived dynamic colour scheme,
 * so the app looks like a Pixel system app in both light and dark. Semantic colours (RAT,
 * signal quality) stay fixed – they carry meaning and must not shift with the wallpaper.
 */
object Palette {
    val bg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
    val surface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer
    val surfaceHi: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
    val text: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
    val textDim: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val accent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
    val onAccent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary

    private val dark: Boolean @Composable @ReadOnlyComposable get() =
        MaterialTheme.colorScheme.background.luminance() < 0.5f
    @Composable @ReadOnlyComposable private fun pick(d: Color, l: Color) = if (dark) d else l

    val nr: Color @Composable @ReadOnlyComposable get() = pick(Color(0xFF3DDC97), Color(0xFF0E8A5A))
    val lte: Color @Composable @ReadOnlyComposable get() = pick(Color(0xFF7FC8FF), Color(0xFF1565C0))
    val wcdma: Color @Composable @ReadOnlyComposable get() = pick(Color(0xFFFFC857), Color(0xFFB26A00))
    val gsm: Color @Composable @ReadOnlyComposable get() = pick(Color(0xFFFF8A65), Color(0xFFC63F17))
    val unknown: Color @Composable @ReadOnlyComposable get() = pick(Color(0xFF9E9E9E), Color(0xFF616161))
    val good: Color @Composable @ReadOnlyComposable get() = nr
    val fair: Color @Composable @ReadOnlyComposable get() = wcdma
    val poor: Color @Composable @ReadOnlyComposable get() = pick(Color(0xFFFF6B6B), Color(0xFFC62828))

    @Composable @ReadOnlyComposable
    fun rat(r: Rat) = when (r) { Rat.NR -> nr; Rat.LTE -> lte; Rat.WCDMA, Rat.TDSCDMA -> wcdma; Rat.GSM -> gsm; Rat.UNKNOWN -> unknown }

    /** RSRP → colour. Thresholds follow the usual drive-test convention. */
    @Composable @ReadOnlyComposable fun rsrp(v: Int?) = when { v == null -> textDim; v >= -90 -> good; v >= -105 -> fair; else -> poor }
    @Composable @ReadOnlyComposable fun rsrq(v: Int?) = when { v == null -> textDim; v >= -10 -> good; v >= -15 -> fair; else -> poor }
    @Composable @ReadOnlyComposable fun sinr(v: Int?) = when { v == null -> textDim; v >= 13 -> good; v >= 0 -> fair; else -> poor }
}

val Mono = FontFamily.Monospace

/** Expressive defaults for everything textual; monospace only where numbers must line up. */
private val typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
    )
}

/** Resolves the Settings theme mode to a dark flag; anything but "light"/"dark" follows the system. */
@Composable
fun isDarkFor(mode: String): Boolean = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CellScopeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        content = content,
    )
}
