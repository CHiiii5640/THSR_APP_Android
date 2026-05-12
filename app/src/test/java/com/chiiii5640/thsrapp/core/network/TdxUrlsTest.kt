package com.chiiii5640.thsrapp.core.network

import com.chiiii5640.thsrapp.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TdxUrlsTest {
    @Test
    fun buildsRequiredTdxUrls() {
        assertEquals(
            "https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/DailyTimetable/TrainDate/2026-05-12?\$format=JSON",
            TdxUrls.dailyTimetable(LocalDate.of(2026, 5, 12)),
        )
        assertEquals(
            "https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/GeneralTimetable?\$top=300&\$format=JSON",
            TdxUrls.generalTimetable(),
        )
        assertTrue(TdxUrls.odSeatStatus(LocalDate.of(2026, 5, 12), Station.Taipei, Station.Zuoying).contains("OriginStationID eq '1000'"))
        assertEquals(
            "https://tdx.transportdata.tw/api/basic/v2/Rail/THSR/AvailableSeatStatusList/1000?\$top=100&\$format=JSON",
            TdxUrls.todaySeatBoard(Station.Taipei),
        )
    }
}
