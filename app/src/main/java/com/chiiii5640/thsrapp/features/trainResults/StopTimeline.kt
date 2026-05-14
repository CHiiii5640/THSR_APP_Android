package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import kotlinx.coroutines.delay

private val timelineNodeWidth = 50.dp
private val timelineLabelWidth = 48.dp
private val timelineSegmentUnitWidth = 38.dp

@Composable
fun StopTimeline(stops: List<TimelineStop>) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusMedium),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.spacing8),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing4),
        ) {
            Text(
                text = "左右滑動查看完整停靠站",
                color = tokens.colors.textSecondary,
                style = tokens.typography.caption,
                modifier = Modifier.padding(horizontal = tokens.spacing.spacing12),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.spacing12),
                verticalAlignment = Alignment.Top,
            ) {
                stops.forEachIndexed { index, stop ->
                    Row(verticalAlignment = Alignment.Top) {
                        StopNode(
                            stop = stop,
                            index = index,
                            isEndpoint = index == 0 || index == stops.lastIndex,
                        )
                        if (index < stops.lastIndex) {
                            TimelineSegment(
                                from = stop,
                                to = stops[index + 1],
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopNode(
    stop: TimelineStop,
    index: Int,
    isEndpoint: Boolean,
) {
    val tokens = ThsrDesignTokens
    var visible by remember(stop.station, stop.arrivalTime, stop.departureTime) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "timeline-node",
    )
    LaunchedEffect(index, stop.station, stop.arrivalTime, stop.departureTime) {
        delay(index * 30L)
        visible = true
    }
    Column(
        modifier = Modifier
            .width(timelineNodeWidth)
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 8.dp.toPx()
                scaleX = 0.92f + (0.08f * progress)
                scaleY = 0.92f + (0.08f * progress)
            },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .then(
                        if (isEndpoint) {
                            Modifier.background(tokens.colors.primaryBlue, CircleShape)
                        } else {
                            Modifier.border(2.dp, tokens.colors.primaryBlue, CircleShape)
                        },
                    ),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.width(timelineLabelWidth),
        ) {
            Text(
                text = stop.displayTime(),
                color = tokens.colors.textPrimary,
                style = tokens.typography.captionStrong,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stop.station.localName,
                color = if (isEndpoint) tokens.colors.textPrimary else tokens.colors.textSecondary,
                style = tokens.typography.caption,
                fontWeight = if (isEndpoint) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun TimelineSegment(
    from: TimelineStop,
    to: TimelineStop,
) {
    val tokens = ThsrDesignTokens
    Spacer(
        modifier = Modifier
            .width(timelineSegmentWidth(from, to))
            .height(2.dp)
            .background(tokens.colors.primaryBlue.copy(alpha = 0.72f))
            .padding(top = 6.dp),
    )
}

private fun timelineSegmentWidth(
    from: TimelineStop,
    to: TimelineStop,
) = timelineSegmentUnitWidth * maxOf(kotlin.math.abs(to.station.sortIndex - from.station.sortIndex), 1)

private fun TimelineStop.displayTime(): String =
    departureTime?.let(ThsrFormatters::displayTimetableTime)
        ?: arrivalTime?.let(ThsrFormatters::displayTimetableTime)
        ?: "--:--"
