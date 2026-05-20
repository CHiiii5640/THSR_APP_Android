package com.chiiii5640.thsrapp.features.searchDashboard

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.BuildConfig
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.bookingNotifications.ScheduledNotificationsScreen
import com.chiiii5640.thsrapp.features.bookingNotifications.ScheduledBookingNotification
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationScheduler
import com.chiiii5640.thsrapp.features.trainResults.DebugTrainPanelSheet
import com.chiiii5640.thsrapp.features.trainResults.TrainOptionCard
import com.chiiii5640.thsrapp.ui.layout.rememberThsrLayoutProfile
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

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
    val options = result?.options.orEmpty()
    val fastestDurations = ResultFilter.fastestDurationOptions(options)
    val resolvedFastestDuration = state.selectedFastestDuration
        ?.takeIf { it in fastestDurations }
        ?: fastestDurations.firstOrNull()
    val filtered = state.selectedFilter.apply(options, resolvedFastestDuration)
    val scheduledById = state.scheduledNotifications.associateBy(ScheduledBookingNotification::id)
    val tokens = ThsrDesignTokens
    val layoutProfile = rememberThsrLayoutProfile()
    val listState = rememberLazyListState()
    val scheduledNotificationsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val debugPanelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDebugPanel by rememberSaveable { mutableStateOf(false) }
    var debugUnlockTapCount by rememberSaveable { mutableStateOf(0) }
    var lastDebugUnlockTapAt by rememberSaveable { mutableStateOf(0L) }

    fun handleDebugUnlockTap() {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        debugUnlockTapCount = if (now - lastDebugUnlockTapAt <= 1_200L) {
            debugUnlockTapCount + 1
        } else {
            1
        }
        lastDebugUnlockTapAt = now
        if (debugUnlockTapCount >= 6) {
            debugUnlockTapCount = 0
            showDebugPanel = true
        }
    }

    Scaffold(
        containerColor = tokens.colors.backgroundColor,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .statusBarsPadding()
                    .height(tokens.sizes.navigationExpandedHeight),
                title = {
                    Text(
                        text = "高鐵開票看板",
                        style = tokens.typography.largeTitle,
                        color = tokens.colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = BuildConfig.DEBUG,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = ::handleDebugUnlockTap,
                            ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = tokens.colors.backgroundColor,
                    titleContentColor = tokens.colors.textPrimary,
                    actionIconContentColor = tokens.colors.primaryBlue,
                    navigationIconContentColor = tokens.colors.textSecondary,
                ),
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(
                                color = if (state.scheduledNotifications.isNotEmpty()) {
                                    tokens.colors.primaryBlue.copy(alpha = 0.16f)
                                } else {
                                    Color.Transparent
                                },
                                shape = CircleShape,
                            ),
                    ) {
                        IconButton(
                            onClick = { viewModel.setShowingScheduledNotifications(!state.showingScheduledNotifications) },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = "通知列表",
                                tint = if (state.scheduledNotifications.isNotEmpty()) {
                                    tokens.colors.warningOrange
                                } else {
                                    tokens.colors.textSecondary
                                },
                            )
                        }
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
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(tokens.colors.backgroundColor),
        ) {
            val listMaxWidth = minOf(maxWidth, layoutProfile.contentMaxWidth)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .widthIn(max = listMaxWidth),
                verticalArrangement = Arrangement.spacedBy(layoutProfile.sectionSpacing),
                contentPadding = PaddingValues(
                    start = layoutProfile.contentHorizontalPadding,
                    top = tokens.spacing.spacing8,
                    end = layoutProfile.contentHorizontalPadding,
                    bottom = tokens.spacing.spacing20,
                ),
            ) {
                if (!state.showingScheduledNotifications) {
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
                                    fastestDurations = fastestDurations,
                                    selectedFastestDuration = resolvedFastestDuration,
                                    onSelected = viewModel::setFilter,
                                    onFastestDurationSelected = viewModel::setFastestDuration,
                                )
                            }
                        item {
                            SearchResultCountSection(
                                count = filtered.size,
                            )
                        }
                        itemsIndexed(
                            items = filtered,
                            key = { _, option -> BookingNotificationScheduler.notificationId(option) },
                            contentType = { _, _ -> "train-option-card" },
                        ) { _, option ->
                            TrainOptionCard(
                                option = option,
                                scheduledNotification = scheduledById[BookingNotificationScheduler.notificationId(option)],
                                layoutProfile = layoutProfile,
                                onScheduleNotification = viewModel::scheduleNotification,
                            )
                        }
                    }
                    }
                }
            }
        }

        if (state.showingScheduledNotifications) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setShowingScheduledNotifications(false) },
                sheetState = scheduledNotificationsSheetState,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = tokens.colors.backgroundColor.copy(alpha = 0.98f),
                scrimColor = tokens.colors.backgroundColor.copy(alpha = 0.55f),
                dragHandle = null,
            ) {
                ScheduledNotificationsSheetContent(
                    state = state,
                    layoutProfile = layoutProfile,
                    onDismiss = { viewModel.setShowingScheduledNotifications(false) },
                    onCancel = viewModel::cancelNotification,
                )
            }
        }

        if (showDebugPanel && BuildConfig.DEBUG) {
            ModalBottomSheet(
                onDismissRequest = {
                    debugUnlockTapCount = 0
                    showDebugPanel = false
                },
                sheetState = debugPanelSheetState,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = tokens.colors.backgroundColor.copy(alpha = 0.98f),
                scrimColor = tokens.colors.backgroundColor.copy(alpha = 0.55f),
                dragHandle = null,
            ) {
                DebugTrainPanelSheet(
                    layoutProfile = layoutProfile,
                    onDismiss = {
                        debugUnlockTapCount = 0
                        showDebugPanel = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultCountSection(
    count: Int,
) {
    val tokens = ThsrDesignTokens
    Text(
        text = "${count} 班符合條件",
        color = tokens.colors.textSecondary,
        style = tokens.typography.sectionLabel,
        modifier = Modifier.padding(horizontal = tokens.spacing.spacing4),
    )
}

@Composable
private fun ScheduledNotificationsSheetContent(
    state: SearchDashboardUiState,
    layoutProfile: com.chiiii5640.thsrapp.ui.layout.ThsrLayoutProfile,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit,
) {
    val tokens = ThsrDesignTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(
                horizontal = layoutProfile.sheetHorizontalPadding,
                vertical = layoutProfile.sheetTopPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "已設定的通知",
                color = tokens.colors.textPrimary,
                style = tokens.typography.largeTitle,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("完成", color = tokens.colors.primaryBlue)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = tokens.colors.surfaceColor,
            shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
            tonalElevation = 0.dp,
        ) {
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
            text = "班次、座位與優惠會依可用資料自動更新。",
            color = tokens.colors.textSecondary,
            style = tokens.typography.caption,
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
    fastestDurations: List<Long>,
    selectedFastestDuration: Long?,
    onSelected: (ResultFilter) -> Unit,
    onFastestDurationSelected: (Long) -> Unit,
) {
    val tokens = ThsrDesignTokens
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val fastestMenuIndex = ResultFilter.entries.size

    LaunchedEffect(selected, fastestDurations) {
        if (selected == ResultFilter.Fastest && fastestDurations.isNotEmpty()) {
            listState.animateScrollToItem(fastestMenuIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = tokens.spacing.spacing4, end = tokens.spacing.spacing16),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing12),
    ) {
        items(ResultFilter.entries, key = { it.name }) { filter ->
            FilterPill(
                label = filter.label,
                selected = filter == selected,
                onClick = {
                    onSelected(filter)
                    if (filter == ResultFilter.Fastest && fastestDurations.isNotEmpty()) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(fastestMenuIndex)
                        }
                    }
                },
            )
        }
        if (selected == ResultFilter.Fastest && fastestDurations.isNotEmpty()) {
            item(key = "fastest-duration-menu") {
                FastestDurationMenu(
                    durations = fastestDurations,
                    selectedDuration = selectedFastestDuration,
                    onSelected = onFastestDurationSelected,
                )
            }
        }
    }
}

@Composable
private fun FastestDurationMenu(
    durations: List<Long>,
    selectedDuration: Long?,
    onSelected: (Long) -> Unit,
) {
    val tokens = ThsrDesignTokens
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .height(tokens.sizes.chipHeight)
            .clip(RoundedCornerShape(tokens.radii.chipRadius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { expanded = true },
            ),
        color = tokens.colors.surfaceColor,
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing16,
                vertical = tokens.spacing.spacing4,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
            ) {
                Text(
                    text = selectedDuration?.let(::durationLabel) ?: "選擇時間",
                    color = tokens.colors.textSecondary,
                    style = tokens.typography.pill,
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = tokens.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                durations.forEach { duration ->
                    DropdownMenuItem(
                        text = { Text(durationLabel(duration)) },
                        onClick = {
                            expanded = false
                            onSelected(duration)
                        },
                    )
                }
            }
        }
    }
}

private fun durationLabel(minutes: Long): String =
    if (minutes < 60) {
        "${minutes} 分鐘"
    } else {
        val hours = minutes / 60
        val remainder = minutes % 60
        if (remainder == 0L) {
            "${hours} 小時"
        } else {
            "${hours} 小時 ${remainder} 分鐘"
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
                vertical = tokens.spacing.spacing4,
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
    label.contains("DailyTimetable", ignoreCase = true) -> "TDX 高鐵每日時刻表"
    label.contains("GeneralTimetable", ignoreCase = true) -> "TDX 高鐵每日時刻表"
    label.contains("persisted", ignoreCase = true) -> "高鐵班次資料"
    label.contains("seat availability", ignoreCase = true) -> "TDX 座位狀態"
    label.contains("seat APIs skipped", ignoreCase = true) -> "座位狀態"
    label.contains("cooldown", ignoreCase = true) -> "座位狀態"
    label.contains("discount feed", ignoreCase = true) -> "優惠資訊"
    label.contains("timetable fallback", ignoreCase = true) -> "高鐵班次資料"
    label.contains("unavailable", ignoreCase = true) -> "資料狀態"
    else -> label
}

private fun SourceStatus.tint(): Color = when (state) {
    SourceState.Live -> ThsrDesignTokens.colors.successGreen
    SourceState.Cache -> ThsrDesignTokens.colors.primaryBlue
    SourceState.Fallback -> ThsrDesignTokens.colors.warningOrange
    SourceState.Unavailable -> ThsrDesignTokens.colors.textTertiary
}
