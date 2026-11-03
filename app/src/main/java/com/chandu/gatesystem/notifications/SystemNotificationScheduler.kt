package com.chandu.gatesystem.notifications

import com.chandu.gatesystem.data.GatePlan
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object SystemNotificationScheduler {
    const val ACTION_INACTIVITY = "com.chandu.gatesystem.INACTIVITY"
    const val ACTION_DANGER = "com.chandu.gatesystem.DANGER"
    const val ACTION_DAILY_QUEST_MORNING = "com.chandu.gatesystem.DAILY_QUEST_MORNING"
    const val ACTION_DAILY_QUEST_AFTERNOON = "com.chandu.gatesystem.DAILY_QUEST_AFTERNOON"
    const val ACTION_DAILY_QUEST_EVENING = "com.chandu.gatesystem.DAILY_QUEST_EVENING"
    const val EXTRA_DAY = "day"

    private const val INACTIVITY_REQUEST = 6400
    private const val DANGER_REQUEST = 830
    private const val DAILY_QUEST_MORNING_REQUEST = 530
    private const val DAILY_QUEST_AFTERNOON_REQUEST = 1430
    private const val DAILY_QUEST_EVENING_REQUEST = 1800
    private const val ACTIVE_START_HOUR = 6
    private const val ACTIVE_END_HOUR = 20
    private const val DANGER_HOUR = 20
    private const val DANGER_MINUTE = 30
    private const val FOUR_HOURS_MS = 4L * 60L * 60L * 1000L

    // The 3 basic "daily quest" reminders: 5:30 AM, 2:30 PM, 6:00 PM.
    private val dailyQuestSlots = listOf(
        Triple(ACTION_DAILY_QUEST_MORNING, 5 to 30, DAILY_QUEST_MORNING_REQUEST),
        Triple(ACTION_DAILY_QUEST_AFTERNOON, 14 to 30, DAILY_QUEST_AFTERNOON_REQUEST),
        Triple(ACTION_DAILY_QUEST_EVENING, 18 to 0, DAILY_QUEST_EVENING_REQUEST)
    )

    fun scheduleAll(context: Context) {
        NotificationHelper.ensureChannels(context)
        scheduleDanger(context)
        scheduleInactivity(context)
        scheduleAllDailyQuests(context)
    }

    private fun dailyQuestNotifsEnabled(context: Context): Boolean =
        context.getSharedPreferences("gate_progress", Context.MODE_PRIVATE)
            .getBoolean("daily_quest_notifs_enabled", true)

    fun scheduleAllDailyQuests(context: Context) {
        if (!dailyQuestNotifsEnabled(context)) return
        dailyQuestSlots.forEach { (action, time, requestCode) ->
            scheduleDailyQuest(context, action, time.first, time.second, requestCode)
        }
    }

    /** Reschedules a single daily-quest slot for the same time tomorrow (self re-arming). */
    fun rescheduleDailyQuest(context: Context, action: String) {
        if (!dailyQuestNotifsEnabled(context)) return
        dailyQuestSlots.firstOrNull { it.first == action }?.let { (a, time, requestCode) ->
            scheduleDailyQuest(context, a, time.first, time.second, requestCode)
        }
    }

    fun scheduleDailyQuest(context: Context, action: String, hour: Int, minute: Int, requestCode: Int) {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(hour, minute)
        if (!target.isAfter(now)) target = target.plusDays(1)
        scheduleAt(context, action, target, requestCode)
    }

    fun scheduleInactivity(context: Context, delayMs: Long = FOUR_HOURS_MS) {
        val now = LocalDateTime.now()
        val target = if (now.toLocalTime().isBefore(LocalTime.of(ACTIVE_START_HOUR, 0))) {
            now.toLocalDate().atTime(9, 0)
        } else if (!now.toLocalTime().isBefore(LocalTime.of(DANGER_HOUR, DANGER_MINUTE))) {
            now.toLocalDate().plusDays(1).atTime(9, 0)
        } else {
            now.plusNanos(delayMs * 1_000_000L)
        }
        scheduleAt(context, ACTION_INACTIVITY, target, INACTIVITY_REQUEST)
    }

    fun scheduleInactivityFromProgress(context: Context, progressAt: Long) {
        val targetMillis = progressAt + FOUR_HOURS_MS
        val now = System.currentTimeMillis()
        val target = if (targetMillis > now) targetMillis else now + 1000L
        val local = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(target), ZoneId.systemDefault())
        if (local.toLocalTime().isBefore(LocalTime.of(ACTIVE_START_HOUR, 0))) {
            scheduleAt(context, ACTION_INACTIVITY, local.toLocalDate().atTime(9, 0), INACTIVITY_REQUEST)
        } else if (!local.toLocalTime().isBefore(LocalTime.of(DANGER_HOUR, DANGER_MINUTE))) {
            scheduleAt(context, ACTION_INACTIVITY, local.toLocalDate().plusDays(1).atTime(9, 0), INACTIVITY_REQUEST)
        } else {
            scheduleMillis(context, ACTION_INACTIVITY, target, INACTIVITY_REQUEST)
        }
    }

    fun scheduleDanger(context: Context) {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(DANGER_HOUR, DANGER_MINUTE)
        if (!target.isAfter(now)) target = target.plusDays(1)
        scheduleAt(context, ACTION_DANGER, target, DANGER_REQUEST)
    }

    private fun scheduleAt(context: Context, action: String, target: LocalDateTime, requestCode: Int) {
        scheduleMillis(context, action, target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), requestCode)
    }

    private fun scheduleMillis(context: Context, action: String, triggerAt: Long, requestCode: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SystemNotificationReceiver::class.java).setAction(action)
        val day = GatePlan.currentDay(LocalDate.now())
        intent.putExtra(EXTRA_DAY, day)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val safeTrigger = maxOf(triggerAt, System.currentTimeMillis() + 500L)
        if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, pending)
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val actionsAndCodes = listOf(
            ACTION_INACTIVITY to INACTIVITY_REQUEST,
            ACTION_DANGER to DANGER_REQUEST
        ) + dailyQuestSlots.map { it.first to it.third }
        actionsAndCodes.forEach { (action, requestCode) ->
            val intent = Intent(context, SystemNotificationReceiver::class.java).setAction(action)
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) alarmManager.cancel(pending)
        }
    }
}
