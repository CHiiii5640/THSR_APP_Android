package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.DiscountType
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationDefaults
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationSheet
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.LocalDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainOptionCard(
    option: TrainOption,
    onScheduleNotification: (TrainOption, LocalDateTime) -> Unit,
) {
    val tokens = ThsrDesignTokens
    val uriHandler = LocalUriHandler.current
    var showNotificationSheet by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }
    var expanded by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tokens.colors.cardColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.spacing16),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
                ) {
                    Text(
                        text = option.trainNo.padStart(4, '0'),
                        color = tokens.colors.textPrimary,
                        style = tokens.typography.cardTrainNo,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
                    ) {
                        Text(
                            text = ThsrFormatters.displayTimetableTime(option.departureTime),
                            color = tokens.colors.textPrimary,
                            style = tokens.typography.cardTime,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = tokens.colors.textTertiary,
                            modifier = Modifier.size(tokens.spacing.spacing16),
                        )
                        Text(
                            text = ThsrFormatters.displayTimetableTime(option.arrivalTime),
                            color = tokens.colors.textPrimary,
                            style = tokens.typography.cardTime,
                        )
                    }
                    Text(
                        text = "${option.origin.localName}  →  ${option.destination.localName}",
                        color = tokens.colors.textSecondary,
                        style = tokens.typography.cardRoute,
                    )
                }
                BookingStatusBadge(option.bookingStatus)
            }

            SeatStatusLine(option)

            if (option.discounts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
                ) {
                    option.discounts.forEach { discount ->
                        DiscountBadge(
                            label = discount.label,
                            color = when (discount.type) {
                                DiscountType.EarlyBird,
                                DiscountType.CollegeStudent -> tokens.colors.warningOrange
                                DiscountType.Other -> tokens.colors.textPrimary
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = tokens.colors.dividerColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceLink(
                    modifier = Modifier.weight(1f),
                    label = option.source.timetable.cardSourceLabel(),
                )
                FooterAction(
                    label = "前往訂票",
                    onClick = { uriHandler.openUri(option.officialBookingUrl) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "停靠 ${option.stops.size} 站",
                    color = tokens.colors.textPrimary,
                    style = tokens.typography.bodyStrong,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = option.durationLabel(),
                    color = tokens.colors.textTertiary,
                    style = tokens.typography.captionStrong,
                )
                Spacer(Modifier.width(tokens.spacing.spacing12))
                FooterAction(
                    label = if (expanded) "收起" else "查看",
                    onClick = { expanded = !expanded },
                    trailing = {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = tokens.colors.primaryBlue,
                            modifier = Modifier.size(tokens.sizes.disclosureIcon),
                        )
                    },
                )
            }

            if (option.bookingStatus == BookingStatus.NotYetOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FooterAction(
                        label = "通知",
                        onClick = { showNotificationSheet = true },
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = tokens.colors.primaryBlue,
                                modifier = Modifier.size(tokens.spacing.spacing20),
                            )
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
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
    val tokens = ThsrDesignTokens
    val tint = status.color()
    val icon = when (status) {
        BookingStatus.Available -> Icons.Outlined.CheckCircle
        BookingStatus.NotYetOpen -> Icons.Outlined.Schedule
        BookingStatus.Closed -> Icons.Outlined.ErrorOutline
    }
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing12,
                vertical = tokens.spacing.spacing4,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(tokens.spacing.spacing16),
            )
            Text(
                text = status.label(),
                color = tint,
                style = tokens.typography.pill,
            )
        }
    }
}

@Composable
private fun SeatStatusLine(option: TrainOption) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing12),
    ) {
        SeatStatusDot(option.seatStatus.color())
        Text(
            text = option.seatStatus.detailLabel(),
            color = option.seatStatus.color(),
            style = tokens.typography.bodyStrong,
        )
    }
}

@Composable
private fun SeatStatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(ThsrDesignTokens.sizes.statusDot)
            .background(color = color, shape = CircleShape),
    )
}

@Composable
private fun DiscountBadge(
    label: String,
    color: Color,
) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.elevatedSurfaceColor,
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.colors.outlineColor),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing12,
                vertical = tokens.spacing.spacing4,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalOffer,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(tokens.spacing.spacing16),
            )
            Text(
                text = label,
                color = color,
                style = tokens.typography.pill,
            )
        }
    }
}

@Composable
private fun SourceLink(
    modifier: Modifier = Modifier,
    label: String,
) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
    ) {
        Icon(
            imageVector = Icons.Outlined.TravelExplore,
            contentDescription = null,
            tint = tokens.colors.primaryBlue,
            modifier = Modifier.size(tokens.spacing.spacing20),
        )
        Text(
            text = label,
            color = tokens.colors.primaryBlue,
            style = tokens.typography.bodyStrong,
        )
    }
}

@Composable
private fun FooterAction(
    label: String,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = ThsrDesignTokens
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing4),
    ) {
        leading?.invoke()
        Text(
            text = label,
            color = tokens.colors.primaryBlue,
            style = tokens.typography.action,
            fontWeight = FontWeight.SemiBold,
        )
        trailing?.invoke()
    }
}

private fun BookingStatus.label(): String = when (this) {
    BookingStatus.Available -> "可訂位"
    BookingStatus.NotYetOpen -> "尚未開賣"
    BookingStatus.Closed -> "已過訂位期限"
}

private fun BookingStatus.color(): Color = when (this) {
    BookingStatus.Available -> ThsrDesignTokens.colors.successGreen
    BookingStatus.NotYetOpen -> ThsrDesignTokens.colors.primaryBlue
    BookingStatus.Closed -> ThsrDesignTokens.colors.warningOrange
}

private fun SeatStatus.color(): Color = when (this) {
    SeatStatus.Unknown -> ThsrDesignTokens.colors.textDisabled
    SeatStatus.Available -> ThsrDesignTokens.colors.successGreen
    SeatStatus.Limited -> ThsrDesignTokens.colors.warningOrange
    SeatStatus.SoldOut -> ThsrDesignTokens.colors.dangerRed
}

private fun SeatStatus.detailLabel(): String = when (this) {
    SeatStatus.Unknown -> "標準 未列入看板"
    SeatStatus.Available -> "標準 座位有餘"
    SeatStatus.Limited -> "標準 座位有限"
    SeatStatus.SoldOut -> "標準 TDX 顯示無座"
}

private fun com.chiiii5640.thsrapp.core.model.SourceStatus.cardSourceLabel(): String = when (state) {
    SourceState.Live -> when {
        label.contains("DailyTimetable", ignoreCase = true) -> "TDX 高鐵每日時刻表"
        label.contains("seat", ignoreCase = true) -> "TDX 座位狀態"
        label.contains("discount", ignoreCase = true) -> "優惠快取"
        else -> label
    }
    SourceState.Cache -> "TDX 高鐵每日時刻表（快取）"
    SourceState.Fallback -> "TDX GeneralTimetable（替代）"
    SourceState.Unavailable -> "資料來源暫時不可用"
}

private fun TrainOption.durationLabel(): String {
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
