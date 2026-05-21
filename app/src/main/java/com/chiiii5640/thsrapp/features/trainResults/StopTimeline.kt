package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiiii5640.thsrapp.core.model.Station
import com.chiiii5640.thsrapp.core.model.TimelineStop
import com.chiiii5640.thsrapp.core.model.TimelineStopRole
import com.chiiii5640.thsrapp.core.model.TrainOption
import com.chiiii5640.thsrapp.core.model.timelineStops
import com.chiiii5640.thsrapp.core.time.ThsrFormatters
import com.chiiii5640.thsrapp.ui.layout.ThsrLayoutProfile
import com.chiiii5640.thsrapp.ui.theme.ThsrDesignTokens
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val timelineDepartingCyan = Color(0xFF64D1DF)
private val timelineApproachingAmber = Color(0xFFF2B46E)
private val timelineCruiseRailBlue = Color(0xFF4E8FC6)
private val timelineCruiseRailTailBlue = Color(0xFF758CA7)
private val timelineCruiseRailGlowBlue = Color(0xFF67A8DF)

private enum class TimelineTrainPhase(val cycleSeconds: Float) {
    Departing(2.15f),
    InTransit(2.55f),
    Approaching(2.9f),
    Docked(2.8f),
}

private enum class TimelineSemanticPhase {
    NotDeparted,
    AboutToDepart,
    Departing,
    InTransit,
    Approaching,
    Arriving,
    Stopped,
    Arrived,
}

private enum class TimelineNodeState {
    Station,
    Passed,
    Next,
    Standby,
    DepartingSoon,
    Approaching,
    ArrivingSoon,
    Stopped,
    Arrived,
}

private enum class TimelineStatusTone {
    Blue,
    Cyan,
    Amber,
}

private data class TimelineStatusPillState(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val tone: TimelineStatusTone,
)

private data class TimelineResolvedStop(
    val index: Int,
    val station: Station,
    val arrival: LocalDateTime?,
    val departure: LocalDateTime?,
    val displayTime: LocalTime,
    val stationTime: LocalDateTime,
    val dwellSeconds: Long,
)

private data class TimelineSegmentMetrics(
    val index: Int,
    val startCenterXPx: Float,
    val endCenterXPx: Float,
    val widthPx: Float,
) {
    fun position(progress: Float): Float = lerp(startCenterXPx, endCenterXPx, progress.clamp01())
}

private data class TimelineLayoutMetrics(
    val leadingInsetPx: Float,
    val trailingInsetPx: Float,
    val nodeCentersPx: List<Float>,
    val segments: List<TimelineSegmentMetrics>,
    val totalWidthPx: Float,
) {
    fun nodeCenterPx(index: Int): Float = nodeCentersPx.getOrElse(index) { 0f }

    fun segment(index: Int): TimelineSegmentMetrics? = segments.getOrNull(index)

    fun maxOffsetPx(viewportWidthPx: Float): Float = max(0f, totalWidthPx - viewportWidthPx)

    fun contentWidthPx(
        viewportWidthPx: Float,
        restingOffsetPx: Float,
    ): Float = max(totalWidthPx, viewportWidthPx + restingOffsetPx)

    fun requestedRestingOffsetPx(
        index: Int,
        preferredLeadingInsetPx: Float,
        labelWidthPx: Float,
    ): Float {
        val anchorCenterPx = nodeCenterPx(index)
        val desiredOffsetPx = (anchorCenterPx - preferredLeadingInsetPx).coerceAtLeast(0f)
        val labelSafeOffsetPx = (anchorCenterPx - (labelWidthPx / 2f)).coerceAtLeast(0f)
        return min(desiredOffsetPx, labelSafeOffsetPx)
    }

    fun defaultRestingOffsetPx(
        index: Int,
        preferredLeadingInsetPx: Float,
        labelWidthPx: Float,
        viewportWidthPx: Float,
        contentWidthPx: Float,
    ): Float {
        val restingOffsetPx = requestedRestingOffsetPx(
            index = index,
            preferredLeadingInsetPx = preferredLeadingInsetPx,
            labelWidthPx = labelWidthPx,
        )
        val maxRestingOffsetPx = max(0f, contentWidthPx - viewportWidthPx)
        return restingOffsetPx.coerceIn(0f, maxRestingOffsetPx)
    }
}

private data class TimelineVisualMetrics(
    val viewportHeight: Dp,
    val nodeContainer: Dp,
    val labelWidth: Dp,
    val labelTop: Dp,
    val markerTop: Dp,
    val markerHeight: Dp,
    val trackY: Dp,
    val leadingInset: Dp,
    val trailingInset: Dp,
)

private data class TimelineCanvasMetrics(
    val trackYPx: Float,
    val baseLineHeightPx: Float,
    val activeLineHeightPx: Float,
    val markerTopPx: Float,
    val markerHeightPx: Float,
    val labelTopPx: Float,
)

private data class TimelineMotionMetrics(
    val headWidthPx: Float,
    val headHeightPx: Float,
    val tailWidthPx: Float,
)

private data class TimelineAttentionRingVisual(
    val color: Color,
    val baseSize: Dp,
    val expandSize: Dp,
    val opacity: Float,
    val opacityRange: Float,
    val lineWidthPx: Float,
)

private data class TimelineMarkerVisual(
    val centerXPx: Float,
    val trackStartXPx: Float,
    val phase: TimelineTrainPhase,
    val motion: TimelineMotionMetrics,
    val opacity: Float,
    val arrivalTransfer: Float,
)

private data class TimelinePhaseState(
    val phase: TimelineSemanticPhase,
    val currentStationIndex: Int?,
    val nextStationIndex: Int?,
    val activeSegmentIndex: Int?,
    val motionPhase: TimelineTrainPhase?,
    val rawProgress: Float?,
    val currentStationTransfer: Float,
    val nextStationTransfer: Float,
    val defaultAnchorStopIndex: Int,
    val focusedSegmentIndex: Int,
)

private data class TimelineActiveSegmentProgress(
    val segmentIndex: Int,
    val rawProgress: Float,
    val easedProgress: Float,
    val motionPhase: TimelineTrainPhase,
    val profile: TimelineInfluenceProfile,
    val magnetic: Boolean,
)

private data class TimelineInfluenceProfile(
    private val departureTimeFraction: Float,
    private val approachTimeFraction: Float,
    private val departureVisualFraction: Float,
    private val approachVisualFraction: Float,
) {
    companion object {
        fun from(
            durationSeconds: Float,
            departureWindowSeconds: Float,
            approachWindowSeconds: Float,
        ): TimelineInfluenceProfile {
            val safeDuration = durationSeconds.coerceAtLeast(1f)
            val rawDeparture = (departureWindowSeconds / safeDuration).coerceIn(0f, 0.20f)
            val rawApproach = (approachWindowSeconds / safeDuration).coerceIn(0f, 0.28f)
            val rawTotal = rawDeparture + rawApproach
            val rawScale = if (rawTotal > 0.82f) 0.82f / rawTotal else 1f
            val compressedSegment = safeDuration < (departureWindowSeconds + approachWindowSeconds) * 1.35f
            val visualScale = if (compressedSegment) 0.88f else 1f
            return TimelineInfluenceProfile(
                departureTimeFraction = rawDeparture * rawScale,
                approachTimeFraction = rawApproach * rawScale,
                departureVisualFraction = 0.16f * visualScale,
                approachVisualFraction = 0.20f * visualScale,
            )
        }
    }

    fun phase(rawProgress: Float): TimelineTrainPhase {
        if (rawProgress <= departureTimeFraction) {
            return TimelineTrainPhase.Departing
        }
        if (rawProgress >= 1f - approachTimeFraction) {
            return TimelineTrainPhase.Approaching
        }
        return TimelineTrainPhase.InTransit
    }

    fun remappedProgress(rawProgress: Float, magnetic: Boolean): Float {
        val clamped = rawProgress.clamp01()
        val cruiseRawStart = departureTimeFraction
        val cruiseRawEnd = 1f - approachTimeFraction
        val cruiseVisualStart = departureVisualFraction
        val cruiseVisualEnd = 1f - approachVisualFraction

        if (departureTimeFraction > 0f && clamped <= cruiseRawStart) {
            val local = clamped / departureTimeFraction
            return departureVisualFraction * departureCurve(local)
        }

        if (approachTimeFraction > 0f && clamped >= cruiseRawEnd) {
            val local = (clamped - cruiseRawEnd) / approachTimeFraction
            return cruiseVisualEnd + (approachVisualFraction * approachCurve(local, magnetic))
        }

        val cruiseDuration = (cruiseRawEnd - cruiseRawStart).coerceAtLeast(0.0001f)
        val cruiseLocal = softenedCruiseProgress((clamped - cruiseRawStart) / cruiseDuration)
        return cruiseVisualStart + ((cruiseVisualEnd - cruiseVisualStart) * cruiseLocal)
    }

    fun departurePhaseProgress(rawProgress: Float): Float {
        if (departureTimeFraction <= 0f) return 1f
        return smoothstep((rawProgress / departureTimeFraction).clamp01())
    }

    fun departureNodeFade(rawProgress: Float): Float {
        if (phase(rawProgress) != TimelineTrainPhase.Departing) return 0f
        return 1f - departurePhaseProgress(rawProgress)
    }

    fun departedStationCarry(rawProgress: Float): Float {
        val start = departureTimeFraction
        val end = (departureTimeFraction + max(0.08f, departureVisualFraction * 0.9f)).coerceAtMost(0.34f)
        if (rawProgress <= start || rawProgress >= end) return 0f
        val local = ((rawProgress - start) / (end - start).coerceAtLeast(0.0001f)).clamp01()
        return 1f - smoothstep(local)
    }

    fun approachPhaseProgress(rawProgress: Float): Float {
        if (approachTimeFraction <= 0f) return 1f
        val localStart = 1f - approachTimeFraction
        return ((rawProgress - localStart) / approachTimeFraction).clamp01()
    }

    fun arrivalNodeTransfer(rawProgress: Float, magnetic: Boolean): Float {
        if (phase(rawProgress) != TimelineTrainPhase.Approaching) return 0f
        return approachCurve(approachPhaseProgress(rawProgress), magnetic)
    }

    private fun departureCurve(progress: Float): Float {
        val clamped = progress.clamp01()
        val smooth = smoothstep(clamped)
        return (smooth * 0.82f) + (clamped.pow(1.18f) * 0.18f)
    }

    private fun approachCurve(progress: Float, magnetic: Boolean): Float {
        val clamped = smoothstep(progress.clamp01())
        val exponent = if (magnetic) 2.8f else 2.2f
        return 1f - (1f - clamped).pow(exponent)
    }

    private fun softenedCruiseProgress(progress: Float): Float {
        val clamped = progress.clamp01()
        val seamBlend = 0.16f
        if (clamped <= seamBlend) {
            return seamBlend * smoothstep(clamped / seamBlend)
        }
        if (clamped >= 1f - seamBlend) {
            val local = (clamped - (1f - seamBlend)) / seamBlend
            return (1f - seamBlend) + (seamBlend * smoothstep(local))
        }
        return clamped
    }

    private fun smoothstep(progress: Float): Float {
        val clamped = progress.clamp01()
        return clamped * clamped * (3f - (2f * clamped))
    }
}

