package com.chiiii5640.thsrapp.features.timetable

import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TdxDailyTimetableItem
import com.chiiii5640.thsrapp.core.model.TdxGeneralTimetableItem
import com.chiiii5640.thsrapp.core.model.TdxStopTime
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.network.TdxApiClient
import com.chiiii5640.thsrapp.core.persistence.PersistedGeneralTimetableStore
import java.time.LocalTime

class TdxTimetableProvider(
    private val api: TdxApiClient,
    private val persistedStore: PersistedGeneralTimetableStore,
) : TimetableProvider {
    private var generalMemoryCache: List<TdxGeneralTimetableItem>? = null

    override suspend fun trains(query: TripQuery): TimetableResult {
        val daily = runCatching { api.dailyTimetable(query.travelDate, query.forceRefresh) }.getOrNull().orEmpty()
        if (daily.isNotEmpty()) {
            return TimetableResult(
                trains = daily.mapNotNull { it.toTimetableTrain(query) },
                status = SourceStatus("TDX DailyTimetable live", SourceState.Live),
                usedSupplyDateFallback = false,
            )
        }

        val general = runCatching { loadGeneral(query.forceRefresh) }.getOrNull().orEmpty()
        if (general.isNotEmpty()) {
            return TimetableResult(
                trains = general.mapNotNull { it.toTimetableTrain(query) },
                status = SourceStatus("TDX GeneralTimetable fallback", SourceState.Fallback),
                usedSupplyDateFallback = true,
            )
        }

        val persisted = runCatching { persistedStore.read() }.getOrNull().orEmpty()
        return TimetableResult(
            trains = persisted.mapNotNull { it.toTimetableTrain(query) },
            status = if (persisted.isEmpty()) {
                SourceStatus("timetable unavailable", SourceState.Unavailable)
            } else {
                SourceStatus("persisted GeneralTimetable fallback", SourceState.Fallback)
            },
            usedSupplyDateFallback = persisted.isNotEmpty(),
        )
    }

    private suspend fun loadGeneral(forceRefresh: Boolean): List<TdxGeneralTimetableItem> {
        if (!forceRefresh) generalMemoryCache?.let { return it }
        val general = api.generalTimetable(forceRefresh)
        if (general.isNotEmpty()) {
            generalMemoryCache = general
            persistedStore.write(general)
        }
        return general
    }
}

private fun TdxDailyTimetableItem.toTimetableTrain(query: TripQuery): TimetableTrain? =
    stopTimes.toTimetableTrain(
        trainNo = dailyTrainInfo.trainNo,
        origin = query.origin,
        destination = query.destination,
    )

private fun TdxGeneralTimetableItem.toTimetableTrain(query: TripQuery): TimetableTrain? =
    stopTimes.toTimetableTrain(
        trainNo = generalTrainInfo.trainNo,
        origin = query.origin,
        destination = query.destination,
    )

private fun List<TdxStopTime>.toTimetableTrain(
    trainNo: String,
    origin: Station,
    destination: Station,
): TimetableTrain? {
    val stops = mapNotNull { stop ->
        val station = Station.fromId(stop.stationId) ?: return@mapNotNull null
        TimelineStop(
            station = station,
            arrivalTime = stop.arrivalTime?.let(LocalTime::parse),
            departureTime = stop.departureTime?.let(LocalTime::parse),
        )
    }
    val originIndex = stops.indexOfFirst { it.station == origin }
    val destinationIndex = stops.indexOfFirst { it.station == destination }
    if (originIndex < 0 || destinationIndex < 0 || originIndex >= destinationIndex) return null
    val originStop = stops[originIndex]
    val destinationStop = stops[destinationIndex]
    return TimetableTrain(
        trainNo = trainNo,
        departureTime = originStop.departureTime ?: originStop.arrivalTime ?: return null,
        arrivalTime = destinationStop.arrivalTime ?: destinationStop.departureTime ?: return null,
        stops = stops.subList(originIndex, destinationIndex + 1),
    )
}
