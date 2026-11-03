package com.chandu.gatesystem.ai2027.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class MistakeCategory(val label: String) {
    CONCEPT("Conceptual error"), CALCULATION("Calculation error"), MISREAD("Misread question"),
    FORMULA("Formula error"), GUESS("Guessing / elimination error"), TIME("Time management")
}

enum class MistakeStatus { ACTIVE, OVERCOME }

data class MistakeRecord(
    val id: String,
    val dedupeKey: String,            // subject + topic + concept (the underlying error)
    val questionIds: List<String>,    // every question that exposed this error
    val subjectId: String,
    val topicId: String,
    val concept: String,
    val createdDate: LocalDate,
    val userAnswer: String,
    val correctAnswer: String,
    val category: MistakeCategory,
    val explanation: String,
    val occurrences: Int,
    val successfulRevisions: Int,     // independent successes, ever
    val consecutiveSuccess: Int,      // current streak of independent successes
    val succeededQuestionIds: List<String>,
    val status: MistakeStatus,
    val lastAttempted: LocalDate?,
    val lastSuccessDate: LocalDate?,
    val nextRevision: LocalDate?,
    val relatedConcepts: List<String>,
    val source: QSource,
    val notes: String = "",
    val overcomeDate: LocalDate? = null,
    val recurrenceAfterOvercome: Int = 0
)

object MistakeClassifier {
    /**
     * Heuristic only: the learner can always override the category in the UI.
     * - quick wrong choice  -> guess
     * - NAT answer within 15% or digit-transposed -> calculation
     * - unanswered / out of time -> time
     * - otherwise -> concept
     */
    fun suggest(q: Question, resp: Response?, secondsSpent: Int): MistakeCategory {
        if (resp == null || !resp.isAnswered(q.type)) return MistakeCategory.TIME
        if (q.type == QType.NAT && q.nat != null && resp.nat != null) {
            val key = (q.nat.low + q.nat.high) / 2
            val v = resp.nat
            if (key != 0.0 && Math.abs(v - key) / Math.abs(key) <= 0.15) return MistakeCategory.CALCULATION
            if (Question.fmt(v).toList().sorted() == Question.fmt(key).toList().sorted()) return MistakeCategory.CALCULATION
        }
        if (secondsSpent in 1..9) return MistakeCategory.GUESS
        if (q.tags.contains("formula")) return MistakeCategory.FORMULA
        return MistakeCategory.CONCEPT
    }
}

object ErrorMonsterEngine {
    /** Retest within 48 hours when wrong (per the plan: "Weak/error topics return within 48 hours"). */
    const val RETEST_AFTER_WRONG_DAYS = 2L
    /** Next review after the 1st, 2nd independent success. The 3rd success overcomes the mistake. */
    val SUCCESS_LADDER_DAYS = listOf(4L, 7L)
    const val SUCCESSES_TO_OVERCOME = 3
    const val FREQUENT_THRESHOLD = 3

    fun dedupeKey(q: Question) = "${q.subjectId}|${q.topicId}|${QuestionValidator.normalize(q.concept)}"

    fun userAnswerText(q: Question, r: Response?): String = when {
        r == null || !r.isAnswered(q.type) -> "(unanswered)"
        q.type == QType.NAT -> Question.fmt(r.nat!!)
        else -> r.selected.sorted().joinToString(", ") { i -> "${'A' + i}) ${q.options.getOrElse(i) { "?" }}" }
    }

    /** Creates a new record or updates the existing record for the same underlying error. No duplicates. */
    fun onWrong(existing: MistakeRecord?, q: Question, r: Response?, category: MistakeCategory, explanation: String, today: LocalDate, newId: () -> String): MistakeRecord {
        val ua = userAnswerText(q, r)
        val ca = q.correctAnswerText()
        if (existing == null) {
            return MistakeRecord(
                id = newId(), dedupeKey = dedupeKey(q), questionIds = listOf(q.id), subjectId = q.subjectId, topicId = q.topicId,
                concept = q.concept, createdDate = today, userAnswer = ua, correctAnswer = ca, category = category,
                explanation = explanation, occurrences = 1, successfulRevisions = 0, consecutiveSuccess = 0,
                succeededQuestionIds = emptyList(), status = MistakeStatus.ACTIVE, lastAttempted = today, lastSuccessDate = null,
                nextRevision = today.plusDays(RETEST_AFTER_WRONG_DAYS), relatedConcepts = q.tags.filter { it != "formula" },
                source = q.source
            )
        }
        val reopened = existing.status == MistakeStatus.OVERCOME
        return existing.copy(
            questionIds = (existing.questionIds + q.id).distinct(), userAnswer = ua, correctAnswer = ca, category = category,
            explanation = explanation.ifBlank { existing.explanation },
            occurrences = existing.occurrences + 1, consecutiveSuccess = 0, status = MistakeStatus.ACTIVE,
            lastAttempted = today, nextRevision = today.plusDays(RETEST_AFTER_WRONG_DAYS),
            overcomeDate = null, recurrenceAfterOvercome = existing.recurrenceAfterOvercome + if (reopened) 1 else 0
        )
    }

