package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.TdxTrainDateSupply
import com.chiiii5640.thsrapp.core.network.TdxApiClient
import com.chiiii5640.thsrapp.core.persistence.PersistedTrainDateSupplySnapshot
import com.chiiii5640.thsrapp.core.persistence.PersistedTrainDateSupplyStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface BookingWindowStatusProvider {
    suspend fun actualLatestBookableDate(forceRefresh: Boolean): LocalDate?
}

class TdxBookingWindowStatusProvider(
    private val api: TdxApiClient,
    private val persistedStore: PersistedTrainDateSupplyStore,
    private val clock: Clock,
) : BookingWindowStatusProvider {
    private val lock = Mutex()
    private var memoryCache: TdxTrainDateSupply? = null

    override suspend fun actualLatestBookableDate(forceRefresh: Boolean): LocalDate? {
        return resolvedSupply(forceRefresh)?.latestBookableDate()
    }

    private suspend fun resolvedSupply(forceRefresh: Boolean): TdxTrainDateSupply? = lock.withLock {
        if (!forceRefresh) {
            memoryCache?.let { return@withLock it }
            persistedStore.read()
                ?.takeIf(::isCurrentSnapshot)
                ?.supply
                ?.also { memoryCache = it }
                ?.let { return@withLock it }
        }

        val supply = runCatching { api.trainDateSupply(forceRefresh) }.getOrNull() ?: return@withLock null
        memoryCache = supply
        runCatching {
            persistedStore.write(
                PersistedTrainDateSupplySnapshot(
                    savedAtEpochMillis = Instant.now(clock).toEpochMilli(),
                    supply = supply,
                ),
            )
        }
        supply
    }

    private fun isCurrentSnapshot(snapshot: PersistedTrainDateSupplySnapshot): Boolean {
        val savedAt = Instant.ofEpochMilli(snapshot.savedAtEpochMillis).atZone(clock.zone).toLocalDate()
        val today = LocalDate.now(clock)
        return savedAt == today
    }
}
