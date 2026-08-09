package com.tatoli.habittracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// App ist immer dunkel (analog zur Web-App, die keinen hellen Modus hat) —
// keine isSystemInDarkTheme()-Verzweigung nötig.
private val HabitTrackerColorScheme = darkColorScheme(
    background = Bg,
    surface = Card,
    outline = CardEdge,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    primary = Amber,
    secondary = AmberDim,
    onPrimary = Bg,
    primaryContainer = Amber,
    onPrimaryContainer = Bg,
    secondaryContainer = AmberDim,
    onSecondaryContainer = Ink,
    surfaceContainerLow = Card,
    surfaceContainer = Card,
    surfaceVariant = Card
)

@Composable
fun HabitTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HabitTrackerColorScheme,
        content = content
    )
}
