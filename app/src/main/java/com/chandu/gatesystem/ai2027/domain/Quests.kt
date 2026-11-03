package com.chandu.gatesystem.ai2027.domain

import java.time.LocalDate

enum class QuestKind(val label: String, val manual: Boolean) {
    LEARNING("Learning", false), PREP_SET("Preparation Set", false), REVISION_SET("Revision Set", false),
    EXAM_SET("Exam Set", false), MOCK_TEST("Full Mock Test", false), SUBJECT_MOCK("Daily Subject Mock", false), GENERAL_APTITUDE("General Aptitude", false),
    ERROR_MONSTER("Error Monster", false), LIFE_SKILL("Life skill", true), WORKOUT("Workout", true),
    SLEEP("Sleep & routine", true), WEEKLY_REVIEW("Weekly review", true)
}

enum class QuestStatus { PENDING, IN_PROGRESS, COMPLETED, SKIPPED, MISSED }
enum class WorkoutStatus { DONE, PARTIAL, SKIPPED }

data class QuestSpec(
    val id: String, val day: Int, val date: String, val kind: QuestKind, val name: String, val detail: String,
    val plannedMinMinutes: Int?, val plannedMaxMinutes: Int?, val required: Int, val xp: Int, val requirement: String,
    val topicId: String? = null, val subjectId: String? = null
)

/** Facts about one day's genuine work, assembled from the database. Manual quests ignore activity facts. */
data class DayFacts(
    val lessonCompleted: Boolean = false,
    val prepAnswered: Int = 0,
    val revisionAnswered: Int = 0,
    val examSubmitted: Boolean = false,
    val mockSubmitted: Boolean = false,
    val subjectMockSubmitted: Boolean = false,
    val gaAnswered: Int = 0,
    val mistakeReattempts: Int = 0,
    val activeMistakes: Int = 0,
    val manualDone: Set<QuestKind> = emptySet(),
    val workout: WorkoutStatus? = null
)

object QuestBuilder {
    /** Quest XP values are separate from activity XP and are intentionally modest. */
    private val questXp = mapOf(
        QuestKind.LEARNING to 0, QuestKind.PREP_SET to 0, QuestKind.REVISION_SET to 0, QuestKind.EXAM_SET to 0,
        QuestKind.MOCK_TEST to 0, QuestKind.SUBJECT_MOCK to 0, QuestKind.GENERAL_APTITUDE to 0, QuestKind.ERROR_MONSTER to 0,
        QuestKind.LIFE_SKILL to 0, QuestKind.WORKOUT to 0, QuestKind.SLEEP to 0, QuestKind.WEEKLY_REVIEW to 0
    )
    /** Full mock every 3rd plan day. */
    fun isFullMockDay(day: Int) = day % 3 == 0

    /** Subject for the daily subject mock: today's subject, else the revision subject, else a rotating CS subject. */
    fun mockSubject(d: PlanDay): String {
        val cs = listOf("C", "DSA", "ALGO", "TOC", "CD", "OS", "DBMS", "CN", "DL", "COA", "EM")
        fun ok(s: String) = s != "MIXED" && s != "GA" && s.isNotBlank()
        return when { ok(d.learnSubjectId) -> d.learnSubjectId; ok(d.revisionSubjectId) -> d.revisionSubjectId; else -> cs[d.day % cs.size] }
    }
    fun id(day: Int, kind: QuestKind) = "d%03d-%s".format(day, kind.name)

    /** Builds the day's quests strictly from the document's data. Planned durations come from its daily routine. */
    fun build(d: PlanDay): List<QuestSpec> {
        val out = ArrayList<QuestSpec>()
        fun add(kind: QuestKind, name: String, detail: String, min: Int?, max: Int?, req: Int, requirement: String, topic: String? = null, subject: String? = null) =
            out.add(QuestSpec(id(d.day, kind), d.day, d.date, kind, name, detail, min, max, req, questXp.getValue(kind), requirement, topic, subject))

        val ms = mockSubject(d)
        add(QuestKind.SUBJECT_MOCK, "Daily Subject Mock: ${Subjects.get(ms).name}", "25 GATE-level questions (15 x 1 mark + 10 x 2 marks). XP = your net marks.", 40, 60, 1,
            "Submit today's subject mock. XP and level-ups come from your marks", null, ms)
        if (isFullMockDay(d.day)) {
            add(QuestKind.MOCK_TEST, "FULL MOCK — 65 Q / 100 marks", "Every 3rd day: full GATE CS pattern, 180 minutes", 180, 180, 1,
                "Complete the timed full mock, then analyse and tag every lost mark")
        }
        add(QuestKind.ERROR_MONSTER, "Error Monster: reattempt 3 mistakes", d.endOfDay ?: "Log P/R/E scores and reattempt three mistakes", 20, 30, 3,
            "Reattempt up to three logged mistakes (fewer if fewer exist)")
        add(QuestKind.LIFE_SKILL, "${d.lifeCategory}", d.lifeTask, 15, 30, 1, "Do it, then tick it manually")
        add(QuestKind.WORKOUT, "Workout", d.workout, 35, 35, 1, "Manual only: you tick Done / Partial / Skipped (5:15–5:50 AM)")
        add(QuestKind.SLEEP, "Sleep & routine", "Prepare tomorrow at 8:30 PM, reduce screens, sleep 9:00 PM (target 5:00 AM wake)", null, null, 1,
            "Manual: tick when you followed the sleep routine")
        if (d.weeklyReview) add(QuestKind.WEEKLY_REVIEW, "Weekly review", d.weeklyReviewText ?: "", 20, 30, 1, "Record focused hours, P/R/E accuracy, mock trend, five weakest topics")
        return out
    }
}

