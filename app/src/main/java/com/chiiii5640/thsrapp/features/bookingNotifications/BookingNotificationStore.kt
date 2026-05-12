package com.chiiii5640.thsrapp.features.bookingNotifications

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface BookingNotificationStore {
    fun list(): List<ScheduledBookingNotification>
    fun upsert(notification: ScheduledBookingNotification)
    fun remove(id: String)
}

class InMemoryBookingNotificationStore(
    initial: List<ScheduledBookingNotification> = emptyList(),
) : BookingNotificationStore {
    private val notifications = initial.associateBy { it.id }.toMutableMap()

    override fun list(): List<ScheduledBookingNotification> =
        notifications.values.sortedBy { it.reminderAt }

    override fun upsert(notification: ScheduledBookingNotification) {
        notifications[notification.id] = notification
    }

    override fun remove(id: String) {
        notifications.remove(id)
    }
}

class SharedPreferencesBookingNotificationStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : BookingNotificationStore {
    private val preferences = context.getSharedPreferences("booking_notifications", Context.MODE_PRIVATE)

    override fun list(): List<ScheduledBookingNotification> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<ScheduledBookingNotification>>(raw)
        }.getOrDefault(emptyList())
    }

    override fun upsert(notification: ScheduledBookingNotification) {
        val next = (list().associateBy { it.id } + (notification.id to notification))
            .values
            .sortedBy { it.reminderAt }
        preferences.edit().putString(KEY, json.encodeToString(next)).apply()
    }

    override fun remove(id: String) {
        val next = list().filterNot { it.id == id }
        preferences.edit().putString(KEY, json.encodeToString(next)).apply()
    }

    private companion object {
        const val KEY = "scheduled_booking_notifications"
    }
}
