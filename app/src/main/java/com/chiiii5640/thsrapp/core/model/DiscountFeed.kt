package com.chiiii5640.thsrapp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscountFeed(
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("items") val items: List<DiscountFeedItem> = emptyList(),
)

@Serializable
data class DiscountFeedItem(
    @SerialName("train_no") val trainNo: String,
    @SerialName("direction") val direction: String? = null,
    @SerialName("type") val type: String,
    @SerialName("label") val label: String,
    @SerialName("percent_off") val percentOff: Int? = null,
    @SerialName("departure_start") val departureStart: String? = null,
    @SerialName("departure_end") val departureEnd: String? = null,
    @SerialName("excluded_dates") val excludedDates: List<String> = emptyList(),
)
