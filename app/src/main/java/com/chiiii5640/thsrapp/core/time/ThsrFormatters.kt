package com.chiiii5640.thsrapp.core.time

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ThsrFormatters {
    val apiDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val apiTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun date(value: LocalDate): String = value.format(apiDate)
    fun time(value: LocalTime): String = value.format(apiTime)
}
