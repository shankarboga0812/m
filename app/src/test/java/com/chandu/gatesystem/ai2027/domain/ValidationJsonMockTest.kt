package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.mcq
import com.chandu.gatesystem.ai2027.domain.TestSupport.msq
import com.chandu.gatesystem.ai2027.domain.TestSupport.nat
import org.junit.Assert.*
import org.junit.Test

class ValidationJsonMockTest {
    @Test fun validQuestionsPass() { assertTrue(QuestionValidator.validate(mcq()).ok); assertTrue(QuestionValidator.validate(msq()).ok); assertTrue(QuestionValidator.validate(nat()).ok) }
    @Test fun detectsStructuralProblems() {
        fun issues(q: Question) = QuestionValidator.validate(q).issues.joinToString(" | ")
        assertTrue(issues(mcq().copy(correctOptions = emptySet())).contains("answer key"))
        assertTrue(issues(mcq().copy(correctOptions = setOf(0, 1))).contains("exactly one"))
        assertTrue(issues(mcq().copy(correctOptions = setOf(9))).contains("non-existent"))
        assertTrue(issues(mcq().copy(options = listOf("a", "a", "b", "c"))).contains("Duplicate options"))
        assertTrue(issues(mcq().copy(options = listOf("a", " ", "b", "c"))).contains("Blank"))
        assertTrue(issues(mcq().copy(statement = " ")).contains("statement"))
        assertTrue(issues(mcq().copy(solution = "")).contains("solution"))
        assertTrue(issues(msq().copy(negativeMarks = 0.5)).contains("negative")); assertTrue(issues(nat().copy(negativeMarks = 1.0)).contains("negative"))
        assertTrue(issues(nat().copy(nat = null)).contains("numeric")); assertTrue(issues(nat().copy(options = listOf("x"))).contains("NAT"))
        assertTrue(issues(nat().copy(nat = NatAnswer(5.0, 1.0))).contains("range"))
        assertTrue(issues(mcq().copy(source = QSource.VERIFIED_PYQ)).contains("year"))
        assertTrue(issues(mcq().copy(source = QSource.AI_GENERATED, validation = ValidationStatus.VERIFIED_DETERMINISTIC)).contains("AI"))
        assertTrue(issues(mcq().copy(optionExplanations = listOf("x"))).contains("explanations"))
    }
    @Test fun duplicateDetectionIgnoresCaseSpacingAndOptionOrder() {
        val a = mcq("a").copy(statement = "What  IS this?")
        val b = a.copy(id = "b", statement = "what is this?", options = a.options.reversed(), correctOptions = setOf(3))
        val det = DuplicateDetector(listOf(a)); assertTrue(det.isDuplicate(b)); assertFalse(det.isDuplicate(a.copy(statement = "Different")))
        assertTrue(det.register(a.copy(statement = "New one"))); assertFalse(det.register(a.copy(statement = "New one")))
    }
    @Test fun jsonRoundTripAndErrors() {
        val v = Json.parseObject("""{"a":[1,2.5,"x\nA",true,null],"b":{"c":-3}}""")
        assertEquals(1L, (v.list("a")!![0])); assertEquals(2.5, v.list("a")!![1] as Double, 0.0); assertEquals("x\nA", v.list("a")!![2]); assertEquals(-3, v.obj("b")!!.int("c"))
        assertEquals(v, Json.parseObject(Json.stringify(v)))
        for (bad in listOf("", "{", "[1,]", "{\"a\":}", "{'a':1}", "[1] x", "\"abc")) assertThrows(Json.JsonException::class.java) { Json.parse(bad) }
        assertEquals("\"a\\\"b\\\\\"", Json.stringify("a\"b\\"))
    }
    // ---- mock tests ------------------------------------------------------------------------------------------
    @Test fun fullMockBlueprintIsOfficialGatePattern() {
        val b = MockBlueprint.fullGateCs
        assertEquals(65, b.size); assertEquals(100, b.sumOf { it.marks }); assertEquals(15, b.filter { it.subjectId == "GA" }.sumOf { it.marks }); assertEquals(10, b.count { it.subjectId == "GA" })
        assertEquals(13, b.filter { it.subjectId == "EM" }.sumOf { it.marks }); assertEquals(72, b.filter { it.subjectId != "GA" && it.subjectId != "EM" }.sumOf { it.marks })
        assertEquals(10800, MockBlueprint.FULL_DURATION_SEC); assertEquals(100, MockBlueprint.totalMarks)
    }
    private fun res(i: Int, pct: Double) = MockResult("m$i", i, "FULL", pct, pct, 100.0, 0.5, 40, 65, 9000, 10800, i.toLong())
    @Test fun mockTrendAndRecentAverage() {
        val rs = listOf(30.0, 35.0, 42.0, 50.0).mapIndexed { i, p -> res(i, p) }
        assertTrue(MockAnalytics.trendSlope(rs) > 5.0); assertEquals((35 + 42 + 50) / 3.0, MockAnalytics.recentAverage(rs), 1e-9)
        assertEquals(0.0, MockAnalytics.trendSlope(listOf(res(1, 50.0))), 0.0)
        assertTrue(MockAnalytics.trendSlope(listOf(60.0, 50.0, 40.0).mapIndexed { i, p -> res(i, p) }) < 0)
    }
    @Test fun timeManagementAdviceAndWeakTopics() {
        val qs = (1..10).map { mcq("q$it", topic = if (it <= 5) "T1" else "T2") }
        val resp = qs.take(5).associate { it.id to Response(setOf(1)) }                // 5 wrong in T1, 5 unanswered in T2
        val rep = SetScorer.evaluate(qs, resp, qs.associate { it.id to 60 })
        assertEquals(listOf("T1"), MockAnalytics.weakTopics(rep).map { it.key })
        val adv = MockAnalytics.timeManagement(rep, 600); assertTrue(adv.advice.isNotBlank()); assertTrue(adv.usedFraction > 0.9)
    }
}
