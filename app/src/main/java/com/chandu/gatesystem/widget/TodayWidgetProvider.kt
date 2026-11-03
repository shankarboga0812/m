package com.chandu.gatesystem.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chandu.gatesystem.ai2027.R
import com.chandu.gatesystem.MainActivity
import com.chandu.gatesystem.data.GatePlan
import java.time.LocalDate

/**
 * Home-screen widget: shows today's quest topics + today's workout + objective
 * progress at a glance, so the player doesn't need to open the app and scroll
 * to find "what do I do today". Tapping it opens straight to the Quest screen.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, TodayWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("gate_progress", Context.MODE_PRIVATE)
            val day = GatePlan.currentDay(LocalDate.now())
            val quest = GatePlan.forDay(day)
            val done = prefs.getInt("day_${day}_done", GatePlan.baselineDoneFor(day)).coerceIn(0, 6)
            val workout = GatePlan.workoutFor(LocalDate.now())

            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_header, "⚔ TODAY'S QUEST • DAY $day/150")
            views.setTextViewText(R.id.widget_title, quest.title)
            views.setTextViewText(R.id.widget_topic, "${quest.primarySubject}: ${quest.primaryTopic}")
            views.setTextViewText(R.id.widget_workout, "♨ WORKOUT — $workout")
            views.setTextViewText(R.id.widget_progress, "Objectives: $done / 6")

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(MainActivity.EXTRA_OPEN_QUEST, true)
            }
            val pending = PendingIntent.getActivity(
                context, 5500, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, pending)
            views.setOnClickPendingIntent(R.id.widget_title, pending)
            views.setOnClickPendingIntent(R.id.widget_workout, pending)
            views.setOnClickPendingIntent(R.id.widget_progress, pending)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
