package com.chandu.gatesystem.ai2027.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chandu.gatesystem.ai2027.domain.AlarmId
import com.chandu.gatesystem.ai2027.domain.AlarmPlanner
import java.time.LocalDateTime

/** Snooze / Dismiss buttons on the reminder notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = runCatching { AlarmId.valueOf(intent.getStringExtra(NotificationHelper.EXTRA_ALARM) ?: "") }.getOrNull() ?: return
        NotificationHelper.cancel(context, id)
        if (intent.action == NotificationHelper.ACTION_SNOOZE) {
            val minutes = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MIN, 10).coerceIn(1, 120)
            AlarmScheduler.schedule(context, id, AlarmPlanner.snooze(LocalDateTime.now(), minutes), snooze = true)
        }
    }
}
