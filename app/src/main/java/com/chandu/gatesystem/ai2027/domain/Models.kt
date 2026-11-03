package com.chandu.gatesystem.ai2027.domain

enum class QType { MCQ, MSQ, NAT }
enum class Difficulty(val weight: Double) { EASY(1.0), MEDIUM(1.5), HARD(2.0) }

/** Where a question came from. Never present AI/template questions as official GATE. */
enum class QSource(val label: String) {
    VERIFIED_PYQ("Verified previous-year question"),
    BANK_ORIGINAL("Original practice question (hand-authored)"),
    TEMPLATE_GENERATED("Original practice question (code-generated, answer computed)"),
    AI_GENERATED("AI-generated practice (not an official GATE question)")
}

enum class ValidationStatus {
    /** Answer key computed by deterministic code at generation time. */
    VERIFIED_DETERMINISTIC,
    /** Hand-authored and reviewed by schema validation only. */
    VALIDATED_SCHEMA,
    /** AI-generated, passed schema checks, answer not independently verified. */
    UNVERIFIED_AI,
    /** Ambiguous/invalid: excluded from scoring and exams, kept for review. */
    FLAGGED
}

/** Inclusive numeric range accepted for a NAT answer. */
data class NatAnswer(val low: Double, val high: Double) {
    fun accepts(v: Double): Boolean = !v.isNaN() && v >= low - EPS && v <= high + EPS
    companion object {
        const val EPS = 1e-9
        fun exact(v: Double, tolerance: Double = 0.0) = NatAnswer(v - tolerance, v + tolerance)
    }
}

data class Question(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val subtopic: String,
    val statement: String,
    val type: QType,
    val options: List<String>,
    val correctOptions: Set<Int>,
    val nat: NatAnswer?,
    val marks: Int,
    val negativeMarks: Double,
    val solution: String,
    val optionExplanations: List<String>,
    val concept: String,
    val difficulty: Difficulty,
    val source: QSource,
    val sourceYear: Int? = null,
    val sourceNote: String? = null,
    val validation: ValidationStatus,
    val tags: List<String> = emptyList()
) {
    /** What the student interface may see before submission: NO key, solution or explanations. */
    fun toStudentView() = QuestionView(id, subjectId, topicId, subtopic, statement, type, options, marks, negativeMarks, difficulty, source)

    /** Human readable correct answer (used only after submission). */
    fun correctAnswerText(): String = when (type) {
        QType.NAT -> nat!!.let { if (it.low == it.high) fmt(it.low) else "${fmt(it.low)} to ${fmt(it.high)}" }
        else -> correctOptions.sorted().joinToString(", ") { "${'A' + it}) ${options[it]}" }
    }
    companion object {
        fun fmt(d: Double): String = if (d == Math.floor(d) && Math.abs(d) < 1e12) d.toLong().toString() else String.format("%.4f", d).trimEnd('0').trimEnd('.')
    }
}

/** Student-facing question: deliberately has no answer key, solution or explanations. */
data class QuestionView(
    val id: String, val subjectId: String, val topicId: String, val subtopic: String,
    val statement: String, val type: QType, val options: List<String>,
    val marks: Int, val negativeMarks: Double, val difficulty: Difficulty, val source: QSource
)

/** A student's response. selected = option indexes; nat = numeric answer text parsed. */
data class Response(val selected: Set<Int> = emptySet(), val nat: Double? = null) {
    fun isAnswered(type: QType) = if (type == QType.NAT) nat != null else selected.isNotEmpty()
}

enum class Verdict { CORRECT, WRONG, UNANSWERED }

data class AnswerResult(val verdict: Verdict, val marks: Double)

// ---------------------------------------------------------------------------------------------
// Subjects
// ---------------------------------------------------------------------------------------------

data class Subject(val id: String, val name: String, val short: String, val isGa: Boolean = false)

object Subjects {
    val all = listOf(
        Subject("EM", "Engineering Mathematics", "EM"),
        Subject("DL", "Digital Logic", "DL"),
        Subject("COA", "Computer Organization & Architecture", "COA"),
        Subject("C", "C Programming", "C"),
        Subject("DSA", "Data Structures", "DSA"),
        Subject("ALGO", "Algorithms", "Algo"),
        Subject("TOC", "Theory of Computation", "TOC"),
        Subject("CD", "Compiler Design", "CD"),
        Subject("OS", "Operating Systems", "OS"),
        Subject("DBMS", "Databases", "DBMS"),
        Subject("CN", "Computer Networks", "CN"),
        Subject("GA", "General Aptitude", "GA", isGa = true),
        Subject("MIXED", "Mixed / Cross-subject", "Mixed")
    )
    private val byId = all.associateBy { it.id }
    fun get(id: String): Subject = byId[id] ?: Subject(id, id, id)

