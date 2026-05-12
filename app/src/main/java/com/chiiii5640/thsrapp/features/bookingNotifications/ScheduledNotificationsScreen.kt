package com.chiiii5640.thsrapp.features.bookingNotifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        Text("尚未設定通知")
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(notifications, key = { it.id }) { notification ->
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
