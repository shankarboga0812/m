package com.chandu.gatesystem.ai2027.domain

/**
 * Preparation / Revision set flow: questions are fixed when the set is created, delivered five at a time,
 * and a batch is graded only when submitted. Answers of the open batch are kept in [PracticeState.answers]
 * (persisted as drafts) so closing the app never loses them.
 */
data class PracticeState(
    val questionIds: List<String>,
    val batchSize: Int = 5,
    val submittedBatches: Int = 0,
    val answers: Map<String, Response> = emptyMap(),
    val times: Map<String, Int> = emptyMap(),
    val results: Map<String, AnswerResult> = emptyMap()
) {
    val batchCount get() = (questionIds.size + batchSize - 1) / batchSize
    val isComplete get() = submittedBatches >= batchCount
    val currentBatchIds: List<String>
        get() = if (isComplete) emptyList() else questionIds.drop(submittedBatches * batchSize).take(batchSize)
    val answeredSubmitted get() = results.size
    /** Index of the first question of the current batch (1-based number for the UI). */
    val nextQuestionNumber get() = submittedBatches * batchSize + 1
}

data class BatchOutcome(val state: PracticeState, val batchIndex: Int, val outcomes: List<QuestionOutcome>, val correct: Int, val marks: Double)

object PracticeFlow {
    fun start(ids: List<String>, batchSize: Int = 5): PracticeState {
        require(ids.isNotEmpty()) { "A practice set needs at least one question" }
        require(ids.toSet().size == ids.size) { "Duplicate question in set" }
        return PracticeState(ids, batchSize)
    }

    fun answer(s: PracticeState, id: String, r: Response): PracticeState {
        require(id in s.currentBatchIds) { "Question $id is not in the unlocked batch" }
        return s.copy(answers = s.answers + (id to r))
    }

    fun addTime(s: PracticeState, id: String, seconds: Int) = s.copy(times = s.times + (id to (s.times[id] ?: 0) + seconds))

    fun submitBatch(s: PracticeState, byId: Map<String, Question>, policy: MarkingPolicy = MarkingPolicy()): BatchOutcome {
        require(!s.isComplete) { "Set already completed" }
        val ids = s.currentBatchIds
        val outcomes = ids.map { id ->
            val q = byId.getValue(id)
            QuestionOutcome(q, s.answers[id], Scorer.score(q, s.answers[id], policy), s.times[id] ?: 0)
        }
        val newResults = s.results + outcomes.associate { it.question.id to it.result }
        val ns = s.copy(submittedBatches = s.submittedBatches + 1, results = newResults)
        return BatchOutcome(ns, s.submittedBatches, outcomes, outcomes.count { it.result.verdict == Verdict.CORRECT }, outcomes.sumOf { it.result.marks })
    }

    fun finalReport(s: PracticeState, byId: Map<String, Question>, policy: MarkingPolicy = MarkingPolicy()): SetReport {
        require(s.isComplete) { "Set not finished" }
        return SetScorer.evaluate(s.questionIds.map { byId.getValue(it) }, s.answers, s.times, emptySet(), policy)
    }
}

enum class NavStatus { NOT_VISITED, UNANSWERED, ANSWERED, MARKED, ANSWERED_MARKED }

/** Timed exam: all questions at once; the question list/keys are frozen at start. */
data class ExamState(
    val questionIds: List<String>,
    val durationSec: Int,
    val remainingSec: Int,
    val responses: Map<String, Response> = emptyMap(),
    val marked: Set<String> = emptySet(),
    val visited: Set<String> = emptySet(),
    val timeSpent: Map<String, Int> = emptyMap(),
    val submitted: Boolean = false
) {
    val timeUp get() = remainingSec <= 0
    fun status(id: String, q: Question): NavStatus = status(id, q.type)
    fun status(id: String, type: QType): NavStatus {
        val answered = responses[id]?.isAnswered(type) == true
        val m = id in marked
        return when {
            answered && m -> NavStatus.ANSWERED_MARKED
            m -> NavStatus.MARKED
            answered -> NavStatus.ANSWERED
            id in visited -> NavStatus.UNANSWERED
            else -> NavStatus.NOT_VISITED
        }
    }
}

object ExamFlow {
    fun start(ids: List<String>, durationSec: Int): ExamState {
        require(ids.toSet().size == ids.size) { "Duplicate question in exam" }
        return ExamState(ids, durationSec, durationSec)
    }
    private fun live(s: ExamState) { require(!s.submitted) { "Exam already submitted" } }

    fun visit(s: ExamState, id: String) = s.copy(visited = s.visited + id)
    fun answer(s: ExamState, id: String, r: Response): ExamState { live(s); return s.copy(responses = s.responses + (id to r), visited = s.visited + id) }
    fun clear(s: ExamState, id: String): ExamState { live(s); return s.copy(responses = s.responses - id) }
    fun toggleMark(s: ExamState, id: String): ExamState { live(s); return s.copy(marked = if (id in s.marked) s.marked - id else s.marked + id) }
    fun tick(s: ExamState, seconds: Int, currentId: String?): ExamState {
        if (s.submitted) return s
        val spent = if (currentId != null) s.timeSpent + (currentId to (s.timeSpent[currentId] ?: 0) + seconds) else s.timeSpent
        return s.copy(remainingSec = maxOf(0, s.remainingSec - seconds), timeSpent = spent)
    }
    fun submit(s: ExamState, byId: Map<String, Question>, policy: MarkingPolicy = MarkingPolicy()): Pair<ExamState, SetReport> {
        live(s)
        val report = SetScorer.evaluate(s.questionIds.map { byId.getValue(it) }, s.responses, s.timeSpent, s.marked, policy)
        return s.copy(submitted = true) to report
    }
    fun summary(s: ExamState, byId: Map<String, Question>): Map<NavStatus, Int> =
        s.questionIds.groupingBy { s.status(it, byId.getValue(it)) }.eachCount()
}
