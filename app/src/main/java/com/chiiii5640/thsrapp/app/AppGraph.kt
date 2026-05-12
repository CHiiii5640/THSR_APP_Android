package com.chiiii5640.thsrapp.app

import android.content.Context
import com.chiiii5640.thsrapp.BuildConfig
import com.chiiii5640.thsrapp.core.network.TdxApiClient
import com.chiiii5640.thsrapp.core.network.TdxAuthInterceptor
import com.chiiii5640.thsrapp.core.network.UrlConnectionHttpClient
import com.chiiii5640.thsrapp.core.persistence.PersistedGeneralTimetableStore
import com.chiiii5640.thsrapp.core.persistence.RoutePreferencesStore
import com.chiiii5640.thsrapp.core.time.ThsrClock
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationScheduler
import com.chiiii5640.thsrapp.features.discounts.FeedDiscountService
import com.chiiii5640.thsrapp.features.searchDashboard.SearchDashboardService
import com.chiiii5640.thsrapp.features.searchDashboard.SearchDashboardViewModel
import com.chiiii5640.thsrapp.features.seatAvailability.TdxSeatAvailabilityProvider
import com.chiiii5640.thsrapp.features.timetable.TdxTimetableProvider

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val clock = ThsrClock.system()
    private val httpClient = UrlConnectionHttpClient()
    private val auth = TdxAuthInterceptor(
        client = httpClient,
        clientId = BuildConfig.TDX_CLIENT_ID,
        clientSecret = BuildConfig.TDX_CLIENT_SECRET,
        clock = clock,
    )
    private val tdxApi = TdxApiClient(httpClient, auth)
    private val routePreferencesStore = RoutePreferencesStore(appContext)
    private val persistedGeneralTimetableStore = PersistedGeneralTimetableStore(appContext)
    private val bookingNotificationScheduler = BookingNotificationScheduler(appContext)

    private val searchDashboardService = SearchDashboardService(
        timetableProvider = TdxTimetableProvider(tdxApi, persistedGeneralTimetableStore),
        seatAvailabilityProvider = TdxSeatAvailabilityProvider(tdxApi, clock),
        discountProvider = FeedDiscountService(httpClient, BuildConfig.DISCOUNT_FEED_URL),
        clock = clock,
    )

    fun searchDashboardViewModel(): SearchDashboardViewModel =
        SearchDashboardViewModel(
            service = searchDashboardService,
            routePreferencesStore = routePreferencesStore,
            bookingNotificationScheduler = bookingNotificationScheduler,
            clock = clock,
        )
}
