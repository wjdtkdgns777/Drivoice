package com.example.drivoice.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val pressed: Color,
    val line: Color,
    val text: Color,
    val textSub: Color,
    val accent: Color,
    val onAccent: Color,
)

private val DarkPalette = Palette(
    background = Color(0xFF101113),
    surface = Color(0xFF18191C),
    pressed = Color(0xFF25272B),
    line = Color(0xFF2C2E33),
    text = Color(0xFFF3F4F6),
    textSub = Color(0xFFA3A8B0),
    accent = Color(0xFF3FD39A),
    onAccent = Color(0xFF07150F),
)

private val LightPalette = Palette(
    background = Color(0xFFF7F7F8),
    surface = Color(0xFFFFFFFF),
    pressed = Color(0xFFEBECEF),
    line = Color(0xFFE1E3E7),
    text = Color(0xFF111214),
    textSub = Color(0xFF555A62),
    accent = Color(0xFF0B7D54),
    onAccent = Color(0xFFFFFFFF),
)

object Type {
    private val numbers = "tnum"
    val hero = TextStyle(fontSize = 112.sp, lineHeight = 120.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = numbers, letterSpacing = (-2).sp)
    val headline = TextStyle(fontSize = 56.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
    val titleSmall = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = numbers)
    val body = TextStyle(fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = numbers)
    val bodyStrong = body.copy(fontWeight = FontWeight.Bold)
    val label = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = numbers)
}

object Space {
    val xs = 8.dp
    val s = 16.dp
    val m = 24.dp
    val l = 32.dp
    val xl = 48.dp
    val xxl = 64.dp
    val touch = 88.dp
    val hairline = 1.dp
}

object Motion {
    val easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    const val SHORT1 = 100
    const val SHORT2 = 150
    const val MEDIUM1 = 250
    const val MEDIUM2 = 300
    const val LONG1 = 400
    const val LONG2 = 450
    const val STAGGER = 40
    const val PRESSED_SCALE = 0.95f
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val LocalStill = staticCompositionLocalOf { false }

@Composable
fun DrivoiceTheme(still: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides if (isSystemInDarkTheme()) DarkPalette else LightPalette,
        LocalStill provides still,
        content = content,
    )
}
