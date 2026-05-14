package com.chiiii5640.thsrapp.features.bookingNotifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.features.searchDashboard.TravelDatePickerDialog
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingNotificationSheet(
    option: TrainOption,
    initialReminderAt: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val tokens = ThsrDesignTokens
    var selectedDate by remember(initialReminderAt) { mutableStateOf(initialReminderAt.toLocalDate()) }
    var selectedTime by remember(initialReminderAt) { mutableStateOf(initialReminderAt.toLocalTime().withSecond(0).withNano(0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val estimatedOpeningDate = BookingNotificationDefaults.estimatedOpeningDate(option)
    val interactionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = tokens.colors.backgroundColor.copy(alpha = 0.96f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = tokens.colors.primaryBlue)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "設定開票通知",
                        color = tokens.colors.textPrimary,
                        style = tokens.typography.largeTitle,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onConfirm(selectedDate.atTime(selectedTime))
                        },
                    ) {
                        Text("設定", color = tokens.colors.primaryBlue)
                    }
                }

                NotificationDetailCard {
                    NotificationInfoRow("車次", option.trainNo.padStart(4, '0'))
                    NotificationDivider()
                    NotificationInfoRow("路線", "${option.origin.localName} → ${option.destination.localName}")
                    NotificationDivider()
                    NotificationInfoRow("搭乘日期", ThsrFormatters.displayDate(option.travelDate))
                    NotificationDivider()
                    NotificationInfoRow("預估開放日", ThsrFormatters.displayDate(estimatedOpeningDate))
                }

                NotificationDetailCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "提醒日期\n與時間",
                            color = tokens.colors.textPrimary,
                            style = tokens.typography.formLabel,
                            modifier = Modifier.weight(0.8f),
                        )
                        Row(
                            modifier = Modifier.weight(1.7f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            NotificationSelectionPill(
                                modifier = Modifier.weight(1f),
                                label = selectedDate.displayNotificationDate(),
                                interactionSource = interactionSource,
                                onClick = { showDatePicker = true },
                            )
                            NotificationSelectionPill(
                                modifier = Modifier.weight(0.8f),
                                label = selectedTime.displayNotificationTime(),
                                interactionSource = interactionSource,
                                onClick = { showTimePicker = true },
                            )
                        }
                    }

                    Text(
                        text = "預設是預估開放日前一天 23:55，也可以改成你想收到提醒的日期與時間。",
                        color = tokens.colors.textSecondary,
                        style = tokens.typography.body,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        TravelDatePickerDialog(
            selectedDate = selectedDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                selectedDate = it
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            selectedTime = selectedTime,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                showTimePicker = false
                selectedTime = it
            },
        )
    }
}

@Composable
private fun NotificationDetailCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = ThsrDesignTokens
    Surface(
        color = tokens.colors.elevatedSurfaceColor,
        shape = RoundedCornerShape(tokens.radii.cornerRadiusLarge),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun NotificationInfoRow(
    title: String,
    value: String,
) {
    val tokens = ThsrDesignTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = tokens.colors.textPrimary,
            style = tokens.typography.formLabel,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = tokens.colors.textSecondary,
            style = tokens.typography.formValue,
        )
    }
}

@Composable
private fun NotificationDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ThsrDesignTokens.colors.dividerColor),
    )
}

@Composable
private fun NotificationSelectionPill(
    modifier: Modifier = Modifier,
    label: String,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val tokens = ThsrDesignTokens
    Box(
        modifier = modifier
            .background(
                color = tokens.colors.surfaceColor,
                shape = RoundedCornerShape(tokens.radii.cornerRadiusMedium),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = tokens.colors.textPrimary,
            style = tokens.typography.formValue,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
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
            TextButton(onClick = { onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute)) }) {
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

private fun LocalDate.displayNotificationDate(): String =
    format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))

private fun LocalTime.displayNotificationTime(): String =
    format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