@Composable
fun StopTimeline(
    option: TrainOption,
    layoutProfile: ThsrLayoutProfile,
    timelineFrame: TrainTimelineFrame? = null,
) {
    val stops = remember(option) { option.timelineStops }
    if (stops.size < 2) {
        return
    }

    val tokens = ThsrDesignTokens
    val visualMetrics = remember(layoutProfile) { timelineVisualMetrics(layoutProfile) }
    val density = LocalDensity.current
    val canvasMetrics = remember(density, tokens, visualMetrics) {
        with(density) {
            TimelineCanvasMetrics(
                trackYPx = visualMetrics.trackY.toPx(),
                baseLineHeightPx = tokens.timeline.lineHeight.toPx(),
                activeLineHeightPx = tokens.timeline.activeLineHeight.toPx(),
                markerTopPx = visualMetrics.markerTop.toPx(),
                markerHeightPx = visualMetrics.markerHeight.toPx(),
                labelTopPx = visualMetrics.labelTop.toPx(),
            )
        }
    }
    val originStopIndex = remember(stops) {
        stops.indexOfFirst { it.role == TimelineStopRole.Origin }
            .takeIf { it >= 0 }
            ?: 0
    }
    val destinationStopIndex = remember(stops) {
        stops.indexOfFirst { it.role == TimelineStopRole.Destination }
            .takeIf { it >= 0 }
            ?: stops.lastIndex
    }
    val frame = timelineFrame ?: rememberTrainTimelineFrame()
    val odLiveState = remember(stops, option.travelDate, frame.now, canvasMetrics, originStopIndex, destinationStopIndex) {
        TimelineLiveState.create(
            stops = stops,
            travelDate = option.travelDate,
            now = frame.now,
            markerHeightPx = canvasMetrics.markerHeightPx,
            originStopIndex = originStopIndex,
            destinationStopIndex = destinationStopIndex,
        )
    }
    val routeLiveState = remember(stops, option.travelDate, frame.now, canvasMetrics) {
        TimelineLiveState.create(
            stops = stops,
            travelDate = option.travelDate,
            now = frame.now,
            markerHeightPx = canvasMetrics.markerHeightPx,
            originStopIndex = 0,
            destinationStopIndex = stops.lastIndex,
        )
    }
    val anchorStopIndex = originStopIndex.coerceIn(0, stops.lastIndex)
    val hasRouteActivityBeforeOrigin = remember(routeLiveState, anchorStopIndex) {
        routeLiveState?.hasLiveActivityBefore(anchorStopIndex) ?: false
    }
    val statusPill = remember(odLiveState, routeLiveState, hasRouteActivityBeforeOrigin) {
        if (hasRouteActivityBeforeOrigin) {
            routeLiveState?.statusPill()
        } else {
            odLiveState?.statusPill()
        }
    }
    val scrollState = rememberScrollState()
    var previousStopsRevealed by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }
    var revealDragDistancePx by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(0f)
    }
    var isUserDragging by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.spacing8),
    ) {
        statusPill?.let { TimelineStatusPill(pill = it) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(visualMetrics.viewportHeight)
                .clipToBounds(),
        ) {
            val timelineHorizontalPaddingPx = with(density) { 8.dp.toPx() }
            val viewportWidthPx = (with(density) { maxWidth.toPx() } - (timelineHorizontalPaddingPx * 2f))
                .coerceAtLeast(0f)
            val layoutMetrics = remember(stops, anchorStopIndex, density, layoutProfile, visualMetrics, viewportWidthPx) {
                buildTimelineLayout(
                    stops = stops,
                    anchorStopIndex = anchorStopIndex,
                    layoutProfile = layoutProfile,
                    visualMetrics = visualMetrics,
                    density = density,
                    viewportWidthPx = viewportWidthPx,
                )
            }
            val hiddenBeforeIndex = anchorStopIndex
            val routeActivityBeforeOrigin = previousStopsRevealed && hasRouteActivityBeforeOrigin
            val focusedSegmentIndex = (
                if (routeActivityBeforeOrigin) {
                    routeLiveState?.focusedSegmentIndex(0)
                } else {
                    odLiveState?.focusedSegmentIndex(anchorStopIndex)
                } ?: anchorStopIndex
                ).coerceIn(0, layoutMetrics.segments.lastIndex)
            val labelWidthPx = with(density) { visualMetrics.labelWidth.toPx() }
            val requestedInitialOffsetPx = remember(anchorStopIndex, layoutMetrics.nodeCentersPx, layoutMetrics.leadingInsetPx, labelWidthPx) {
                layoutMetrics.requestedRestingOffsetPx(
                    index = anchorStopIndex,
                    preferredLeadingInsetPx = layoutMetrics.leadingInsetPx,
                    labelWidthPx = labelWidthPx,
                )
            }
            val contentWidthPx = remember(layoutMetrics.totalWidthPx, viewportWidthPx, requestedInitialOffsetPx) {
                layoutMetrics.contentWidthPx(
                    viewportWidthPx = viewportWidthPx,
                    restingOffsetPx = requestedInitialOffsetPx,
                )
            }
            val lockedOffsetPx = remember(anchorStopIndex, layoutMetrics.nodeCentersPx, labelWidthPx, contentWidthPx, viewportWidthPx) {
                layoutMetrics.defaultRestingOffsetPx(
                    index = anchorStopIndex,
                    preferredLeadingInsetPx = layoutMetrics.leadingInsetPx,
                    labelWidthPx = labelWidthPx,
                    viewportWidthPx = viewportWidthPx,
                    contentWidthPx = contentWidthPx,
                )
            }
            val revealThresholdPx = with(density) { tokens.timeline.revealThreshold.toPx() }
            val shouldShowRailProgress = odLiveState?.hasVisibleRailProgress(anchorStopIndex) ?: false
            val shouldShowMarker = (odLiveState?.shouldShowMarker(anchorStopIndex) ?: false) || routeActivityBeforeOrigin

            LaunchedEffect(anchorStopIndex, lockedOffsetPx, contentWidthPx, option.trainNo, option.travelDate, option.origin, option.destination) {
                previousStopsRevealed = anchorStopIndex <= 0
                revealDragDistancePx = 0f
                isUserDragging = false
                withFrameNanos { }
                scrollState.scrollTo(lockedOffsetPx.roundToInt())
            }

            LaunchedEffect(scrollState.value, isUserDragging, revealDragDistancePx, previousStopsRevealed, lockedOffsetPx, anchorStopIndex) {
                val lockedOffset = lockedOffsetPx.roundToInt()
                if (!previousStopsRevealed && anchorStopIndex > 0 && scrollState.value < lockedOffset) {
                    if (isUserDragging && revealDragDistancePx >= revealThresholdPx) {
                        val revealOffset = (lockedOffsetPx - revealDragDistancePx)
                            .roundToInt()
                            .coerceAtLeast(0)
                        previousStopsRevealed = true
                        scrollState.scrollTo(revealOffset)
                    } else {
                        scrollState.scrollTo(lockedOffset)
                    }
                }
            }

            LaunchedEffect(isUserDragging, previousStopsRevealed, lockedOffsetPx, anchorStopIndex) {
                val lockedOffset = lockedOffsetPx.roundToInt()
                if (!isUserDragging && !previousStopsRevealed && anchorStopIndex > 0 && scrollState.value != lockedOffset) {
                    scrollState.animateScrollTo(lockedOffset)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .clipToBounds()
                    .pointerInput(anchorStopIndex, previousStopsRevealed, lockedOffsetPx) {
                        if (anchorStopIndex <= 0 || previousStopsRevealed) return@pointerInput

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isUserDragging = true
                            revealDragDistancePx = 0f
                            var lastX = down.position.x

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                if (!change.pressed) break

                                val deltaX = change.position.x - lastX
                                lastX = change.position.x
                                if (deltaX > 0f && scrollState.value.toFloat() <= lockedOffsetPx.roundToInt().toFloat() + 1f) {
                                    revealDragDistancePx = (revealDragDistancePx + deltaX)
                                        .coerceAtMost(revealThresholdPx * 1.6f)
                                } else if (deltaX < 0f) {
                                    revealDragDistancePx = 0f
                                }
                            }

                            isUserDragging = false
                            if (!previousStopsRevealed) {
                                revealDragDistancePx = 0f
                            }
                        }
                    }
                    .horizontalScroll(
                        state = scrollState,
                        enabled = contentWidthPx > viewportWidthPx,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { contentWidthPx.toDp() })
                        .fillMaxHeight(),
                ) {
                    TimelineRailCanvas(
                        layoutMetrics = layoutMetrics,
                        liveState = odLiveState,
                        revealedRouteLiveState = routeLiveState,
                        canvasMetrics = canvasMetrics,
                        frameNanos = frame.frameNanos,
                        hiddenBeforeIndex = hiddenBeforeIndex,
                        previousStopsRevealed = previousStopsRevealed,
                        routeActivityBeforeOrigin = routeActivityBeforeOrigin,
                        focusedSegmentIndex = focusedSegmentIndex,
                        showsRailProgress = shouldShowRailProgress,
                        showsMarker = shouldShowMarker,
                    )

                    TimelineParticleCanvas(
                        layoutMetrics = layoutMetrics,
                        liveState = odLiveState,
                        revealedRouteLiveState = routeLiveState,
                        canvasMetrics = canvasMetrics,
                        frameNanos = frame.frameNanos,
                        hiddenBeforeIndex = hiddenBeforeIndex,
                        previousStopsRevealed = previousStopsRevealed,
                        showsLiveProgress = shouldShowRailProgress,
                    )

                    stops.forEachIndexed { index, stop ->
                        val nodeStateSource = when {
                            previousStopsRevealed && index < hiddenBeforeIndex -> routeLiveState
                            else -> odLiveState
                        }
                        TimelineNode(
                            stop = stop,
                            centerXPx = layoutMetrics.nodeCenterPx(index),
                            totalWidthPx = contentWidthPx,
                            state = nodeStateSource?.nodeState(index) ?: TimelineNodeState.Station,
                            activeTransfer = nodeStateSource?.nodeActivationProgress(index) ?: 0f,
                            frameNanos = frame.frameNanos,
                            visualMetrics = visualMetrics,
                            canvasMetrics = canvasMetrics,
                            hidden = !previousStopsRevealed && index < hiddenBeforeIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineStatusPill(
    pill: TimelineStatusPillState,
) {
    val tokens = ThsrDesignTokens
    val tint = pill.tone.color()
    Surface(
        color = tint.copy(alpha = 0.10f),
        shape = RoundedCornerShape(tokens.radii.chipRadius),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 5.dp,
            ),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = pill.icon,
                    contentDescription = null,
                    tint = tint.copy(alpha = 0.95f),
                    modifier = Modifier
                        .size(12.dp)
                        .offset(y = 0.5.dp),
                )
                Text(
                    text = pill.title,
                    color = tint,
                    style = tokens.typography.statusPill,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pill.detail,
                    color = tokens.colors.textSecondary.copy(alpha = 0.82f),
                    style = tokens.typography.captionStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TimelineRailCanvas(
    layoutMetrics: TimelineLayoutMetrics,
    liveState: TimelineLiveState?,
    revealedRouteLiveState: TimelineLiveState?,
    canvasMetrics: TimelineCanvasMetrics,
    frameNanos: Long,
    hiddenBeforeIndex: Int,
    previousStopsRevealed: Boolean,
    routeActivityBeforeOrigin: Boolean,
    focusedSegmentIndex: Int,
    showsRailProgress: Boolean,
    showsMarker: Boolean,
) {
    val tokens = ThsrDesignTokens
    Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        val idleGlowColor = tokens.colors.primaryBlue.copy(alpha = 0.06f)
        layoutMetrics.segments.forEach { segment ->
            if (!previousStopsRevealed && segment.index < hiddenBeforeIndex) {
                return@forEach
            }

            val segmentState = when {
                previousStopsRevealed && segment.index < hiddenBeforeIndex -> revealedRouteLiveState
                else -> liveState
            }
            val segmentShowsProgress = when {
                previousStopsRevealed && segment.index < hiddenBeforeIndex -> true
                else -> showsRailProgress
            }
            val segmentOpacity = if (segment.index == focusedSegmentIndex) 1f else 0.82f
            drawRoundRect(
                color = Color(0xFF2E4056).copy(alpha = 0.50f * segmentOpacity),
                topLeft = Offset(
                    x = segment.startCenterXPx,
                    y = canvasMetrics.trackYPx - (canvasMetrics.baseLineHeightPx / 2f),
                ),
                size = Size(segment.widthPx, canvasMetrics.baseLineHeightPx),
                cornerRadius = CornerRadius(canvasMetrics.baseLineHeightPx, canvasMetrics.baseLineHeightPx),
            )

            val progress = segmentState?.easedSegmentProgress(segment.index) ?: 0f
            if (!segmentShowsProgress || progress <= 0f) {
                return@forEach
            }

            val phase = segmentState?.phaseOnSegment(segment.index) ?: TimelineTrainPhase.InTransit
            val fillWidthPx = segment.widthPx * progress
            val accent = phase.railAccentColor()
            val tailAccent = phase.railTailAccentColor()

            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to tailAccent.copy(alpha = 0.18f * segmentOpacity),
                        0.34f to tailAccent.copy(alpha = 0.28f * segmentOpacity),
                        0.74f to accent.copy(alpha = 0.42f * segmentOpacity),
                        1f to accent.copy(alpha = 0.76f * segmentOpacity),
                    ),
                    startX = segment.startCenterXPx,
                    endX = segment.startCenterXPx + fillWidthPx,
                ),
                topLeft = Offset(
                    x = segment.startCenterXPx,
                    y = canvasMetrics.trackYPx - (canvasMetrics.activeLineHeightPx / 2f),
                ),
                size = Size(fillWidthPx, canvasMetrics.activeLineHeightPx),
                cornerRadius = CornerRadius(canvasMetrics.activeLineHeightPx, canvasMetrics.activeLineHeightPx),
            )

            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.42f to accent.copy(alpha = 0.08f * segmentOpacity),
                        0.80f to phase.railGlowColor().copy(alpha = 0.20f * segmentOpacity),
                        1f to phase.railGlowColor().copy(alpha = 0.34f * segmentOpacity),
                    ),
                    startX = segment.startCenterXPx,
                    endX = segment.startCenterXPx + fillWidthPx,
                ),
                topLeft = Offset(
                    x = segment.startCenterXPx,
                    y = canvasMetrics.trackYPx - (canvasMetrics.activeLineHeightPx / 2f) - 0.8f,
                ),
                size = Size(fillWidthPx, canvasMetrics.activeLineHeightPx + 1.6f),
                cornerRadius = CornerRadius(canvasMetrics.activeLineHeightPx, canvasMetrics.activeLineHeightPx),
            )

            drawRoundRect(
                color = idleGlowColor.copy(alpha = idleGlowColor.alpha * segmentOpacity),
                topLeft = Offset(
                    x = segment.startCenterXPx,
                    y = canvasMetrics.trackYPx - (canvasMetrics.baseLineHeightPx / 2f),
                ),
                size = Size(fillWidthPx, canvasMetrics.baseLineHeightPx),
                cornerRadius = CornerRadius(canvasMetrics.baseLineHeightPx, canvasMetrics.baseLineHeightPx),
            )
        }

        val markerState = when {
            routeActivityBeforeOrigin -> revealedRouteLiveState?.marker(layoutMetrics)
            else -> liveState?.marker(layoutMetrics)
        }
        markerState?.let { marker ->
            if (
                showsMarker &&
                (previousStopsRevealed || marker.centerXPx >= layoutMetrics.nodeCenterPx(hiddenBeforeIndex))
            ) {
                drawTimelineMarker(
                    marker = marker,
                    canvasMetrics = canvasMetrics,
                    frameNanos = frameNanos,
                )
            }
        }
    }
}

