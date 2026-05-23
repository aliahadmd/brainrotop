package com.aliahad.brainrotop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Color0B1C15 = Color(0xFF0B1C15)
private val Color123427 = Color(0xFF123427)
private val ColorC9F1DF = Color(0xFFC9F1DF)
private val Color251703 = Color(0xFF251703)
private val Color3F2D0B = Color(0xFF3F2D0B)
private val ColorFFE2A6 = Color(0xFFFFE2A6)
private val Color3C0C05 = Color(0xFF3C0C05)
private val Color202B26 = Color(0xFF202B26)
private val ColorB8C8BF = Color(0xFFB8C8BF)
private val Color541910 = Color(0xFF541910)
private val ColorFFDAD2 = Color(0xFFFFDAD2)
private val Color75857C = Color(0xFF75857C)
private val ColorDDF4E8 = Color(0xFFDDF4E8)
private val Color103728 = Color(0xFF103728)
private val ColorFFE8B6 = Color(0xFFFFE8B6)
private val Color382604 = Color(0xFF382604)
private val ColorE5ECE7 = Color(0xFFE5ECE7)
private val Color53615A = Color(0xFF53615A)
private val Color3D0903 = Color(0xFF3D0903)
private val Color75847C = Color(0xFF75847C)

private val DarkColorScheme = darkColorScheme(
    primary = ForestDark,
    onPrimary = Color0B1C15,
    primaryContainer = Color123427,
    onPrimaryContainer = ColorC9F1DF,
    secondary = AmberDark,
    onSecondary = Color251703,
    secondaryContainer = Color3F2D0B,
    onSecondaryContainer = ColorFFE2A6,
    tertiary = RustDark,
    onTertiary = Color3C0C05,
    background = Night,
    onBackground = InkDark,
    surface = Charcoal,
    onSurface = InkDark,
    surfaceVariant = Color202B26,
    onSurfaceVariant = ColorB8C8BF,
    error = RustDark,
    errorContainer = Color541910,
    onErrorContainer = ColorFFDAD2,
    outline = Color75857C,
)

private val LightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Paper,
    primaryContainer = ColorDDF4E8,
    onPrimaryContainer = Color103728,
    secondary = Amber,
    onSecondary = Paper,
    secondaryContainer = ColorFFE8B6,
    onSecondaryContainer = Color382604,
    tertiary = Rust,
    onTertiary = Paper,
    background = Fog,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = ColorE5ECE7,
    onSurfaceVariant = Color53615A,
    error = Rust,
    errorContainer = ColorFFDAD2,
    onErrorContainer = Color3D0903,
    outline = Color75847C,
)

@Composable
fun BrainrotopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
