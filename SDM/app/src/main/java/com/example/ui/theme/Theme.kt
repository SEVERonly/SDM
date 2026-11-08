package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SdmDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = ObsidianSurfaceElevated,
    onPrimaryContainer = NeonCyan,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = ObsidianSurfaceHighlight,
    onSecondaryContainer = TextPrimary,
    tertiary = MatrixGreen,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianCardBorder,
    outlineVariant = ObsidianBorderSubtle,
    error = LaserRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SdmDarkColorScheme,
        typography = Typography,
        content = content
    )
}
