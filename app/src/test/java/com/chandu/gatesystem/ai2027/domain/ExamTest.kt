package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.mcq
import com.chandu.gatesystem.ai2027.domain.TestSupport.msq
import com.chandu.gatesystem.ai2027.domain.TestSupport.nat
import org.junit.Assert.*
import org.junit.Test

class ExamTest {
    private fun paper(): List<Question> {
        val l = ArrayList<Question>()
        repeat(20) { l += mcq("m$it", marks = if (it % 2 == 0) 1 else 2) }
        repeat(4) { l += msq("s$it") }
        repeat(6) { l += nat("n$it", marks = 2) }
        return l
    }

    @Test fun scoreUsesAssignedMarksNotAssumed100() {
        val qs = paper(); val byId = qs.associateBy { it.id }
        val max = qs.sumOf { it.marks }.toDouble()
        assertEquals(10 * 1 + 10 * 2 + 4 * 2 + 6 * 2.0, max, 0.0)
        // answer everything correctly
        val resp = qs.associate { q -> q.id to when (q.type) { QType.MCQ -> Response(q.correctOptions); QType.MSQ -> Response(q.correctOptions); QType.NAT -> Response(nat = 10.0) } }
        val rep = SetScorer.evaluate(qs, resp)
        assertEquals(max, rep.netMarks, 1e-9); assertEquals(max, rep.maxMarks, 0.0); assertEquals(30, rep.correct); assertEquals(1.0, rep.accuracy, 1e-9)
        assertNotEquals(100.0, rep.maxMarks)
    }
    @Test fun negativeMarkingOnlyOnWrongMcq() {
        val qs = paper()
        val resp = HashMap<String, Response>()
        resp["m0"] = Response(setOf(1))      // wrong 1-mark MCQ  -> -1/3
        resp["m1"] = Response(setOf(2))      // wrong 2-mark MCQ  -> -2/3
        resp["s0"] = Response(setOf(1))      // wrong MSQ         -> 0
        resp["n0"] = Response(nat = 99.0)    // wrong NAT         -> 0
        resp["m2"] = Response(setOf(0))      // right 1-mark      -> +1
        val rep = SetScorer.evaluate(qs, resp)
        assertEquals(1, rep.correct); assertEquals(4, rep.incorrect); assertEquals(25, rep.unanswered)
        assertEquals(1.0 / 3 + 2.0 / 3, rep.negativeMarks, 1e-5); assertEquals(1.0, rep.positiveMarks, 1e-9)
        assertEquals(0.0, rep.netMarks, 1e-5)
        assertEquals(0.2, rep.accuracy, 1e-9) // 1 correct of 5 attempted
        assertTrue("m0" in rep.needsReview && "m2" !in rep.needsReview)
    }
    @Test fun reportGroupsByTopicAndDifficulty() {
        val a = mcq("a", topic = "T1"); val b = mcq("b", topic = "T2")
        val rep = SetScorer.evaluate(listOf(a, b), mapOf("a" to Response(setOf(0)), "b" to Response(setOf(1))))
        assertEquals(2, rep.byTopic.size); assertEquals(1, rep.byTopic.first { it.key == "T1" }.correct)
        assertEquals(1, rep.byDifficulty.size)
    }
    @Test fun examNavigationAndStatuses() {
        val qs = paper(); val byId = qs.associateBy { it.id }
        var s = ExamFlow.start(qs.map { it.id }, 3600)
        assertEquals(NavStatus.NOT_VISITED, s.status("m0", byId["m0"]!!))
        s = ExamFlow.visit(s, "m0"); assertEquals(NavStatus.UNANSWERED, s.status("m0", byId["m0"]!!))
        s = ExamFlow.answer(s, "m0", Response(setOf(0))); assertEquals(NavStatus.ANSWERED, s.status("m0", byId["m0"]!!))
        s = ExamFlow.toggleMark(s, "m0"); assertEquals(NavStatus.ANSWERED_MARKED, s.status("m0", byId["m0"]!!))
        s = ExamFlow.toggleMark(s, "m1"); assertEquals(NavStatus.MARKED, s.status("m1", byId["m1"]!!))
        s = ExamFlow.clear(s, "m0"); assertEquals(NavStatus.MARKED, s.status("m0", byId["m0"]!!))
        val sum = ExamFlow.summary(s, byId); assertEquals(2, sum[NavStatus.MARKED])
    }
    @Test fun timerStopsAtZeroAndRecordsTime() {
        var s = ExamFlow.start(listOf("m0"), 10)
        s = ExamFlow.tick(s, 4, "m0"); assertEquals(6, s.remainingSec); assertEquals(4, s.timeSpent["m0"])
        s = ExamFlow.tick(s, 100, "m0"); assertEquals(0, s.remainingSec); assertTrue(s.timeUp)
    }
    @Test fun submittedExamIsFrozen() {
        val qs = listOf(mcq("m0")); val byId = qs.associateBy { it.id }
        var s = ExamFlow.start(listOf("m0"), 100); s = ExamFlow.answer(s, "m0", Response(setOf(0)))
        val (done, rep) = ExamFlow.submit(s, byId); assertTrue(done.submitted); assertEquals(1.0, rep.netMarks, 1e-9)
        assertThrows(IllegalArgumentException::class.java) { ExamFlow.answer(done, "m0", Response(setOf(1))) }
        assertThrows(IllegalArgumentException::class.java) { ExamFlow.submit(done, byId) }
        // regenerating a different question with the same slot cannot change the stored report
        val changed = qs.map { it.copy(correctOptions = setOf(3)) }.associateBy { it.id }
        assertEquals(1.0, rep.netMarks, 1e-9); assertNotEquals(1.0, SetScorer.evaluate(changed.values.toList(), s.responses).netMarks, 1e-9)
    }
    @Test fun studentViewHasNoAnswerKey() {
        val v = mcq().toStudentView()
        val fields = QuestionView::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("correct", true) || it.contains("solution", true) || it.contains("explanation", true) || it == "nat" })
        assertEquals(4, v.options.size)
    }
}
