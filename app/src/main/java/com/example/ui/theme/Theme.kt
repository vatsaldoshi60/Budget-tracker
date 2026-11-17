package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandSoftContainer,
    onPrimaryContainer = BrandOnSoftContainer,
    secondary = BrandDeep,
    onSecondary = Color.White,
    secondaryContainer = SoftSurfaceVariant,
    onSecondaryContainer = SlateInk,
    tertiary = AmberAccent,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = SlateInk,
    error = CoralExpense,
    onError = Color.White,
    errorContainer = CoralSoft,
    onErrorContainer = Color(0xFF881337),
    background = AlabasterBg,
    onBackground = SlateInk,
    surface = PureCard,
    onSurface = SlateInk,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = OutlineHairline,
    outlineVariant = OutlineHairline
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = BrandDeep,
    primaryContainer = DarkBrandContainer,
    onPrimaryContainer = BrandSoftContainer,
    secondary = BrandGlow,
    onSecondary = BrandDeep,
    secondaryContainer = ObsidianCardHigh,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF533408),
    onTertiaryContainer = AmberSoft,
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF881337),
    onErrorContainer = CoralSoft,
    background = ObsidianBg,
    onBackground = DarkTextPrimary,
    surface = ObsidianSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = ObsidianCardHigh,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkOutline,
    outlineVariant = DarkOutline
)

private val FinovaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FinovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FinovaTypography,
        shapes = FinovaShapes,
        content = content
    )
}