    /**
     * Outcome of a revision attempt on a *new* question testing the same concept.
     * Only an independent success (a question never answered correctly for this mistake before,
     * on a day different from the previous success) advances the streak.
     */
    fun onRevision(m: MistakeRecord, questionId: String, correct: Boolean, today: LocalDate): MistakeRecord {
        if (!correct) {
            return m.copy(consecutiveSuccess = 0, lastAttempted = today, status = MistakeStatus.ACTIVE, overcomeDate = null,
                questionIds = (m.questionIds + questionId).distinct(), occurrences = m.occurrences + 1,
                nextRevision = today.plusDays(RETEST_AFTER_WRONG_DAYS))
        }
        val independent = questionId !in m.succeededQuestionIds && questionId !in m.questionIds
        val newDay = m.lastSuccessDate != today
        if (!independent) return m.copy(lastAttempted = today) // memorised repeat: no evidence
        val succeeded = m.succeededQuestionIds + questionId
        val streak = if (newDay) m.consecutiveSuccess + 1 else m.consecutiveSuccess
        val updated = m.copy(
            successfulRevisions = m.successfulRevisions + 1, succeededQuestionIds = succeeded,
            consecutiveSuccess = streak, lastAttempted = today, lastSuccessDate = today
        )
        return if (streak >= SUCCESSES_TO_OVERCOME) updated.copy(status = MistakeStatus.OVERCOME, nextRevision = null, overcomeDate = today)
        else updated.copy(nextRevision = today.plusDays(SUCCESS_LADDER_DAYS[(streak - 1).coerceIn(0, SUCCESS_LADDER_DAYS.size - 1)]))
    }

    // ---- Filters (all required filter chips) ----
    enum class Filter { ALL, DUE_TODAY, OVERDUE, FREQUENT, CALCULATION, CONCEPTUAL, MISREAD, FORMULA, GUESS, OVERCOME }

    fun filter(all: List<MistakeRecord>, f: Filter, today: LocalDate): List<MistakeRecord> = when (f) {
        Filter.ALL -> all
        Filter.DUE_TODAY -> all.filter { it.status == MistakeStatus.ACTIVE && it.nextRevision == today }
        Filter.OVERDUE -> all.filter { it.status == MistakeStatus.ACTIVE && it.nextRevision?.isBefore(today) == true }
        Filter.FREQUENT -> all.filter { it.occurrences >= FREQUENT_THRESHOLD }
        Filter.CALCULATION -> all.filter { it.category == MistakeCategory.CALCULATION }
        Filter.CONCEPTUAL -> all.filter { it.category == MistakeCategory.CONCEPT }
        Filter.MISREAD -> all.filter { it.category == MistakeCategory.MISREAD }
        Filter.FORMULA -> all.filter { it.category == MistakeCategory.FORMULA }
        Filter.GUESS -> all.filter { it.category == MistakeCategory.GUESS }
        Filter.OVERCOME -> all.filter { it.status == MistakeStatus.OVERCOME }
    }

    data class Dashboard(
        val active: Int, val dueToday: Int, val overdue: Int, val repeated: Int, val recentlyOvercome: Int,
        val topErrorTopics: List<Pair<String, Int>>, val resolutionRate: Double
    )

    fun dashboard(all: List<MistakeRecord>, today: LocalDate): Dashboard {
        val active = all.filter { it.status == MistakeStatus.ACTIVE }
        return Dashboard(
            active = active.size,
            dueToday = active.count { it.nextRevision == today },
            overdue = active.count { it.nextRevision?.isBefore(today) == true },
            repeated = all.count { it.occurrences >= FREQUENT_THRESHOLD },
            recentlyOvercome = all.count { it.status == MistakeStatus.OVERCOME && it.overcomeDate != null && ChronoUnit.DAYS.between(it.overcomeDate, today) <= 7 },
            topErrorTopics = all.groupBy { it.topicId }.map { (t, l) -> t to l.sumOf { it.occurrences } }.sortedByDescending { it.second }.take(5),
            resolutionRate = if (all.isEmpty()) 0.0 else all.count { it.status == MistakeStatus.OVERCOME }.toDouble() / all.size
        )
    }
}
