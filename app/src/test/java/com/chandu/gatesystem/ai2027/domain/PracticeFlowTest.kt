package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.mcq
import org.junit.Assert.*
import org.junit.Test

class PracticeFlowTest {
    private val qs = (1..30).map { mcq("q$it") }
    private val byId = qs.associateBy { it.id }

    @Test fun thirtyQuestionsMakeSixBatchesOfFive() {
        var s = PracticeFlow.start(qs.map { it.id })
        assertEquals(6, s.batchCount); assertEquals(5, s.currentBatchIds.size); assertEquals(listOf("q1", "q2", "q3", "q4", "q5"), s.currentBatchIds)
        for (b in 0 until 6) {
            assertEquals(5, s.currentBatchIds.size); assertEquals(b * 5 + 1, s.nextQuestionNumber)
            s.currentBatchIds.forEach { s = PracticeFlow.answer(s, it, Response(setOf(0))) }
            val out = PracticeFlow.submitBatch(s, byId); s = out.state; assertEquals(5, out.correct); assertEquals(b, out.batchIndex)
        }
        assertTrue(s.isComplete); assertTrue(s.currentBatchIds.isEmpty())
        val rep = PracticeFlow.finalReport(s, byId); assertEquals(30, rep.total); assertEquals(30, rep.correct)
    }
    @Test fun cannotAnswerLockedQuestions() {
        val s = PracticeFlow.start(qs.map { it.id })
        assertThrows(IllegalArgumentException::class.java) { PracticeFlow.answer(s, "q6", Response(setOf(0))) }
    }
    @Test fun batchSubmitScoresMixedResultsAndUnanswered() {
        var s = PracticeFlow.start(qs.map { it.id })
        s = PracticeFlow.answer(s, "q1", Response(setOf(0))); s = PracticeFlow.answer(s, "q2", Response(setOf(2)))
        val o = PracticeFlow.submitBatch(s, byId)
        assertEquals(1, o.correct); assertEquals(1.0 - 1.0 / 3, o.marks, 1e-5)
        assertEquals(3, o.outcomes.count { it.result.verdict == Verdict.UNANSWERED })
    }
    @Test fun resumeKeepsDraftAnswersAndSubmittedBatches() {
        var s = PracticeFlow.start(qs.map { it.id })
        s.currentBatchIds.forEach { s = PracticeFlow.answer(s, it, Response(setOf(0))) }
        s = PracticeFlow.submitBatch(s, byId).state
        s = PracticeFlow.answer(s, "q6", Response(setOf(1)))
        // simulate process death: only the data-class values survive (the DB stores exactly these)
        val restored = s.copy()
        assertEquals(1, restored.submittedBatches); assertEquals(6, restored.nextQuestionNumber)
        assertEquals(Response(setOf(1)), restored.answers["q6"]); assertEquals(5, restored.results.size)
    }
    @Test fun cannotResubmitCompletedSetAndRejectsDuplicates() {
        var s = PracticeFlow.start(listOf("q1", "q2", "q3", "q4", "q5"))
        s = PracticeFlow.submitBatch(s, byId).state; assertTrue(s.isComplete)
        assertThrows(IllegalArgumentException::class.java) { PracticeFlow.submitBatch(s, byId) }
        assertThrows(IllegalArgumentException::class.java) { PracticeFlow.start(listOf("q1", "q1")) }
    }
    @Test fun partialLastBatchFor12QuestionPlanSets() {
        var s = PracticeFlow.start(qs.take(12).map { it.id }); assertEquals(3, s.batchCount)
        repeat(2) { s = PracticeFlow.submitBatch(s, byId).state }
        assertEquals(2, s.currentBatchIds.size)
    }
}
