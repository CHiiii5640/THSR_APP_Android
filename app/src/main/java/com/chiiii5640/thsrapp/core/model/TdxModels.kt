package com.chiiii5640.thsrapp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TdxDailyTimetableItem(
    @SerialName("TrainDate") val trainDate: String,
    @SerialName("DailyTrainInfo") val dailyTrainInfo: TdxTrainInfo,
    @SerialName("StopTimes") val stopTimes: List<TdxStopTime>,
)

@Serializable
data class TdxGeneralTimetableRecord(
    @SerialName("GeneralTimetable") val generalTimetable: TdxGeneralTimetableItem,
)

@Serializable
data class TdxGeneralTimetableItem(
    @SerialName("GeneralTrainInfo") val generalTrainInfo: TdxTrainInfo,
    @SerialName("StopTimes") val stopTimes: List<TdxStopTime>,
)

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
