package com.vaibhavser.ttvodtomp4converter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Slate,
    secondary = Clay,
    tertiary = Pine,
    surface = Mist,
)

@Composable
fun TTVodToMp4ConverterTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}
