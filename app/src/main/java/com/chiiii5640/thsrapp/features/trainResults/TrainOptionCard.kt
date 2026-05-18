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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TravelExplore
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
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
import com.chiiii5640.thsrapp.ui.layout.ThsrLayoutProfile
import com.chiiii5640.thsrapp.ui.theme.ThsrColorTokens
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private data class TrainNotificationVisualState(
    val actionTint: Color,
    val cardTint: Color,
    val strokeTint: Color,
    val shouldShowBadge: Boolean,
)

private val bookingStatusDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Composable
fun TrainResultsGroup(
    options: List<TrainOption>,
    scheduledNotifications: Map<String, ScheduledBookingNotification>,
    layoutProfile: ThsrLayoutProfile,
    onScheduleNotification: (TrainOption, LocalDateTime) -> Unit,
) {
    val tokens = ThsrDesignTokens
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8)) {
        Text(
            text = "${options.size} 班符合條件",
            color = tokens.colors.textSecondary,
            style = tokens.typography.sectionLabel,
        )
        options.forEach { option ->
            TrainOptionCard(
                option = option,
                scheduledNotification = scheduledNotifications[BookingNotificationScheduler.notificationId(option)],
                layoutProfile = layoutProfile,
                onScheduleNotification = onScheduleNotification,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrainOptionCard(
    option: TrainOption,
    scheduledNotification: ScheduledBookingNotification?,
    layoutProfile: ThsrLayoutProfile,
    onScheduleNotification: (TrainOption, LocalDateTime) -> Unit,
) {
    val tokens = ThsrDesignTokens
    val cardTokens = tokens.trainCard
    val uriHandler = LocalUriHandler.current
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
            .padding(vertical = 2.dp)
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
                    .clip(RoundedCornerShape(tokens.radii.cornerRadiusLarge))
                    .background(rowColor)
                    .border(
                        width = 1.dp,
                        color = notificationState.strokeTint,
                        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
                    )
                    .padding(
                        horizontal = layoutProfile.cardContentHorizontalPadding,
                        vertical = cardTokens.verticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(cardTokens.itemVerticalSpacing),
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
                        verticalArrangement = Arrangement.spacedBy(3.dp),
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
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.84f)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.84f)),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    StopTimeline(
                        option = option,
                        layoutProfile = layoutProfile,
                    )
                }

                if (option.discounts.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        option.discounts.forEach { discount ->
                            DiscountBadge(label = discount.label)
                        }
                    }
                }

                option.seatAvailability?.let { seatAvailability ->
                    SeatAvailabilityBlock(
                        seatAvailability = seatAvailability,
                        layoutProfile = layoutProfile,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FooterAction(
                        label = option.source.timetable.cardSourceLabel(),
                        tint = tokens.colors.primaryBlue.copy(alpha = 0.92f),
                        onClick = { uriHandler.openUri(option.sourceUrl()) },
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = tokens.colors.primaryBlue,
                                modifier = Modifier.size(tokens.spacing.spacing16),
                            )
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    FooterAction(
                        label = "前往訂票",
                        onClick = { uriHandler.openUri(option.officialBookingUrl) },
                        leading = {
                            Icon(
                                imageVector = Icons.Outlined.TravelExplore,
                                contentDescription = null,
                                tint = tokens.colors.primaryBlue,
                                modifier = Modifier.size(tokens.spacing.spacing16),
                            )
                        },
                    )
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
        color = tokens.colors.successGreen.copy(alpha = 0.14f),
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = "已加入通知",
            color = tokens.colors.successGreen,
            style = tokens.typography.captionStrong,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing8,
                vertical = 3.dp,
            ),
        )
    }
}

@Composable
private fun TimeRouteRow(option: TrainOption) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeStationColumn(
            time = ThsrFormatters.displayTimetableTime(option.departureTime),
            station = option.origin.localName,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = tokens.colors.textDisabled,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = time,
            color = tokens.colors.textPrimary,
            style = tokens.typography.cardTime,
        )
        Text(
            text = station,
            color = tokens.colors.textSecondary.copy(alpha = tokens.opacity.subduedText),
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
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.84f),
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = status.label(),
                color = tint,
                style = tokens.typography.captionStrong,
            )
        }
        Spacer(Modifier.width(6.dp))
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
private fun SeatAvailabilityBlock(
    seatAvailability: SeatAvailabilityDetail,
    layoutProfile: ThsrLayoutProfile,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SeatStatusSourceRow(
            source = "OD",
            standard = seatAvailability.standardSeatStatus,
            business = seatAvailability.businessSeatStatus,
            layoutProfile = layoutProfile,
        )
        SeatStatusSourceRow(
            source = "看板",
            standard = if (seatAvailability.hasBoardSeatStatus) seatAvailability.boardStandardSeatStatus else SeatStatus.Unknown,
            business = if (seatAvailability.hasBoardSeatStatus) seatAvailability.boardBusinessSeatStatus else SeatStatus.Unknown,
            layoutProfile = layoutProfile,
        )
    }
}

