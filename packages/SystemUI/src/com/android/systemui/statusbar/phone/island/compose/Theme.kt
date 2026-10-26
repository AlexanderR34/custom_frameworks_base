package com.android.systemui.statusbar.phone.island.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

object PixelTokens {
    val Green = Color(0xFF81C995)
    val GreenContainer = Color(0xFF1E3827)
    val OnGreenContainer = Color(0xFFA8E6B8)

    val Blue = Color(0xFF8AB4F8)
    val BlueContainer = Color(0xFF1F3558)
    val OnBlueContainer = Color(0xFFC2E7FF)

    val SkyBlue = Color(0xFF38BDF8)
    val SkyBlueContainer = Color(0xFF0C4A6E)

    val Yellow = Color(0xFFFDD663)
    val YellowContainer = Color(0xFF453810)
    val OnYellowContainer = Color(0xFFFFE082)

    val Orange = Color(0xFFFCAD70)
    val OrangeContainer = Color(0xFF422614)
    val OnOrangeContainer = Color(0xFFFFCCAA)

    val Red = Color(0xFFF28B82)
    val RedContainer = Color(0xFF4E1D1A)
    val OnRedContainer = Color(0xFFFFB4AB)

    val Purple = Color(0xFFC58AF9)
    val PurpleContainer = Color(0xFF3B2054)
    val OnPurpleContainer = Color(0xFFE8D0FF)

    val Cyan = Color(0xFF78D9EC)
    val CyanContainer = Color(0xFF133C45)
    val OnCyanContainer = Color(0xFFA6EEF9)

    val MusicAccent = Color(0xFFE8DEF8)
    val MusicAccentMuted = Color(0xFF938F99)

    val IslandBlack = Color(0xFF000000)
    val CardBackground = Color(0xFF18181A)
    val CardSurface = Color(0xFF232326)
    val CardSurfaceHigh = Color(0xFF2E2E32)
    val CardDivider = Color(0x24FFFFFF)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1A6)
    val TextMuted = Color(0xFF737377)
}

val IslandTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
private val LightColorScheme = lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)

@Composable
fun DynamicIslandLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(colorScheme = colorScheme, typography = IslandTypography, content = content)
}
