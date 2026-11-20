package com.focusforceplus.app.study.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * StudyGuard 的视觉系统：中性色画布、描边卡片、语义色只承担含义。
 * 取值与「用时记」DESIGN.md 对齐：控件 6dp、容器 12dp 圆角，字重上限 600，时长等宽。
 */
@Immutable
data class StudyColors(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val tertiary: Color,
    val line: Color,
    val lineSubtle: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val comparison: Color,
    val comparisonSoft: Color,
    val success: Color,
    val danger: Color,
    val dangerSoft: Color,
    val control: Color,
    val solid: Color,
    val onSolid: Color,
    val solidEdge: Color,
    val focus: Color,
    val dark: Boolean,
)

private val LightStudyColors = StudyColors(
    canvas = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF171717),
    muted = Color(0xFF5C5C5C),
    tertiary = Color(0xFF737373),
    line = Color(0xFFE6E6E6),
    lineSubtle = Color(0xFFF0F0F0),
    lineStrong = Color(0xFFD1D1D1),
    accent = Color(0xFF3F56B5),
    accentSoft = Color(0xFFEEF1F9),
    comparison = Color(0xFF8A5F16),
    comparisonSoft = Color(0xFFF8F1E6),
    success = Color(0xFF2B7454),
    danger = Color(0xFFAB3F3B),
    dangerSoft = Color(0xFFF9EFEE),
    control = Color(0xFFF2F2F2),
    solid = Color(0xFF171717),
    onSolid = Color(0xFFFFFFFF),
    solidEdge = Color(0xFF000000),
    focus = Color(0xFF6B83D6),
    dark = false,
)

private val DarkStudyColors = StudyColors(
    canvas = Color(0xFF0A0A0A),
    surface = Color(0xFF111111),
    ink = Color(0xFFEDEDED),
    muted = Color(0xFFA3A3A3),
    tertiary = Color(0xFF8A8A8A),
    line = Color(0xFF262626),
    lineSubtle = Color(0xFF1A1A1A),
    lineStrong = Color(0xFF3A3A3A),
    accent = Color(0xFF8E9FF2),
    accentSoft = Color(0xFF1A2038),
    comparison = Color(0xFFD5A05A),
    comparisonSoft = Color(0xFF2A2114),
    success = Color(0xFF6CC39A),
    danger = Color(0xFFEF8F8A),
    dangerSoft = Color(0xFF2E1D1C),
    control = Color(0xFF1C1C1C),
    solid = Color(0xFFEDEDED),
    onSolid = Color(0xFF111111),
    solidEdge = Color(0xFF8A8A8A),
    focus = Color(0xFF9DB0F5),
    dark = true,
)

internal val LocalStudyColors = staticCompositionLocalOf { LightStudyColors }

object StudyTheme {
    val colors: StudyColors
        @Composable get() = LocalStudyColors.current
}

private fun studyColorScheme(c: StudyColors) = if (c.dark) {
    darkColorScheme(
        primary = c.solid,
        onPrimary = c.onSolid,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.accent,
        secondary = c.accent,
        onSecondary = c.canvas,
        secondaryContainer = c.accentSoft,
        onSecondaryContainer = c.accent,
        tertiary = c.comparison,
        onTertiary = c.canvas,
        tertiaryContainer = c.comparisonSoft,
        onTertiaryContainer = c.comparison,
        background = c.canvas,
        onBackground = c.ink,
        surface = c.surface,
        onSurface = c.ink,
        surfaceVariant = c.control,
        onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.canvas,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.control,
        surfaceContainerHighest = c.control,
        surfaceTint = c.accent,
        inverseSurface = c.ink,
        inverseOnSurface = c.canvas,
        inversePrimary = c.canvas,
        error = c.danger,
        onError = c.canvas,
        errorContainer = c.dangerSoft,
        onErrorContainer = c.danger,
        outline = c.lineStrong,
        outlineVariant = c.line,
        scrim = Color(0xFF000000),
    )
} else {
    lightColorScheme(
        primary = c.solid,
        onPrimary = c.onSolid,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.accent,
        secondary = c.accent,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = c.accentSoft,
        onSecondaryContainer = c.accent,
        tertiary = c.comparison,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = c.comparisonSoft,
        onTertiaryContainer = c.comparison,
        background = c.canvas,
        onBackground = c.ink,
        surface = c.surface,
        onSurface = c.ink,
        surfaceVariant = c.control,
        onSurfaceVariant = c.muted,
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.control,
        surfaceContainerHighest = c.control,
        surfaceTint = c.accent,
        inverseSurface = c.ink,
        inverseOnSurface = Color(0xFFFFFFFF),
        inversePrimary = c.surface,
        error = c.danger,
        onError = Color(0xFFFFFFFF),
        errorContainer = c.dangerSoft,
        onErrorContainer = c.danger,
        outline = c.lineStrong,
        outlineVariant = c.line,
        scrim = Color(0xFF000000),
    )
}

private val StudyTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum",
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.025).em,
        fontFeatureSettings = "tnum",
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.025).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.02).em,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.015).em,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
)

internal val StudyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
internal fun StudyGuardTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkStudyColors else LightStudyColors
    CompositionLocalProvider(LocalStudyColors provides colors) {
        MaterialTheme(
            colorScheme = studyColorScheme(colors),
            typography = StudyTypography,
            shapes = StudyShapes,
            content = content,
        )
    }
}