@Composable
private fun TimelineParticleCanvas(
    layoutMetrics: TimelineLayoutMetrics,
    liveState: TimelineLiveState?,
    revealedRouteLiveState: TimelineLiveState?,
    canvasMetrics: TimelineCanvasMetrics,
    frameNanos: Long,
    hiddenBeforeIndex: Int,
    previousStopsRevealed: Boolean,
    showsLiveProgress: Boolean,
) {
    if (liveState == null && revealedRouteLiveState == null) return

    Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        val timeSeconds = frameNanos / 1_000_000_000f
        layoutMetrics.segments.forEach { segment ->
            if (!previousStopsRevealed && segment.index < hiddenBeforeIndex) {
                return@forEach
            }

            val segmentState = when {
                previousStopsRevealed && segment.index < hiddenBeforeIndex -> revealedRouteLiveState
                else -> liveState
            } ?: return@forEach
            val segmentShowsProgress = when {
                previousStopsRevealed && segment.index < hiddenBeforeIndex -> true
                else -> showsLiveProgress
            }
            if (!segmentShowsProgress) {
                return@forEach
            }

            val phase = segmentState.phaseOnSegment(segment.index) ?: return@forEach
            val fillWidthPx = segment.widthPx * segmentState.easedSegmentProgress(segment.index)
            val intensity = segmentState.particleIntensity(segment.index)
            if (fillWidthPx <= 12f || intensity <= 0.06f || phase == TimelineTrainPhase.Docked) {
                return@forEach
            }

            val visibleWidthPx = max(fillWidthPx - 5f, 1f)
            val cycleSeconds = phase.cycleSeconds
            val cycleProgress = ((timeSeconds % cycleSeconds) / cycleSeconds).clamp01()
            val accent = phase.railGlowColor()
            val phaseBoost = when (phase) {
                TimelineTrainPhase.Departing -> 1.22f
                TimelineTrainPhase.Approaching -> 1.08f
                TimelineTrainPhase.InTransit -> 1.08f
                TimelineTrainPhase.Docked -> 0f
            }

            repeat(4) { index ->
                val localProgress = (cycleProgress + (index / 4f)) % 1f
                val fadeIn = min(localProgress / 0.22f, 1f)
                val fadeOut = min((1f - localProgress) / 0.30f, 1f)
                val opacity = (0.18f + (0.46f * min(fadeIn, fadeOut))) * intensity * phaseBoost
                val widthPx = 5.2f + ((index % 2) * 1.4f)
                val heightPx = 2.2f + ((index % 2) * 0.35f)
                val x = max(segment.startCenterXPx, segment.startCenterXPx + (localProgress * visibleWidthPx))
                val topLeftX = x - (widthPx / 2f)
                val topLeftY = canvasMetrics.trackYPx - (heightPx / 2f)

                drawRoundRect(
                    color = accent.copy(alpha = opacity * 0.16f),
                    topLeft = Offset(topLeftX - 2.2f, topLeftY - 1.0f),
                    size = Size(widthPx + 4.4f, heightPx + 2.0f),
                    cornerRadius = CornerRadius(heightPx + 2.0f, heightPx + 2.0f),
                )

                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = opacity * 0.38f),
                            accent.copy(alpha = opacity * 0.98f),
                            Color.White.copy(alpha = opacity * 0.78f),
                        ),
                    ),
                    topLeft = Offset(topLeftX - 1.2f, topLeftY - 0.5f),
                    size = Size(widthPx + 2.4f, heightPx + 1f),
                    cornerRadius = CornerRadius(heightPx + 1f, heightPx + 1f),
                )

                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = opacity * 0.48f),
                            accent.copy(alpha = (opacity * 1.18f).coerceAtMost(1f)),
                            Color.White.copy(alpha = opacity * 0.96f),
                        ),
                    ),
                    topLeft = Offset(topLeftX, topLeftY),
                    size = Size(widthPx, heightPx),
                    cornerRadius = CornerRadius(heightPx, heightPx),
                )
            }
        }
    }
}

