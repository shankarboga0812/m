package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.d
import com.chandu.gatesystem.ai2027.domain.TestSupport.plan
import org.junit.Assert.*
import org.junit.Test

class RevisionSchedulerTest {
    @Test fun oneFourSevenDatesAreDayPlus1_4_7() {
        val e = RevisionScheduler.scheduleFor("topic:x", d("2026-10-11"), "X", "EM", "EM/x")
        assertEquals(listOf(d("2026-10-12"), d("2026-10-15"), d("2026-10-18")), e.map { it.dueDate })
        assertEquals(listOf(1, 2, 3), e.map { it.stage })
    }

    @Test fun mergeIsIdempotentSoNoDuplicateReminders() {
        val e = RevisionScheduler.scheduleFor("k", d("2026-10-11"), "X", "EM", "EM/x")
        val merged = RevisionScheduler.merge(RevisionScheduler.merge(emptyList(), e), e)
        assertEquals(3, merged.size)
        val done = RevisionScheduler.complete(merged[0], d("2026-10-12"))
        assertEquals(3, RevisionScheduler.merge(listOf(done) + merged.drop(1), e).size)
        assertEquals(d("2026-10-12"), RevisionScheduler.merge(listOf(done) + merged.drop(1), e)[0].doneDate)
    }

    @Test fun queuePrioritisesOverdueAndRepeatedMistakesAndNeverDiscards() {
        val today = d("2026-10-20")
        val e1 = RevisionScheduler.scheduleFor("a", d("2026-10-11"), "Old topic", "EM", "EM/a")   // all three stages overdue
        val e2 = RevisionScheduler.scheduleFor("b", d("2026-10-19"), "New topic", "OS", "OS/b")   // stage 1 due today
        val m = ErrorMonsterEngine.onWrong(null, TestSupport.mcq("q", concept = "Cache"), Response(setOf(1)), MistakeCategory.CONCEPT, "", d("2026-10-10")) { "m" }
            .copy(occurrences = 5, nextRevision = today.minusDays(1))
        val q = RevisionScheduler.buildQueue(today, e1 + e2, listOf(m), limit = 3)
        assertEquals(3, q.today.size)
        assertEquals(2, q.carriedOver.size)                       // nothing silently dropped
        assertEquals(5, q.all.size)
        assertEquals(QueueKind.MISTAKE, q.today.first().kind)      // repeated + overdue mistake first
        assertEquals("a#1", q.today[1].key)                        // then the most overdue topic revision
        assertTrue(q.carriedOver.any { it.key == "b#1" })
    }

    @Test fun completedAndFutureEntriesAreNotQueued() {
        val today = d("2026-10-12")
        val e = RevisionScheduler.scheduleFor("a", d("2026-10-11"), "A", "EM", "EM/a")
        val done = listOf(RevisionScheduler.complete(e[0], today)) + e.drop(1)
        assertTrue(RevisionScheduler.buildQueue(today, done, emptyList()).all.isEmpty())
    }

    @Test fun overcomeMistakesAreNotQueued() {
        val m = ErrorMonsterEngine.onWrong(null, TestSupport.mcq("q"), Response(setOf(1)), MistakeCategory.CONCEPT, "", d("2026-10-01")) { "m" }
            .copy(status = MistakeStatus.OVERCOME, nextRevision = null)
        assertTrue(RevisionScheduler.buildQueue(d("2026-10-20"), emptyList(), listOf(m)).all.isEmpty())
    }

    @Test fun sameSubjectReturnShowsDueItems() {
        val today = d("2026-10-20")
        val e = RevisionScheduler.scheduleFor("a", d("2026-10-11"), "Old", "OS", "OS/a") + RevisionScheduler.scheduleFor("b", d("2026-10-11"), "Other", "EM", "EM/b")
        val q = RevisionScheduler.buildQueue(today, e, emptyList())
        assertEquals(3, RevisionScheduler.dueForSubject(q, "OS").size)
    }

    // ---- consistency with the supplied Word document ------------------------------------------------------
    @Test fun schedulerReproducesEveryDay147ListInTheDocument() {
        // learn dates per plan day, scheduler output must equal the doc's "147 DUE" lists for all 114 days
        val entries = plan.days.flatMap { RevisionScheduler.scheduleFor("day:${it.day}", d(it.date), "", "", "") }
        for (day in plan.days) {
            val expectedFromDocument = day.due.map { it.fromDay }.sorted()
            val fromScheduler = entries.filter { it.dueDate == d(day.date) }.map { it.itemKey.removePrefix("day:").toInt() }.sorted()
            assertEquals("Day ${day.day} due list", expectedFromDocument, fromScheduler)
        }
    }

    @Test fun planMatchesDocumentFacts() {
        assertEquals(114, plan.size); assertEquals("2026-10-11", plan.days.first().date); assertEquals("2027-02-01", plan.days.last().date)
        assertEquals(listOf(14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 81, 85, 89, 93, 97, 100, 103, 106, 109, 112), plan.days.filter { it.fullMock }.map { it.day })
        assertEquals(plan.mockDays, plan.days.filter { it.fullMock }.map { it.day })
        for (i in 1 until plan.size) assertEquals(d(plan.days[i - 1].date).plusDays(1), d(plan.days[i].date))
        // NOTE: the document's calendar rests on every 7th day (Day 7, 14, ...), which falls on Saturdays, although its
        // "Workout split" table says Sunday. The app follows the day-by-day calendar and flags the discrepancy.
        assertTrue(plan.days.filter { it.day % 7 == 0 }.all { it.workout.startsWith("Rest") })
        assertTrue(plan.days.filter { it.day % 7 != 0 }.none { it.workout.startsWith("Rest") })
        assertEquals("Sat", plan.day(7)!!.weekday)
        assertTrue(plan.days.all { it.gaQuestions == 5 })
        assertEquals("Engineering Mathematics", plan.day(1)!!.learnSubjectLabel)
        assertEquals("Chest + shoulders + triceps; 5-min warm-up, controlled sets", plan.day(1)!!.workout)
        assertEquals(12, plan.day(1)!!.setP); assertEquals(12, plan.day(1)!!.setR); assertEquals(8, plan.day(1)!!.setE)
        assertEquals(65, plan.day(14)!!.mockQuestions); assertEquals(100, plan.day(14)!!.mockMarks); assertEquals(180, plan.day(14)!!.mockMinutes)
        assertEquals(17, plan.days.count { it.weeklyReview })
    }
}
