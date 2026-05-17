package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TimelineStatusSummary
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TrainDataSource
import com.chiiii5640.thsrapp.core.model.TrainLiveStatus
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.model.TrainServiceState
import com.chiiii5640.thsrapp.ui.layout.rememberThsrLayoutProfile
import com.chiiii5640.thsrapp.ui.theme.ThsrAppTheme
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.LocalDate
import java.time.LocalTime

@Preview(
    name = "UltraDefault",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
private fun TrainResultsUltraDefaultPreview() {
    TrainResultsPreviewContent()
}

@Preview(
    name = "UltraLargeFont",
    widthDp = 412,
    heightDp = 915,
    fontScale = 1.3f,
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
private fun TrainResultsUltraLargeFontPreview() {
    TrainResultsPreviewContent()
}

@Preview(
    name = "UltraLandscape",
    widthDp = 915,
    heightDp = 412,
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
private fun TrainResultsUltraLandscapePreview() {
    TrainResultsPreviewContent()
}

@Composable
private fun TrainResultsPreviewContent() {
    val profile = rememberThsrLayoutProfile()
    val options = previewOptions()

    ThsrAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ThsrDesignTokens.colors.backgroundColor,
        ) {
            TrainResultsGroup(
                options = options,
                scheduledNotifications = emptyMap(),
                layoutProfile = profile,
                onScheduleNotification = { _, _ -> },
            )
        }
    }
}

private fun previewOptions(): List<TrainOption> {
    val travelDate = LocalDate.of(2026, 5, 17)
    val sharedStops = listOf(
        TimelineStop(station = Station.Taipei, arrivalTime = null, departureTime = LocalTime.of(9, 0)),
        TimelineStop(station = Station.Taoyuan, arrivalTime = LocalTime.of(9, 18), departureTime = LocalTime.of(9, 20)),
        TimelineStop(station = Station.Taichung, arrivalTime = LocalTime.of(9, 56), departureTime = LocalTime.of(9, 58)),
        TimelineStop(station = Station.Zuoying, arrivalTime = LocalTime.of(10, 35), departureTime = null),
    )

    return listOf(
        TrainOption(
            trainNo = "0821",
            origin = Station.Taipei,
            destination = Station.Zuoying,
            travelDate = travelDate,
            departureTime = LocalTime.of(9, 0),
            arrivalTime = LocalTime.of(10, 35),
            stops = sharedStops,
            bookingStatus = BookingStatus.Available,
            seatAvailability = null,
            discounts = emptyList(),
            source = TrainDataSource(
                timetable = SourceStatus("DailyTimetable live", SourceState.Live),
                seatAvailability = SourceStatus("seat availability cache", SourceState.Cache),
                discountFeed = SourceStatus("discount feed", SourceState.Live),
            ),
            liveStatus = TrainLiveStatus(
                serviceState = TrainServiceState.InTransit,
                summary = TimelineStatusSummary(
                    headline = "行進中",
                    detail = "桃園 -> 台中",
                    currentStopIndex = 1,
                    nextStopIndex = 2,
                    activeSegmentIndex = 1,
                ),
            ),
        ),
        TrainOption(
            trainNo = "0933",
            origin = Station.Taipei,
            destination = Station.Zuoying,
            travelDate = travelDate.plusDays(10),
            departureTime = LocalTime.of(11, 30),
            arrivalTime = LocalTime.of(13, 5),
            stops = sharedStops.mapIndexed { index, stop ->
                when (index) {
                    0 -> stop.copy(departureTime = LocalTime.of(11, 30))
                    1 -> stop.copy(arrivalTime = LocalTime.of(11, 48), departureTime = LocalTime.of(11, 50))
                    2 -> stop.copy(arrivalTime = LocalTime.of(12, 26), departureTime = LocalTime.of(12, 28))
                    else -> stop.copy(arrivalTime = LocalTime.of(13, 5), departureTime = null)
                }
            },
            bookingStatus = BookingStatus.NotYetOpen(openingDate = travelDate.plusDays(1)),
            seatAvailability = null,
            discounts = emptyList(),
            source = TrainDataSource(
                timetable = SourceStatus("DailyTimetable cache", SourceState.Cache),
                seatAvailability = SourceStatus("seat APIs skipped", SourceState.Unavailable),
                discountFeed = SourceStatus("discount feed", SourceState.Live),
            ),
            liveStatus = TrainLiveStatus(
                serviceState = TrainServiceState.NotDeparted,
                summary = TimelineStatusSummary(
                    headline = "未發車",
                    detail = "預計 11:30 自台北發車",
                    currentStopIndex = 0,
                    nextStopIndex = 0,
                    activeSegmentIndex = null,
                ),
            ),
        ),
    )
}
