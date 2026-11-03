package com.chandu.gatesystem.ai2027.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.chandu.gatesystem.ai2027.data.repo.PlanRepository
import com.chandu.gatesystem.ai2027.data.toConfig
import com.chandu.gatesystem.ai2027.data.db.AppDatabase
import com.chandu.gatesystem.ai2027.domain.*
import java.time.LocalDateTime
import java.time.ZoneId

/** Honest status of what Android currently allows for this app's alarms. */
data class AlarmCapability(val exactAllowed: Boolean, val notificationsAllowed: Boolean, val ignoringBatteryOptimisation: Boolean) {
    val summary: String get() = buildString {
        append(if (exactAllowed) "Exact alarms: allowed. " else "Exact alarms: NOT allowed — Android may delay reminders by several minutes. ")
        append(if (notificationsAllowed) "Notifications: allowed. " else "Notifications: BLOCKED — reminders cannot be shown. ")
        append(if (ignoringBatteryOptimisation) "Battery optimisation: exempt." else "Battery optimisation: on (some phone makers stop background alarms; exempting the app helps).")
    }
}

object AlarmScheduler {
    private fun manager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 || manager(ctx).canScheduleExactAlarms()

    fun capability(ctx: Context): AlarmCapability {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return AlarmCapability(canScheduleExact(ctx), NotificationHelper.canPost(ctx), pm.isIgnoringBatteryOptimizations(ctx.packageName))
    }

    fun exactAlarmSettingsIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    fun notificationSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    private fun pending(ctx: Context, id: AlarmId, snooze: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(ctx, (if (snooze) 200 else 100) + id.ordinal,
            Intent(ctx, AlarmReceiver::class.java).putExtra(NotificationHelper.EXTRA_ALARM, id.name).putExtra("snooze", snooze),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun millis(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Registers one alarm. Uses an exact alarm when permitted, otherwise an inexact one that Android may delay. */
    fun schedule(ctx: Context, id: AlarmId, at: LocalDateTime, snooze: Boolean = false) {
        val am = manager(ctx); val pi = pending(ctx, id, snooze); val t = millis(at)
        try {
            if (canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        } catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi) }
    }

    fun cancel(ctx: Context, id: AlarmId) { manager(ctx).cancel(pending(ctx, id)); manager(ctx).cancel(pending(ctx, id, true)) }

    /** Re-registers every enabled alarm from the saved settings (app start, boot, time change, permission change). */
    suspend fun rescheduleAll(ctx: Context, db: AppDatabase, plan: PlanRepository, now: LocalDateTime = LocalDateTime.now()): List<AlarmPlanner.Planned> {
        val configs = db.player().alarmsOnce().map { it.toConfig() }
        AlarmId.values().forEach { cancel(ctx, it) }
        val planned = AlarmPlanner.plan(configs, now, plan.plan)
        planned.forEach { schedule(ctx, it.id, it.at) }
        return planned
    }
}
