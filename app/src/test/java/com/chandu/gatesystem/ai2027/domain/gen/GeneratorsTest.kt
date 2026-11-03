package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*
import org.junit.Assert.*
import org.junit.Test

class GeneratorsTest {
    @Test fun everyTemplateProducesValidDeterministicallyVerifiedQuestionsAcrossSeeds() {
        assertTrue("expected a large template catalogue", TemplateCatalog.all.size >= 60)
        assertEquals("duplicate template ids", TemplateCatalog.all.size, TemplateCatalog.all.map { it.id }.toSet().size)
        for (tmpl in TemplateCatalog.all) {
            val ids = HashSet<String>()
            for (seed in 1L..40L) {
                val q = tmpl.generate(seed * 1013, "${tmpl.subjectId}/test")
                val v = QuestionValidator.validate(q)
                assertTrue("${tmpl.id} seed $seed invalid: ${v.issues}\n${q.statement}", v.ok)
                assertEquals(ValidationStatus.VERIFIED_DETERMINISTIC, q.validation); assertEquals(QSource.TEMPLATE_GENERATED, q.source)
                assertTrue(q.solution.length > 10)
                if (q.type == QType.MCQ) { assertEquals(4, q.options.size); assertEquals(4, q.optionExplanations.size) }
                ids += q.id
            }
            assertTrue("${tmpl.id} has too little variety (${ids.size}/40)", ids.size >= 4)  // a few concept-bound templates have small parameter spaces
        }
    }
    @Test fun everySubjectInThePlanHasATemplatePool() {
        for (s in Subjects.all.filter { it.id != "MIXED" }) assertTrue("no templates for ${s.id}", TemplateCatalog.forSubject(s.id).isNotEmpty())
        for (d in TestSupport.plan.days) {
            assertTrue("no pool for day ${d.day} ${d.learnTopic}", TemplateCatalog.forTopic(d.learnSubjectId, d.learnTopic).isNotEmpty())
            assertTrue("no pool for revision day ${d.day}", TemplateCatalog.forTopic(d.revisionSubjectId, d.revisionTopic).isNotEmpty())
        }
    }
    @Test fun keywordMatchingCoversMostPlanTopics() {
        val matched = TestSupport.plan.days.count { TemplateCatalog.topicMatches(it.learnSubjectId, it.learnTopic) }
        println("plan learning topics with a keyword-matched template: $matched/114")
        assertTrue(matched >= 40)
    }
    @Test fun factoryReturnsUniqueQuestionsAndHonoursExclusions() {
        val a = QuestionFactory.generate("OS", "OS/x", "CPU scheduling", 30, 1L)
        assertEquals(30, a.size); assertEquals(30, a.map { it.id }.toSet().size); assertEquals(30, a.map { QuestionValidator.fingerprint(it) }.toSet().size)
        val b = QuestionFactory.generate("OS", "OS/x", "CPU scheduling", 10, 99L, exclude = a.map { it.id }.toSet())
        assertTrue(b.none { q -> a.any { it.id == q.id } })
    }
    @Test fun revisionVariantsTestSameConceptWithChangedValues() {
        val first = QuestionFactory.generate("OS", "OS/x", "page replacement", 1, 5L)[0]
        val tmpl = TemplateCatalog.forTopic("OS", "page replacement").first { it.concept == first.concept }
        val variants = (1L..10L).map { tmpl.generate(it * 7919, "OS/x") }
        assertTrue(variants.all { it.concept == first.concept }); assertTrue(variants.map { it.statement }.toSet().size >= 5)
    }
    @Test fun mockPaperCanBeAssembledOffline() {
        val ids = HashSet<String>()
        for (slot in MockBlueprint.fullGateCs) {
            val q = QuestionFactory.generate(slot.subjectId, "${slot.subjectId}/mock", "", 1, ids.size * 31L + 7, exclude = ids)
            assertEquals("no question for ${slot.subjectId}", 1, q.size); ids += q[0].id
        }
        assertEquals(65, ids.size)
    }

