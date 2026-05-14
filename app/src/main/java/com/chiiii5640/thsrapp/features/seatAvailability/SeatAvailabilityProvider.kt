package com.chiiii5640.thsrapp.features.seatAvailability

import com.chiiii5640.thsrapp.core.model.SeatAvailabilityDetail
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.TripQuery

interface SeatAvailabilityProvider {
    suspend fun seats(query: TripQuery, trainNos: List<String>, skip: Boolean): SeatAvailabilityResult
}

data class SeatAvailabilityResult(
    val seatsByTrainNo: Map<String, SeatAvailabilityDetail>,
    val status: SourceStatus,
)
