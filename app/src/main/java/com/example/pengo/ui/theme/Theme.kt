package com.example.pengo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PengoColors {
    val BgTop = Color(0xFFEAECED)
    val BgBottom = Color(0xFFFFFCFC)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF20242B)
    val Muted = Color(0xFF737983)
    val Divider = Color(0xFFE6E9EE)

    val Blue = Color(0xFF64A1FF)
    val Cyan = Color(0xFF8BFCFE)
    val Mint = Color(0xFF80F9B7)
    val SkyLavender = Color(0xFF9ABDEB)
    val Pink = Color(0xFFF095FF)
    val Red = Color(0xFFF64848)
    val Lavender = Color(0xFFA6C1FF)
    val Coral = Color(0xFFFC7B7B)
    val Peach = Color(0xFFFC9899)
    val Purple = Color(0xFFD278F2)

    val Success = Color(0xFF1FAE6B)
    val Warning = Color(0xFFFFA63D)
    val PrimaryDeep = Color(0xFF3D7FE8)
}

object PengoGradients {
    val background = Brush.verticalGradient(listOf(PengoColors.BgTop, PengoColors.BgBottom))
    val primary = Brush.linearGradient(listOf(PengoColors.Blue, PengoColors.Cyan))
    val mint = Brush.linearGradient(listOf(PengoColors.Mint, PengoColors.SkyLavender))
    val pink = Brush.linearGradient(listOf(PengoColors.Pink, PengoColors.Red))
    val lavender = Brush.linearGradient(listOf(PengoColors.Lavender, PengoColors.Coral))
    val peach = Brush.linearGradient(listOf(PengoColors.Peach, PengoColors.Purple))

    val all = listOf(primary, mint, pink, lavender, peach)
}

private val scheme = lightColorScheme(
    primary = PengoColors.PrimaryDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EEFF),
    onPrimaryContainer = PengoColors.Ink,
    secondary = PengoColors.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E7FF),
    tertiary = PengoColors.Success,
    background = PengoColors.BgBottom,
    onBackground = PengoColors.Ink,
    surface = PengoColors.Surface,
    onSurface = PengoColors.Ink,
    surfaceVariant = Color(0xFFF2F4F7),
    onSurfaceVariant = PengoColors.Muted,
    surfaceContainer = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color.White,
    surfaceContainerLow = Color(0xFFF9FAFB),
    outline = Color(0xFFD5D9E0),
    outlineVariant = PengoColors.Divider,
    error = PengoColors.Red,
)

private val base = FontFamily.SansSerif

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = base, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = base, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = base, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PengoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
