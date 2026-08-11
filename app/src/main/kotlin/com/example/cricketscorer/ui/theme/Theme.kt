package com.example.cricketscorer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF74B9FF),
    onPrimary = Color(0xFF001D36),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF4FC3F7),
    onSecondary = Color(0xFF003366),
    secondaryContainer = Color(0xFF01579B),
    onSecondaryContainer = Color(0xFFB3E5FC),
    surface = Color(0xFF1B263B),
    background = Color(0xFF0A192F),
    onSurface = Color.White,
    onBackground = Color.White,
    error = Color(0xFFFF5252)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF001D3D), // Deep Midnight Navy
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF001D3D),
    secondary = Color(0xFF003566), // Royal Blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB3E5FC),
    onSecondaryContainer = Color(0xFF003566),
    tertiary = Color(0xFFFFC300), // Gold
    surface = Color.White,
    background = Color(0xFFF0F4F8), // Soft Blue-Grey
    onSurface = Color(0xFF1A1A1A),
    onBackground = Color(0xFF1A1A1A),
    error = Color(0xFFD32F2F)
)

@Composable
fun CricketScorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
            }
        }
    )
}
