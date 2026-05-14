package com.chiiii5640.thsrapp.features.searchDashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.bookingNotifications.ScheduledNotificationsScreen
import com.chiiii5640.thsrapp.features.trainResults.TrainResultsGroup
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private enum class SourceRowKind {
    Timetable,
    SeatAvailability,
    Discount,
    Attribution,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDashboardScreen(viewModel: SearchDashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val result = (state.loadState as? SearchLoadState.Loaded)?.result
    val filtered = state.selectedFilter.apply(result?.options.orEmpty())
    val tokens = ThsrDesignTokens
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val collapsedFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val topTitleStyle = lerp(tokens.typography.largeTitle, tokens.typography.navTitle, collapsedFraction)
    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 6
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tokens.colors.backgroundColor,
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = tokens.colors.backgroundColor,
                    scrolledContainerColor = tokens.colors.backgroundColor,
                    titleContentColor = tokens.colors.textPrimary,
                    actionIconContentColor = tokens.colors.primaryBlue,
                    navigationIconContentColor = tokens.colors.textSecondary,
                ),
                title = {
                    Text(
                        text = if (state.showingScheduledNotifications) "通知列表" else "高鐵開票看板",
                        style = topTitleStyle,
                        color = tokens.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = if (isCollapsed) TextAlign.Center else TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.setShowingScheduledNotifications(!state.showingScheduledNotifications) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = if (state.showingScheduledNotifications) "回查詢" else "通知列表",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::search) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "查詢",
                            tint = tokens.colors.primaryBlue,
                        )
                    }
                    IconButton(onClick = viewModel::forceRefresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "強制更新",
                            tint = tokens.colors.primaryBlue,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .padding(padding)
                .background(tokens.colors.backgroundColor),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing12),
            contentPadding = PaddingValues(
                start = tokens.spacing.spacing16,
                top = tokens.spacing.spacing4,
                end = tokens.spacing.spacing16,
                bottom = tokens.spacing.spacing20,
            ),
        ) {
            if (state.showingScheduledNotifications) {
                item {
                    ScheduledNotificationsSection(
                        state = state,
                        onCancel = viewModel::cancelNotification,
                    )
                }
            } else {
                item {
                    QueryFormSection(
                        state = state,
                        onOrigin = viewModel::setOrigin,
                        onDestination = viewModel::setDestination,
                        onTravelDate = viewModel::setTravelDate,
                        onDepartureAfter = viewModel::setDepartureAfter,
                        onSwap = viewModel::swapRoute,
                        onForceRefresh = viewModel::forceRefresh,
                    )
                }
                when (val loadState = state.loadState) {
                    SearchLoadState.Idle -> item { EmptyHint("設定查詢條件後，即可查看當天班次與資料來源狀態。") }
                    SearchLoadState.Loading -> item { LoadingSection() }
                    is SearchLoadState.Failed -> item { ErrorSection(loadState.message) }
                    is SearchLoadState.Loaded -> {
                        item { DataSourceSection(loadState.result) }
                        item {
                            ResultFilterBar(
                                selected = state.selectedFilter,
                                onSelected = viewModel::setFilter,
                            )
                        }
                        item {
                            TrainResultsGroup(
                                options = filtered,
                                scheduledNotificationIds = state.scheduledNotifications.map { it.id }.toSet(),
                                onScheduleNotification = viewModel::scheduleNotification,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledNotificationsSection(
    state: SearchDashboardUiState,
    onCancel: (String) -> Unit,
) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(vertical = tokens.spacing.spacing8)) {
            ScheduledNotificationsScreen(
                notifications = state.scheduledNotifications,
                onCancel = onCancel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueryFormSection(
    state: SearchDashboardUiState,
    onOrigin: (Station) -> Unit,
    onDestination: (Station) -> Unit,
    onTravelDate: (LocalDate) -> Unit,
    onDepartureAfter: (LocalTime) -> Unit,
    onSwap: () -> Unit,
    onForceRefresh: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Surface(
        color = tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
        tonalElevation = 0.dp,
    ) {
        Column {
            QueryValueRow(
                label = "起站",
                value = state.origin.localName,
                onClick = null,
                trailing = {
                    StationValueMenu(
                        selected = state.origin,
                        onSelected = onOrigin,
                    )
                },
            )
            QueryDivider()
            QueryValueRow(
                label = "迄站",
                value = state.destination.localName,
                onClick = null,
                trailing = {
                    StationValueMenu(
                        selected = state.destination,
                        onSelected = onDestination,
                    )
                },
            )
            QueryDivider()
            QueryActionRow(onClick = onSwap)
            QueryDivider()
            QueryValueRow(
                label = "搭乘日期",
                value = ThsrFormatters.displayDate(state.travelDate),
                onClick = { showDatePicker = true },
            )
            QueryDivider()
            QueryValueRow(
                label = "出發時間",
                value = ThsrFormatters.pickerTime(state.departureAfter),
                onClick = { showTimePicker = true },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = tokens.spacing.spacing16,
                        vertical = tokens.spacing.spacing8,
                    ),
            ) {
                Button(
                    onClick = onForceRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.sizes.buttonHeight),
                    shape = RoundedCornerShape(tokens.radii.cornerRadiusSmall),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.colors.primaryBlue,
                        contentColor = tokens.colors.textPrimary,
                    ),
                    elevation = null,
                ) {
                    Text(
                        text = "強制更新最新資料",
                        style = tokens.typography.action,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        TravelDatePickerDialog(
            selectedDate = state.travelDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                showDatePicker = false
                onTravelDate(it)
            },
        )
    }

    if (showTimePicker) {
        DepartureTimePickerDialog(
            selectedTime = state.departureAfter,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                showTimePicker = false
                onDepartureAfter(it)
            },
        )
    }
}

@Composable
private fun QueryValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = ThsrDesignTokens
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.sizes.formRowHeight)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = tokens.spacing.spacing20),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tokens.colors.textPrimary,
            style = tokens.typography.formLabel,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        } else {
            FormValue(value = value)
        }
    }
}

@Composable
private fun QueryActionRow(
    onClick: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.sizes.formRowHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = tokens.spacing.spacing20),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
    ) {
        Icon(
            imageVector = Icons.Outlined.SwapVert,
            contentDescription = "交換起迄站",
            tint = tokens.colors.primaryBlue,
        )
        Text(
            text = "交換起迄站",
            color = tokens.colors.primaryBlue,
            style = tokens.typography.formLabel,
        )
    }
}

