package com.chandu.gatesystem.ai2027.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

enum class TopicStatus(val label: String) {
    NOT_STARTED("Not started"), LEARNING("Learning"), PRACTISING("Practising"),
    REVISION_REQUIRED("Revision required"), STRONG("Strong"), MASTERED("Mastered")
}

data class AttemptRecord(
    val questionId: String, val correct: Boolean, val difficulty: Difficulty,
    val date: LocalDate, val isRevision: Boolean = false
)

data class MasteryResult(val status: TopicStatus, val score: Int, val reason: String)

/**
 * Topic mastery depends on actual performance, never on opening a lesson or one lucky answer:
 *  - STRONG   : >=10 DISTINCT questions, difficulty-weighted+recency-weighted accuracy >= 80%,
 *               attempts on >= 2 different days, and the last 5 attempts >= 60% correct.
 *  - MASTERED : >=20 distinct questions, weighted accuracy >= 85%, >= 3 different days,
 *               >=30% medium/hard questions, a correct answer >= 4 days after first attempt
 *               (spaced recall), and no active mistake in the topic.
 *  - REVISION_REQUIRED: had enough data (>=5 distinct) but recent accuracy < 50%, or active mistakes overdue.
 */
object MasteryCalculator {
    const val HALF_LIFE_DAYS = 21.0

    fun compute(attempts: List<AttemptRecord>, lessonOpened: Boolean, activeMistakes: Int, overdueMistakes: Int, today: LocalDate): MasteryResult {
        if (attempts.isEmpty()) {
            return if (lessonOpened) MasteryResult(TopicStatus.LEARNING, 5, "Lesson opened; no questions attempted yet")
            else MasteryResult(TopicStatus.NOT_STARTED, 0, "Nothing done yet")
        }
        // Latest attempt per question counts once (re-attempts of the same question are not new evidence).
        val ordered = attempts.sortedBy { it.date }
        val latestPerQuestion = LinkedHashMap<String, AttemptRecord>()
        for (a in ordered) latestPerQuestion[a.questionId] = a
        val distinct = latestPerQuestion.values.toList()
        var wSum = 0.0; var wCorrect = 0.0
        for (a in distinct) {
            val age = ChronoUnit.DAYS.between(a.date, today).coerceAtLeast(0).toDouble()
            val w = a.difficulty.weight * 0.5.pow(age / HALF_LIFE_DAYS)
            wSum += w; if (a.correct) wCorrect += w
        }
        val acc = if (wSum == 0.0) 0.0 else wCorrect / wSum
        val n = distinct.size
        val days = attempts.map { it.date }.toSet().size
        val last5 = ordered.takeLast(5)
        val last5Acc = last5.count { it.correct }.toDouble() / last5.size
        val nonEasy = distinct.count { it.difficulty != Difficulty.EASY }.toDouble() / n
        val first = ordered.first().date
        val spacedCorrect = ordered.any { it.correct && ChronoUnit.DAYS.between(first, it.date) >= 4 }
        // Score blends accuracy with a confidence factor so a handful of answers can't produce 100.
        val confidence = minOf(1.0, n / 20.0)
        val score = (acc * 100 * (0.4 + 0.6 * confidence)).toInt().coerceIn(0, 100)

        return when {
            n >= 20 && acc >= 0.85 && days >= 3 && nonEasy >= 0.30 && spacedCorrect && activeMistakes == 0 && last5Acc >= 0.6 ->
                MasteryResult(TopicStatus.MASTERED, score, "20+ distinct questions at ${pct(acc)} across $days days with spaced recall")
            n >= 10 && acc >= 0.80 && days >= 2 && last5Acc >= 0.6 && overdueMistakes == 0 ->
                MasteryResult(TopicStatus.STRONG, score, "10+ distinct questions at ${pct(acc)} across $days days")
            n >= 5 && (last5Acc < 0.5 || overdueMistakes > 0) ->
                MasteryResult(TopicStatus.REVISION_REQUIRED, score, if (overdueMistakes > 0) "Overdue mistakes in this topic" else "Recent accuracy ${pct(last5Acc)} is low")
            else -> MasteryResult(TopicStatus.PRACTISING, score, "Needs more varied practice (distinct questions: $n, days: $days, accuracy ${pct(acc)})")
        }
    }
    private fun pct(d: Double) = "${(d * 100).toInt()}%"
}
