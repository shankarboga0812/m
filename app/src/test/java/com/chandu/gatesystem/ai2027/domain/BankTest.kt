package com.chandu.gatesystem.ai2027.domain

import org.junit.Assert.*
import org.junit.Test

class BankTest {
    private val qs: List<Question> by lazy { BankCodec.fromJson(TestSupport.asset(BankCodec.ASSET)) }

    @Test fun every_subject_ships_250_split_into_practice_exam_mock() {
        for (s in BankCodec.subjects) {
            val list = qs.filter { it.subjectId == s }
            assertEquals("$s total", 250, list.size)
            BankRole.values().forEach { r -> assertEquals("$s ${r.name}", r.perSubject, list.count { BankCodec.role(it) == r }) }
        }
        assertEquals(3000, qs.size)
    }

    @Test fun bank_is_clean_unique_and_marked_correctly() {
        assertEquals(qs.size, qs.map { it.id }.toSet().size)
        assertEquals(qs.size, qs.map { QuestionValidator.fingerprint(it) }.toSet().size)
        qs.forEach { q ->
            assertTrue("${q.id}: ${QuestionValidator.validate(q).issues}", QuestionValidator.validate(q).ok)
            assertTrue(q.source == QSource.TEMPLATE_GENERATED)
            assertEquals(ValidationStatus.VERIFIED_DETERMINISTIC, q.validation)
            if (q.type == QType.MCQ) assertEquals(Scorer.defaultNegative(QType.MCQ, q.marks), q.negativeMarks, 1e-9) else assertEquals(0.0, q.negativeMarks, 0.0)
        }
    }

    @Test fun bank_has_a_realistic_type_mix_and_mock_pool_has_both_weights() {
        for (s in BankCodec.subjects) {
            val list = qs.filter { it.subjectId == s }
            assertTrue("$s needs MCQs", list.count { it.type == QType.MCQ } >= 40)
            val mock = list.filter { BankCodec.role(it) == BankRole.MOCK }
            assertEquals(24, mock.count { it.marks == 1 }); assertEquals(16, mock.count { it.marks == 2 })
        }
    }

    @Test fun bank_round_trips_through_json() {
        val sample = qs.take(50)
        assertEquals(sample, BankCodec.fromJson(BankCodec.toJson(sample)))
    }

    @Test fun bank_topics_are_plan_topics_where_possible() {
        val planIds = TestSupport.plan.days.flatMap { listOf(it.learnTopicId, it.revisionTopicId, Topics.id("GA", it.gaTopic)) }.toSet()
        val inPlan = qs.count { it.topicId in planIds }
        assertTrue("most bank questions should map to plan topics ($inPlan/${qs.size})", inPlan > qs.size * 0.7)
    }
}
