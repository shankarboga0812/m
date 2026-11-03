package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.gen.TemplateCatalog
import com.chandu.gatesystem.ai2027.domain.gen.TemplateDef

data class TopicRef(val id: String, val subjectId: String, val title: String)

/** Draws offline, computed-answer questions and attributes them to plan topics. */
object PaperBuilder {
    private const val STEP = 104729L

    /** Plan topic whose title matches the template's keywords; otherwise a stable "bank" topic id. */
    fun topicFor(def: TemplateDef, topics: List<TopicRef>, salt: Int = 0): String {
        val hits = topics.filter { it.subjectId == def.subjectId && def.keywords.any { k -> k in it.title.lowercase() } }
        return if (hits.isEmpty()) Topics.id(def.subjectId, def.subtopic) else hits[Math.floorMod(salt, hits.size)].id
    }

    private fun remark(q: Question, marks: Int) =
        q.copy(marks = marks, negativeMarks = if (q.type == QType.MCQ) Scorer.defaultNegative(QType.MCQ, marks) else 0.0)

    /**
     * Draws [count] distinct valid questions. When [marks] is given, questions of that weight are preferred; if the
     * templates cannot supply enough, remaining ones are re-weighted (key and solution unchanged).
     */
    fun draw(subjectId: String, marks: Int?, count: Int, seed: Long, exclude: Set<String>, topics: List<TopicRef>,
             only: List<TemplateDef>? = null): List<Question> {
        val pool = (only ?: TemplateCatalog.forSubject(subjectId)).ifEmpty { return emptyList() }
        val order = pool.shuffled(java.util.Random(seed))
        val dup = DuplicateDetector()
        val out = ArrayList<Question>(); val spare = ArrayList<Question>()
        var s = seed; var i = 0; var guard = 0
        while (out.size < count && guard++ < count * 60 + 60) {
            val def = order[i++ % order.size]
            val q = try { def.generate(s, topicFor(def, topics, (s / STEP).toInt() + i)) } catch (e: IllegalStateException) { s += STEP; continue }
            s += STEP
            if (q.id in exclude || QuestionValidator.fingerprint(q) in exclude || dup.isDuplicate(q) || !QuestionValidator.validate(q).ok) continue
            if (marks != null && q.marks != marks) { if (spare.size < count * 4) spare += q; continue }
            dup.register(q); out += q
        }
        if (marks != null) for (q in spare) {
            if (out.size >= count) break
            if (!dup.isDuplicate(q)) { dup.register(q); out += remark(q, marks) }
        }
        return out
    }
}

object MockPaper {
    /** 65 questions / 100 marks following [MockBlueprint]. Throws if the generators cannot fill a slot (never silently short). */
    fun build(seed: Long, exclude: Set<String>, topics: List<TopicRef>): List<Question> {
        val groups = MockBlueprint.fullGateCs.groupingBy { it.subjectId to it.marks }.eachCount()
        val out = ArrayList<Question>(); val used = HashSet<String>(exclude); var k = 0L
        for (slot in MockBlueprint.fullGateCs.map { it.subjectId to it.marks }.distinct()) {
            val need = groups.getValue(slot)
            val got = PaperBuilder.draw(slot.first, slot.second, need, seed + 31 * (++k), used, topics)
            check(got.size == need) { "Could not build ${slot.first} ${slot.second}-mark questions ($need needed, ${got.size} found)" }
            got.forEach { used += it.id; used += QuestionValidator.fingerprint(it) }
            out += got
        }
        return out
    }
}

object ExamPaper {
    /** ~1.8 minutes per mark, the same pace as the real paper (180 min / 100 marks). */
    fun durationSec(qs: List<Question>) = qs.sumOf { it.marks } * 108

