package com.chiiii5640.thsrapp.features.bookingNotifications

import com.chiiii5640.thsrapp.core.model.TrainOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object BookingNotificationDefaults {
    private val defaultReminderTime: LocalTime = LocalTime.of(23, 55)

    fun estimatedOpeningDate(option: TrainOption): LocalDate =
        option.bookingNotificationOpeningDate ?: option.travelDate.minusDays(28)

    fun reminderAt(option: TrainOption): LocalDateTime =
        estimatedOpeningDate(option)
            .minusDays(1)
            .atTime(defaultReminderTime)
}
