package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

private data class TimelineFrame(
    val now: LocalDateTime,
    val frameNanos: Long,
)

private enum class TimelineTrainPhase(val cycleSeconds: Float) {
    Departing(2.15f),
    InTransit(2.45f),
    Approaching(2.7f),
    Docked(2.8f),
}

private enum class TimelineSemanticPhase {
    NotDeparted,
    AboutToDepart,
    Departing,
    Departed,
    InTransit,
    Approaching,
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

private data class TimelineMarkerVisual(
    val centerXPx: Float,
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
    val frame = rememberTimelineFrame()
    val liveState = remember(stops, option.travelDate, frame.now, canvasMetrics) {
        TimelineLiveState.create(
            stops = stops,
            travelDate = option.travelDate,
            now = frame.now,
            markerHeightPx = canvasMetrics.markerHeightPx,
        )
    }
    val originStopIndex = remember(stops, option.origin) {
        stops.indexOfFirst { it.station == option.origin }
            .takeIf { it >= 0 }
            ?: 0
    }
    val statusPill = remember(liveState) { liveState?.statusPill() }
    val showsRailProgress = remember(liveState) { liveState?.showsRailProgress() ?: false }
    var initialAnchorStopIndex by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableIntStateOf(Int.MIN_VALUE)
    }
    val scrollState = rememberScrollState()

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
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val layoutMetrics = remember(stops, density, layoutProfile, visualMetrics, viewportWidthPx) {
                buildTimelineLayout(
                    stops = stops,
                    layoutProfile = layoutProfile,
                    visualMetrics = visualMetrics,
                    density = density,
                    viewportWidthPx = viewportWidthPx,
                )
            }
            val anchorStopIndex = originStopIndex.coerceIn(0, stops.lastIndex)
            val currentVisibleAnchorStopIndex = (
                liveState?.defaultAnchorStopIndex(anchorStopIndex)
                    ?: anchorStopIndex
                ).coerceIn(anchorStopIndex, stops.lastIndex)
            val hiddenBeforeIndex = if (initialAnchorStopIndex == Int.MIN_VALUE) {
                currentVisibleAnchorStopIndex
            } else {
                initialAnchorStopIndex.coerceIn(anchorStopIndex, stops.lastIndex)
            }
            val focusedSegmentIndex = (
                liveState?.focusedSegmentIndex(hiddenBeforeIndex)
                    ?: hiddenBeforeIndex
                ).coerceIn(0, layoutMetrics.segments.lastIndex)
            val maxOffsetPx = layoutMetrics.maxOffsetPx(viewportWidthPx)
            val initialOffsetPx = remember(hiddenBeforeIndex, maxOffsetPx, viewportWidthPx, layoutMetrics.totalWidthPx) {
                val anchorCenterPx = layoutMetrics.nodeCenterPx(hiddenBeforeIndex)
                (anchorCenterPx - (viewportWidthPx * 0.46f))
                    .coerceIn(0f, maxOffsetPx)
            }
            val revealThresholdPx = with(density) { tokens.timeline.revealThreshold.toPx() }

