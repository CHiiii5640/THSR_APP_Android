package com.chiiii5640.thsrapp.core.time

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object ThsrFormatters {
    val apiDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val apiTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private fun locale(): Locale = Locale.getDefault()
    private val displayTime24Hour: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun date(value: LocalDate): String = value.format(apiDate)
    fun time(value: LocalTime): String = value.format(apiTime)
    fun displayDate(value: LocalDate): String =
        value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    fun pickerTime(value: LocalTime): String =
        value.format(displayTime24Hour)

    fun displayTimetableTime(value: LocalTime): String = value.format(displayTime24Hour)
}
