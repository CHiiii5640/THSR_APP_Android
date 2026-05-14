package com.chiiii5640.thsrapp.features.searchDashboard

import com.chiiii5640.thsrapp.core.model.DiscountOffer
import com.chiiii5640.thsrapp.core.model.DiscountType
import com.chiiii5640.thsrapp.core.model.SeatAvailabilityDetail
import com.chiiii5640.thsrapp.core.model.SeatStatus
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.model.BookingStatus
import com.chiiii5640.thsrapp.features.discounts.DiscountProvider
import com.chiiii5640.thsrapp.features.discounts.DiscountResult
import com.chiiii5640.thsrapp.features.seatAvailability.SeatAvailabilityProvider
import com.chiiii5640.thsrapp.features.seatAvailability.SeatAvailabilityResult
import com.chiiii5640.thsrapp.features.timetable.FallbackTimetableProvider
import com.chiiii5640.thsrapp.features.timetable.FallbackTimetableResult
import com.chiiii5640.thsrapp.features.timetable.TimetableProvider
import com.chiiii5640.thsrapp.features.timetable.TimetableResult
import com.chiiii5640.thsrapp.features.timetable.TimetableTrain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SearchDashboardServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-12T04:00:00Z"), ZoneId.of("Asia/Taipei"))

    @Test
    fun searchDoesNotTruncateMoreThanFortyFiveResults() = runTest {
        val service = SearchDashboardService(
            timetableProvider = FakeTimetableProvider((0 until 46).map { index -> train(index) }),
            seatAvailabilityProvider = FakeSeatProvider(),
            discountProvider = FakeDiscountProvider(),
            clock = clock,
        )

        val result = service.search(query())

        assertEquals(46, result.options.size)
        assertEquals("1045", result.options.last().trainNo)
    }

    @Test
    fun supplyDateFallbackSkipsSeatApis() = runTest {
        val seatProvider = FakeSeatProvider()
        val service = SearchDashboardService(
            timetableProvider = FakeTimetableProvider(listOf(train(0)), usedFallback = true),
            seatAvailabilityProvider = seatProvider,
            discountProvider = FakeDiscountProvider(),
            clock = clock,
        )

        service.search(query())

        assertTrue(seatProvider.lastSkip == true)
    }

    @Test
    fun filtersDiscountedAndFastestResults() {
        val slow = trainOption(trainNo = "1", minutes = 90, discounted = false)
        val fastDiscounted = trainOption(trainNo = "2", minutes = 70, discounted = true)

        assertEquals(listOf(fastDiscounted), ResultFilter.Discounted.apply(listOf(slow, fastDiscounted)))
        assertEquals(listOf(fastDiscounted), ResultFilter.Fastest.apply(listOf(slow, fastDiscounted)))
    }

    @Test
    fun skipsSeatAndDiscountCallsWhenNoTimetableResultsRemain() = runTest {
        val seatProvider = FakeSeatProvider()
        val discountProvider = FakeDiscountProvider()
        val service = SearchDashboardService(
            timetableProvider = FakeTimetableProvider(listOf(train(0))),
            seatAvailabilityProvider = seatProvider,
            discountProvider = discountProvider,
            clock = clock,
        )

        val result = service.search(query().copy(departureAfter = LocalTime.of(23, 59)))

        assertTrue(result.options.isEmpty())
        assertTrue(seatProvider.called.not())
        assertTrue(discountProvider.called.not())
        assertEquals("seat skipped without timetable results", result.sourceStatuses[1].label)
    }

    @Test
    fun usesFallbackTimetableProviderWhenPrimaryTimetableIsEmpty() = runTest {
        val service = SearchDashboardService(
            timetableProvider = FakeTimetableProvider(emptyList()),
            seatAvailabilityProvider = FakeSeatProvider(),
            discountProvider = FakeDiscountProvider(),
            fallbackTimetableProvider = FakeFallbackTimetableProvider(listOf(train(1))),
            clock = clock,
        )

        val result = service.search(query().copy(departureAfter = LocalTime.MIDNIGHT))

        assertEquals(1, result.options.size)
        assertEquals("1001", result.options.first().trainNo)
        assertEquals("fallback", result.sourceStatuses.first().label)
    }

    @Test
    fun surfacesFallbackStatusWhenPrimaryAndFallbackAreEmpty() = runTest {
        val service = SearchDashboardService(
            timetableProvider = FakeTimetableProvider(emptyList()),
            seatAvailabilityProvider = FakeSeatProvider(),
            discountProvider = FakeDiscountProvider(),
            fallbackTimetableProvider = FakeFallbackTimetableProvider(
                trains = emptyList(),
                status = SourceStatus("discount feed timetable fallback empty", SourceState.Unavailable),
            ),
            clock = clock,
        )

        val result = service.search(query().copy(departureAfter = LocalTime.MIDNIGHT))

        assertTrue(result.options.isEmpty())
        assertEquals("discount feed timetable fallback empty", result.sourceStatuses.first().label)
    }

    @Test
    fun futureTripsReturnNotYetOpenWithEstimatedOpeningDate() = runTest {
        val service = SearchDashboardService(
            timetableProvider = FakeTimetableProvider(listOf(train(0))),
            seatAvailabilityProvider = FakeSeatProvider(),
            discountProvider = FakeDiscountProvider(),
            clock = clock,
        )

        val result = service.search(
            query().copy(
                travelDate = LocalDate.of(2026, 6, 10),
                departureAfter = LocalTime.MIDNIGHT,
            ),
        )

        val status = result.options.single().bookingStatus
        assertTrue(status is BookingStatus.NotYetOpen)
        assertEquals(LocalDate.of(2026, 5, 13), (status as BookingStatus.NotYetOpen).openingDate)
    }

    private fun query(): TripQuery = TripQuery(
        origin = Station.Taipei,
        destination = Station.Zuoying,
        travelDate = LocalDate.of(2026, 5, 12),
        departureAfter = LocalTime.of(6, 0),
    )

    private fun train(index: Int): TimetableTrain = TimetableTrain(
        trainNo = (1000 + index).toString(),
        departureTime = LocalTime.of(6, 0).plusMinutes(index.toLong()),
        arrivalTime = LocalTime.of(8, 0).plusMinutes(index.toLong()),
        stops = listOf(
            TimelineStop(Station.Taipei, null, LocalTime.of(6, 0).plusMinutes(index.toLong())),
            TimelineStop(Station.Zuoying, LocalTime.of(8, 0).plusMinutes(index.toLong()), null),
        ),
    )

    private fun trainOption(trainNo: String, minutes: Long, discounted: Boolean) =
        com.chiiii5640.thsrapp.core.model.TrainOption(
            trainNo = trainNo,
            origin = Station.Taipei,
            destination = Station.Zuoying,
            travelDate = LocalDate.of(2026, 5, 12),
            departureTime = LocalTime.of(9, 0),
            arrivalTime = LocalTime.of(9, 0).plusMinutes(minutes),
            stops = emptyList(),
            bookingStatus = com.chiiii5640.thsrapp.core.model.BookingStatus.Available,
            seatAvailability = SeatAvailabilityDetail(
                standardSeatStatus = SeatStatus.Available,
                businessSeatStatus = SeatStatus.Unknown,
            ),
            discounts = if (discounted) listOf(DiscountOffer(DiscountType.EarlyBird, "早鳥", 35)) else emptyList(),
            source = com.chiiii5640.thsrapp.core.model.TrainDataSource(
                SourceStatus("timetable", SourceState.Live),
                SourceStatus("seat", SourceState.Live),
                SourceStatus("discount", SourceState.Live),
            ),
        )
}

