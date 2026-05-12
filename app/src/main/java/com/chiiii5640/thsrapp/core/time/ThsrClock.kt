package com.chiiii5640.thsrapp.core.time

import java.time.Clock
import java.time.ZoneId

object ThsrClock {
    val zone: ZoneId = ZoneId.of("Asia/Taipei")

    fun system(): Clock = Clock.system(zone)
}
