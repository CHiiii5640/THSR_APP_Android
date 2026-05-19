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
    val deepCardColor: Color,
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
    val statusPill: TextStyle,
    val timelineTime: TextStyle,
    val timelineStation: TextStyle,
)

@Immutable
data class ThsrOpacityTokens(
    val subduedText: Float,
    val mutedText: Float,
    val faintText: Float,
    val inactiveTimeline: Float,
)

@Immutable
data class ThsrTrainCardTokens(
    val collapsedBackground: Color,
    val expandedBackground: Color,
    val notificationHighlightBackground: Color,
    val notificationScheduledBackground: Color,
    val collapsedBorder: Color,
    val expandedBorder: Color,
    val notificationHighlightBorder: Color,
    val notificationScheduledBorder: Color,
    val itemVerticalSpacing: Dp,
    val horizontalActionSpacing: Dp,
    val verticalPadding: Dp,
)

@Immutable
data class ThsrTimelineTokens(
    val lineHeight: Dp,
    val activeLineHeight: Dp,
    val idleNodeSize: Dp,
    val nextNodeSize: Dp,
    val activeNodeSize: Dp,
    val activeHaloSize: Dp,
    val stationLabelGap: Dp,
    val revealThreshold: Dp,
    val leadingInset: Dp,
)

object ThsrDesignTokens {
    val colors = ThsrColorTokens(
        backgroundColor = Color(0xFF000000),
        surfaceColor = Color(0xFF1C1C1E),
        cardColor = Color(0xFF1F1F22),
        elevatedSurfaceColor = Color(0xFF2C2C2E),
        deepCardColor = Color(0xFF172433),
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
            fontWeight = FontWeight.SemiBold,
            fontSize = 25.sp,
            lineHeight = 27.sp,
            letterSpacing = (-0.35).sp,
        ),
        cardTime = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.18).sp,
        ),
        cardRoute = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 15.sp,
        ),
        cardMeta = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 15.sp,
        ),
        statusPill = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = (-0.1).sp,
        ),
        timelineTime = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        ),
        timelineStation = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
        ),
    )

    val opacity = ThsrOpacityTokens(
        subduedText = 0.82f,
        mutedText = 0.66f,
        faintText = 0.48f,
        inactiveTimeline = 0.38f,
    )

    val trainCard = ThsrTrainCardTokens(
        collapsedBackground = Color(0xFF1B1D22),
        expandedBackground = Color(0xFF1A2938),
        notificationHighlightBackground = Color(0xFF2B2117),
        notificationScheduledBackground = Color(0xFF18211B),
        collapsedBorder = Color.White.copy(alpha = 0.07f),
        expandedBorder = colors.primaryBlue.copy(alpha = 0.12f),
        notificationHighlightBorder = colors.warningOrange.copy(alpha = 0.18f),
        notificationScheduledBorder = colors.successGreen.copy(alpha = 0.14f),
        itemVerticalSpacing = 6.dp,
        horizontalActionSpacing = 4.dp,
        verticalPadding = 9.dp,
    )

    val timeline = ThsrTimelineTokens(
        lineHeight = 4.dp,
        activeLineHeight = 4.dp,
        idleNodeSize = 5.dp,
        nextNodeSize = 7.dp,
        activeNodeSize = 9.dp,
        activeHaloSize = 14.dp,
        stationLabelGap = 3.dp,
        revealThreshold = 36.dp,
        leadingInset = 18.dp,
    )
}
