package com.chiiii5640.thsrapp.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class DiscountFeed(
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("sources") val sources: List<DiscountFeedSource> = emptyList(),
    @SerialName("schedule_rules") val scheduleRules: List<DiscountFeedScheduleRule> = emptyList(),
    @SerialName("rules") val rules: List<DiscountFeedRule> = emptyList(),
    @SerialName("items") val items: List<DiscountFeedItem> = emptyList(),
)

@Serializable
data class DiscountFeedSource(
    @SerialName("title") val title: String,
    @SerialName("url") val url: String? = null,
    @SerialName("fetched_at") val fetchedAt: String? = null,
    @SerialName("timetable_kind") val timetableKind: String? = null,
)

@Serializable
data class DiscountFeedScheduleRule(
    @SerialName("train_number") val trainNumber: String,
    @SerialName("direction") val direction: String,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    @SerialName("dates") val dates: List<String> = emptyList(),
    @SerialName("stops") val stops: Map<String, String> = emptyMap(),
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("timetable_kind") val timetableKind: String? = null,
)

@Serializable
data class DiscountFeedRule(
    @SerialName("kind") val kind: String,
    @SerialName("train_number") val trainNumber: String,
    @SerialName("direction") val direction: String,
    @SerialName("departure_time") val departureTime: String,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    @SerialName("weekdays") val weekdays: List<Int> = emptyList(),
    @SerialName("dates") val dates: List<String> = emptyList(),
    @SerialName("excluded_dates") val excludedDates: List<String> = emptyList(),
    @SerialName("offer") val offer: DiscountFeedOffer? = null,
    @SerialName("priority") val priority: Int = 0,
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
data class DiscountFeedOffer(
    @SerialName("kind") val kind: String? = null,
    @SerialName("percent") val percent: Int? = null,
    @SerialName("label") val label: String? = null,
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

fun DiscountFeedScheduleRule.matches(date: LocalDate): Boolean {
    val target = date.toString()
    if (dates.isNotEmpty()) return target in dates
    if (validFrom != null && target < validFrom) return false
    if (validUntil != null && target > validUntil) return false
    return true
}

fun DiscountFeedRule.matches(date: LocalDate): Boolean {
    val target = date.toString()
    if (target in excludedDates) return false
    if (dates.isNotEmpty()) return target in dates
    if (validFrom != null && target < validFrom) return false
    if (validUntil != null && target > validUntil) return false
    if (weekdays.isNotEmpty()) {
        val weekday = when (date.dayOfWeek) {
            DayOfWeek.SUNDAY -> 1
            DayOfWeek.MONDAY -> 2
            DayOfWeek.TUESDAY -> 3
            DayOfWeek.WEDNESDAY -> 4
            DayOfWeek.THURSDAY -> 5
            DayOfWeek.FRIDAY -> 6
            DayOfWeek.SATURDAY -> 7
        }
        return weekday in weekdays
    }
    return true
}

fun DiscountFeedRule.departureLocalTime(): LocalTime = LocalTime.parse(departureTime)

fun DiscountFeedScheduleRule.timetableDepartureLocalTime(): LocalTime? = when (direction) {
    "南下" -> stops[Station.Nangang.localName]?.let(LocalTime::parse)
    "北上" -> stops[Station.Zuoying.localName]?.let(LocalTime::parse)
    else -> null
}

fun DiscountFeedScheduleRule.isOfficialSpecial(): Boolean = timetableKind == "official_special"

fun DiscountFeedRule.isNotRunning(): Boolean = offer?.kind == "not_running"
