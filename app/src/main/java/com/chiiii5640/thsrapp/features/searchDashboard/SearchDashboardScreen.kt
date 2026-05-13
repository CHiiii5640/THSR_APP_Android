package com.chiiii5640.thsrapp.features.searchDashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.bookingNotifications.ScheduledNotificationsScreen
import com.chiiii5640.thsrapp.features.trainResults.TrainOptionCard
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val PageBackground = Color(0xFF050505)
private val PanelBackground = Color(0xFF1C1C1E)
private val DividerColor = Color(0xFF2C2C2E)
private val SecondaryTextColor = Color(0xFF8E8E93)
private val AccentColor = Color(0xFF0A84FF)
private val SuccessColor = Color(0xFF30D158)
private val WarningColor = Color(0xFFFF9F0A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDashboardScreen(viewModel: SearchDashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val result = (state.loadState as? SearchLoadState.Loaded)?.result
    val filtered = state.selectedFilter.apply(result?.options.orEmpty())

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    actionIconContentColor = AccentColor,
                ),
                title = {
                    Text(
                        text = if (state.showingScheduledNotifications) "通知列表" else "高鐵開票看板",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.setShowingScheduledNotifications(!state.showingScheduledNotifications)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = if (state.showingScheduledNotifications) "回查詢" else "通知列表",
                            tint = if (state.showingScheduledNotifications) Color.White else SecondaryTextColor,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::forceRefresh) {
                        Icon(Icons.Outlined.Search, contentDescription = "重新查詢")
                    }
                    IconButton(onClick = viewModel::forceRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "強制更新")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .background(PageBackground),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 20.dp),
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
                item {
                    when (val loadState = state.loadState) {
                        SearchLoadState.Idle -> EmptyHint("設定條件後即可查詢班次")
                        SearchLoadState.Loading -> LoadingSection()
                        is SearchLoadState.Failed -> ErrorSection(loadState.message)
                        is SearchLoadState.Loaded -> DataSourceSection(loadState.result)
                    }
                }
                item {
                    ResultFilterBar(
                        selected = state.selectedFilter,
                        onSelected = viewModel::setFilter,
                    )
                }
                if (result != null) {
                    item {
                        Text(
                            text = "${filtered.size} 班符合條件",
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                items(filtered, key = { "${it.trainNo}-${it.departureTime}" }) { option ->
                    TrainOptionCard(
                        option = option,
                        onScheduleNotification = viewModel::scheduleNotification,
                    )
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
    Surface(
        color = PanelBackground,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
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
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Surface(
        color = PanelBackground,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Column {
            QueryFieldRow(
                label = "起站",
                content = {
                    StationDropdown(
                        selected = state.origin,
                        onSelected = onOrigin,
                    )
                },
            )
            QueryDivider()
            QueryFieldRow(
                label = "迄站",
                content = {
                    StationDropdown(
                        selected = state.destination,
                        onSelected = onDestination,
                    )
                },
            )
            QueryDivider()
            QueryActionRow(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = "交換起迄站",
                        tint = AccentColor,
                    )
                },
                text = "交換起迄站",
                onClick = onSwap,
            )
            QueryDivider()
            QueryFieldRow(
                label = "搭乘日期",
                content = {
                    PickerValue(
                        value = ThsrFormatters.date(state.travelDate),
                        onClick = { showDatePicker = true },
                    )
                },
            )
            QueryDivider()
            QueryFieldRow(
                label = "出發時間",
                content = {
                    PickerValue(
                        value = ThsrFormatters.time(state.departureAfter),
                        onClick = { showTimePicker = true },
                    )
                },
            )
            QueryDivider()
            Button(
                onClick = onForceRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text("強制更新最新資料", style = MaterialTheme.typography.titleMedium)
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
private fun QueryFieldRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.weight(1.3f), contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}

@Composable
private fun QueryActionRow(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(
            text = text,
            color = AccentColor,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun QueryDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationDropdown(
    selected: Station,
    onSelected: (Station) -> Unit,
) {
    var expanded by rememberSaveable(selected) { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected.localName,
            onValueChange = {},
            readOnly = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier
                .menuAnchor()
                .width(122.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                unfocusedContainerColor = Color(0xFF2C2C2E),
                focusedContainerColor = Color(0xFF2C2C2E),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = AccentColor,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTrailingIconColor = SecondaryTextColor,
                focusedTrailingIconColor = AccentColor,
            ),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Station.entries.forEach { station ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = station.localName,
                            color = if (station == selected) AccentColor else Color.White,
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
private fun PickerValue(
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2C2C2E))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = SecondaryTextColor,
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
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
        is24Hour = false,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                },
            ) {
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        text = {
            TimeInput(state = timePickerState)
        },
    )
}

@Composable
private fun DataSourceSection(result: SearchResult) {
    Surface(
        color = PanelBackground,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "資料來源",
                color = SecondaryTextColor,
                style = MaterialTheme.typography.titleMedium,
            )
            result.sourceStatuses.forEach { status ->
                SourceStatusRow(status)
            }
            Text(
                text = "本次結果會優先使用 TDX，若官方來源缺資料才退回快取或 fallback。",
                color = SecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SourceStatusRow(status: SourceStatus) {
    val indicatorColor = when (status.state) {
        SourceState.Live -> SuccessColor
        SourceState.Cache -> AccentColor
        SourceState.Fallback -> WarningColor
        SourceState.Unavailable -> SecondaryTextColor
    }
    val stateLabel = when (status.state) {
        SourceState.Live -> "即時"
        SourceState.Cache -> "快取"
        SourceState.Fallback -> "替代"
        SourceState.Unavailable -> "不可用"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(indicatorColor),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = status.label,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stateLabel,
            color = SecondaryTextColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ResultFilterBar(selected: ResultFilter, onSelected: (ResultFilter) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResultFilter.entries.forEach { filter ->
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
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) AccentColor else Color(0xFF161618),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun LoadingSection() {
    Surface(
        color = PanelBackground,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("正在更新班次與座位資料", color = Color.White, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AccentColor,
                trackColor = DividerColor,
            )
        }
    }
}

@Composable
private fun ErrorSection(message: String) {
    Surface(
        color = PanelBackground,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = message,
            color = WarningColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun EmptyHint(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelBackground),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = message,
            color = SecondaryTextColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}
