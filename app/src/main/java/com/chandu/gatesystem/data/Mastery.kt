package com.chandu.gatesystem.data

import android.content.Context

/**
 * Manual subject mastery tracking (0-100%). There is no auto-graded question
 * bank in this app, so mastery is a self-reported number you nudge up/down
 * after each study session — the Weak Topic Detector and Boss Battle windows
 * are both derived from these same numbers, so keeping them honest keeps the
 * whole "what should I study next" picture accurate.
 */
object Mastery {
    val subjects = listOf(
        "Digital Logic",
        "Computer Organization & Architecture",
        "Data Structures & Algorithms",
        "Operating Systems",
        "Database Management Systems",
        "Theory of Computation",
        "Compiler Design",
        "Computer Networks",
        "Engineering Mathematics",
        "General Aptitude"
    )

    private const val PREFS = "gate_progress"
    private fun keyFor(subject: String) = "mastery_pct_${subject.hashCode()}"

    fun get(context: Context, subject: String): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(keyFor(subject), 0).coerceIn(0, 100)
    }

    fun set(context: Context, subject: String, value: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(keyFor(subject), value.coerceIn(0, 100)).apply()
    }

    fun all(context: Context): List<Pair<String, Int>> = subjects.map { it to get(context, it) }

    /** Subjects below [threshold]%, weakest first — this feeds the Weak Topic Detector. */
    fun weakest(context: Context, threshold: Int = 60): List<Pair<String, Int>> =
        all(context).filter { it.second < threshold }.sortedBy { it.second }

    fun overallReadiness(context: Context): Int {
        val values = subjects.map { get(context, it) }
        return if (values.isEmpty()) 0 else values.sum() / values.size
    }
}
