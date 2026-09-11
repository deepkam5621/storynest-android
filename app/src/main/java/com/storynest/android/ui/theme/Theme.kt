package com.storynest.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NightInk = Color(0xFF1A1528)
val NightSurface = Color(0xFF241E38)
val NightCard = Color(0xFF2F2748)
val MoonCream = Color(0xFFF5E6C8)
val SoftGold = Color(0xFFE8B86D)
val Lavender = Color(0xFFB8A8E8)
val Mist = Color(0xFF9A90B8)

private val DarkColors = darkColorScheme(
    primary = SoftGold,
    onPrimary = NightInk,
    secondary = Lavender,
    onSecondary = NightInk,
    tertiary = SoftGold,
    background = NightInk,
    onBackground = MoonCream,
    surface = NightSurface,
    onSurface = MoonCream,
    surfaceVariant = NightCard,
    onSurfaceVariant = Mist,
    outline = Mist.copy(alpha = 0.5f),
    error = Color(0xFFFFB4A8),
    onError = NightInk
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4A8A),
    onPrimary = Color.White,
    secondary = Color(0xFF7A5A2A),
    onSecondary = Color.White,
    background = Color(0xFFFFF8EE),
    onBackground = Color(0xFF2A2040),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF2A2040),
    surfaceVariant = Color(0xFFF0E6D8),
    onSurfaceVariant = Color(0xFF5A5068)
)

private val StoryTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 30.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun StoryNestTheme(
    darkTheme: Boolean = true, // cozy night-reader default
    content: @Composable () -> Unit
) {
    val forceNight = darkTheme || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (forceNight) DarkColors else LightColors,
        typography = StoryTypography,
        content = content
    )
}
