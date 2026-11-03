package com.chandu.gatesystem.ai2027.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class AlarmId(val label: String) {
    WAKE("Morning alarm"), WORKOUT("Workout reminder"), FOLLOW_UP("Follow-up reminder"),
    PREPARE_TOMORROW("8:30 PM prepare-tomorrow / incomplete-quest reminder"), SLEEP("Sleep reminder"),
    DUE_REVISION("Due-revision reminder"), SAME_SUBJECT("Same-subject-day reminder"), MOCK("Mock-test reminder")
}

/** [time] == null means "no time specified in the plan; the user must choose one before enabling". */
data class AlarmConfig(
    val id: AlarmId, val enabled: Boolean, val time: LocalTime?, val sound: Boolean = true,
    val vibrate: Boolean = true, val snoozeMinutes: Int = 10, val fromPlan: Boolean = false
) {
    val schedulable get() = enabled && time != null
}

object AlarmDefaults {
    /**
     * Times that appear in the plan document:
     *  - 5:00 AM  wake;  5:15 AM workout window start (5:15-5:50);  8:30 PM prepare tomorrow / reduce screens;
     *  - 9:00 PM  sleep.
     * The document specifies NO time for a follow-up reminder, due-revision, same-subject or mock reminders,
     * so those are OFF with no time until the user picks one. No time has been invented.
     */
    fun all(): List<AlarmConfig> = listOf(
        AlarmConfig(AlarmId.WAKE, true, LocalTime.of(5, 0), fromPlan = true),
        AlarmConfig(AlarmId.WORKOUT, true, LocalTime.of(5, 15), fromPlan = true),
        AlarmConfig(AlarmId.FOLLOW_UP, false, null),
        AlarmConfig(AlarmId.PREPARE_TOMORROW, true, LocalTime.of(20, 30), fromPlan = true),
        AlarmConfig(AlarmId.SLEEP, true, LocalTime.of(21, 0), fromPlan = true),
        AlarmConfig(AlarmId.DUE_REVISION, false, null),
        AlarmConfig(AlarmId.SAME_SUBJECT, false, null),
        AlarmConfig(AlarmId.MOCK, false, null)
    )
}

object AlarmPlanner {
    /** Next trigger strictly after [now] for a daily alarm. */
    fun nextTrigger(time: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    data class Planned(val id: AlarmId, val at: LocalDateTime)

    /** Everything that must be (re)registered, e.g. after a reboot. Disabled or time-less alarms are skipped. */
    fun plan(configs: List<AlarmConfig>, now: LocalDateTime, plan: Plan?): List<Planned> =
        configs.filter { it.schedulable }.mapNotNull { c ->
            var at = nextTrigger(c.time!!, now)
            if (c.id == AlarmId.MOCK) {
                // Fires on the evening before a full-mock day (per the plan's mock calendar).
                if (plan == null) return@mapNotNull null
                var guard = 0
                while (!mockTomorrow(plan, at.toLocalDate()) && guard++ < 200) at = at.plusDays(1)
                if (!mockTomorrow(plan, at.toLocalDate())) return@mapNotNull null
            }
            Planned(c.id, at)
        }.sortedBy { it.at }

    fun mockTomorrow(plan: Plan, fireDate: LocalDate) = plan.forDate(fireDate.plusDays(1).toString())?.fullMock == true

    fun snooze(now: LocalDateTime, minutes: Int) = now.plusMinutes(minutes.toLong())
}
