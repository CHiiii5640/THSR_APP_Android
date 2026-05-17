package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.TimelineStatusSummary
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.model.TrainServiceState
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.ui.layout.ThsrLayoutProfile
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens

private val timelineMarkerSize = 10.dp
private val timelineLineHeight = 2.dp

private enum class TimelineNodeRole {
    Past,
    Current,
    Next,
    Future,
}

private data class TimelineNodeVisual(
    val role: TimelineNodeRole,
    val markerColor: Color,
    val markerFilled: Boolean,
    val labelColor: Color,
    val stationColor: Color,
    val scale: Float,
)

@Composable
fun StopTimeline(
    option: TrainOption,
    layoutProfile: ThsrLayoutProfile,
) {
    val stops = option.stops
    if (stops.isEmpty()) {
        return
    }

    val tokens = ThsrDesignTokens
    val summary = option.liveStatus.summary
    val serviceState = option.liveStatus.serviceState

    Surface(
        color = tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusMedium),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.spacing8),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
        ) {
            TimelineSummaryHeader(
                summary = summary,
                helper = "左右滑動查看完整停靠站",
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.spacing12),
                verticalAlignment = Alignment.Top,
            ) {
                stops.forEachIndexed { index, stop ->
                    val visual = timelineNodeVisual(
                        index = index,
                        lastIndex = stops.lastIndex,
                        summary = summary,
                        serviceState = serviceState,
                    )
                    Row(verticalAlignment = Alignment.Top) {
                        StopNode(
                            stop = stop,
                            visual = visual,
                            layoutProfile = layoutProfile,
                        )
                        if (index < stops.lastIndex) {
                            TimelineSegment(
                                from = stop,
                                to = stops[index + 1],
                                index = index,
                                summary = summary,
                                layoutProfile = layoutProfile,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineSummaryHeader(
    summary: TimelineStatusSummary,
    helper: String,
) {
    val tokens = ThsrDesignTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.spacing12),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing4),
    ) {
        Text(
            text = summary.headline,
            color = tokens.colors.textPrimary,
            style = tokens.typography.bodyStrong,
        )
        Text(
            text = summary.detail,
            color = tokens.colors.textSecondary,
            style = tokens.typography.caption,
        )
        Text(
            text = helper,
            color = tokens.colors.textTertiary,
            style = tokens.typography.caption,
        )
    }
}

@Composable
private fun StopNode(
    stop: TimelineStop,
    visual: TimelineNodeVisual,
    layoutProfile: ThsrLayoutProfile,
) {
    val tokens = ThsrDesignTokens
    val animatedScale by animateFloatAsState(
        targetValue = visual.scale,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = 0.82f,
        ),
        label = "timeline-node-scale",
    )

    Column(
        modifier = Modifier
            .width(layoutProfile.timelineNodeWidth)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(timelineMarkerSize)
                    .then(
                        if (visual.markerFilled) {
                            Modifier
                                .clip(CircleShape)
                                .background(visual.markerColor)
                        } else {
                            Modifier.border(2.dp, visual.markerColor, CircleShape)
                        },
                    ),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.width(layoutProfile.timelineLabelWidth),
        ) {
            Text(
                text = stop.displayTime(),
                color = visual.labelColor,
                style = tokens.typography.captionStrong,
                fontWeight = if (visual.role == TimelineNodeRole.Current) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stop.station.localName,
                color = visual.stationColor,
                style = tokens.typography.caption,
                fontWeight = if (visual.role == TimelineNodeRole.Current) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimelineSegment(
    from: TimelineStop,
    to: TimelineStop,
    index: Int,
    summary: TimelineStatusSummary,
    layoutProfile: ThsrLayoutProfile,
) {
    val tokens = ThsrDesignTokens
    val color = when {
        summary.activeSegmentIndex == index -> tokens.colors.warningOrange.copy(alpha = 0.90f)
        summary.currentStopIndex != null && index < summary.currentStopIndex -> tokens.colors.successGreen.copy(alpha = 0.72f)
        else -> tokens.colors.primaryBlue.copy(alpha = 0.62f)
    }

    Box(
        modifier = Modifier
            .width(timelineSegmentWidth(from, to, layoutProfile))
            .height(timelineMarkerSize),
        contentAlignment = Alignment.CenterStart,
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(timelineLineHeight)
                .background(color),
        )
    }
}

private fun timelineSegmentWidth(
    from: TimelineStop,
    to: TimelineStop,
    layoutProfile: ThsrLayoutProfile,
) = layoutProfile.timelineSegmentUnitWidth * maxOf(kotlin.math.abs(to.station.sortIndex - from.station.sortIndex), 1)

private fun timelineNodeVisual(
    index: Int,
    lastIndex: Int,
    summary: TimelineStatusSummary,
    serviceState: TrainServiceState,
): TimelineNodeVisual {
    val tokens = ThsrDesignTokens
    val role = timelineNodeRole(index, lastIndex, summary, serviceState)

    return when (role) {
        TimelineNodeRole.Past -> TimelineNodeVisual(
            role = role,
            markerColor = tokens.colors.successGreen,
            markerFilled = true,
            labelColor = tokens.colors.textSecondary,
            stationColor = tokens.colors.textTertiary,
            scale = 1f,
        )

        TimelineNodeRole.Current -> TimelineNodeVisual(
            role = role,
            markerColor = when (serviceState) {
                TrainServiceState.ArrivedDestination -> tokens.colors.successGreen
                TrainServiceState.DwellingAtStation,
                TrainServiceState.DepartingSoon,
                TrainServiceState.ApproachingStation,
                -> tokens.colors.warningOrange
                else -> tokens.colors.primaryBlue
            },
            markerFilled = true,
            labelColor = tokens.colors.textPrimary,
            stationColor = tokens.colors.textPrimary,
            scale = 1.08f,
        )

        TimelineNodeRole.Next -> TimelineNodeVisual(
            role = role,
            markerColor = tokens.colors.warningOrange,
            markerFilled = false,
            labelColor = tokens.colors.textPrimary,
            stationColor = tokens.colors.textSecondary,
            scale = 1.04f,
        )

        TimelineNodeRole.Future -> TimelineNodeVisual(
            role = role,
            markerColor = tokens.colors.primaryBlue.copy(alpha = 0.72f),
            markerFilled = false,
            labelColor = tokens.colors.textSecondary,
            stationColor = tokens.colors.textTertiary,
            scale = 1f,
        )
    }
}

private fun timelineNodeRole(
    index: Int,
    lastIndex: Int,
    summary: TimelineStatusSummary,
    serviceState: TrainServiceState,
): TimelineNodeRole {
    val current = summary.currentStopIndex
    val next = summary.nextStopIndex

    if (serviceState == TrainServiceState.ArrivedDestination && index == lastIndex) {
        return TimelineNodeRole.Current
    }
    if (current != null && index < current) {
        return TimelineNodeRole.Past
    }
    if (current != null && index == current) {
        return TimelineNodeRole.Current
    }
    if (next != null && index == next) {
        return TimelineNodeRole.Next
    }
    return TimelineNodeRole.Future
}

private fun TimelineStop.displayTime(): String =
    departureTime?.let(ThsrFormatters::displayTimetableTime)
        ?: arrivalTime?.let(ThsrFormatters::displayTimetableTime)
        ?: "--:--"
