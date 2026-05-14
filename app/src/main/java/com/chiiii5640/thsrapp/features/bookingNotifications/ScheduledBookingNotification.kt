package com.chiiii5640.thsrapp.features.bookingNotifications

import kotlinx.serialization.Serializable

@Serializable
data class ScheduledBookingNotification(
    val id: String,
    val trainNo: String,
    val travelDate: String,
    val originName: String,
    val destinationName: String,
    val reminderAt: String,
    val openingDate: String? = null,
)
