package app.otter.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

val OtterBlue = Color(0xFF4EA7F5)
val OtterOrange = Color(0xFFFF6A2B)
val OtterPeriwinkle = Color(0xFF8092FF)

@Immutable
data class OtterColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceGlass: Color,
    val drawerSurface: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val upvote: Color,
    val downvote: Color,
    val saved: Color,
    val spoiler: Color,
    val mediaBlue: Color,
)

private val DarkOtterColors = OtterColors(
    canvas = Color(0xFF101316),
    surface = Color(0xFF15191D),
    surfaceRaised = Color(0xFF1C2228),
    surfaceGlass = Color(0xE61B2025),
    drawerSurface = Color(0xFA181D22),
    divider = Color(0xFF293038),
    textPrimary = Color(0xFFF3F6F8),
    textSecondary = Color(0xFFA3ADB7),
    textTertiary = Color(0xFF6F7983),
    accent = Color(0xFF5CB2FF),
    upvote = OtterOrange,
    downvote = OtterPeriwinkle,
    saved = Color(0xFFF1C75B),
    spoiler = Color(0xFF313840),
    mediaBlue = Color(0xFF0C5F91),
)

/** True-black surfaces for OLED displays, kept separate from the softer gray dark theme. */
private val AmoledOtterColors = OtterColors(
    canvas = Color.Black,
    surface = Color.Black,
    surfaceRaised = Color(0xFF0B0B0B),
    surfaceGlass = Color(0xF2070707),
    drawerSurface = Color.Black,
    divider = Color(0xFF242424),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFAAAAAA),
    textTertiary = Color(0xFF747474),
    accent = Color(0xFF5CB2FF),
    upvote = OtterOrange,
    downvote = OtterPeriwinkle,
    saved = Color(0xFFF1C75B),
    spoiler = Color(0xFF222222),
    mediaBlue = Color(0xFF0C5F91),
)

private val LightOtterColors = OtterColors(
    canvas = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEEF1F4),
    surfaceGlass = Color(0xF2FFFFFF),
    drawerSurface = Color(0xFAFFFFFF),
    divider = Color(0xFFDDE2E7),
    textPrimary = Color(0xFF17202A),
    textSecondary = Color(0xFF66717C),
    textTertiary = Color(0xFF929BA4),
    accent = Color(0xFF1678D3),
    upvote = Color(0xFFE8501F),
    downvote = Color(0xFF586BD7),
    saved = Color(0xFFD99E18),
    spoiler = Color(0xFFE2E6EA),
    mediaBlue = Color(0xFF287CAF),
)

val LocalOtterColors = staticCompositionLocalOf { DarkOtterColors }

val MaterialTheme.otterColors: OtterColors
    @Composable get() = LocalOtterColors.current

private fun materialColors(colors: OtterColors, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color(0xFF001D33),
            secondary = colors.upvote,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.divider,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            secondary = colors.upvote,
            background = colors.canvas,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.divider,
        )
    }

private val OtterTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.1.sp,
    ),
)

private fun TextUnit.scaledBy(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * scale

private fun TextStyle.scaledBy(scale: Float): TextStyle = copy(
    fontSize = fontSize.scaledBy(scale),
    lineHeight = lineHeight.scaledBy(scale),
    letterSpacing = letterSpacing.scaledBy(scale),
)

/** Scales every Material type role so screens cannot accidentally opt out of accessibility. */
internal fun scaledTypography(scale: Float): Typography {
    val value = scale.coerceIn(.85f, 1.3f)
    return OtterTypography.copy(
        displayLarge = OtterTypography.displayLarge.scaledBy(value),
        displayMedium = OtterTypography.displayMedium.scaledBy(value),
        displaySmall = OtterTypography.displaySmall.scaledBy(value),
        headlineLarge = OtterTypography.headlineLarge.scaledBy(value),
        headlineMedium = OtterTypography.headlineMedium.scaledBy(value),
        headlineSmall = OtterTypography.headlineSmall.scaledBy(value),
        titleLarge = OtterTypography.titleLarge.scaledBy(value),
        titleMedium = OtterTypography.titleMedium.scaledBy(value),
        titleSmall = OtterTypography.titleSmall.scaledBy(value),
        bodyLarge = OtterTypography.bodyLarge.scaledBy(value),
        bodyMedium = OtterTypography.bodyMedium.scaledBy(value),
        bodySmall = OtterTypography.bodySmall.scaledBy(value),
        labelLarge = OtterTypography.labelLarge.scaledBy(value),
        labelMedium = OtterTypography.labelMedium.scaledBy(value),
        labelSmall = OtterTypography.labelSmall.scaledBy(value),
    )
}

@Composable
fun OtterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledTheme: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = when {
        amoledTheme -> AmoledOtterColors
        darkTheme -> DarkOtterColors
        else -> LightOtterColors
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalOtterColors provides colors) {
        MaterialTheme(
            colorScheme = materialColors(colors, darkTheme),
            typography = scaledTypography(textScale),
            content = content,
        )
    }
}
