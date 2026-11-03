package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.TestSupport.d
import org.junit.Assert.*
import org.junit.Test

class ProgressionTest {
    private val rules = XpRules()
    @Test fun xpAwardIsIdempotentPerSourceKey() {
        val a = XpEngine.batch(rules, "s1", 0, 4)
        val seen = HashSet<String>()
        val first = XpEngine.award(seen, a.key, a.amount, a.reason); assertNotNull("a=$a seen=$seen", first); seen += a.key
        assertNull(XpEngine.award(seen, a.key, a.amount, a.reason))                 // reopening the same batch gives nothing
        assertNotNull(XpEngine.award(seen, XpEngine.batch(rules, "s1", 1, 4).key, 10, "x"))
        assertNull(XpEngine.award(seen, "k2", 0, "zero"))
    }
    @Test fun xpAmountsAreConfigurable() {
        assertEquals(0, XpEngine.batch(rules, "s", 0, 5).amount) // practice batches no longer give XP; marks do
        assertEquals(10 + 1 * 2, XpEngine.batch(XpRules(batchComplete = 10, batchPerCorrect = 1), "s", 0, 2).amount)
        assertEquals(50, XpEngine.streak(rules, "2026-10-12", 20).amount) // capped
    }
    @Test fun levelThresholdsAndProgress() {
        assertEquals(1, Levels.levelFor(0)); assertEquals(1, Levels.levelFor(99)); assertEquals(2, Levels.levelFor(100)); assertEquals(3, Levels.levelFor(300))
        assertEquals(100, Levels.xpForLevel(2)); assertEquals(600, Levels.xpForLevel(4))
        val p = Levels.progress(150); assertEquals(2, p.level); assertEquals(50, p.xpIntoLevel); assertEquals(200, p.xpForNext)
        val lc = RankEvaluator.levelChange(90, 120, "Practice batch"); assertNotNull(lc); assertEquals(2, lc!!.to); assertTrue(lc.reason.contains("Level 2"))
        assertNull(RankEvaluator.levelChange(110, 150, "x"))
    }
    @Test fun rankNeverIncreasesJustFromXpOrActivity() {
        val xpOnly = PlayerStats(totalXp = 100000)                               // huge XP, but no performance
        assertEquals(Rank.E, RankEvaluator.evaluate(xpOnly))
        val spam = PlayerStats(totalXp = 5000, questionsAnswered = 5000, correctAnswers = 500)  // many submissions, bad accuracy
        assertTrue(RankEvaluator.evaluate(spam) <= Rank.D)
    }
    @Test fun rankUpRequiresAllRequirementsAndReportsReason() {
        val s = PlayerStats(totalXp = Levels.xpForLevel(10), questionsAnswered = 400, correctAnswers = 260, topicsStrong = 3)
        assertEquals(Rank.C, RankEvaluator.evaluate(s))
        val ch = RankEvaluator.update(Rank.D, s)!!; assertEquals(Rank.C, ch.to); assertTrue(ch.reason.contains("Level 10"))
        assertNull(RankEvaluator.update(Rank.C, s))
        assertFalse(RankEvaluator.requirements(Rank.B, s).all { it.met })
        assertNull(RankEvaluator.update(Rank.B, s))                              // never demoted
    }
    @Test fun achievementsFollowPerformance() {
        assertTrue(Achievements.unlocked(PlayerStats()).isEmpty())
        val u = Achievements.unlocked(PlayerStats(questionsAnswered = 120, longestStreak = 7, mistakesOvercome = 1, mocksCompleted = 1, bestMockPercent = 61.0))
        assertTrue(setOf("first_batch", "q100", "streak7", "monster1", "mock1", "mock60").all { it in u }); assertFalse("mock85" in u)
    }
    @Test fun streaks() {
        val q = setOf("2026-10-11", "2026-10-12", "2026-10-13", "2026-10-20", "2026-10-21").map { d(it) }.toSet()
        assertEquals(Streaks.Result(2, 3), Streaks.compute(q, d("2026-10-21")))
        assertEquals(Streaks.Result(2, 3), Streaks.compute(q, d("2026-10-22")))  // today not yet done: streak survives
        assertEquals(Streaks.Result(0, 3), Streaks.compute(q, d("2026-10-25")))
    }
}
