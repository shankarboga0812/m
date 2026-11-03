package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.mcq
import com.chandu.gatesystem.ai2027.domain.TestSupport.msq
import com.chandu.gatesystem.ai2027.domain.TestSupport.nat
import org.junit.Assert.*
import org.junit.Test

class ScoringTest {
    @Test fun mcqCorrectGivesFullMarks() {
        assertEquals(1.0, Scorer.score(mcq(marks = 1), Response(setOf(0))).marks, 1e-9)
        assertEquals(2.0, Scorer.score(mcq(marks = 2), Response(setOf(0))).marks, 1e-9)
    }
    @Test fun mcqNegativeMarkingOneThirdAndTwoThirds() {
        val r1 = Scorer.score(mcq(marks = 1), Response(setOf(1)))
        assertEquals(Verdict.WRONG, r1.verdict); assertEquals(-1.0 / 3, r1.marks, 1e-5)
        val r2 = Scorer.score(mcq(marks = 2), Response(setOf(3)))
        assertEquals(-2.0 / 3, r2.marks, 1e-5)
    }
    @Test fun unansweredCostsNothing() {
        val r = Scorer.score(mcq(), null); assertEquals(Verdict.UNANSWERED, r.verdict); assertEquals(0.0, r.marks, 0.0)
        assertEquals(Verdict.UNANSWERED, Scorer.score(mcq(), Response()).verdict)
    }
    @Test fun msqIsAllOrNothingAndNeverNegative() {
        val q = msq()
        assertEquals(2.0, Scorer.score(q, Response(setOf(0, 2))).marks, 1e-9)
        assertEquals(0.0, Scorer.score(q, Response(setOf(0))).marks, 1e-9)       // subset: no partial credit by default
        assertEquals(0.0, Scorer.score(q, Response(setOf(0, 1, 2))).marks, 1e-9) // superset: no negative marks
        assertEquals(Verdict.WRONG, Scorer.score(q, Response(setOf(1))).verdict)
    }
    @Test fun msqPartialCreditOnlyWhenConfiguredAndOnlyForNoWrongOption() {
        val q = msq(marks = 4, correct = setOf(0, 1, 2, 3).let { setOf(0, 1, 2) })
        val p = MarkingPolicy(msqPartialCredit = true)
        assertEquals(4.0 * 2 / 3, Scorer.score(q, Response(setOf(0, 1)), p).marks, 1e-5)
        assertEquals(0.0, Scorer.score(q, Response(setOf(0, 3)), p).marks, 1e-9)
    }
    @Test fun natToleranceAndRange() {
        val q = nat(v = 10.0, tol = 0.5)
        assertEquals(Verdict.CORRECT, Scorer.score(q, Response(nat = 10.4)).verdict)
        assertEquals(Verdict.CORRECT, Scorer.score(q, Response(nat = 9.5)).verdict)
        assertEquals(Verdict.WRONG, Scorer.score(q, Response(nat = 10.6)).verdict)
        assertEquals(0.0, Scorer.score(q, Response(nat = 99.0)).marks, 0.0) // no negative marks for NAT
        val range = nat(v = 0.0).copy(nat = NatAnswer(1.4, 1.6))
        assertEquals(Verdict.CORRECT, Scorer.score(range, Response(nat = 1.5)).verdict)
        assertEquals(Verdict.WRONG, Scorer.score(range, Response(nat = 1.7)).verdict)
    }
    @Test fun natInputParsing() {
        assertEquals(12.0, NatInput.parse(" 12 ")!!, 0.0); assertEquals(0.5, NatInput.parse("1/2")!!, 1e-9)
        assertNull(NatInput.parse("abc")); assertNull(NatInput.parse("")); assertNull(NatInput.parse("1/0")); assertNull(NatInput.parse("NaN"))
    }
    @Test fun defaultNegativeRules() {
        assertEquals(1.0 / 3, Scorer.defaultNegative(QType.MCQ, 1), 1e-5)
        assertEquals(2.0 / 3, Scorer.defaultNegative(QType.MCQ, 2), 1e-5)
        assertEquals(0.0, Scorer.defaultNegative(QType.MSQ, 2), 0.0); assertEquals(0.0, Scorer.defaultNegative(QType.NAT, 2), 0.0)
    }
}
