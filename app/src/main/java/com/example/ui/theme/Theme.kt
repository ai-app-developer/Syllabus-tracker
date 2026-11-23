package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AccentColorOption {
  INDIGO,
}

@Immutable
data class BookBadgePalette(
  val background: Color,
  val tint: Color,
)

@Immutable
data class AppColors(
  val accentColor: AccentColorOption = AccentColorOption.INDIGO,
  val dynamicColorEnabled: Boolean = false,
  val isAmoled: Boolean = false,
  val isGlass: Boolean = false,
  val background: Color = AppBackground,
  val screenBackground: Color = AppBackground,
  val surface: Color = CardSurface,
  val surfaceVariant: Color = AppBackground,
  val dialogSurface: Color = CardSurface,
  val inputSurface: Color = InputSurface,
  val border: Color = HairlineBorder,
  val tileBorder: Color = HairlineBorder,
  val softAccentBorder: Color = SoftLavenderBorder,
  val softAccentCardStart: Color = SoftLavenderCardStart,
  val softAccentCardEnd: Color = SoftLavenderCardEnd,
  val primary: Color = PrimaryIndigo,
  val primarySolid: Color = PrimaryIndigo,
  val primaryGradientTop: Color = VioletGradientTop,
  val primaryGradientBottom: Color = VioletGradientBottom,
  val onPrimary: Color = Color.White,
  val tint: Color = LightIndigoTint,
  val timerIconBadge: Color = CardSurface,
  val secondaryBar: Color = SecondaryIndigoBar,
  val progressTrack: Color = ProgressTrack,
  val ringGradientTop: Color = RingGradientTop,
  val ringGradientMid: Color = RingGradientMid,
  val ringGradientBottom: Color = RingGradientBottom,
  val normalBarTop: Color = VioletGradientTop,
  val normalBarBottom: Color = VioletGradientBottom,
  val setupTileStart: Color = SoftLavenderCardStart,
  val setupTileEnd: Color = SoftLavenderCardEnd,
  val dialWaveBack: Color = LightIndigoTint.copy(alpha = 0.7f),
  val dialWaveFront: Color = SecondaryIndigoBar.copy(alpha = 0.55f),
  val success: Color = SuccessGreen,
  val vibrantSuccess: Color = VibrantGreen,
  val successTint: Color = LightGreenTint,
  val successBorder: Color = LightGreenBorder,
  val warning: Color = StreakOrange,
  val warningTint: Color = SoftOrangeTint,
  val warningText: Color = WarningTextAmber,
  val error: Color = ErrorRed,
  val errorTint: Color = LightRedTint,
  val textPrimary: Color = TextPrimary,
  val textSecondary: Color = TextSecondary,
  val ambientShadow: Color = AmbientShadow,
  val switchThumb: Color = Color.White,
  val bookBadges: List<BookBadgePalette> =
    listOf(
      BookBadgePalette(background = BlueBadgeBg, tint = BlueBadgeTint),
      BookBadgePalette(background = LightGreenTint, tint = SuccessGreen),
      BookBadgePalette(background = SoftOrangeTint, tint = StreakOrange),
      BookBadgePalette(background = LightIndigoTint, tint = PrimaryIndigo),
    ),
  val donutColors: List<Color> =
    listOf(
      PrimaryIndigo,
      SuccessGreen,
      StreakOrange,
      BlueBadgeTint,
      RingGradientTop,
    ),
  val donutOtherColor: Color = TextSecondary,
)

object AppTheme {
  val colors: AppColors = AppColors()
}

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
