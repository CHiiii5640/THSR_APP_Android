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
    val liveStatus: TrainLiveStatus = TrainLiveStatus.unresolved(),
) {
    val duration: Duration
        get() {
            val departure = LocalDateTime.of(travelDate, departureTime)
            val sameDayArrival = LocalDateTime.of(travelDate, arrivalTime)
            val resolvedArrival = if (arrivalTime.isBefore(departureTime)) {
                sameDayArrival.plusDays(1)
            } else {
                sameDayArrival
            }
            return Duration.between(departure, resolvedArrival)
        }

    val seatStatus: SeatStatus
        get() = seatAvailability?.summary ?: SeatStatus.Unknown

    val officialBookingUrl: String
        get() = "https://irs.thsrc.com.tw/IMINT/?locale=tw"

    val bookingNotificationOpeningDate: LocalDate?
        get() = (bookingStatus as? BookingStatus.NotYetOpen)?.openingDate

    val canScheduleBookingNotification: Boolean
        get() = bookingNotificationOpeningDate != null
}

data class TrainLiveStatus(
    val serviceState: TrainServiceState,
    val summary: TimelineStatusSummary,
) {
    companion object {
        fun unresolved(): TrainLiveStatus = TrainLiveStatus(
            serviceState = TrainServiceState.NotDeparted,
            summary = TimelineStatusSummary(
                headline = "等待班次更新",
                detail = "尚未取得即時行車狀態",
                currentStopIndex = null,
                nextStopIndex = null,
                activeSegmentIndex = null,
            ),
        )
    }
}

enum class TrainServiceState {
    NotDeparted,
    DepartingSoon,
    InTransit,
    ApproachingStation,
    DwellingAtStation,
    DepartedStation,
    ArrivedDestination,
}

data class TimelineStatusSummary(
    val headline: String,
    val detail: String,
    val currentStopIndex: Int?,
    val nextStopIndex: Int?,
    val activeSegmentIndex: Int?,
)

data class TimelineStop(
    val station: Station,
    val arrivalTime: LocalTime?,
    val departureTime: LocalTime?,
    val role: TimelineStopRole = TimelineStopRole.Intermediate,
)

enum class TimelineStopRole {
    Origin,
    Intermediate,
    Destination,
}

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

val TrainOption.timelineStops: List<TimelineStop>
    get() {
        val southbound = origin.sortIndex < destination.sortIndex
        val orderedStops = stops
            .filter { stop ->
                if (southbound) {
                    stop.station.sortIndex <= destination.sortIndex
                } else {
                    stop.station.sortIndex >= destination.sortIndex
                }
            }
            .sortedBy { stop ->
                if (southbound) stop.station.sortIndex else -stop.station.sortIndex
            }
            .toMutableList()

        if (orderedStops.none { it.station == origin }) {
            val originStop = TimelineStop(
                station = origin,
                arrivalTime = null,
                departureTime = departureTime,
            )
            val insertionIndex = orderedStops.indexOfFirst { stop ->
                if (southbound) {
                    stop.station.sortIndex > origin.sortIndex
                } else {
                    stop.station.sortIndex < origin.sortIndex
                }
            }.takeIf { it >= 0 } ?: orderedStops.size
            orderedStops.add(insertionIndex, originStop)
        }

        if (orderedStops.none { it.station == destination }) {
            val destinationStop = TimelineStop(
                station = destination,
                arrivalTime = arrivalTime,
                departureTime = null,
            )
            val insertionIndex = orderedStops.indexOfFirst { stop ->
                if (southbound) {
                    stop.station.sortIndex > destination.sortIndex
                } else {
                    stop.station.sortIndex < destination.sortIndex
                }
            }.takeIf { it >= 0 } ?: orderedStops.size
            orderedStops.add(insertionIndex, destinationStop)
        }

        return orderedStops.map { stop ->
            when (stop.station) {
                origin -> stop.copy(
                    departureTime = stop.departureTime ?: departureTime,
                    role = TimelineStopRole.Origin,
                )

                destination -> stop.copy(
                    arrivalTime = stop.arrivalTime ?: arrivalTime,
                    departureTime = null,
                    role = TimelineStopRole.Destination,
                )

                else -> stop.copy(role = TimelineStopRole.Intermediate)
            }
        }
    }
