package com.chiiii5640.thsrapp.features.bookingNotifications

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chiiii5640.thsrapp.features.searchDashboard.TravelDatePickerDialog
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingNotificationSheet(
    initialReminderAt: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    var selectedDate by remember(initialReminderAt) { mutableStateOf(initialReminderAt.toLocalDate()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = initialReminderAt.hour,
        initialMinute = initialReminderAt.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedDate.atTime(timePickerState.hour, timePickerState.minute))
                },
            ) {
                Text("設定通知")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = {
            Text("開票通知")
        },
        text = {
            androidx.compose.foundation.layout.Column {
                TextButton(onClick = { showDatePicker = true }) {
                    Text("通知日期 $selectedDate")
                }
                TimeInput(state = timePickerState)
            }
        },
    )

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
}