object QuestEvaluator {
    data class Progress(val done: Int, val required: Int, val status: QuestStatus) {
        val fraction get() = if (required == 0) 1f else (done.toFloat() / required).coerceIn(0f, 1f)
    }

    /**
     * Auto quests complete only when the genuine work is done. Manual quests (life skill, workout,
     * sleep, weekly review) are never completed by activity; workout is never auto-ticked.
     */
    fun evaluate(q: QuestSpec, f: DayFacts, today: LocalDate): Progress {
        val dayOver = LocalDate.parse(q.date).isBefore(today)
        fun finish(done: Int, req: Int): Progress {
            val d = minOf(done, req)
            val st = when {
                d >= req && req > 0 -> QuestStatus.COMPLETED
                d > 0 -> if (dayOver) QuestStatus.MISSED else QuestStatus.IN_PROGRESS
                else -> if (dayOver) QuestStatus.MISSED else QuestStatus.PENDING
            }
            return Progress(d, req, st)
        }
        return when (q.kind) {
            QuestKind.LEARNING -> finish(if (f.lessonCompleted) 1 else 0, 1)
            QuestKind.PREP_SET -> finish(f.prepAnswered, q.required)
            QuestKind.REVISION_SET -> finish(f.revisionAnswered, q.required)
            QuestKind.EXAM_SET -> finish(if (f.examSubmitted) 1 else 0, 1)
            QuestKind.MOCK_TEST -> finish(if (f.mockSubmitted) 1 else 0, 1)
            QuestKind.SUBJECT_MOCK -> finish(if (f.subjectMockSubmitted) 1 else 0, 1)
            QuestKind.GENERAL_APTITUDE -> finish(f.gaAnswered, q.required)
            QuestKind.ERROR_MONSTER -> {
                val req = minOf(q.required, maxOf(f.activeMistakes + f.mistakeReattempts, 0))
                if (req == 0) finish(if (f.revisionAnswered >= 1) 1 else 0, 1) else finish(f.mistakeReattempts, req)
            }
            QuestKind.WORKOUT -> when (f.workout) {
                WorkoutStatus.DONE -> Progress(1, 1, QuestStatus.COMPLETED)
                WorkoutStatus.PARTIAL -> Progress(0, 1, if (dayOver) QuestStatus.MISSED else QuestStatus.IN_PROGRESS)
                WorkoutStatus.SKIPPED -> Progress(0, 1, QuestStatus.SKIPPED)
                null -> Progress(0, 1, if (dayOver) QuestStatus.MISSED else QuestStatus.PENDING)
            }
            else -> if (q.kind in f.manualDone) Progress(1, 1, QuestStatus.COMPLETED)
                    else Progress(0, 1, if (dayOver) QuestStatus.MISSED else QuestStatus.PENDING)
        }
    }

    /** Completion percentage: partial workout counts as half; skipped counts as resolved-not-done (0). */
    fun dayPercent(specs: List<QuestSpec>, f: DayFacts, today: LocalDate): Int {
        if (specs.isEmpty()) return 0
        var sum = 0.0
        for (q in specs) {
            val p = evaluate(q, f, today)
            sum += if (q.kind == QuestKind.WORKOUT && f.workout == WorkoutStatus.PARTIAL) 0.5 else p.fraction.toDouble()
        }
        return Math.round(sum / specs.size * 100).toInt()
    }

    fun incomplete(specs: List<QuestSpec>, f: DayFacts, today: LocalDate): List<QuestSpec> =
        specs.filter { val s = evaluate(it, f, today).status; s != QuestStatus.COMPLETED && s != QuestStatus.SKIPPED }

    fun allCleared(specs: List<QuestSpec>, f: DayFacts, today: LocalDate): Boolean =
        specs.all { val s = evaluate(it, f, today).status; s == QuestStatus.COMPLETED || s == QuestStatus.SKIPPED }
}

object Streaks {
    const val QUALIFY_PERCENT = 70
    data class Result(val current: Int, val longest: Int)

    /** [qualifying] holds ISO dates whose completion >= QUALIFY_PERCENT. Today not yet qualifying doesn't break the streak. */
    fun compute(qualifying: Set<LocalDate>, today: LocalDate): Result {
        var cur = 0
        var d = if (today in qualifying) today else today.minusDays(1)
        while (d in qualifying) { cur++; d = d.minusDays(1) }
        var longest = 0; var run = 0; var prev: LocalDate? = null
        for (x in qualifying.sorted()) {
            run = if (prev != null && prev.plusDays(1) == x) run + 1 else 1
            longest = maxOf(longest, run); prev = x
        }
        return Result(cur, longest)
    }
}
