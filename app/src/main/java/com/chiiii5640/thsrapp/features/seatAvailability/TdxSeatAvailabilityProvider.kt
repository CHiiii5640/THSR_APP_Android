package com.chiiii5640.thsrapp.features.seatAvailability

import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.TdxSeatStatusItem
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.network.TdxApiClient
import com.chiiii5640.thsrapp.core.network.unavailableStatus
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

        val fetchedResult = runCatching {
            val od = api.odSeatStatus(query.travelDate, query.origin, query.destination, query.forceRefresh)
            if (query.travelDate == LocalDate.now(clock)) {
                od + api.todaySeatBoard(query.origin, query.forceRefresh)
            } else {
                od
            }
        }.onFailure { error ->
            if (error.message?.contains("429") == true) cooldownUntil = now.plusSeconds(30)
        }
        val fetched = fetchedResult.getOrNull()

        if (fetched == null) {
            return SeatAvailabilityResult(
                emptyMap(),
                unavailableStatus("seat availability unavailable", fetchedResult.exceptionOrNull()),
            )
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
    return seatStatusFromCodes(standardSeatStatus, businessSeatStatus)
}

internal fun seatStatusFromCodes(vararg rawStatuses: String?): SeatStatus {
    val normalized = rawStatuses
        .mapNotNull { it?.trim()?.uppercase() }
        .filter { it.isNotEmpty() }

    return when {
        normalized.any { it == "O" || it.contains("AVAILABLE") || it.contains("充足") } -> SeatStatus.Available
        normalized.any { it == "L" || it.contains("LIMITED") || it.contains("有限") } -> SeatStatus.Limited
        normalized.any { it == "X" || it.contains("SOLD") || it.contains("FULL") || it.contains("無") } -> SeatStatus.SoldOut
        else -> SeatStatus.Unknown
    }
}