    /** Maps the labels used in the 114-day plan document to subject IDs. */
    fun fromPlanLabel(label: String): String = when (label.trim()) {
        "Engineering Mathematics" -> "EM"
        "Theory of Computation" -> "TOC"
        "Computer Networks" -> "CN"
        "Compiler Design" -> "CD"
        "C Programming" -> "C"
        "Algorithms" -> "ALGO"
        "General Aptitude", "GA" -> "GA"
        "DL" -> "DL"; "DSA" -> "DSA"; "DBMS" -> "DBMS"; "OS" -> "OS"; "COA" -> "COA"
        "DSA + Algorithms", "DBMS + OS", "DL + COA", "Mixed CS", "All subjects" -> "MIXED"
        else -> "MIXED"
    }
}

object Topics {
    /** Deterministic topic id: SUBJECT/slug-of-title. */
    fun id(subjectId: String, title: String): String {
        val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(64).trimEnd('-')
        return "$subjectId/$slug"
    }
}

// ---------------------------------------------------------------------------------------------
// 114-day plan model (source of truth: the supplied Word document)
// ---------------------------------------------------------------------------------------------

data class DueRevision(val fromDay: Int, val subjectLabel: String, val topic: String)

data class PlanDay(
    val day: Int,
    val date: String,          // ISO yyyy-MM-dd
    val weekday: String,
    val fullMock: Boolean,
    val learnSubjectLabel: String,
    val learnTopic: String,
    val revisionSubjectLabel: String,
    val revisionTopic: String,
    val gaTopic: String,
    val gaQuestions: Int,
    val due: List<DueRevision>,
    val dueNote: String?,
    val setsText: String,
    val setP: Int?,
    val setR: Int?,
    val setRMax: Int?,
    val setE: Int?,
    val mockQuestions: Int?,
    val mockMarks: Int?,
    val mockMinutes: Int?,
    val workout: String,
    val lifeCategory: String,
    val lifeTask: String,
    val weeklyReview: Boolean,
    val weeklyReviewText: String?,
    val mockPhase: String?,
    val endOfDay: String?
) {
    val learnSubjectId get() = Subjects.fromPlanLabel(learnSubjectLabel)
    val revisionSubjectId get() = Subjects.fromPlanLabel(revisionSubjectLabel)
    val learnTopicId get() = Topics.id(learnSubjectId, learnTopic)
    val revisionTopicId get() = Topics.id(revisionSubjectId, revisionTopic)
    val isRestDay get() = workout.startsWith("Rest")
}

class Plan(val days: List<PlanDay>, val mockDays: List<Int>, val startDate: String, val endDate: String) {
    private val byDay = days.associateBy { it.day }
    private val byDate = days.associateBy { it.date }
    fun day(n: Int): PlanDay? = byDay[n]
    fun forDate(iso: String): PlanDay? = byDate[iso]
    val size get() = days.size

    companion object {
        /** Parses assets/plan_114.json produced by tools/parse_plan.py from the Word document. */
        fun fromJson(text: String): Plan {
            val root = Json.parseObject(text)
            val days = (root.list("days") ?: throw IllegalArgumentException("plan has no days")).map { d ->
                @Suppress("UNCHECKED_CAST") val m = d as Map<String, Any?>
                PlanDay(
                    day = m.int("day")!!, date = m.str("date")!!, weekday = m.str("weekday")!!,
                    fullMock = m.bool("fullMock") ?: false,
                    learnSubjectLabel = m.str("learnSubject")!!, learnTopic = m.str("learnTopic")!!,
                    revisionSubjectLabel = m.str("revisionSubject")!!, revisionTopic = m.str("revisionTopic")!!,
                    gaTopic = m.str("gaTopic")!!, gaQuestions = m.int("gaQuestions") ?: 5,
                    due = (m.list("due") ?: emptyList()).map { x ->
                        @Suppress("UNCHECKED_CAST") val dm = x as Map<String, Any?>
                        DueRevision(dm.int("fromDay")!!, dm.str("subject")!!, dm.str("topic")!!)
                    },
                    dueNote = m.str("dueNote"), setsText = m.str("setsText")!!,
                    setP = m.int("setP"), setR = m.int("setR"), setRMax = m.int("setRMax"), setE = m.int("setE"),
                    mockQuestions = m.int("mockQuestions"), mockMarks = m.int("mockMarks"), mockMinutes = m.int("mockMinutes"),
                    workout = m.str("workout")!!, lifeCategory = m.str("lifeCategory")!!, lifeTask = m.str("lifeTask")!!,
                    weeklyReview = m.bool("weeklyReview") ?: false, weeklyReviewText = m.str("weeklyReviewText"),
                    mockPhase = m.str("mockPhase"), endOfDay = m.str("endOfDay")
                )
            }
            val mocks = (root.list("mockDays") ?: emptyList()).map { (it as Number).toInt() }
            return Plan(days, mocks, root.str("start")!!, root.str("end")!!)
        }
    }
}
