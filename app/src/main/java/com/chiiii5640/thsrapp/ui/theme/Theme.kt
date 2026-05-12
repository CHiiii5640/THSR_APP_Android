package com.chiiii5640.thsrapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C67),
    secondary = Color(0xFF735C00),
    tertiary = Color(0xFF8B3E52),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF55D7CE),
    secondary = Color(0xFFE6C44C),
    tertiary = Color(0xFFFFB1C4),
)

@Composable
fun ThsrAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
