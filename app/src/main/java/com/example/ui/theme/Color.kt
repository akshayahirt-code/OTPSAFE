package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Elegant Obsidian Dark Security Theme Palette - Raw Values
val ObsidianBgDark = Color(0xFF0A0C10)
val ObsidianSurfaceDark = Color(0xFF13171F)
val ObsidianBorderDark = Color(0xFF1F2633)
val SecureEmerald = Color(0xFF00E676)
val SafePrimary = Color(0xFF2979FF)
val WarningAmber = Color(0xFFFFB300)
val LightTextDark = Color(0xFFF1F5F9)
val MutedTextDark = Color(0xFF64748B)

val BubbleFromMeDark = Color(0xFF1E293B)
val BubbleToMeDark = Color(0xFF162E6B)

// Elegant Light Security Theme Palette - Raw Values
val ObsidianBgLight = Color(0xFFF8FAFC)
val ObsidianSurfaceLight = Color(0xFFFFFFFF)
val ObsidianBorderLight = Color(0xFFEFF1F5)
val LightTextLight = Color(0xFF0F172A)
val MutedTextLight = Color(0xFF64748B)

val BubbleFromMeLight = Color(0xFFE2E8F0)
val BubbleToMeLight = Color(0xFFDBEAFE)

val ObsidianBg: Color
    @Composable
    get() = if (isSystemInDarkTheme()) ObsidianBgDark else ObsidianBgLight

val ObsidianSurface: Color
    @Composable
    get() = if (isSystemInDarkTheme()) ObsidianSurfaceDark else ObsidianSurfaceLight

val ObsidianBorder: Color
    @Composable
    get() = if (isSystemInDarkTheme()) ObsidianBorderDark else ObsidianBorderLight

val LightText: Color
    @Composable
    get() = if (isSystemInDarkTheme()) LightTextDark else LightTextLight

val MutedText: Color
    @Composable
    get() = if (isSystemInDarkTheme()) MutedTextDark else MutedTextLight

val BubbleFromMe: Color
    @Composable
    get() = if (isSystemInDarkTheme()) BubbleFromMeDark else BubbleFromMeLight

val BubbleToMe: Color
    @Composable
    get() = if (isSystemInDarkTheme()) BubbleToMeDark else BubbleToMeLight
