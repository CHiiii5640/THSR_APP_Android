package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.TrainOption
import java.time.Duration

enum class ResultFilter(val label: String) {
    All("全部"),
    Bookable("可訂位"),
    Discounted("有優惠"),
    HasSeats("有座位"),
    Fastest("最快");

    fun apply(options: List<TrainOption>, fastestDuration: Long? = null): List<TrainOption> =
        when (this) {
            All -> options
            Bookable -> options.filter { it.bookingStatus == BookingStatus.Available }
            Discounted -> options.filter { it.discounts.isNotEmpty() }.sortedWith(departureTimeFirst)
            HasSeats -> options.filter { it.seatStatus == SeatStatus.Available || it.seatStatus == SeatStatus.Limited }
            Fastest -> fastestOptions(options, fastestDuration)
        }

    companion object {
        fun fastestDurationOptions(options: List<TrainOption>): List<Long> =
            options.map { it.duration.toMinutes() }.distinct().sorted()

        private fun fastestOptions(options: List<TrainOption>, selectedDuration: Long?): List<TrainOption> {
            val duration = selectedDuration ?: options.minOfOrNull { it.duration.toMinutes() } ?: return emptyList()
            return options
                .filter { it.duration == Duration.ofMinutes(duration) }
                .sortedWith(departureTimeFirst)
        }

        private val departureTimeFirst = compareBy<TrainOption> { it.departureTime }.thenBy { it.trainNo }
    }
}
