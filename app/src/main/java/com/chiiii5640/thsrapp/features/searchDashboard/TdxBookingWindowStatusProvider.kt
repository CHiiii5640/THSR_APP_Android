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
    suspend fun refreshTrainDateSupplyOnAppOpenIfNeeded(): LocalDate? = actualLatestBookableDate(forceRefresh = false)
    suspend fun lastTrainDateSupplyUpdatedAt(forceRefresh: Boolean): Instant? = null
}

class TdxBookingWindowStatusProvider(
    private val api: TdxApiClient,
    private val persistedStore: PersistedTrainDateSupplyStore,
    private val clock: Clock,
) : BookingWindowStatusProvider {
    private val lock = Mutex()
    private var memorySnapshot: PersistedTrainDateSupplySnapshot? = null
    private var autoRefreshAttemptedDay: LocalDate? = null

    override suspend fun actualLatestBookableDate(forceRefresh: Boolean): LocalDate? {
        return resolvedSupply(forceRefresh)?.latestBookableDate()
    }

    override suspend fun refreshTrainDateSupplyOnAppOpenIfNeeded(): LocalDate? {
        val today = LocalDate.now(clock)
        val (shouldRefresh, latestBookableDate) = lock.withLock {
            val latestSnapshot = latestKnownSnapshotLocked()
            if (latestSnapshot != null && isCurrentSnapshot(latestSnapshot)) {
                memorySnapshot = latestSnapshot
                return@withLock false to latestSnapshot.supply.latestBookableDate()
            }
            if (autoRefreshAttemptedDay == today) {
                return@withLock false to latestSnapshot?.supply?.latestBookableDate()
            }
            autoRefreshAttemptedDay = today
            true to null
        }

        if (!shouldRefresh) return latestBookableDate
        return actualLatestBookableDate(forceRefresh = true)
            ?: lock.withLock { latestKnownSnapshotLocked()?.supply?.latestBookableDate() }
    }

    override suspend fun lastTrainDateSupplyUpdatedAt(forceRefresh: Boolean): Instant? {
        if (forceRefresh) {
            val refreshedSupply = resolvedSupply(forceRefresh = true) ?: return null
            return lock.withLock {
                latestKnownSnapshotLocked()
                    ?.takeIf { it.supply == refreshedSupply }
                    ?.let { Instant.ofEpochMilli(it.savedAtEpochMillis) }
            }
        }

        resolvedSupply(forceRefresh = false)
        return lock.withLock {
            latestKnownSnapshotLocked()?.let { Instant.ofEpochMilli(it.savedAtEpochMillis) }
        }
    }

    suspend fun confirmAvailableDate(
        confirmedDate: LocalDate,
        updateTime: String?,
    ) = lock.withLock {
        val currentSnapshot = latestKnownSnapshotLocked() ?: return@withLock
        val currentSupply = currentSnapshot.supply
        val currentLatestBookableDate = currentSupply.latestBookableDate() ?: return@withLock
        if (!currentLatestBookableDate.isBefore(confirmedDate)) return@withLock

        val updatedSupply = currentSupply.mergedConfirmedAvailableDate(
            confirmedDate = confirmedDate,
            updateTime = updateTime,
        )
        val updatedSnapshot = PersistedTrainDateSupplySnapshot(
            savedAtEpochMillis = Instant.now(clock).toEpochMilli(),
            supply = updatedSupply,
        )
        memorySnapshot = updatedSnapshot
        runCatching {
            persistedStore.write(updatedSnapshot)
        }
    }

    private suspend fun resolvedSupply(forceRefresh: Boolean): TdxTrainDateSupply? = lock.withLock {
        val today = LocalDate.now(clock)
        if (!forceRefresh) {
            latestKnownSnapshotLocked()
                ?.takeIf(::isCurrentSnapshot)
                ?.also { memorySnapshot = it }
                ?.let { return@withLock it.supply }

            if (autoRefreshAttemptedDay == today) {
                return@withLock latestKnownSnapshotLocked()?.supply
            }
        }

        val supply = runCatching { api.trainDateSupply(forceRefresh) }.getOrNull() ?: return@withLock null
        val snapshot = PersistedTrainDateSupplySnapshot(
            savedAtEpochMillis = Instant.now(clock).toEpochMilli(),
            supply = supply,
        )
        memorySnapshot = snapshot
        runCatching {
            persistedStore.write(snapshot)
        }
        supply
    }

    private suspend fun latestKnownSnapshotLocked(): PersistedTrainDateSupplySnapshot? {
        memorySnapshot?.let { return it }
        return persistedStore.read()?.also { memorySnapshot = it }
    }

    private fun isCurrentSnapshot(snapshot: PersistedTrainDateSupplySnapshot): Boolean {
        val savedAt = Instant.ofEpochMilli(snapshot.savedAtEpochMillis).atZone(clock.zone).toLocalDate()
        val today = LocalDate.now(clock)
        return savedAt == today
    }
}
