package com.chandu.gatesystem.ai2027.domain

/** All XP amounts are configurable; defaults are chosen so a full study day is roughly 250-350 XP. */
data class XpRules(
    val lessonComplete: Int = 0,
    val batchComplete: Int = 0,
    val batchPerCorrect: Int = 0,
    val prepSetComplete: Int = 0,
    val revisionSessionComplete: Int = 10,
    val revisionPerCorrect: Int = 1,
    val mistakeOvercome: Int = 30,
    val examSetComplete: Int = 10,
    val examPerCorrect: Int = 0,
    val mockComplete: Int = 30,
    val mockPerNetMark: Double = 4.0,
    val questComplete: Int = 0,
    val dayClear: Int = 0,
    val streakBonusPerDay: Int = 5,
    val streakBonusCap: Int = 50,
    // marks-based XP: daily subject mock, full mock (every 3 days), exam set, workout
    val subjectMockBase: Int = 10,
    val subjectMockPerMark: Int = 5,
    val examPerMark: Int = 3,
    val bonus60: Int = 40,
    val bonus75: Int = 100,
    val workoutDone: Int = 40,
    val workoutPartial: Int = 20
)

data class XpAward(val key: String, val amount: Int, val reason: String)

/**
 * XP is idempotent: every award has a unique source key (e.g. "batch:<sessionId>:3").
 * Re-opening or re-submitting an already completed unit produces no second award.
 */
object XpEngine {
    fun award(alreadyAwarded: Set<String>, key: String, amount: Int, reason: String): XpAward? =
        if (amount <= 0 || key in alreadyAwarded) null else XpAward(key, amount, reason)

    fun batch(r: XpRules, sessionId: String, batchIdx: Int, correct: Int) =
        XpAward("batch:$sessionId:$batchIdx", r.batchComplete + r.batchPerCorrect * correct, "Practice batch ${batchIdx + 1} completed ($correct correct)")
    fun prepSet(r: XpRules, sessionId: String) = XpAward("prepset:$sessionId", r.prepSetComplete, "Preparation Set completed")
    fun lesson(r: XpRules, topicId: String) = XpAward("lesson:$topicId", r.lessonComplete, "Lesson completed")
    fun revision(r: XpRules, sessionId: String, correct: Int) = XpAward("revision:$sessionId", r.revisionSessionComplete + r.revisionPerCorrect * correct, "Revision session completed ($correct correct)")
    fun overcome(r: XpRules, mistakeId: String, round: Int) = XpAward("overcome:$mistakeId:$round", r.mistakeOvercome, "Error Monster overcome")
    /** Marks-based XP. Negative net marks give only the base. Extra bonus at 60% and 75%+. */
    private fun tier(r: XpRules, net: Double, max: Double): Int {
        val pct = if (max <= 0.0) 0.0 else net / max * 100.0
        return if (pct >= 75.0) r.bonus75 else if (pct >= 60.0) r.bonus60 else 0
    }
    fun subjectMock(r: XpRules, sessionId: String, net: Double, max: Double) =
        XpAward("submock:$sessionId", r.subjectMockBase + (maxOf(0.0, net) * r.subjectMockPerMark).toInt() + tier(r, net, max), "Daily subject mock (${"%.1f".format(net)}/${"%.0f".format(max)} marks)")
    fun examByMarks(r: XpRules, sessionId: String, net: Double, max: Double) =
        XpAward("exam:$sessionId", r.examSetComplete + (maxOf(0.0, net) * r.examPerMark).toInt() + tier(r, net, max) / 2, "Exam set (${"%.1f".format(net)}/${"%.0f".format(max)} marks)")
    fun fullMock(r: XpRules, sessionId: String, net: Double, max: Double) =
        XpAward("mock:$sessionId", r.mockComplete + (maxOf(0.0, net) * r.mockPerNetMark).toInt() + tier(r, net, max), "Full mock (${"%.1f".format(net)}/${"%.0f".format(max)} marks)")
    fun workout(r: XpRules, epoch: Long, done: Boolean) =
        if (done) XpAward("workout:$epoch:DONE", maxOf(0, r.workoutDone - r.workoutPartial), "Workout completed")
        else XpAward("workout:$epoch:PARTIAL", r.workoutPartial, "Workout (partial)")
    fun exam(r: XpRules, sessionId: String, correct: Int) = XpAward("exam:$sessionId", r.examSetComplete + r.examPerCorrect * correct, "Exam Set completed ($correct correct)")
    fun mock(r: XpRules, sessionId: String, net: Double) = XpAward("mock:$sessionId", r.mockComplete + (maxOf(0.0, net) * r.mockPerNetMark).toInt(), "Full mock completed")
    fun quest(r: XpRules, questId: String, xp: Int = r.questComplete) = XpAward("quest:$questId", xp, "Quest cleared")
    fun dayClear(r: XpRules, date: String) = XpAward("dayclear:$date", r.dayClear, "All of today's quests cleared")
    fun streak(r: XpRules, date: String, streakDays: Int) =
        XpAward("streak:$date", minOf(r.streakBonusCap, r.streakBonusPerDay * streakDays), "$streakDays-day streak bonus")
}

