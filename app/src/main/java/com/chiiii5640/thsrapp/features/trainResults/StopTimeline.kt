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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.time.ThsrFormatters

private val TimelineBackground = Color(0xFF151517)
private val TimelineLine = Color(0xFF3A3A3C)
private val TimelineNode = Color(0xFF0A84FF)
private val TimelineSecondaryText = Color(0xFF8E8E93)

@Composable
fun StopTimeline(stops: List<TimelineStop>) {
    Surface(
        color = TimelineBackground,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "左右滑動查看完整停靠站",
                color = TimelineSecondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
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
    Column(
        modifier = Modifier.width(112.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(12.dp)
                    .background(TimelineNode, CircleShape),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(2.dp)
                        .background(TimelineLine),
                )
            }
        }
        Text(
            text = stop.station.localName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stop.displayTime(),
            color = TimelineSecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun TimelineStop.displayTime(): String =
    departureTime?.let(ThsrFormatters::time)
        ?: arrivalTime?.let(ThsrFormatters::time)
        ?: "--:--"
