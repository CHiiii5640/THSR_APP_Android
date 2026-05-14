package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.BookingStatus
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

class BookingWindowCalculator(
    private val clock: Clock,
) {
    fun bookingStatus(
        travelDate: LocalDate,
        departureTime: LocalTime,
    ): BookingStatus {
        val today = LocalDate.now(clock)
        val now = LocalTime.now(clock)

        if (travelDate.isBefore(today)) {
            return BookingStatus.Closed
        }

        if (travelDate == today && !now.isBefore(departureTime.minusMinutes(30))) {
            return BookingStatus.Closed
        }

        val latestBookableDate = latestBookableDate(today)
        if (!travelDate.isAfter(latestBookableDate)) {
            return BookingStatus.Available
        }

        return BookingStatus.NotYetOpen(
            openingDate = estimatedOpeningDate(
                travelDate = travelDate,
                today = today,
            ),
        )
    }

    fun estimatedOpeningDate(
        travelDate: LocalDate,
        today: LocalDate = LocalDate.now(clock),
    ): LocalDate {
        var candidate = travelDate.minusDays(28)

        while (latestBookableDate(candidate).isBefore(travelDate)) {
            candidate = candidate.plusDays(1)
        }

        return maxOf(today, candidate)
    }

    private fun latestBookableDate(today: LocalDate): LocalDate {
        val normalLimit = today.plusDays(28)
        return when (today.dayOfWeek.value) {
            5, 6 -> maxOf(normalLimit, firstSundayAtLeastFourWeeksAway(today))
            else -> normalLimit
        }
    }

    private fun firstSundayAtLeastFourWeeksAway(day: LocalDate): LocalDate {
        for (offset in 28..34) {
            val candidate = day.plusDays(offset.toLong())
            if (candidate.dayOfWeek.value == 7) {
                return candidate
            }
        }
        return day.plusDays(28)
    }
}
