package com.chiiii5640.thsrapp.features.discounts

import com.chiiii5640.thsrapp.core.logging.ThsrLog
import com.chiiii5640.thsrapp.core.model.DiscountFeedRule
import com.chiiii5640.thsrapp.core.model.DiscountFeedScheduleRule
import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TripQuery
import com.chiiii5640.thsrapp.core.model.departureLocalTime
import com.chiiii5640.thsrapp.core.model.isNotRunning
import com.chiiii5640.thsrapp.core.model.isOfficialSpecial
import com.chiiii5640.thsrapp.core.model.matches
import com.chiiii5640.thsrapp.core.model.timetableDepartureLocalTime
import com.chiiii5640.thsrapp.core.model.DiscountFeed
import com.chiiii5640.thsrapp.core.model.DiscountOffer
import com.chiiii5640.thsrapp.core.model.DiscountType
import com.chiiii5640.thsrapp.core.network.HttpClient
import com.chiiii5640.thsrapp.core.network.unavailableStatus
import com.chiiii5640.thsrapp.features.timetable.FallbackTimetableProvider
import com.chiiii5640.thsrapp.features.timetable.FallbackTimetableResult
import com.chiiii5640.thsrapp.features.timetable.TimetableTrain
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

class FeedDiscountService(
    private val client: HttpClient,
    private val feedUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DiscountProvider, FallbackTimetableProvider {
    private var cachedFeed: DiscountFeed? = null

    override suspend fun discounts(
        date: LocalDate,
        trains: List<TimetableTrain>,
        forceRefresh: Boolean,
    ): DiscountResult {
        val (feed, error) = loadFeed(forceRefresh)

        if (feed == null) {
            return DiscountResult(
                emptyMap(),
                unavailableStatus("discount feed unavailable", error),
            )
        }

        val offers = trains.associate { train ->
            train.trainNo to offersForTrain(feed, date, train)
        }
        return DiscountResult(offers, SourceStatus("GitHub Pages discount feed", SourceState.Live))
    }

    override suspend fun trains(query: TripQuery, forceRefresh: Boolean): FallbackTimetableResult {
        val (feed, error) = loadFeed(forceRefresh)
        if (feed == null) {
            ThsrLog.e("discount feed timetable fallback load failed", error)
            return FallbackTimetableResult(
                emptyList(),
                unavailableStatus("discount feed timetable unavailable", error),
            )
        }

        val direction = query.directionLabel()
        val options = scheduleRuleFallback(feed, query, direction).ifEmpty {
            rulesOnlyFallback(feed, query, direction)
        }

        ThsrLog.i(
            "discount feed fallback matched ${options.size} trains for ${query.directionLabel()} ${query.origin.localName}-${query.destination.localName} ${query.travelDate} after ${query.departureAfter}",
        )

        return FallbackTimetableResult(
            trains = options,
            status = if (options.isEmpty()) {
                SourceStatus("discount feed timetable fallback empty", SourceState.Unavailable)
            } else {
                SourceStatus("GitHub Pages timetable fallback", SourceState.Fallback)
            },
        )
    }

    private suspend fun loadFeed(forceRefresh: Boolean): Pair<DiscountFeed?, Throwable?> {
        val feedResult = if (!forceRefresh && cachedFeed != null) {
            Result.success(cachedFeed)
        } else {
            runCatching {
                val response = client.get(feedUrl)
                if (!response.isSuccessful) error("discount feed HTTP ${response.code}")
                json.decodeFromString<DiscountFeed>(response.body).also { cachedFeed = it }
            }
        }
        val feed = if (!forceRefresh && cachedFeed != null) cachedFeed else feedResult.getOrNull()
        return feed to feedResult.exceptionOrNull()
    }

    private fun scheduleRuleFallback(
        feed: DiscountFeed,
        query: TripQuery,
        direction: String,
    ): List<TimetableTrain> {
        val matchingScheduleRules = feed.scheduleRules
            .asSequence()
            .filter { it.direction == direction && it.matches(query.travelDate) }
            .filter { it.stops.containsKey(query.origin.localName) && it.stops.containsKey(query.destination.localName) }
            .filter { it.hasOrderedStops(query.origin, query.destination) }
            .toList()

        if (matchingScheduleRules.isEmpty()) return emptyList()

        val selectedScheduleRules = if (matchingScheduleRules.any(DiscountFeedScheduleRule::isOfficialSpecial)) {
            matchingScheduleRules.filter(DiscountFeedScheduleRule::isOfficialSpecial)
        } else {
            matchingScheduleRules
        }

        return selectedScheduleRules
            .filterNot { hasNotRunningRule(feed, it, query.travelDate) }
            .mapNotNull { scheduleRuleToTimetableTrain(it, query) }
            .sortedBy { it.departureTime }
    }

    private fun rulesOnlyFallback(
        feed: DiscountFeed,
        query: TripQuery,
        direction: String,
    ): List<TimetableTrain> =
        feed.rules
            .asSequence()
            .filter { it.direction == direction && it.matches(query.travelDate) && !it.isNotRunning() }
            .distinctBy { "${it.trainNumber}-${it.departureTime}" }
            .mapNotNull { rule ->
                val departureTime = rule.departureLocalTime()
                if (departureTime.isBefore(query.departureAfter)) return@mapNotNull null
                val inferredArrivalTime = inferArrivalTimeForFallbackDestination(
                    direction = direction,
                    destination = query.destination,
                    listedTime = departureTime,
                )
                TimetableTrain(
                    trainNo = rule.trainNumber.padStart(4, '0'),
                    departureTime = departureTime,
                    arrivalTime = inferredArrivalTime,
                    stops = listOf(
                        TimelineStop(query.origin, arrivalTime = null, departureTime = departureTime),
                        TimelineStop(query.destination, arrivalTime = inferredArrivalTime, departureTime = null),
                    ),
                )
            }
            .sortedBy { it.departureTime }
            .toList()

    private fun hasNotRunningRule(
        feed: DiscountFeed,
        scheduleRule: DiscountFeedScheduleRule,
        date: LocalDate,
    ): Boolean {
        val departureTime = scheduleRule.timetableDepartureLocalTime() ?: return false
        return feed.rules.any { rule ->
            rule.trainNumber.padStart(4, '0') == scheduleRule.trainNumber.padStart(4, '0') &&
                rule.direction == scheduleRule.direction &&
                rule.departureLocalTime() == departureTime &&
                rule.isNotRunning() &&
                rule.matches(date)
        }
    }

    private fun scheduleRuleToTimetableTrain(
        rule: DiscountFeedScheduleRule,
        query: TripQuery,
    ): TimetableTrain? {
        val originTime = rule.stops[query.origin.localName]?.let(LocalTime::parse) ?: return null
        val destinationListedTime = rule.stops[query.destination.localName]?.let(LocalTime::parse) ?: return null
        if (originTime.isBefore(query.departureAfter)) return null

        val orderedStations = when (rule.direction) {
            "南下" -> Station.entries.sortedBy { it.sortIndex }
            "北上" -> Station.entries.sortedByDescending { it.sortIndex }
            else -> return null
        }

        val rawStops = orderedStations.mapNotNull { station ->
            rule.stops[station.localName]?.let(LocalTime::parse)?.let { time ->
                station to time
            }
        }
        if (rawStops.isEmpty()) return null

        val stops = rawStops.mapIndexed { index, (station, listedTime) ->
            val isRouteFirst = index == 0
            val isRouteLast = index == rawStops.lastIndex
            TimelineStop(
                station = station,
                arrivalTime = when {
                    isRouteFirst -> null
                    isRouteLast -> listedTime
                    else -> listedTime.minusMinutes(1)
                },
                departureTime = when {
                    isRouteLast -> null
                    else -> listedTime
                },
            )
        }

        val destinationTime = stops
            .firstOrNull { it.station == query.destination }
            ?.arrivalTime
            ?: destinationListedTime

        return TimetableTrain(
            trainNo = rule.trainNumber.padStart(4, '0'),
            departureTime = originTime,
            arrivalTime = destinationTime,
            stops = stops,
        )
    }

    private fun inferArrivalTimeForFallbackDestination(
        direction: String,
        destination: Station,
        listedTime: LocalTime,
    ): LocalTime {
        val routeTerminal = when (direction) {
            "南下" -> Station.Zuoying
            "北上" -> Station.Nangang
            else -> destination
        }
        return if (destination == routeTerminal) listedTime else listedTime.minusMinutes(1)
    }

    private fun offersForTrain(feed: DiscountFeed, date: LocalDate, train: TimetableTrain): List<DiscountOffer> {
        val trainNoKeys = trainNoLookupKeys(train.trainNo)
        val legacyOffers = feed.items
            .filter { item ->
                item.trainNo in trainNoKeys &&
                    date.toString() !in item.excludedDates
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

        val ruleOffers = feed.rules
            .filter { rule ->
                trainNoLookupKeys(rule.trainNumber).any { it in trainNoKeys } &&
                    rule.matches(date)
            }
            .sortedByDescending(DiscountFeedRule::priority)
            .mapNotNull { rule ->
                val offer = rule.offer ?: return@mapNotNull null
                DiscountOffer(
                    type = when (rule.kind.lowercase()) {
                        "early_bird", "earlybird" -> DiscountType.EarlyBird
                        "student", "college_student", "college" -> DiscountType.CollegeStudent
                        else -> DiscountType.Other
                    },
                    label = offer.label ?: offer.percent?.let { "${it}折" } ?: rule.kind,
                    percentOff = offer.percent,
                )
            }

        return (legacyOffers + ruleOffers)
            .distinctBy { "${it.type}-${it.label}-${it.percentOff}" }
    }
}

private fun DiscountFeedScheduleRule.hasOrderedStops(origin: Station, destination: Station): Boolean {
    if (!stops.containsKey(origin.localName) || !stops.containsKey(destination.localName)) return false
    return when (direction) {
        "南下" -> origin.sortIndex < destination.sortIndex
        "北上" -> origin.sortIndex > destination.sortIndex
        else -> false
    }
}

private fun trainNoLookupKeys(trainNo: String): Set<String> {
    val trimmed = trainNo.trim()
    if (trimmed.isEmpty()) return emptySet()
    val withoutLeadingZeros = trimmed.dropWhile { it == '0' }
    val normalized = if (withoutLeadingZeros.isEmpty()) "0" else withoutLeadingZeros
    val padded = normalized.padStart(4, '0')
    return linkedSetOf(trimmed, normalized, padded)
}
