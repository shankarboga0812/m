package com.chandu.gatesystem.ai2027.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BuildersTest {
    private val plan = TestSupport.plan
    private val topics: List<TopicRef> = plan.days.flatMap { d ->
        listOf(TopicRef(d.learnTopicId, d.learnSubjectId, d.learnTopic), TopicRef(d.revisionTopicId, d.revisionSubjectId, d.revisionTopic))
    }.distinctBy { it.id }

    @Test fun mockPaperFollowsBlueprint() {
        val qs = MockPaper.build(42, emptySet(), topics)
        assertEquals(65, qs.size)
        assertEquals(100, qs.sumOf { it.marks })
        assertEquals(65, qs.map { it.id }.toSet().size)
        assertEquals(65, qs.map { QuestionValidator.fingerprint(it) }.toSet().size)
        assertEquals(10, qs.count { it.subjectId == "GA" })
        assertEquals(8, qs.count { it.subjectId == "EM" })
        assertEquals(47, qs.count { it.subjectId != "GA" && it.subjectId != "EM" })
        assertTrue(qs.all { QuestionValidator.validate(it).ok })
        // MCQ negative marking rule holds for every question, including re-weighted ones
        qs.filter { it.type == QType.MCQ }.forEach { assertEquals(Scorer.defaultNegative(QType.MCQ, it.marks), it.negativeMarks, 1e-9) }
        qs.filter { it.type != QType.MCQ }.forEach { assertEquals(0.0, it.negativeMarks, 0.0) }
    }

    @Test fun mockPapersDifferBySeedAndRespectExclusions() {
        val a = MockPaper.build(1, emptySet(), topics); val b = MockPaper.build(2, a.map { it.id }.toSet(), topics)
        assertTrue(a.map { it.id }.toSet().intersect(b.map { it.id }.toSet()).isEmpty())
    }

    @Test fun examSetHasThirtyDistinctQuestionsWithDurationFromMarks() {
        val focus = topics.first { it.subjectId == "OS" }
        val qs = ExamPaper.build(30, focus, topics.take(40), topics, 7, emptySet())
        assertEquals(30, qs.size); assertEquals(30, qs.map { it.id }.toSet().size)
        assertEquals(qs.sumOf { it.marks } * 108, ExamPaper.durationSec(qs))
        assertTrue(qs.any { it.subjectId == "GA" })
    }

    @Test fun topicAttributionPrefersPlanTopics() {
        val q = PaperBuilder.draw("OS", 1, 5, 3, emptySet(), topics)
        assertTrue(q.isNotEmpty())
        val ids = topics.map { it.id }.toSet()
        assertTrue("some questions map to plan topics", q.any { it.topicId in ids })
    }

    @Test fun mistakeVariantIsNewQuestionSameConcept() {
        val seen = PaperBuilder.draw("DSA", null, 1, 5, emptySet(), topics).first()
        val m = ErrorMonsterEngine.onWrong(null, seen, Response(setOf(0)), MistakeCategory.CONCEPT, "x", LocalDate.of(2026, 10, 12)) { "m1" }
        val v = ConceptVariants.forMistake(m, 99, setOf(seen.id, QuestionValidator.fingerprint(seen)), topics)
        assertNotNull(v)
        assertEquals(seen.concept, v!!.concept)
        assertNotEquals(seen.id, v.id)
        assertNotEquals(QuestionValidator.fingerprint(seen), QuestionValidator.fingerprint(v))
        assertEquals(m.topicId, v.topicId)
    }

    @Test fun revisionComposerPutsMistakesFirstAndNeverExceedsCount() {
        val d = LocalDate.of(2026, 10, 20)
        val q = listOf(
            QueueItem(QueueKind.TOPIC_REVISION, "t#1", "T1", "OS", "OS/a", d, 1, 10),
            QueueItem(QueueKind.MISTAKE, "m1", "Concept X", "DSA", "DSA/b", d, 2, 50),
            QueueItem(QueueKind.MISTAKE, "m2", "Concept Y", "DSA", "DSA/b", d, 0, 30))
        val s = RevisionComposer.slots(q, topics.take(3), 6)
        assertEquals(6, s.size)
        assertEquals(listOf("m1", "m2"), s.take(2).map { it.mistakeId })
        assertTrue(RevisionComposer.slots(q, emptyList(), 2).all { it.mistakeId != null })
        assertEquals(1, RevisionComposer.slots(q, emptyList(), 1).size)
    }

    @Test fun revisionFilters() {
        val d = LocalDate.of(2026, 10, 20)
        val ms = mapOf("m1" to ErrorMonsterEngine.onWrong(null, TestSupport.mcq(), Response(setOf(1)), MistakeCategory.CALCULATION, "", d) { "m1" }.copy(occurrences = 3))
        val q = listOf(QueueItem(QueueKind.MISTAKE, "m1", "c", "OS", "OS/t", d, 0, 1), QueueItem(QueueKind.TOPIC_REVISION, "k#2", "t", "DL", "DL/x", d.minusDays(2), 2, 1))
        assertEquals(1, RevisionFilter(subjectId = "OS").apply(q, d, ms).size)
        assertEquals(1, RevisionFilter(overdue = true).apply(q, d, ms).size)
        assertEquals(1, RevisionFilter(frequentOnly = true).apply(q, d, ms).size)
        assertEquals(1, RevisionFilter(category = MistakeCategory.CALCULATION).apply(q, d, ms).size)
        assertEquals(1, RevisionFilter(stage = 2).apply(q, d, ms).size)
        assertEquals(1, RevisionFilter(dueToday = true).apply(q, d, ms).size)
        assertEquals(2, RevisionFilter().apply(q, d, ms).size)
    }

    @Test fun lessonsRoundTripAndSkeletonIsHonest() {
        val l = LessonBank.curated.values.first()
        val back = LessonCodec.fromJson(LessonCodec.toJson(l), l.topicId, l.title, LessonSource.CURATED)!!
        assertEquals(l.definition, back.definition); assertEquals(l.formulas, back.formulas); assertEquals(l.examples, back.examples)
        val sk = LessonBank.lessonFor("OS/none", "Unknown topic", "Operating Systems")
        assertEquals(LessonSource.SKELETON, sk.source)
        assertNull(LessonCodec.fromJson("{}", "t", "t", LessonSource.AI))
        // curated topic ids exist in the plan
        val planIds = topics.map { it.id }.toSet()
        LessonBank.curated.keys.forEach { assertTrue("$it must match a plan topic", it in planIds) }
    }
}
