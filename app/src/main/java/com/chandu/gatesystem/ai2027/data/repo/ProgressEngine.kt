package com.chandu.gatesystem.ai2027.data.repo

import com.chandu.gatesystem.ai2027.data.*
import com.chandu.gatesystem.ai2027.data.db.*
import com.chandu.gatesystem.ai2027.domain.*
import java.time.LocalDate

/**
 * Derives quests, XP, mastery, level, rank, streak and achievements from stored facts.
 * Everything here is idempotent: XP uses unique source keys; statuses are recomputed from attempts.
 * Callers invoke these inside a Room transaction.
 */
class ProgressEngine(private val db: AppDatabase, private val planRepo: PlanRepository, private val clock: Clock, private val settings: SettingsStore) {
    private val content = db.content(); private val sessions = db.sessions(); private val mistakes = db.mistakes(); private val player = db.player()

    suspend fun award(a: XpAward, epochDay: Long): Boolean {
        if (a.amount <= 0) return false
        val id = player.insertXp(XpTransactionEntity(sourceKey = a.key, amount = a.amount, reason = a.reason, epochDay = epochDay, createdAt = clock.millis()))
        return id != -1L
    }

    suspend fun factsFor(day: Int): DayFacts {
        val pd = planRepo.plan.day(day) ?: return DayFacts()
        val epoch = planRepo.epochOf(day)
        val manual = player.questsForDayOnce(day).filter { it.manualDone }.map { QuestKind.valueOf(it.kind) }.toSet()
        val lesson = content.progressOnce(pd.learnTopicId)?.lessonCompleted == true
        return DayFacts(
            lessonCompleted = lesson,
            prepAnswered = sessions.answeredForDayKindOnce(day, "PREP"),
            revisionAnswered = sessions.answeredForDayKindOnce(day, "REVISION"),
            examSubmitted = sessions.submittedCountOnce(day, "EXAM") > 0,
            mockSubmitted = sessions.submittedCountOnce(day, "MOCK") > 0,
            subjectMockSubmitted = sessions.submittedCountOnce(day, "SUBMOCK") > 0,
            gaAnswered = sessions.answeredForDayKindOnce(day, "GA"),
            mistakeReattempts = mistakes.revisionEventsOn(epoch),
            activeMistakes = mistakes.activeCount(),
            manualDone = manual,
            workout = player.workout(epoch)?.let { runCatching { WorkoutStatus.valueOf(it.status) }.getOrNull() }
        )
    }

    /** Re-evaluates one plan day: quest XP, day-clear XP, completion summary, streak bonus. */
    suspend fun recomputeDay(day: Int) {
        val pd = planRepo.plan.day(day) ?: return
        val epoch = planRepo.epochOf(day); val today = clock.today(); val rules = settings.xpRules()
        val quests = player.questsForDayOnce(day)
        val specs = quests.map { it.toSpec() }
        val facts = factsFor(day)
        for (e in quests) {
            val spec = e.toSpec()
            if (QuestEvaluator.evaluate(spec, facts, today).status == QuestStatus.COMPLETED) {
                award(XpEngine.quest(rules, spec.id, spec.xp), epoch)
                if (!e.xpAwarded) player.updateQuest(e.copy(xpAwarded = true))
            }
        }
        if (specs.isNotEmpty() && QuestEvaluator.allCleared(specs, facts, today)) award(XpEngine.dayClear(rules, pd.date), epoch)
        val pct = QuestEvaluator.dayPercent(specs, facts, today)
        val qualifies = pct >= Streaks.QUALIFY_PERCENT
        player.upsertSummary(DailySummaryEntity(epoch, day, pct, qualifies, player.xpOn(epoch), sessions.answeredOn(epoch), sessions.correctOn(epoch)))
        if (qualifies && LocalDate.ofEpochDay(epoch) == today) {
            val q = player.summariesOnce().filter { it.qualifies }.map { LocalDate.ofEpochDay(it.epochDay) }.toSet()
            val st = Streaks.compute(q, today)
            if (st.current >= 2) award(XpEngine.streak(rules, pd.date, st.current), epoch)
        }
    }

