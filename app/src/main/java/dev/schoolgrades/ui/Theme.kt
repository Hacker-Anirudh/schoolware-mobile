@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.schoolgrades.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0489DB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E8FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF526171),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F5),
    onSecondaryContainer = Color(0xFF101C29),
    tertiary = Color(0xFF6B5A7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1DAFF),
    onTertiaryContainer = Color(0xFF251432),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91CCFF),
    onPrimary = Color(0xFF003353),
    primaryContainer = Color(0xFF004A75),
    onPrimaryContainer = Color(0xFFD0E8FF),
    secondary = Color(0xFFBAC8DA),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD6E4F5),
    tertiary = Color(0xFFD5BFE4),
    onTertiary = Color(0xFF3B2949),
    tertiaryContainer = Color(0xFF52405F),
    onTertiaryContainer = Color(0xFFF1DAFF),
)

@Composable
fun GradesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
