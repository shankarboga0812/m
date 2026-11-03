package com.chandu.gatesystem.ai2027.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTopics(l: List<TopicEntity>)

    @Query("SELECT COUNT(*) FROM topics")
    suspend fun topicCount(): Int

    @Query("SELECT * FROM topics ORDER BY subjectId, COALESCE(firstDay, 999), title")
    fun topics(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE topicId = :id")
    suspend fun topic(id: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE subjectId = :s ORDER BY COALESCE(firstDay, 999), title")
    fun topicsBySubject(s: String): Flow<List<TopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLesson(l: LessonEntity)

    @Query("SELECT * FROM lessons WHERE topicId = :id")
    fun lesson(id: String): Flow<LessonEntity?>

    @Query("SELECT * FROM lessons WHERE topicId = :id")
    suspend fun lessonOnce(id: String): LessonEntity?

    @Query("SELECT COUNT(*) FROM lessons WHERE source != 'SKELETON'")
    fun authoredLessonCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: TopicProgressEntity)

    @Query("SELECT * FROM topic_progress WHERE topicId = :id")
    suspend fun progressOnce(id: String): TopicProgressEntity?

    @Query("SELECT * FROM topic_progress WHERE topicId = :id")
    fun progress(id: String): Flow<TopicProgressEntity?>

    @Query("SELECT * FROM topic_progress")
    fun allProgress(): Flow<List<TopicProgressEntity>>

    @Query("SELECT COUNT(*) FROM topic_progress WHERE status = :s")
    suspend fun countStatus(s: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestion(q: QuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOptions(o: List<AnswerOptionEntity>)

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun question(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun questions(ids: List<String>): List<QuestionEntity>

    @Query("SELECT * FROM answer_options WHERE questionId IN (:ids) ORDER BY questionId, idx")
    suspend fun options(ids: List<String>): List<AnswerOptionEntity>

    @Query("SELECT id FROM questions WHERE fingerprint = :fp LIMIT 1")
    suspend fun idByFingerprint(fp: String): String?

    @Query("SELECT fingerprint FROM questions")
    suspend fun fingerprints(): List<String>

    @Query("SELECT COUNT(*) FROM questions")
    fun questionCount(): Flow<Int>

    @Query("SELECT source, COUNT(*) AS n FROM questions GROUP BY source")
    fun countBySource(): Flow<List<SourceCount>>

    @Query("SELECT COUNT(*) FROM questions WHERE flagged = 1")
    fun flaggedCount(): Flow<Int>

    @Query("SELECT * FROM questions WHERE flagged = 1 ORDER BY createdAt DESC LIMIT 50")
    fun flaggedQuestions(): Flow<List<QuestionEntity>>

    @Query("UPDATE questions SET flagged = :f WHERE id = :id")
    suspend fun setFlagged(id: String, f: Boolean)

    @Query("SELECT * FROM questions WHERE topicId = :t ORDER BY createdAt DESC LIMIT :n")
    suspend fun recentForTopic(t: String, n: Int): List<QuestionEntity>

    @Query("SELECT id FROM questions")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM questions WHERE tagsJson LIKE '%pool:%'")
    suspend fun bankCount(): Int

    // FIX: Alias subjectId as source to match SourceCount.
    @Query("""
        SELECT subjectId AS source, COUNT(*) AS n
        FROM questions
        WHERE tagsJson LIKE '%pool:%'
        GROUP BY subjectId
    """)
    fun bankBySubject(): Flow<List<SourceCount>>

    @Query("SELECT COUNT(*) FROM questions WHERE tagsJson LIKE :roleLike AND subjectId = :s")
    suspend fun bankRoleCount(s: String, roleLike: String): Int

    @Query("SELECT DISTINCT questionId FROM attempts")
    suspend fun attemptedIds(): List<String>

    @Query("""
        SELECT COUNT(*) FROM questions
        WHERE flagged = 0
        AND subjectId = :s
        AND tagsJson LIKE :roleLike
        AND id NOT IN (SELECT questionId FROM attempts)
    """)
    suspend fun unseenCount(s: String, roleLike: String): Int

    @Query("SELECT fingerprint FROM questions WHERE id IN (SELECT questionId FROM attempts)")
    suspend fun attemptedFingerprints(): List<String>

    @Query("""
        SELECT * FROM questions
        WHERE flagged = 0
        AND subjectId = :s
        AND (:t IS NULL OR topicId = :t)
        AND (:c IS NULL OR concept = :c)
        AND (:marks IS NULL OR marks = :marks)
        AND (:roleLike IS NULL OR tagsJson LIKE :roleLike)
        AND id NOT IN (SELECT questionId FROM attempts)
        AND id NOT IN (:excludeIds)
        ORDER BY ((rowid * :salt) % 100003)
        LIMIT :n
    """)
    suspend fun unseen(
        s: String,
        t: String?,
        c: String?,
        marks: Int?,
        roleLike: String?,
        excludeIds: List<String>,
        salt: Int,
        n: Int
    ): List<QuestionEntity>

    @Query("SELECT id FROM questions WHERE flagged = 1 AND id IN (:ids)")
    suspend fun flaggedAmong(ids: List<String>): List<String>

    // FIX: Alias subjectId as source to match SourceCount.
    @Query("""
        SELECT subjectId AS source, COUNT(*) AS n
        FROM questions
        WHERE flagged = 0
        GROUP BY subjectId
    """)
    fun countBySubject(): Flow<List<SourceCount>>

    @Query("""
        UPDATE topic_progress
        SET lessonOpenedAt = :t
        WHERE topicId = :id AND lessonOpenedAt IS NULL
    """)
    suspend fun touchOpened(id: String, t: Long)
}

data class SourceCount(
    val source: String,
    val n: Int
)

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPractice(p: PracticeSessionEntity)

    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    suspend fun practice(id: String): PracticeSessionEntity?

    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    fun practiceFlow(id: String): Flow<PracticeSessionEntity?>

    @Query("SELECT * FROM practice_sessions WHERE day = :day AND kind = :kind ORDER BY createdAt DESC LIMIT 1")
    suspend fun practiceForDay(day: Int, kind: String): PracticeSessionEntity?

    @Query("SELECT * FROM practice_sessions WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC")
    fun unfinishedPractice(): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions ORDER BY createdAt DESC LIMIT 100")
    fun practiceHistory(): Flow<List<PracticeSessionEntity>>

    @Query("SELECT COUNT(*) FROM practice_sessions WHERE kind = 'PREP' AND status = 'COMPLETED'")
    suspend fun completedPrepCount(): Int

    @Query("SELECT COUNT(*) FROM practice_sessions WHERE kind = 'REVISION' AND status = 'COMPLETED'")
    suspend fun completedRevisionCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttempt(a: AttemptEntity): Long

    @Query("SELECT * FROM attempts WHERE sessionId = :s")
    suspend fun attemptsForSession(s: String): List<AttemptEntity>

    @Query("SELECT * FROM attempts WHERE topicId = :t ORDER BY createdAt")
    suspend fun attemptsForTopic(t: String): List<AttemptEntity>

    @Query("""
        SELECT COUNT(*) FROM attempts
        WHERE day = :day AND sessionKind = :kind AND verdict != 'UNANSWERED'
    """)
    fun answeredForDayKind(day: Int, kind: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM attempts
        WHERE day = :day AND sessionKind = :kind AND verdict != 'UNANSWERED'
    """)
    suspend fun answeredForDayKindOnce(day: Int, kind: String): Int

    @Query("SELECT COUNT(*) FROM attempts WHERE verdict != 'UNANSWERED'")
    suspend fun totalAnswered(): Int

    @Query("SELECT COUNT(*) FROM attempts WHERE verdict = 'CORRECT'")
    suspend fun totalCorrect(): Int

    @Query("SELECT COUNT(*) FROM attempts WHERE epochDay = :d AND verdict != 'UNANSWERED'")
    suspend fun answeredOn(d: Long): Int

    @Query("SELECT COUNT(*) FROM attempts WHERE epochDay = :d AND verdict = 'CORRECT'")
    suspend fun correctOn(d: Long): Int

    @Query("""
        SELECT topicId,
               COUNT(*) AS attempted,
               SUM(CASE WHEN verdict = 'CORRECT' THEN 1 ELSE 0 END) AS correct
        FROM attempts
        WHERE verdict != 'UNANSWERED'
        GROUP BY topicId
    """)
    fun topicAccuracy(): Flow<List<TopicAccuracy>>

    @Query("""
        SELECT topicId,
               COUNT(*) AS attempted,
               SUM(CASE WHEN verdict = 'CORRECT' THEN 1 ELSE 0 END) AS correct
        FROM attempts
        WHERE verdict != 'UNANSWERED' AND subjectId = :s
        GROUP BY topicId
    """)
    suspend fun topicAccuracyForSubject(s: String): List<TopicAccuracy>

    @Query("""
        SELECT epochDay,
               COUNT(*) AS attempted,
               SUM(CASE WHEN verdict = 'CORRECT' THEN 1 ELSE 0 END) AS correct
        FROM attempts
        WHERE verdict != 'UNANSWERED' AND epochDay >= :from
        GROUP BY epochDay
        ORDER BY epochDay
    """)
    fun dailyAccuracy(from: Long): Flow<List<DayAccuracy>>

    @Query("SELECT DISTINCT questionId FROM attempts WHERE topicId = :t")
    suspend fun questionIdsSeenForTopic(t: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExam(e: ExamSessionEntity)

    @Query("SELECT * FROM exam_sessions WHERE id = :id")
    suspend fun exam(id: String): ExamSessionEntity?

    @Query("SELECT * FROM exam_sessions WHERE id = :id")
    fun examFlow(id: String): Flow<ExamSessionEntity?>

    @Query("SELECT * FROM exam_sessions WHERE day = :day AND kind = :kind ORDER BY startedAt DESC LIMIT 1")
    suspend fun examForDay(day: Int, kind: String): ExamSessionEntity?

    @Query("SELECT * FROM exam_sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC")
    fun unfinishedExams(): Flow<List<ExamSessionEntity>>

    @Query("SELECT * FROM exam_sessions WHERE status = 'SUBMITTED' ORDER BY submittedAt DESC LIMIT 100")
    fun examHistory(): Flow<List<ExamSessionEntity>>

    @Query("SELECT COUNT(*) FROM exam_sessions WHERE day = :day AND kind = :kind AND status = 'SUBMITTED'")
    fun submittedCount(day: Int, kind: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM exam_sessions WHERE day = :day AND kind = :kind AND status = 'SUBMITTED'")
    suspend fun submittedCountOnce(day: Int, kind: String): Int
}

data class TopicAccuracy(
    val topicId: String,
    val attempted: Int,
    val correct: Int
)

data class DayAccuracy(
    val epochDay: Long,
    val attempted: Int,
    val correct: Int
)

@Dao
interface MistakeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: MistakeEntity)

    @Query("SELECT * FROM mistakes WHERE dedupeKey = :k")
    suspend fun byKey(k: String): MistakeEntity?

    @Query("SELECT * FROM mistakes WHERE id = :id")
    suspend fun byId(id: String): MistakeEntity?

    @Query("SELECT * FROM mistakes WHERE id = :id")
    fun byIdFlow(id: String): Flow<MistakeEntity?>

    @Query("""
        SELECT * FROM mistakes
        ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END,
                 COALESCE(nextRevisionEpochDay, 99999999),
                 occurrences DESC
    """)
    fun all(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes")
    suspend fun allOnce(): List<MistakeEntity>

    @Query("SELECT COUNT(*) FROM mistakes WHERE status = 'ACTIVE'")
    suspend fun activeCount(): Int

    @Query("SELECT COUNT(*) FROM mistakes WHERE status = 'ACTIVE'")
    fun activeCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM mistakes WHERE status = 'OVERCOME'")
    suspend fun overcomeCount(): Int

    @Query("SELECT COUNT(*) FROM mistakes WHERE status = 'ACTIVE' AND topicId = :t")
    suspend fun activeInTopic(t: String): Int

    @Query("""
        SELECT COUNT(*) FROM mistakes
        WHERE status = 'ACTIVE'
        AND topicId = :t
        AND nextRevisionEpochDay < :today
    """)
    suspend fun overdueInTopic(t: String, today: Long): Int

    @Query("UPDATE mistakes SET notes = :n WHERE id = :id")
    suspend fun setNotes(id: String, n: String)

    @Insert
    suspend fun insertEvent(e: MistakeEventEntity)

    @Query("SELECT * FROM mistake_events WHERE mistakeId = :id ORDER BY id DESC")
    fun events(id: String): Flow<List<MistakeEventEntity>>

    @Query("""
        SELECT COUNT(*) FROM mistake_events
        WHERE epochDay = :d
        AND kind IN ('REVISION_OK','REVISION_FAIL')
    """)
    suspend fun revisionEventsOn(d: Long): Int

    @Query("""
        SELECT COUNT(*) FROM mistake_events
        WHERE epochDay = :d
        AND kind IN ('REVISION_OK','REVISION_FAIL')
    """)
    fun revisionEventsOnFlow(d: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRevisions(l: List<RevisionItemEntity>)

    @Query("SELECT * FROM revision_items WHERE doneEpochDay IS NULL ORDER BY dueEpochDay")
    suspend fun pendingRevisions(): List<RevisionItemEntity>

    @Query("SELECT * FROM revision_items WHERE doneEpochDay IS NULL ORDER BY dueEpochDay")
    fun pendingRevisionsFlow(): Flow<List<RevisionItemEntity>>

    @Query("SELECT * FROM revision_items ORDER BY dueEpochDay")
    fun allRevisionsFlow(): Flow<List<RevisionItemEntity>>

    @Query("""
        UPDATE revision_items
        SET doneEpochDay = :d
        WHERE uniqueKey = :k AND doneEpochDay IS NULL
    """)
    suspend fun markRevisionDone(k: String, d: Long)

    @Query("""
        UPDATE revision_items
        SET dueEpochDay = :d
        WHERE uniqueKey = :k AND doneEpochDay IS NULL
    """)
    suspend fun moveRevision(k: String, d: Long)
}

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuests(l: List<DailyQuestEntity>)

    @Query("SELECT COUNT(*) FROM daily_quests")
    suspend fun questCount(): Int

    /** v2 quest model: no lesson/prep/exam/GA quests, full mock only every 3rd day, XP comes from marks and workout. */
    @Query("DELETE FROM daily_quests WHERE kind IN ('LEARNING','PREP_SET','REVISION_SET','EXAM_SET','GENERAL_APTITUDE') OR (kind = 'MOCK_TEST' AND day % 3 != 0)")
    suspend fun dropOldQuestKinds()

    @Query("UPDATE daily_quests SET xp = 0")
    suspend fun zeroQuestXp()

    @Query("SELECT * FROM daily_quests WHERE day = :day ORDER BY rowid")
    fun questsForDay(day: Int): Flow<List<DailyQuestEntity>>

    @Query("SELECT * FROM daily_quests WHERE day = :day ORDER BY rowid")
    suspend fun questsForDayOnce(day: Int): List<DailyQuestEntity>

    @Query("SELECT * FROM daily_quests WHERE id = :id")
    suspend fun quest(id: String): DailyQuestEntity?

    @Update
    suspend fun updateQuest(q: DailyQuestEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertXp(x: XpTransactionEntity): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions")
    suspend fun totalXp(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions")
    fun totalXpFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE epochDay = :d")
    suspend fun xpOn(d: Long): Int

    @Query("SELECT * FROM xp_transactions ORDER BY id DESC LIMIT 60")
    fun recentXp(): Flow<List<XpTransactionEntity>>

    @Query("SELECT reason FROM xp_transactions ORDER BY id DESC LIMIT 1")
    suspend fun lastXpReason(): String?

    @Query("SELECT COUNT(*) FROM xp_transactions WHERE sourceKey LIKE 'quest:%'")
    suspend fun questXpCount(): Int

    @Query("SELECT * FROM daily_quests ORDER BY day, rowid")
    suspend fun allQuestsOnce(): List<DailyQuestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayer(p: PlayerEntity)

    @Query("SELECT * FROM player WHERE id = 1")
    suspend fun player(): PlayerEntity?

    @Query("SELECT * FROM player WHERE id = 1")
    fun playerFlow(): Flow<PlayerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkout(w: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE epochDay = :d")
    suspend fun workout(d: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE epochDay = :d")
    fun workoutFlow(d: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts ORDER BY epochDay DESC")
    fun workouts(): Flow<List<WorkoutEntity>>

    @Query("DELETE FROM workouts WHERE epochDay = :d")
    suspend fun clearWorkout(d: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlarmDefaults(l: List<AlarmSettingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlarm(a: AlarmSettingEntity)

    @Query("SELECT * FROM alarm_settings")
    fun alarms(): Flow<List<AlarmSettingEntity>>

    @Query("SELECT * FROM alarm_settings")
    suspend fun alarmsOnce(): List<AlarmSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMock(m: MockResultEntity)

    @Query("SELECT * FROM mock_results ORDER BY epochDay, id")
    fun mocks(): Flow<List<MockResultEntity>>

    @Query("SELECT * FROM mock_results ORDER BY epochDay, id")
    suspend fun mocksOnce(): List<MockResultEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(a: AchievementEntity): Long

    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    fun achievements(): Flow<List<AchievementEntity>>

    @Query("SELECT id FROM achievements")
    suspend fun achievementIds(): List<String>

    @Insert
    suspend fun insertEvent(e: ProgressEventEntity)

    @Query("SELECT * FROM progress_events ORDER BY id DESC LIMIT 100")
    fun events(): Flow<List<ProgressEventEntity>>

    @Query("SELECT * FROM progress_events WHERE seen = 0 ORDER BY id")
    fun unseenEvents(): Flow<List<ProgressEventEntity>>

    @Query("UPDATE progress_events SET seen = 1")
    suspend fun markEventsSeen()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSummary(s: DailySummaryEntity)

    @Query("SELECT * FROM daily_summaries ORDER BY epochDay")
    fun summaries(): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summaries ORDER BY epochDay")
    suspend fun summariesOnce(): List<DailySummaryEntity>
}