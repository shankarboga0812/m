package com.chandu.gatesystem.ai2027.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AiTest {
    private val ctx = GenerationContext("OS", "OS/deadlock", "Banker", Difficulty.MEDIUM, 1)
    private fun q(body: String) = """{"provider":"test","questions":[$body]}"""
    private val goodMcq = """{"statement":"Which is a Coffman condition?","type":"MCQ","options":["Mutual exclusion","Preemption","Spooling","Paging"],"correct":[0],"marks":1,
        "solution":"Mutual exclusion is necessary.","optionExplanations":["ok","no","no","no"],"concept":"Coffman conditions","difficulty":"EASY","negativeMarks":99}"""
    private val goodNat = """{"statement":"Compute 3*4+5","type":"NAT","answer":17,"marks":1,"solution":"12+5","concept":"Arithmetic","check":{"kind":"arith","expr":"3*4+5"}}"""

    @Test fun validBatchAcceptedLabelledAiAndLocalScoringParameters() {
        val b = AiQuestionParser.parse(q("$goodMcq,$goodNat"), ctx)
        assertEquals(2, b.accepted.size); assertTrue(b.rejected.isEmpty())
        val m = b.accepted[0]; assertEquals(QSource.AI_GENERATED, m.source); assertEquals(ValidationStatus.UNVERIFIED_AI, m.validation)
        assertEquals(1.0 / 3, m.negativeMarks, 1e-5)                                  // the AI's "negativeMarks":99 is ignored
        assertEquals(ValidationStatus.VERIFIED_DETERMINISTIC, b.accepted[1].validation) // arithmetic cross-check passed
        assertTrue(m.id.startsWith("ai-"))
    }
    @Test fun malformedJsonAndMissingArrayAreSchemaErrors() {
        assertNotNull(AiQuestionParser.parse("not json{", ctx).schemaError); assertNotNull(AiQuestionParser.parse("""{"foo":1}""", ctx).schemaError)
        assertNotNull(AiQuestionParser.parse("""[1,2]""", ctx).schemaError)
    }
    @Test fun missingKeyBadIndexBlankOptionsAndUnknownTypeAreRejectedNotScored() {
        val noKey = goodMcq.replace("\"correct\":[0]", "\"correct\":[]")
        val badIdx = goodMcq.replace("\"correct\":[0]", "\"correct\":[7]")
        val dupOpt = goodMcq.replace("\"Spooling\"", "\"Preemption\"")
        val noType = goodMcq.replace("\"MCQ\"", "\"ESSAY\"")
        val natNoKey = """{"statement":"x","type":"NAT","marks":1,"solution":"s","concept":"c"}"""
        val b = AiQuestionParser.parse(q(listOf(noKey, badIdx, dupOpt, noType, natNoKey, "5").joinToString(",")), ctx)
        assertTrue(b.accepted.isEmpty()); assertEquals(6, b.rejected.size)
        assertTrue(b.rejected[0].reasons.any { it.contains("answer key", true) }); assertTrue(b.rejected[2].reasons.any { it.contains("Duplicate options") })
    }
    @Test fun duplicatesOfExistingQuestionsAreRejected() {
        val first = AiQuestionParser.parse(q(goodMcq), ctx).accepted
        val again = AiQuestionParser.parse(q(goodMcq), ctx, first)
        assertTrue(again.accepted.isEmpty()); assertEquals("Duplicate of an existing question", again.rejected[0].reasons[0])
        assertEquals(1, AiQuestionParser.parse(q("$goodMcq,$goodMcq"), ctx).accepted.size)
    }
    @Test fun answerThatContradictsMechanicalCheckIsFlaggedForReviewNotAccepted() {
        val wrong = goodNat.replace("\"answer\":17", "\"answer\":18")
        val b = AiQuestionParser.parse(q(wrong), ctx)
        assertTrue(b.accepted.isEmpty()); assertEquals(1, b.flagged.size); assertEquals(ValidationStatus.FLAGGED, b.flagged[0].validation)
    }
    @Test fun expressionEvaluator() {
        assertEquals(17.0, ExpressionEvaluator.eval("3*4+5"), 1e-9); assertEquals(512.0, ExpressionEvaluator.eval("2^3^2"), 1e-9)
        assertEquals(-3.0, ExpressionEvaluator.eval("-(1+2)"), 1e-9); assertEquals(1.0, ExpressionEvaluator.eval("10 % 3"), 1e-9)
        assertThrows(ExpressionEvaluator.EvalException::class.java) { ExpressionEvaluator.eval("1/0") }
        assertThrows(ExpressionEvaluator.EvalException::class.java) { ExpressionEvaluator.eval("1+") }
    }

    // ---- backend errors / offline fallback ----------------------------------------------------------------
    private class Fake(val gen: (String) -> TransportResponse) : AiTransport {
        override fun post(url: String, bearerToken: String, jsonBody: String) = gen(url)
        override fun get(url: String, bearerToken: String?) = gen(url)
    }
    private val cfg = AiConfig("https://example.invalid/", "tok")
    private val req = GenerationRequest("Operating Systems", "Deadlocks", "Banker", "safe sequence", Difficulty.MEDIUM, QType.MCQ, 3, 1)

    @Test fun notConfiguredIsReportedHonestly() {
        assertTrue(AiService(Fake { error("must not be called") }, AiConfig("", "")).generate(req) is AiOutcome.NotConfigured)
        assertFalse(AiConfig("ftp://x", "t").configured)
        assertFalse(AiService(Fake { error("no") }, AiConfig("", "")).testConnection().configured)
    }
    @Test fun httpAndNetworkFailuresMapToDistinctOutcomes() {
        fun outcome(f: (String) -> TransportResponse) = AiService(Fake(f), cfg).generate(req)
        assertTrue(outcome { throw IOException("no route") } is AiOutcome.Offline)
        assertTrue(outcome { TransportResponse(401, "{}") } is AiOutcome.Unauthorized)
        assertEquals(12, (outcome { TransportResponse(429, """{"retryAfterSec":12}""") } as AiOutcome.RateLimited).retryAfterSec)
        assertEquals(500, (outcome { TransportResponse(500, """{"error":"boom"}""") } as AiOutcome.ServerError).code)
        assertTrue(outcome { TransportResponse(200, "<html>") } is AiOutcome.Malformed)
        assertTrue(outcome { TransportResponse(200, q(goodMcq)) } is AiOutcome.Success)
    }
    @Test fun connectionTestReportsRealStagesAndNeverFakesSuccess() {
        fun test(f: (String) -> TransportResponse) = AiService(Fake(f), cfg).testConnection()
        assertEquals(false, test { throw IOException("dns") }.reachable)
        val bad = test { if (it.endsWith("/health")) TransportResponse(200, "{}") else TransportResponse(401, "{}") }
        assertEquals(true, bad.reachable); assertEquals(false, bad.authorised)
        val noKey = test { if (it.endsWith("/health")) TransportResponse(200, "{}") else TransportResponse(200, """{"aiConfigured":false}""") }
        assertEquals(false, noKey.aiAvailable)
        val ok = test { if (it.endsWith("/health")) TransportResponse(200, "{}") else TransportResponse(200, """{"aiConfigured":true,"provider":"anthropic"}""") }
        assertEquals(true, ok.aiAvailable)
    }
    @Test fun offlineSessionFallsBackToLocalQuestionsAndNeverReturnsEmptyForCoveredTopics() {
        val ai = AiService(Fake { throw IOException("offline") }, cfg)
        val s = QuestionSupplier.supply(ai, req.copy(count = 5), "OS", "OS/sched", "CPU scheduling and process", true, 42L)
        assertFalse(s.usedAi); assertEquals(5, s.questions.size); assertTrue(s.note.contains("offline", true))
        assertTrue(s.questions.all { it.source == QSource.TEMPLATE_GENERATED && QuestionValidator.validate(it).ok })
        val s2 = QuestionSupplier.supply(null, req.copy(count = 5), "OS", "OS/sched", "CPU scheduling", false, 43L); assertEquals(5, s2.questions.size)
    }
    @Test fun localExplanationHasAllSevenParts() {
        val q = TestSupport.mcq("e1", concept = "Banker's algorithm")
        val e = ExplanationBuilder.local(q, Response(setOf(2)))
        assertEquals("C) three", e.selected); assertTrue(e.correct.startsWith("A)")); assertTrue(e.whyWrong.contains("Option C")); assertEquals("Banker's algorithm", e.concept)
        assertFalse(e.steps.isBlank()); assertNotNull(e.shortcut); assertTrue(e.practiceNext.contains("Banker's algorithm"))
    }
}
