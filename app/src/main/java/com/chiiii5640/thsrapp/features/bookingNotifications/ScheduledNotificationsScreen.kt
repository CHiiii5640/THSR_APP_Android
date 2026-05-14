package com.chiiii5640.thsrapp.features.bookingNotifications

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScheduledNotificationsScreen(
    notifications: List<ScheduledBookingNotification>,
    onCancel: (String) -> Unit,
) {
    if (notifications.isEmpty()) {
        EmptyScheduledNotifications()
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        notifications.forEach { notification ->
            ScheduledNotificationRow(notification, onCancel)
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun ScheduledNotificationRow(
    notification: ScheduledBookingNotification,
    onCancel: (String) -> Unit,
) {
    val tokens = ThsrDesignTokens
    val shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onCancel(notification.id)
                true
            } else {
                false
            }
        },
    )
    val estimatedOpeningDate = notification.estimatedOpeningDate().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
    val backgroundColor by animateColorAsState(
        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
            tokens.colors.dangerRed.copy(alpha = 0.88f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(220),
        label = "scheduled-notification-delete-bg",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            modifier = Modifier.fillMaxWidth(),
            backgroundContent = {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                    color = backgroundColor,
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = "取消通知",
                            color = Color.White,
                            style = tokens.typography.action,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = tokens.colors.cardColor,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "高鐵 ${notification.trainNo.padStart(4, '0')} 開放訂票提醒",
                        color = tokens.colors.textPrimary,
                        style = tokens.typography.formLabel,
                    )
                    Text(
                        text = "${notification.originName}到${notification.destinationName} ${notification.travelDate.displayScheduledTravelDate()} 的車票預估於 $estimatedOpeningDate 開放訂票。",
                        color = tokens.colors.textSecondary,
                        style = tokens.typography.body,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            tint = tokens.colors.warningOrange,
                        )
                        Text(
                            text = "${notification.reminderAt.displayScheduledReminder()} 提醒",
                            color = tokens.colors.warningOrange,
                            style = tokens.typography.bodyStrong,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyScheduledNotifications() {
    val tokens = ThsrDesignTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.NotificationsNone,
            contentDescription = null,
            tint = tokens.colors.textDisabled,
        )
        Text(
            text = "目前沒有排程中的通知",
            color = tokens.colors.textPrimary,
            style = tokens.typography.largeTitle,
        )
        Text(
            text = "對班次向左滑動可設定開票通知。",
            color = tokens.colors.textSecondary,
            style = tokens.typography.body,
        )
    }
}

private fun String.displayScheduledTravelDate(): String =
    LocalDate.parse(this).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))

private fun String.displayScheduledReminder(): String =
    LocalDateTime.parse(this).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))

private fun ScheduledBookingNotification.estimatedOpeningDate(): LocalDate =
    LocalDate.parse(reminderAt.substringBefore('T')).plusDays(1)
