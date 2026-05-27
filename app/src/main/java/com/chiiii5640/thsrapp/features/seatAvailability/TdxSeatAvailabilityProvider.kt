package com.chiiii5640.thsrapp.features.seatAvailability

import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SeatAvailabilityDetail
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
        val key = query.cacheKey()
        val cached = cache[key]
        cooldownUntil?.takeIf { it.isAfter(now) }?.let {
            if (cached != null) {
                return SeatAvailabilityResult(
                    cached.values,
                    SourceStatus("seat availability fallback cache", SourceState.Cache),
                )
            }
            return SeatAvailabilityResult(emptyMap(), SourceStatus("seat API cooldown", SourceState.Unavailable))
        }

        val ttl = if (query.travelDate == LocalDate.now(clock)) Duration.ofSeconds(90) else Duration.ofSeconds(600)
        if (!query.forceRefresh && cached != null && cached.createdAt.plus(ttl).isAfter(now)) {
            return SeatAvailabilityResult(cached.values, SourceStatus("seat availability cache", SourceState.Cache))
        }

        val odResult = runCatching {
            api.odSeatStatus(query.travelDate, query.origin, query.destination, query.forceRefresh)
        }
            .onFailure { error -> recordCooldown(error, now) }
        val boardAvailableToday = query.travelDate == LocalDate.now(clock)
        val boardResult = if (boardAvailableToday) {
            runCatching {
                api.todaySeatBoard(query.origin, query.forceRefresh)
            }.onFailure { error -> recordCooldown(error, now) }
        } else {
            null
        }

        val odStatuses = odResult.getOrNull()
        val boardStatuses = boardResult?.getOrNull()
        if (odStatuses == null && boardStatuses == null) {
            if (cached != null) {
                return SeatAvailabilityResult(
                    cached.values,
                    SourceStatus("seat availability fallback cache", SourceState.Cache),
                )
            }
            val failure = odResult.exceptionOrNull() ?: boardResult?.exceptionOrNull()
            return SeatAvailabilityResult(
                emptyMap(),
                unavailableStatus("seat availability unavailable", failure),
            )
        }

        val odByTrainNo = odStatuses.orEmpty()
            .filter { it.trainNo in trainNos }
            .associateBy { it.trainNo }
        val boardByTrainNo = boardStatuses.orEmpty()
            .filter { it.trainNo in trainNos && it.stationId == query.destination.id }
            .associateBy { it.trainNo }
        val mapped = (odByTrainNo.keys + boardByTrainNo.keys)
            .associateWith { trainNo ->
                seatAvailabilityDetail(
                    od = odByTrainNo[trainNo],
                    board = boardByTrainNo[trainNo],
                )
            }
            .filterValues { it != null }
            .mapValues { (_, value) -> checkNotNull(value) }
        cache[key] = CacheEntry(now, mapped)
        return SeatAvailabilityResult(
            mapped,
            SourceStatus("TDX seat availability live", SourceState.Live),
        )
    }

    private fun recordCooldown(error: Throwable, now: Instant) {
        if (error.message?.contains("429") == true) {
            cooldownUntil = now.plusSeconds(30)
        }
    }

    private data class CacheEntry(
        val createdAt: Instant,
        val values: Map<String, SeatAvailabilityDetail>,
    )
}

private fun seatAvailabilityDetail(
    od: TdxSeatStatusItem?,
    board: TdxSeatStatusItem?,
): SeatAvailabilityDetail? {
    if (od == null && board == null) return null
    return SeatAvailabilityDetail(
        standardSeatStatus = seatStatusFromCode(od?.standardSeatStatus),
        businessSeatStatus = seatStatusFromCode(od?.businessSeatStatus),
        boardStandardSeatStatus = seatStatusFromCode(board?.standardSeatStatus),
        boardBusinessSeatStatus = seatStatusFromCode(board?.businessSeatStatus),
        hasBoardSeatStatus = board != null,
    )
}

internal fun seatStatusFromCode(rawStatus: String?): SeatStatus {
    val normalized = rawStatus?.trim()?.uppercase().orEmpty()
    if (normalized.isEmpty()) return SeatStatus.Unknown

    return when {
        normalized == "O" || normalized.contains("AVAILABLE") || normalized.contains("充足") -> SeatStatus.Available
        normalized == "L" || normalized.contains("LIMITED") || normalized.contains("有限") -> SeatStatus.Limited
        normalized == "X" || normalized.contains("SOLD") || normalized.contains("FULL") || normalized.contains("無") -> SeatStatus.SoldOut
        else -> SeatStatus.Unknown
    }
}

internal fun seatStatusFromCodes(vararg rawStatuses: String?): SeatStatus {
    return rawStatuses
        .map(::seatStatusFromCode)
        .maxByOrNull { status ->
            when (status) {
                SeatStatus.Unknown -> 0
                SeatStatus.SoldOut -> 1
                SeatStatus.Limited -> 2
                SeatStatus.Available -> 3
            }
        } ?: SeatStatus.Unknown
}
