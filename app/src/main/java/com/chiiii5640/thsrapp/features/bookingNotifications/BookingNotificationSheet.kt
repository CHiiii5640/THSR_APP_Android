package com.chiiii5640.thsrapp.features.bookingNotifications

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import com.chiiii5640.thsrapp.features.searchDashboard.TravelDatePickerDialog
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingNotificationSheet(
    initialReminderAt: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val tokens = ThsrDesignTokens
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
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = tokens.colors.warningOrange,
                    ),
                ) {
                    Text(
                        text = "通知日期 $selectedDate",
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    text = "通知時間",
                    color = tokens.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
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
