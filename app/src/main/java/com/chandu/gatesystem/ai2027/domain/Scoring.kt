package com.chandu.gatesystem.ai2027.domain

import kotlin.math.abs
import kotlin.math.round

/**
 * Marking rules (GATE standard, configurable):
 *  - MCQ: +marks if correct; wrong answer costs q.negativeMarks (1/3 for 1 mark, 2/3 for 2 marks).
 *  - MSQ: full marks only if the selected set equals the correct set; no negative marking.
 *         (Optional partial credit exists only when [msqPartialCredit] is enabled; it is OFF by default.)
 *  - NAT: full marks if within the accepted range; no negative marking.
 * Negative marks are taken from each question so they remain configurable per question.
 */
data class MarkingPolicy(val msqPartialCredit: Boolean = false)

object Scorer {
    fun r(x: Double): Double = round(x * 1_000_000.0) / 1_000_000.0

    fun defaultNegative(type: QType, marks: Int): Double =
        if (type == QType.MCQ) r(marks / 3.0) else 0.0

    fun score(q: Question, resp: Response?, policy: MarkingPolicy = MarkingPolicy()): AnswerResult {
        if (resp == null || !resp.isAnswered(q.type)) return AnswerResult(Verdict.UNANSWERED, 0.0)
        return when (q.type) {
            QType.MCQ ->
                if (resp.selected == q.correctOptions) AnswerResult(Verdict.CORRECT, q.marks.toDouble())
                else AnswerResult(Verdict.WRONG, -q.negativeMarks)
            QType.MSQ -> {
                if (resp.selected == q.correctOptions) AnswerResult(Verdict.CORRECT, q.marks.toDouble())
                else if (policy.msqPartialCredit && resp.selected.all { it in q.correctOptions }) {
                    AnswerResult(Verdict.WRONG, r(q.marks.toDouble() * resp.selected.size / q.correctOptions.size))
                } else AnswerResult(Verdict.WRONG, 0.0)
            }
            QType.NAT ->
                if (q.nat != null && resp.nat != null && q.nat.accepts(resp.nat)) AnswerResult(Verdict.CORRECT, q.marks.toDouble())
                else AnswerResult(Verdict.WRONG, 0.0)
        }
    }
}

data class QuestionOutcome(
    val question: Question, val response: Response?, val result: AnswerResult,
    val timeSpentSec: Int = 0, val markedForReview: Boolean = false
)

data class GroupStat(val key: String, val total: Int, val attempted: Int, val correct: Int, val marksEarned: Double, val marksPossible: Double) {
    val accuracy get() = if (attempted == 0) 0.0 else correct.toDouble() / attempted
}

data class SetReport(
    val total: Int, val attempted: Int, val correct: Int, val incorrect: Int, val unanswered: Int,
    val positiveMarks: Double, val negativeMarks: Double, val netMarks: Double, val maxMarks: Double,
    val accuracy: Double,           // correct / attempted
    val timeSpentSec: Int,
    val byTopic: List<GroupStat>, val bySubject: List<GroupStat>, val byDifficulty: List<GroupStat>,
    val needsReview: List<String>, // question ids: wrong, unanswered or marked
    val outcomes: List<QuestionOutcome>
) {
    val percent get() = if (maxMarks == 0.0) 0.0 else netMarks / maxMarks * 100.0
}

object SetScorer {
    fun build(outcomes: List<QuestionOutcome>): SetReport {
        var pos = 0.0; var neg = 0.0; var max = 0.0
        var correct = 0; var wrong = 0; var un = 0; var time = 0
        for (o in outcomes) {
            max += o.question.marks; time += o.timeSpentSec
            when (o.result.verdict) {
                Verdict.CORRECT -> { correct++; pos += o.result.marks }
                Verdict.WRONG -> { wrong++; if (o.result.marks < 0) neg += -o.result.marks else pos += o.result.marks }
                Verdict.UNANSWERED -> un++
            }
        }
        fun group(keyOf: (QuestionOutcome) -> String) = outcomes.groupBy(keyOf).map { (k, v) ->
            GroupStat(k, v.size, v.count { it.result.verdict != Verdict.UNANSWERED }, v.count { it.result.verdict == Verdict.CORRECT },
                Scorer.r(v.sumOf { it.result.marks }), v.sumOf { it.question.marks }.toDouble())
        }.sortedBy { it.key }
        val attempted = correct + wrong
        return SetReport(
            total = outcomes.size, attempted = attempted, correct = correct, incorrect = wrong, unanswered = un,
            positiveMarks = Scorer.r(pos), negativeMarks = Scorer.r(neg), netMarks = Scorer.r(pos - neg), maxMarks = max,
            accuracy = if (attempted == 0) 0.0 else correct.toDouble() / attempted,
            timeSpentSec = time,
            byTopic = group { it.question.topicId }, bySubject = group { it.question.subjectId },
            byDifficulty = group { it.question.difficulty.name },
            needsReview = outcomes.filter { it.result.verdict != Verdict.CORRECT || it.markedForReview }.map { it.question.id },
            outcomes = outcomes
        )
    }

    fun evaluate(questions: List<Question>, responses: Map<String, Response>, times: Map<String, Int> = emptyMap(),
                 marked: Set<String> = emptySet(), policy: MarkingPolicy = MarkingPolicy()): SetReport =
        build(questions.map { q ->
            QuestionOutcome(q, responses[q.id], Scorer.score(q, responses[q.id], policy), times[q.id] ?: 0, q.id in marked)
        })
}

/** Parses student NAT text such as "12", "3.50", "-4", "1/2". Returns null when not numeric. */
object NatInput {
    fun parse(text: String): Double? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if ('/' in t) {
            val p = t.split('/')
            if (p.size != 2) return null
            val a = p[0].trim().toDoubleOrNull(); val b = p[1].trim().toDoubleOrNull()
            return if (a != null && b != null && b != 0.0) a / b else null
        }
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isNaN() || v.isInfinite()) null else v
    }
    fun closeEnough(a: Double, b: Double, relTol: Double) = abs(a - b) <= relTol * maxOf(1.0, abs(b))
}
