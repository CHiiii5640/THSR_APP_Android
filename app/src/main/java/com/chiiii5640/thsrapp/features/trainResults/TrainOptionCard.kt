package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatAvailabilityDetail
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationDefaults
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationSheet
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationScheduler
import com.chiiii5640.thsrapp.features.bookingNotifications.ScheduledBookingNotification
import com.chiiii5640.thsrapp.ui.theme.ThsrColorTokens
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private data class TrainNotificationVisualState(
    val actionTint: Color,
    val cardTint: Color,
    val shouldShowBadge: Boolean,
)

private val bookingStatusDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Composable
fun TrainResultsGroup(
    options: List<TrainOption>,
    scheduledNotifications: Map<String, ScheduledBookingNotification>,
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
                    scheduledNotification = scheduledNotifications[BookingNotificationScheduler.notificationId(option)],
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrainOptionCard(
    option: TrainOption,
    scheduledNotification: ScheduledBookingNotification?,
    onScheduleNotification: (TrainOption, LocalDateTime) -> Unit,
) {
    val tokens = ThsrDesignTokens
    val coroutineScope = rememberCoroutineScope()
    var showNotificationSheet by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }
    var expanded by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }
    var highlightNotification by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }
    var pendingNotificationSheetBySwipe by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }
    val isNotificationScheduled = scheduledNotification != null
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && option.bookingStatus is BookingStatus.NotYetOpen) {
                highlightNotification = true
                pendingNotificationSheetBySwipe = true
            }
            false
        },
    )
    val notificationState = remember(isNotificationScheduled, highlightNotification, expanded) {
        trainNotificationVisualState(tokens.colors, isNotificationScheduled, highlightNotification, expanded)
    }
    val rowColor by animateColorAsState(
        targetValue = notificationState.cardTint,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "train-row-background",
    )
    val swipeBackgroundColor by animateColorAsState(
        targetValue = if (
            dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart ||
            pendingNotificationSheetBySwipe ||
            highlightNotification
        ) {
            tokens.colors.warningOrange.copy(alpha = 0.88f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 220),
        label = "train-row-swipe-background",
    )
    fun openNotificationSheet() {
        highlightNotification = true
        coroutineScope.launch {
            delay(180)
            showNotificationSheet = true
            highlightNotification = false
        }
    }

    LaunchedEffect(pendingNotificationSheetBySwipe) {
        if (pendingNotificationSheetBySwipe) {
            delay(180)
            dismissState.reset()
            showNotificationSheet = true
            highlightNotification = false
            pendingNotificationSheetBySwipe = false
        }
    }

    Box(
        modifier = Modifier
            .padding(horizontal = tokens.spacing.spacing16, vertical = 6.dp)
            .clip(RoundedCornerShape(tokens.radii.cornerRadiusLarge)),
    ) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = option.bookingStatus is BookingStatus.NotYetOpen,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(swipeBackgroundColor)
                        .padding(horizontal = tokens.spacing.spacing16),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(tokens.spacing.spacing16),
                        )
                        Text(
                            text = "通知",
                            color = Color.White,
                            style = tokens.typography.action,
                        )
                    }
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowColor)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BookingStatusBadge(
                            status = option.bookingStatus,
                            expanded = expanded,
                            actionTint = notificationState.actionTint,
                        ) {
                            expanded = !expanded
                        }
                        if (notificationState.shouldShowBadge) {
                            ScheduledNotificationBadge()
                        }
                    }
                }

                TimeRouteRow(option = option)

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.80f)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.80f)),
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
                            DiscountBadge(label = discount.label)
                        }
                    }
                }

                option.seatAvailability?.let { seatAvailability ->
                    SeatAvailabilityBlock(seatAvailability)
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
                        if (option.bookingStatus is BookingStatus.NotYetOpen) {
                            FooterAction(
                                label = "通知",
                                tint = notificationState.actionTint,
                                onClick = ::openNotificationSheet,
                                leading = {
                                    Icon(
                                        imageVector = Icons.Outlined.NotificationsNone,
                                        contentDescription = null,
                                        tint = notificationState.actionTint,
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
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.source.timetable.cardSourceLabel(),
                            color = tokens.colors.textTertiary,
                            style = tokens.typography.caption,
                            modifier = Modifier.weight(1f),
                        )
                        if (option.bookingStatus is BookingStatus.NotYetOpen) {
                            Spacer(Modifier.width(tokens.spacing.spacing12))
                            FooterAction(
                                label = "通知",
                                tint = notificationState.actionTint,
                                onClick = ::openNotificationSheet,
                                leading = {
                                    Icon(
                                        imageVector = Icons.Outlined.NotificationsNone,
                                        contentDescription = null,
                                        tint = notificationState.actionTint,
                                        modifier = Modifier.size(tokens.spacing.spacing16),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNotificationSheet) {
        BookingNotificationSheet(
            option = option,
            initialReminderAt = scheduledNotification?.reminderAt?.let(LocalDateTime::parse)
                ?: BookingNotificationDefaults.reminderAt(option),
            onDismiss = { showNotificationSheet = false },
            onConfirm = { reminderAt ->
                showNotificationSheet = false
                onScheduleNotification(option, reminderAt)
            },
        )
    }
}

@Composable
private fun ScheduledNotificationBadge() {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.successGreen.copy(alpha = 0.20f),
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = "已加入通知",
            color = tokens.colors.successGreen,
            style = tokens.typography.captionStrong,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing8,
                vertical = tokens.spacing.spacing4,
            ),
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
    actionTint: Color,
    onToggleExpand: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    val tint = status.color()
    val icon = when (status) {
        BookingStatus.Available -> Icons.Outlined.CheckCircle
        is BookingStatus.NotYetOpen -> Icons.Outlined.Schedule
        BookingStatus.Closed -> Icons.Outlined.ErrorOutline
    }
    val interactionSource = remember { MutableInteractionSource() }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.80f),
        label = "booking-status-chevron",
    )
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
            tint = actionTint,
            modifier = Modifier
                .size(tokens.sizes.disclosureIcon)
                .graphicsLayer { rotationZ = rotation },
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
) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.elevatedSurfaceColor,
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            color = tokens.colors.textPrimary,
            style = tokens.typography.captionStrong,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing8,
                vertical = tokens.spacing.spacing4,
            ),
        )
    }
}

