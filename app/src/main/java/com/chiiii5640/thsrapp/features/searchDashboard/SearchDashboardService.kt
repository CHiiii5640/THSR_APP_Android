package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.core.logging.ThsrLog
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.TimelineStatusSummary
import com.chiiii5640.thsrapp.core.model.TrainLiveStatus
import com.chiiii5640.thsrapp.core.model.TrainDataSource
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.model.TrainServiceState
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.features.discounts.DiscountProvider
import com.chiiii5640.thsrapp.features.seatAvailability.SeatAvailabilityProvider
import com.chiiii5640.thsrapp.features.timetable.FallbackTimetableProvider
import com.chiiii5640.thsrapp.features.timetable.TimetableTrain
import com.chiiii5640.thsrapp.features.timetable.TimetableProvider
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SearchDashboardService(
    private val timetableProvider: TimetableProvider,
    private val seatAvailabilityProvider: SeatAvailabilityProvider,
    private val discountProvider: DiscountProvider,
    private val fallbackTimetableProvider: FallbackTimetableProvider? = null,
    private val clock: Clock,
) {
    private val bookingWindowCalculator = BookingWindowCalculator(clock)
    private val departingSoonWindowMinutes = 8L
    private val departedPulseWindowMinutes = 2L
    private val approachingWindowMinutes = 6L

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
            val bookingStatus = bookingWindowCalculator.bookingStatus(
                travelDate = query.travelDate,
                departureTime = train.departureTime,
            )
            TrainOption(
                trainNo = train.trainNo,
                origin = query.origin,
                destination = query.destination,
                travelDate = query.travelDate,
                departureTime = train.departureTime,
                arrivalTime = train.arrivalTime,
                stops = train.stops,
                bookingStatus = bookingStatus,
                seatAvailability = seats.seatsByTrainNo[train.trainNo],
                discounts = discounts.offersByTrainNo[train.trainNo].orEmpty(),
                source = TrainDataSource(
                    timetable = timetable.status,
                    seatAvailability = seats.status,
                    discountFeed = discounts.status,
                ),
                liveStatus = deriveLiveStatus(
                    train = train,
                    travelDate = query.travelDate,
                    bookingStatus = bookingStatus,
                ),
            )
        }

        return SearchResult(
            options = options,
            sourceStatuses = listOf(timetable.status, seats.status, discounts.status),
        )
    }

    private fun deriveLiveStatus(
        train: TimetableTrain,
        travelDate: LocalDate,
        bookingStatus: BookingStatus,
    ): TrainLiveStatus {
        val resolvedStops = resolveStops(
            stops = train.stops,
            travelDate = travelDate,
            fallbackDeparture = train.departureTime,
            fallbackArrival = train.arrivalTime,
        )
        if (resolvedStops.isEmpty()) {
            return TrainLiveStatus.unresolved()
        }

        val now = LocalDateTime.now(clock)
        val firstDeparture = resolvedStops.firstNotNullOfOrNull { it.departure ?: it.arrival }
            ?: return TrainLiveStatus.unresolved()
        val lastArrival = resolvedStops.asReversed().firstNotNullOfOrNull { it.arrival ?: it.departure }
            ?: return TrainLiveStatus.unresolved()

        val firstStation = resolvedStops.first()
        val lastStation = resolvedStops.last()

        if (now.isBefore(firstDeparture.minusMinutes(departingSoonWindowMinutes))) {
            val headline = when (bookingStatus) {
                is BookingStatus.NotYetOpen -> "未發車"
                BookingStatus.Available -> "未發車"
                BookingStatus.Closed -> "已離站"
            }
            return TrainLiveStatus(
                serviceState = TrainServiceState.NotDeparted,
                summary = TimelineStatusSummary(
                    headline = headline,
                    detail = "預計 ${train.departureTime} 自 ${firstStation.station.localName} 發車",
                    currentStopIndex = firstStation.index,
                    nextStopIndex = firstStation.index,
                    activeSegmentIndex = null,
                ),
            )
        }

        if (now.isBefore(firstDeparture)) {
            return TrainLiveStatus(
                serviceState = TrainServiceState.DepartingSoon,
                summary = TimelineStatusSummary(
                    headline = "即將發車",
                    detail = "${firstStation.station.localName} ${train.departureTime}",
                    currentStopIndex = firstStation.index,
                    nextStopIndex = firstStation.index,
                    activeSegmentIndex = null,
                ),
            )
        }

        resolvedStops.forEachIndexed { index, stop ->
            val arrival = stop.arrival
            val departure = stop.departure

            if (arrival != null && departure != null && !now.isBefore(arrival) && now.isBefore(departure)) {
                return TrainLiveStatus(
                    serviceState = TrainServiceState.DwellingAtStation,
                    summary = TimelineStatusSummary(
                        headline = "停靠中",
                        detail = stop.station.localName,
                        currentStopIndex = stop.index,
                        nextStopIndex = stop.index,
                        activeSegmentIndex = (index - 1).takeIf { it >= 0 },
                    ),
                )
            }

            if (departure != null && now.isAfter(departure) && now.isBefore(departure.plusMinutes(departedPulseWindowMinutes))) {
                return TrainLiveStatus(
                    serviceState = TrainServiceState.DepartedStation,
                    summary = TimelineStatusSummary(
                        headline = "已離站",
                        detail = stop.station.localName,
                        currentStopIndex = stop.index,
                        nextStopIndex = (index + 1).takeIf { it <= lastStation.index },
                        activeSegmentIndex = index.takeIf { it < resolvedStops.lastIndex },
                    ),
                )
            }
        }

        for (index in 0 until resolvedStops.lastIndex) {
            val from = resolvedStops[index]
            val to = resolvedStops[index + 1]
            val departure = from.departure ?: continue
            val arrival = to.arrival ?: to.departure ?: continue
            if (now.isBefore(departure) || now.isAfter(arrival)) {
                continue
            }

            val isApproaching = !now.isBefore(arrival.minusMinutes(approachingWindowMinutes))
            return TrainLiveStatus(
                serviceState = if (isApproaching) TrainServiceState.ApproachingStation else TrainServiceState.InTransit,
                summary = TimelineStatusSummary(
                    headline = if (isApproaching) "即將進站" else "行進中",
                    detail = "${from.station.localName} -> ${to.station.localName}",
                    currentStopIndex = from.index,
                    nextStopIndex = to.index,
                    activeSegmentIndex = index,
                ),
            )
        }

        if (now.isAfter(lastArrival.minusMinutes(approachingWindowMinutes)) && now.isBefore(lastArrival)) {
            return TrainLiveStatus(
                serviceState = TrainServiceState.ApproachingStation,
                summary = TimelineStatusSummary(
                    headline = "即將抵達終點",
                    detail = lastStation.station.localName,
                    currentStopIndex = lastStation.index,
                    nextStopIndex = lastStation.index,
                    activeSegmentIndex = resolvedStops.lastIndex - 1,
                ),
            )
        }

        return if (now.isAfter(lastArrival) || now.isEqual(lastArrival)) {
            TrainLiveStatus(
                serviceState = TrainServiceState.ArrivedDestination,
                summary = TimelineStatusSummary(
                    headline = "已抵達終點",
                    detail = lastStation.station.localName,
                    currentStopIndex = lastStation.index,
                    nextStopIndex = null,
                    activeSegmentIndex = null,
                ),
            )
        } else {
            TrainLiveStatus.unresolved()
        }
    }

    private fun resolveStops(
        stops: List<com.chiiii5640.thsrapp.core.model.TimelineStop>,
        travelDate: LocalDate,
        fallbackDeparture: LocalTime,
        fallbackArrival: LocalTime,
    ): List<ResolvedStopTime> {
        if (stops.isEmpty()) {
            return emptyList()
        }

        val fallbackDepartureDateTime = LocalDateTime.of(travelDate, fallbackDeparture)
        val fallbackArrivalDateTime = resolveDateTime(
            travelDate = travelDate,
            time = fallbackArrival,
            notBefore = fallbackDepartureDateTime,
        )

        var anchor = fallbackDepartureDateTime.minusMinutes(1)
        return stops.mapIndexed { index, stop ->
            val arrival = stop.arrivalTime?.let {
                resolveDateTime(
                    travelDate = travelDate,
                    time = it,
                    notBefore = anchor,
                )
            }
            if (arrival != null && arrival.isAfter(anchor)) {
                anchor = arrival
            }
            val departure = stop.departureTime?.let {
                resolveDateTime(
                    travelDate = travelDate,
                    time = it,
                    notBefore = anchor,
                )
            }
            if (departure != null && departure.isAfter(anchor)) {
                anchor = departure
            }

            ResolvedStopTime(
                index = index,
                station = stop.station,
                arrival = arrival ?: fallbackArrivalDateTime.takeIf { index == stops.lastIndex && stop.departureTime == null },
                departure = departure ?: fallbackDepartureDateTime.takeIf { index == 0 && stop.arrivalTime == null },
            )
        }
    }

    private fun resolveDateTime(
        travelDate: LocalDate,
        time: LocalTime,
        notBefore: LocalDateTime,
    ): LocalDateTime {
        var resolved = LocalDateTime.of(travelDate, time)
        while (resolved.isBefore(notBefore)) {
            resolved = resolved.plusDays(1)
        }
        return resolved
    }
}

private data class ResolvedStopTime(
    val index: Int,
    val station: com.chiiii5640.thsrapp.core.model.Station,
    val arrival: LocalDateTime?,
    val departure: LocalDateTime?,
)

data class SearchResult(
    val options: List<TrainOption>,
    val sourceStatuses: List<SourceStatus> = listOf(
        SourceStatus("timetable unavailable", SourceState.Unavailable),
        SourceStatus("seat unavailable", SourceState.Unavailable),
        SourceStatus("discount unavailable", SourceState.Unavailable),
    ),
)
