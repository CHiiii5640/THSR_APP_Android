package com.chiiii5640.thsrapp.features.bookingNotifications

import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TrainDataSource
import com.chiiii5640.thsrapp.core.model.TrainOption
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BookingNotificationSchedulerTest {
    @Test
    fun notificationIdMatchesIosRule() {
        val option = TrainOption(
            trainNo = "0803",
            origin = Station.Taipei,
            destination = Station.Zuoying,
            travelDate = LocalDate.of(2026, 6, 1),
            departureTime = LocalTime.of(8, 0),
            arrivalTime = LocalTime.of(10, 0),
            stops = emptyList(),
            bookingStatus = BookingStatus.NotYetOpen,
            seatStatus = SeatStatus.Unknown,
            discounts = emptyList(),
            source = TrainDataSource(
                SourceStatus("timetable", SourceState.Live),
                SourceStatus("seat", SourceState.Unavailable),
                SourceStatus("discount", SourceState.Live),
            ),
        )

        assertEquals(
            "booking-open-0803-2026-06-01-TPE-ZUY",
            BookingNotificationScheduler.notificationId(option),
        )
    }
}
