package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationDefaults
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationSheet
import java.time.LocalDateTime

private val CardBackground = Color(0xFF1C1C1E)
private val MutedText = Color(0xFF8E8E93)
private val AccentBlue = Color(0xFF0A84FF)
private val WarningOrange = Color(0xFFFF9F0A)
private val SuccessGreen = Color(0xFF30D158)
private val OutlineGray = Color(0xFF2C2C2E)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainOptionCard(
    option: TrainOption,
    onScheduleNotification: (TrainOption, LocalDateTime) -> Unit,
) {
    var showNotificationSheet by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }
    var expanded by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = CardBackground,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = option.trainNo.padStart(4, '0'),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ThsrFormatters.time(option.departureTime),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "  →  ",
                            color = MutedText,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = ThsrFormatters.time(option.arrivalTime),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "${option.origin.localName}  →  ${option.destination.localName}",
                        color = MutedText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                BookingStatusBadge(option.bookingStatus)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetaChip(
                    label = option.seatStatus.label(),
                    color = option.seatStatus.color(),
                )
                option.discounts.forEach { discount ->
                    MetaChip(
                        label = discount.label,
                        color = if (discount.percentOff != null) WarningOrange else AccentBlue,
                    )
                }
                MetaChip(
                    label = option.durationLabel(),
                    color = MutedText,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = MutedText,
                        )
                    },
                )
                if (option.bookingStatus == BookingStatus.NotYetOpen) {
                    MetaChip(
                        label = "通知",
                        color = AccentBlue,
                        onClick = { showNotificationSheet = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = AccentBlue,
                            )
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "停靠 ${option.stops.size} 站",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "收起" else "查看",
                    color = AccentBlue,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起停靠站" else "展開停靠站",
                    tint = AccentBlue,
                )
            }

            AnimatedVisibility(visible = expanded) {
                StopTimeline(stops = option.stops)
            }
        }
    }

    if (showNotificationSheet) {
        BookingNotificationSheet(
            initialReminderAt = BookingNotificationDefaults.reminderAt(option),
            onDismiss = { showNotificationSheet = false },
            onConfirm = { reminderAt ->
                showNotificationSheet = false
                onScheduleNotification(option, reminderAt)
            },
        )
    }
}

@Composable
private fun BookingStatusBadge(status: BookingStatus) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, status.color()),
    ) {
        Text(
            text = status.label(),
            color = status.color(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MetaChip(
    label: String,
    color: Color,
    onClick: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        color = Color(0xFF242426),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineGray),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                icon()
            } else {
                Surface(color = color, shape = CircleShape) {
                    Spacer(Modifier.size(8.dp))
                }
            }
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun BookingStatus.label(): String = when (this) {
    BookingStatus.Available -> "可訂位"
    BookingStatus.NotYetOpen -> "未開放"
    BookingStatus.Closed -> "已過訂位期限"
}

private fun BookingStatus.color(): Color = when (this) {
    BookingStatus.Available -> SuccessGreen
    BookingStatus.NotYetOpen -> AccentBlue
    BookingStatus.Closed -> WarningOrange
}

private fun SeatStatus.label(): String = when (this) {
    SeatStatus.Unknown -> "座位未知"
    SeatStatus.Available -> "座位有餘"
    SeatStatus.Limited -> "座位有限"
    SeatStatus.SoldOut -> "售完"
}

private fun SeatStatus.color(): Color = when (this) {
    SeatStatus.Unknown -> MutedText
    SeatStatus.Available -> SuccessGreen
    SeatStatus.Limited -> WarningOrange
    SeatStatus.SoldOut -> Color(0xFFFF453A)
}

private fun TrainOption.durationLabel(): String {
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
