package com.chiiii5640.thsrapp.core.model

import java.time.LocalDate
import java.time.LocalTime

data class TripQuery(
    val origin: Station = Station.Taipei,
    val destination: Station = Station.Zuoying,
    val travelDate: LocalDate,
    val departureAfter: LocalTime = LocalTime.now(),
    val forceRefresh: Boolean = false,
) {
    init {
        require(origin != destination) { "起站與迄站不能相同" }
    }

    fun cacheKey(): String = "${origin.id}-${destination.id}-$travelDate"
}
