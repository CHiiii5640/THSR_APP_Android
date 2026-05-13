package com.chiiii5640.thsrapp.features.trainResults

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens

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
                .padding(vertical = tokens.spacing.spacing12),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
        ) {
            Text(
                text = "左右滑動查看完整停靠站",
                color = tokens.colors.textSecondary,
                style = tokens.typography.caption,
                modifier = Modifier.padding(horizontal = tokens.spacing.spacing16),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.spacing16),
                verticalAlignment = Alignment.Top,
            ) {
                stops.forEachIndexed { index, stop ->
                    StopNode(
                        stop = stop,
                        isLast = index == stops.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopNode(
    stop: TimelineStop,
    isLast: Boolean,
) {
    val tokens = ThsrDesignTokens
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(tokens.sizes.statusDot)
                    .background(tokens.colors.primaryBlue, CircleShape),
            )
            if (!isLast) {
                Spacer(
                    modifier = Modifier
                        .width(88.dp)
                        .height(2.dp)
                        .background(tokens.colors.dividerColor),
                )
            }
        }
        Text(
            text = stop.station.localName,
            color = tokens.colors.textPrimary,
            style = tokens.typography.captionStrong,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stop.displayTime(),
            color = tokens.colors.textSecondary,
            style = tokens.typography.caption,
        )
    }
}

private fun TimelineStop.displayTime(): String =
    departureTime?.let(ThsrFormatters::displayTimetableTime)
        ?: arrivalTime?.let(ThsrFormatters::displayTimetableTime)
        ?: "--:--"
