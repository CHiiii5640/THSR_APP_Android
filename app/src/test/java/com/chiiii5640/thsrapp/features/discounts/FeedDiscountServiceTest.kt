package com.chiiii5640.thsrapp.features.discounts

import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.network.HttpClient
import com.chiiii5640.thsrapp.core.network.HttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FeedDiscountServiceTest {
    @Test
    fun timetableFallbackBuildsSouthboundTrainsFromScheduleRules() = runTest {
        val service = FeedDiscountService(
            client = FakeHttpClient(feedBody = """
                {
                  "schedule_rules": [
                    {
                      "train_number": "0160",
                      "direction": "南下",
                      "valid_from": "2026-02-02",
                      "stops": {
                        "台北": "06:11",
                        "台中": "06:58",
                        "左營": "07:47"
                      }
                    }
                  ]
                }
            """.trimIndent()),
            feedUrl = "https://example.com/discounts.json",
        )

        val result = service.trains(
            query = TripQuery(
                origin = Station.Taipei,
                destination = Station.Zuoying,
                travelDate = LocalDate.of(2026, 6, 17),
                departureAfter = LocalTime.MIDNIGHT,
            ),
            forceRefresh = true,
        )

        assertEquals(SourceState.Fallback, result.status.state)
        assertEquals(1, result.trains.size)
        assertEquals("0160", result.trains.first().trainNo)
    }

    @Test
    fun openEndedScheduleRulesStillMatchTargetDate() = runTest {
        val service = FeedDiscountService(
            client = FakeHttpClient(feedBody = """
                {
                  "schedule_rules": [
                    {
                      "train_number": "0300",
                      "direction": "北上",
                      "stops": {
                        "左營": "05:50",
                        "台中": "06:56",
                        "台北": "07:47"
                      }
                    }
                  ]
                }
            """.trimIndent()),
            feedUrl = "https://example.com/discounts.json",
        )

        val result = service.trains(
            query = TripQuery(
                origin = Station.Zuoying,
                destination = Station.Taipei,
                travelDate = LocalDate.of(2026, 6, 17),
                departureAfter = LocalTime.MIDNIGHT,
            ),
            forceRefresh = true,
        )

        assertTrue(result.trains.isNotEmpty())
        assertEquals("0300", result.trains.first().trainNo)
    }

    @Test
    fun officialSpecialScheduleRulesOverrideRegularOnMatchingDates() = runTest {
        val service = FeedDiscountService(
            client = FakeHttpClient(feedBody = """
                {
                  "schedule_rules": [
                    {
                      "train_number": "1103",
                      "direction": "南下",
                      "valid_from": "2026-02-02",
                      "timetable_kind": "official_regular",
                      "stops": {
                        "南港": "06:00",
                        "台北": "06:11",
                        "左營": "08:25"
                      }
                    },
                    {
                      "train_number": "1103",
                      "direction": "南下",
                      "dates": ["2026-06-01", "2026-06-06"],
                      "timetable_kind": "official_special",
                      "stops": {
                        "南港": "06:00",
                        "台北": "06:11",
                        "左營": "08:25"
                      }
                    }
                  ],
                  "rules": [
                    {
                      "kind": "student",
                      "train_number": "1103",
                      "direction": "南下",
                      "departure_time": "06:00",
                      "dates": ["2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05"],
                      "offer": { "kind": "not_running" },
                      "priority": 100
                    }
                  ]
                }
            """.trimIndent()),
            feedUrl = "https://example.com/discounts.json",
        )

        val specialDayResult = service.trains(
            query = TripQuery(
                origin = Station.Taipei,
                destination = Station.Zuoying,
                travelDate = LocalDate.of(2026, 6, 1),
                departureAfter = LocalTime.MIDNIGHT,
            ),
            forceRefresh = true,
        )
        val regularWeekdayResult = service.trains(
            query = TripQuery(
                origin = Station.Taipei,
                destination = Station.Zuoying,
                travelDate = LocalDate.of(2026, 6, 2),
                departureAfter = LocalTime.MIDNIGHT,
            ),
            forceRefresh = true,
        )
        val saturdayResult = service.trains(
            query = TripQuery(
                origin = Station.Taipei,
                destination = Station.Zuoying,
                travelDate = LocalDate.of(2026, 6, 6),
                departureAfter = LocalTime.MIDNIGHT,
            ),
            forceRefresh = true,
        )

        assertEquals(listOf("1103"), specialDayResult.trains.map { it.trainNo })
        assertTrue(regularWeekdayResult.trains.isEmpty())
        assertEquals(listOf("1103"), saturdayResult.trains.map { it.trainNo })
    }

    @Test
    fun rulesOnlyFallbackBuildsTrainWhenScheduleRulesAreAbsent() = runTest {
        val service = FeedDiscountService(
            client = FakeHttpClient(feedBody = """
                {
                  "rules": [
                    {
                      "kind": "early_bird",
                      "train_number": "300",
                      "direction": "北上",
                      "departure_time": "05:50",
                      "valid_from": "2026-06-01",
                      "offer": { "kind": "percentage", "percent": 65 },
                      "priority": 10
                    },
                    {
                      "kind": "student",
                      "train_number": "300",
                      "direction": "北上",
                      "departure_time": "05:50",
                      "valid_from": "2026-06-01",
                      "offer": { "kind": "percentage", "percent": 75 },
                      "priority": 10
                    }
                  ]
                }
            """.trimIndent()),
            feedUrl = "https://example.com/discounts.json",
        )

        val result = service.trains(
            query = TripQuery(
                origin = Station.Zuoying,
                destination = Station.Taichung,
                travelDate = LocalDate.of(2026, 6, 1),
                departureAfter = LocalTime.MIDNIGHT,
            ),
            forceRefresh = true,
        )

        assertEquals(listOf("0300"), result.trains.map { it.trainNo })
    }

    @Test
    fun discountsMatchWhenFeedUsesPaddedTrainNumberButTimetableDoesNot() = runTest {
        val service = FeedDiscountService(
            client = FakeHttpClient(feedBody = """
                {
                  "rules": [
                    {
                      "kind": "early_bird",
                      "train_number": "0640",
                      "direction": "南下",
                      "departure_time": "14:11",
                      "dates": ["2026-05-14"],
                      "offer": { "kind": "percentage", "percent": 80, "label": "早鳥8折" },
                      "priority": 10
                    }
                  ]
                }
            """.trimIndent()),
            feedUrl = "https://example.com/discounts.json",
        )

        val result = service.discounts(
            date = LocalDate.of(2026, 5, 14),
            trains = listOf(
                com.chiiii5640.thsrapp.features.timetable.TimetableTrain(
                    trainNo = "640",
                    departureTime = LocalTime.of(14, 11),
                    arrivalTime = LocalTime.of(14, 45),
                    stops = emptyList(),
                ),
            ),
            forceRefresh = true,
        )

        assertEquals(listOf("早鳥8折"), result.offersByTrainNo["640"]?.map { it.label })
    }
}

private class FakeHttpClient(
    private val feedBody: String,
) : HttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
        HttpResponse(code = 200, body = feedBody)

    override suspend fun postForm(
        url: String,
        body: Map<String, String>,
        headers: Map<String, String>,
    ): HttpResponse = error("unused")
}
