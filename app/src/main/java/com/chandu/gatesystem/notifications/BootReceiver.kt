package com.chandu.gatesystem.notifications

import android.content.BroadcastReceiver
import android.content.Context
import com.chandu.gatesystem.widget.TodayWidgetProvider
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SystemNotificationScheduler.scheduleAll(context)
        TodayWidgetProvider.updateAll(context)
    }
}