private class FakeTimetableProvider(
    private val trains: List<TimetableTrain>,
    private val usedFallback: Boolean = false,
) : TimetableProvider {
    override suspend fun trains(query: TripQuery): TimetableResult =
        TimetableResult(trains, SourceStatus("timetable", SourceState.Live), usedFallback)
}

private class FakeSeatProvider : SeatAvailabilityProvider {
    var lastSkip: Boolean? = null
    var called: Boolean = false

    override suspend fun seats(
        query: TripQuery,
        trainNos: List<String>,
        skip: Boolean,
    ): SeatAvailabilityResult {
        called = true
        lastSkip = skip
        return SeatAvailabilityResult(
            trainNos.associateWith {
                SeatAvailabilityDetail(
                    standardSeatStatus = SeatStatus.Available,
                    businessSeatStatus = SeatStatus.Unknown,
                )
            },
            SourceStatus("seat", SourceState.Live),
        )
    }
}

private class FakeDiscountProvider : DiscountProvider {
    var called: Boolean = false

    override suspend fun discounts(
        date: LocalDate,
        trains: List<TimetableTrain>,
        forceRefresh: Boolean,
    ): DiscountResult {
        called = true
        return DiscountResult(emptyMap(), SourceStatus("discount", SourceState.Live))
    }
}

private class FakeFallbackTimetableProvider(
    private val trains: List<TimetableTrain>,
    private val status: SourceStatus = SourceStatus("fallback", SourceState.Fallback),
) : FallbackTimetableProvider {
    override suspend fun trains(query: TripQuery, forceRefresh: Boolean): FallbackTimetableResult =
        FallbackTimetableResult(trains, status)
}
