package com.chandu.gatesystem.ai2027.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chandu.gatesystem.ai2027.GateApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-registers alarms after reboot, app update, clock/timezone change and exact-alarm permission changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = (context.applicationContext as GateApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try { c.plan.seedIfNeeded(); AlarmScheduler.rescheduleAll(context, c.db, c.plan) } finally { pending.finish() }
        }
    }
}