    // ---- independent cross-checks of the simulators against textbook answers --------------------------------
    @Test fun pageReplacementMatchesTextbookReferenceString() {
        val ref = listOf(7, 0, 1, 2, 0, 3, 0, 4, 2, 3, 0, 3, 2, 1, 2, 0, 1, 7, 0, 1)
        assertEquals(15, OsSim.pageFaults(ref, 3, "FIFO")); assertEquals(12, OsSim.pageFaults(ref, 3, "LRU")); assertEquals(9, OsSim.pageFaults(ref, 3, "OPT"))
    }
    @Test fun diskSchedulingMatchesTextbookExample() {
        val reqs = listOf(98, 183, 37, 122, 14, 124, 65, 67)
        assertEquals(640, OsSim.diskMovement(reqs, 53, "FCFS", 199)); assertEquals(236, OsSim.diskMovement(reqs, 53, "SSTF", 199))
                assertEquals(331, OsSim.diskMovement(reqs, 53, "SCAN", 199))
    }
    @Test fun cpuSchedulingTextbookExamples() {
        // P1(0,24) P2(0,3) P3(0,3): FCFS avg wait 17, SJF 3, RR(q=4) 5.666..
        val arr = intArrayOf(0, 0, 0); val b = intArrayOf(24, 3, 3)
        assertEquals(17.0, OsSim.fcfsWait(arr, b), 1e-9); assertEquals(3.0, OsSim.sjfWait(arr, b), 1e-9)
        assertEquals(17.0 / 3, OsSim.rrWait(arr, b, 4), 1e-9)
        // SRTF classic: arrivals 0,1,2,3 bursts 8,4,9,5 -> avg wait 6.5
        assertEquals(6.5, OsSim.srtfWait(intArrayOf(0, 1, 2, 3), intArrayOf(8, 4, 9, 5)), 1e-9)
        // non-preemptive SJF classic: arrivals 0,2,4,5 bursts 7,4,1,4 -> waits 0,6,3,7 -> avg 4
        assertEquals(4.0, OsSim.sjfWait(intArrayOf(0, 2, 4, 5), intArrayOf(7, 4, 1, 4)), 1e-9)
    }
    @Test fun bankersSafetyTextbookExample() {
        val alloc = arrayOf(intArrayOf(0, 1, 0), intArrayOf(2, 0, 0), intArrayOf(3, 0, 2), intArrayOf(2, 1, 1), intArrayOf(0, 0, 2))
        val max = arrayOf(intArrayOf(7, 5, 3), intArrayOf(3, 2, 2), intArrayOf(9, 0, 2), intArrayOf(2, 2, 2), intArrayOf(4, 3, 3))
        val need = Array(5) { i -> IntArray(3) { j -> max[i][j] - alloc[i][j] } }
        val seq = OsSim.safeSequence(alloc, need, intArrayOf(3, 3, 2))!!
        assertTrue(OsSim.isValidSafeSequence(seq, alloc, need, intArrayOf(3, 3, 2))); assertTrue(OsSim.isValidSafeSequence(listOf(1, 3, 4, 0, 2), alloc, need, intArrayOf(3, 3, 2)))
        assertFalse(OsSim.isValidSafeSequence(listOf(0, 1, 2, 3, 4), alloc, need, intArrayOf(3, 3, 2)))
    }
    @Test fun crcAndClosureAndNormalForms() {
        assertEquals("1110", GenCn.crcRemainder("1101011011", "10011"))                     // classic textbook CRC
        val fds = listOf(setOf('A') to setOf('B'), setOf('B') to setOf('C'))
        assertEquals(setOf('A', 'B', 'C'), DbSim.closure(setOf('A'), fds)); assertEquals(setOf('B', 'C'), DbSim.closure(setOf('B'), fds))
        val all = listOf('A', 'B', 'C')
        assertEquals(listOf(setOf('A')), DbSim.candidateKeys(all, fds)); assertEquals(2, DbSim.normalForm(all, fds))             // transitive dep: 2NF, not 3NF
        assertEquals(4, DbSim.normalForm(all, listOf(setOf('A') to setOf('B', 'C'))))                                        // BCNF
        assertEquals(3, DbSim.normalForm(listOf('A', 'B', 'C'), listOf(setOf('A', 'B') to setOf('C'), setOf('C') to setOf('B'))))  // 3NF not BCNF
        assertEquals(1, DbSim.normalForm(listOf('A', 'B', 'C'), listOf(setOf('A', 'B') to setOf('C'), setOf('A') to setOf('C'))))   // partial dependency: only 1NF
        val ids = DbSim.precedenceEdges(listOf(Triple(1, 'R', "X"), Triple(2, 'W', "X"), Triple(2, 'R', "Y"), Triple(1, 'W', "Y")))
        assertEquals(setOf(1 to 2, 2 to 1), ids)                                                                               // classic cycle
    }
    @Test fun dijkstraAndGraphHelpers() {
        val es = listOf(Edge(0, 1, 4), Edge(0, 2, 1), Edge(2, 1, 2), Edge(1, 3, 1), Edge(2, 3, 5))
        assertEquals(listOf(0, 3, 1, 4), dijkstra(4, es, 0).toList())
    }
    @Test fun numberHelpers() {
        assertEquals(14L, choose(8, 4) / 5); assertEquals(5, log2Ceil(30)); assertEquals(1L, modPow(7, 4, 10)); assertEquals(1L, modPow(2, 10, 1023))
        assertEquals("3/4", frac(6, 8)); assertEquals("5", frac(10, 2)); assertEquals(6, gcd(54, 24))
    }
    @Test fun staticBankIsValidAndOriginalLabelled() {
        for (q in StaticBank.questions) { val v = QuestionValidator.validate(q); assertTrue("${q.id}: ${v.issues}", v.ok); assertEquals(QSource.BANK_ORIGINAL, q.source) }
        assertEquals(StaticBank.questions.size, StaticBank.questions.map { it.id }.toSet().size)
        assertTrue(StaticBank.questions.none { it.source == QSource.VERIFIED_PYQ })   // no fake PYQs bundled
    }
}
