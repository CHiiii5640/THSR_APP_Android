package com.chiiii5640.thsrapp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

@Serializable
data class TdxDailyTimetableItem(
    @SerialName("TrainDate") val trainDate: String,
    @SerialName("DailyTrainInfo") val dailyTrainInfo: TdxTrainInfo,
    @SerialName("StopTimes") val stopTimes: List<TdxStopTime>,
    @SerialName("UpdateTime") val updateTime: String? = null,
)

@Serializable
data class TdxGeneralTimetableRecord(
    @SerialName("GeneralTimetable") val generalTimetable: TdxGeneralTimetableItem,
)

@Serializable
data class TdxGeneralTimetableItem(
    @SerialName("GeneralTrainInfo") val generalTrainInfo: TdxTrainInfo,
    @SerialName("StopTimes") val stopTimes: List<TdxStopTime>,
    @SerialName("ServiceDay") val serviceDay: TdxServiceDay? = null,
)

@Serializable
data class TdxServiceDay(
    @SerialName("Monday") val monday: Int = 0,
    @SerialName("Tuesday") val tuesday: Int = 0,
    @SerialName("Wednesday") val wednesday: Int = 0,
    @SerialName("Thursday") val thursday: Int = 0,
    @SerialName("Friday") val friday: Int = 0,
    @SerialName("Saturday") val saturday: Int = 0,
    @SerialName("Sunday") val sunday: Int = 0,
) {
    fun runsOn(dayOfWeek: DayOfWeek): Boolean = when (dayOfWeek) {
        DayOfWeek.MONDAY -> monday == 1
        DayOfWeek.TUESDAY -> tuesday == 1
        DayOfWeek.WEDNESDAY -> wednesday == 1
        DayOfWeek.THURSDAY -> thursday == 1
        DayOfWeek.FRIDAY -> friday == 1
        DayOfWeek.SATURDAY -> saturday == 1
        DayOfWeek.SUNDAY -> sunday == 1
    }
}

@Serializable
data class TdxTrainInfo(
    @SerialName("TrainNo") val trainNo: String,
    @SerialName("Direction") val direction: Int? = null,
)

@Serializable
data class TdxStopTime(
    @SerialName("StationID") val stationId: String,
    @SerialName("ArrivalTime") val arrivalTime: String? = null,
    @SerialName("DepartureTime") val departureTime: String? = null,
)

@Serializable
data class TdxSeatStatusItem(
    @SerialName("TrainNo") val trainNo: String,
    @SerialName("Direction") val direction: Int? = null,
    @SerialName("StationID") val stationId: String? = null,
    @SerialName("OriginStationID") val originStationId: String? = null,
    @SerialName("DestinationStationID") val destinationStationId: String? = null,
    @SerialName("StandardSeatStatus") val standardSeatStatus: String? = null,
    @SerialName("BusinessSeatStatus") val businessSeatStatus: String? = null,
)

@Serializable
data class TdxSeatStatusResponse(
    @SerialName("UpdateTime") val updateTime: String? = null,
    @SerialName("AvailableSeats") val availableSeats: List<TdxSeatStatusItem> = emptyList(),
)

@Serializable
data class TdxSeatBoardStationResponse(
    @SerialName("UpdateTime") val updateTime: String? = null,
    @SerialName("Items") val items: List<TdxSeatBoardItem> = emptyList(),
)

@Serializable
data class TdxSeatBoardObjectResponse(
    @SerialName("UpdateTime") val updateTime: String? = null,
    @SerialName("Items") val items: List<TdxSeatBoardItem> = emptyList(),
    @SerialName("AvailableSeats") val availableSeats: List<TdxSeatBoardItem> = emptyList(),
)

@Serializable
data class TdxSeatBoardItem(
    @SerialName("TrainNo") val trainNo: String,
    @SerialName("Direction") val direction: Int? = null,
    @SerialName("StopStations") val stopStations: List<TdxSeatBoardStopStation> = emptyList(),
)

@Serializable
data class TdxSeatBoardStopStation(
    @SerialName("StationID") val stationId: String,
    @SerialName("StandardSeatStatus") val standardSeatStatus: String? = null,
    @SerialName("BusinessSeatStatus") val businessSeatStatus: String? = null,
)

@Serializable
data class TdxTrainDateSupply(
    @SerialName("StartDate") val startDate: String,
    @SerialName("EndDate") val endDate: String,
    @SerialName("TrainDates") val trainDates: List<String> = emptyList(),
    @SerialName("UpdateTime") val updateTime: String? = null,
) {
    fun latestBookableDate(): java.time.LocalDate? {
        val listedDates = trainDates.mapNotNull(::parseLocalDateOrNull)
        val explicitEndDate = parseLocalDateOrNull(endDate)
        return (listedDates + listOfNotNull(explicitEndDate)).maxOrNull()
    }

    fun mergedConfirmedAvailableDate(
        confirmedDate: java.time.LocalDate,
        updateTime: String?,
    ): TdxTrainDateSupply {
        val normalizedDate = confirmedDate.toString()
        val normalizedStartDate = parseLocalDateOrNull(startDate)
        val normalizedEndDate = parseLocalDateOrNull(endDate)
        val mergedTrainDates = trainDates
            .asSequence()
            .filter { it.isNotBlank() }
            .plus(normalizedDate)
            .distinct()
            .sorted()
            .toList()

        return TdxTrainDateSupply(
            startDate = minOf(normalizedStartDate ?: confirmedDate, confirmedDate).toString(),
            endDate = maxOf(normalizedEndDate ?: confirmedDate, confirmedDate).toString(),
            trainDates = mergedTrainDates,
            updateTime = latestUpdateTime(this.updateTime, updateTime),
        )
    }
}

private fun parseLocalDateOrNull(value: String?): java.time.LocalDate? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return runCatching { java.time.LocalDate.parse(normalized) }.getOrNull()
}

private fun latestUpdateTime(current: String?, incoming: String?): String? {
    val currentValue = parseOffsetDateTimeOrNull(current)
    val incomingValue = parseOffsetDateTimeOrNull(incoming)
    return when {
        currentValue == null -> incoming
        incomingValue == null -> current
        incomingValue >= currentValue -> incoming
        else -> current
    }
}

private fun parseOffsetDateTimeOrNull(value: String?): java.time.OffsetDateTime? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return runCatching { java.time.OffsetDateTime.parse(normalized) }.getOrNull()
}