object Levels {
    /** Cumulative XP needed to *reach* level L (L1 = 0). Level 2 = 100, 3 = 300, 4 = 600, ... */
    fun xpForLevel(level: Int): Int = 50 * (level - 1) * level
    fun levelFor(xp: Int): Int { var l = 1; while (xp >= xpForLevel(l + 1)) l++; return l }
    data class Progress(val level: Int, val xpIntoLevel: Int, val xpForNext: Int) {
        val fraction get() = if (xpForNext == 0) 1f else xpIntoLevel.toFloat() / xpForNext
    }
    fun progress(xp: Int): Progress {
        val l = levelFor(xp)
        return Progress(l, xp - xpForLevel(l), xpForLevel(l + 1) - xpForLevel(l))
    }
}

enum class Rank(val label: String, val title: String) {
    E("E-Rank", "Awakened"), D("D-Rank", "Gate Walker"), C("C-Rank", "Dungeon Delver"),
    B("B-Rank", "Elite Hunter"), A("A-Rank", "Vanguard"), S("S-Rank", "Shadow Monarch candidate"),
    NATIONAL("National Level", "GATE 85 Hunter")
}

data class PlayerStats(
    val totalXp: Int = 0, val questionsAnswered: Int = 0, val correctAnswers: Int = 0,
    val topicsStrong: Int = 0, val topicsMastered: Int = 0, val mocksCompleted: Int = 0,
    val bestMockPercent: Double = 0.0, val avgRecentMockPercent: Double = 0.0,
    val mistakesOvercome: Int = 0, val streak: Int = 0, val longestStreak: Int = 0,
    val questsCompleted: Int = 0, val revisionsCompleted: Int = 0, val prepSetsCompleted: Int = 0
) {
    val level get() = Levels.levelFor(totalXp)
    val accuracy get() = if (questionsAnswered == 0) 0.0 else correctAnswers.toDouble() / questionsAnswered
}

data class RankRequirement(val description: String, val met: Boolean)

object RankEvaluator {
    private fun reqs(rank: Rank, s: PlayerStats): List<RankRequirement> = when (rank) {
        Rank.E -> emptyList()
        Rank.D -> listOf(
            RankRequirement("Level 5 (now ${s.level})", s.level >= 5),
            RankRequirement("100 questions answered (now ${s.questionsAnswered})", s.questionsAnswered >= 100))
        Rank.C -> listOf(
            RankRequirement("Level 10 (now ${s.level})", s.level >= 10),
            RankRequirement("300 questions answered", s.questionsAnswered >= 300),
            RankRequirement("Accuracy 55%+ (now ${pct(s.accuracy)})", s.accuracy >= 0.55),
            RankRequirement("3 topics Strong or Mastered (now ${s.topicsStrong + s.topicsMastered})", s.topicsStrong + s.topicsMastered >= 3))
        Rank.B -> listOf(
            RankRequirement("Level 15 (now ${s.level})", s.level >= 15),
            RankRequirement("800 questions answered", s.questionsAnswered >= 800),
            RankRequirement("Accuracy 65%+ (now ${pct(s.accuracy)})", s.accuracy >= 0.65),
            RankRequirement("8 topics Strong or Mastered", s.topicsStrong + s.topicsMastered >= 8),
            RankRequirement("At least 1 full mock completed", s.mocksCompleted >= 1))
        Rank.A -> listOf(
            RankRequirement("Level 20 (now ${s.level})", s.level >= 20),
            RankRequirement("1500 questions answered", s.questionsAnswered >= 1500),
            RankRequirement("Accuracy 70%+", s.accuracy >= 0.70),
            RankRequirement("15 topics Strong or Mastered", s.topicsStrong + s.topicsMastered >= 15),
            RankRequirement("3 full mocks and recent mock average 50%+", s.mocksCompleted >= 3 && s.avgRecentMockPercent >= 50.0),
            RankRequirement("5 Error Monsters overcome", s.mistakesOvercome >= 5))
        Rank.S -> listOf(
            RankRequirement("Level 25 (now ${s.level})", s.level >= 25),
            RankRequirement("2500 questions answered", s.questionsAnswered >= 2500),
            RankRequirement("Accuracy 75%+", s.accuracy >= 0.75),
            RankRequirement("25 topics Strong or Mastered, 8 Mastered", s.topicsStrong + s.topicsMastered >= 25 && s.topicsMastered >= 8),
            RankRequirement("8 full mocks and recent average 65%+", s.mocksCompleted >= 8 && s.avgRecentMockPercent >= 65.0),
            RankRequirement("15 Error Monsters overcome", s.mistakesOvercome >= 15))
        Rank.NATIONAL -> listOf(
            RankRequirement("Level 30 (now ${s.level})", s.level >= 30),
            RankRequirement("Recent mock average 80%+ across 5+ mocks (now ${"%.0f".format(s.avgRecentMockPercent)}%)", s.mocksCompleted >= 5 && s.avgRecentMockPercent >= 80.0),
            RankRequirement("Accuracy 80%+", s.accuracy >= 0.80),
            RankRequirement("40 topics Strong or Mastered", s.topicsStrong + s.topicsMastered >= 40),
            RankRequirement("30 Error Monsters overcome", s.mistakesOvercome >= 30))
    }
    private fun pct(d: Double) = "${(d * 100).toInt()}%"

