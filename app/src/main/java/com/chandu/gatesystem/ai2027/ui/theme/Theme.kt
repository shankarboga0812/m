package com.chandu.gatesystem.ai2027.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Palette of the original "hunter system" look: void black, system-blue glow, violet energy, gold rank accents. */
object Hunter {
    val Void = Color(0xFF04050D)
    val Deep = Color(0xFF080B1A)
    val Panel = Color(0xFF0D1226)
    val PanelHi = Color(0xFF151C3A)
    val Line = Color(0xFF26335F)
    val Cyan = Color(0xFF38BDF8)
    val Blue = Color(0xFF3B82F6)
    val Violet = Color(0xFF8B5CF6)
    val Gold = Color(0xFFFBBF24)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFF43F5E)
    val Orange = Color(0xFFFB923C)
    val Text = Color(0xFFE6EBFF)
    val TextDim = Color(0xFF93A0C8)
}

private val scheme = darkColorScheme(
    primary = Hunter.Cyan, onPrimary = Color(0xFF00121F), secondary = Hunter.Violet, onSecondary = Color.White,
    tertiary = Hunter.Gold, background = Hunter.Void, onBackground = Hunter.Text, surface = Hunter.Panel, onSurface = Hunter.Text,
    surfaceVariant = Hunter.PanelHi, onSurfaceVariant = Hunter.TextDim, outline = Hunter.Line, error = Hunter.Red, onError = Color.White
)

private val type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 1.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 0.8.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp)
)

/** The app is always dark: it is the "system window" look; there is deliberately no light theme. */
@Composable
fun GateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
