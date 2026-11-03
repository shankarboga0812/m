package com.chandu.gatesystem.ai2027.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** All dates are stored as epoch-day Longs (LocalDate.toEpochDay) and all enums as their String names. */

@Entity(tableName = "topics", indices = [Index("subjectId")])
data class TopicEntity(
    @PrimaryKey val topicId: String,
    val subjectId: String,
    val title: String,
    val kind: String,           // LEARN | REVISION | GA
    val firstDay: Int?          // first plan day on which the topic appears
)

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val topicId: String,
    val bodyJson: String,       // structured lesson JSON (see domain LessonContent)
    val source: String,         // CURATED | AI | SKELETON
    val updatedAt: Long
)

@Entity(tableName = "topic_progress")
data class TopicProgressEntity(
    @PrimaryKey val topicId: String,
    val lessonOpenedAt: Long?,
    val lessonCompleted: Boolean,
    val lessonCompletedEpochDay: Long?,
    val status: String,         // TopicStatus name
    val masteryScore: Int,
    val statusReason: String,
    val updatedAt: Long
)

@Entity(tableName = "questions", indices = [Index(value = ["fingerprint"], unique = true), Index("subjectId"), Index("topicId")])
data class QuestionEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val topicId: String,
    val subtopic: String,
    val statement: String,
    val type: String,
    val correctJson: String,    // JSON list of correct option indexes (empty for NAT)
    val natLow: Double?,
    val natHigh: Double?,
    val marks: Int,
    val negativeMarks: Double,
    val solution: String,
    val concept: String,
    val difficulty: String,
    val source: String,
    val sourceYear: Int?,
    val sourceNote: String?,
    val validation: String,
    val tagsJson: String,
    val fingerprint: String,
    val flagged: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "answer_options", primaryKeys = ["questionId", "idx"],
    foreignKeys = [ForeignKey(QuestionEntity::class, ["id"], ["questionId"], onDelete = ForeignKey.CASCADE)]
)
data class AnswerOptionEntity(
    val questionId: String,
    val idx: Int,
    val text: String,
    val isCorrect: Boolean,
    val explanation: String
)

@Entity(tableName = "practice_sessions", indices = [Index("day", "kind")])
data class PracticeSessionEntity(
    @PrimaryKey val id: String,
    val kind: String,           // PREP | REVISION | GA
    val day: Int,
    val epochDay: Long,
    val subjectId: String,
    val topicId: String,
    val title: String,
    val questionIdsJson: String,
    val batchSize: Int,
    val submittedBatches: Int,
    val draftJson: String,      // {"answers":{qid:{"sel":[..],"nat":x}},"times":{qid:sec}} — survives app restarts
    val mistakeMapJson: String, // qid -> mistakeId for revision questions that test an existing mistake
    val status: String,         // IN_PROGRESS | COMPLETED
    val xpAwarded: Int,
    val note: String,
    val createdAt: Long,
    val completedAt: Long?
)

@Entity(tableName = "attempts", indices = [Index(value = ["sessionId", "questionId"], unique = true), Index("topicId"), Index("day", "sessionKind"), Index("epochDay")])
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val sessionId: String,
    val sessionKind: String,    // PREP | REVISION | GA | EXAM | MOCK
    val topicId: String,
    val subjectId: String,
    val day: Int,
    val epochDay: Long,
    val selectedJson: String,
    val natValue: Double?,
    val verdict: String,
    val marks: Double,
    val timeSec: Int,
    val difficulty: String,
    val createdAt: Long
)

@Entity(tableName = "exam_sessions", indices = [Index("day", "kind")])
data class ExamSessionEntity(
    @PrimaryKey val id: String,
    val kind: String,           // EXAM | MOCK
    val mockType: String,       // FULL | SUBJECT | TOPIC | MIXED | CUSTOM | DAILY
    val day: Int,
    val epochDay: Long,
    val title: String,
    val questionIdsJson: String,
    val durationSec: Int,
    val remainingSec: Int,
    val responsesJson: String,
    val markedJson: String,
    val visitedJson: String,
    val timesJson: String,
    val status: String,         // IN_PROGRESS | SUBMITTED
    val startedAt: Long,
    val submittedAt: Long?,
    val netMarks: Double?,
    val maxMarks: Double?,
    val reportJson: String?,
    val xpAwarded: Int
)

