package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatAvailabilityDetail
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TrainDataSource
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.searchDashboard.TravelDatePickerDialog
import com.chiiii5640.thsrapp.ui.layout.ThsrLayoutProfile
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private enum class DebugPreviewClockMode(val title: String) {
    Live("跟隨現在"),
    Manual("手動指定"),
}

private enum class DebugTrainDirection(val title: String) {
    Southbound("南下"),
    Northbound("北上"),
}

@Composable
fun DebugTrainPanelSheet(
    layoutProfile: ThsrLayoutProfile,
    onDismiss: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    var trainNumber by remember { mutableStateOf("0811") }
    var direction by remember { mutableStateOf(DebugTrainDirection.Southbound) }
    var origin by remember { mutableStateOf(Station.Nangang) }
    var destination by remember { mutableStateOf(Station.Zuoying) }
    var travelDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var enabledStops by remember {
        mutableStateOf(debugRouteStations(Station.Nangang, Station.Zuoying, DebugTrainDirection.Southbound).drop(1).dropLast(1).toSet())
    }
    var stopTimes by remember { mutableStateOf<Map<Station, LocalTime>>(emptyMap()) }
    var previewClockMode by remember { mutableStateOf(DebugPreviewClockMode.Live) }
    var previewTime by remember { mutableStateOf(LocalTime.of(6, 0)) }
    var previewFocusStation by remember { mutableStateOf(Station.Nangang) }
    var previewTimeStartedAt by remember { mutableStateOf(LocalDateTime.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var editingStopTimeStation by remember { mutableStateOf<Station?>(null) }
    var editingPreviewTime by remember { mutableStateOf(false) }

    val routeStations = remember(origin, destination, direction) {
        debugRouteStations(origin, destination, direction)
    }
    val activeStops = remember(routeStations, enabledStops, origin, destination) {
        routeStations.filter { station ->
            station == origin || station == destination || station in enabledStops
        }
    }
    val previewReferenceDateTime = remember(previewClockMode, travelDate, previewTime) {
        if (previewClockMode == DebugPreviewClockMode.Manual) {
            LocalDateTime.of(travelDate, previewTime)
        } else {
            null
        }
    }
    val previewReferenceAnchorDateTime = remember(previewClockMode, previewTimeStartedAt) {
        if (previewClockMode == DebugPreviewClockMode.Manual) {
            previewTimeStartedAt
        } else {
            null
        }
    }
    val previewOption = remember(trainNumber, direction, origin, destination, travelDate, activeStops, stopTimes) {
        buildDebugTrainOption(
            trainNumber = trainNumber,
            direction = direction,
            origin = origin,
            destination = destination,
            travelDate = travelDate,
            activeStops = activeStops,
            stopTimes = stopTimes,
        )
    }
    val canJumpToNextSegment = remember(activeStops, previewFocusStation) {
        activeStops.indexOf(previewFocusStation)
            .takeIf { it >= 0 }
            ?.let { index -> index < activeStops.lastIndex }
            ?: false
    }

    fun stopTimeFor(station: Station): LocalTime =
        stopTimes[station] ?: debugDefaultTime(
            station = station,
            travelDate = travelDate,
            activeStops = activeStops,
        )

    fun setPreviewTime(time: LocalTime) {
        previewTime = time.withSecond(0).withNano(0)
        previewTimeStartedAt = LocalDateTime.now()
    }

    fun applyRouteChange(
        newDirection: DebugTrainDirection = direction,
        newOrigin: Station = origin,
        newDestination: Station = destination,
        resetIntermediateStops: Boolean = true,
    ) {
        val (alignedOrigin, alignedDestination) = alignDebugTerminals(
            origin = newOrigin,
            destination = newDestination,
            direction = newDirection,
        )
        val nextRouteStations = debugRouteStations(alignedOrigin, alignedDestination, newDirection)
        val nextIntermediateStops = nextRouteStations.drop(1).dropLast(1).toSet()

        direction = newDirection
        origin = alignedOrigin
        destination = alignedDestination
        enabledStops = if (resetIntermediateStops) {
            nextIntermediateStops
        } else {
            enabledStops.intersect(nextIntermediateStops)
        }
        stopTimes = stopTimes.filterKeys { it in nextRouteStations }
        if (previewFocusStation !in nextRouteStations) {
            previewFocusStation = nextRouteStations.first()
        }
    }

    fun setTimesFromNow(offsetMinutes: Long = 1L) {
        travelDate = LocalDate.now()
        val resolvedStops = activeStops
        val now = LocalDateTime.now().plusMinutes(offsetMinutes)
        stopTimes = resolvedStops.associateWith { station ->
            now.plusMinutes(cumulativeTravelMinutes(station, resolvedStops).toLong()).toLocalTime()
        }
        if (previewClockMode == DebugPreviewClockMode.Manual) {
            setPreviewTime(now.toLocalTime())
        }
    }

    fun resolvedStopDateTimes(): Map<Station, LocalDateTime> {
        var dayOffset = 0L
        var previous: LocalDateTime? = null
        return buildMap {
            activeStops.forEach { station ->
                var resolved = LocalDateTime.of(travelDate.plusDays(dayOffset), stopTimeFor(station))
                if (previous != null && resolved.isBefore(previous)) {
                    dayOffset += 1
                    resolved = LocalDateTime.of(travelDate.plusDays(dayOffset), stopTimeFor(station))
                }
                put(station, resolved)
                previous = resolved
            }
        }
    }

    fun jumpPreviewToSelectedStation() {
        previewClockMode = DebugPreviewClockMode.Manual
        setPreviewTime(stopTimeFor(previewFocusStation))
    }

    fun jumpPreviewToNextSegment() {
        val resolvedStops = resolvedStopDateTimes()
        val selectedIndex = activeStops.indexOf(previewFocusStation)
        if (selectedIndex < 0 || selectedIndex >= activeStops.lastIndex) return

        val current = activeStops[selectedIndex]
        val next = activeStops[selectedIndex + 1]
        val start = resolvedStops.getValue(current).plusSeconds(dwellSecondsFor(current))
        val end = resolvedStops.getValue(next)
        val midpoint = if (end.isAfter(start)) {
            start.plusSeconds(java.time.Duration.between(start, end).seconds / 2)
        } else {
            end
        }
        previewClockMode = DebugPreviewClockMode.Manual
        setPreviewTime(midpoint.toLocalTime())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = layoutProfile.sheetHorizontalPadding,
                vertical = layoutProfile.sheetTopPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing16),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Debug 模擬列車",
                color = tokens.colors.textPrimary,
                style = tokens.typography.largeTitle,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("關閉", color = tokens.colors.primaryBlue)
            }
        }

        DebugSectionCard(title = "班次設定") {
            Column {
                OutlinedTextField(
                    value = trainNumber,
                    onValueChange = { value ->
                        trainNumber = value.filter(Char::isDigit).take(4)
                    },
                    label = { Text("車次號碼") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(tokens.spacing.spacing12))
                DebugDirectionRow(
                    selected = direction,
                    onSelected = { selectedDirection ->
                        applyRouteChange(newDirection = selectedDirection)
                    },
                )
                Spacer(Modifier.height(tokens.spacing.spacing12))
                DebugDropdownRow(
                    label = "起站",
                    selected = origin,
                    onSelected = { station ->
                        applyRouteChange(newOrigin = station)
                    },
                )
                DebugDivider()
                DebugDropdownRow(
                    label = "迄站",
                    selected = destination,
                    onSelected = { station ->
                        applyRouteChange(newDestination = station)
                    },
                )
                DebugDivider()
                DebugValueRow(
                    label = "搭乘日期",
                    value = ThsrFormatters.displayDate(travelDate),
                    onClick = { showDatePicker = true },
                )
            }
        }

        DebugSectionCard(
            title = "停靠站選擇",
            footer = "起迄站固定停靠，中間站可自由勾選",
        ) {
            Column {
                routeStations.forEachIndexed { index, station ->
                    val isTerminal = station == origin || station == destination
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !isTerminal,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                val nextEnabledStops = if (station in enabledStops) {
                                    enabledStops - station
                                } else {
                                    enabledStops + station
                                }
                                enabledStops = nextEnabledStops
                                val nextActiveStops = routeStations.filter { candidate ->
                                    candidate == origin || candidate == destination || candidate in nextEnabledStops
                                }
                                if (previewFocusStation !in nextActiveStops) {
                                    previewFocusStation = nextActiveStops.firstOrNull() ?: origin
                                }
                            }
                            .padding(vertical = tokens.spacing.spacing4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isTerminal || station in enabledStops,
                            onCheckedChange = if (isTerminal) null else { checked ->
                                val nextEnabledStops = if (checked) {
                                    enabledStops + station
                                } else {
                                    enabledStops - station
                                }
                                enabledStops = nextEnabledStops
                                val nextActiveStops = routeStations.filter { candidate ->
                                    candidate == origin || candidate == destination || candidate in nextEnabledStops
                                }
                                if (previewFocusStation !in nextActiveStops) {
                                    previewFocusStation = nextActiveStops.firstOrNull() ?: origin
                                }
                            },
                        )
                        Text(
                            text = buildString {
                                append(station.localName)
                                if (isTerminal) {
                                    append(if (station == origin) "  起站" else "  迄站")
                                }
                            },
                            color = tokens.colors.textPrimary,
                            style = tokens.typography.bodyStrong,
                        )
                    }
                    if (index != routeStations.lastIndex) {
                        DebugDivider()
                    }
                }
            }
        }

        DebugSectionCard(
            title = "各站時間微調",
            footer = "中間站以到站前 1 分鐘作為到站時間，preview 卡片仍然走正式 TrainOptionCard / StopTimeline。",
        ) {
            Column {
                activeStops.forEachIndexed { index, station ->
                    DebugValueRow(
                        label = station.localName,
                        value = ThsrFormatters.pickerTime(stopTimeFor(station)),
                        onClick = { editingStopTimeStation = station },
                    )
                    if (index != activeStops.lastIndex) {
                        DebugDivider()
                    }
                }
            }
        }

        DebugSectionCard(
            title = "快速設定",
            footer = "快速把模擬班次移到現在附近，方便直接看 timeline 動畫。",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8)) {
                Button(
                    onClick = { setTimesFromNow() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("設為從現在開始")
                }
                Button(
                    onClick = { setTimesFromNow(offsetMinutes = 5) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("5 分鐘後出發")
                }
                Button(
                    onClick = { setTimesFromNow(offsetMinutes = -2) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("已出發 2 分鐘（進行中）")
                }
            }
        }

        DebugSectionCard(
            title = "預覽控制",
            footer = "只覆蓋 Debug 模擬預覽的時間基準；手動指定後會從該時間繼續往前跑，正式查詢結果仍然跟隨現在時間。",
        ) {
            Column {
                DebugPreviewModeRow(
                    selected = previewClockMode,
                    onSelected = { mode ->
                        previewClockMode = mode
                        if (mode == DebugPreviewClockMode.Manual) {
                            setPreviewTime(previewTime)
                        }
                    },
                )
                if (previewClockMode == DebugPreviewClockMode.Manual) {
                    Spacer(Modifier.height(tokens.spacing.spacing12))
                    DebugDropdownRow(
                        label = "快速跳站",
                        selected = previewFocusStation,
                        options = activeStops,
                        onSelected = { previewFocusStation = it },
                    )
                    DebugDivider()
                    DebugValueRow(
                        label = "預覽時間",
                        value = ThsrFormatters.pickerTime(previewTime),
                        onClick = { editingPreviewTime = true },
                    )
                    Spacer(Modifier.height(tokens.spacing.spacing8))
                    Button(
                        onClick = ::jumpPreviewToSelectedStation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("跳到所選站停靠")
                    }
                    Spacer(Modifier.height(tokens.spacing.spacing8))
                    Button(
                        onClick = ::jumpPreviewToNextSegment,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canJumpToNextSegment,
                    ) {
                        Text("跳到下一段途中")
                    }
                } else {
                    Spacer(Modifier.height(tokens.spacing.spacing12))
                    Text(
                        text = "目前使用真實現在時間，展開後會和正式結果一樣持續往前跑。",
                        color = tokens.colors.textSecondary,
                        style = tokens.typography.caption,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8)) {
            Text(
                text = "1 班符合條件",
                color = tokens.colors.textSecondary,
                style = tokens.typography.sectionLabel,
                modifier = Modifier.padding(horizontal = tokens.spacing.spacing4),
            )
            Surface(
                color = tokens.colors.surfaceColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.cornerRadiusLarge),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = layoutProfile.cardContentHorizontalPadding,
                        vertical = tokens.spacing.spacing8,
                    ),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
                ) {
                    Text(
                        text = "模擬預覽",
                        color = tokens.colors.textPrimary,
                        style = tokens.typography.bodyStrong,
                    )
                    Text(
                        text = "此區直接重用正式 TrainOptionCard，包含 StopTimeline 動畫。",
                        color = tokens.colors.textSecondary,
                        style = tokens.typography.caption,
                    )
                    TrainOptionCard(
                        option = previewOption,
                        scheduledNotification = null,
                        layoutProfile = layoutProfile,
                        timelineReferenceDateTime = previewReferenceDateTime,
                        timelineReferenceAnchorDateTime = previewReferenceAnchorDateTime,
                        showSourceLink = false,
                        onScheduleNotification = { _, _ -> },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        TravelDatePickerDialog(
            selectedDate = travelDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                travelDate = date
                showDatePicker = false
            },
        )
    }

    editingStopTimeStation?.let { station ->
        DebugTimePickerDialog(
            selectedTime = stopTimeFor(station),
            onDismiss = { editingStopTimeStation = null },
            onConfirm = { time ->
                stopTimes = stopTimes + (station to time)
                editingStopTimeStation = null
            },
        )
    }

    if (editingPreviewTime) {
        DebugTimePickerDialog(
            selectedTime = previewTime,
            onDismiss = { editingPreviewTime = false },
            onConfirm = { time ->
                setPreviewTime(time)
                editingPreviewTime = false
            },
        )
    }
}

@Composable
private fun DebugSectionCard(
    title: String,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val tokens = ThsrDesignTokens
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8)) {
        Text(
            text = title,
            color = tokens.colors.textSecondary,
            style = tokens.typography.sectionLabel,
            modifier = Modifier.padding(horizontal = tokens.spacing.spacing4),
        )
        Surface(
            color = tokens.colors.surfaceColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.cornerRadiusLarge),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = tokens.spacing.spacing16, vertical = tokens.spacing.spacing12),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
            ) {
                content()
                footer?.let {
                    Text(
                        text = it,
                        color = tokens.colors.textSecondary,
                        style = tokens.typography.caption,
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugDirectionRow(
    selected: DebugTrainDirection,
    onSelected: (DebugTrainDirection) -> Unit,
) {
    val tokens = ThsrDesignTokens
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8)) {
        DebugSelectableButton(
            modifier = Modifier.weight(1f),
            text = DebugTrainDirection.Southbound.title,
            selected = selected == DebugTrainDirection.Southbound,
            onClick = { onSelected(DebugTrainDirection.Southbound) },
        )
        DebugSelectableButton(
            modifier = Modifier.weight(1f),
            text = DebugTrainDirection.Northbound.title,
            selected = selected == DebugTrainDirection.Northbound,
            onClick = { onSelected(DebugTrainDirection.Northbound) },
        )
    }
}

@Composable
private fun DebugPreviewModeRow(
    selected: DebugPreviewClockMode,
    onSelected: (DebugPreviewClockMode) -> Unit,
) {
    val tokens = ThsrDesignTokens
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8)) {
        DebugSelectableButton(
            modifier = Modifier.weight(1f),
            text = DebugPreviewClockMode.Live.title,
            selected = selected == DebugPreviewClockMode.Live,
            onClick = { onSelected(DebugPreviewClockMode.Live) },
        )
        DebugSelectableButton(
            modifier = Modifier.weight(1f),
            text = DebugPreviewClockMode.Manual.title,
            selected = selected == DebugPreviewClockMode.Manual,
            onClick = { onSelected(DebugPreviewClockMode.Manual) },
        )
    }
}

