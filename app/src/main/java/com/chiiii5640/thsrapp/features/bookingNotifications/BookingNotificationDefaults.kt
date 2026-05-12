package com.chiiii5640.thsrapp.features.bookingNotifications

import com.chiiii5640.thsrapp.core.model.TrainOption
import java.time.LocalDateTime
import java.time.LocalTime

object BookingNotificationDefaults {
    private val defaultReminderTime: LocalTime = LocalTime.of(23, 55)

    fun reminderAt(option: TrainOption): LocalDateTime =
        option.travelDate
            .minusDays(29)
            .atTime(defaultReminderTime)
}
