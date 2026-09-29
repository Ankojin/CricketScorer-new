package `in`.nrkmart.cricscore.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import `in`.nrkmart.cricscore.ui.theme.ExportLightColorScheme

@Composable
fun CaptureArea(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ExportLightColorScheme) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}
