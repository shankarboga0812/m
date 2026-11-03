package com.chandu.gatesystem.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.chandu.gatesystem.ai2027.R
import android.os.VibratorManager
import androidx.annotation.RawRes

class SoundManager(private val context: Context) {
    // Sound mapping (per instructions):
    //  - System open, level up, and selecting  -> arise_system.wav
    //  - Notifications and gaining new things  -> arise.wav
    //  - Everything else                       -> level_up_notification.wav
    enum class Event(@RawRes val resId: Int) {
        LEVEL_UP(R.raw.arise_system),        // level up
        ARISE(R.raw.arise),                  // notification
        ARISE_SYSTEM(R.raw.arise_system),    // system open / selecting
        SYSTEM_BOOT(R.raw.arise_system),     // system open
        QUEST_APPEAR(R.raw.arise_system),    // selecting
        RNR_DUE(R.raw.level_up_notification),// other
        WARNING(R.raw.level_up_notification),// other
        RANK_UP(R.raw.arise_system),         // level up (rank up)
        CHECKBOX_TICK(R.raw.arise),          // gaining new things (objective progress)
        EXP_GAIN(R.raw.arise),               // gaining new things
        GOLD_GAIN(R.raw.arise)               // gaining new things
    }

    var enabled = true
    var vibrationEnabled = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sounds = Event.entries.associateWith { pool.load(context, it.resId, 1) }

    // Level-up/rank-up are the two most important moments in the app (they're the
    // whole point of the game loop), so a failure in sound or vibration must NEVER
    // take the app down with it. Every effect here is best-effort and self-contained.
    fun play(event: Event) {
        if (!enabled) return
        try {
            sounds[event]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
        } catch (t: Throwable) {
            // Sound is cosmetic — swallow and continue, never crash the study app over it.
        }
        if (vibrationEnabled && (event == Event.LEVEL_UP || event == Event.RANK_UP)) {
            try {
                vibrate(120)
            } catch (t: Throwable) {
                // Same reasoning: vibration is cosmetic, must never crash level-up.
            }
        }
    }

    private fun vibrate(duration: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return // no vibrator hardware/service on this device — skip silently

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(duration)
        }
    }

    fun release() = try { pool.release() } catch (t: Throwable) { /* no-op */ }
}