@Composable
private fun TimelineNode(
    stop: TimelineStop,
    centerXPx: Float,
    totalWidthPx: Float,
    state: TimelineNodeState,
    activeTransfer: Float,
    frameNanos: Long,
    visualMetrics: TimelineVisualMetrics,
    canvasMetrics: TimelineCanvasMetrics,
    hidden: Boolean,
) {
    val tokens = ThsrDesignTokens
    val density = LocalDensity.current
    val containerPx = with(density) { visualMetrics.nodeContainer.toPx() }
    val resolvedLabelWidth = if (stop.role == TimelineStopRole.Intermediate) {
        visualMetrics.labelWidth
    } else {
        visualMetrics.labelWidth + 8.dp
    }
    val labelWidthPx = with(density) { resolvedLabelWidth.toPx() }
    val visibleAlpha by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
        label = "timeline-node-visibility",
    )
    val nodePalette = remember(state, stop.role, activeTransfer) {
        state.nodePalette(role = stop.role, activeTransfer = activeTransfer)
    }
    val attentionRing = remember(state, nodePalette.ringColor, activeTransfer) {
        state.attentionRingVisual(
            ringColor = nodePalette.ringColor,
            activeTransfer = activeTransfer,
        )
    }
    val nodeLeftPx = centerXPx - (containerPx / 2f)
    val nodeTopPx = canvasMetrics.trackYPx - (containerPx / 2f)
    val labelLeftPx = (centerXPx - (labelWidthPx / 2f)).coerceIn(0f, max(0f, totalWidthPx - labelWidthPx))
    val emphasized = state in setOf(
        TimelineNodeState.DepartingSoon,
        TimelineNodeState.Approaching,
        TimelineNodeState.ArrivingSoon,
        TimelineNodeState.Stopped,
        TimelineNodeState.Arrived,
    ) || activeTransfer > 0.22f

    Box(
        modifier = Modifier
            .offset { IntOffset(nodeLeftPx.roundToInt(), nodeTopPx.roundToInt()) }
            .size(visualMetrics.nodeContainer)
            .alpha(visibleAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(visualMetrics.nodeContainer)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val nodeRadius = with(density) { nodePalette.size.toPx() } / 2f
            val ringWave = timelinePulseWave(
                frameNanos = frameNanos,
                cycleSeconds = 2.8f,
                phaseSeed = stop.station.sortIndex * 0.17f,
            )
            val activeGlowRadius = nodeRadius + (5.2f * activeTransfer)
            if (activeTransfer > 0.01f) {
                drawCircle(
                    color = nodePalette.ringColor.copy(alpha = 0.24f * activeTransfer),
                    radius = activeGlowRadius + 0.8f,
                    center = center,
                )
            }
            if (attentionRing != null) {
                val ringBaseSizePx = with(density) { attentionRing.baseSize.toPx() }
                val ringExpandSizePx = with(density) { attentionRing.expandSize.toPx() }
                val ringOpacity = (attentionRing.opacity + (attentionRing.opacityRange * ringWave))
                    .coerceIn(0f, 1f)
                val ringRadius = (ringBaseSizePx + (ringExpandSizePx * ringWave)) / 2f
                drawCircle(
                    color = attentionRing.color.copy(alpha = (ringOpacity * 0.22f).coerceIn(0f, 0.32f)),
                    radius = ringRadius + 1.4f,
                    center = center,
                    style = Stroke(width = attentionRing.lineWidthPx * 1.9f),
                )
                drawCircle(
                    color = attentionRing.color.copy(alpha = ringOpacity),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = attentionRing.lineWidthPx),
                )
            }
            drawCircle(
                color = nodePalette.fillColor,
                radius = nodeRadius,
                center = center,
            )
            drawCircle(
                color = nodePalette.strokeColor,
                radius = nodeRadius,
                center = center,
                style = Stroke(width = nodePalette.strokeWidthPx),
            )
            if (state.showsInnerDot()) {
                drawCircle(
                    color = tokens.colors.textPrimary.copy(alpha = 0.92f),
                    radius = nodeRadius * 0.28f,
                    center = center,
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .offset { IntOffset(labelLeftPx.roundToInt(), canvasMetrics.labelTopPx.roundToInt()) }
            .width(resolvedLabelWidth)
            .alpha(visibleAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val isIntermediateStop = stop.role == TimelineStopRole.Intermediate
        val labelTransfer = if (emphasized) {
            max(activeTransfer, 0.54f)
        } else {
            activeTransfer
        }.clamp01()
        val timeBaseStyle = if (emphasized) {
            if (isIntermediateStop) {
                tokens.typography.timelineTime.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                tokens.typography.timelineTime.copy(fontSize = 13.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (isIntermediateStop) {
            tokens.typography.timelineTime.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
        } else {
            tokens.typography.timelineTime.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        val stationBaseStyle = if (emphasized) {
            if (isIntermediateStop) {
                tokens.typography.timelineStation.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                tokens.typography.timelineStation.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (isIntermediateStop) {
            tokens.typography.timelineStation.copy(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)
        } else {
            tokens.typography.timelineStation.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal)
        }
        val timeEmphasisStyle = if (isIntermediateStop) {
            tokens.typography.timelineTime.copy(fontSize = 11.8.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
        } else {
            tokens.typography.timelineTime.copy(fontSize = 13.8.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        val stationEmphasisStyle = if (isIntermediateStop) {
            tokens.typography.timelineStation.copy(fontSize = 10.7.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
        } else {
            tokens.typography.timelineStation.copy(fontSize = 11.7.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val timeRoleOpacityBias = if (isIntermediateStop) 0.78f else 1f
        val stationRoleOpacityBias = if (isIntermediateStop) 0.74f else 1f
        TimelineNodeLabelText(
            text = stop.displayTime(),
            baseColor = nodePalette.timeColor.copy(alpha = nodePalette.timeColor.alpha * 0.88f * timeRoleOpacityBias),
            emphasisColor = nodePalette.timeColor.copy(alpha = nodePalette.timeColor.alpha * timeRoleOpacityBias),
            baseStyle = timeBaseStyle,
            emphasisStyle = timeEmphasisStyle,
            emphasisAlpha = 0.88f * labelTransfer,
            overflow = TextOverflow.Clip,
        )
        TimelineNodeLabelText(
            text = stop.station.localName,
            baseColor = nodePalette.stationColor.copy(alpha = nodePalette.stationColor.alpha * 0.88f * stationRoleOpacityBias),
            emphasisColor = nodePalette.stationColor.copy(alpha = nodePalette.stationColor.alpha * stationRoleOpacityBias),
            baseStyle = stationBaseStyle,
            emphasisStyle = stationEmphasisStyle,
            emphasisAlpha = 0.82f * labelTransfer,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimelineNodeLabelText(
    text: String,
    baseColor: Color,
    emphasisColor: Color,
    baseStyle: TextStyle,
    emphasisStyle: TextStyle,
    emphasisAlpha: Float,
    overflow: TextOverflow,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = baseColor,
            style = baseStyle,
            maxLines = 1,
            overflow = overflow,
        )
        Text(
            text = text,
            color = emphasisColor.copy(alpha = (emphasisColor.alpha * emphasisAlpha).coerceIn(0f, 1f)),
            style = emphasisStyle,
            maxLines = 1,
            overflow = overflow,
        )
    }
}

private fun buildTimelineLayout(
    stops: List<TimelineStop>,
    anchorStopIndex: Int,
    layoutProfile: ThsrLayoutProfile,
    visualMetrics: TimelineVisualMetrics,
    density: Density,
    viewportWidthPx: Float,
): TimelineLayoutMetrics {
    val leadingInsetPx = with(density) { visualMetrics.leadingInset.toPx() }
    val trailingInsetPx = with(density) { visualMetrics.trailingInset.toPx() }
    val widestLabelWidthPx = with(density) { (visualMetrics.labelWidth + 8.dp).toPx() }
    val nodeContainerPx = with(density) { visualMetrics.nodeContainer.toPx() }
    val nodeWidthPx = with(density) { layoutProfile.timelineNodeWidth.toPx() }
    val unitWidthPx = with(density) { layoutProfile.timelineSegmentUnitWidth.toPx() }
    val segmentCount = stops.lastIndex
    val fontScale = density.fontScale
    val minReadableSpacingPx = max(
        widestLabelWidthPx * if (fontScale >= 1.2f) 1.24f else 1.14f,
        max(nodeContainerPx * 1.42f, nodeWidthPx * 1.20f),
    )
    val minSegmentWidthPx = max(
        unitWidthPx * if (layoutProfile.isLargeFont) 2.4f else 2.2f,
        minReadableSpacingPx,
    )
    val baseSegmentWidthsPx = List(segmentCount) { index ->
        val stationGap = abs(stops[index + 1].station.sortIndex - stops[index].station.sortIndex)
            .coerceAtLeast(1)
        max(minSegmentWidthPx, stationGap * unitWidthPx)
    }
    val segmentWidthsPx = if (segmentCount <= 0) {
        emptyList()
    } else {
        val visibleSegmentStartIndex = anchorStopIndex.coerceIn(0, segmentCount)
        val visibleSegmentCount = (segmentCount - visibleSegmentStartIndex).coerceAtLeast(0)
        if (visibleSegmentCount == 0) {
            baseSegmentWidthsPx
        } else {
            val availableVisibleTrackWidthPx = (viewportWidthPx - leadingInsetPx - trailingInsetPx)
                .coerceAtLeast(0f)
            val visibleBaseTrackWidthPx = baseSegmentWidthsPx
                .drop(visibleSegmentStartIndex)
                .fold(0f) { acc, widthPx -> acc + widthPx }
            val visibleScale = if (visibleBaseTrackWidthPx > 0f && availableVisibleTrackWidthPx > visibleBaseTrackWidthPx) {
                availableVisibleTrackWidthPx / visibleBaseTrackWidthPx
            } else {
                1f
            }
            baseSegmentWidthsPx.mapIndexed { index, widthPx ->
                if (index >= visibleSegmentStartIndex) {
                    widthPx * visibleScale
                } else {
                    widthPx
                }
            }
        }
    }
    var currentCenterXPx = leadingInsetPx
    val nodeCenters = mutableListOf(currentCenterXPx)
    val segments = mutableListOf<TimelineSegmentMetrics>()

    segmentWidthsPx.forEachIndexed { index, widthPx ->
        val nextCenterXPx = currentCenterXPx + widthPx
        segments += TimelineSegmentMetrics(
            index = index,
            startCenterXPx = currentCenterXPx,
            endCenterXPx = nextCenterXPx,
            widthPx = widthPx,
        )
        currentCenterXPx = nextCenterXPx
        nodeCenters += currentCenterXPx
    }

    val trackContentWidthPx = currentCenterXPx + trailingInsetPx

    return TimelineLayoutMetrics(
        leadingInsetPx = leadingInsetPx,
        trailingInsetPx = trailingInsetPx,
        nodeCentersPx = nodeCenters,
        segments = segments,
        totalWidthPx = max(viewportWidthPx, trackContentWidthPx),
    )
}

private fun timelineVisualMetrics(layoutProfile: ThsrLayoutProfile): TimelineVisualMetrics = when {
    layoutProfile.widthSizeClass == WindowWidthSizeClass.Expanded || layoutProfile.isLandscape -> TimelineVisualMetrics(
        viewportHeight = 110.dp,
        nodeContainer = 28.dp,
        labelWidth = layoutProfile.timelineLabelWidth + 8.dp,
        labelTop = 54.dp,
        markerTop = 10.dp,
        markerHeight = 10.dp,
        trackY = 34.dp,
        leadingInset = ThsrDesignTokens.timeline.leadingInset,
        trailingInset = 40.dp,
    )

    layoutProfile.isLargeFont -> TimelineVisualMetrics(
        viewportHeight = 104.dp,
        nodeContainer = 28.dp,
        labelWidth = layoutProfile.timelineLabelWidth + 8.dp,
        labelTop = 52.dp,
        markerTop = 9.dp,
        markerHeight = 10.dp,
        trackY = 33.dp,
        leadingInset = ThsrDesignTokens.timeline.leadingInset,
        trailingInset = 38.dp,
    )

    else -> TimelineVisualMetrics(
        viewportHeight = 96.dp,
        nodeContainer = 26.dp,
        labelWidth = layoutProfile.timelineLabelWidth + 6.dp,
        labelTop = 49.dp,
        markerTop = 8.dp,
        markerHeight = 9.dp,
        trackY = 31.dp,
        leadingInset = ThsrDesignTokens.timeline.leadingInset,
        trailingInset = 36.dp,
    )
}

private fun fallbackTimelineStops(option: TrainOption): List<TimelineStop> = listOf(
    TimelineStop(
        station = option.origin,
        arrivalTime = null,
        departureTime = option.departureTime,
    ),
    TimelineStop(
        station = option.destination,
        arrivalTime = option.arrivalTime,
        departureTime = null,
    ),
)

private fun TimelineTrainPhase.accentColor(): Color = when (this) {
    TimelineTrainPhase.InTransit -> ThsrDesignTokens.colors.primaryBlue
    TimelineTrainPhase.Departing -> timelineDepartingCyan
    TimelineTrainPhase.Approaching -> timelineApproachingAmber
    TimelineTrainPhase.Docked -> ThsrDesignTokens.colors.primaryBlue.copy(alpha = 0.96f)
}

private fun TimelineTrainPhase.tailAccentColor(): Color = when (this) {
    TimelineTrainPhase.InTransit -> ThsrDesignTokens.colors.primaryBlue.copy(alpha = 0.72f)
    TimelineTrainPhase.Departing -> timelineDepartingCyan.copy(alpha = 0.76f)
    TimelineTrainPhase.Approaching -> timelineApproachingAmber.copy(alpha = 0.72f)
    TimelineTrainPhase.Docked -> ThsrDesignTokens.colors.primaryBlue.copy(alpha = 0.70f)
}

private fun TimelineTrainPhase.railAccentColor(): Color = when (this) {
    TimelineTrainPhase.InTransit -> timelineCruiseRailBlue
    else -> accentColor()
}

private fun TimelineTrainPhase.railTailAccentColor(): Color = when (this) {
    TimelineTrainPhase.InTransit -> timelineCruiseRailTailBlue
    else -> tailAccentColor()
}

private fun TimelineTrainPhase.railGlowColor(): Color = when (this) {
    TimelineTrainPhase.InTransit -> timelineCruiseRailGlowBlue
    else -> accentColor()
}

private fun TimelineStatusTone.color(): Color = when (this) {
    TimelineStatusTone.Blue -> ThsrDesignTokens.colors.primaryBlue.copy(alpha = 0.96f)
    TimelineStatusTone.Cyan -> timelineDepartingCyan
    TimelineStatusTone.Amber -> timelineApproachingAmber
}

private fun TimelineNodeState.showsInnerDot(): Boolean = this == TimelineNodeState.Stopped || this == TimelineNodeState.Arrived

private data class TimelineNodePalette(
    val size: Dp,
    val fillColor: Color,
    val strokeColor: Color,
    val ringColor: Color,
    val strokeWidthPx: Float,
    val timeColor: Color,
    val stationColor: Color,
)

private fun TimelineNodeState.nodePalette(
    role: TimelineStopRole,
    activeTransfer: Float,
): TimelineNodePalette {
    val tokens = ThsrDesignTokens
    val highlightedEndpoint = role != TimelineStopRole.Intermediate
    val idleSize = if (highlightedEndpoint) tokens.timeline.idleNodeSize + 1.dp else tokens.timeline.idleNodeSize
    val nextSize = if (highlightedEndpoint) tokens.timeline.nextNodeSize + 1.dp else tokens.timeline.nextNodeSize
    val activeSize = if (highlightedEndpoint) tokens.timeline.activeNodeSize + 1.dp else tokens.timeline.activeNodeSize
    val timePrimary = tokens.colors.textPrimary
    val timeSecondary = tokens.colors.textSecondary
    val stationSecondary = tokens.colors.textTertiary
    return when (this) {
        TimelineNodeState.Station -> TimelineNodePalette(
            size = idleSize,
            fillColor = Color.Transparent,
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.34f),
            ringColor = tokens.colors.primaryBlue,
            strokeWidthPx = 1.1f,
            timeColor = timeSecondary.copy(alpha = 0.74f),
            stationColor = stationSecondary.copy(alpha = 0.74f),
        )

        TimelineNodeState.Passed -> TimelineNodePalette(
            size = idleSize - 1.dp,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.12f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.22f),
            ringColor = tokens.colors.primaryBlue,
            strokeWidthPx = 1f,
            timeColor = timeSecondary.copy(alpha = 0.60f),
            stationColor = stationSecondary.copy(alpha = 0.58f),
        )

        TimelineNodeState.Next -> TimelineNodePalette(
            size = nextSize,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.16f + (0.10f * activeTransfer)),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.82f + (0.10f * activeTransfer)),
            ringColor = tokens.colors.primaryBlue,
            strokeWidthPx = 1.45f + (0.20f * activeTransfer),
            timeColor = timePrimary.copy(alpha = 0.78f + (0.18f * activeTransfer)),
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.82f + (0.10f * activeTransfer)),
        )

        TimelineNodeState.Standby -> TimelineNodePalette(
            size = nextSize,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.28f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.70f),
            ringColor = tokens.colors.primaryBlue,
            strokeWidthPx = 1.45f,
            timeColor = tokens.colors.textSecondary.copy(alpha = 0.88f),
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.76f),
        )

        TimelineNodeState.DepartingSoon -> TimelineNodePalette(
            size = activeSize,
            fillColor = timelineDepartingCyan.copy(alpha = 0.18f + (0.06f * activeTransfer)),
            strokeColor = timelineDepartingCyan.copy(alpha = 0.92f),
            ringColor = timelineDepartingCyan,
            strokeWidthPx = 1.65f,
            timeColor = timePrimary,
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.92f),
        )

        TimelineNodeState.Approaching -> TimelineNodePalette(
            size = nextSize,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.20f + (0.14f * activeTransfer)),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.82f + (0.10f * activeTransfer)),
            ringColor = tokens.colors.primaryBlue,
            strokeWidthPx = 1.58f + (0.20f * activeTransfer),
            timeColor = timePrimary.copy(alpha = 0.78f + (0.18f * activeTransfer)),
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.90f + (0.08f * activeTransfer)),
        )

        TimelineNodeState.ArrivingSoon -> TimelineNodePalette(
            size = activeSize,
            fillColor = timelineApproachingAmber.copy(alpha = 0.16f + (0.14f * activeTransfer)),
            strokeColor = timelineApproachingAmber.copy(alpha = 0.92f),
            ringColor = timelineApproachingAmber,
            strokeWidthPx = 1.74f + (0.22f * activeTransfer),
            timeColor = timePrimary,
            stationColor = timePrimary,
        )

        TimelineNodeState.Stopped -> TimelineNodePalette(
            size = activeSize + 1.dp,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.92f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.96f),
            ringColor = tokens.colors.primaryBlue,
            strokeWidthPx = 1.8f,
            timeColor = timePrimary,
            stationColor = timePrimary,
        )

        TimelineNodeState.Arrived -> TimelineNodePalette(
            size = activeSize + 1.dp,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.88f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.94f),
            ringColor = tokens.colors.primaryBlue,
            strokeWidthPx = 1.7f,
            timeColor = timePrimary,
            stationColor = timePrimary,
        )
    }
}

private fun TimelineNodeState.attentionRingVisual(
    ringColor: Color,
    activeTransfer: Float,
): TimelineAttentionRingVisual? {
    val clampedTransfer = activeTransfer.clamp01()
    val baseVisual = when (this) {
        TimelineNodeState.Standby -> TimelineAttentionRingVisual(
            color = ringColor,
            baseSize = 16.5.dp,
            expandSize = 6.0.dp,
            opacity = 0.13f,
            opacityRange = 0.07f,
            lineWidthPx = 1.9f,
        )

        TimelineNodeState.DepartingSoon -> TimelineAttentionRingVisual(
            color = ringColor,
            baseSize = 16.5.dp,
            expandSize = 6.2.dp,
            opacity = 0.18f,
            opacityRange = 0.09f,
            lineWidthPx = 2.0f,
        )

        TimelineNodeState.Approaching -> TimelineAttentionRingVisual(
            color = ringColor,
            baseSize = 16.5.dp,
            expandSize = 6.0.dp,
            opacity = 0.13f,
            opacityRange = 0.07f,
            lineWidthPx = 1.9f,
        )

        TimelineNodeState.Next -> TimelineAttentionRingVisual(
            color = ringColor,
            baseSize = 16.5.dp,
            expandSize = 5.8.dp,
            opacity = 0.10f,
            opacityRange = 0.05f,
            lineWidthPx = 1.8f,
        )

        TimelineNodeState.ArrivingSoon -> TimelineAttentionRingVisual(
            color = ringColor,
            baseSize = 16.5.dp,
            expandSize = 6.3.dp,
            opacity = 0.20f,
            opacityRange = 0.10f,
            lineWidthPx = 2.05f,
        )

        TimelineNodeState.Stopped -> TimelineAttentionRingVisual(
            color = ringColor,
            baseSize = 16.5.dp,
            expandSize = 6.1.dp,
            opacity = 0.17f,
            opacityRange = 0.08f,
            lineWidthPx = 2.0f,
        )

        TimelineNodeState.Arrived -> TimelineAttentionRingVisual(
            color = ringColor,
            baseSize = 16.5.dp,
            expandSize = 5.8.dp,
            opacity = 0.10f,
            opacityRange = 0.05f,
            lineWidthPx = 1.8f,
        )

        TimelineNodeState.Station,
        TimelineNodeState.Passed -> null
    } ?: return null

    return baseVisual.copy(
        baseSize = (16.5f + (1.3f * clampedTransfer)).dp,
        expandSize = max(3.2f, baseVisual.expandSize.value - (1.0f * clampedTransfer)).dp,
        opacity = (baseVisual.opacity + (0.06f * clampedTransfer)).coerceIn(0f, 1f),
        opacityRange = (baseVisual.opacityRange * (1f - (0.32f * clampedTransfer))).coerceAtLeast(0f),
        lineWidthPx = baseVisual.lineWidthPx + (0.32f * clampedTransfer),
    )
}

private fun DrawScope.drawTimelineMarker(
    marker: TimelineMarkerVisual,
    canvasMetrics: TimelineCanvasMetrics,
    frameNanos: Long,
) {
    val accent = marker.phase.accentColor()
    val tailAccent = marker.phase.tailAccentColor()
    val wave = timelinePulseWave(frameNanos = frameNanos, cycleSeconds = marker.phase.cycleSeconds)
    val arrivalTransfer = marker.arrivalTransfer.clamp01()
    val arrivalScale = 1f - (0.08f * arrivalTransfer)
    val glowOpacityFactor = 1f - (0.24f * arrivalTransfer)
    val shellOpacityFactor = 1f - (0.10f * arrivalTransfer)
    val bodyScale = 0.998f + (0.0055f * wave)
    val bodyYScale = 1f + (0.006f * wave)
    val glowScaleX = 1f + ((0.018f - (0.005f * arrivalTransfer)) * wave)
    val glowScaleY = 1f + (0.014f * wave)
    val shellWidthPx = marker.motion.headWidthPx * arrivalScale
    val shellHeightPx = marker.motion.headHeightPx * arrivalScale
    val bodyWidthPx = shellWidthPx * bodyScale
    val bodyHeightPx = shellHeightPx * bodyYScale
    val tailWidthPx = marker.motion.tailWidthPx
    val markerRailOverlapPx = max(
        canvasMetrics.activeLineHeightPx * 1.15f,
        shellHeightPx * when (marker.phase) {
            TimelineTrainPhase.Departing -> 0.28f
            TimelineTrainPhase.InTransit -> 0.24f
            TimelineTrainPhase.Approaching -> 0.26f
            TimelineTrainPhase.Docked -> 0.30f
        },
    )
    val centerYPx = canvasMetrics.trackYPx - ((shellHeightPx / 2f) - markerRailOverlapPx)
    val shellRightPx = marker.centerXPx + (shellWidthPx / 2f)
    val shellLeftPx = shellRightPx - bodyWidthPx
    val shellTopPx = centerYPx - (bodyHeightPx / 2f)
    val effectiveTailLengthPx = min(
        tailWidthPx.coerceAtLeast(0f),
        (marker.centerXPx - marker.trackStartXPx).coerceAtLeast(0f),
    )
    val trailWidthPx = effectiveTailLengthPx + (shellWidthPx * 0.12f)
    val trailHeightPx = max(2.8f, shellHeightPx - 4f)
    val trailGlowHeightPx = trailHeightPx + 2.8f
    val trailStartXPx = marker.centerXPx - effectiveTailLengthPx
    val trailTopPx = centerYPx - (trailHeightPx / 2f)
    val trailGlowTopPx = centerYPx - (trailGlowHeightPx / 2f)
    val trailOpacity = when (marker.phase) {
        TimelineTrainPhase.Departing -> 0.28f + (0.12f * wave)
        TimelineTrainPhase.InTransit -> 0.12f + (0.04f * wave)
        TimelineTrainPhase.Approaching -> 0.16f * (1f - (0.35f * arrivalTransfer))
        TimelineTrainPhase.Docked -> 0f
    }
    val trailGlowOpacity = when (marker.phase) {
        TimelineTrainPhase.Departing -> 0.22f + (0.06f * wave)
        TimelineTrainPhase.InTransit -> 0.09f + (0.03f * wave)
        TimelineTrainPhase.Approaching -> 0.14f * (1f - (0.48f * arrivalTransfer))
        TimelineTrainPhase.Docked -> 0f
    }

    if (trailWidthPx > 1.5f && trailOpacity > 0f) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    tailAccent.copy(alpha = 0f),
                    tailAccent.copy(alpha = trailGlowOpacity * marker.opacity),
                    accent.copy(alpha = (trailGlowOpacity + 0.06f) * marker.opacity * glowOpacityFactor),
                ),
                startX = trailStartXPx,
                endX = trailStartXPx + trailWidthPx,
            ),
            topLeft = Offset(
                x = trailStartXPx,
                y = trailGlowTopPx,
            ),
            size = Size(trailWidthPx, trailGlowHeightPx),
            cornerRadius = CornerRadius(trailGlowHeightPx, trailGlowHeightPx),
        )

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    tailAccent.copy(alpha = 0f),
                    tailAccent.copy(alpha = trailOpacity * 0.72f * marker.opacity),
                    accent.copy(alpha = trailOpacity * marker.opacity * shellOpacityFactor),
                ),
                startX = trailStartXPx,
                endX = trailStartXPx + trailWidthPx,
            ),
            topLeft = Offset(trailStartXPx, trailTopPx),
            size = Size(trailWidthPx, trailHeightPx),
            cornerRadius = CornerRadius(trailHeightPx, trailHeightPx),
        )
    }

    val glowWidthPx = (shellWidthPx + 2.2f) * glowScaleX
    val glowHeightPx = (shellHeightPx + 1.8f) * glowScaleY
    val glowLeftPx = marker.centerXPx - (glowWidthPx / 2f)
    val glowTopPx = centerYPx - (glowHeightPx / 2f)

    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                tailAccent.copy(alpha = 0.02f * marker.opacity * glowOpacityFactor),
                accent.copy(alpha = (0.04f + (0.02f * wave)) * marker.opacity * glowOpacityFactor),
                accent.copy(alpha = (0.10f + (0.02f * wave)) * marker.opacity * glowOpacityFactor),
            ),
            startX = glowLeftPx,
            endX = glowLeftPx + glowWidthPx,
        ),
        topLeft = Offset(glowLeftPx, glowTopPx),
        size = Size(glowWidthPx, glowHeightPx),
        cornerRadius = CornerRadius(glowHeightPx, glowHeightPx),
    )

    drawRoundRect(
        color = accent.copy(alpha = (0.10f + (0.04f * wave)) * marker.opacity * glowOpacityFactor),
        topLeft = Offset(shellLeftPx - 1.1f, shellTopPx - 0.7f),
        size = Size(bodyWidthPx + 2.2f, bodyHeightPx + 1.4f),
        cornerRadius = CornerRadius(bodyHeightPx, bodyHeightPx),
    )

    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                tailAccent.copy(alpha = 0.34f * marker.opacity * shellOpacityFactor),
                accent.copy(alpha = 0.56f * marker.opacity * shellOpacityFactor),
                Color.White.copy(alpha = 0.78f * marker.opacity * shellOpacityFactor),
                Color.White.copy(alpha = 0.94f * marker.opacity * shellOpacityFactor),
            ),
            startX = shellLeftPx,
            endX = shellLeftPx + bodyWidthPx,
        ),
        topLeft = Offset(shellLeftPx, shellTopPx),
        size = Size(bodyWidthPx, bodyHeightPx),
        cornerRadius = CornerRadius(bodyHeightPx, bodyHeightPx),
    )

    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                tailAccent.copy(alpha = 0.36f * marker.opacity * shellOpacityFactor),
                accent.copy(alpha = 0.44f * marker.opacity * shellOpacityFactor),
                Color.White.copy(alpha = 0.72f * marker.opacity * shellOpacityFactor),
            ),
            startX = shellLeftPx,
            endX = shellLeftPx + bodyWidthPx,
        ),
        topLeft = Offset(shellLeftPx, shellTopPx),
        size = Size(bodyWidthPx, bodyHeightPx),
        cornerRadius = CornerRadius(bodyHeightPx, bodyHeightPx),
        style = Stroke(width = 1f),
    )

    val rearShadeWidthPx = max(6f, bodyWidthPx * 0.30f)
    val rearShadeHeightPx = max(1f, bodyHeightPx - 2f)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.10f * marker.opacity * shellOpacityFactor),
                Color.Transparent,
            ),
            startX = shellLeftPx + 2f,
            endX = shellLeftPx + 2f + rearShadeWidthPx,
        ),
        topLeft = Offset(shellLeftPx + 2f, centerYPx - (rearShadeHeightPx / 2f)),
        size = Size(rearShadeWidthPx, rearShadeHeightPx),
        cornerRadius = CornerRadius(rearShadeHeightPx, rearShadeHeightPx),
    )

    val frontHighlightWidthPx = max(2.8f, bodyWidthPx * 0.10f)
    val frontHighlightHeightPx = max(4.8f, bodyHeightPx * 0.62f)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f * marker.opacity * shellOpacityFactor),
                Color.White.copy(alpha = 0.50f * marker.opacity * shellOpacityFactor),
                Color.White.copy(alpha = 0.78f * marker.opacity * shellOpacityFactor),
            ),
            startX = shellLeftPx + bodyWidthPx - frontHighlightWidthPx - 2.2f,
            endX = shellLeftPx + bodyWidthPx - 2.2f,
        ),
        topLeft = Offset(
            x = shellLeftPx + bodyWidthPx - frontHighlightWidthPx - 2.2f,
            y = centerYPx - (frontHighlightHeightPx / 2f) - 0.9f,
        ),
        size = Size(frontHighlightWidthPx, frontHighlightHeightPx),
        cornerRadius = CornerRadius(frontHighlightHeightPx, frontHighlightHeightPx),
    )

    val stripeWidthPx = min(2.4f, bodyWidthPx * 0.18f).coerceAtLeast(1.5f)
    val stripeHeightPx = max(1f, bodyHeightPx - 3f)
    drawRoundRect(
        color = accent.copy(alpha = 0.42f * marker.opacity * shellOpacityFactor),
        topLeft = Offset(
            x = shellLeftPx + bodyWidthPx - stripeWidthPx - 0.4f,
            y = centerYPx - (stripeHeightPx / 2f),
        ),
        size = Size(stripeWidthPx, stripeHeightPx),
        cornerRadius = CornerRadius(stripeHeightPx, stripeHeightPx),
    )
}

