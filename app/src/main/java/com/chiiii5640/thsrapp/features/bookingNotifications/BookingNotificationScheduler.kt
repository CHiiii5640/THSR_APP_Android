package com.chiiii5640.thsrapp.features.bookingNotifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chiiii5640.thsrapp.core.model.TrainOption
import java.time.LocalDateTime
import java.time.ZoneId

class BookingNotificationScheduler(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.of("Asia/Taipei"),
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun schedule(option: TrainOption, reminderAt: LocalDateTime) {
        ensureChannel()
        val pendingIntent = pendingIntent(option)
        val triggerAtMillis = reminderAt.atZone(zoneId).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(option: TrainOption) {
        alarmManager.cancel(pendingIntent(option))
    }

    private fun pendingIntent(option: TrainOption): PendingIntent {
        val intent = Intent(context, BookingNotificationReceiver::class.java).apply {
            action = notificationId(option)
            putExtra("trainNo", option.trainNo)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId(option).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "開票通知", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val CHANNEL_ID = "booking-open"

        fun notificationId(option: TrainOption): String =
            "booking-open-${option.trainNo}-${option.travelDate}-${option.origin.code}-${option.destination.code}"
    }
}
