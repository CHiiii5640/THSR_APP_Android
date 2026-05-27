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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SearchDashboardService(
    private val timetableProvider: TimetableProvider,
    private val seatAvailabilityProvider: SeatAvailabilityProvider,
    private val discountProvider: DiscountProvider,
    private val fallbackTimetableProvider: FallbackTimetableProvider? = null,
    private val clock: Clock,
    private val bookingWindowStatusProvider: BookingWindowStatusProvider? = null,
) {
    private val bookingWindowCalculator = BookingWindowCalculator(clock)
    private val aboutToDepartWindow = Duration.ofMinutes(2)
    private val approachingWindow = Duration.ofSeconds(90)
    private val arrivingWindow = Duration.ofSeconds(30)
    private val departureBlendWindow = Duration.ofSeconds(12)

    suspend fun search(query: TripQuery): SearchResult {
        val initialLatestBookableDate = bookingWindowStatusProvider?.actualLatestBookableDate(query.forceRefresh)
        val primaryTimetable = timetableProvider.trains(query)
        val actualLatestBookableDate = bookingWindowStatusProvider?.actualLatestBookableDate(forceRefresh = false)
            ?: initialLatestBookableDate
        val fallbackTimetable = if (primaryTimetable.trains.isEmpty() && primaryTimetable.allowsFeedFallback) {
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
                actualLatestBookableDate = actualLatestBookableDate,
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
                actualLatestBookableDate = actualLatestBookableDate,
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
                ),
            )
        }

        return SearchResult(
            options = options,
            sourceStatuses = listOf(timetable.status, seats.status, discounts.status),
            actualLatestBookableDate = actualLatestBookableDate,
        )
    }

    private fun deriveLiveStatus(
        train: TimetableTrain,
        travelDate: LocalDate,
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

        if (now.isBefore(firstDeparture.minus(aboutToDepartWindow))) {
            return TrainLiveStatus(
                serviceState = TrainServiceState.NotDeparted,
                summary = TimelineStatusSummary(
                    headline = "待命",
                    detail = "${firstStation.station.localName} ${train.departureTime}",
                    currentStopIndex = firstStation.index,
                    nextStopIndex = firstStation.index,
                    activeSegmentIndex = null,
                ),
            )
        }

        if (now.isBefore(firstDeparture)) {
            return TrainLiveStatus(
                serviceState = TrainServiceState.AboutToDepart,
                summary = TimelineStatusSummary(
                    headline = "準備發車",
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
                val remainingSeconds = Duration.between(now, departure).seconds.coerceAtLeast(0)
                return TrainLiveStatus(
                    serviceState = TrainServiceState.DwellingAtStation,
                    summary = TimelineStatusSummary(
                        headline = "停靠中",
                        detail = if (remainingSeconds > 0) {
                            "${stop.station.localName} 剩餘 ${remainingSeconds}s"
                        } else {
                            stop.station.localName
                        },
                        currentStopIndex = stop.index,
                        nextStopIndex = stop.index,
                        activeSegmentIndex = (index - 1).takeIf { it >= 0 },
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

            val segmentDuration = Duration.between(departure, arrival)
            val departureWindow = cappedSegmentWindow(segmentDuration, fraction = 0.20, cap = departureBlendWindow)
            val timeToArrival = Duration.between(now, arrival)
            val arrivalLead = cappedSegmentWindow(segmentDuration, fraction = 0.28, cap = arrivingWindow)
            val approachLead = cappedSegmentWindow(segmentDuration, fraction = 0.28, cap = approachingWindow)
            val nextDisplayTime = (to.departure ?: to.arrival)?.toLocalTime() ?: train.arrivalTime
            val serviceState = when {
                now.isBefore(departure.plus(departureWindow)) -> TrainServiceState.Departing
                timeToArrival <= arrivalLead -> TrainServiceState.Arriving
                timeToArrival <= approachLead -> TrainServiceState.Approaching
                else -> TrainServiceState.InTransit
            }
            return TrainLiveStatus(
                serviceState = serviceState,
                summary = TimelineStatusSummary(
                    headline = when (serviceState) {
                        TrainServiceState.Departing -> "離站中"
                        TrainServiceState.Arriving -> "進站中"
                        TrainServiceState.Approaching -> "接近"
                        else -> "行進中"
                    },
                    detail = when (serviceState) {
                        TrainServiceState.Departing -> "${from.station.localName} 開出"
                        TrainServiceState.Approaching,
                        TrainServiceState.Arriving -> "${to.station.localName} $nextDisplayTime"
                        else -> "下一站 ${to.station.localName} $nextDisplayTime"
                    },
                    currentStopIndex = from.index,
                    nextStopIndex = to.index,
                    activeSegmentIndex = index,
                ),
            )
        }

        if (now.isAfter(lastArrival.minus(approachingWindow)) && now.isBefore(lastArrival)) {
            return TrainLiveStatus(
                serviceState = if (Duration.between(now, lastArrival) <= arrivingWindow) {
                    TrainServiceState.Arriving
                } else {
                    TrainServiceState.Approaching
                },
                summary = TimelineStatusSummary(
                    headline = if (Duration.between(now, lastArrival) <= arrivingWindow) "進站中" else "接近",
                    detail = "${lastStation.station.localName} ${train.arrivalTime}",
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
                    headline = "已抵達",
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

    private fun cappedSegmentWindow(
        segmentDuration: Duration,
        fraction: Double,
        cap: Duration,
    ): Duration {
        val windowMillis = (segmentDuration.toMillis().coerceAtLeast(0L) * fraction).toLong()
        return minOf(cap, Duration.ofMillis(windowMillis.coerceAtLeast(0L)))
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
    val actualLatestBookableDate: LocalDate? = null,
)
