package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.logging.ThsrLog
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
            ThsrLog.i(
                "coordinator received query ${query.origin.localName}-${query.destination.localName} ${query.travelDate} ${query.departureAfter} forceRefresh=${query.forceRefresh}",
            )
            if (!query.forceRefresh) {
                delay(350)
                if (query == lastCompletedRequest) {
                    ThsrLog.i("coordinator skipped duplicate query")
                    return@launch
                }
            }
            onState(SearchLoadState.Loading)
            ThsrLog.i("coordinator loading query")
            runCatching { service.search(query) }
                .onSuccess {
                    lastCompletedRequest = query.copy(forceRefresh = false)
                    ThsrLog.i("coordinator loaded ${it.options.size} options")
                    onState(SearchLoadState.Loaded(it))
                }
                .onFailure {
                    ThsrLog.e("coordinator failed query", it)
                    onState(SearchLoadState.Failed(it.message ?: "查詢失敗"))
                }
        }
    }
}

sealed interface SearchLoadState {
    data object Idle : SearchLoadState
    data object Loading : SearchLoadState
    data class Loaded(val result: SearchResult) : SearchLoadState
    data class Failed(val message: String) : SearchLoadState
}
