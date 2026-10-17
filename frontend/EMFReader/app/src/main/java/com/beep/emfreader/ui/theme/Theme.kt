package com.beep.emfreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EmfColorScheme = darkColorScheme(
    primary = EmfText,
    onPrimary = EmfBackground,
    secondary = EmfText,
    onSecondary = EmfBackground,
    background = EmfBackground,
    onBackground = EmfText,
    surface = EmfBackground,
    onSurface = EmfText,
    surfaceVariant = EmfBackground,
    onSurfaceVariant = EmfTextMuted,
    outline = EmfTextMuted,
    outlineVariant = EmfTextMuted
)

@Composable
fun EMFReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EmfColorScheme,
        typography = EmfTypography,
        content = content
    )
}
