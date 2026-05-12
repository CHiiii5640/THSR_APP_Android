package com.chiiii5640.thsrapp.features.discounts

import com.chiiii5640.thsrapp.core.model.DiscountOffer
import com.chiiii5640.thsrapp.core.model.SourceStatus
import com.chiiii5640.thsrapp.features.timetable.TimetableTrain
import java.time.LocalDate

interface DiscountProvider {
    suspend fun discounts(date: LocalDate, trains: List<TimetableTrain>, forceRefresh: Boolean): DiscountResult
}

data class DiscountResult(
    val offersByTrainNo: Map<String, List<DiscountOffer>>,
    val status: SourceStatus,
)
