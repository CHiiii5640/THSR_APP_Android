package com.chiiii5640.thsrapp.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class TrainOption(
    val trainNo: String,
    val origin: Station,
    val destination: Station,
    val travelDate: LocalDate,
    val departureTime: LocalTime,
    val arrivalTime: LocalTime,
    val stops: List<TimelineStop>,
    val bookingStatus: BookingStatus,
    val seatStatus: SeatStatus,
    val discounts: List<DiscountOffer>,
    val source: TrainDataSource,
) {
    val duration: Duration
        get() = Duration.between(
            LocalDateTime.of(travelDate, departureTime),
            LocalDateTime.of(travelDate, arrivalTime),
        )

    val officialBookingUrl: String
        get() = "https://irs.thsrc.com.tw/IMINT/?locale=tw"
}

data class TimelineStop(
    val station: Station,
    val arrivalTime: LocalTime?,
    val departureTime: LocalTime?,
)

enum class BookingStatus {
    Available,
    NotYetOpen,
    Closed,
}

enum class SeatStatus {
    Unknown,
    Available,
    Limited,
    SoldOut,
}

data class DiscountOffer(
    val type: DiscountType,
    val label: String,
    val percentOff: Int?,
)

enum class DiscountType {
    EarlyBird,
    CollegeStudent,
    Other,
}

data class TrainDataSource(
    val timetable: SourceStatus,
    val seatAvailability: SourceStatus,
    val discountFeed: SourceStatus,
)

data class SourceStatus(
    val label: String,
    val state: SourceState,
)

enum class SourceState {
    Live,
    Cache,
    Fallback,
    Unavailable,
}