@Composable
private fun SeatStatusSourceRow(
    source: String,
    standard: SeatStatus,
    business: SeatStatus,
    layoutProfile: ThsrLayoutProfile,
) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = source,
            color = tokens.colors.textSecondary.copy(alpha = tokens.opacity.mutedText),
            style = tokens.typography.captionStrong,
            modifier = Modifier.width(if (layoutProfile.isLargeFont) 34.dp else 28.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color = status.color(), shape = CircleShape),
        )
        Text(
            text = "$title ${status.detailLabel()}",
            color = tokens.colors.textSecondary.copy(alpha = tokens.opacity.subduedText),
            style = tokens.typography.caption,
        )
    }
}

@Composable
private fun DiscountBadge(label: String) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.elevatedSurfaceColor.copy(alpha = 0.42f),
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            color = tokens.colors.textPrimary,
            style = tokens.typography.captionStrong,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing8,
                vertical = 4.dp,
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
        horizontalArrangement = Arrangement.spacedBy(tokens.trainCard.horizontalActionSpacing),
    ) {
        leading?.invoke()
        Text(
            text = label,
            color = tint,
            style = tokens.typography.action,
            fontWeight = FontWeight.SemiBold,
        )
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
    return when {
        highlightNotification -> TrainNotificationVisualState(
            actionTint = actionTint,
            cardTint = ThsrDesignTokens.trainCard.notificationHighlightBackground,
            strokeTint = ThsrDesignTokens.trainCard.notificationHighlightBorder,
            shouldShowBadge = isNotificationScheduled,
        )

        isNotificationScheduled -> TrainNotificationVisualState(
            actionTint = actionTint,
            cardTint = ThsrDesignTokens.trainCard.notificationScheduledBackground,
            strokeTint = ThsrDesignTokens.trainCard.notificationScheduledBorder,
            shouldShowBadge = true,
        )

        expanded -> TrainNotificationVisualState(
            actionTint = actionTint,
            cardTint = ThsrDesignTokens.trainCard.expandedBackground,
            strokeTint = ThsrDesignTokens.trainCard.expandedBorder,
            shouldShowBadge = false,
        )

        else -> TrainNotificationVisualState(
            actionTint = actionTint,
            cardTint = ThsrDesignTokens.trainCard.collapsedBackground,
            strokeTint = ThsrDesignTokens.trainCard.collapsedBorder,
            shouldShowBadge = false,
        )
    }
}

private fun BookingStatus.label(): String = when (this) {
    BookingStatus.Available -> "已開放"
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
    SeatStatus.Unknown -> "暫無資料"
    SeatStatus.Available -> "座位有餘"
    SeatStatus.Limited -> "座位有限"
    SeatStatus.SoldOut -> "暫無座位"
}

private fun com.chiiii5640.thsrapp.core.model.SourceStatus.cardSourceLabel(): String = when (state) {
    SourceState.Unavailable -> "高鐵班次來源"
    else -> "TDX 高鐵每日時刻表"
}

private fun TrainOption.sourceUrl(): String {
    val base = "https://tdx.transportdata.tw/api/basic/v2/Rail/THSR"
    return if (source.timetable.state == SourceState.Fallback || source.timetable.label.contains("GeneralTimetable", ignoreCase = true)) {
        "$base/GeneralTimetable?${'$'}top=300&${'$'}format=JSON"
    } else {
        "$base/DailyTimetable/TrainDate/$travelDate?${'$'}format=JSON"
    }
}

private fun TrainOption.compactStatusLabel(): String {
    val currentStation = liveStatus.summary.currentStopIndex?.let(stops::getOrNull)?.station?.localName
    val nextStation = liveStatus.summary.nextStopIndex?.let(stops::getOrNull)?.station?.localName
    return when (liveStatus.serviceState) {
        com.chiiii5640.thsrapp.core.model.TrainServiceState.NotDeparted ->
            "未發車 · ${origin.localName} ${ThsrFormatters.displayTimetableTime(departureTime)}"

        com.chiiii5640.thsrapp.core.model.TrainServiceState.DepartingSoon ->
            "即將發車 · ${origin.localName}"

        com.chiiii5640.thsrapp.core.model.TrainServiceState.InTransit ->
            nextStation?.let { "行進中 · 下一站 $it" } ?: "行進中"

        com.chiiii5640.thsrapp.core.model.TrainServiceState.ApproachingStation ->
            (nextStation ?: destination.localName).let { "即將進站 · $it" }

        com.chiiii5640.thsrapp.core.model.TrainServiceState.DwellingAtStation ->
            (currentStation ?: origin.localName).let { "停靠中 · $it" }

        com.chiiii5640.thsrapp.core.model.TrainServiceState.DepartedStation ->
            nextStation?.let { "已離站 · 前往 $it" } ?: "已離站"

        com.chiiii5640.thsrapp.core.model.TrainServiceState.ArrivedDestination ->
            "已抵達 · ${destination.localName}"
    }
}
