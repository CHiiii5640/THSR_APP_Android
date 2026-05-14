package com.chiiii5640.thsrapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class ThsrColorTokens(
    val backgroundColor: Color,
    val surfaceColor: Color,
    val cardColor: Color,
    val elevatedSurfaceColor: Color,
    val dividerColor: Color,
    val primaryBlue: Color,
    val successGreen: Color,
    val warningOrange: Color,
    val dangerRed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val outlineColor: Color,
)

@Immutable
data class ThsrSpacingTokens(
    val spacing4: Dp,
    val spacing8: Dp,
    val spacing12: Dp,
    val spacing16: Dp,
    val spacing20: Dp,
    val spacing24: Dp,
    val spacing32: Dp,
)

@Immutable
data class ThsrRadiusTokens(
    val cornerRadiusLarge: Dp,
    val cornerRadiusMedium: Dp,
    val cornerRadiusSmall: Dp,
    val chipRadius: Dp,
)

@Immutable
data class ThsrSizeTokens(
    val navigationExpandedHeight: Dp,
    val formRowHeight: Dp,
    val sourceRowHeight: Dp,
    val buttonHeight: Dp,
    val chipHeight: Dp,
    val disclosureIcon: Dp,
    val statusDot: Dp,
)

@Immutable
data class ThsrTypographyTokens(
    val largeTitle: TextStyle,
    val navTitle: TextStyle,
    val sectionLabel: TextStyle,
    val formLabel: TextStyle,
    val formValue: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val captionStrong: TextStyle,
    val pill: TextStyle,
    val action: TextStyle,
    val cardTrainNo: TextStyle,
    val cardTime: TextStyle,
    val cardRoute: TextStyle,
    val cardMeta: TextStyle,
)

object ThsrDesignTokens {
    val colors = ThsrColorTokens(
        backgroundColor = Color(0xFF000000),
        surfaceColor = Color(0xFF1C1C1E),
        cardColor = Color(0xFF1F1F22),
        elevatedSurfaceColor = Color(0xFF2C2C2E),
        dividerColor = Color.White.copy(alpha = 0.10f),
        primaryBlue = Color(0xFF0A84FF),
        successGreen = Color(0xFF30D158),
        warningOrange = Color(0xFFFF9F0A),
        dangerRed = Color(0xFFFF453A),
        textPrimary = Color.White,
        textSecondary = Color.White.copy(alpha = 0.64f),
        textTertiary = Color.White.copy(alpha = 0.50f),
        textDisabled = Color.White.copy(alpha = 0.38f),
        outlineColor = Color.White.copy(alpha = 0.09f),
    )

    val spacing = ThsrSpacingTokens(
        spacing4 = 4.dp,
        spacing8 = 8.dp,
        spacing12 = 10.dp,
        spacing16 = 14.dp,
        spacing20 = 16.dp,
        spacing24 = 18.dp,
        spacing32 = 24.dp,
    )

    val radii = ThsrRadiusTokens(
        cornerRadiusLarge = 20.dp,
        cornerRadiusMedium = 16.dp,
        cornerRadiusSmall = 10.dp,
        chipRadius = 999.dp,
    )

    val sizes = ThsrSizeTokens(
        navigationExpandedHeight = 56.dp,
        formRowHeight = 52.dp,
        sourceRowHeight = 44.dp,
        buttonHeight = 42.dp,
        chipHeight = 36.dp,
        disclosureIcon = 16.dp,
        statusDot = 7.dp,
    )

    val typography = ThsrTypographyTokens(
        largeTitle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp,
        ),
        navTitle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp,
            textAlign = TextAlign.Center,
        ),
        sectionLabel = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
        formLabel = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            letterSpacing = (-0.2).sp,
        ),
        formValue = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            letterSpacing = (-0.2).sp,
        ),
        body = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        bodyStrong = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        caption = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        captionStrong = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        pill = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.1).sp,
        ),
        action = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        ),
        cardTrainNo = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.8).sp,
        ),
        cardTime = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.8).sp,
        ),
        cardRoute = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        ),
        cardMeta = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 16.sp,
        ),
    )
}
