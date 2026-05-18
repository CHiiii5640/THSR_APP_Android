package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val startXPx: Float,
    val widthPx: Float,
)

private data class TimelineLayoutMetrics(
    val leadingInsetPx: Float,
    val trailingInsetPx: Float,
    val nodeCentersPx: List<Float>,
    val segments: List<TimelineSegmentMetrics>,
    val totalWidthPx: Float,
) {
    fun nodeCenterPx(index: Int): Float = nodeCentersPx.getOrElse(index) { 0f }

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
    val showsVisibleLiveActivity = remember(liveState, originStopIndex) {
        liveState?.hasVisibleLiveActivity(originStopIndex) ?: false
    }
    var initialVisibleAnchorStopIndex by remember(option.trainNo, option.travelDate, option.origin, option.destination) {
        mutableIntStateOf(Int.MIN_VALUE)
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
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val layoutMetrics = remember(stops, density, layoutProfile, visualMetrics) {
                buildTimelineLayout(
                    stops = stops,
                    layoutProfile = layoutProfile,
                    visualMetrics = visualMetrics,
                    density = density,
                )
            }
            val anchorStopIndex = originStopIndex.coerceIn(0, stops.lastIndex)
            val currentVisibleAnchorStopIndex = (
                if (showsVisibleLiveActivity) {
                    liveState?.visibleAnchorStopIndex()
                        ?.coerceAtLeast(originStopIndex)
                } else {
                    originStopIndex
                }
                )?.coerceIn(originStopIndex, stops.lastIndex)
                ?: originStopIndex
            val focusedSegmentIndex = (
                liveState?.focusedSegmentIndex(originStopIndex)
                    ?: originStopIndex
                ).coerceIn(0, layoutMetrics.segments.lastIndex)
            val maxOffsetPx = layoutMetrics.maxOffsetPx(viewportWidthPx)
            val resolvedInitialVisibleAnchorStopIndex = if (initialVisibleAnchorStopIndex == Int.MIN_VALUE) {
                currentVisibleAnchorStopIndex
            } else {
                initialVisibleAnchorStopIndex.coerceIn(originStopIndex, stops.lastIndex)
            }
            val initialOffsetPx = remember(resolvedInitialVisibleAnchorStopIndex, maxOffsetPx, layoutMetrics.totalWidthPx) {
                (layoutMetrics.nodeCenterPx(resolvedInitialVisibleAnchorStopIndex) - layoutMetrics.leadingInsetPx)
                    .coerceIn(0f, maxOffsetPx)
            }
            val revealThresholdPx = with(density) { tokens.timeline.revealThreshold.toPx() }

            var previousStopsRevealed by remember(option.trainNo, option.travelDate, layoutProfile) {
                mutableStateOf(false)
            }
            var isDragging by remember(option.trainNo, option.travelDate, layoutProfile) {
                mutableStateOf(false)
            }
            var draggedOffsetPx by remember(option.trainNo, option.travelDate, layoutProfile) {
                mutableFloatStateOf(initialOffsetPx)
            }
            var settledOffsetPx by remember(option.trainNo, option.travelDate, layoutProfile) {
                mutableFloatStateOf(initialOffsetPx)
            }
            var backwardPullPx by remember(option.trainNo, option.travelDate, layoutProfile) {
                mutableFloatStateOf(0f)
            }
            val visibleOffsetPx = if (isDragging) draggedOffsetPx else settledOffsetPx

            LaunchedEffect(
                option.trainNo,
                option.travelDate,
                option.origin,
                option.destination,
                currentVisibleAnchorStopIndex,
            ) {
                if (initialVisibleAnchorStopIndex == Int.MIN_VALUE) {
                    initialVisibleAnchorStopIndex = currentVisibleAnchorStopIndex
                }
            }

            LaunchedEffect(initialOffsetPx, maxOffsetPx, option.trainNo, option.travelDate, layoutProfile) {
                previousStopsRevealed = false
                isDragging = false
                backwardPullPx = 0f
                draggedOffsetPx = initialOffsetPx
                settledOffsetPx = initialOffsetPx
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(
                        initialOffsetPx,
                        maxOffsetPx,
                        revealThresholdPx,
                        previousStopsRevealed,
                        anchorStopIndex,
                    ) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                backwardPullPx = 0f
                                draggedOffsetPx = settledOffsetPx
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (!previousStopsRevealed && dragAmount > 0f && draggedOffsetPx <= initialOffsetPx + 0.5f) {
                                    backwardPullPx = (backwardPullPx + dragAmount).coerceAtLeast(0f)
                                    if (backwardPullPx >= revealThresholdPx) {
                                        previousStopsRevealed = true
                                        val overshootPx = backwardPullPx - revealThresholdPx
                                        draggedOffsetPx = (initialOffsetPx - overshootPx).coerceIn(0f, maxOffsetPx)
                                    } else {
                                        draggedOffsetPx = initialOffsetPx
                                    }
                                } else {
                                    if (dragAmount < 0f) {
                                        backwardPullPx = 0f
                                    }
                                    val minOffsetPx = if (previousStopsRevealed) 0f else initialOffsetPx
                                    draggedOffsetPx = (draggedOffsetPx - dragAmount).coerceIn(minOffsetPx, maxOffsetPx)
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                backwardPullPx = 0f
                                settledOffsetPx = if (previousStopsRevealed) {
                                    draggedOffsetPx.coerceIn(0f, maxOffsetPx)
                                } else {
                                    initialOffsetPx
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                backwardPullPx = 0f
                                settledOffsetPx = if (previousStopsRevealed) {
                                    draggedOffsetPx.coerceIn(0f, maxOffsetPx)
                                } else {
                                    initialOffsetPx
                                }
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(-visibleOffsetPx.roundToInt(), 0) }
                        .width(with(density) { layoutMetrics.totalWidthPx.toDp() })
                        .fillMaxHeight(),
                ) {
                    TimelineRailCanvas(
                        layoutMetrics = layoutMetrics,
                        liveState = liveState,
                        canvasMetrics = canvasMetrics,
                        frameNanos = frame.frameNanos,
                        hiddenBeforeIndex = anchorStopIndex,
                        previousStopsRevealed = previousStopsRevealed,
                        focusedSegmentIndex = focusedSegmentIndex,
                        showsLiveProgress = previousStopsRevealed || showsVisibleLiveActivity,
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
                            hidden = !previousStopsRevealed && index < anchorStopIndex,
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
                    x = segment.startXPx,
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
                    startX = segment.startXPx,
                    endX = segment.startXPx + fillWidthPx,
                ),
                topLeft = Offset(
                    x = segment.startXPx,
                    y = canvasMetrics.trackYPx - (canvasMetrics.activeLineHeightPx / 2f),
                ),
                size = Size(fillWidthPx, canvasMetrics.activeLineHeightPx),
                cornerRadius = CornerRadius(canvasMetrics.activeLineHeightPx, canvasMetrics.activeLineHeightPx),
            )

            drawRoundRect(
                color = accent.copy(alpha = 0.10f * segmentOpacity),
                topLeft = Offset(
                    x = segment.startXPx,
                    y = canvasMetrics.trackYPx - (canvasMetrics.activeLineHeightPx / 2f) - 0.8f,
                ),
                size = Size(fillWidthPx, canvasMetrics.activeLineHeightPx + 1.6f),
                cornerRadius = CornerRadius(canvasMetrics.activeLineHeightPx, canvasMetrics.activeLineHeightPx),
            )

            drawRoundRect(
                color = idleGlowColor.copy(alpha = idleGlowColor.alpha * segmentOpacity),
                topLeft = Offset(
                    x = segment.startXPx,
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
    ) || activeTransfer > 0.35f

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
            val activeGlowRadius = nodeRadius + (3.5f * activeTransfer)
            if (activeTransfer > 0.01f) {
                drawCircle(
                    color = nodePalette.ringColor.copy(alpha = 0.12f * activeTransfer),
                    radius = activeGlowRadius,
                    center = center,
                )
            }
            if (nodePalette.ringAlpha > 0f) {
                drawCircle(
                    color = nodePalette.ringColor.copy(alpha = nodePalette.ringAlpha * (0.72f + (0.28f * ringWave))),
                    radius = nodeRadius + 2.6f + (1.8f * ringWave) + activeTransfer,
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
): TimelineLayoutMetrics {
    val leadingInsetPx = with(density) { visualMetrics.leadingInset.toPx() }
    val trailingInsetPx = with(density) { visualMetrics.trailingInset.toPx() }
    val unitWidthPx = with(density) { layoutProfile.timelineSegmentUnitWidth.toPx() }
    val minSegmentWidthPx = unitWidthPx * 2.35f
    var currentXPx = leadingInsetPx
    val nodeCenters = mutableListOf(currentXPx)
    val segments = mutableListOf<TimelineSegmentMetrics>()

    for (index in 0 until stops.lastIndex) {
        val gap = abs(stops[index + 1].station.sortIndex - stops[index].station.sortIndex).coerceAtLeast(1)
        val widthPx = max(unitWidthPx * gap, minSegmentWidthPx)
        segments += TimelineSegmentMetrics(
            index = index,
            startXPx = currentXPx,
            widthPx = widthPx,
        )
        currentXPx += widthPx
        nodeCenters += currentXPx
    }

    return TimelineLayoutMetrics(
        leadingInsetPx = leadingInsetPx,
        trailingInsetPx = trailingInsetPx,
        nodeCentersPx = nodeCenters,
        segments = segments,
        totalWidthPx = currentXPx + trailingInsetPx,
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
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.14f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.82f),
            ringColor = tokens.colors.primaryBlue,
            ringAlpha = 0.06f,
            strokeWidthPx = 1.45f,
            timeColor = timeSecondary.copy(alpha = 0.86f),
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.76f),
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
            fillColor = tokens.colors.primaryBlue.copy(alpha = 0.18f),
            strokeColor = tokens.colors.primaryBlue.copy(alpha = 0.78f),
            ringColor = tokens.colors.primaryBlue,
            ringAlpha = 0.09f,
            strokeWidthPx = 1.55f,
            timeColor = tokens.colors.textSecondary.copy(alpha = 0.92f),
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.88f),
        )

        TimelineNodeState.ArrivingSoon -> TimelineNodePalette(
            size = activeSize,
            fillColor = timelineApproachingAmber.copy(alpha = 0.14f + (0.08f * activeTransfer)),
            strokeColor = timelineApproachingAmber.copy(alpha = 0.92f),
            ringColor = timelineApproachingAmber,
            ringAlpha = 0.14f,
            strokeWidthPx = 1.7f,
            timeColor = timePrimary,
            stationColor = tokens.colors.textSecondary.copy(alpha = 0.94f),
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
    val markerVerticalCorrectionPx = 6.dp.toPx()
    val centerYPx = canvasMetrics.trackYPx - markerVerticalCorrectionPx
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
        if (now.isBefore(firstDeparture)) {
            val departingSoon = !now.isBefore(firstDeparture.minus(departingSoonWindow))
            return TimelineStatusPillState(
                title = if (departingSoon) "準備發車" else "待命",
                detail = "${stops.first().station.localName} ${ThsrFormatters.displayTimetableTime(stops.first().displayTime)}",
                tone = if (departingSoon) TimelineStatusTone.Cyan else TimelineStatusTone.Blue,
            )
        }

        val transitIndex = activeTransitSegmentIndex()
        if (transitIndex != null) {
            val currentStop = stops[transitIndex]
            val nextStop = stops[transitIndex + 1]
            val arrival = nextStop.arrival ?: nextStop.departure ?: nextStopDisplayDateTime(transitIndex + 1)
            val remainingSeconds = Duration.between(now, arrival).seconds.toFloat()
            val phase = phaseOnSegment(transitIndex) ?: TimelineTrainPhase.InTransit
            return when {
                phase == TimelineTrainPhase.Departing -> TimelineStatusPillState(
                    title = "離站中",
                    detail = "${currentStop.station.localName} 開出",
                    tone = TimelineStatusTone.Cyan,
                )

                remainingSeconds <= arrivalPulseWindow.seconds.toFloat() -> TimelineStatusPillState(
                    title = "進站中",
                    detail = "${nextStop.station.localName} ${ThsrFormatters.displayTimetableTime(nextStop.displayTime)}",
                    tone = TimelineStatusTone.Amber,
                )

                remainingSeconds <= approachWindow.seconds.toFloat() -> TimelineStatusPillState(
                    title = "接近",
                    detail = "${nextStop.station.localName} ${ThsrFormatters.displayTimetableTime(nextStop.displayTime)}",
                    tone = TimelineStatusTone.Blue,
                )

                else -> TimelineStatusPillState(
                    title = "行進中",
                    detail = "下一站 ${nextStop.station.localName} ${ThsrFormatters.displayTimetableTime(nextStop.displayTime)}",
                    tone = TimelineStatusTone.Blue,
                )
            }
        }

        val stoppedIndex = activeStoppedStationIndex()
        if (stoppedIndex != null) {
            val stop = stops[stoppedIndex]
            val departure = stop.departure ?: stop.arrival?.plusSeconds(stop.dwellSeconds)
            val remaining = departure?.let { Duration.between(now, it).seconds.coerceAtLeast(0) } ?: 0
            val detail = if (remaining > 0) {
                "${stop.station.localName} 剩餘 ${remaining}s"
            } else {
                stop.station.localName
            }
            return TimelineStatusPillState(
                title = "停靠中",
                detail = detail,
                tone = TimelineStatusTone.Cyan,
            )
        }

        return TimelineStatusPillState(
            title = "已抵達",
            detail = stops.last().station.localName,
            tone = TimelineStatusTone.Blue,
        )
    }

    fun focusedSegmentIndex(originStopIndex: Int): Int {
        activeTransitSegmentIndex()?.let { return max(originStopIndex, it) }
        activeStoppedStationIndex()?.let { return max(originStopIndex, minOf(it, stops.lastIndex - 1)) }
        if (!now.isBefore(lastArrival)) {
            return max(originStopIndex, stops.lastIndex - 1)
        }
        return originStopIndex
    }

    fun visibleAnchorStopIndex(): Int {
        activeStoppedStationIndex()?.let { return it }
        activeTransitSegmentIndex()?.let { return it }
        if (!now.isBefore(lastArrival)) {
            return stops.lastIndex
        }
        return 0
    }

    fun hasVisibleLiveActivity(anchorStopIndex: Int): Boolean {
        activeTransitSegmentIndex()?.let { return it >= anchorStopIndex }
        activeStoppedStationIndex()?.let { return it >= anchorStopIndex }
        return false
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
        if (index != activeTransitSegmentIndex()) return null
        val start = segmentStart(index)
        val end = stops[index + 1].arrival ?: stops[index + 1].departure ?: return null
        val rawProgress = normalizedProgress(start = start, end = end, current = now)
        return influenceProfile(start = start, end = end).phase(rawProgress)
    }

    fun nodeState(index: Int): TimelineNodeState {
        if (index !in stops.indices) return TimelineNodeState.Station
        val transitIndex = activeTransitSegmentIndex()
        val stop = stops[index]
        val stopMoment = stop.arrival ?: stop.departure ?: return TimelineNodeState.Station

        if (index == 0) {
            if (now.isBefore(firstDeparture)) {
                return if (!now.isBefore(firstDeparture.minus(departingSoonWindow))) {
                    TimelineNodeState.DepartingSoon
                } else {
                    TimelineNodeState.Standby
                }
            }
            if (transitIndex == 0 && phaseOnSegment(0) == TimelineTrainPhase.Departing) {
                return TimelineNodeState.DepartingSoon
            }
            return TimelineNodeState.Passed
        }

        if (index == stops.lastIndex && !now.isBefore(lastArrival)) {
            return TimelineNodeState.Arrived
        }

        val holdDeparture = stop.departure ?: stopMoment.plusSeconds(stop.dwellSeconds)
        if (!now.isBefore(stopMoment) && now.isBefore(holdDeparture)) {
            return TimelineNodeState.Stopped
        }

        if (!now.isBefore(stopMoment.minus(approachWindow)) && now.isBefore(stopMoment)) {
            return if (!now.isBefore(stopMoment.minus(arrivalPulseWindow))) {
                TimelineNodeState.ArrivingSoon
            } else {
                TimelineNodeState.Approaching
            }
        }

        if (transitIndex != null && transitIndex == index && phaseOnSegment(index) == TimelineTrainPhase.Departing) {
            return TimelineNodeState.DepartingSoon
        }

        if (!now.isBefore(holdDeparture)) {
            return TimelineNodeState.Passed
        }

        if (transitIndex != null && index == transitIndex + 1) {
            return TimelineNodeState.Next
        }

        return TimelineNodeState.Station
    }

    fun nodeActivationProgress(index: Int): Float {
        if (index !in stops.indices) return 0f
        val stoppedIndex = activeStoppedStationIndex()
        if (stoppedIndex == index) return 1f
        if (index == stops.lastIndex && !now.isBefore(lastArrival)) return 1f

        val transitIndex = activeTransitSegmentIndex() ?: return 0f
        val start = segmentStart(transitIndex)
        val end = stops[transitIndex + 1].arrival ?: stops[transitIndex + 1].departure ?: return 0f
        val rawProgress = normalizedProgress(start = start, end = end, current = now)
        val profile = influenceProfile(start = start, end = end)
        val magnetic = Duration.between(now, end) <= arrivalPulseWindow

        return when {
            transitIndex == index -> profile.departureNodeFade(rawProgress)
            transitIndex + 1 == index -> profile.arrivalNodeTransfer(rawProgress, magnetic)
            else -> 0f
        }
    }

    fun marker(layout: TimelineLayoutMetrics): TimelineMarkerVisual? {
        if (now.isBefore(firstDeparture)) {
            return TimelineMarkerVisual(
                centerXPx = layout.nodeCenterPx(0),
                phase = TimelineTrainPhase.Docked,
                motion = TimelineMotionMetrics(
                    headWidthPx = markerHeightPx * 1.28f,
                    headHeightPx = markerHeightPx * 1.06f,
                    tailWidthPx = 0f,
                ),
                opacity = 0.76f,
                arrivalTransfer = 0f,
            )
        }

        val transitIndex = activeTransitSegmentIndex()
        if (transitIndex != null) {
            val start = segmentStart(transitIndex)
            val end = stops[transitIndex + 1].arrival ?: stops[transitIndex + 1].departure ?: return null
            val rawProgress = normalizedProgress(start = start, end = end, current = now)
            val profile = influenceProfile(start = start, end = end)
            val magnetic = Duration.between(now, end) <= arrivalPulseWindow
            val easedProgress = profile.remappedProgress(rawProgress, magnetic)
            val phase = profile.phase(rawProgress)
            val startXPx = layout.nodeCenterPx(transitIndex)
            val endXPx = layout.nodeCenterPx(transitIndex + 1)
            return TimelineMarkerVisual(
                centerXPx = lerp(startXPx, endXPx, easedProgress),
                phase = phase,
                motion = motionMetrics(
                    phase = phase,
                    rawProgress = rawProgress,
                    profile = profile,
                ),
                opacity = 1f,
                arrivalTransfer = if (phase == TimelineTrainPhase.Approaching) {
                    profile.arrivalNodeTransfer(rawProgress, magnetic)
                } else {
                    0f
                },
            )
        }

        val stoppedIndex = activeStoppedStationIndex()
        if (stoppedIndex != null) {
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

        return null
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
