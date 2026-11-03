package com.chandu.gatesystem.ai2027.data

import java.time.LocalDate
import java.time.LocalDateTime

/** Injectable clock so day-dependent logic stays testable. */
interface Clock {
    fun today(): LocalDate
    fun now(): LocalDateTime
    fun millis(): Long
}

object SystemClock : Clock {
    override fun today(): LocalDate = LocalDate.now()
    override fun now(): LocalDateTime = LocalDateTime.now()
    override fun millis(): Long = System.currentTimeMillis()
}
