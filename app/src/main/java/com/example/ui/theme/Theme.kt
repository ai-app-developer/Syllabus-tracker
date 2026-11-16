package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SyllabusLightColorScheme =
  lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = LightIndigoTint,
    onPrimaryContainer = PrimaryIndigo,
    secondary = PrimaryIndigo,
    onSecondary = Color.White,
    secondaryContainer = LightIndigoTint,
    onSecondaryContainer = TextPrimary,
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppBackground,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder,
    outlineVariant = ProgressTrack,
    error = ErrorRed,
    onError = Color.White,
  )

val SyllabusShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun SyllabusTrackerTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = SyllabusLightColorScheme,
    typography = Typography,
    shapes = SyllabusShapes,
    content = content,
  )
}