    /**
     * Mixed GATE-style set: ~40% today's topic, ~40% earlier topics, ~20% General Aptitude, with about 40% two-mark questions.
     */
    fun build(count: Int, focus: TopicRef?, earlier: List<TopicRef>, allTopics: List<TopicRef>, seed: Long, exclude: Set<String>): List<Question> {
        val used = HashSet<String>(exclude); val out = ArrayList<Question>()
        val nFocus = if (focus == null) 0 else Math.round(count * 0.4).toInt()
        val nGa = Math.round(count * 0.2).toInt()
        val nEarlier = count - nFocus - nGa
        fun take(list: List<Question>) { for (q in list) { used += q.id; used += QuestionValidator.fingerprint(q); out += q } }
        if (focus != null) take(PaperBuilder.draw(focus.subjectId, null, nFocus, seed + 1, used, allTopics, TemplateCatalog.forTopic(focus.subjectId, focus.title)))
        take(PaperBuilder.draw("GA", null, nGa, seed + 2, used, allTopics))
        val subjects = (earlier.map { it.subjectId } + (focus?.subjectId ?: "")).filter { it.isNotBlank() && it != "GA" && it != "MIXED" }.distinct()
            .ifEmpty { Subjects.all.filter { !it.isGa && it.id != "MIXED" }.map { it.id } }
        var rem = nEarlier; var j = 0
        while (rem > 0 && j < subjects.size * 4) {
            val subj = subjects[j % subjects.size]; j++
            val got = PaperBuilder.draw(subj, null, 1, seed + 100 + j, used, allTopics)
            if (got.isNotEmpty()) { take(got); rem-- }
        }
        // top up if some generators fell short
        var topUp = 0
        while (out.size < count && topUp++ < 40) {
            val all = Subjects.all.filter { it.id != "MIXED" }
            val subj = all[java.util.Random(seed + topUp).nextInt(all.size)].id
            take(PaperBuilder.draw(subj, null, 1, seed + 500 + topUp, used, allTopics))
        }
        return out.take(count).shuffled(java.util.Random(seed + 9))
    }
}

/** A planned revision question: either a fresh variant of a known mistake or a topic question. */
/** [itemKey] is the queue key (mistake id or "<item>#<stage>") so completing the set can close the right reviews. */
data class RevisionSlot(val mistakeId: String?, val subjectId: String, val topicId: String, val concept: String?, val label: String, val itemKey: String? = null)

object RevisionComposer {
    /**
     * Every due mistake gets one slot first (each tests the same concept with a NEW question); the remaining slots are
     * shared among the due topic reviews, then topped up from [fallback] topics. Nothing due is deleted when the set is
     * smaller than the queue: it simply stays due.
     */
    fun slots(queue: List<QueueItem>, fallback: List<TopicRef>, count: Int): List<RevisionSlot> {
        val out = ArrayList<RevisionSlot>()
        for (qi in queue.filter { it.kind == QueueKind.MISTAKE }) { if (out.size < count) out += RevisionSlot(qi.key, qi.subjectId, qi.topicId, qi.label, "Mistake: ${qi.label}", qi.key) }
        val topics = queue.filter { it.kind == QueueKind.TOPIC_REVISION }
        if (topics.isNotEmpty() && out.size < count) {
            val remaining = count - out.size
            val per = maxOf(1, remaining / topics.size)
            for (t in topics) repeat(per) { if (out.size < count) out += RevisionSlot(null, t.subjectId, t.topicId, null, "Due review: ${t.label}", t.key) }
        }
        var i = 0
        while (out.size < count && fallback.isNotEmpty()) { val t = fallback[i++ % fallback.size]; out += RevisionSlot(null, t.subjectId, t.id, null, "Revision: ${t.title}") }
        return out
    }
}

data class RevisionFilter(
    val subjectId: String? = null, val topicId: String? = null, val dueToday: Boolean = false, val overdue: Boolean = false,
    val frequentOnly: Boolean = false, val category: MistakeCategory? = null, val stage: Int? = null, val mistakesOnly: Boolean = false
) {
    val isDefault get() = this == RevisionFilter()

    fun apply(items: List<QueueItem>, today: java.time.LocalDate, mistakes: Map<String, MistakeRecord>): List<QueueItem> = items.filter { q ->
        (subjectId == null || q.subjectId == subjectId) && (topicId == null || q.topicId == topicId) &&
            (!dueToday || q.dueDate == today) && (!overdue || q.overdueDays > 0) &&
            (!mistakesOnly || q.kind == QueueKind.MISTAKE) &&
            (!frequentOnly || (q.kind == QueueKind.MISTAKE && (mistakes[q.key]?.occurrences ?: 0) >= ErrorMonsterEngine.FREQUENT_THRESHOLD)) &&
            (category == null || (q.kind == QueueKind.MISTAKE && mistakes[q.key]?.category == category)) &&
            (stage == null || (q.kind == QueueKind.TOPIC_REVISION && q.key.substringAfterLast('#').toIntOrNull() == stage))
    }
}

object ConceptVariants {
    /** A NEW question for the mistake's concept: same template family, different seed values. Null when no template covers it. */
    fun forMistake(m: MistakeRecord, seed: Long, exclude: Set<String>, topics: List<TopicRef>): Question? {
        val norm = QuestionValidator.normalize(m.concept)
        val defs = TemplateCatalog.forSubject(m.subjectId).filter { QuestionValidator.normalize(it.concept) == norm }
        if (defs.isEmpty()) return null
        val got = PaperBuilder.draw(m.subjectId, null, 1, seed, exclude, topics, defs)
        return got.firstOrNull()?.copy(topicId = m.topicId)
    }
}