    fun requirements(rank: Rank, s: PlayerStats) = reqs(rank, s)

    /** Highest rank whose requirements AND all lower ranks' requirements are met. */
    fun evaluate(s: PlayerStats): Rank {
        var best = Rank.E
        for (r in Rank.values()) { if (reqs(r, s).all { it.met }) best = r else break }
        return best
    }

    data class RankChange(val from: Rank, val to: Rank, val reason: String)
    data class LevelChange(val from: Int, val to: Int, val reason: String)

    /** Ranks never go down; a rank-up is reported with the requirements that were just satisfied. */
    fun update(previous: Rank, s: PlayerStats): RankChange? {
        val now = evaluate(s)
        if (now.ordinal <= previous.ordinal) return null
        val met = reqs(now, s).joinToString("; ") { it.description }.ifBlank { "initial rank" }
        return RankChange(previous, now, "Requirements satisfied for ${now.label}: $met")
    }

    fun levelChange(oldXp: Int, newXp: Int, lastReason: String): LevelChange? {
        val a = Levels.levelFor(oldXp); val b = Levels.levelFor(newXp)
        return if (b > a) LevelChange(a, b, "Reached ${Levels.xpForLevel(b)} XP for Level $b ($lastReason)") else null
    }
}

data class AchievementDef(val id: String, val title: String, val description: String, val test: (PlayerStats) -> Boolean)

object Achievements {
    val all = listOf(
        AchievementDef("first_batch", "First Blood", "Answer your first 5 questions") { it.questionsAnswered >= 5 },
        AchievementDef("q100", "Hundred Slain", "Answer 100 questions") { it.questionsAnswered >= 100 },
        AchievementDef("correct250", "Sharpshooter", "250 correct answers") { it.correctAnswers >= 250 },
        AchievementDef("streak7", "Seven Dawns", "7-day streak") { it.longestStreak >= 7 },
        AchievementDef("streak30", "Unbroken", "30-day streak") { it.longestStreak >= 30 },
        AchievementDef("prepset1", "Gate Cleared", "Finish a full Preparation Set") { it.prepSetsCompleted >= 1 },
        AchievementDef("monster1", "Monster Slayer", "Overcome an Error Monster") { it.mistakesOvercome >= 1 },
        AchievementDef("monster10", "Monster Hunter", "Overcome 10 Error Monsters") { it.mistakesOvercome >= 10 },
        AchievementDef("strong5", "Specialist", "5 topics Strong or Mastered") { it.topicsStrong + it.topicsMastered >= 5 },
        AchievementDef("master1", "Archmage", "Master a topic") { it.topicsMastered >= 1 },
        AchievementDef("mock1", "Boss Battle", "Complete a full mock") { it.mocksCompleted >= 1 },
        AchievementDef("mock60", "Boss Slain", "Score 60%+ in a mock") { it.bestMockPercent >= 60.0 },
        AchievementDef("mock85", "Target Locked", "Score 85%+ in a mock") { it.bestMockPercent >= 85.0 },
        AchievementDef("lvl10", "Level 10", "Reach level 10") { it.level >= 10 },
        AchievementDef("lvl20", "Level 20", "Reach level 20") { it.level >= 20 }
    )
    fun unlocked(s: PlayerStats): Set<String> = all.filter { it.test(s) }.map { it.id }.toSet()
}
