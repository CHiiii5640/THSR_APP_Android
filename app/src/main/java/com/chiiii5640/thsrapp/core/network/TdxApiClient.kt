package com.chiiii5640.thsrapp.core.network

import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TdxDailyTimetableItem
import com.chiiii5640.thsrapp.core.model.TdxGeneralTimetableItem
import com.chiiii5640.thsrapp.core.model.TdxSeatStatusItem
import kotlinx.serialization.json.Json
import java.time.LocalDate

class TdxApiClient(
    private val client: HttpClient,
    private val auth: TdxAuthInterceptor,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun dailyTimetable(date: LocalDate, forceRefresh: Boolean): List<TdxDailyTimetableItem> =
        getJson(TdxUrls.dailyTimetable(date), forceRefresh)

    suspend fun generalTimetable(forceRefresh: Boolean): List<TdxGeneralTimetableItem> =
        getJson(TdxUrls.generalTimetable(), forceRefresh)

    suspend fun odSeatStatus(
        date: LocalDate,
        origin: Station,
        destination: Station,
        forceRefresh: Boolean,
    ): List<TdxSeatStatusItem> = getJson(TdxUrls.odSeatStatus(date, origin, destination), forceRefresh)

    suspend fun todaySeatBoard(origin: Station, forceRefresh: Boolean): List<TdxSeatStatusItem> =
        getJson(TdxUrls.todaySeatBoard(origin), forceRefresh)

    private suspend inline fun <reified T> getJson(url: String, forceRefresh: Boolean): T {
        val response = client.get(
            url = url,
            headers = mapOf("Authorization" to "Bearer ${auth.bearerToken(forceRefresh)}"),
        )
        if (!response.isSuccessful) error("TDX request failed: HTTP ${response.code} url=$url")
        return json.decodeFromString(response.body)
    }
}

object TdxUrls {
    private const val BASE = "https://tdx.transportdata.tw/api/basic/v2/Rail/THSR"

    fun dailyTimetable(date: LocalDate): String =
        "$BASE/DailyTimetable/TrainDate/$date?\$format=JSON"

    fun generalTimetable(): String =
        "$BASE/GeneralTimetable?\$top=300&\$format=JSON"

    fun odSeatStatus(date: LocalDate, origin: Station, destination: Station): String =
        "$BASE/AvailableSeatStatus/Train/OD/TrainDate/$date?\$top=500&" +
            "\$filter=OriginStationID eq '${origin.id}' and DestinationStationID eq '${destination.id}'&\$format=JSON"

    fun todaySeatBoard(origin: Station): String =
        "$BASE/AvailableSeatStatusList/${origin.id}?\$top=100&\$format=JSON"
}