@Composable
private fun DebugSelectableButton(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    Surface(
        modifier = modifier,
        color = if (selected) tokens.colors.primaryBlue.copy(alpha = 0.14f) else tokens.colors.backgroundColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.cornerRadiusSmall),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) tokens.colors.primaryBlue else tokens.colors.dividerColor,
        ),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(vertical = tokens.spacing.spacing12),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (selected) tokens.colors.primaryBlue else tokens.colors.textPrimary,
                style = tokens.typography.action,
            )
        }
    }
}

@Composable
private fun DebugDropdownRow(
    label: String,
    selected: Station,
    onSelected: (Station) -> Unit,
    options: List<Station> = Station.entries,
) {
    var expanded by remember(selected, options) { mutableStateOf(false) }
    DebugValueRow(
        label = label,
        value = selected.localName,
        onClick = { expanded = true },
        trailing = {
            Box {
                DebugFormValue(
                    value = selected.localName,
                    onClick = { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(160.dp),
                ) {
                    options.forEach { station ->
                        DropdownMenuItem(
                            text = { Text(station.localName) },
                            onClick = {
                                expanded = false
                                onSelected(station)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DebugValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = ThsrDesignTokens
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.cornerRadiusSmall))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = tokens.spacing.spacing8),
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
            DebugFormValue(
                value = value,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun DebugFormValue(
    value: String,
    onClick: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.cornerRadiusSmall))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = tokens.spacing.spacing8, vertical = tokens.spacing.spacing4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.spacing4),
    ) {
        Text(
            text = value,
            color = tokens.colors.textSecondary,
            style = tokens.typography.formValue,
            textAlign = TextAlign.End,
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = tokens.colors.textTertiary,
            modifier = Modifier.size(tokens.sizes.disclosureIcon),
        )
    }
}

@Composable
private fun DebugDivider() {
    HorizontalDivider(color = ThsrDesignTokens.colors.dividerColor)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugTimePickerDialog(
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

private fun buildDebugTrainOption(
    trainNumber: String,
    direction: DebugTrainDirection,
    origin: Station,
    destination: Station,
    travelDate: LocalDate,
    activeStops: List<Station>,
    stopTimes: Map<Station, LocalTime>,
): TrainOption {
    val stops = activeStops.map { station ->
        val departureTime = stopTimes[station]
            ?: debugDefaultTime(station = station, travelDate = travelDate, activeStops = activeStops)
        when (station) {
            origin -> TimelineStop(
                station = station,
                arrivalTime = null,
                departureTime = departureTime,
            )

            destination -> TimelineStop(
                station = station,
                arrivalTime = departureTime,
                departureTime = null,
            )

            else -> TimelineStop(
                station = station,
                arrivalTime = departureTime.minusMinutes(1),
                departureTime = departureTime,
            )
        }
    }
    val resolvedTrainNumber = trainNumber.ifBlank { "0811" }.padStart(4, '0')
    val bookingStatus = if (direction == DebugTrainDirection.Southbound) {
        BookingStatus.Available
    } else {
        BookingStatus.Available
    }
    return TrainOption(
        trainNo = resolvedTrainNumber,
        origin = origin,
        destination = destination,
        travelDate = travelDate,
        departureTime = stops.first().departureTime ?: LocalTime.of(6, 0),
        arrivalTime = stops.last().arrivalTime ?: stops.last().departureTime ?: LocalTime.of(7, 0),
        stops = stops,
        bookingStatus = bookingStatus,
        seatAvailability = SeatAvailabilityDetail(
            standardSeatStatus = SeatStatus.Available,
            businessSeatStatus = SeatStatus.Available,
        ),
        discounts = emptyList(),
        source = TrainDataSource(
            timetable = SourceStatus("Debug 模擬資料", SourceState.Live),
            seatAvailability = SourceStatus("Debug 模擬資料", SourceState.Live),
            discountFeed = SourceStatus("Debug 模擬資料", SourceState.Live),
        ),
    )
}

private fun alignDebugTerminals(
    origin: Station,
    destination: Station,
    direction: DebugTrainDirection,
): Pair<Station, Station> = when {
    direction == DebugTrainDirection.Southbound && origin.sortIndex > destination.sortIndex -> destination to origin
    direction == DebugTrainDirection.Northbound && origin.sortIndex < destination.sortIndex -> destination to origin
    else -> origin to destination
}

private fun debugRouteStations(
    origin: Station,
    destination: Station,
    direction: DebugTrainDirection,
): List<Station> {
    val lowerIndex = minOf(origin.sortIndex, destination.sortIndex)
    val upperIndex = maxOf(origin.sortIndex, destination.sortIndex)
    val routeStations = Station.entries.filter { station ->
        station.sortIndex in lowerIndex..upperIndex
    }
    return if (direction == DebugTrainDirection.Southbound) {
        routeStations
    } else {
        routeStations.reversed()
    }
}

private fun debugDefaultTime(
    station: Station,
    travelDate: LocalDate,
    activeStops: List<Station>,
): LocalTime {
    if (station !in activeStops) {
        return LocalDateTime.of(travelDate, LocalTime.of(6, 0)).toLocalTime()
    }
    return LocalTime.of(6, 0).plusMinutes(cumulativeTravelMinutes(station, activeStops).toLong())
}

private fun cumulativeTravelMinutes(
    station: Station,
    activeStops: List<Station>,
): Int {
    val targetIndex = activeStops.indexOf(station)
    if (targetIndex <= 0) return 0
    var total = 0
    for (index in 1..targetIndex) {
        total += travelMinutes(activeStops[index - 1], activeStops[index])
    }
    return total
}

private fun travelMinutes(
    first: Station,
    second: Station,
): Int {
    val lowerIndex = minOf(first.sortIndex, second.sortIndex)
    val upperIndex = maxOf(first.sortIndex, second.sortIndex)
    var total = 0
    for (index in lowerIndex until upperIndex) {
        total += stationGapMinutes(index)
    }
    return total
}

private fun stationGapMinutes(index: Int): Int {
    val gapMinutes = listOf(7, 8, 11, 11, 12, 19, 13, 12, 13, 18, 12)
    return gapMinutes.getOrElse(index) { 12 }
}

private fun dwellSecondsFor(station: Station): Long {
    return when (station) {
        Station.Nangang,
        Station.Zuoying,
        -> 0L

        else -> 40L
    }
}
