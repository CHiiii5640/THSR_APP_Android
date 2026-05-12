package com.chiiii5640.thsrapp.features.discounts

import com.chiiii5640.thsrapp.core.model.DiscountFeed
import com.chiiii5640.thsrapp.core.model.DiscountOffer
import com.chiiii5640.thsrapp.core.model.DiscountType
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.network.HttpClient
import com.chiiii5640.thsrapp.features.timetable.TimetableTrain
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

class FeedDiscountService(
    private val client: HttpClient,
    private val feedUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DiscountProvider {
    private var cachedFeed: DiscountFeed? = null

    override suspend fun discounts(
        date: LocalDate,
        trains: List<TimetableTrain>,
        forceRefresh: Boolean,
    ): DiscountResult {
        val feed = if (!forceRefresh && cachedFeed != null) {
            cachedFeed
        } else {
            runCatching {
                val response = client.get(feedUrl)
                if (!response.isSuccessful) error("discount feed HTTP ${response.code}")
                json.decodeFromString<DiscountFeed>(response.body).also { cachedFeed = it }
            }.getOrNull()
        }

        if (feed == null) {
            return DiscountResult(emptyMap(), SourceStatus("discount feed unavailable", SourceState.Unavailable))
        }

        val offers = trains.associate { train ->
            train.trainNo to feed.items
                .filter { item ->
                    item.trainNo == train.trainNo &&
                        date.toString() !in item.excludedDates &&
                        train.departureTime.isWithin(item.departureStart, item.departureEnd)
                }
                .map { item ->
                    DiscountOffer(
                        type = when (item.type.lowercase()) {
                            "early_bird", "earlybird" -> DiscountType.EarlyBird
                            "college", "student", "college_student" -> DiscountType.CollegeStudent
                            else -> DiscountType.Other
                        },
                        label = item.label,
                        percentOff = item.percentOff,
                    )
                }
        }
        return DiscountResult(offers, SourceStatus("GitHub Pages discount feed", SourceState.Live))
    }
}

private fun LocalTime.isWithin(start: String?, end: String?): Boolean {
    val lower = start?.let(LocalTime::parse)
    val upper = end?.let(LocalTime::parse)
    return (lower == null || !isBefore(lower)) && (upper == null || !isAfter(upper))
}
