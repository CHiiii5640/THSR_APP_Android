package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class BookingWindowCalculatorTest {
    private val zoneId = ZoneId.of("Asia/Taipei")

    @Test
    fun normalDaysCanBookTwentyNineInclusiveDates() {
        val calculator = calculator("2026-04-27T01:00:00Z")

        val status = calculator.bookingStatus(
            travelDate = LocalDate.of(2026, 5, 25),
            departureTime = LocalTime.NOON,
        )

        assertEquals(BookingStatus.Available, status)
    }

    @Test
    fun normalDaysPastWindowBecomeNotYetOpenWithEstimatedDate() {
        val calculator = calculator("2026-04-27T01:00:00Z")

        val status = calculator.bookingStatus(
            travelDate = LocalDate.of(2026, 5, 26),
            departureTime = LocalTime.NOON,
        )

        assertTrue(status is BookingStatus.NotYetOpen)
        assertEquals(LocalDate.of(2026, 4, 28), (status as BookingStatus.NotYetOpen).openingDate)
    }

    @Test
    fun fridayExtendsBookingThroughFourWeeksLaterSunday() {
        val calculator = calculator("2026-05-01T01:00:00Z")

        val status = calculator.bookingStatus(
            travelDate = LocalDate.of(2026, 5, 31),
            departureTime = LocalTime.NOON,
        )

        assertEquals(BookingStatus.Available, status)
    }

    @Test
    fun bookingClosesThirtyMinutesBeforeDeparture() {
        val calculator = calculator("2026-04-27T03:30:00Z")

        val status = calculator.bookingStatus(
            travelDate = LocalDate.of(2026, 4, 27),
            departureTime = LocalTime.NOON,
        )

        assertEquals(BookingStatus.Closed, status)
    }

    private fun calculator(instant: String): BookingWindowCalculator =
        BookingWindowCalculator(
            clock = Clock.fixed(Instant.parse(instant), zoneId),
        )
}
