package com.chiiii5640.thsrapp.features.timetable

import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.TripQuery

interface FallbackTimetableProvider {
    suspend fun trains(query: TripQuery, forceRefresh: Boolean): FallbackTimetableResult
}

data class FallbackTimetableResult(
    val trains: List<TimetableTrain>,
    val status: SourceStatus,
)