private fun timelinePulseWave(
    frameNanos: Long,
    cycleSeconds: Float,
    phaseSeed: Float = 0f,
): Float {
    if (frameNanos == 0L) return 0f
    val phase = (((frameNanos / 1_000_000_000f) + phaseSeed) % cycleSeconds) / cycleSeconds
    val cosine = 0.5f - (0.5f * cos(phase * (Math.PI.toFloat() * 2f)))
    return cosine * cosine * (3f - (2f * cosine))
}

private fun Duration.preciseSeconds(): Double = seconds.toDouble() + (nano.toDouble() / 1_000_000_000.0)

private fun TimelineStop.displayTime(): String =
    when (role) {
        TimelineStopRole.Destination ->
            arrivalTime?.let(ThsrFormatters::displayTimetableTime)
                ?: departureTime?.let(ThsrFormatters::displayTimetableTime)
                ?: "--:--"

        TimelineStopRole.Origin,
        TimelineStopRole.Intermediate ->
            departureTime?.let(ThsrFormatters::displayTimetableTime)
                ?: arrivalTime?.let(ThsrFormatters::displayTimetableTime)
                ?: "--:--"
    }

private fun Float.clamp01(): Float = coerceIn(0f, 1f)

private class TimelineLiveState private constructor(
    private val stops: List<TimelineResolvedStop>,
    private val now: LocalDateTime,
    private val markerHeightPx: Float,
    private val originStopIndex: Int,
    private val destinationStopIndex: Int,
) {
    private val departingSoonWindow = Duration.ofMinutes(2)
    private val approachWindow = Duration.ofSeconds(90)
    private val arrivalPulseWindow = Duration.ofSeconds(30)
    private val dockBlendDuration = Duration.ofSeconds(10)
    private val departureBlendWindow = Duration.ofSeconds(12)

    private val originStop: TimelineResolvedStop = stops[originStopIndex]
    private val destinationStop: TimelineResolvedStop = stops[destinationStopIndex]
    private val originDeparture: LocalDateTime = originStop.stationTime
    private val destinationArrival: LocalDateTime = destinationStop.stationTime
    private val phaseState: TimelinePhaseState by lazy(::resolvePhaseState)

    companion object {
        private val visibilityPredepartureWindow = Duration.ofHours(12)
        private val visibilityPostArrivalWindow = Duration.ofMinutes(30)

        fun create(
            stops: List<TimelineStop>,
            travelDate: LocalDate,
            now: LocalDateTime,
            markerHeightPx: Float,
            originStopIndex: Int,
            destinationStopIndex: Int,
        ): TimelineLiveState? {
            if (stops.size < 2) return null
            val resolvedStops = resolveStops(stops = stops, travelDate = travelDate)
            if (resolvedStops.size < 2) return null
            if (originStopIndex !in resolvedStops.indices || destinationStopIndex !in resolvedStops.indices) {
                return null
            }
            if (originStopIndex >= destinationStopIndex) {
                return null
            }
            val originDeparture = resolvedStops[originStopIndex].stationTime
            val destinationArrival = resolvedStops[destinationStopIndex].stationTime
            val visibilityEnd = maxOf(destinationArrival.plus(visibilityPostArrivalWindow), travelDate.plusDays(1).atStartOfDay())
            if (now.isBefore(originDeparture.minus(visibilityPredepartureWindow)) || now.isAfter(visibilityEnd)) {
                return null
            }
            return TimelineLiveState(
                stops = resolvedStops,
                now = now,
                markerHeightPx = markerHeightPx,
                originStopIndex = originStopIndex,
                destinationStopIndex = destinationStopIndex,
            )
        }

        private fun resolveStops(
            stops: List<TimelineStop>,
            travelDate: LocalDate,
        ): List<TimelineResolvedStop> {
            val seedTime = stops.firstNotNullOfOrNull { it.departureTime ?: it.arrivalTime } ?: LocalTime.MIDNIGHT
            var anchor = LocalDateTime.of(travelDate, seedTime).minusMinutes(1)
            var previousStationTime: LocalDateTime? = null
            return stops.mapIndexed { index, stop ->
                val arrival = stop.arrivalTime?.let { resolveDateTime(travelDate, it, anchor) }
                if (arrival != null && arrival.isAfter(anchor)) {
                    anchor = arrival
                }
                val departure = stop.departureTime?.let { resolveDateTime(travelDate, it, anchor) }
                if (departure != null && departure.isAfter(anchor)) {
                    anchor = departure
                }
                val displayTime = stop.departureTime ?: stop.arrivalTime ?: LocalTime.MIDNIGHT
                val stationTime = resolveStationDateTime(
                    travelDate = travelDate,
                    time = displayTime,
                    previous = previousStationTime,
                )
                previousStationTime = stationTime
                val dwellSeconds = when {
                    arrival != null && departure != null -> {
                        val realDwellSeconds = Duration.between(arrival, departure).seconds
                        if (realDwellSeconds > 0) realDwellSeconds else 40L
                    }

                    index in 1 until stops.lastIndex -> 40L
                    else -> 0L
                }
                TimelineResolvedStop(
                    index = index,
                    station = stop.station,
                    arrival = arrival,
                    departure = departure,
                    displayTime = displayTime,
                    stationTime = stationTime,
                    dwellSeconds = dwellSeconds,
                )
            }
        }

        private fun resolveDateTime(
            travelDate: LocalDate,
            time: LocalTime,
            notBefore: LocalDateTime,
        ): LocalDateTime {
            var resolved = LocalDateTime.of(travelDate, time)
            while (resolved.isBefore(notBefore)) {
                resolved = resolved.plusDays(1)
            }
            return resolved
        }

        private fun resolveStationDateTime(
            travelDate: LocalDate,
            time: LocalTime,
            previous: LocalDateTime?,
        ): LocalDateTime {
            var resolved = LocalDateTime.of(travelDate, time)
            while (previous != null && !resolved.isAfter(previous)) {
                resolved = resolved.plusDays(1)
            }
            return resolved
        }
    }

    fun statusPill(): TimelineStatusPillState {
        val resolvedPhase = phaseState
        val currentStop = resolvedPhase.currentStationIndex?.let(stops::getOrNull)
        val nextStop = resolvedPhase.nextStationIndex?.let(stops::getOrNull)

        return when (resolvedPhase.phase) {
            TimelineSemanticPhase.NotDeparted -> TimelineStatusPillState(
                icon = Icons.Outlined.PauseCircleOutline,
                title = "待命",
                detail = "${originStop.station.localName} ${ThsrFormatters.displayTimetableTime(originStop.displayTime)}",
                tone = TimelineStatusTone.Blue,
            )

            TimelineSemanticPhase.AboutToDepart -> TimelineStatusPillState(
                icon = Icons.Outlined.RadioButtonChecked,
                title = "準備發車",
                detail = "${originStop.station.localName} ${ThsrFormatters.displayTimetableTime(originStop.displayTime)}",
                tone = TimelineStatusTone.Cyan,
            )

            TimelineSemanticPhase.Departing -> TimelineStatusPillState(
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                title = "離站中",
                detail = "${currentStop?.station?.localName ?: originStop.station.localName} 開出",
                tone = TimelineStatusTone.Cyan,
            )

            TimelineSemanticPhase.InTransit -> TimelineStatusPillState(
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                title = "行進中",
                detail = "下一站 ${nextStop?.station?.localName ?: destinationStop.station.localName} ${nextStop?.let { ThsrFormatters.displayTimetableTime(it.displayTime) } ?: ""}".trim(),
                tone = TimelineStatusTone.Blue,
            )

            TimelineSemanticPhase.Approaching -> TimelineStatusPillState(
                icon = Icons.Outlined.NearMe,
                title = "接近",
                detail = "${nextStop?.station?.localName ?: destinationStop.station.localName} ${nextStop?.let { ThsrFormatters.displayTimetableTime(it.displayTime) } ?: ""}".trim(),
                tone = TimelineStatusTone.Blue,
            )

            TimelineSemanticPhase.Arriving -> TimelineStatusPillState(
                icon = Icons.Outlined.MyLocation,
                title = "進站中",
                detail = "${nextStop?.station?.localName ?: destinationStop.station.localName} ${nextStop?.let { ThsrFormatters.displayTimetableTime(it.displayTime) } ?: ""}".trim(),
                tone = TimelineStatusTone.Amber,
            )

            TimelineSemanticPhase.Stopped -> {
                val stop = currentStop ?: destinationStop
                val departure = stationDepartureTime(stop.index)
                val remaining = Duration.between(now, departure).seconds.coerceAtLeast(0)
                TimelineStatusPillState(
                    icon = Icons.Outlined.RadioButtonChecked,
                    title = "停靠中",
                    detail = if (remaining > 0) "${stop.station.localName} 剩餘 ${remaining}s" else stop.station.localName,
                    tone = TimelineStatusTone.Cyan,
                )
            }

            TimelineSemanticPhase.Arrived -> TimelineStatusPillState(
                icon = Icons.Outlined.CheckCircleOutline,
                title = "已抵達",
                detail = destinationStop.station.localName,
                tone = TimelineStatusTone.Blue,
            )
        }
    }

    fun focusedSegmentIndex(originStopIndex: Int): Int {
        return max(originStopIndex, phaseState.focusedSegmentIndex)
    }

    fun hasVisibleRailProgress(originStopIndex: Int): Boolean {
        val resolvedPhase = phaseState
        return when (resolvedPhase.phase) {
            TimelineSemanticPhase.NotDeparted,
            TimelineSemanticPhase.AboutToDepart,
            TimelineSemanticPhase.Arrived -> false

            TimelineSemanticPhase.Stopped ->
                resolvedPhase.currentStationIndex?.let { it >= originStopIndex } ?: false

            else ->
                resolvedPhase.activeSegmentIndex?.let { it >= originStopIndex } ?: false
        }
    }

    fun hasLiveActivityBefore(anchorStopIndex: Int): Boolean {
        val resolvedPhase = phaseState
        return when (resolvedPhase.phase) {
            TimelineSemanticPhase.Stopped ->
                resolvedPhase.currentStationIndex?.let { it < anchorStopIndex } ?: false

            TimelineSemanticPhase.Departing,
            TimelineSemanticPhase.InTransit,
            TimelineSemanticPhase.Approaching,
            TimelineSemanticPhase.Arriving ->
                resolvedPhase.activeSegmentIndex?.let { it < anchorStopIndex } ?: false

            else -> false
        }
    }

    fun shouldShowMarker(originStopIndex: Int): Boolean {
        val resolvedPhase = phaseState
        return when (resolvedPhase.phase) {
            TimelineSemanticPhase.Arrived -> false
            TimelineSemanticPhase.NotDeparted,
            TimelineSemanticPhase.AboutToDepart -> this.originStopIndex >= originStopIndex

            TimelineSemanticPhase.Stopped ->
                resolvedPhase.currentStationIndex?.let { it >= originStopIndex } ?: false

            else ->
                resolvedPhase.activeSegmentIndex?.let { it >= originStopIndex } ?: false
        }
    }

    fun particleIntensity(index: Int): Float {
        if (index !in originStopIndex until destinationStopIndex) return 0f
        val start = segmentStart(index)
        val end = stops[index + 1].stationTime
        if (now.isBefore(start) || !now.isBefore(end)) return 0f

        val rawProgress = normalizedProgress(start = start, end = end, current = now)
        val profile = influenceProfile(start = start, end = end)
        return when (profile.phase(rawProgress)) {
            TimelineTrainPhase.Departing -> {
                val launch = profile.departurePhaseProgress(rawProgress)
                0.56f + (0.34f * launch)
            }

            TimelineTrainPhase.Approaching -> {
                val approach = profile.approachPhaseProgress(rawProgress)
                0.34f + (0.20f * (1f - approach))
            }

            TimelineTrainPhase.InTransit -> 0.42f
            TimelineTrainPhase.Docked -> 0f
        }
    }

    fun easedSegmentProgress(index: Int): Float {
        if (index !in originStopIndex until destinationStopIndex) return 0f
        val start = segmentStart(index)
        val end = stops[index + 1].stationTime
        if (now.isBefore(start)) return 0f
        if (!now.isBefore(end)) return 1f
        val rawProgress = normalizedProgress(start = start, end = end, current = now)
        val profile = influenceProfile(start = start, end = end)
        val magnetic = Duration.between(now, end) <= arrivalPulseWindow
        return profile.remappedProgress(rawProgress, magnetic)
    }

    fun phaseOnSegment(index: Int): TimelineTrainPhase? {
        val resolvedPhase = phaseState
        return if (index == resolvedPhase.activeSegmentIndex) resolvedPhase.motionPhase else null
    }

    fun nodeState(index: Int): TimelineNodeState {
        if (index !in stops.indices) return TimelineNodeState.Station
        if (index < originStopIndex || index > destinationStopIndex) return TimelineNodeState.Station
        val resolvedPhase = phaseState
        val currentIndex = resolvedPhase.currentStationIndex
        val nextIndex = resolvedPhase.nextStationIndex

        if (index == destinationStopIndex && resolvedPhase.phase == TimelineSemanticPhase.Arrived) {
            return TimelineNodeState.Arrived
        }

        return when {
            resolvedPhase.phase == TimelineSemanticPhase.NotDeparted && index == originStopIndex -> TimelineNodeState.Standby
            resolvedPhase.phase == TimelineSemanticPhase.AboutToDepart && index == originStopIndex -> TimelineNodeState.DepartingSoon
            resolvedPhase.phase == TimelineSemanticPhase.Stopped && index == currentIndex -> TimelineNodeState.Stopped
            resolvedPhase.phase == TimelineSemanticPhase.Departing && index == currentIndex -> TimelineNodeState.DepartingSoon
            resolvedPhase.phase == TimelineSemanticPhase.Arriving && index == nextIndex -> TimelineNodeState.ArrivingSoon
            resolvedPhase.phase == TimelineSemanticPhase.Approaching && index == nextIndex -> TimelineNodeState.Approaching
            resolvedPhase.nextStationIndex == index && resolvedPhase.phase in setOf(
                TimelineSemanticPhase.Departing,
                TimelineSemanticPhase.InTransit,
            ) -> TimelineNodeState.Next
            currentIndex != null && index in originStopIndex until currentIndex -> TimelineNodeState.Passed
            resolvedPhase.phase !in setOf(TimelineSemanticPhase.NotDeparted, TimelineSemanticPhase.AboutToDepart) &&
                currentIndex != null &&
                index == currentIndex &&
                resolvedPhase.phase != TimelineSemanticPhase.Stopped &&
                resolvedPhase.phase != TimelineSemanticPhase.Arrived -> TimelineNodeState.Passed
            else -> TimelineNodeState.Station
        }
    }

    fun nodeActivationProgress(index: Int): Float {
        if (index !in stops.indices) return 0f
        if (index < originStopIndex || index > destinationStopIndex) return 0f
        val resolvedPhase = phaseState
        return when {
            resolvedPhase.phase == TimelineSemanticPhase.Arrived && index == destinationStopIndex -> 1f
            resolvedPhase.currentStationIndex == index -> resolvedPhase.currentStationTransfer
            resolvedPhase.nextStationIndex == index -> resolvedPhase.nextStationTransfer
            else -> 0f
        }
    }

    fun marker(layout: TimelineLayoutMetrics): TimelineMarkerVisual? {
        val resolvedPhase = phaseState
        if (resolvedPhase.phase in setOf(TimelineSemanticPhase.NotDeparted, TimelineSemanticPhase.AboutToDepart)) {
            return TimelineMarkerVisual(
                centerXPx = layout.nodeCenterPx(originStopIndex),
                trackStartXPx = layout.nodeCenterPx(originStopIndex),
                phase = TimelineTrainPhase.Docked,
                motion = TimelineMotionMetrics(
                    headWidthPx = markerHeightPx * 1.28f,
                    headHeightPx = markerHeightPx * 1.06f,
                    tailWidthPx = 0f,
                ),
                opacity = if (resolvedPhase.phase == TimelineSemanticPhase.AboutToDepart) 0.84f else 0.76f,
                arrivalTransfer = 0f,
            )
        }

        activeSegmentProgress()?.let { progress ->
            val phase = resolvedPhase.motionPhase ?: progress.motionPhase
            val transitIndex = progress.segmentIndex
            val segment = layout.segment(transitIndex) ?: return null
            return TimelineMarkerVisual(
                centerXPx = segment.position(progress.easedProgress),
                trackStartXPx = segment.startCenterXPx,
                phase = phase,
                motion = motionMetrics(
                    phase = phase,
                    rawProgress = progress.rawProgress,
                    profile = progress.profile,
                ),
                opacity = 1f,
                arrivalTransfer = if (phase == TimelineTrainPhase.Approaching) {
                    progress.profile.arrivalNodeTransfer(progress.rawProgress, progress.magnetic)
                } else {
                    0f
                },
            )
        }

        val stoppedIndex = resolvedPhase.currentStationIndex
        if (stoppedIndex != null) {
            if (resolvedPhase.phase == TimelineSemanticPhase.Stopped) {
                val arrival = stops[stoppedIndex].stationTime
                val departure = stationDepartureTime(stoppedIndex)
                val elapsedSeconds = Duration.between(arrival, now).preciseSeconds().coerceAtLeast(0.0).toFloat()
                val remainingSeconds = Duration.between(now, departure).preciseSeconds().coerceAtLeast(0.0).toFloat()
                val settleProgress = railBlend(elapsedSeconds / dockBlendDuration.preciseSeconds().toFloat())
                val settledOpacity = 1f - (0.22f * settleProgress)
                val departureProgress = railBlend(1f - (remainingSeconds / departureBlendWindow.preciseSeconds().toFloat()))
                val opacity = settledOpacity + ((0.92f - settledOpacity) * departureProgress)
                return TimelineMarkerVisual(
                    centerXPx = layout.nodeCenterPx(stoppedIndex),
                    trackStartXPx = layout.nodeCenterPx((stoppedIndex - 1).coerceAtLeast(originStopIndex)),
                    phase = TimelineTrainPhase.Docked,
                    motion = TimelineMotionMetrics(
                        headWidthPx = markerHeightPx * 1.50f,
                        headHeightPx = markerHeightPx * 1.50f,
                        tailWidthPx = 0f,
                    ),
                    opacity = opacity,
                    arrivalTransfer = 1f,
                )
            }
        }

        return null
    }

    private fun resolvePhaseState(): TimelinePhaseState {
        if (now.isBefore(originDeparture.minus(departingSoonWindow))) {
            return TimelinePhaseState(
                phase = TimelineSemanticPhase.NotDeparted,
                currentStationIndex = originStopIndex,
                nextStationIndex = (originStopIndex + 1).takeIf { it <= destinationStopIndex },
                activeSegmentIndex = null,
                motionPhase = null,
                rawProgress = null,
                currentStationTransfer = 1f,
                nextStationTransfer = 0f,
                defaultAnchorStopIndex = originStopIndex,
                focusedSegmentIndex = originStopIndex.coerceAtMost(stops.lastIndex - 1),
            )
        }

        if (now.isBefore(originDeparture)) {
            return TimelinePhaseState(
                phase = TimelineSemanticPhase.AboutToDepart,
                currentStationIndex = originStopIndex,
                nextStationIndex = (originStopIndex + 1).takeIf { it <= destinationStopIndex },
                activeSegmentIndex = null,
                motionPhase = null,
                rawProgress = null,
                currentStationTransfer = 1f,
                nextStationTransfer = 0.10f,
                defaultAnchorStopIndex = originStopIndex,
                focusedSegmentIndex = originStopIndex.coerceAtMost(stops.lastIndex - 1),
            )
        }

        activeStoppedStationIndex()?.let { stoppedIndex ->
            return TimelinePhaseState(
                phase = TimelineSemanticPhase.Stopped,
                currentStationIndex = stoppedIndex,
                nextStationIndex = (stoppedIndex + 1).takeIf { it <= destinationStopIndex },
                activeSegmentIndex = minOf(stoppedIndex, destinationStopIndex - 1),
                motionPhase = TimelineTrainPhase.Docked,
                rawProgress = null,
                currentStationTransfer = 1f,
                nextStationTransfer = 0.14f,
                defaultAnchorStopIndex = stoppedIndex,
                focusedSegmentIndex = minOf(stoppedIndex, destinationStopIndex - 1),
            )
        }

        activeSegmentProgress()?.let { progress ->
            val remainingSeconds = Duration.between(now, nextStopDisplayDateTime(progress.segmentIndex + 1))
                .preciseSeconds()
                .coerceAtLeast(0.0)
            val phase = when {
                progress.motionPhase == TimelineTrainPhase.Departing -> TimelineSemanticPhase.Departing
                remainingSeconds <= arrivalPulseWindow.preciseSeconds() -> TimelineSemanticPhase.Arriving
                progress.motionPhase == TimelineTrainPhase.Approaching -> TimelineSemanticPhase.Approaching
                else -> TimelineSemanticPhase.InTransit
            }
            val nextTransfer = when (phase) {
                TimelineSemanticPhase.Arriving,
                TimelineSemanticPhase.Approaching ->
                    progress.profile.arrivalNodeTransfer(progress.rawProgress, progress.magnetic)

                else -> 0f
            }
            val currentTransfer = when (phase) {
                TimelineSemanticPhase.Departing ->
                    progress.profile.departureNodeFade(progress.rawProgress).coerceAtLeast(0.26f)

                else -> 0f
            }
            return TimelinePhaseState(
                phase = phase,
                currentStationIndex = progress.segmentIndex,
                nextStationIndex = progress.segmentIndex + 1,
                activeSegmentIndex = progress.segmentIndex,
                motionPhase = progress.motionPhase,
                rawProgress = progress.rawProgress,
                currentStationTransfer = currentTransfer,
                nextStationTransfer = nextTransfer,
                defaultAnchorStopIndex = progress.segmentIndex.coerceIn(originStopIndex, destinationStopIndex),
                focusedSegmentIndex = progress.segmentIndex.coerceIn(originStopIndex, destinationStopIndex - 1),
            )
        }

        return TimelinePhaseState(
            phase = TimelineSemanticPhase.Arrived,
            currentStationIndex = destinationStopIndex,
            nextStationIndex = null,
            activeSegmentIndex = (destinationStopIndex - 1).takeIf { it >= originStopIndex },
            motionPhase = null,
            rawProgress = null,
            currentStationTransfer = 1f,
            nextStationTransfer = 0f,
            defaultAnchorStopIndex = destinationStopIndex,
            focusedSegmentIndex = (destinationStopIndex - 1).coerceAtLeast(originStopIndex),
        )
    }

    private fun activeSegmentProgress(): TimelineActiveSegmentProgress? {
        val transitIndex = activeTransitSegmentIndex() ?: return null
        val start = segmentStart(transitIndex)
        val end = stops[transitIndex + 1].stationTime
        val rawProgress = normalizedProgress(start = start, end = end, current = now)
        val profile = influenceProfile(start = start, end = end)
        val magnetic = Duration.between(now, end) <= arrivalPulseWindow
        return TimelineActiveSegmentProgress(
            segmentIndex = transitIndex,
            rawProgress = rawProgress,
            easedProgress = profile.remappedProgress(rawProgress, magnetic),
            motionPhase = profile.phase(rawProgress),
            profile = profile,
            magnetic = magnetic,
        )
    }

    private fun segmentStart(index: Int): LocalDateTime {
        val stop = stops[index]
        // Mirror iOS: intermediate segments start after the station timeline time plus its dwell hold.
        return if (index == originStopIndex) {
            stop.stationTime
        } else {
            stationDepartureTime(index)
        }
    }

    private fun activeTransitSegmentIndex(): Int? {
        for (index in originStopIndex until destinationStopIndex) {
            val start = segmentStart(index)
            val end = stops[index + 1].stationTime
            if (!now.isBefore(start) && now.isBefore(end)) {
                return index
            }
        }
        return null
    }

    private fun activeStoppedStationIndex(): Int? {
        if (destinationStopIndex - originStopIndex <= 1) return null
        for (index in (originStopIndex + 1) until destinationStopIndex) {
            val arrival = stops[index].stationTime
            val departure = stationDepartureTime(index)
            if (!now.isBefore(arrival) && now.isBefore(departure)) {
                return index
            }
        }
        return null
    }

    private fun normalizedProgress(
        start: LocalDateTime,
        end: LocalDateTime,
        current: LocalDateTime,
    ): Float {
        val totalSeconds = Duration.between(start, end).preciseSeconds().coerceAtLeast(1.0).toFloat()
        val elapsedSeconds = Duration.between(start, current).preciseSeconds().toFloat()
        return (elapsedSeconds / totalSeconds).clamp01()
    }

    private fun influenceProfile(
        start: LocalDateTime,
        end: LocalDateTime,
    ): TimelineInfluenceProfile {
        val durationSeconds = Duration.between(start, end).preciseSeconds().coerceAtLeast(1.0).toFloat()
        return TimelineInfluenceProfile.from(
            durationSeconds = durationSeconds,
            departureWindowSeconds = departureBlendWindow.preciseSeconds().toFloat(),
            approachWindowSeconds = approachWindow.preciseSeconds().toFloat(),
        )
    }

    private fun railBlend(progress: Float): Float {
        val clamped = progress.clamp01()
        return 1f - (1f - clamped).pow(2f)
    }

    private fun motionMetrics(
        phase: TimelineTrainPhase,
        rawProgress: Float,
        profile: TimelineInfluenceProfile,
    ): TimelineMotionMetrics = when (phase) {
        TimelineTrainPhase.Departing -> {
            val launch = profile.departurePhaseProgress(rawProgress)
            val headDiameterPx = markerHeightPx * 1.13f
            TimelineMotionMetrics(
                headWidthPx = headDiameterPx * (1.16f + (0.54f * launch)),
                headHeightPx = headDiameterPx,
                tailWidthPx = markerHeightPx * (1.34f + (0.78f * launch)),
            )
        }

        TimelineTrainPhase.InTransit -> {
            val headDiameterPx = markerHeightPx * 1.09f
            TimelineMotionMetrics(
                headWidthPx = headDiameterPx * 1.58f,
                headHeightPx = headDiameterPx,
                tailWidthPx = markerHeightPx * 0.89f,
            )
        }

        TimelineTrainPhase.Approaching -> {
            val transfer = profile.arrivalNodeTransfer(rawProgress, magnetic = true)
            val headDiameterPx = markerHeightPx * (1.11f - (0.03f * transfer))
            TimelineMotionMetrics(
                headWidthPx = headDiameterPx * (1.44f - (0.24f * transfer)),
                headHeightPx = headDiameterPx,
                tailWidthPx = markerHeightPx * (0.94f - (0.50f * transfer)),
            )
        }

        TimelineTrainPhase.Docked -> TimelineMotionMetrics(
            headWidthPx = markerHeightPx * 1.50f,
            headHeightPx = markerHeightPx * 1.50f,
            tailWidthPx = 0f,
        )
    }

    private fun nextStopDisplayDateTime(index: Int): LocalDateTime =
        stops.getOrNull(index)?.stationTime ?: destinationArrival

    private fun stationDepartureTime(index: Int): LocalDateTime {
        val stop = stops.getOrNull(index) ?: return originDeparture
        return stop.stationTime.plusSeconds(stop.dwellSeconds)
    }
}

private fun lerp(start: Float, end: Float, progress: Float): Float = start + ((end - start) * progress)
