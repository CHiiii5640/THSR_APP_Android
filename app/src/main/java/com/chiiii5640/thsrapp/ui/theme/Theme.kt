package com.chiiii5640.thsrapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape

private val AppColorScheme = darkColorScheme(
    primary = ThsrDesignTokens.colors.primaryBlue,
    secondary = ThsrDesignTokens.colors.successGreen,
    tertiary = ThsrDesignTokens.colors.warningOrange,
    background = ThsrDesignTokens.colors.backgroundColor,
    surface = ThsrDesignTokens.colors.surfaceColor,
    surfaceVariant = ThsrDesignTokens.colors.cardColor,
    outline = ThsrDesignTokens.colors.outlineColor,
    onPrimary = ThsrDesignTokens.colors.textPrimary,
    onBackground = ThsrDesignTokens.colors.textPrimary,
    onSurface = ThsrDesignTokens.colors.textPrimary,
)

private val AppTypography = Typography(
    displayLarge = ThsrDesignTokens.typography.largeTitle,
    headlineSmall = ThsrDesignTokens.typography.navTitle,
    titleLarge = ThsrDesignTokens.typography.formLabel,
    titleMedium = ThsrDesignTokens.typography.bodyStrong,
    bodyLarge = ThsrDesignTokens.typography.body,
    bodyMedium = ThsrDesignTokens.typography.cardMeta,
    labelLarge = ThsrDesignTokens.typography.action,
    labelMedium = ThsrDesignTokens.typography.captionStrong,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(ThsrDesignTokens.radii.cornerRadiusSmall),
    medium = RoundedCornerShape(ThsrDesignTokens.radii.cornerRadiusMedium),
    large = RoundedCornerShape(ThsrDesignTokens.radii.cornerRadiusLarge),
)

@Composable
fun ThsrAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