@Composable
private fun FooterAction(
    label: String,
    tint: Color = ThsrDesignTokens.colors.primaryBlue,
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
            color = tint,
            style = tokens.typography.action,
            fontWeight = FontWeight.SemiBold,
        )
        trailing?.invoke()
    }
}

private fun trainNotificationVisualState(
    colors: ThsrColorTokens,
    isNotificationScheduled: Boolean,
    highlightNotification: Boolean,
    expanded: Boolean,
): TrainNotificationVisualState {
    val actionTint = if (isNotificationScheduled || highlightNotification) {
        colors.warningOrange
    } else {
        colors.primaryBlue
    }
    val cardTint = when {
        highlightNotification -> colors.warningOrange.copy(alpha = 0.16f)
        isNotificationScheduled -> Color(0xFF193624)
        expanded -> colors.primaryBlue.copy(alpha = 0.08f)
        else -> colors.cardColor
    }
    return TrainNotificationVisualState(
        actionTint = actionTint,
        cardTint = cardTint,
        shouldShowBadge = isNotificationScheduled,
    )
}

private fun BookingStatus.label(): String = when (this) {
    BookingStatus.Available -> "可訂位"
    is BookingStatus.NotYetOpen -> "未開放，預估 ${openingDate.format(bookingStatusDateFormatter)}"
    BookingStatus.Closed -> "已過訂位期限"
}

private fun BookingStatus.color(): Color = when (this) {
    BookingStatus.Available -> ThsrDesignTokens.colors.successGreen
    is BookingStatus.NotYetOpen -> ThsrDesignTokens.colors.warningOrange
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
