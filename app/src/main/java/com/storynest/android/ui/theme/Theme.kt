package com.storynest.android.ui.theme

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

/** Soft night palette — used by the reader bedtime toggle. */
val NightInk = Color(0xFF1A1528)
val NightSurface = Color(0xFF241E38)
val NightCard = Color(0xFF2F2748)
val MoonCream = Color(0xFFF5E6C8)
val SoftGold = Color(0xFFE8B86D)
val Lavender = Color(0xFFB8A8E8)
val Mist = Color(0xFF9A90B8)

/** Cheerful daytime storybook accents. */
val SkyBlue = Color(0xFF7EC8E3)
val SoftCoral = Color(0xFFFF8A9A)
val SoftPink = Color(0xFFFFB6C8)
val Mint = Color(0xFF8FD9B6)
val Cream = Color(0xFFFFF8EE)
val SunnyYellow = Color(0xFFFFD56A)
val WarmInk = Color(0xFF3D2C5A)
val CardPeach = Color(0xFFFFE8DE)
val CardMint = Color(0xFFE4F7EF)
val CardSky = Color(0xFFE3F4FB)
val CardLavender = Color(0xFFF0E8FF)
val CardSunny = Color(0xFFFFF4D6)

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

/** Bright, playful default for kids (~9) + parents. */
private val LightColors = lightColorScheme(
    primary = Color(0xFFFF6B81),
    onPrimary = Color.White,
    secondary = Color(0xFF4DB8D9),
    onSecondary = Color.White,
    tertiary = Color(0xFF5ECF9A),
    onTertiary = Color.White,
    background = Cream,
    onBackground = WarmInk,
    surface = Color(0xFFFFFCF6),
    onSurface = WarmInk,
    surfaceVariant = CardPeach,
    onSurfaceVariant = Color(0xFF6B5A7A),
    outline = Color(0xFFD4C4B0),
    error = Color(0xFFD64545),
    onError = Color.White,
    primaryContainer = SoftPink,
    onPrimaryContainer = WarmInk,
    secondaryContainer = CardSky,
    onSecondaryContainer = WarmInk,
    tertiaryContainer = CardMint,
    onTertiaryContainer = WarmInk
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
    darkTheme: Boolean = false, // cheerful daytime kids UI by default
    content: @Composable () -> Unit
) {
    // App chrome is bright/storybook. Reader uses its own soft night toggle.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StoryTypography,
        content = content
    )
}
