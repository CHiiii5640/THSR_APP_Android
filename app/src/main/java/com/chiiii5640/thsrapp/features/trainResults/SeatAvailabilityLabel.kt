package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.chiiii5640.thsrapp.core.model.SeatStatus

@Composable
fun SeatAvailabilityLabel(status: SeatStatus) {
    Text(
        when (status) {
            SeatStatus.Unknown -> "座位未知"
            SeatStatus.Available -> "有座位"
            SeatStatus.Limited -> "座位有限"
            SeatStatus.SoldOut -> "售完"
        },
    )
}
