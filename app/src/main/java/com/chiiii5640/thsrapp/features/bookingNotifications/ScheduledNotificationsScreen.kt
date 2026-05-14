package com.chiiii5640.thsrapp.features.bookingNotifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScheduledNotificationsScreen(
    notifications: List<ScheduledBookingNotification>,
    onCancel: (String) -> Unit,
) {
    if (notifications.isEmpty()) {
        Text(
            text = "尚未設定通知",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        notifications.forEach { notification ->
            ScheduledNotificationRow(notification, onCancel)
        }
    }
}

@Composable
private fun ScheduledNotificationRow(
    notification: ScheduledBookingNotification,
    onCancel: (String) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("車次 ${notification.trainNo}")
                Text("${notification.originName} → ${notification.destinationName}")
                Text("搭乘 ${notification.travelDate}  通知 ${notification.reminderAt}")
            }
            Button(onClick = { onCancel(notification.id) }) {
                Text("取消")
            }
        }
    }
}
