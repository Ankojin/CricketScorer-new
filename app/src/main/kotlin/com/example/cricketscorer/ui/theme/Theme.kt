package com.example.cricketscorer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF001D36),
    primaryContainer = Color(0xFF00497E),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF003912),
    secondaryContainer = Color(0xFF00531E),
    onSecondaryContainer = Color(0xFF9DF59F),
    surface = Color(0xFF121212),
    background = Color(0xFF121212),
    onSurface = Color.White,
    onBackground = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF003366),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF003366),
    secondary = Color(0xFF00AA55),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6FFD5),
    onSecondaryContainer = Color(0xFF00210A),
    surface = Color.White,
    background = Color(0xFFF5F5F5),
    onSurface = Color.Black,
    onBackground = Color.Black
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
