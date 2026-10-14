package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = IndigoPrimary,
  onPrimary = IndigoOnPrimary,
  primaryContainer = Color(0xFF312E81),
  onPrimaryContainer = Color(0xFFE0E7FF),
  secondary = CyanSecondary,
  onSecondary = CyanOnSecondary,
  secondaryContainer = Color(0xFF155E75),
  onSecondaryContainer = Color(0xFFCFFAFE),
  tertiary = RoseTertiary,
  background = DarkSurface,
  surface = DarkCard,
  onBackground = Color(0xFFF8FAFC),
  onSurface = Color(0xFFF8FAFC),
  surfaceVariant = Color(0xFF1E293B),
  onSurfaceVariant = Color(0xFF94A3B8)
)

private val LightColorScheme = lightColorScheme(
  primary = IndigoPrimary,
  onPrimary = IndigoOnPrimary,
  primaryContainer = IndigoPrimaryContainer,
  onPrimaryContainer = IndigoOnPrimaryContainer,
  secondary = CyanSecondary,
  onSecondary = CyanOnSecondary,
  secondaryContainer = CyanSecondaryContainer,
  onSecondaryContainer = CyanOnSecondaryContainer,
  tertiary = RoseTertiary,
  background = LightSurface,
  surface = LightCard,
  onBackground = Color(0xFF0F172A),
  onSurface = Color(0xFF0F172A),
  surfaceVariant = Color(0xFFF1F5F9),
  onSurfaceVariant = Color(0xFF64748B)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Keep consistent branding colors
  content: @Composable () -> Unit
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
