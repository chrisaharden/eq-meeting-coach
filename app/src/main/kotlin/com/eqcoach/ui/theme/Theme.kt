package com.eqcoach.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = VerdictGreen,
    secondary = VerdictYellow,
    background = DarkBackground,
    onBackground = OnDarkBackground,
    surface = DarkBackground,
    onSurface = OnDarkBackground,
)

@Composable
fun EQCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
