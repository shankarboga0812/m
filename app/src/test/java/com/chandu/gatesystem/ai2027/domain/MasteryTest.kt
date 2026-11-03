package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.d
import org.junit.Assert.*
import org.junit.Test

class MasteryTest {
    private val today = d("2026-10-20")
    private fun att(id: String, ok: Boolean, day: String, diff: Difficulty = Difficulty.MEDIUM) = AttemptRecord(id, ok, diff, d(day))
    private fun many(n: Int, correctEvery: Int = 1, days: List<String> = listOf("2026-10-15", "2026-10-18", "2026-10-20")) =
        (1..n).map { att("q$it", it % correctEvery == 0 || correctEvery == 1, days[it % days.size]) }

    @Test fun lessonOnlyIsLearningNotStrong() {
        assertEquals(TopicStatus.LEARNING, MasteryCalculator.compute(emptyList(), true, 0, 0, today).status)
        assertEquals(TopicStatus.NOT_STARTED, MasteryCalculator.compute(emptyList(), false, 0, 0, today).status)
    }
    @Test fun oneCorrectAnswerNeverMakesTopicStrong() {
        val r = MasteryCalculator.compute(listOf(att("q1", true, "2026-10-20")), true, 0, 0, today)
        assertNotEquals(TopicStatus.STRONG, r.status); assertNotEquals(TopicStatus.MASTERED, r.status); assertTrue(r.score < 60)
    }
    @Test fun tenDistinctQuestionsAcrossDaysWithHighAccuracyIsStrong() {
        assertEquals(TopicStatus.STRONG, MasteryCalculator.compute(many(10), true, 0, 0, today).status)
    }
    @Test fun repeatingTheSameQuestionDoesNotCountAsNewEvidence() {
        val rep = (1..30).map { att("same", true, if (it % 2 == 0) "2026-10-18" else "2026-10-20") }
        val r = MasteryCalculator.compute(rep, true, 0, 0, today)
        assertTrue(r.status == TopicStatus.PRACTISING)
    }
    @Test fun allAttemptsOnOneDayCannotBeStrong() {
        val oneDay = (1..12).map { att("q$it", true, "2026-10-20") }
        assertEquals(TopicStatus.PRACTISING, MasteryCalculator.compute(oneDay, true, 0, 0, today).status)
    }
    @Test fun masteredNeedsVolumeDifficultySpacedRecallAndNoActiveMistakes() {
        val hard = (1..24).map { att("q$it", true, listOf("2026-10-10", "2026-10-15", "2026-10-20")[it % 3], if (it % 2 == 0) Difficulty.HARD else Difficulty.EASY) }
        assertEquals(TopicStatus.MASTERED, MasteryCalculator.compute(hard, true, 0, 0, today).status)
        assertNotEquals(TopicStatus.MASTERED, MasteryCalculator.compute(hard, true, 1, 0, today).status)
        val easyOnly = (1..24).map { att("q$it", true, listOf("2026-10-10", "2026-10-15", "2026-10-20")[it % 3], Difficulty.EASY) }
        assertNotEquals(TopicStatus.MASTERED, MasteryCalculator.compute(easyOnly, true, 0, 0, today).status)
    }
    @Test fun poorRecentPerformanceOrOverdueMistakesFlagsRevision() {
        val bad = (1..8).map { att("q$it", it <= 2, "2026-10-${10 + it}") }
        assertEquals(TopicStatus.REVISION_REQUIRED, MasteryCalculator.compute(bad, true, 2, 0, today).status)
        assertEquals(TopicStatus.REVISION_REQUIRED, MasteryCalculator.compute(many(10), true, 1, 1, today).status)
    }
}
