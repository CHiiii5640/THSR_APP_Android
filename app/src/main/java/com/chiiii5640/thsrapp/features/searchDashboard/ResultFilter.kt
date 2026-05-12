package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.TrainOption

enum class ResultFilter(val label: String) {
    All("全部"),
    Bookable("可訂位"),
    Discounted("有優惠"),
    HasSeats("有座位"),
    Fastest("最快");

    fun apply(options: List<TrainOption>): List<TrainOption> =
        when (this) {
            All -> options
            Bookable -> options.filter { it.bookingStatus == BookingStatus.Available }
            Discounted -> options.filter { it.discounts.isNotEmpty() }
            HasSeats -> options.filter { it.seatStatus == SeatStatus.Available || it.seatStatus == SeatStatus.Limited }
            Fastest -> options.minByOrNull { it.duration }?.let(::listOf).orEmpty()
        }
}
