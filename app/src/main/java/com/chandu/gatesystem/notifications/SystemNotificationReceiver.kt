package com.chandu.gatesystem.notifications

import com.chandu.gatesystem.data.GatePlan
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SystemNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("gate_progress", Context.MODE_PRIVATE)
        val day = GatePlan.currentDay(LocalDate.now()).coerceIn(1, 150)
        val done = prefs.getInt("day_${day}_done", GatePlan.baselineDoneFor(day)).coerceIn(0, 6)
        val action = intent.action

        when (action) {
            SystemNotificationScheduler.ACTION_DANGER -> {
                if (done < 6) {
                    NotificationHelper.showDanger(context, day, 6 - done)
                    prefs.edit().putLong("danger_fired_${LocalDate.now()}", System.currentTimeMillis()).apply()
                }
                SystemNotificationScheduler.scheduleDanger(context)
            }
            SystemNotificationScheduler.ACTION_INACTIVITY -> {
                val now = System.currentTimeMillis()
                val lastProgress = prefs.getLong("last_progress_at", dayStartMillis())
                val lastWarning = prefs.getLong("last_inactivity_warning", 0L)
                val activeTime = LocalTime.now()
                val daytime = activeTime >= LocalTime.of(6, 0) && activeTime < LocalTime.of(20, 30)
                if (done < 6 && daytime && now - lastProgress >= 4L * 60L * 60L * 1000L && now - lastWarning >= 4L * 60L * 60L * 1000L) {
                    NotificationHelper.showInactivity(context, day)
                    prefs.edit().putLong("last_inactivity_warning", now).apply()
                    SystemNotificationScheduler.scheduleInactivity(context)
                } else {
                    SystemNotificationScheduler.scheduleInactivityFromProgress(context, lastProgress)
                }
            }
            SystemNotificationScheduler.ACTION_DAILY_QUEST_MORNING,
            SystemNotificationScheduler.ACTION_DAILY_QUEST_AFTERNOON,
            SystemNotificationScheduler.ACTION_DAILY_QUEST_EVENING -> {
                val slotLabel = when (action) {
                    SystemNotificationScheduler.ACTION_DAILY_QUEST_MORNING -> "Morning"
                    SystemNotificationScheduler.ACTION_DAILY_QUEST_AFTERNOON -> "Afternoon"
                    else -> "Evening"
                }
                val quest = GatePlan.forDay(day)
                val summary = "${quest.title} • Primary: ${quest.primarySubject} • GA slot ${quest.ga.cycleSlot}/3 • $done/6 objectives done"
                NotificationHelper.showDailyQuest(context, day, slotLabel, summary)
                SystemNotificationScheduler.rescheduleDailyQuest(context, action)
            }
        }
    }

    private fun dayStartMillis(): Long {
        val today = LocalDate.now()
        return today.atTime(5, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
