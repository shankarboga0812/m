package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.d
import com.chandu.gatesystem.ai2027.domain.TestSupport.mcq
import org.junit.Assert.*
import org.junit.Test

class ErrorMonsterTest {
    private val day1 = d("2026-10-11")
    private var n = 0
    private fun id() = "m${n++}"
    private fun wrong(existing: MistakeRecord?, q: Question, day: String = "2026-10-11") =
        ErrorMonsterEngine.onWrong(existing, q, Response(setOf(1)), MistakeCategory.CONCEPT, "why", d(day), ::id)

    @Test fun wrongAnswerCreatesRecordWith48HourRetest() {
        val m = wrong(null, mcq("q1", correct = 0))
        assertEquals(1, m.occurrences); assertEquals(MistakeStatus.ACTIVE, m.status); assertEquals(day1.plusDays(2), m.nextRevision)
        assertEquals("B) two", m.userAnswer); assertTrue(m.correctAnswer.startsWith("A)"))
    }
    @Test fun sameUnderlyingErrorDoesNotCreateDuplicate() {
        val q1 = mcq("q1", concept = "Banker safety"); val q2 = mcq("q2", concept = "  banker   SAFETY ")
        assertEquals(ErrorMonsterEngine.dedupeKey(q1), ErrorMonsterEngine.dedupeKey(q2))
        val m1 = wrong(null, q1); val m2 = wrong(m1, q2, "2026-10-12")
        assertEquals(m1.id, m2.id); assertEquals(2, m2.occurrences); assertEquals(listOf("q1", "q2"), m2.questionIds)
    }
    @Test fun independentSuccessesOvercomeOnlyAfterThreeOnDifferentDays() {
        var m = wrong(null, mcq("q1"))
        m = ErrorMonsterEngine.onRevision(m, "r1", true, d("2026-10-13")); assertEquals(MistakeStatus.ACTIVE, m.status); assertEquals(d("2026-10-17"), m.nextRevision)
        m = ErrorMonsterEngine.onRevision(m, "r2", true, d("2026-10-17")); assertEquals(MistakeStatus.ACTIVE, m.status); assertEquals(d("2026-10-24"), m.nextRevision)
        m = ErrorMonsterEngine.onRevision(m, "r3", true, d("2026-10-24"))
        assertEquals(MistakeStatus.OVERCOME, m.status); assertNull(m.nextRevision); assertEquals(3, m.successfulRevisions)
    }
    @Test fun singleCorrectAnswerNeverRemovesMistake() {
        var m = wrong(null, mcq("q1")); m = ErrorMonsterEngine.onRevision(m, "r1", true, d("2026-10-13"))
        assertEquals(MistakeStatus.ACTIVE, m.status); assertEquals(1, m.successfulRevisions)
    }
    @Test fun memorisedRepeatOfSameQuestionGivesNoEvidence() {
        var m = wrong(null, mcq("q1"))
        m = ErrorMonsterEngine.onRevision(m, "q1", true, d("2026-10-13"))      // the original question again
        assertEquals(0, m.successfulRevisions); assertEquals(0, m.consecutiveSuccess)
        m = ErrorMonsterEngine.onRevision(m, "r1", true, d("2026-10-14"))
        m = ErrorMonsterEngine.onRevision(m, "r1", true, d("2026-10-15"))      // same revision question again
        assertEquals(1, m.successfulRevisions)
    }
    @Test fun sameDaySuccessesDoNotAdvanceStreak() {
        var m = wrong(null, mcq("q1"))
        for (i in 1..5) m = ErrorMonsterEngine.onRevision(m, "r$i", true, d("2026-10-13"))
        assertEquals(MistakeStatus.ACTIVE, m.status); assertEquals(1, m.consecutiveSuccess); assertEquals(5, m.successfulRevisions)
    }
    @Test fun failingRevisionResetsStreakAndSchedulesWithin48Hours() {
        var m = wrong(null, mcq("q1"))
        m = ErrorMonsterEngine.onRevision(m, "r1", true, d("2026-10-13")); m = ErrorMonsterEngine.onRevision(m, "r2", true, d("2026-10-17"))
        m = ErrorMonsterEngine.onRevision(m, "r3", false, d("2026-10-24"))
        assertEquals(0, m.consecutiveSuccess); assertEquals(d("2026-10-26"), m.nextRevision); assertEquals(MistakeStatus.ACTIVE, m.status)
    }
    @Test fun recurringMistakeReopensAnOvercomeRecord() {
        var m = wrong(null, mcq("q1"))
        for ((i, dt) in listOf("2026-10-13", "2026-10-17", "2026-10-24").withIndex()) m = ErrorMonsterEngine.onRevision(m, "r$i", true, d(dt))
        assertEquals(MistakeStatus.OVERCOME, m.status)
        m = wrong(m, mcq("q9"), "2026-11-02")
        assertEquals(MistakeStatus.ACTIVE, m.status); assertEquals(1, m.recurrenceAfterOvercome); assertNull(m.overcomeDate)
    }
    @Test fun filtersAndDashboard() {
        val today = d("2026-10-20")
        val a = wrong(null, mcq("a", concept = "A")).copy(nextRevision = today)
        val b = wrong(null, mcq("b", concept = "B")).copy(nextRevision = today.minusDays(3), category = MistakeCategory.CALCULATION, occurrences = 4)
        val c = wrong(null, mcq("c", concept = "C")).copy(status = MistakeStatus.OVERCOME, overcomeDate = today.minusDays(2), nextRevision = null, category = MistakeCategory.GUESS)
        val all = listOf(a, b, c)
        fun f(x: ErrorMonsterEngine.Filter) = ErrorMonsterEngine.filter(all, x, today).map { it.concept }
        assertEquals(listOf("A"), f(ErrorMonsterEngine.Filter.DUE_TODAY)); assertEquals(listOf("B"), f(ErrorMonsterEngine.Filter.OVERDUE))
        assertEquals(listOf("B"), f(ErrorMonsterEngine.Filter.FREQUENT)); assertEquals(listOf("B"), f(ErrorMonsterEngine.Filter.CALCULATION))
        assertEquals(listOf("C"), f(ErrorMonsterEngine.Filter.OVERCOME)); assertEquals(listOf("C"), f(ErrorMonsterEngine.Filter.GUESS))
        assertEquals(3, f(ErrorMonsterEngine.Filter.ALL).size)
        val dash = ErrorMonsterEngine.dashboard(all, today)
        assertEquals(2, dash.active); assertEquals(1, dash.dueToday); assertEquals(1, dash.overdue); assertEquals(1, dash.repeated)
        assertEquals(1, dash.recentlyOvercome); assertEquals(1.0 / 3, dash.resolutionRate, 1e-9)
    }
    @Test fun classifierHeuristics() {
        val nat = TestSupport.nat(v = 100.0)
        assertEquals(MistakeCategory.CALCULATION, MistakeClassifier.suggest(nat, Response(nat = 105.0), 60))
        assertEquals(MistakeCategory.GUESS, MistakeClassifier.suggest(mcq(), Response(setOf(2)), 4))
        assertEquals(MistakeCategory.TIME, MistakeClassifier.suggest(mcq(), null, 0))
        assertEquals(MistakeCategory.CONCEPT, MistakeClassifier.suggest(mcq(), Response(setOf(2)), 90))
    }
}