@Composable
private fun QueryDivider() {
    HorizontalDivider(color = ThsrDesignTokens.colors.dividerColor)
}

@Composable
private fun StationValueMenu(
    selected: Station,
    onSelected: (Station) -> Unit,
) {
    var expanded by rememberSaveable(selected) { mutableStateOf(false) }
    Box {
        FormValue(
            value = selected.localName,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(ThsrDesignTokens.colors.surfaceColor),
        ) {
            Station.entries.forEach { station ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = station.localName,
                            color = if (station == selected) ThsrDesignTokens.colors.primaryBlue else ThsrDesignTokens.colors.textPrimary,
                            style = ThsrDesignTokens.typography.bodyStrong,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(station)
                    },
                )
            }
        }
    }
}

@Composable
private fun FormValue(
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val tokens = ThsrDesignTokens
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.radii.cornerRadiusSmall))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = tokens.spacing.spacing8,
                vertical = tokens.spacing.spacing4,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing4),
    ) {
        Text(
            text = value,
            color = tokens.colors.textSecondary,
            style = tokens.typography.formValue,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = tokens.colors.textTertiary,
            modifier = Modifier.size(tokens.sizes.disclosureIcon),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelDatePickerDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val zone = ZoneId.of("Asia/Taipei")
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis ?: return@TextButton
                    onConfirm(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())
                },
            ) {
                Text("確定", color = ThsrDesignTokens.colors.primaryBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = ThsrDesignTokens.colors.textSecondary)
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepartureTimePickerDialog(
    selectedTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = selectedTime.hour,
        initialMinute = selectedTime.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute)) },
            ) {
                Text("確定", color = ThsrDesignTokens.colors.primaryBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = ThsrDesignTokens.colors.textSecondary)
            }
        },
        text = {
            TimeInput(state = timePickerState)
        },
    )
}

@Composable
private fun DataSourceSection(result: SearchResult) {
    val tokens = ThsrDesignTokens
    val rows = result.sourceStatusRows()
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8)) {
        Text(
            text = "資料來源",
            color = tokens.colors.textSecondary,
            style = tokens.typography.sectionLabel,
            modifier = Modifier.padding(horizontal = tokens.spacing.spacing4),
        )
        Surface(
            color = tokens.colors.surfaceColor,
            shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
            tonalElevation = 0.dp,
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    DataSourceRow(row)
                    if (index != rows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = tokens.spacing.spacing20),
                            color = tokens.colors.dividerColor,
                        )
                    }
                }
            }
        }
        Text(
            text = "本次結果會優先使用 TDX，若官方來源缺資料才會退回快取或 fallback。",
            color = tokens.colors.textSecondary,
            style = tokens.typography.body,
            modifier = Modifier.padding(horizontal = tokens.spacing.spacing4),
        )
    }
}

@Composable
private fun DataSourceRow(row: DashboardSourceRow) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.sizes.sourceRowHeight)
            .padding(horizontal = tokens.spacing.spacing20),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(tokens.sizes.statusDot)
                .background(row.tint, CircleShape),
        )
        Spacer(Modifier.width(tokens.spacing.spacing12))
        Text(
            text = row.title,
            color = tokens.colors.textPrimary,
            style = tokens.typography.bodyStrong,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = row.stateLabel,
            color = tokens.colors.textTertiary,
            style = tokens.typography.captionStrong,
        )
    }
}

