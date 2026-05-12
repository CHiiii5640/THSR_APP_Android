package com.chiiii5640.thsrapp.core.network

import com.chiiii5640.thsrapp.core.model.SourceState
import com.chiiii5640.thsrapp.core.model.SourceStatus

fun unavailableStatus(defaultLabel: String, throwable: Throwable?): SourceStatus {
    val message = throwable?.message
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return SourceStatus(
        label = if (message == null) defaultLabel else "$defaultLabel: $message",
        state = SourceState.Unavailable,
    )
}
