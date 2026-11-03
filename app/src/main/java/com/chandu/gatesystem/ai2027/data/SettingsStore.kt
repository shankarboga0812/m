package com.chandu.gatesystem.ai2027.data

import android.content.Context
import com.chandu.gatesystem.ai2027.domain.AiConfig
import com.chandu.gatesystem.ai2027.domain.MarkingPolicy
import com.chandu.gatesystem.ai2027.domain.XpRules

/**
 * App-private settings. The AI backend URL and the *app token* live here (SharedPreferences in the app sandbox,
 * not in source, resources, assets or the repository). The AI provider key and the Telegram bot token are NEVER on the device.
 */
class SettingsStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("gate_ai2027_settings", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = sp.getString("backend_url", "") ?: ""
        set(v) { sp.edit().putString("backend_url", v.trim()).apply() }
    var appToken: String
        get() = sp.getString("app_token", "") ?: ""
        set(v) { sp.edit().putString("app_token", v.trim()).apply() }
    var questsV2: Boolean
        get() = sp.getBoolean("quests_v2", false)
        set(v) { sp.edit().putBoolean("quests_v2", v).apply() }
    var useAiWhenOnline: Boolean
        get() = sp.getBoolean("use_ai", true)
        set(v) { sp.edit().putBoolean("use_ai", v).apply() }
    var playerName: String
        get() = sp.getString("player_name", "Hunter") ?: "Hunter"
        set(v) { sp.edit().putString("player_name", v.trim().ifBlank { "Hunter" }).apply() }
    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) { sp.edit().putBoolean("setup_done", v).apply() }
    var msqPartialCredit: Boolean
        get() = sp.getBoolean("msq_partial", false)
        set(v) { sp.edit().putBoolean("msq_partial", v).apply() }
    var telegramRelay: Boolean
        get() = sp.getBoolean("telegram_relay", false)
        set(v) { sp.edit().putBoolean("telegram_relay", v).apply() }
    var reduceMotion: Boolean
        get() = sp.getBoolean("reduce_motion", false)
        set(v) { sp.edit().putBoolean("reduce_motion", v).apply() }
    var lastConnectionNote: String
        get() = sp.getString("last_conn", "") ?: ""
        set(v) { sp.edit().putString("last_conn", v).apply() }

    fun aiConfig() = AiConfig(backendUrl, appToken)
    fun markingPolicy() = MarkingPolicy(msqPartialCredit)

    fun xpRules(): XpRules {
        val d = XpRules()
        fun i(k: String, def: Int) = sp.getInt("xp_$k", def)
        return XpRules(
            lessonComplete = i("lesson", d.lessonComplete), batchComplete = i("batch", d.batchComplete), batchPerCorrect = i("batchCorrect", d.batchPerCorrect),
            prepSetComplete = i("prep", d.prepSetComplete), revisionSessionComplete = i("rev", d.revisionSessionComplete), revisionPerCorrect = i("revCorrect", d.revisionPerCorrect),
            mistakeOvercome = i("overcome", d.mistakeOvercome), examSetComplete = i("exam", d.examSetComplete), examPerCorrect = i("examCorrect", d.examPerCorrect),
            mockComplete = i("mock", d.mockComplete), mockPerNetMark = i("mockMark", d.mockPerNetMark.toInt()).toDouble(), questComplete = i("quest", d.questComplete),
            dayClear = i("dayClear", d.dayClear), streakBonusPerDay = i("streakDay", d.streakBonusPerDay), streakBonusCap = i("streakCap", d.streakBonusCap),
            subjectMockBase = i("subMockBase", d.subjectMockBase), subjectMockPerMark = i("subMockMark", d.subjectMockPerMark), examPerMark = i("examMark", d.examPerMark),
            bonus60 = i("bonus60", d.bonus60), bonus75 = i("bonus75", d.bonus75), workoutDone = i("workoutDone", d.workoutDone), workoutPartial = i("workoutPartial", d.workoutPartial)
        )
    }
    fun setXpRule(key: String, value: Int) { sp.edit().putInt("xp_$key", value.coerceIn(0, 1000)).apply() }
    fun resetXpRules() { sp.edit().apply { sp.all.keys.filter { it.startsWith("xp_") }.forEach { remove(it) } }.apply() }
}
