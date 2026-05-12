package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.TrainDataSource
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.features.discounts.DiscountProvider
import com.chiiii5640.thsrapp.features.seatAvailability.SeatAvailabilityProvider
import com.chiiii5640.thsrapp.features.timetable.TimetableProvider
import java.time.Clock
import java.time.LocalDate

class SearchDashboardService(
    private val timetableProvider: TimetableProvider,
    private val seatAvailabilityProvider: SeatAvailabilityProvider,
    private val discountProvider: DiscountProvider,
    private val clock: Clock,
) {
    suspend fun search(query: TripQuery): SearchResult {
        val timetable = timetableProvider.trains(query)
        val filtered = timetable.trains
            .filter { !it.departureTime.isBefore(query.departureAfter) }
            .sortedBy { it.departureTime }

        val seats = seatAvailabilityProvider.seats(
            query = query,
            trainNos = filtered.map { it.trainNo },
            skip = timetable.usedSupplyDateFallback,
        )
        val discounts = discountProvider.discounts(query.travelDate, filtered, query.forceRefresh)

        val options = filtered.map { train ->
            TrainOption(
                trainNo = train.trainNo,
                origin = query.origin,
                destination = query.destination,
                travelDate = query.travelDate,
                departureTime = train.departureTime,
                arrivalTime = train.arrivalTime,
                stops = train.stops,
                bookingStatus = bookingStatus(query),
                seatStatus = seats.seatsByTrainNo[train.trainNo] ?: SeatStatus.Unknown,
                discounts = discounts.offersByTrainNo[train.trainNo].orEmpty(),
                source = TrainDataSource(
                    timetable = timetable.status,
                    seatAvailability = seats.status,
                    discountFeed = discounts.status,
                ),
            )
        }

        return SearchResult(
            options = options,
            sourceStatuses = listOf(timetable.status, seats.status, discounts.status),
        )
    }

    private fun bookingStatus(query: TripQuery): BookingStatus {
        val today = LocalDate.now(clock)
        return when {
            query.travelDate.isBefore(today) -> BookingStatus.Closed
            query.travelDate.isAfter(today.plusDays(28)) -> BookingStatus.NotYetOpen
            else -> BookingStatus.Available
        }
    }
}

data class SearchResult(
    val options: List<TrainOption>,
    val sourceStatuses: List<SourceStatus> = listOf(
        SourceStatus("timetable unavailable", SourceState.Unavailable),
        SourceStatus("seat unavailable", SourceState.Unavailable),
        SourceStatus("discount unavailable", SourceState.Unavailable),
    ),
)
