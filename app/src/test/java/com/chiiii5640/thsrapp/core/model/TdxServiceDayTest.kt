package com.chiiii5640.thsrapp.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class TdxServiceDayTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesAndMatchesGeneralTimetableServiceDay() {
        val payload = json.decodeFromString<TdxGeneralTimetableRecord>(
            """
            {
              "GeneralTimetable": {
                "GeneralTrainInfo": {
                  "TrainNo": "1103",
                  "Direction": 0
                },
                "StopTimes": [
                  { "StationID": "0990", "DepartureTime": "06:35" },
                  { "StationID": "1040", "ArrivalTime": "07:33", "DepartureTime": "07:35" }
                ],
                "ServiceDay": {
                  "Monday": 1,
                  "Tuesday": 0,
                  "Wednesday": 0,
                  "Thursday": 0,
                  "Friday": 0,
                  "Saturday": 1,
                  "Sunday": 0
                }
              }
            }
            """.trimIndent(),
        )

        val serviceDay = payload.generalTimetable.serviceDay!!
        assertTrue(serviceDay.runsOn(DayOfWeek.MONDAY))
        assertTrue(serviceDay.runsOn(DayOfWeek.SATURDAY))
        assertFalse(serviceDay.runsOn(DayOfWeek.WEDNESDAY))
    }
}
