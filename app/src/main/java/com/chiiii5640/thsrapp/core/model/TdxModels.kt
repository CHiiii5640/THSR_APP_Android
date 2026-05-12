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
    @SerialName("StationID") val stationId: String? = null,
    @SerialName("OriginStationID") val originStationId: String? = null,
    @SerialName("DestinationStationID") val destinationStationId: String? = null,
    @SerialName("StandardSeatStatus") val standardSeatStatus: String? = null,
    @SerialName("BusinessSeatStatus") val businessSeatStatus: String? = null,
)
