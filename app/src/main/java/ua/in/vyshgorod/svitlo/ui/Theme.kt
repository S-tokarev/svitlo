package ua.`in`.vyshgorod.svitlo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ua.`in`.vyshgorod.svitlo.data.ThemeMode

data class SvitloColors(
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val card: Color,
    val card2: Color,
    val chip: Color,
    val line: Color,
    val powerOn: Color,
    val powerOff: Color,
    val onPowerOn: Color,
    val onPowerOff: Color,
    val powerOnText: Color,
    val powerOffText: Color,
    val accent: Color,
    val onAccent: Color,
    val amber: Color,
    val note: Color,
    val danger: Color,
    val field: Color,
    val fieldBorder: Color,
    val now: Color,
    val dark: Boolean,
)

private val LightColors = SvitloColors(
    paper = Color(0xFFF4F0E6),
    ink = Color(0xFF1E1A14),
    muted = Color(0xFF6E655A),
    card = Color(0xFFFFFCF7),
    card2 = Color(0xFFEBE4D6),
    chip = Color(0xFFF7F1E6),
    line = Color(0xFFDDD4C4),
    powerOn = Color(0xFF7DDE96),
    powerOff = Color(0xFFD1543E),
    onPowerOn = Color(0xFF0E3B22),
    onPowerOff = Color(0xFF3C120C),
    powerOnText = Color(0xFF1E8F4A),
    powerOffText = Color(0xFFA33E2C),
    accent = Color(0xFF1C6E8A),
    onAccent = Color(0xFFFFFFFF),
    amber = Color(0xFF8C6208),
    note = Color(0xFFF8EFCF),
    danger = Color(0xFFF8E3DC),
    field = Color(0xFFFFFCF7),
    fieldBorder = Color(0xFFD9D0C2),
    now = Color(0xFF1E1A14),
    dark = false,
)

private val DarkColors = SvitloColors(
    paper = Color(0xFF141311),
    ink = Color(0xFFF6F1E7),
    muted = Color(0xFFB6AB9C),
    card = Color(0xFF22201C),
    card2 = Color(0xFF2E2A24),
    chip = Color(0xFF1A1916),
    line = Color(0xFF3C362E),
    powerOn = Color(0xFF8EE4A8),
    powerOff = Color(0xFFE87864),
    onPowerOn = Color(0xFF0C3320),
    onPowerOff = Color(0xFF2C100C),
    powerOnText = Color(0xFF8EE4A8),
    powerOffText = Color(0xFFF0A090),
    accent = Color(0xFF8FD0E4),
    onAccent = Color(0xFF042028),
    amber = Color(0xFFE7C36A),
    note = Color(0xFF2C2718),
    danger = Color(0xFF2E1C18),
    field = Color(0xFF1A1916),
    fieldBorder = Color(0xFF3C362E),
    now = Color(0xFFF6F1E7),
    dark = true,
)

val LocalSvitloColors = staticCompositionLocalOf { DarkColors }

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun SvitloTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.amber,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.card,
            onSurface = colors.ink,
            surfaceVariant = colors.card2,
            onSurfaceVariant = colors.muted,
            error = colors.powerOff,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.amber,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.card,
            onSurface = colors.ink,
            surfaceVariant = colors.card2,
            onSurfaceVariant = colors.muted,
            error = colors.powerOff,
        )
    }
    CompositionLocalProvider(LocalSvitloColors provides colors) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
    }
}
