package com.chandu.gatesystem.ai2027.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.chandu.gatesystem.ai2027.data.*
import com.chandu.gatesystem.ai2027.data.db.*
import com.chandu.gatesystem.ai2027.domain.*
import java.time.LocalDate

enum class PlanPhase { BEFORE, ACTIVE, AFTER }

/** Loads the 114-day plan parsed from the user's Word document and seeds the database from it. */
class PlanRepository(private val context: Context, private val db: AppDatabase, private val clock: Clock, private val settings: SettingsStore) {
    val plan: Plan by lazy { Plan.fromJson(context.assets.open("plan_114.json").bufferedReader().use { it.readText() }) }
    val start: LocalDate get() = LocalDate.parse(plan.startDate)
    val end: LocalDate get() = LocalDate.parse(plan.endDate)

    fun phase(today: LocalDate = clock.today()) = when { today.isBefore(start) -> PlanPhase.BEFORE; today.isAfter(end) -> PlanPhase.AFTER; else -> PlanPhase.ACTIVE }

    /** The plan day for today; before the plan starts shows Day 1, after it ends shows the last day. */
    fun focusDay(today: LocalDate = clock.today()): PlanDay = plan.forDate(today.toString()) ?: if (today.isBefore(start)) plan.days.first() else plan.days.last()
    fun daysUntilStart(today: LocalDate = clock.today()) = java.time.temporal.ChronoUnit.DAYS.between(today, start)
    fun epochOf(day: Int): Long = LocalDate.parse(plan.day(day)!!.date).toEpochDay()

    val topicRefs: List<TopicRef> by lazy {
        plan.days.flatMap { d ->
            listOf(TopicRef(d.learnTopicId, d.learnSubjectId, d.learnTopic), TopicRef(d.revisionTopicId, d.revisionSubjectId, d.revisionTopic),
                TopicRef(Topics.id("GA", d.gaTopic), "GA", d.gaTopic))
        }.distinctBy { it.id }
    }
    fun topicRef(id: String): TopicRef? = topicRefs.firstOrNull { it.id == id }

    /** Topics learned on plan days before [day] (used to build mixed exams and revision fallbacks). */
    fun earlierLearnTopics(day: Int): List<TopicRef> =
        plan.days.filter { it.day < day && it.learnSubjectId != "MIXED" }.map { TopicRef(it.learnTopicId, it.learnSubjectId, it.learnTopic) }

    suspend fun seedIfNeeded() = db.withTransaction {
        val c = db.content(); val p = db.player()
        if (c.topicCount() == 0) {
            val seen = HashSet<String>(); val rows = ArrayList<TopicEntity>()
            for (d in plan.days) {
                if (seen.add(d.learnTopicId)) rows += TopicEntity(d.learnTopicId, d.learnSubjectId, d.learnTopic, "LEARN", d.day)
            }
            for (d in plan.days) {
                if (seen.add(d.revisionTopicId)) rows += TopicEntity(d.revisionTopicId, d.revisionSubjectId, d.revisionTopic, "REVISION", d.day)
                val gid = Topics.id("GA", d.gaTopic)
                if (seen.add(gid)) rows += TopicEntity(gid, "GA", d.gaTopic, "GA", d.day)
            }
            c.insertTopics(rows)
        }
        if (p.questCount() == 0) {
            val rows = plan.days.flatMap { d ->
                QuestBuilder.build(d).map { q ->
                    DailyQuestEntity(q.id, q.day, LocalDate.parse(q.date).toEpochDay(), q.kind.name, q.name, q.detail, q.plannedMinMinutes, q.plannedMaxMinutes,
                        q.required, q.xp, q.requirement, q.topicId, q.subjectId, false, null, false)
                }
            }
            p.insertQuests(rows)
        }
        if (!settings.questsV2) {
            p.dropOldQuestKinds(); p.zeroQuestXp()
            p.insertQuests(plan.days.flatMap { d -> QuestBuilder.build(d).map { q ->
                DailyQuestEntity(q.id, q.day, LocalDate.parse(q.date).toEpochDay(), q.kind.name, q.name, q.detail, q.plannedMinMinutes, q.plannedMaxMinutes,
                    q.required, q.xp, q.requirement, q.topicId, q.subjectId, false, null, false)
            } })
            settings.questsV2 = true
        }
        p.insertAlarmDefaults(AlarmDefaults.all().map { it.toEntity() })
        if (p.player() == null) {
            p.upsertPlayer(PlayerEntity(1, settings.playerName, 0, 1, Rank.E.name, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, null, clock.millis()))
        }
    }
}