            var previousStopsRevealed by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
                mutableStateOf(hiddenBeforeIndex <= anchorStopIndex)
            }

            LaunchedEffect(
                option.trainNo,
                option.travelDate,
                option.origin,
                option.destination,
                currentVisibleAnchorStopIndex,
            ) {
                if (initialAnchorStopIndex == Int.MIN_VALUE) {
                    initialAnchorStopIndex = currentVisibleAnchorStopIndex
                }
            }

            LaunchedEffect(initialOffsetPx, option.trainNo, option.travelDate, option.origin, option.destination) {
                previousStopsRevealed = hiddenBeforeIndex <= anchorStopIndex
                scrollState.scrollTo(initialOffsetPx.roundToInt())
            }

            LaunchedEffect(scrollState.value, hiddenBeforeIndex, initialOffsetPx) {
                val revealOffset = (initialOffsetPx - revealThresholdPx).roundToInt().coerceAtLeast(0)
                if (!previousStopsRevealed && hiddenBeforeIndex > anchorStopIndex && scrollState.value <= revealOffset) {
                    previousStopsRevealed = true
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clipToBounds()
                    .horizontalScroll(
                        state = scrollState,
                        enabled = layoutMetrics.totalWidthPx > viewportWidthPx,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { layoutMetrics.totalWidthPx.toDp() })
                        .fillMaxHeight(),
                ) {
                    TimelineRailCanvas(
                        layoutMetrics = layoutMetrics,
                        liveState = liveState,
                        canvasMetrics = canvasMetrics,
                        frameNanos = frame.frameNanos,
                        hiddenBeforeIndex = hiddenBeforeIndex,
                        previousStopsRevealed = previousStopsRevealed,
                        focusedSegmentIndex = focusedSegmentIndex,
                        showsLiveProgress = showsRailProgress,
                    )

                    stops.forEachIndexed { index, stop ->
                        TimelineNode(
                            stop = stop,
                            centerXPx = layoutMetrics.nodeCenterPx(index),
                            totalWidthPx = layoutMetrics.totalWidthPx,
                            state = liveState?.nodeState(index) ?: TimelineNodeState.Station,
                            activeTransfer = liveState?.nodeActivationProgress(index) ?: 0f,
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
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.spacing12,
                vertical = 6.dp,
            ),
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .offset(y = 1.dp)
                        .alpha(0.92f)
                        .width(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(color = tint)
                    }
                }
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
    canvasMetrics: TimelineCanvasMetrics,
    frameNanos: Long,
    hiddenBeforeIndex: Int,
    previousStopsRevealed: Boolean,
    focusedSegmentIndex: Int,
    showsLiveProgress: Boolean,
) {
    val tokens = ThsrDesignTokens
    Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        val baseTrackColor = tokens.colors.primaryBlue.copy(alpha = 0.16f)
        val idleGlowColor = tokens.colors.primaryBlue.copy(alpha = 0.06f)
        layoutMetrics.segments.forEach { segment ->
            if (!previousStopsRevealed && segment.index < hiddenBeforeIndex) {
                return@forEach
            }

            val segmentOpacity = if (segment.index == focusedSegmentIndex) 1f else 0.82f
            drawRoundRect(
                color = baseTrackColor.copy(alpha = baseTrackColor.alpha * segmentOpacity),
                topLeft = Offset(
                    x = segment.startCenterXPx,
                    y = canvasMetrics.trackYPx - (canvasMetrics.baseLineHeightPx / 2f),
                ),
                size = Size(segment.widthPx, canvasMetrics.baseLineHeightPx),
                cornerRadius = CornerRadius(canvasMetrics.baseLineHeightPx, canvasMetrics.baseLineHeightPx),
            )

            val progress = liveState?.easedSegmentProgress(segment.index) ?: 0f
            if (!showsLiveProgress || progress <= 0f) {
                return@forEach
            }

            val phase = liveState?.phaseOnSegment(segment.index) ?: TimelineTrainPhase.InTransit
            val fillWidthPx = segment.widthPx * progress
            val accent = phase.accentColor()
            val tailAccent = phase.tailAccentColor()

            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        tailAccent.copy(alpha = 0.24f * segmentOpacity),
                        tailAccent.copy(alpha = 0.34f * segmentOpacity),
                        accent.copy(alpha = 0.54f * segmentOpacity),
                        accent.copy(alpha = 0.82f * segmentOpacity),
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
                color = accent.copy(alpha = 0.10f * segmentOpacity),
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

        liveState?.marker(layoutMetrics)?.let { marker ->
            if (
                showsLiveProgress &&
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
                    color = nodePalette.ringColor.copy(alpha = 0.16f * activeTransfer),
                    radius = activeGlowRadius,
                    center = center,
                )
            }
            if (nodePalette.ringAlpha > 0f) {
                drawCircle(
                    color = nodePalette.ringColor.copy(alpha = nodePalette.ringAlpha * (0.72f + (0.28f * ringWave))),
                    radius = nodeRadius + 2.8f + (2.0f * ringWave) + (activeTransfer * 1.6f),
                    center = center,
                    style = Stroke(width = nodePalette.strokeWidthPx * 0.92f),
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
        Text(
            text = stop.displayTime(),
            color = if (emphasized) nodePalette.timeColor else nodePalette.timeColor.copy(alpha = 0.86f),
            style = tokens.typography.timelineTime.copy(
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = stop.station.localName,
            color = if (emphasized) nodePalette.stationColor else nodePalette.stationColor.copy(alpha = 0.88f),
            style = tokens.typography.timelineStation.copy(
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberTimelineFrame(): TimelineFrame {
    var frame by remember { mutableStateOf(TimelineFrame(now = LocalDateTime.now(), frameNanos = 0L)) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frameNanos ->
                frame = TimelineFrame(
                    now = LocalDateTime.now(),
                    frameNanos = frameNanos,
                )
            }
        }
    }
    return frame
}

private fun buildTimelineLayout(
    stops: List<TimelineStop>,
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
    val minSegmentWidthPx = max(unitWidthPx * if (layoutProfile.isLargeFont) 2.4f else 2.2f, minReadableSpacingPx)
    val segmentWeights = List(segmentCount) { index ->
        abs(stops[index + 1].station.sortIndex - stops[index].station.sortIndex)
            .coerceAtLeast(1)
            .toFloat()
    }
    val minTrackWidthPx = minSegmentWidthPx * segmentCount
    val targetTrackWidthPx = max(0f, viewportWidthPx - leadingInsetPx - trailingInsetPx)
        .coerceAtLeast(minTrackWidthPx)
    val averageSegmentWidthPx = if (segmentCount > 0) targetTrackWidthPx / segmentCount else minSegmentWidthPx
    val maxSegmentWidthPx = max(
        minSegmentWidthPx + (unitWidthPx * 3.2f),
        averageSegmentWidthPx * 1.7f,
    )
    val segmentWidthsPx = distributeSegmentWidths(
        weights = segmentWeights,
        targetTrackWidthPx = targetTrackWidthPx,
        minSegmentWidthPx = minSegmentWidthPx,
        maxSegmentWidthPx = maxSegmentWidthPx,
    )
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

private fun distributeSegmentWidths(
    weights: List<Float>,
    targetTrackWidthPx: Float,
    minSegmentWidthPx: Float,
    maxSegmentWidthPx: Float,
): List<Float> {
    if (weights.isEmpty()) return emptyList()

    val widths = MutableList(weights.size) { minSegmentWidthPx }
    var remainingExtraPx = (targetTrackWidthPx - (minSegmentWidthPx * weights.size)).coerceAtLeast(0f)
    if (remainingExtraPx <= 0f) {
        return widths
    }

    val remainingIndices = weights.indices.toMutableSet()
    while (remainingExtraPx > 0.5f && remainingIndices.isNotEmpty()) {
        var distributedThisPassPx = 0f
        var remainingWeight = 0f
        remainingIndices.forEach { index -> remainingWeight += weights[index] }
        val safeWeight = remainingWeight.coerceAtLeast(0.0001f)
        val saturatedIndices = mutableListOf<Int>()

        remainingIndices.forEach { index ->
            val sharePx = remainingExtraPx * (weights[index] / safeWeight)
            val capacityPx = (maxSegmentWidthPx - widths[index]).coerceAtLeast(0f)
            val appliedPx = min(sharePx, capacityPx)
            widths[index] += appliedPx
            distributedThisPassPx += appliedPx
            if (capacityPx - appliedPx <= 0.5f) {
                saturatedIndices += index
            }
        }

        if (distributedThisPassPx <= 0.5f) {
            break
        }

        remainingExtraPx -= distributedThisPassPx
        remainingIndices.removeAll(saturatedIndices)
    }

    if (remainingExtraPx > 0.5f) {
        val fallbackExtraPx = remainingExtraPx / widths.size
        for (index in widths.indices) {
            widths[index] += fallbackExtraPx
        }
    }

    return widths
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
    val ringAlpha: Float,
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
            ringAlpha = 0f,
            strokeWidthPx = 1.1f,
            timeColor = timeSecondary.copy(alpha = 0.74f),
            stationColor = stationSecondary.copy(alpha = 0.74f),
        )

        TimelineNodeState.Passed -> TimelineNodePalette(
            size = idleSize - 1.dp,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.12f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.22f),
            ringColor = tokens.colors.primaryBlue,
            ringAlpha = 0f,
            strokeWidthPx = 1f,
            timeColor = timeSecondary.copy(alpha = 0.60f),
            stationColor = stationSecondary.copy(alpha = 0.58f),
        )

        TimelineNodeState.Next -> TimelineNodePalette(
            size = nextSize,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.16f + (0.10f * activeTransfer)),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.82f + (0.10f * activeTransfer)),
            ringColor = tokens.colors.primaryBlue,
            ringAlpha = 0.08f + (0.06f * activeTransfer),
            strokeWidthPx = 1.45f + (0.20f * activeTransfer),
            timeColor = timePrimary.copy(alpha = 0.78f + (0.18f * activeTransfer)),
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.82f + (0.10f * activeTransfer)),
        )

        TimelineNodeState.Standby -> TimelineNodePalette(
            size = nextSize,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.28f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.70f),
            ringColor = tokens.colors.primaryBlue,
            ringAlpha = 0.08f,
            strokeWidthPx = 1.45f,
            timeColor = tokens.colors.textSecondary.copy(alpha = 0.88f),
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.76f),
        )

        TimelineNodeState.DepartingSoon -> TimelineNodePalette(
            size = activeSize,
            fillColor = timelineDepartingCyan.copy(alpha = 0.18f + (0.06f * activeTransfer)),
            strokeColor = timelineDepartingCyan.copy(alpha = 0.92f),
            ringColor = timelineDepartingCyan,
            ringAlpha = 0.13f,
            strokeWidthPx = 1.65f,
            timeColor = timePrimary,
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.92f),
        )

        TimelineNodeState.Approaching -> TimelineNodePalette(
            size = nextSize,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.20f + (0.14f * activeTransfer)),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.82f + (0.10f * activeTransfer)),
            ringColor = tokens.colors.primaryBlue,
            ringAlpha = 0.10f + (0.06f * activeTransfer),
            strokeWidthPx = 1.58f + (0.20f * activeTransfer),
            timeColor = timePrimary.copy(alpha = 0.78f + (0.18f * activeTransfer)),
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.90f + (0.08f * activeTransfer)),
        )

        TimelineNodeState.ArrivingSoon -> TimelineNodePalette(
            size = activeSize,
            fillColor = timelineApproachingAmber.copy(alpha = 0.16f + (0.14f * activeTransfer)),
            strokeColor = timelineApproachingAmber.copy(alpha = 0.92f),
            ringColor = timelineApproachingAmber,
            ringAlpha = 0.16f + (0.08f * activeTransfer),
            strokeWidthPx = 1.74f + (0.22f * activeTransfer),
            timeColor = timePrimary,
            stationColor = timePrimary,
        )

        TimelineNodeState.Stopped -> TimelineNodePalette(
            size = activeSize + 1.dp,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.92f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.96f),
            ringColor = tokens.colors.primaryBlue,
            ringAlpha = 0.11f,
            strokeWidthPx = 1.8f,
            timeColor = timePrimary,
            stationColor = timePrimary,
        )

        TimelineNodeState.Arrived -> TimelineNodePalette(
            size = activeSize + 1.dp,
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.88f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.94f),
            ringColor = tokens.colors.primaryBlue,
            ringAlpha = 0.08f,
            strokeWidthPx = 1.7f,
            timeColor = timePrimary,
            stationColor = timePrimary,
        )
    }
}

