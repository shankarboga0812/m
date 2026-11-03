package com.chandu.gatesystem.ai2027.alarms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chandu.gatesystem.ai2027.MainActivity
import com.chandu.gatesystem.ai2027.R
import com.chandu.gatesystem.ai2027.domain.AlarmId

object NotificationHelper {
    const val CHANNEL_ALARMS = "gate_alarms"
    const val CHANNEL_PROGRESS = "gate_progress"
    const val ACTION_SNOOZE = "com.chandu.gatesystem.ai2027.SNOOZE"
    const val ACTION_DISMISS = "com.chandu.gatesystem.ai2027.DISMISS"
    const val EXTRA_ALARM = "alarm_id"
    const val EXTRA_SNOOZE_MIN = "snooze_min"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALARMS, "Study alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Wake, workout, revision and sleep reminders from your 114-day plan"; enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Level-ups, rank changes and achievements"
        })
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun immutable() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun showAlarm(ctx: Context, id: AlarmId, title: String, text: String, snoozeMinutes: Int, sound: Boolean, vibrate: Boolean) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(ctx, id.ordinal, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_ALARM, id.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), immutable())
        fun action(code: Int, act: String) = PendingIntent.getBroadcast(ctx, code, Intent(ctx, NotificationActionReceiver::class.java).setAction(act)
            .putExtra(EXTRA_ALARM, id.name).putExtra(EXTRA_SNOOZE_MIN, snoozeMinutes), immutable())
        val b = NotificationCompat.Builder(ctx, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_system).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Snooze ${snoozeMinutes}m", action(300 + id.ordinal, ACTION_SNOOZE))
            .addAction(0, "Dismiss", action(400 + id.ordinal, ACTION_DISMISS))
        var defaults = 0
        if (sound) defaults = defaults or NotificationCompat.DEFAULT_SOUND
        if (vibrate) defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
        b.setDefaults(defaults)
        try { NotificationManagerCompat.from(ctx).notify(1000 + id.ordinal, b.build()) } catch (_: SecurityException) { /* permission revoked meanwhile */ }
    }

    fun cancel(ctx: Context, id: AlarmId) = NotificationManagerCompat.from(ctx).cancel(1000 + id.ordinal)

    fun showProgress(ctx: Context, key: Int, title: String, text: String) {
        if (!canPost(ctx)) return
        val open = PendingIntent.getActivity(ctx, 99, Intent(ctx, MainActivity::class.java), immutable())
        val n = NotificationCompat.Builder(ctx, CHANNEL_PROGRESS).setSmallIcon(R.drawable.ic_stat_system).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(2000 + key, n) } catch (_: SecurityException) { }
    }
}
