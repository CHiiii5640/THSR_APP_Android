package com.chiiii5640.thsrapp.features.searchDashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiiii5640.thsrapp.core.logging.ThsrLog
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.persistence.RoutePreferencesStore
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationScheduler
import com.chiiii5640.thsrapp.features.bookingNotifications.ScheduledBookingNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SearchDashboardViewModel(
    service: SearchDashboardService,
    private val routePreferencesStore: RoutePreferencesStore,
    private val bookingNotificationScheduler: BookingNotificationScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val initialRoute = routePreferencesStore.load()
    private val coordinator = SearchCoordinator(
        scope = viewModelScope,
        service = service,
        onState = { state ->
            _uiState.update {
                when (state) {
                    is SearchLoadState.Loaded -> it.copy(
                        loadState = state,
                        actualLatestBookableDate = state.result.actualLatestBookableDate,
                    )
                    else -> it.copy(loadState = state)
                }
            }
        },
    )

    private val _uiState = MutableStateFlow(
        SearchDashboardUiState(
            origin = initialRoute.first,
            destination = initialRoute.second,
            travelDate = LocalDate.now(clock),
            departureAfter = defaultDepartureAfter(LocalDate.now(clock), clock),
            scheduledNotifications = bookingNotificationScheduler.scheduledNotifications(),
        ),
    )
    val uiState: StateFlow<SearchDashboardUiState> = _uiState.asStateFlow()

    init {
        submit(forceRefresh = false)
    }

    fun setOrigin(station: Station) = updateRoute(origin = station, destination = _uiState.value.destination)
    fun setDestination(station: Station) = updateRoute(origin = _uiState.value.origin, destination = station)
    fun swapRoute() = updateRoute(origin = _uiState.value.destination, destination = _uiState.value.origin)
    fun setTravelDate(date: LocalDate) = updateAndSearch {
        copy(
            travelDate = date,
            departureAfter = if (isDepartureTimeUserSelected) {
                departureAfter
            } else {
                defaultDepartureAfter(date, clock)
            },
        )
    }
    fun setDepartureAfter(time: LocalTime) = updateAndSearch {
        copy(
            departureAfter = time,
            isDepartureTimeUserSelected = true,
        )
    }
    fun setFilter(filter: ResultFilter) = _uiState.update { it.copy(selectedFilter = filter) }
    fun setFastestDuration(duration: Long) = _uiState.update { it.copy(selectedFastestDuration = duration) }
    fun setShowingScheduledNotifications(showing: Boolean) = _uiState.update { it.copy(showingScheduledNotifications = showing) }
    fun search() = submit(forceRefresh = false)
    fun forceRefresh() = submit(forceRefresh = true)
    fun scheduleNotification(option: TrainOption, reminderAt: LocalDateTime) {
        bookingNotificationScheduler.schedule(option, reminderAt)
        refreshScheduledNotifications()
    }

    fun cancelNotification(id: String) {
        bookingNotificationScheduler.cancel(id)
        refreshScheduledNotifications()
    }

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
        ThsrLog.i(
            "submit query ${query.origin.localName}-${query.destination.localName} ${query.travelDate} ${query.departureAfter} forceRefresh=${query.forceRefresh}",
        )
        coordinator.search(query)
    }

    private fun refreshScheduledNotifications() {
        _uiState.update {
            it.copy(scheduledNotifications = bookingNotificationScheduler.scheduledNotifications())
        }
    }
}

data class SearchDashboardUiState(
    val origin: Station,
    val destination: Station,
    val travelDate: LocalDate,
    val departureAfter: LocalTime,
    val selectedFilter: ResultFilter = ResultFilter.All,
    val selectedFastestDuration: Long? = null,
    val loadState: SearchLoadState = SearchLoadState.Idle,
    val showingScheduledNotifications: Boolean = false,
    val scheduledNotifications: List<ScheduledBookingNotification> = emptyList(),
    val isDepartureTimeUserSelected: Boolean = false,
    val actualLatestBookableDate: LocalDate? = null,
)

internal fun defaultDepartureAfter(travelDate: LocalDate, clock: Clock): LocalTime =
    if (travelDate == LocalDate.now(clock)) {
        LocalTime.now(clock).withSecond(0).withNano(0)
    } else {
        LocalTime.MIDNIGHT
    }
