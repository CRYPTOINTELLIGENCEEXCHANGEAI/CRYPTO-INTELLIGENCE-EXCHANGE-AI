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
    onPrimary = MatteBlackBackground,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = EmeraldSoft,
    secondary = ExchangeYellow,
    onSecondary = MatteBlackBackground,
    secondaryContainer = ExchangeYellowDeep,
    onSecondaryContainer = ExchangeYellowSoft,
    tertiary = ExchangeYellowSoft,
    onTertiary = MatteBlackBackground,
    background = MatteBlackBackground,
    onBackground = IvoryText,
    surface = MatteBlackSurface,
    onSurface = IvoryText,
    surfaceVariant = MatteBlackSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = MatteBlackOutline,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldTint,
    onPrimaryContainer = EmeraldDeep,
    secondary = ExchangeYellow,
    onSecondary = ObsidianText,
    secondaryContainer = ExchangeYellowSoft,
    onSecondaryContainer = ExchangeYellowDeep,
    tertiary = EmeraldAccent,
    background = IvoryBackground,
    onBackground = ObsidianText,
    surface = PureSurface,
    onSurface = ObsidianText,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateSecondaryText,
    outline = OutlineSubtle,
    error = StatusError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyFundGlobalTheme(
    darkTheme: Boolean = true,
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
