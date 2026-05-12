package com.chiiii5640.thsrapp.core.network

import com.chiiii5640.thsrapp.core.model.TdxSeatBoardStationResponse
import com.chiiii5640.thsrapp.core.model.TdxSeatBoardObjectResponse
import com.chiiii5640.thsrapp.core.model.TdxSeatStatusResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TdxSeatDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesOdSeatStatusWrapperObject() {
        val payload = json.decodeFromString<TdxSeatStatusResponse>(
            """
            {
              "UpdateTime": "2026-05-12T18:30:00+08:00",
              "AvailableSeats": [
                {
                  "TrainNo": "0803",
                  "OriginStationID": "1000",
                  "DestinationStationID": "1043",
                  "StandardSeatStatus": "Available",
                  "BusinessSeatStatus": "Limited"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, payload.availableSeats.size)
        assertEquals("0803", payload.availableSeats.first().trainNo)
        assertEquals("1000", payload.availableSeats.first().originStationId)
    }

    @Test
    fun decodesSeatBoardStationArray() {
        val payload = json.decodeFromString<List<TdxSeatBoardStationResponse>>(
            """
            [
              {
                "UpdateTime": "2026-05-12T18:30:00+08:00",
                "Items": [
                  {
                    "TrainNo": "0803",
                    "Direction": 0,
                    "StopStations": [
                      {
                        "StationID": "1043",
                        "StandardSeatStatus": "Available",
                        "BusinessSeatStatus": "Sold Out"
                      }
                    ]
                  }
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1, payload.size)
        assertEquals(1, payload.first().items.size)
        assertEquals("1043", payload.first().items.first().stopStations.first().stationId)
    }

    @Test
    fun decodesSeatBoardObjectWrapper() {
        val payload = json.decodeFromString<TdxSeatBoardObjectResponse>(
            """
            {
              "UpdateTime": "2026-05-12T19:00:00+08:00",
              "Items": [
                {
                  "TrainNo": "0803",
                  "Direction": 0,
                  "StopStations": [
                    {
                      "StationID": "1043",
                      "StandardSeatStatus": "Available",
                      "BusinessSeatStatus": "Sold Out"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, payload.items.size)
        assertEquals("0803", payload.items.first().trainNo)
    }
}
