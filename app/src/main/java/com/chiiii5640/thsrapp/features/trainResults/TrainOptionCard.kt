package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.DiscountType
import com.chiiii5640.thsrapp.core.model.SeatAvailabilityDetail
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationDefaults
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationSheet
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.LocalDateTime

@Composable
fun TrainResultsGroup(
    options: List<TrainOption>,
    onScheduleNotification: (TrainOption, LocalDateTime) -> Unit,
) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.cardColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
        tonalElevation = 0.dp,
    ) {
        Column {
            Text(
                text = "${options.size} 班符合條件",
                color = tokens.colors.textSecondary,
                style = tokens.typography.sectionLabel,
                modifier = Modifier.padding(
                    start = tokens.spacing.spacing16,
                    top = tokens.spacing.spacing12,
                    end = tokens.spacing.spacing16,
                    bottom = tokens.spacing.spacing12,
                ),
            )
            HorizontalDivider(color = tokens.colors.dividerColor)
            options.forEachIndexed { index, option ->
                TrainOptionCard(
                    option = option,
                    onScheduleNotification = onScheduleNotification,
                )
                if (index != options.lastIndex) {
                    HorizontalDivider(
                        color = tokens.colors.dividerColor,
                        modifier = Modifier.padding(horizontal = tokens.spacing.spacing16),
                    )
                }
            }
        }
    }
}

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

    Column(
        modifier = Modifier.padding(tokens.spacing.spacing16),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = option.trainNo.padStart(4, '0'),
                color = tokens.colors.textPrimary,
                style = tokens.typography.cardTrainNo,
            )
            Spacer(Modifier.weight(1f))
            BookingStatusBadge(status = option.bookingStatus, expanded = expanded) {
                expanded = !expanded
            }
        }

        TimeRouteRow(option = option)

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            StopTimeline(stops = option.stops)
        }

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

        option.seatAvailability?.let { seatAvailability ->
            SeatAvailabilityBlock(seatAvailability)
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

        if (!expanded) {
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
                if (option.bookingStatus == BookingStatus.NotYetOpen) {
                    FooterAction(
                        label = "通知",
                        onClick = { showNotificationSheet = true },
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = tokens.colors.primaryBlue,
                                modifier = Modifier.size(tokens.spacing.spacing16),
                            )
                        },
                    )
                    Spacer(Modifier.width(tokens.spacing.spacing12))
                }
                Text(
                    text = option.durationLabel(),
                    color = tokens.colors.textTertiary,
                    style = tokens.typography.captionStrong,
                )
                Spacer(Modifier.width(tokens.spacing.spacing12))
                FooterAction(
                    label = "查看",
                    onClick = { expanded = true },
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
        } else if (option.bookingStatus == BookingStatus.NotYetOpen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                FooterAction(
                    label = "通知",
                    onClick = { showNotificationSheet = true },
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            tint = tokens.colors.primaryBlue,
                            modifier = Modifier.size(tokens.spacing.spacing16),
                        )
                    },
                )
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
private fun TimeRouteRow(option: TrainOption) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        TimeStationColumn(
            time = ThsrFormatters.displayTimetableTime(option.departureTime),
            station = option.origin.localName,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = tokens.colors.textTertiary,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(tokens.spacing.spacing16),
        )
        Spacer(Modifier.width(tokens.spacing.spacing8))
        TimeStationColumn(
            time = ThsrFormatters.displayTimetableTime(option.arrivalTime),
            station = option.destination.localName,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TimeStationColumn(
    time: String,
    station: String,
    modifier: Modifier = Modifier,
) {
    val tokens = ThsrDesignTokens
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing4),
    ) {
        Text(
            text = time,
            color = tokens.colors.textPrimary,
            style = tokens.typography.cardTime,
        )
        Text(
            text = station,
            color = tokens.colors.textSecondary,
            style = tokens.typography.cardRoute,
        )
    }
}

@Composable
private fun BookingStatusBadge(
    status: BookingStatus,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    val tint = status.color()
    val icon = when (status) {
        BookingStatus.Available -> Icons.Outlined.CheckCircle
        BookingStatus.NotYetOpen -> Icons.Outlined.Schedule
        BookingStatus.Closed -> Icons.Outlined.ErrorOutline
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onToggleExpand,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
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
                style = tokens.typography.captionStrong,
            )
        }
        Spacer(Modifier.width(tokens.spacing.spacing8))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = tokens.colors.primaryBlue,
            modifier = Modifier
                .size(tokens.sizes.disclosureIcon)
                .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
        )
    }
}

@Composable
private fun SeatAvailabilityBlock(seatAvailability: SeatAvailabilityDetail) {
    val tokens = ThsrDesignTokens
    Column(
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
    ) {
        SeatStatusSourceRow(
            source = "OD",
            standard = seatAvailability.standardSeatStatus,
            business = seatAvailability.businessSeatStatus,
        )
        SeatStatusSourceRow(
            source = "看板",
            standard = if (seatAvailability.hasBoardSeatStatus) seatAvailability.boardStandardSeatStatus else SeatStatus.Unknown,
            business = if (seatAvailability.hasBoardSeatStatus) seatAvailability.boardBusinessSeatStatus else SeatStatus.Unknown,
        )
    }
}

@Composable
private fun SeatStatusSourceRow(
    source: String,
    standard: SeatStatus,
    business: SeatStatus,
) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing12),
    ) {
        Text(
            text = source,
            color = tokens.colors.textSecondary,
            style = tokens.typography.captionStrong,
            modifier = Modifier.width(28.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing12),
        ) {
            SeatStatusText(
                title = "標準",
                status = standard,
                modifier = Modifier.weight(1f),
            )
            SeatStatusText(
                title = "商務",
                status = business,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SeatStatusText(
    title: String,
    status: SeatStatus,
    modifier: Modifier = Modifier,
) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
    ) {
        Box(
            modifier = Modifier
                .size(tokens.sizes.statusDot)
                .background(color = status.color(), shape = CircleShape),
        )
        Text(
            text = "$title ${status.detailLabel()}",
            color = tokens.colors.textSecondary,
            style = tokens.typography.caption,
        )
    }
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
                horizontal = tokens.spacing.spacing8,
                vertical = tokens.spacing.spacing4,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalOffer,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                color = color,
                style = tokens.typography.captionStrong,
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
            modifier = Modifier.size(18.dp),
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
    SeatStatus.Unknown -> "未列入看板"
    SeatStatus.Available -> "座位有餘"
    SeatStatus.Limited -> "座位有限"
    SeatStatus.SoldOut -> "TDX 顯示無座"
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
