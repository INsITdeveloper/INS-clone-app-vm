package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val InsDarkColorScheme = darkColorScheme(
    primary = InsCyanPrimary,
    onPrimary = Color(0xFF00262D),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFA6F2FF),
    secondary = InsIndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF282B6E),
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = InsVioletTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3B1F70),
    onTertiaryContainer = Color(0xFFEDE0FF),
    background = InsNavyDeep,
    onBackground = Color(0xFFE6EEF8),
    surface = InsSurfaceDark,
    onSurface = Color(0xFFE6EEF8),
    surfaceVariant = InsSurfaceElevated,
    onSurfaceVariant = Color(0xFF9FB0CC),
    outline = Color(0xFF324363),
    error = InsCrimsonDanger,
    onError = Color.White
)

private val InsLightColorScheme = lightColorScheme(
    primary = InsCyanLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2F5FF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = InsIndigoLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0FF),
    onSecondaryContainer = Color(0xFF14134A),
    tertiary = InsVioletLightTertiary,
    onTertiary = Color.White,
    background = InsLightBg,
    onBackground = Color(0xFF0E1726),
    surface = InsLightSurface,
    onSurface = Color(0xFF0E1726),
    surfaceVariant = InsLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = InsCrimsonDanger,
    onError = Color.White
)

val InsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek Cyber Dark theme for INS Virtual Space
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) InsDarkColorScheme else InsLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = InsShapes,
        content = content
    )
}
