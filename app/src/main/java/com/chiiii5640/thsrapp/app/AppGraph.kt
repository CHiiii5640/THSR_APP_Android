package com.chiiii5640.thsrapp.app

import android.content.Context
import com.chiiii5640.thsrapp.BuildConfig
import com.chiiii5640.thsrapp.core.network.TdxApiClient
import com.chiiii5640.thsrapp.core.network.TdxAuthInterceptor
import com.chiiii5640.thsrapp.core.network.UrlConnectionHttpClient
import com.chiiii5640.thsrapp.core.persistence.PersistedGeneralTimetableStore
import com.chiiii5640.thsrapp.core.persistence.PersistedTrainDateSupplyStore
import com.chiiii5640.thsrapp.core.persistence.RoutePreferencesStore
import com.chiiii5640.thsrapp.core.time.ThsrClock
import com.chiiii5640.thsrapp.features.bookingNotifications.BookingNotificationScheduler
import com.chiiii5640.thsrapp.features.discounts.FeedDiscountService
import com.chiiii5640.thsrapp.features.searchDashboard.SearchDashboardService
import com.chiiii5640.thsrapp.features.searchDashboard.SearchDashboardViewModel
import com.chiiii5640.thsrapp.features.searchDashboard.TdxBookingWindowStatusProvider
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
    private val persistedTrainDateSupplyStore = PersistedTrainDateSupplyStore(appContext)
    private val bookingNotificationScheduler = BookingNotificationScheduler(appContext)
    private val feedDiscountService = FeedDiscountService(httpClient, BuildConfig.DISCOUNT_FEED_URL)
    private val bookingWindowStatusProvider = TdxBookingWindowStatusProvider(
        api = tdxApi,
        persistedStore = persistedTrainDateSupplyStore,
        clock = clock,
    )

    private val searchDashboardService = SearchDashboardService(
        timetableProvider = TdxTimetableProvider(
            api = tdxApi,
            persistedStore = persistedGeneralTimetableStore,
            clock = clock,
            onDailyTimetableConfirmedAvailableDate = bookingWindowStatusProvider::confirmAvailableDate,
        ),
        seatAvailabilityProvider = TdxSeatAvailabilityProvider(tdxApi, clock),
        discountProvider = feedDiscountService,
        fallbackTimetableProvider = feedDiscountService,
        clock = clock,
        bookingWindowStatusProvider = bookingWindowStatusProvider,
    )

    fun searchDashboardViewModel(): SearchDashboardViewModel =
        SearchDashboardViewModel(
            service = searchDashboardService,
            bookingWindowStatusProvider = bookingWindowStatusProvider,
            routePreferencesStore = routePreferencesStore,
            bookingNotificationScheduler = bookingNotificationScheduler,
            clock = clock,
        )
}
