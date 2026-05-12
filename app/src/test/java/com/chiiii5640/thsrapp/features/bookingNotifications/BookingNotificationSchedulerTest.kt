package com.chiiii5640.thsrapp.features.bookingNotifications

import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TrainDataSource
import com.chiiii5640.thsrapp.core.model.TrainOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun storeUpsertReplacesSameNotificationAndSortsByReminderTime() {
        val store = InMemoryBookingNotificationStore()

        store.upsert(scheduled(id = "later", reminderAt = "2026-05-20T23:55"))
        store.upsert(scheduled(id = "same", reminderAt = "2026-05-19T23:55"))
        store.upsert(scheduled(id = "same", reminderAt = "2026-05-18T23:55"))

        assertEquals(listOf("same", "later"), store.list().map { it.id })
        assertEquals("2026-05-18T23:55", store.list().first().reminderAt)
    }

    @Test
    fun storeRemoveClearsPendingNotificationRecord() {
        val store = InMemoryBookingNotificationStore(
            listOf(scheduled(id = "booking-open-0803-2026-06-01-TPE-ZUY")),
        )

        store.remove("booking-open-0803-2026-06-01-TPE-ZUY")

        assertTrue(store.list().isEmpty())
    }

    private fun scheduled(
        id: String,
        reminderAt: String = "2026-05-31T23:55",
    ): ScheduledBookingNotification =
        ScheduledBookingNotification(
            id = id,
            trainNo = "0803",
            travelDate = "2026-06-01",
            originName = "台北",
            destinationName = "左營",
            reminderAt = reminderAt,
        )
}
