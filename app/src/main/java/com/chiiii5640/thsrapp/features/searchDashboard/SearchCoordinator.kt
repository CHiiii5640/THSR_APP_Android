package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.TripQuery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class SearchCoordinator(
    private val scope: CoroutineScope,
    private val service: SearchDashboardService,
    private val onState: (SearchLoadState) -> Unit,
) {
    private var pendingJob: Job? = null
    private var lastCompletedRequest: TripQuery? = null

    fun search(query: TripQuery) {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            if (!query.forceRefresh) {
                delay(350)
                if (query == lastCompletedRequest) return@launch
            }
            onState(SearchLoadState.Loading)
            runCatching { service.search(query) }
                .onSuccess {
                    lastCompletedRequest = query.copy(forceRefresh = false)
                    onState(SearchLoadState.Loaded(it))
                }
                .onFailure { onState(SearchLoadState.Failed(it.message ?: "查詢失敗")) }
        }
    }
}

sealed interface SearchLoadState {
    data object Idle : SearchLoadState
    data object Loading : SearchLoadState
    data class Loaded(val result: SearchResult) : SearchLoadState
    data class Failed(val message: String) : SearchLoadState
}
