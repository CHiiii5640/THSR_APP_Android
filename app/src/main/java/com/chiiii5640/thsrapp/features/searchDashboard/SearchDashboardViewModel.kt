package com.chiiii5640.thsrapp.features.searchDashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.persistence.RoutePreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

class SearchDashboardViewModel(
    service: SearchDashboardService,
    private val routePreferencesStore: RoutePreferencesStore,
    private val clock: Clock,
) : ViewModel() {
    private val initialRoute = routePreferencesStore.load()
    private val coordinator = SearchCoordinator(
        scope = viewModelScope,
        service = service,
        onState = { state -> _uiState.update { it.copy(loadState = state) } },
    )

    private val _uiState = MutableStateFlow(
        SearchDashboardUiState(
            origin = initialRoute.first,
            destination = initialRoute.second,
            travelDate = LocalDate.now(clock),
            departureAfter = LocalTime.now(clock).withSecond(0).withNano(0),
        ),
    )
    val uiState: StateFlow<SearchDashboardUiState> = _uiState.asStateFlow()

    init {
        submit(forceRefresh = false)
    }

    fun setOrigin(station: Station) = updateRoute(origin = station, destination = _uiState.value.destination)
    fun setDestination(station: Station) = updateRoute(origin = _uiState.value.origin, destination = station)
    fun swapRoute() = updateRoute(origin = _uiState.value.destination, destination = _uiState.value.origin)
    fun setTravelDate(date: LocalDate) = updateAndSearch { copy(travelDate = date) }
    fun setDepartureAfter(time: LocalTime) = updateAndSearch { copy(departureAfter = time) }
    fun setFilter(filter: ResultFilter) = _uiState.update { it.copy(selectedFilter = filter) }
    fun forceRefresh() = submit(forceRefresh = true)

    private fun updateRoute(origin: Station, destination: Station) {
        if (origin == destination) return
        _uiState.update { it.copy(origin = origin, destination = destination) }
        viewModelScope.launch { routePreferencesStore.save(origin, destination) }
        submit(forceRefresh = false)
    }

    private fun updateAndSearch(reducer: SearchDashboardUiState.() -> SearchDashboardUiState) {
        _uiState.update(reducer)
        submit(forceRefresh = false)
    }

    private fun submit(forceRefresh: Boolean) {
        val state = _uiState.value
        val query = TripQuery(
            origin = state.origin,
            destination = state.destination,
            travelDate = state.travelDate,
            departureAfter = state.departureAfter,
            forceRefresh = forceRefresh,
        )
        coordinator.search(query)
    }
}

data class SearchDashboardUiState(
    val origin: Station,
    val destination: Station,
    val travelDate: LocalDate,
    val departureAfter: LocalTime,
    val selectedFilter: ResultFilter = ResultFilter.All,
    val loadState: SearchLoadState = SearchLoadState.Idle,
)
