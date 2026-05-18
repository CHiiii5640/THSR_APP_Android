package com.chiiii5640.thsrapp.features.timetable

import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TdxDailyTimetableItem
import com.chiiii5640.thsrapp.core.model.TdxGeneralTimetableItem
import com.chiiii5640.thsrapp.core.model.TdxGeneralTimetableRecord
import com.chiiii5640.thsrapp.core.model.TdxStopTime
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.network.TdxApiClient
import com.chiiii5640.thsrapp.core.network.unavailableStatus
import com.chiiii5640.thsrapp.core.persistence.PersistedGeneralTimetableStore
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.Clock

class TdxTimetableProvider(
    private val api: TdxApiClient,
    private val persistedStore: PersistedGeneralTimetableStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) : TimetableProvider {
    private var generalMemoryCache: List<TdxGeneralTimetableRecord>? = null
    private var generalCooldownUntil: Instant? = null

    override suspend fun trains(query: TripQuery): TimetableResult {
        val dailyResult = runCatching { api.dailyTimetable(query.travelDate, query.forceRefresh) }
        val daily = dailyResult.getOrNull().orEmpty()
        if (daily.isNotEmpty()) {
            return TimetableResult(
                trains = daily.mapNotNull { it.toTimetableTrain(query) },
                status = SourceStatus("TDX DailyTimetable live", SourceState.Live),
                usedSupplyDateFallback = false,
            )
        }

        val generalResult = runCatching { loadGeneral(query.forceRefresh) }
        val general = generalResult.getOrNull().orEmpty()
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
                unavailableStatus(
                    defaultLabel = "timetable unavailable",
                    throwable = generalResult.exceptionOrNull() ?: dailyResult.exceptionOrNull(),
                )
            } else {
                SourceStatus("persisted GeneralTimetable fallback", SourceState.Fallback)
            },
            usedSupplyDateFallback = persisted.isNotEmpty(),
        )
    }

    private suspend fun loadGeneral(forceRefresh: Boolean): List<TdxGeneralTimetableRecord> {
        val now = Instant.now(clock)
        val cooldownActive = generalCooldownUntil?.isAfter(now) == true

        if (!forceRefresh || cooldownActive) {
            generalMemoryCache?.let { return it }
            if (cooldownActive) {
                persistedStore.read().takeIf { it.isNotEmpty() }?.let { return it }
                return emptyList()
            }
        }

        val general = try {
            api.generalTimetable(forceRefresh)
        } catch (error: Throwable) {
            if (error.message?.contains("429") == true) {
                generalCooldownUntil = now.plus(Duration.ofSeconds(30))
                generalMemoryCache?.let { return it }
                persistedStore.read().takeIf { it.isNotEmpty() }?.let { return it }
            }
            throw error
        }

        generalCooldownUntil = null
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

private fun TdxGeneralTimetableRecord.toTimetableTrain(query: TripQuery): TimetableTrain? =
    if (generalTimetable.serviceDay?.runsOn(query.travelDate.dayOfWeek) == false) {
        null
    } else {
        generalTimetable.stopTimes.toTimetableTrain(
            trainNo = generalTimetable.generalTrainInfo.trainNo,
            origin = query.origin,
            destination = query.destination,
        )
    }

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
        stops = stops,
    )
}
