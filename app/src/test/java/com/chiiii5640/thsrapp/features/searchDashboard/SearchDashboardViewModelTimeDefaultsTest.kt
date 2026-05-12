package com.chiiii5640.thsrapp.features.searchDashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SearchDashboardViewModelTimeDefaultsTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-12T10:08:00Z"), ZoneId.of("Asia/Taipei"))

    @Test
    fun todayDefaultsToCurrentTime() {
        assertEquals(
            LocalTime.of(18, 8),
            defaultDepartureAfter(LocalDate.of(2026, 5, 12), clock),
        )
    }

    @Test
    fun futureDateDefaultsToMidnight() {
        assertEquals(
            LocalTime.MIDNIGHT,
            defaultDepartureAfter(LocalDate.of(2026, 5, 13), clock),
        )
    }
}
