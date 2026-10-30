package com.antiscrolling.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Toujours sombre : le style néon n'a pas de version claire.
private val colors = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Neon.Bg,
    secondary = Neon.Magenta,
    onSecondary = Neon.Bg,
    tertiary = Neon.Yellow,
    onTertiary = Neon.Bg,
    background = Neon.Bg,
    onBackground = Neon.Text,
    surface = Neon.Bg,
    onSurface = Neon.Text,
    surfaceVariant = Neon.Panel,
    onSurfaceVariant = Neon.Soft,
    surfaceContainer = Neon.Panel,
    surfaceContainerHigh = Neon.Panel,
    surfaceContainerHighest = Neon.Panel,
    outline = Neon.Cyan.copy(alpha = 0.85f),
    outlineVariant = Neon.Off,
    error = Neon.Magenta,
)

private val square = RoundedCornerShape(0.dp)

@Composable
fun AntiscrollingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = Shapes(square, square, square, square, square)) {
        Surface(Modifier.fillMaxSize(), color = Neon.Bg) {
            Box(Modifier.safeDrawingPadding()) { content() }
        }
    }
}