    suspend fun recomputeTopic(topicId: String) {
        val today = clock.today()
        val attempts = sessions.attemptsForTopic(topicId).filter { it.verdict != Verdict.UNANSWERED.name }.map {
            AttemptRecord(it.questionId, it.verdict == Verdict.CORRECT.name, Difficulty.valueOf(it.difficulty), LocalDate.ofEpochDay(it.epochDay), it.sessionKind == "REVISION")
        }
        val prog = content.progressOnce(topicId)
        val res = MasteryCalculator.compute(attempts, prog?.lessonOpenedAt != null || prog?.lessonCompleted == true,
            mistakes.activeInTopic(topicId), mistakes.overdueInTopic(topicId, today.toEpochDay()), today)
        val now = clock.millis()
        val old = prog?.status
        content.upsertProgress(TopicProgressEntity(topicId, prog?.lessonOpenedAt, prog?.lessonCompleted ?: false, prog?.lessonCompletedEpochDay, res.status.name, res.score, res.reason, now))
        if (old != res.status.name && res.status in setOf(TopicStatus.STRONG, TopicStatus.MASTERED, TopicStatus.REVISION_REQUIRED)) {
            val title = content.topic(topicId)?.title ?: topicId
            player.insertEvent(ProgressEventEntity(kind = "MASTERY", title = "$title is now ${res.status.label}", reason = res.reason, createdAt = now, seen = false))
        }
    }

    /** Recomputes totals, level, rank (never decreases), achievements and records the reason for each change. */
    suspend fun recomputePlayer() {
        val old = player.player() ?: return
        val today = clock.today(); val now = clock.millis()
        val xp = player.totalXp()
        val mocks = player.mocksOnce().map { it.toDomain() }
        val streaks = Streaks.compute(player.summariesOnce().filter { it.qualifies }.map { LocalDate.ofEpochDay(it.epochDay) }.toSet(), today)
        val stats = PlayerStats(
            totalXp = xp, questionsAnswered = sessions.totalAnswered(), correctAnswers = sessions.totalCorrect(),
            topicsStrong = content.countStatus(TopicStatus.STRONG.name), topicsMastered = content.countStatus(TopicStatus.MASTERED.name),
            mocksCompleted = mocks.size, bestMockPercent = mocks.maxOfOrNull { it.percent } ?: 0.0, avgRecentMockPercent = MockAnalytics.recentAverage(mocks),
            mistakesOvercome = mistakes.overcomeCount(), streak = streaks.current, longestStreak = maxOf(old.longestStreak, streaks.longest),
            questsCompleted = player.questXpCount(), revisionsCompleted = sessions.completedRevisionCount(), prepSetsCompleted = sessions.completedPrepCount()
        )
        val prevRank = runCatching { Rank.valueOf(old.rank) }.getOrDefault(Rank.E)
        val rc = RankEvaluator.update(prevRank, stats)
        val reason = player.lastXpReason() ?: "XP gained"
        RankEvaluator.levelChange(old.totalXp, xp, reason)?.let {
            player.insertEvent(ProgressEventEntity(kind = "LEVEL_UP", title = "Level ${it.to} reached", reason = it.reason, createdAt = now, seen = false))
        }
        rc?.let { player.insertEvent(ProgressEventEntity(kind = "RANK_UP", title = "Rank up: ${it.to.label} — ${it.to.title}", reason = it.reason, createdAt = now, seen = false)) }
        val have = player.achievementIds().toSet()
        for (id in Achievements.unlocked(stats) - have) {
            val def = Achievements.all.first { it.id == id }
            if (player.insertAchievement(AchievementEntity(id, now, def.description)) != -1L)
                player.insertEvent(ProgressEventEntity(kind = "ACHIEVEMENT", title = "Achievement: ${def.title}", reason = def.description, createdAt = now, seen = false))
        }
        player.upsertPlayer(old.copy(
            totalXp = xp, level = stats.level, rank = (rc?.to ?: prevRank).name, currentStreak = streaks.current, longestStreak = stats.longestStreak,
            questionsAnswered = stats.questionsAnswered, correctAnswers = stats.correctAnswers, topicsStrong = stats.topicsStrong, topicsMastered = stats.topicsMastered,
            questsCompleted = stats.questsCompleted, revisionsCompleted = stats.revisionsCompleted, prepSetsCompleted = stats.prepSetsCompleted,
            mistakesOvercome = stats.mistakesOvercome, mocksCompleted = stats.mocksCompleted, bestMockPercent = stats.bestMockPercent,
            avgRecentMockPercent = stats.avgRecentMockPercent, lastActiveEpochDay = today.toEpochDay()
        ))
    }

    suspend fun currentStats(): PlayerStats? {
        val p = player.player() ?: return null
        return PlayerStats(p.totalXp, p.questionsAnswered, p.correctAnswers, p.topicsStrong, p.topicsMastered, p.mocksCompleted, p.bestMockPercent,
            p.avgRecentMockPercent, p.mistakesOvercome, p.currentStreak, p.longestStreak, p.questsCompleted, p.revisionsCompleted, p.prepSetsCompleted)
    }
}