@Composable
private fun ResultFilterBar(
    selected: ResultFilter,
    onSelected: (ResultFilter) -> Unit,
) {
    val tokens = ThsrDesignTokens
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = tokens.spacing.spacing4, end = tokens.spacing.spacing16),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
    ) {
        items(ResultFilter.entries, key = { it.name }) { filter ->
            FilterPill(
                label = filter.label,
                selected = filter == selected,
                onClick = { onSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .height(tokens.sizes.chipHeight)
            .clip(RoundedCornerShape(tokens.radii.chipRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        color = if (selected) tokens.colors.primaryBlue else tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing16,
                vertical = tokens.spacing.spacing8,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (selected) tokens.colors.textPrimary else tokens.colors.textSecondary,
                style = tokens.typography.pill,
            )
        }
    }
}

@Composable
private fun LoadingSection() {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.spacing16),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing12),
        ) {
            Text(
                text = "正在更新班次與座位資料",
                color = tokens.colors.textPrimary,
                style = tokens.typography.bodyStrong,
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = tokens.colors.primaryBlue,
                trackColor = tokens.colors.elevatedSurfaceColor,
            )
        }
    }
}

@Composable
private fun ErrorSection(message: String) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = message,
            color = tokens.colors.warningOrange,
            style = tokens.typography.body,
            modifier = Modifier.padding(tokens.spacing.spacing16),
        )
    }
}

@Composable
private fun EmptyHint(message: String) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = message,
            color = tokens.colors.textSecondary,
            style = tokens.typography.body,
            modifier = Modifier.padding(tokens.spacing.spacing16),
        )
    }
}

private data class DashboardSourceRow(
    val kind: SourceRowKind,
    val title: String,
    val stateLabel: String,
    val tint: Color,
)

private fun SearchResult.sourceStatusRows(): List<DashboardSourceRow> {
    val timetableStatus = sourceStatuses.getOrNull(0) ?: SourceStatus("timetable unavailable", SourceState.Unavailable)
    val seatStatus = sourceStatuses.getOrNull(1) ?: SourceStatus("seat unavailable", SourceState.Unavailable)
    val discountStatus = sourceStatuses.getOrNull(2) ?: SourceStatus("discount unavailable", SourceState.Unavailable)

    return listOf(
        DashboardSourceRow(
            kind = SourceRowKind.Timetable,
            title = timetableStatus.iosTitle(),
            stateLabel = timetableStatus.stateLabel(),
            tint = timetableStatus.tint(),
        ),
        DashboardSourceRow(
            kind = SourceRowKind.SeatAvailability,
            title = seatStatus.iosTitle(),
            stateLabel = seatStatus.stateLabel(),
            tint = seatStatus.tint(),
        ),
        DashboardSourceRow(
            kind = SourceRowKind.Discount,
            title = discountStatus.iosTitle(),
            stateLabel = discountStatus.stateLabel(),
            tint = discountStatus.tint(),
        ),
    )
}

private fun SourceStatus.stateLabel(): String = when (state) {
    SourceState.Live -> "即時"
    SourceState.Cache -> "快取"
    SourceState.Fallback -> "替代"
    SourceState.Unavailable -> "不可用"
}

private fun SourceStatus.iosTitle(): String = when {
    label.contains("DailyTimetable", ignoreCase = true) && state == SourceState.Live -> "TDX DailyTimetable live"
    label.contains("DailyTimetable", ignoreCase = true) && state == SourceState.Cache -> "TDX 高鐵每日時刻表（快取）"
    label.contains("GeneralTimetable", ignoreCase = true) -> "TDX GeneralTimetable fallback"
    label.contains("persisted", ignoreCase = true) -> "本機保存 GeneralTimetable"
    label.contains("seat availability live", ignoreCase = true) -> "TDX seat availability live"
    label.contains("seat availability cache", ignoreCase = true) -> "TDX seat availability（快取）"
    label.contains("seat APIs skipped", ignoreCase = true) -> "座位 API 已略過"
    label.contains("cooldown", ignoreCase = true) -> "座位 API 冷卻中"
    label.contains("discount feed", ignoreCase = true) -> "GitHub Pages discount feed"
    label.contains("timetable fallback", ignoreCase = true) -> "Discount feed fallback timetable"
    label.contains("unavailable", ignoreCase = true) -> "不可用"
    else -> label
}

private fun SourceStatus.tint(): Color = when (state) {
    SourceState.Live -> ThsrDesignTokens.colors.successGreen
    SourceState.Cache -> ThsrDesignTokens.colors.primaryBlue
    SourceState.Fallback -> ThsrDesignTokens.colors.warningOrange
    SourceState.Unavailable -> ThsrDesignTokens.colors.textTertiary
}
