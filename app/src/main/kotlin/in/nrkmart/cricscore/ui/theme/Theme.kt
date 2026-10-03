package `in`.nrkmart.cricscore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// CricLeague web tokens (exact)
val WebBg = Color(0xFF041326)            // #041326 bg
val WebSurface = Color(0xFF071B33)       // #071B33 surface
val WebSoft = Color(0xFF0D2A4A)          // #0D2A4A soft
val WebBorder = Color(0xFF29415B)        // #29415B border
val WebPrimaryGreen = Color(0xFF13A968)  // #13A968 primary green (4s chip)
val WebElectricGreen = Color(0xFF49E878) // #49E878 electric (6s chip)
val WebLiveGreen = Color(0xFF39F57A)     // #39F57A live
val WebText = Color(0xFFF8FAFC)          // #F8FAFC text
val WebMuted = Color(0xFFB5C4D5)         // #B5C4D5 muted
val WebError = Color(0xFFD92D20)         // #D92D20 error (Wicket chip)
val WebWarning = Color(0xFFF59E0B)       // #F59E0B warning (Wide/NB chip)

// Chips
val Chip4Color = WebPrimaryGreen
val Chip6Color = WebElectricGreen
val ChipWicketColor = WebError
val ChipExtraColor = WebWarning

val DarkColorScheme = darkColorScheme(
    primary = WebPrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = WebSoft,
    onPrimaryContainer = WebElectricGreen,
    secondary = WebElectricGreen,
    onSecondary = WebBg,
    secondaryContainer = WebSoft,
    onSecondaryContainer = WebText,
    tertiary = WebLiveGreen,
    onTertiary = WebBg,
    surface = WebSurface,
    onSurface = WebText,
    surfaceVariant = WebSoft,
    onSurfaceVariant = WebMuted,
    background = WebBg,
    onBackground = WebText,
    outline = WebBorder,
    outlineVariant = WebBorder,
    error = WebError,
    onError = Color.White
)

val LightColorScheme = DarkColorScheme // Unified Navy + Electric Green look across light and dark mode

val ExportLightColorScheme = lightColorScheme(
    primary = WebPrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = WebPrimaryGreen,
    onSecondary = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF4F6F9),
    onSurfaceVariant = Color(0xFF555555),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF1A1A1A),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFE1E4E8)
)

@Composable
fun CricketScorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                content()
            }
        }
    )
}
