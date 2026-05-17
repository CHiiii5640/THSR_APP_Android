package com.chiiii5640.thsrapp.ui.layout

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@Immutable
data class ThsrLayoutProfile(
    val widthSizeClass: WindowWidthSizeClass,
    val isLandscape: Boolean,
    val isLargeFont: Boolean,
    val contentMaxWidth: Dp,
    val contentHorizontalPadding: Dp,
    val cardContentHorizontalPadding: Dp,
    val sheetHorizontalPadding: Dp,
    val sheetTopPadding: Dp,
    val sectionSpacing: Dp,
    val timelineNodeWidth: Dp,
    val timelineLabelWidth: Dp,
    val timelineSegmentUnitWidth: Dp,
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberThsrLayoutProfile(): ThsrLayoutProfile {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    val heightDp = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isLargeFont = configuration.fontScale >= 1.2f

    val widthSizeClass = remember(widthDp, heightDp) {
        WindowSizeClass.calculateFromSize(DpSize(widthDp, heightDp)).widthSizeClass
    }

    return remember(widthSizeClass, widthDp, isLandscape, isLargeFont) {
        when {
            widthSizeClass == WindowWidthSizeClass.Expanded || widthDp >= 700.dp -> ThsrLayoutProfile(
                widthSizeClass = widthSizeClass,
                isLandscape = isLandscape,
                isLargeFont = isLargeFont,
                contentMaxWidth = 760.dp,
                contentHorizontalPadding = 24.dp,
                cardContentHorizontalPadding = 20.dp,
                sheetHorizontalPadding = 32.dp,
                sheetTopPadding = 28.dp,
                sectionSpacing = 14.dp,
                timelineNodeWidth = 56.dp,
                timelineLabelWidth = 54.dp,
                timelineSegmentUnitWidth = 44.dp,
            )

            widthSizeClass == WindowWidthSizeClass.Medium || widthDp >= 448.dp -> ThsrLayoutProfile(
                widthSizeClass = widthSizeClass,
                isLandscape = isLandscape,
                isLargeFont = isLargeFont,
                contentMaxWidth = 640.dp,
                contentHorizontalPadding = 18.dp,
                cardContentHorizontalPadding = 18.dp,
                sheetHorizontalPadding = 26.dp,
                sheetTopPadding = 26.dp,
                sectionSpacing = 12.dp,
                timelineNodeWidth = 52.dp,
                timelineLabelWidth = 50.dp,
                timelineSegmentUnitWidth = 40.dp,
            )

            widthDp >= 412.dp -> ThsrLayoutProfile(
                widthSizeClass = widthSizeClass,
                isLandscape = isLandscape,
                isLargeFont = isLargeFont,
                contentMaxWidth = 560.dp,
                contentHorizontalPadding = 16.dp,
                cardContentHorizontalPadding = if (isLargeFont) 14.dp else 16.dp,
                sheetHorizontalPadding = 22.dp,
                sheetTopPadding = 24.dp,
                sectionSpacing = 12.dp,
                timelineNodeWidth = 50.dp,
                timelineLabelWidth = 48.dp,
                timelineSegmentUnitWidth = 38.dp,
            )

            else -> ThsrLayoutProfile(
                widthSizeClass = widthSizeClass,
                isLandscape = isLandscape,
                isLargeFont = isLargeFont,
                contentMaxWidth = 520.dp,
                contentHorizontalPadding = 14.dp,
                cardContentHorizontalPadding = if (isLargeFont) 12.dp else 14.dp,
                sheetHorizontalPadding = 18.dp,
                sheetTopPadding = 22.dp,
                sectionSpacing = 10.dp,
                timelineNodeWidth = 46.dp,
                timelineLabelWidth = 44.dp,
                timelineSegmentUnitWidth = 34.dp,
            )
        }
    }
}
