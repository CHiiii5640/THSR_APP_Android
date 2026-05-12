package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.time.ThsrFormatters

@Composable
fun StopTimeline(stops: List<TimelineStop>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        stops.forEach { stop ->
            Text("${stop.station.localName}\n${stop.departureTime?.let(ThsrFormatters::time) ?: stop.arrivalTime?.let(ThsrFormatters::time).orEmpty()}")
        }
    }
}
