package com.chiiii5640.thsrapp.features.trainResults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.isActive

data class TrainTimelineFrame(
    val now: LocalDateTime,
    val frameNanos: Long,
)

@Composable
fun rememberTrainTimelineFrame(
    referenceDateTime: LocalDateTime? = null,
    referenceAnchorDateTime: LocalDateTime? = null,
): TrainTimelineFrame {
    var frame by remember(referenceDateTime, referenceAnchorDateTime) {
        mutableStateOf(
            TrainTimelineFrame(
                now = resolveTrainTimelineNow(
                    realNow = LocalDateTime.now(),
                    referenceDateTime = referenceDateTime,
                    referenceAnchorDateTime = referenceAnchorDateTime,
                ),
                frameNanos = 0L,
            ),
        )
    }
    LaunchedEffect(referenceDateTime, referenceAnchorDateTime) {
        while (isActive) {
            withFrameNanos { frameNanos ->
                val realNow = LocalDateTime.now()
                frame = TrainTimelineFrame(
                    now = resolveTrainTimelineNow(
                        realNow = realNow,
                        referenceDateTime = referenceDateTime,
                        referenceAnchorDateTime = referenceAnchorDateTime,
                    ),
                    frameNanos = frameNanos,
                )
            }
        }
    }
    return frame
}

private fun resolveTrainTimelineNow(
    realNow: LocalDateTime,
    referenceDateTime: LocalDateTime?,
    referenceAnchorDateTime: LocalDateTime?,
): LocalDateTime {
    if (referenceDateTime == null) {
        return realNow
    }
    val anchor = referenceAnchorDateTime ?: return referenceDateTime
    return try {
        referenceDateTime.plusNanos(Duration.between(anchor, realNow).toNanos())
    } catch (_: ArithmeticException) {
        referenceDateTime
    }
}