@Entity(tableName = "mistakes", indices = [Index(value = ["dedupeKey"], unique = true), Index("status"), Index("topicId")])
data class MistakeEntity(
    @PrimaryKey val id: String,
    val dedupeKey: String,
    val subjectId: String,
    val topicId: String,
    val concept: String,
    val createdEpochDay: Long,
    val userAnswer: String,
    val correctAnswer: String,
    val category: String,
    val explanation: String,
    val occurrences: Int,
    val successfulRevisions: Int,
    val consecutiveSuccess: Int,
    val succeededIdsJson: String,
    val questionIdsJson: String,
    val status: String,
    val lastAttemptedEpochDay: Long?,
    val lastSuccessEpochDay: Long?,
    val nextRevisionEpochDay: Long?,
    val relatedJson: String,
    val source: String,
    val notes: String,
    val overcomeEpochDay: Long?,
    val recurrence: Int
)

@Entity(tableName = "mistake_events", indices = [Index("mistakeId"), Index("epochDay")])
data class MistakeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mistakeId: String,
    val epochDay: Long,
    val questionId: String,
    val kind: String,           // WRONG | REVISION_OK | REVISION_FAIL | REPEAT_NO_EVIDENCE
    val userAnswer: String
)

@Entity(tableName = "revision_items", indices = [Index("dueEpochDay"), Index("itemKey")])
data class RevisionItemEntity(
    @PrimaryKey val uniqueKey: String,     // "<itemKey>#<stage>" => no duplicate reminders
    val itemKey: String,
    val stage: Int,
    val dueEpochDay: Long,
    val doneEpochDay: Long?,
    val label: String,
    val subjectId: String,
    val topicId: String,
    val learnDay: Int
)

@Entity(tableName = "daily_quests", indices = [Index("day")])
data class DailyQuestEntity(
    @PrimaryKey val id: String,
    val day: Int,
    val epochDay: Long,
    val kind: String,
    val name: String,
    val detail: String,
    val plannedMin: Int?,
    val plannedMax: Int?,
    val required: Int,
    val xp: Int,
    val requirement: String,
    val topicId: String?,
    val subjectId: String?,
    val manualDone: Boolean,
    val manualDoneAt: Long?,
    val xpAwarded: Boolean
)

@Entity(tableName = "xp_transactions", indices = [Index(value = ["sourceKey"], unique = true)])
data class XpTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String,
    val amount: Int,
    val reason: String,
    val epochDay: Long,
    val createdAt: Long
)

@Entity(tableName = "player")
data class PlayerEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val totalXp: Int,
    val level: Int,
    val rank: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val questionsAnswered: Int,
    val correctAnswers: Int,
    val topicsStrong: Int,
    val topicsMastered: Int,
    val questsCompleted: Int,
    val revisionsCompleted: Int,
    val prepSetsCompleted: Int,
    val mistakesOvercome: Int,
    val mocksCompleted: Int,
    val bestMockPercent: Double,
    val avgRecentMockPercent: Double,
    val lastActiveEpochDay: Long?,
    val createdAt: Long
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey val epochDay: Long,
    val day: Int,
    val planned: String,
    val status: String,         // DONE | PARTIAL | SKIPPED  (only ever set by a user tap)
    val note: String,
    val updatedAt: Long
)

@Entity(tableName = "alarm_settings")
data class AlarmSettingEntity(
    @PrimaryKey val id: String,             // AlarmId name
    val enabled: Boolean,
    val minuteOfDay: Int,                   // -1 = no time chosen
    val sound: Boolean,
    val vibrate: Boolean,
    val snoozeMinutes: Int
)

@Entity(tableName = "mock_results")
data class MockResultEntity(
    @PrimaryKey val id: String,
    val day: Int,
    val type: String,
    val percent: Double,
    val netMarks: Double,
    val maxMarks: Double,
    val accuracy: Double,
    val attempted: Int,
    val total: Int,
    val timeUsedSec: Int,
    val durationSec: Int,
    val epochDay: Long,
    val topicJson: String
)

@Entity(tableName = "achievements")
data class AchievementEntity(@PrimaryKey val id: String, val unlockedAt: Long, val reason: String)

@Entity(tableName = "progress_events")
data class ProgressEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,           // LEVEL_UP | RANK_UP | ACHIEVEMENT | MASTERY
    val title: String,
    val reason: String,
    val createdAt: Long,
    val seen: Boolean
)

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val epochDay: Long,
    val day: Int,
    val percent: Int,
    val qualifies: Boolean,
    val xpEarned: Int,
    val questionsAnswered: Int,
    val correct: Int
)
