package com.example.cricketscorer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.cricketscorer.ui.theme.LightColorScheme

@Composable
fun CaptureArea(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme) {
        Surface(color = Color.White, contentColor = Color.Black) {
            content()
        }
    }
}
