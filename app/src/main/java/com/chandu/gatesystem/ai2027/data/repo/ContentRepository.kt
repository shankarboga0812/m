package com.chandu.gatesystem.ai2027.data.repo

import androidx.room.withTransaction
import com.chandu.gatesystem.ai2027.data.*
import com.chandu.gatesystem.ai2027.data.db.*
import com.chandu.gatesystem.ai2027.domain.*
import kotlinx.coroutines.flow.Flow

/** Questions (with de-duplication), topics and lessons. */
class ContentRepository(private val db: AppDatabase, private val clock: Clock) {
    private val dao = db.content()

    val topics: Flow<List<TopicEntity>> get() = dao.topics()
    val progress: Flow<List<TopicProgressEntity>> get() = dao.allProgress()
    val questionCount: Flow<Int> get() = dao.questionCount()
    val countBySource: Flow<List<SourceCount>> get() = dao.countBySource()
    val countBySubject: Flow<List<SourceCount>> get() = dao.countBySubject()
    val flaggedCount: Flow<Int> get() = dao.flaggedCount()
    val flaggedQuestions: Flow<List<QuestionEntity>> get() = dao.flaggedQuestions()
    val authoredLessons: Flow<Int> get() = dao.authoredLessonCount()

    fun progressFor(topicId: String) = dao.progress(topicId)
    fun topicsBySubject(subjectId: String) = dao.topicsBySubject(subjectId)
    suspend fun topic(id: String) = dao.topic(id)

    /** Stores [q] unless the same id or the same content fingerprint already exists; returns the stored version. */
    suspend fun ensure(q: Question, flagged: Boolean = false): Question = db.withTransaction {
        dao.question(q.id)?.let { return@withTransaction load(it) }
        val fp = QuestionValidator.fingerprint(q)
        dao.idByFingerprint(fp)?.let { id -> dao.question(id)?.let { return@withTransaction load(it) } }
        val (e, opts) = q.toRows(clock.millis(), flagged)
        dao.insertQuestion(e); dao.insertOptions(opts)
        q
    }

    suspend fun ensureAll(qs: List<Question>, flagged: Boolean = false): List<Question> = qs.map { ensure(it, flagged) }.distinctBy { it.id }

    private suspend fun load(e: QuestionEntity): Question = e.toDomain(dao.options(listOf(e.id)))

    suspend fun questions(ids: List<String>): Map<String, Question> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<String, Question>()
        for (chunk in ids.distinct().chunked(400)) {
            val ents = dao.questions(chunk)
            val opts = dao.options(chunk).groupBy { it.questionId }
            for (e in ents) out[e.id] = e.toDomain(opts[e.id] ?: emptyList())
        }
        return out
    }

    suspend fun question(id: String): Question? = questions(listOf(id))[id]

    /** Ids and fingerprints of everything stored: fresh questions must avoid all of them. */
    suspend fun excludeSet(): Set<String> = (dao.allIds() + dao.fingerprints()).toHashSet()

    suspend fun recentForTopic(topicId: String, n: Int): List<Question> {
        val ents = dao.recentForTopic(topicId, n)
        val opts = dao.options(ents.map { it.id }).groupBy { it.questionId }
        return ents.map { it.toDomain(opts[it.id] ?: emptyList()) }
    }

    val bankBySubject: Flow<List<SourceCount>> get() = dao.bankBySubject()
    suspend fun bankCount() = dao.bankCount()
    suspend fun unseenCount(subjectId: String, role: BankRole) = dao.unseenCount(subjectId, "%${role.tag}%")
    suspend fun bankRoleCount(subjectId: String, role: BankRole) = dao.bankRoleCount(subjectId, "%${role.tag}%")

    /** Everything the learner has already answered (ids and fingerprints): fresh questions must avoid these. */
    suspend fun seenSet(): Set<String> = (dao.attemptedIds() + dao.attemptedFingerprints()).toHashSet()

    /** Unseen pre-installed (or stored) questions, filtered; order varies with [salt]. */
    suspend fun unseen(subjectId: String, topicId: String? = null, concept: String? = null, marks: Int? = null, role: BankRole? = null,
                       excludeIds: Collection<String> = emptyList(), salt: Int = 1, n: Int): List<Question> {
        if (n <= 0) return emptyList()
        val ents = dao.unseen(subjectId, topicId, concept, marks, role?.let { "%${it.tag}%" }, excludeIds.toList().take(500).ifEmpty { listOf("") }, salt.coerceAtLeast(1), n)
        val opts = dao.options(ents.map { it.id }).groupBy { it.questionId }
        return ents.map { it.toDomain(opts[it.id] ?: emptyList()) }
    }

    suspend fun setFlagged(id: String, f: Boolean) = dao.setFlagged(id, f)
    suspend fun flaggedAmong(ids: List<String>): Set<String> = if (ids.isEmpty()) emptySet() else dao.flaggedAmong(ids).toSet()

    // ---- lessons ----
    suspend fun lesson(topicId: String): Lesson {
        val topic = dao.topic(topicId)
        val title = topic?.title ?: topicId.substringAfter('/').replace('-', ' ')
        val subjectName = Subjects.get(topic?.subjectId ?: topicId.substringBefore('/')).name
        val stored = dao.lessonOnce(topicId)
        if (stored != null) {
            val src = runCatching { LessonSource.valueOf(stored.source) }.getOrDefault(LessonSource.SKELETON)
            // a curated lesson shipped in a newer app version should replace an old skeleton
            if (src != LessonSource.SKELETON || topicId !in LessonBank.curated) LessonCodec.fromJson(stored.bodyJson, topicId, title, src)?.let { return it }
        }
        return LessonBank.lessonFor(topicId, title, subjectName)
    }

    suspend fun saveAiLesson(l: Lesson) {
        dao.upsertLesson(LessonEntity(l.topicId, LessonCodec.toJson(l), LessonSource.AI.name, clock.millis()))
    }

    suspend fun markLessonOpened(topicId: String) {
        val p = dao.progressOnce(topicId)
        val now = clock.millis()
        if (p == null) dao.upsertProgress(TopicProgressEntity(topicId, now, false, null, TopicStatus.LEARNING.name, 5, "Lesson opened; no questions attempted yet", now))
        else if (p.lessonOpenedAt == null) {
            dao.touchOpened(topicId, now)
            if (p.status == TopicStatus.NOT_STARTED.name) dao.upsertProgress(p.copy(lessonOpenedAt = now, status = TopicStatus.LEARNING.name, masteryScore = 5, statusReason = "Lesson opened; no questions attempted yet", updatedAt = now))
        }
    }
}
