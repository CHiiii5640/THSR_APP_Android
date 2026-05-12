package com.chiiii5640.thsrapp.features.seatAvailability

import com.chiiii5640.thsrapp.core.model.SeatStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TdxSeatAvailabilityProviderTest {
    @Test
    fun mapsTdxSeatStatusCodesToSeatStatus() {
        assertEquals(SeatStatus.Available, seatStatusFromCodes("O"))
        assertEquals(SeatStatus.Limited, seatStatusFromCodes("L"))
        assertEquals(SeatStatus.SoldOut, seatStatusFromCodes("X"))
    }

    @Test
    fun keepsEnglishStatusesWorking() {
        assertEquals(SeatStatus.Available, seatStatusFromCodes("Available"))
        assertEquals(SeatStatus.Limited, seatStatusFromCodes("Limited"))
        assertEquals(SeatStatus.SoldOut, seatStatusFromCodes("Sold Out"))
    }

    @Test
    fun prefersBestAvailableStatusAcrossSeatClasses() {
        assertEquals(SeatStatus.Available, seatStatusFromCodes("X", "O"))
        assertEquals(SeatStatus.Limited, seatStatusFromCodes("X", "L"))
    }
}
