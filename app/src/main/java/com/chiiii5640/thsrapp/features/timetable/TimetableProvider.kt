package com.chiiii5640.thsrapp.features.timetable

import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TripQuery
import java.time.LocalTime

interface TimetableProvider {
    suspend fun trains(query: TripQuery): TimetableResult
}

data class TimetableResult(
    val trains: List<TimetableTrain>,
    val status: SourceStatus,
    val usedSupplyDateFallback: Boolean,
    val allowsFeedFallback: Boolean = false,
)

data class TimetableTrain(
    val trainNo: String,
    val departureTime: LocalTime,
    val arrivalTime: LocalTime,
    val stops: List<TimelineStop>,
)
