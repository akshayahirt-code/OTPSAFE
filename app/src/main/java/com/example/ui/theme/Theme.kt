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
    primary = SafePrimary,
    secondary = SecureEmerald,
    tertiary = WarningAmber,
    background = ObsidianBgDark,
    surface = ObsidianSurfaceDark,
    onBackground = LightTextDark,
    onSurface = LightTextDark,
    surfaceVariant = ObsidianBorderDark,
    onPrimary = Color.White,
    onSecondary = ObsidianBgDark
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E3A8A),
    secondary = Color(0xFF0F766E),
    tertiary = Color(0xFFB45309),
    background = ObsidianBgLight,
    surface = ObsidianSurfaceLight,
    onBackground = LightTextLight,
    onSurface = LightTextLight,
    surfaceVariant = ObsidianBorderLight,
    onPrimary = Color.White,
    onSecondary = ObsidianBgLight
)

@Composable
fun OTPSecureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Follow system theme setting automatically
    dynamicColor: Boolean = false, // Disable dynamic system colors to preserve our beautiful custom theme branding
    content: @Composable () -> Unit,
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