private fun DrawScope.drawTimelineMarker(
    marker: TimelineMarkerVisual,
    canvasMetrics: TimelineCanvasMetrics,
    frameNanos: Long,
) {
    val accent = marker.phase.accentColor()
    val tailAccent = marker.phase.tailAccentColor()
    val wave = timelinePulseWave(frameNanos = frameNanos, cycleSeconds = marker.phase.cycleSeconds)
    val scale = when (marker.phase) {
        TimelineTrainPhase.Departing -> 1f + (0.020f * wave)
        TimelineTrainPhase.InTransit -> 1f + (0.015f * wave)
        TimelineTrainPhase.Approaching -> 1f + (0.010f * wave)
        TimelineTrainPhase.Docked -> 1f + (0.008f * wave)
    }
    val headWidthPx = marker.motion.headWidthPx * scale
    val headHeightPx = marker.motion.headHeightPx * (1f + (0.008f * wave))
    val tailWidthPx = marker.motion.tailWidthPx
    val markerRailOverlapPx = max(
        canvasMetrics.activeLineHeightPx * 1.15f,
        headHeightPx * when (marker.phase) {
            TimelineTrainPhase.Departing -> 0.28f
            TimelineTrainPhase.InTransit -> 0.24f
            TimelineTrainPhase.Approaching -> 0.26f
            TimelineTrainPhase.Docked -> 0.30f
        },
    )
    val centerYPx = canvasMetrics.trackYPx - ((headHeightPx / 2f) - markerRailOverlapPx)
    val headLeftPx = marker.centerXPx - (headWidthPx / 2f)
    val headTopPx = centerYPx - (headHeightPx / 2f)
    val tailHeightPx = headHeightPx * 0.70f
    val tailTopPx = centerYPx - (tailHeightPx / 2f)
    val arrivalFade = 1f - (0.24f * marker.arrivalTransfer)

    if (tailWidthPx > 0.5f) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    tailAccent.copy(alpha = 0f),
                    tailAccent.copy(alpha = 0.18f * marker.opacity),
                    accent.copy(alpha = 0.34f * marker.opacity),
                ),
                startX = headLeftPx - tailWidthPx,
                endX = headLeftPx + (headHeightPx * 0.4f),
            ),
            topLeft = Offset(
                x = headLeftPx - tailWidthPx + (headHeightPx * 0.26f),
                y = tailTopPx,
            ),
            size = Size(tailWidthPx, tailHeightPx),
            cornerRadius = CornerRadius(tailHeightPx, tailHeightPx),
            alpha = marker.opacity * arrivalFade,
        )
    }

    drawRoundRect(
        color = accent.copy(alpha = 0.16f * marker.opacity * arrivalFade),
        topLeft = Offset(
            x = headLeftPx - 1.4f,
            y = headTopPx - 1.2f,
        ),
        size = Size(headWidthPx + 2.8f, headHeightPx + 2.4f),
        cornerRadius = CornerRadius(headHeightPx, headHeightPx),
    )

    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                tailAccent.copy(alpha = 0.90f * marker.opacity),
                accent.copy(alpha = 0.96f * marker.opacity),
                Color.White.copy(alpha = 0.94f * marker.opacity),
            ),
            startX = headLeftPx,
            endX = headLeftPx + headWidthPx,
        ),
        topLeft = Offset(headLeftPx, headTopPx),
        size = Size(headWidthPx, headHeightPx),
        cornerRadius = CornerRadius(headHeightPx, headHeightPx),
    )

    drawRoundRect(
        color = Color.White.copy(alpha = 0.24f * marker.opacity * arrivalFade),
        topLeft = Offset(
            x = headLeftPx + (headWidthPx * 0.58f),
            y = headTopPx + 1.2f,
        ),
        size = Size(headWidthPx * 0.18f, headHeightPx - 2.4f),
        cornerRadius = CornerRadius(headHeightPx, headHeightPx),
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
) {
    private val departingSoonWindow = Duration.ofMinutes(2)
    private val approachWindow = Duration.ofSeconds(90)
    private val arrivalPulseWindow = Duration.ofSeconds(30)
    private val dockBlendDuration = Duration.ofSeconds(10)
    private val departureBlendWindow = Duration.ofSeconds(12)

    private val firstDeparture: LocalDateTime = stops.first().departure ?: stops.first().arrival!!
    private val lastArrival: LocalDateTime = stops.last().arrival ?: stops.last().departure!!
    private val phaseState: TimelinePhaseState by lazy(::resolvePhaseState)

    companion object {
        private val visibilityPredepartureWindow = Duration.ofHours(12)
        private val visibilityPostArrivalWindow = Duration.ofMinutes(30)

        fun create(
            stops: List<TimelineStop>,
            travelDate: LocalDate,
            now: LocalDateTime,
            markerHeightPx: Float,
        ): TimelineLiveState? {
            if (stops.size < 2) return null
            val resolvedStops = resolveStops(stops = stops, travelDate = travelDate)
            if (resolvedStops.size < 2) return null
            val firstDeparture = resolvedStops.firstNotNullOfOrNull { it.departure ?: it.arrival } ?: return null
            val lastArrival = resolvedStops.asReversed().firstNotNullOfOrNull { it.arrival ?: it.departure } ?: return null
            val visibilityEnd = maxOf(lastArrival.plus(visibilityPostArrivalWindow), travelDate.plusDays(1).atStartOfDay())
            if (now.isBefore(firstDeparture.minus(visibilityPredepartureWindow)) || now.isAfter(visibilityEnd)) {
                return null
            }
            return TimelineLiveState(
                stops = resolvedStops,
                now = now,
                markerHeightPx = markerHeightPx,
            )
        }

        private fun resolveStops(
            stops: List<TimelineStop>,
            travelDate: LocalDate,
        ): List<TimelineResolvedStop> {
            val seedTime = stops.firstNotNullOfOrNull { it.departureTime ?: it.arrivalTime } ?: LocalTime.MIDNIGHT
            var anchor = LocalDateTime.of(travelDate, seedTime).minusMinutes(1)
            return stops.mapIndexed { index, stop ->
                val arrival = stop.arrivalTime?.let { resolveDateTime(travelDate, it, anchor) }
                if (arrival != null && arrival.isAfter(anchor)) {
                    anchor = arrival
                }
                val departure = stop.departureTime?.let { resolveDateTime(travelDate, it, anchor) }
                if (departure != null && departure.isAfter(anchor)) {
                    anchor = departure
                }
                val dwellSeconds = when {
                    arrival != null && departure != null && departure.isAfter(arrival) ->
                        Duration.between(arrival, departure).seconds.coerceAtLeast(0)

                    index in 1 until stops.lastIndex -> 40L
                    else -> 0L
                }
                TimelineResolvedStop(
                    index = index,
                    station = stop.station,
                    arrival = arrival,
                    departure = departure,
                    displayTime = stop.departureTime ?: stop.arrivalTime ?: LocalTime.MIDNIGHT,
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
    }

    fun statusPill(): TimelineStatusPillState {
        val resolvedPhase = phaseState
        val currentStop = resolvedPhase.currentStationIndex?.let(stops::getOrNull)
        val nextStop = resolvedPhase.nextStationIndex?.let(stops::getOrNull)

        return when (resolvedPhase.phase) {
            TimelineSemanticPhase.NotDeparted -> TimelineStatusPillState(
                title = "待命",
                detail = "${stops.first().station.localName} ${ThsrFormatters.displayTimetableTime(stops.first().displayTime)}",
                tone = TimelineStatusTone.Blue,
            )

            TimelineSemanticPhase.AboutToDepart -> TimelineStatusPillState(
                title = "準備發車",
                detail = "${stops.first().station.localName} ${ThsrFormatters.displayTimetableTime(stops.first().displayTime)}",
                tone = TimelineStatusTone.Cyan,
            )

            TimelineSemanticPhase.Departing -> TimelineStatusPillState(
                title = "離站中",
                detail = "${currentStop?.station?.localName ?: stops.first().station.localName} 開出",
                tone = TimelineStatusTone.Cyan,
            )

            TimelineSemanticPhase.Departed -> TimelineStatusPillState(
                title = "已離站",
                detail = "前往 ${nextStop?.station?.localName ?: stops.last().station.localName}",
                tone = TimelineStatusTone.Blue,
            )

            TimelineSemanticPhase.InTransit -> TimelineStatusPillState(
                title = "行進中",
                detail = "下一站 ${nextStop?.station?.localName ?: stops.last().station.localName} ${nextStop?.let { ThsrFormatters.displayTimetableTime(it.displayTime) } ?: ""}".trim(),
                tone = TimelineStatusTone.Blue,
            )

            TimelineSemanticPhase.Approaching -> TimelineStatusPillState(
                title = "進站中",
                detail = "${nextStop?.station?.localName ?: stops.last().station.localName} ${nextStop?.let { ThsrFormatters.displayTimetableTime(it.displayTime) } ?: ""}".trim(),
                tone = TimelineStatusTone.Amber,
            )

            TimelineSemanticPhase.Stopped -> {
                val stop = currentStop ?: stops.last()
                val departure = stop.departure ?: stop.arrival?.plusSeconds(stop.dwellSeconds)
                val remaining = departure?.let { Duration.between(now, it).seconds.coerceAtLeast(0) } ?: 0
                TimelineStatusPillState(
                    title = "停靠中",
                    detail = if (remaining > 0) "${stop.station.localName} 剩餘 ${remaining}s" else stop.station.localName,
                    tone = TimelineStatusTone.Cyan,
                )
            }

            TimelineSemanticPhase.Arrived -> TimelineStatusPillState(
                title = "已抵達",
                detail = stops.last().station.localName,
                tone = TimelineStatusTone.Blue,
            )
        }
    }

    fun focusedSegmentIndex(originStopIndex: Int): Int {
        return max(originStopIndex, phaseState.focusedSegmentIndex)
    }

    fun defaultAnchorStopIndex(originStopIndex: Int): Int {
        return phaseState.defaultAnchorStopIndex.coerceAtLeast(originStopIndex)
    }

    fun showsRailProgress(): Boolean {
        return phaseState.phase !in setOf(
            TimelineSemanticPhase.NotDeparted,
            TimelineSemanticPhase.AboutToDepart,
        )
    }

    fun easedSegmentProgress(index: Int): Float {
        if (index !in 0 until stops.lastIndex) return 0f
        val start = segmentStart(index)
        val end = stops[index + 1].arrival ?: stops[index + 1].departure ?: return 0f
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
        val resolvedPhase = phaseState
        val currentIndex = resolvedPhase.currentStationIndex
        val nextIndex = resolvedPhase.nextStationIndex

        if (index == stops.lastIndex && resolvedPhase.phase == TimelineSemanticPhase.Arrived) {
            return TimelineNodeState.Arrived
        }

        return when {
            resolvedPhase.phase == TimelineSemanticPhase.NotDeparted && index == 0 -> TimelineNodeState.Standby
            resolvedPhase.phase == TimelineSemanticPhase.AboutToDepart && index == 0 -> TimelineNodeState.DepartingSoon
            resolvedPhase.phase == TimelineSemanticPhase.Stopped && index == currentIndex -> TimelineNodeState.Stopped
            resolvedPhase.phase == TimelineSemanticPhase.Approaching && index == nextIndex ->
                if (resolvedPhase.nextStationTransfer >= 0.76f) TimelineNodeState.ArrivingSoon else TimelineNodeState.Approaching
            resolvedPhase.phase == TimelineSemanticPhase.Departing && index == currentIndex -> TimelineNodeState.DepartingSoon
            resolvedPhase.phase == TimelineSemanticPhase.Departed && index == nextIndex -> TimelineNodeState.Next
            resolvedPhase.phase == TimelineSemanticPhase.InTransit && index == nextIndex -> TimelineNodeState.Next
            currentIndex != null && index < currentIndex -> TimelineNodeState.Passed
            currentIndex != null && nextIndex != null && index < nextIndex && resolvedPhase.phase in setOf(
                TimelineSemanticPhase.Approaching,
                TimelineSemanticPhase.InTransit,
                TimelineSemanticPhase.Departed,
            ) -> TimelineNodeState.Passed
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
        val resolvedPhase = phaseState
        return when {
            resolvedPhase.phase == TimelineSemanticPhase.Arrived && index == stops.lastIndex -> 1f
            resolvedPhase.currentStationIndex == index -> resolvedPhase.currentStationTransfer
            resolvedPhase.nextStationIndex == index -> resolvedPhase.nextStationTransfer
            else -> 0f
        }
    }

    fun marker(layout: TimelineLayoutMetrics): TimelineMarkerVisual? {
        val resolvedPhase = phaseState
        if (resolvedPhase.phase in setOf(TimelineSemanticPhase.NotDeparted, TimelineSemanticPhase.AboutToDepart)) {
            return TimelineMarkerVisual(
                centerXPx = layout.nodeCenterPx(0),
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
                val arrival = stops[stoppedIndex].arrival ?: return null
                val departure = stops[stoppedIndex].departure ?: arrival.plusSeconds(stops[stoppedIndex].dwellSeconds)
                val elapsedSeconds = Duration.between(arrival, now).seconds.coerceAtLeast(0).toFloat()
                val remainingSeconds = Duration.between(now, departure).seconds.coerceAtLeast(0).toFloat()
                val settleProgress = railBlend(elapsedSeconds / dockBlendDuration.seconds.toFloat())
                val settledOpacity = 1f - (0.22f * settleProgress)
                val departureProgress = railBlend(1f - (remainingSeconds / departureBlendWindow.seconds.toFloat()))
                val opacity = settledOpacity + ((0.92f - settledOpacity) * departureProgress)
                return TimelineMarkerVisual(
                    centerXPx = layout.nodeCenterPx(stoppedIndex),
                    phase = TimelineTrainPhase.Docked,
                    motion = TimelineMotionMetrics(
                        headWidthPx = markerHeightPx * 1.42f,
                        headHeightPx = markerHeightPx * 1.10f,
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
        if (now.isBefore(firstDeparture.minus(departingSoonWindow))) {
            return TimelinePhaseState(
                phase = TimelineSemanticPhase.NotDeparted,
                currentStationIndex = 0,
                nextStationIndex = stops.getOrNull(1)?.index,
                activeSegmentIndex = 0,
                motionPhase = TimelineTrainPhase.Docked,
                rawProgress = null,
                currentStationTransfer = 1f,
                nextStationTransfer = 0f,
                defaultAnchorStopIndex = 0,
                focusedSegmentIndex = 0,
            )
        }

        if (now.isBefore(firstDeparture)) {
            return TimelinePhaseState(
                phase = TimelineSemanticPhase.AboutToDepart,
                currentStationIndex = 0,
                nextStationIndex = stops.getOrNull(1)?.index,
                activeSegmentIndex = 0,
                motionPhase = TimelineTrainPhase.Docked,
                rawProgress = null,
                currentStationTransfer = 1f,
                nextStationTransfer = 0.10f,
                defaultAnchorStopIndex = 0,
                focusedSegmentIndex = 0,
            )
        }

        activeStoppedStationIndex()?.let { stoppedIndex ->
            return TimelinePhaseState(
                phase = TimelineSemanticPhase.Stopped,
                currentStationIndex = stoppedIndex,
                nextStationIndex = (stoppedIndex + 1).takeIf { it <= stops.lastIndex },
                activeSegmentIndex = minOf(stoppedIndex, stops.lastIndex - 1),
                motionPhase = TimelineTrainPhase.Docked,
                rawProgress = null,
                currentStationTransfer = 1f,
                nextStationTransfer = 0.14f,
                defaultAnchorStopIndex = stoppedIndex,
                focusedSegmentIndex = minOf(stoppedIndex, stops.lastIndex - 1),
            )
        }

        activeSegmentProgress()?.let { progress ->
            val recentDepartureCarry = progress.profile.departedStationCarry(progress.rawProgress)
            val phase = when {
                progress.motionPhase == TimelineTrainPhase.Departing -> TimelineSemanticPhase.Departing
                progress.motionPhase == TimelineTrainPhase.Approaching -> TimelineSemanticPhase.Approaching
                recentDepartureCarry > 0.02f -> TimelineSemanticPhase.Departed
                else -> TimelineSemanticPhase.InTransit
            }
            val nextTransfer = when (phase) {
                TimelineSemanticPhase.Approaching ->
                    progress.profile.arrivalNodeTransfer(progress.rawProgress, progress.magnetic)

                TimelineSemanticPhase.InTransit ->
                    ((progress.rawProgress - 0.56f) / 0.30f).clamp01() * 0.24f

                else -> 0f
            }
            val currentTransfer = when (phase) {
                TimelineSemanticPhase.Departing ->
                    progress.profile.departureNodeFade(progress.rawProgress).coerceAtLeast(0.26f)

                TimelineSemanticPhase.Departed ->
                    (0.18f + (recentDepartureCarry * 0.54f)).coerceIn(0f, 0.72f)

                TimelineSemanticPhase.Approaching ->
                    ((1f - nextTransfer) * 0.16f).coerceAtLeast(0f)

                else -> 0f
            }
            val anchorIndex = when (phase) {
                TimelineSemanticPhase.Approaching -> progress.segmentIndex + 1
                else -> progress.segmentIndex
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
                defaultAnchorStopIndex = anchorIndex.coerceIn(0, stops.lastIndex),
                focusedSegmentIndex = progress.segmentIndex.coerceIn(0, stops.lastIndex - 1),
            )
        }

        return TimelinePhaseState(
            phase = TimelineSemanticPhase.Arrived,
            currentStationIndex = stops.lastIndex,
            nextStationIndex = null,
            activeSegmentIndex = stops.lastIndex - 1,
            motionPhase = null,
            rawProgress = null,
            currentStationTransfer = 1f,
            nextStationTransfer = 0f,
            defaultAnchorStopIndex = stops.lastIndex,
            focusedSegmentIndex = stops.lastIndex - 1,
        )
    }

    private fun activeSegmentProgress(): TimelineActiveSegmentProgress? {
        val transitIndex = activeTransitSegmentIndex() ?: return null
        val start = segmentStart(transitIndex)
        val end = stops[transitIndex + 1].arrival ?: stops[transitIndex + 1].departure ?: return null
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
        if (index == 0) {
            return firstDeparture
        }
        val stop = stops[index]
        return stop.departure ?: stop.arrival?.plusSeconds(stop.dwellSeconds) ?: firstDeparture
    }

    private fun activeTransitSegmentIndex(): Int? {
        for (index in 0 until stops.lastIndex) {
            val start = segmentStart(index)
            val end = stops[index + 1].arrival ?: stops[index + 1].departure ?: continue
            if (!now.isBefore(start) && now.isBefore(end)) {
                return index
            }
        }
        return null
    }

    private fun activeStoppedStationIndex(): Int? {
        if (stops.size <= 2) return null
        for (index in 1 until stops.lastIndex) {
            val arrival = stops[index].arrival ?: continue
            val departure = stops[index].departure ?: arrival.plusSeconds(stops[index].dwellSeconds)
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
        val totalSeconds = Duration.between(start, end).seconds.coerceAtLeast(1).toFloat()
        val elapsedSeconds = Duration.between(start, current).seconds.toFloat()
        return (elapsedSeconds / totalSeconds).clamp01()
    }

    private fun influenceProfile(
        start: LocalDateTime,
        end: LocalDateTime,
    ): TimelineInfluenceProfile {
        val durationSeconds = Duration.between(start, end).seconds.coerceAtLeast(1).toFloat()
        return TimelineInfluenceProfile.from(
            durationSeconds = durationSeconds,
            departureWindowSeconds = departureBlendWindow.seconds.toFloat(),
            approachWindowSeconds = approachWindow.seconds.toFloat(),
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
            TimelineMotionMetrics(
                headWidthPx = markerHeightPx * (1.72f + (0.24f * launch)),
                headHeightPx = markerHeightPx * (1.02f + (0.04f * launch)),
                tailWidthPx = markerHeightPx * (0.92f + (0.64f * launch)),
            )
        }

        TimelineTrainPhase.InTransit -> TimelineMotionMetrics(
            headWidthPx = markerHeightPx * 1.82f,
            headHeightPx = markerHeightPx,
            tailWidthPx = markerHeightPx * 0.84f,
        )

        TimelineTrainPhase.Approaching -> {
            val transfer = profile.arrivalNodeTransfer(rawProgress, magnetic = true)
            TimelineMotionMetrics(
                headWidthPx = markerHeightPx * (1.68f - (0.18f * transfer)),
                headHeightPx = markerHeightPx * (1.00f - (0.03f * transfer)),
                tailWidthPx = markerHeightPx * (0.82f - (0.44f * transfer)),
            )
        }

        TimelineTrainPhase.Docked -> TimelineMotionMetrics(
            headWidthPx = markerHeightPx * 1.40f,
            headHeightPx = markerHeightPx * 1.08f,
            tailWidthPx = 0f,
        )
    }

    private fun nextStopDisplayDateTime(index: Int): LocalDateTime =
        stops[index].arrival ?: stops[index].departure ?: firstDeparture
}

private fun lerp(start: Float, end: Float, progress: Float): Float = start + ((end - start) * progress)
