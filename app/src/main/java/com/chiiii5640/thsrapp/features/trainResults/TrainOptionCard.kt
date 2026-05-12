package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.time.ThsrFormatters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainOptionCard(option: TrainOption) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("車次 ${option.trainNo}", fontWeight = FontWeight.SemiBold)
                Text(option.bookingStatus.label())
            }
            Text("${ThsrFormatters.time(option.departureTime)} → ${ThsrFormatters.time(option.arrivalTime)}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(option.seatStatus.label()) })
                option.discounts.forEach { discount ->
                    AssistChip(onClick = {}, label = { Text(discount.label) })
                }
                if (option.bookingStatus == BookingStatus.NotYetOpen) {
                    AssistChip(onClick = {}, label = { Text("通知") })
                }
            }
            StopTimeline(option.stops)
        }
    }
}

private fun BookingStatus.label(): String = when (this) {
    BookingStatus.Available -> "可訂位"
    BookingStatus.NotYetOpen -> "未開放"
    BookingStatus.Closed -> "已截止"
}

private fun SeatStatus.label(): String = when (this) {
    SeatStatus.Unknown -> "座位未知"
    SeatStatus.Available -> "有座位"
    SeatStatus.Limited -> "座位有限"
    SeatStatus.SoldOut -> "售完"
}
