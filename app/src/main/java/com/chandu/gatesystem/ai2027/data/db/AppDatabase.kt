package com.chandu.gatesystem.ai2027.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TopicEntity::class, LessonEntity::class, TopicProgressEntity::class, QuestionEntity::class, AnswerOptionEntity::class,
        PracticeSessionEntity::class, AttemptEntity::class, ExamSessionEntity::class, MistakeEntity::class, MistakeEventEntity::class,
        RevisionItemEntity::class, DailyQuestEntity::class, XpTransactionEntity::class, PlayerEntity::class, WorkoutEntity::class,
        AlarmSettingEntity::class, MockResultEntity::class, AchievementEntity::class, ProgressEventEntity::class, DailySummaryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun content(): ContentDao
    abstract fun sessions(): SessionDao
    abstract fun mistakes(): MistakeDao
    abstract fun player(): PlayerDao

    companion object {
        const val FILE_NAME = "gate_ai2027.db"

        /**
         * Migrations for future schema versions go here (add MIGRATION_1_2 etc. and bump `version`).
         * `fallbackToDestructiveMigration` is deliberately NOT used so an app update can never wipe player data.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
