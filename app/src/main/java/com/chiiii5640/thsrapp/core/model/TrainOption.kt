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
    val seatAvailability: SeatAvailabilityDetail?,
    val discounts: List<DiscountOffer>,
    val source: TrainDataSource,
) {
    val duration: Duration
        get() = Duration.between(
            LocalDateTime.of(travelDate, departureTime),
            LocalDateTime.of(travelDate, arrivalTime),
        )

    val seatStatus: SeatStatus
        get() = seatAvailability?.summary ?: SeatStatus.Unknown

    val officialBookingUrl: String
        get() = "https://irs.thsrc.com.tw/IMINT/?locale=tw"

    val bookingNotificationOpeningDate: LocalDate?
        get() = (bookingStatus as? BookingStatus.NotYetOpen)?.openingDate

    val canScheduleBookingNotification: Boolean
        get() = bookingNotificationOpeningDate != null
}

data class TimelineStop(
    val station: Station,
    val arrivalTime: LocalTime?,
    val departureTime: LocalTime?,
)

sealed class BookingStatus {
    object Available : BookingStatus()

    data class NotYetOpen(val openingDate: LocalDate) : BookingStatus()

    object Closed : BookingStatus()
}

enum class SeatStatus {
    Unknown,
    Available,
    Limited,
    SoldOut,
}

data class SeatAvailabilityDetail(
    val standardSeatStatus: SeatStatus,
    val businessSeatStatus: SeatStatus,
    val boardStandardSeatStatus: SeatStatus = SeatStatus.Unknown,
    val boardBusinessSeatStatus: SeatStatus = SeatStatus.Unknown,
    val hasBoardSeatStatus: Boolean = false,
) {
    val summary: SeatStatus
        get() = listOfNotNull(
            standardSeatStatus,
            businessSeatStatus,
            boardStandardSeatStatus.takeIf { hasBoardSeatStatus },
            boardBusinessSeatStatus.takeIf { hasBoardSeatStatus },
        ).fold(SeatStatus.Unknown) { best, current ->
            if (current.rank > best.rank) current else best
        }
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

private val SeatStatus.rank: Int
    get() = when (this) {
        SeatStatus.Unknown -> 0
        SeatStatus.SoldOut -> 1
        SeatStatus.Limited -> 2
        SeatStatus.Available -> 3
    }
