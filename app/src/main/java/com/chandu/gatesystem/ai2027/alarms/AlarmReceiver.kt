package com.chandu.gatesystem.ai2027.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chandu.gatesystem.ai2027.GateApp
import com.chandu.gatesystem.ai2027.data.toConfig
import com.chandu.gatesystem.ai2027.domain.AlarmId
import com.chandu.gatesystem.ai2027.domain.AlarmPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Fires when a scheduled alarm time arrives: shows the notification and registers the next occurrence. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = runCatching { AlarmId.valueOf(intent.getStringExtra(NotificationHelper.EXTRA_ALARM) ?: "") }.getOrNull() ?: return
        val isSnooze = intent.getBooleanExtra("snooze", false)
        val pending = goAsync()
        val container = (context.applicationContext as GateApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.plan.seedIfNeeded()
                val cfg = container.db.player().alarmsOnce().map { it.toConfig() }.firstOrNull { it.id == id }
                if (cfg != null && (cfg.enabled || isSnooze)) {
                    val (title, text) = AlarmContent.build(container, id, container.clock.today())
                    NotificationHelper.showAlarm(context, id, title, text, cfg.snoozeMinutes, cfg.sound, cfg.vibrate)
                }
                if (!isSnooze && cfg != null && cfg.schedulable) {
                    val next = AlarmPlanner.plan(listOf(cfg), LocalDateTime.now().plusSeconds(5), container.plan.plan).firstOrNull()
                    if (next != null) AlarmScheduler.schedule(context, id, next.at)
                }
            } finally { pending.finish() }
        }
    }
}
