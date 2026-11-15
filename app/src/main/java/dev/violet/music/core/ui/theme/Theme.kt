package dev.violet.music.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MidnightViolet = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF21143F),
    primaryContainer = Color(0xFF7764D8),
    onPrimaryContainer = Color(0xFFF5F5F7),
    secondary = Color(0xFFA78BFA),
    background = Color(0xFF101116),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1D1F29),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF292B39),
    onSurfaceVariant = Color(0xFFA9ABBA),
    outline = Color(0xFF555767),
    error = Color(0xFFEF7777),
    onError = Color(0xFF310D0D),
)

private val VioletTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
)

@Composable
fun VioletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MidnightViolet, typography = VioletTypography, content = content)
}
