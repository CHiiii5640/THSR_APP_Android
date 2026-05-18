package com.chiiii5640.thsrapp.features.trainResults

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.chiiii5640.thsrapp.core.model.TimelineStatusSummary
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.ui.layout.ThsrLayoutProfile
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens

private val timelineMarkerSize = 7.dp
private val timelineCurrentHaloSize = 12.dp
private val timelineLineHeight = 1.dp

@Composable
fun StopTimeline(
    option: TrainOption,
    layoutProfile: ThsrLayoutProfile,
) {
    if (option.stops.isEmpty()) {
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Top,
    ) {
        option.stops.forEachIndexed { index, stop ->
            Row(verticalAlignment = Alignment.Top) {
                StopNode(
                    stop = stop,
                    isCurrent = option.liveStatus.summary.currentStopIndex == index,
                    layoutProfile = layoutProfile,
                )
                if (index < option.stops.lastIndex) {
                    TimelineSegment(
                        from = stop,
                        to = option.stops[index + 1],
                        isActive = option.liveStatus.summary.activeSegmentIndex == index,
                        layoutProfile = layoutProfile,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopNode(
    stop: TimelineStop,
    isCurrent: Boolean,
    layoutProfile: ThsrLayoutProfile,
) {
    val tokens = ThsrDesignTokens
    val timeColor = if (isCurrent) {
        tokens.colors.textPrimary
    } else {
        tokens.colors.textSecondary.copy(alpha = 0.72f)
    }
    val stationColor = if (isCurrent) {
        tokens.colors.textPrimary
    } else {
        tokens.colors.textTertiary.copy(alpha = 0.78f)
    }

    Column(
        modifier = Modifier.width(layoutProfile.timelineNodeWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .size(timelineCurrentHaloSize)
                    .background(tokens.colors.primaryBlue.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(timelineMarkerSize)
                        .background(tokens.colors.textPrimary, CircleShape)
                        .border(1.dp, tokens.colors.primaryBlue.copy(alpha = 0.75f), CircleShape),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(timelineMarkerSize)
                    .border(1.dp, tokens.colors.primaryBlue.copy(alpha = 0.42f), CircleShape),
            )
        }

        Column(
            modifier = Modifier.width(layoutProfile.timelineLabelWidth),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stop.displayTime(),
                color = timeColor,
                style = tokens.typography.captionStrong,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stop.station.localName,
                color = stationColor,
                style = tokens.typography.caption,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
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
    isActive: Boolean,
    layoutProfile: ThsrLayoutProfile,
) {
    val tokens = ThsrDesignTokens
    val lineColor = if (isActive) {
        tokens.colors.primaryBlue.copy(alpha = 0.78f)
    } else {
        tokens.colors.primaryBlue.copy(alpha = 0.18f)
    }

    Box(
        modifier = Modifier
            .width(timelineSegmentWidth(from, to, layoutProfile))
            .height(timelineCurrentHaloSize),
        contentAlignment = Alignment.CenterStart,
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(timelineLineHeight)
                .background(lineColor),
        )
    }
}

private fun timelineSegmentWidth(
    from: TimelineStop,
    to: TimelineStop,
    layoutProfile: ThsrLayoutProfile,
) = layoutProfile.timelineSegmentUnitWidth * maxOf(kotlin.math.abs(to.station.sortIndex - from.station.sortIndex), 1)

private fun TimelineStop.displayTime(): String =
    departureTime?.let(ThsrFormatters::displayTimetableTime)
        ?: arrivalTime?.let(ThsrFormatters::displayTimetableTime)
        ?: "--:--"
