package com.chiiii5640.thsrapp.features.seatAvailability

import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.TdxSeatStatusItem
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.network.TdxApiClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class TdxSeatAvailabilityProvider(
    private val api: TdxApiClient,
    private val clock: Clock,
) : SeatAvailabilityProvider {
    private val cache = mutableMapOf<String, CacheEntry>()
    private var cooldownUntil: Instant? = null

    override suspend fun seats(
        query: TripQuery,
        trainNos: List<String>,
        skip: Boolean,
    ): SeatAvailabilityResult {
        if (skip) {
            return SeatAvailabilityResult(
                seatsByTrainNo = emptyMap(),
                status = SourceStatus("seat APIs skipped for fallback timetable", SourceState.Unavailable),
            )
        }

        val now = Instant.now(clock)
        cooldownUntil?.takeIf { it.isAfter(now) }?.let {
            return SeatAvailabilityResult(emptyMap(), SourceStatus("seat API cooldown", SourceState.Unavailable))
        }

        val key = query.cacheKey()
        val ttl = if (query.travelDate == LocalDate.now(clock)) Duration.ofSeconds(90) else Duration.ofSeconds(600)
        val cached = cache[key]
        if (!query.forceRefresh && cached != null && cached.createdAt.plus(ttl).isAfter(now)) {
            return SeatAvailabilityResult(cached.values, SourceStatus("seat availability cache", SourceState.Cache))
        }

        val fetched = runCatching {
            val od = api.odSeatStatus(query.travelDate, query.origin, query.destination, query.forceRefresh)
            if (query.travelDate == LocalDate.now(clock)) {
                od + api.todaySeatBoard(query.origin, query.forceRefresh)
            } else {
                od
            }
        }.onFailure { error ->
            if (error.message?.contains("429") == true) cooldownUntil = now.plusSeconds(30)
        }.getOrNull()

        if (fetched == null) {
            return SeatAvailabilityResult(emptyMap(), SourceStatus("seat availability unavailable", SourceState.Unavailable))
        }

        val mapped = fetched
            .filter { it.trainNo in trainNos }
            .associate { it.trainNo to it.toSeatStatus() }
        cache[key] = CacheEntry(now, mapped)
        return SeatAvailabilityResult(mapped, SourceStatus("TDX seat availability live", SourceState.Live))
    }

    private data class CacheEntry(
        val createdAt: Instant,
        val values: Map<String, SeatStatus>,
    )
}

private fun TdxSeatStatusItem.toSeatStatus(): SeatStatus {
    val combined = listOfNotNull(standardSeatStatus, businessSeatStatus).joinToString(" ")
    return when {
        combined.contains("充足") || combined.contains("Available", ignoreCase = true) -> SeatStatus.Available
        combined.contains("有限") || combined.contains("Limited", ignoreCase = true) -> SeatStatus.Limited
        combined.contains("無") || combined.contains("Sold", ignoreCase = true) -> SeatStatus.SoldOut
        else -> SeatStatus.Unknown
    }
}
