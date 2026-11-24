package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.papiai.domain.model.ThemeMode

private val DarkColorScheme = darkColorScheme(
  primary = TealPrimaryDark,
  onPrimary = OnTealDark,
  primaryContainer = TealContainerDark,
  onPrimaryContainer = OnTealContainerDark,
  secondary = TerracottaSecondaryDark,
  onSecondary = OnTerracottaDark,
  secondaryContainer = TerracottaContainerDark,
  onSecondaryContainer = OnTerracottaContainerDark,
  tertiary = SageTertiaryDark,
  onTertiary = OnSageDark,
  tertiaryContainer = SageContainerDark,
  onTertiaryContainer = OnSageContainerDark,
  background = BackgroundDark,
  onBackground = OnBackgroundDark,
  surface = SurfaceDark,
  onSurface = OnBackgroundDark,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = OnSurfaceVariantDark,
  outline = OutlineDark,
)

private val LightColorScheme = lightColorScheme(
  primary = TealPrimaryLight,
  onPrimary = OnTealLight,
  primaryContainer = TealContainerLight,
  onPrimaryContainer = OnTealContainerLight,
  secondary = TerracottaSecondaryLight,
  onSecondary = OnTerracottaLight,
  secondaryContainer = TerracottaContainerLight,
  onSecondaryContainer = OnTerracottaContainerLight,
  tertiary = SageTertiaryLight,
  onTertiary = OnSageLight,
  tertiaryContainer = SageContainerLight,
  onTertiaryContainer = OnSageContainerLight,
  background = BackgroundLight,
  onBackground = OnBackgroundLight,
  surface = SurfaceLight,
  onSurface = OnBackgroundLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = OnSurfaceVariantLight,
  outline = OutlineLight,
)

private val PapiShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PapiTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    isDark -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = PapiTypography,
    shapes = PapiShapes,
    content = content,
  )
}
