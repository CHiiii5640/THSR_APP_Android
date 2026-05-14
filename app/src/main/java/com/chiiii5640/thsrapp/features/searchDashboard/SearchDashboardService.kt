package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.logging.ThsrLog
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.TrainDataSource
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.features.discounts.DiscountProvider
import com.chiiii5640.thsrapp.features.seatAvailability.SeatAvailabilityProvider
import com.chiiii5640.thsrapp.features.timetable.FallbackTimetableProvider
import com.chiiii5640.thsrapp.features.timetable.TimetableProvider
import java.time.Clock
import java.time.LocalDate

class SearchDashboardService(
    private val timetableProvider: TimetableProvider,
    private val seatAvailabilityProvider: SeatAvailabilityProvider,
    private val discountProvider: DiscountProvider,
    private val fallbackTimetableProvider: FallbackTimetableProvider? = null,
    private val clock: Clock,
) {
    private val bookingWindowCalculator = BookingWindowCalculator(clock)

    suspend fun search(query: TripQuery): SearchResult {
        val primaryTimetable = timetableProvider.trains(query)
        val fallbackTimetable = if (primaryTimetable.trains.isEmpty()) {
            ThsrLog.i("primary timetable empty, trying feed fallback for ${query.origin.localName}-${query.destination.localName} ${query.travelDate} ${query.departureAfter}")
            fallbackTimetableProvider?.trains(query, query.forceRefresh)
        } else {
            null
        }

        val timetable = when {
            !fallbackTimetable?.trains.isNullOrEmpty() -> com.chiiii5640.thsrapp.features.timetable.TimetableResult(
                trains = fallbackTimetable!!.trains,
                status = fallbackTimetable.status,
                usedSupplyDateFallback = true,
            )
            primaryTimetable.trains.isEmpty() && fallbackTimetable != null -> {
                ThsrLog.w("feed fallback returned no trains: ${fallbackTimetable.status.label}")
                com.chiiii5640.thsrapp.features.timetable.TimetableResult(
                    trains = emptyList(),
                    status = fallbackTimetable.status,
                    usedSupplyDateFallback = false,
                )
            }

            else -> primaryTimetable
        }

        val filtered = timetable.trains
            .filter { !it.departureTime.isBefore(query.departureAfter) }
            .sortedBy { it.departureTime }

        if (filtered.isEmpty()) {
            return SearchResult(
                options = emptyList(),
                sourceStatuses = listOf(
                    timetable.status,
                    SourceStatus("seat skipped without timetable results", SourceState.Unavailable),
                    SourceStatus("discount skipped without timetable results", SourceState.Unavailable),
                ),
            )
        }

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
                bookingStatus = bookingWindowCalculator.bookingStatus(
                    travelDate = query.travelDate,
                    departureTime = train.departureTime,
                ),
                seatAvailability = seats.seatsByTrainNo[train.trainNo],
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
}

data class SearchResult(
    val options: List<TrainOption>,
    val sourceStatuses: List<SourceStatus> = listOf(
        SourceStatus("timetable unavailable", SourceState.Unavailable),
        SourceStatus("seat unavailable", SourceState.Unavailable),
        SourceStatus("discount unavailable", SourceState.Unavailable),
    ),
)
