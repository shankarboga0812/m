package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.plan
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class AlarmPlannerTest {
    private fun cfg(id: AlarmId) = AlarmDefaults.all().first { it.id == id }

    @Test fun defaultsUseOnlyTimesFromTheDocument() {
        assertEquals(LocalTime.of(5, 0), cfg(AlarmId.WAKE).time); assertEquals(LocalTime.of(5, 15), cfg(AlarmId.WORKOUT).time)
        assertEquals(LocalTime.of(20, 30), cfg(AlarmId.PREPARE_TOMORROW).time); assertEquals(LocalTime.of(21, 0), cfg(AlarmId.SLEEP).time)
        for (id in listOf(AlarmId.FOLLOW_UP, AlarmId.DUE_REVISION, AlarmId.SAME_SUBJECT, AlarmId.MOCK)) { assertNull(cfg(id).time); assertFalse(cfg(id).enabled) }
        assertTrue(AlarmDefaults.all().filter { it.fromPlan }.size == 4)
    }
    @Test fun nextTriggerIsTodayIfStillAheadElseTomorrow() {
        val now = LocalDateTime.of(2026, 10, 11, 4, 30)
        assertEquals(LocalDateTime.of(2026, 10, 11, 5, 0), AlarmPlanner.nextTrigger(LocalTime.of(5, 0), now))
        assertEquals(LocalDateTime.of(2026, 10, 12, 5, 0), AlarmPlanner.nextTrigger(LocalTime.of(5, 0), LocalDateTime.of(2026, 10, 11, 5, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 12, 5, 0), AlarmPlanner.nextTrigger(LocalTime.of(5, 0), LocalDateTime.of(2026, 10, 11, 23, 59)))
    }
    @Test fun rebootRescheduleListsEveryEnabledAlarmInOrder() {
        val reboot = LocalDateTime.of(2026, 10, 11, 12, 0)
        val p = AlarmPlanner.plan(AlarmDefaults.all(), reboot, plan)
        assertEquals(listOf(AlarmId.PREPARE_TOMORROW, AlarmId.SLEEP, AlarmId.WAKE, AlarmId.WORKOUT), p.map { it.id })
        assertEquals(LocalDateTime.of(2026, 10, 12, 5, 0), p.first { it.id == AlarmId.WAKE }.at)
    }
    @Test fun disabledOrTimelessAlarmsAreNeverScheduled() {
        val cs = AlarmDefaults.all().map { if (it.id == AlarmId.WAKE) it.copy(enabled = false) else it }.map { if (it.id == AlarmId.FOLLOW_UP) it.copy(enabled = true) else it }
        val ids = AlarmPlanner.plan(cs, LocalDateTime.of(2026, 10, 11, 0, 0), plan).map { it.id }
        assertFalse(AlarmId.WAKE in ids); assertFalse(AlarmId.FOLLOW_UP in ids)   // enabled but no time chosen yet
    }
    @Test fun mockReminderFiresTheEveningBeforeAFullMock() {
        val cs = AlarmDefaults.all().map { if (it.id == AlarmId.MOCK) it.copy(enabled = true, time = LocalTime.of(19, 0)) else it }
        val p = AlarmPlanner.plan(cs, LocalDateTime.of(2026, 10, 11, 8, 0), plan).first { it.id == AlarmId.MOCK }
        assertEquals(java.time.LocalDate.of(2026, 10, 23), p.at.toLocalDate())     // Day 14 mock is Sat 24 Oct
        assertNull(AlarmPlanner.plan(cs, LocalDateTime.of(2027, 2, 1, 8, 0), plan).firstOrNull { it.id == AlarmId.MOCK })
    }
    @Test fun snoozeAddsMinutes() = assertEquals(LocalDateTime.of(2026, 10, 11, 5, 10), AlarmPlanner.snooze(LocalDateTime.of(2026, 10, 11, 5, 0), 10))
}
