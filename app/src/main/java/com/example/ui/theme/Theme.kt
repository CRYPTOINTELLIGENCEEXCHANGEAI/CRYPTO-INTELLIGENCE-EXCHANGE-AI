package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = ObsidianBackground,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = EmeraldSoft,
    secondary = GoldChampagne,
    onSecondary = ObsidianBackground,
    secondaryContainer = GoldDeep,
    onSecondaryContainer = GoldMuted,
    tertiary = EmeraldSoft,
    background = ObsidianBackground,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = OutlineDark,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldTint,
    onPrimaryContainer = EmeraldDeep,
    secondary = GoldChampagne,
    onSecondary = ObsidianText,
    secondaryContainer = GoldMuted,
    onSecondaryContainer = GoldDeep,
    tertiary = EmeraldPrimary,
    background = IvoryBackground,
    onBackground = ObsidianText,
    surface = PureSurface,
    onSurface = ObsidianText,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateSecondaryText,
    outline = OutlineSubtle,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorContainer
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyFundGlobalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
