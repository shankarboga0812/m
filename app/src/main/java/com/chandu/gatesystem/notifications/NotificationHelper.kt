package com.chandu.gatesystem.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chandu.gatesystem.MainActivity
import com.chandu.gatesystem.ai2027.R

/**
 * v2 -- rebuilt for reliability.
 *
 * The old version used a custom RemoteViews "glowing frame" layout
 * (DecoratedCustomViewStyle). On some devices -- budget / Android-Go phones
 * like the Nokia G11 Plus in particular -- a custom RemoteViews layout can
 * silently fail to inflate. When that happens the notification still gets
 * POSTED (so it shows up in notification history) but it renders with no
 * content and does NOT show as a heads-up popup. That matches exactly what
 * was reported: entries in history with no preview text, no popup.
 *
 * This version uses Android's plain built-in NotificationCompat.BigTextStyle
 * -- no custom views, nothing that can fail to inflate. The alarm-style
 * channels (level up, danger, inactivity) are IMPORTANCE_HIGH with arise.wav
 * for heads-up delivery. The Daily Quest channel (5:30 AM / 2:30 PM / 6:00 PM)
 * is a plain IMPORTANCE_DEFAULT basic notification with the system default
 * sound, on purpose -- it's a routine reminder, not an alarm.
 */
object NotificationHelper {

    private const val CH_LEVEL_UP = "system_alerts_levelup_v2"
    private const val CH_DANGER = "system_alerts_danger_v2"
    private const val CH_INACTIVITY = "system_alerts_inactivity_v2"
    private const val CH_DAILY_QUEST = "system_alerts_dailyquest_v1"

    private const val INACTIVITY_ID = 632401
    private const val DANGER_ID = 632830
    private const val LEVEL_UP_ID = 632950
    private const val DAILY_QUEST_ID = 633000

    // NOTE: channel ids above end in "_v2" on purpose. Android locks a
    // channel's importance/sound forever once it's first created -- the app
    // can never change it again in code. Using new ids forces Android to
    // create brand-new channels instead of reusing old ones that might be
    // stuck at the wrong importance from a previous install. You do NOT need
    // to uninstall the app for this version -- new channel ids guarantee a
    // clean slate automatically.

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val ariseSound: Uri = Uri.parse("android.resource://${context.packageName}/raw/arise")

        fun channel(id: String, name: String, desc: String): NotificationChannel =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = desc
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250, 120, 250)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(ariseSound, attrs)
                setBypassDnd(false)
            }

        nm.createNotificationChannel(channel(CH_LEVEL_UP, "System \u2014 Level Up", "Quest cleared / rank up"))
        nm.createNotificationChannel(channel(CH_DANGER, "System \u2014 Danger", "8:30 PM incomplete-quest warning"))
        nm.createNotificationChannel(channel(CH_INACTIVITY, "System \u2014 Inactivity", "No progress / daily quest not ticked"))

        // Plain basic notification channel -- default importance, default system
        // notification sound, no forced full-screen/alarm behavior. Used for the
        // three daily quest reminders (5:30 AM / 2:30 PM / 6:00 PM).
        if (nm.getNotificationChannel(CH_DAILY_QUEST) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_DAILY_QUEST, "System \u2014 Daily Quest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Basic daily quest reminders at 5:30 AM, 2:30 PM and 6:00 PM"
                }
            )
        }
    }

    fun showInactivity(context: Context, day: Int) {
        show(
            context, CH_INACTIVITY, INACTIVITY_ID,
            title = "\u26a0 DAILY QUEST NOT TICKED",
            text = "Day $day/150 \u2014 no progress detected. Open GATE SYSTEM and tick today's quest.",
            fullScreen = true
        )
    }

    fun showDanger(context: Context, day: Int, remaining: Int) {
        show(
            context, CH_DANGER, DANGER_ID,
            title = "\u26a0 QUEST INCOMPLETE \u2014 8:30 PM",
            text = "Day $day/150 \u2014 $remaining objectives remaining. Failure risk: HIGH.",
            fullScreen = true
        )
    }

    /** Plain basic notification for the 5:30 AM / 2:30 PM / 6:00 PM daily quest reminders. */
    fun showDailyQuest(context: Context, day: Int, slotLabel: String, summary: String) {
        show(
            context, CH_DAILY_QUEST, DAILY_QUEST_ID,
            title = "Day $day/150 \u2014 $slotLabel Quest Reminder",
            text = summary,
            fullScreen = false
        )
    }

    fun showLevelUp(context: Context, newLevel: Int, rank: String) {
        show(
            context, CH_LEVEL_UP, LEVEL_UP_ID,
            title = "\u2b06 LEVEL UP \u2014 LEVEL $newLevel",
            text = "Rank: $rank",
            fullScreen = true
        )
    }

    private fun show(context: Context, channelId: String, id: Int, title: String, text: String, fullScreen: Boolean) {
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.EXTRA_OPEN_QUEST, true)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_system_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (fullScreen) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(pending)

        if (fullScreen) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pending, true)
        }

        if (Build.VERSION.SDK_INT < 26) {
            // Pre-Oreo devices have no channels; sound/vibration/priority must
            // be set directly on the builder itself.
            if (fullScreen) {
                builder.setSound(Uri.parse("android.resource://${context.packageName}/raw/arise"))
                    .setVibrate(longArrayOf(0, 250, 120, 250, 120, 250))
                    .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            } else {
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            }
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT < 33 || NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
