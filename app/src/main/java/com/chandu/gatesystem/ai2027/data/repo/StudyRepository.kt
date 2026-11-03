package com.chandu.gatesystem.ai2027.data.repo

import androidx.room.withTransaction
import com.chandu.gatesystem.ai2027.data.*
import com.chandu.gatesystem.ai2027.data.db.*
import com.chandu.gatesystem.ai2027.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate

class NoQuestionsException(message: String) : Exception(message)

object SetKinds { const val PREP = "PREP"; const val REVISION = "REVISION"; const val GA = "GA"; const val MONSTER = "MONSTER" }
object ExamKinds { const val EXAM = "EXAM"; const val MOCK = "MOCK"; const val SUBJECT_MOCK = "SUBMOCK" }

/** A graded question with its seven-part explanation (built from stored data, so it works offline). */
data class Graded(val question: Question, val response: Response?, val result: AnswerResult, val explanation: Explanation, val timeSec: Int)
data class BatchReview(val batchIndex: Int, val graded: List<Graded>, val correct: Int, val marks: Double, val setComplete: Boolean)
/** What the student sees while answering: question views only (no key, solution or explanations). */
data class PracticeView(val session: PracticeSessionEntity, val state: PracticeState, val current: List<QuestionView>)
data class PracticeReport(val session: PracticeSessionEntity, val report: SetReport, val explanations: Map<String, Explanation>)
data class ExamView(val session: ExamSessionEntity, val state: ExamState, val views: List<QuestionView>)
data class ExamReview(
    val session: ExamSessionEntity, val report: SetReport, val state: ExamState, val explanations: Map<String, Explanation>,
    val nav: Map<NavStatus, Int>, val time: MockAnalytics.TimeAnalysis, val weak: List<GroupStat>, val flaggedExcluded: Int
)

/** All study flows: lessons, practice/revision/GA/Error-Monster sets, exam sets and mocks. Persists every step. */
class StudyRepository(
    private val db: AppDatabase, private val content: ContentRepository, private val planRepo: PlanRepository, private val progress: ProgressEngine,
    private val provider: QuestionProvider, private val settings: SettingsStore, private val clock: Clock
) {
    private val sessions = db.sessions(); private val mistakes = db.mistakes(); private val player = db.player(); private val contentDao = db.content()

    // ------------------------------------------------------------------ lessons
    suspend fun openLesson(topicId: String) = content.markLessonOpened(topicId)

    /** Idempotent. Records the actual completion date and schedules the 1-4-7 reviews from that date. */
    suspend fun completeLesson(topicId: String) = db.withTransaction {
        val prog = contentDao.progressOnce(topicId)
        if (prog?.lessonCompleted == true) return@withTransaction
        val today = clock.today(); val now = clock.millis()
        val topic = contentDao.topic(topicId)
        contentDao.upsertProgress(TopicProgressEntity(topicId, prog?.lessonOpenedAt ?: now, true, today.toEpochDay(),
            prog?.status ?: TopicStatus.LEARNING.name, prog?.masteryScore ?: 5, prog?.statusReason ?: "Lesson completed", now))
        val learnDay = topic?.firstDay ?: planRepo.focusDay().day
        val entries = RevisionScheduler.scheduleFor("topic:$topicId", today, topic?.title ?: topicId, topic?.subjectId ?: topicId.substringBefore('/'), topicId)
        mistakes.insertRevisions(entries.map { it.toEntity(learnDay) })
        progress.award(XpEngine.lesson(settings.xpRules(), topicId), today.toEpochDay())
        progress.recomputeTopic(topicId)
        progress.recomputeDay(learnDay)
        progress.recomputePlayer()
    }

    // ------------------------------------------------------------------ practice sets (PREP / REVISION / GA / MONSTER)
    suspend fun startOrResumePractice(kind: String, day: Int, count: Int, subjectId: String, topicId: String, title: String,
                                      wantAi: Boolean, forceNew: Boolean = false): String {
        if (!forceNew) sessions.practiceForDay(day, kind)?.let { return it.id }
        val seed = clock.millis()
        val provided = provider.provide(subjectId, topicId, title, count, wantAi, seed, content.excludeSet(), role = BankRole.PRACTICE)
        if (provided.questions.isEmpty()) throw NoQuestionsException("No questions are available for \"$title\". ${provided.note}")
        return createPractice(kind, day, subjectId, topicId, title, provided.questions.map { it.id }, emptyMap(), provided.note)
    }

    private suspend fun createPractice(kind: String, day: Int, subjectId: String, topicId: String, title: String, ids: List<String>,
                                       map: Map<String, String>, note: String): String {
        val id = "ps-$kind-d$day-${clock.millis()}"
        val state = PracticeFlow.start(ids.distinct())
        sessions.upsertPractice(PracticeSessionEntity(id, kind, day, clock.today().toEpochDay(), subjectId, topicId, title, ResponseCodec.encodeList(state.questionIds),
            state.batchSize, 0, ResponseCodec.encodeDraft(state), ResponseCodec.encodeStrings(map), "IN_PROGRESS", 0, note, clock.millis(), null))
        return id
    }

    /**
     * Revision Set: driven by the Error Monster plus the 1-4-7 schedule. Every mistake is retested with a NEW question on the
     * same concept; due topic reviews get fresh questions from unseen stock.
     */
    suspend fun startRevision(day: Int, count: Int, filter: RevisionFilter = RevisionFilter(), wantAi: Boolean = false, monsterIds: List<String>? = null): String =
        withContext(Dispatchers.Default) {
            val today = clock.today(); val seed = clock.millis(); val rnd = java.util.Random(seed)
            val allM = mistakes.allOnce().map { it.toDomain() }; val mById = allM.associateBy { it.id }
            val items: List<QueueItem> = if (monsterIds != null) monsterIds.mapNotNull { mById[it] }.map { m ->
                QueueItem(QueueKind.MISTAKE, m.id, m.concept, m.subjectId, m.topicId, m.nextRevision ?: today,
                    maxOf(0L, java.time.temporal.ChronoUnit.DAYS.between(m.nextRevision ?: today, today)), 0)
            } else {
                val entries = mistakes.pendingRevisions().map { it.toDomain() }
                filter.apply(RevisionScheduler.buildQueue(today, entries, allM, 500).all, today, mById)
            }
            val fallback = if (monsterIds != null || filter.mistakesOnly) emptyList() else
                (listOfNotNull(planRepo.topicRef(planRepo.focusDay(today).revisionTopicId)) + planRepo.earlierLearnTopics(day).shuffled(rnd).take(8))
                    .filter { (filter.subjectId == null || it.subjectId == filter.subjectId) && (filter.topicId == null || it.id == filter.topicId) }
                    .ifEmpty { listOfNotNull(filter.topicId?.let { planRepo.topicRef(it) }) }
            val n = if (monsterIds != null) maxOf(1, items.size) else count
            val slots = RevisionComposer.slots(items, fallback, n)
            if (slots.isEmpty()) throw NoQuestionsException("Nothing is due or matches those filters. Clear the filters or learn a topic first.")

            val stored = content.excludeSet(); val used = HashSet<String>(); val qs = ArrayList<Question>(); val map = LinkedHashMap<String, String>()
            val notes = ArrayList<String>()
            slots.filter { it.mistakeId != null }.forEachIndexed { i, s ->
                val m = mById.getValue(s.mistakeId!!)
                var q = content.unseen(m.subjectId, null, m.concept, null, null, used, i + 3, 1).firstOrNull()
                if (q == null) q = ConceptVariants.forMistake(m, seed + i * 131L, stored + used, planRepo.topicRefs)?.let { content.ensure(it) }
                if (q == null) {
                    val title = planRepo.topicRef(m.topicId)?.title ?: m.concept
                    q = provider.provide(m.subjectId, m.topicId, title, 1, wantAi, seed + i, stored, listOf(m.concept), avoidIds = used, aiMode = "revision").questions.firstOrNull()
                }
                if (q != null && used.add(q.id)) { qs += q; map[q.id] = "m:${m.id}" }
            }
            slots.filter { it.mistakeId == null }.groupBy { it.topicId }.forEach { (tid, list) ->
                val title = planRepo.topicRef(tid)?.title ?: tid.substringAfter('/')
                val got = provider.provide(list[0].subjectId, tid, title, list.size, wantAi, seed + tid.hashCode(), stored, avoidIds = used, aiMode = "revision")
                notes += got.note
                got.questions.forEachIndexed { i, q -> if (used.add(q.id)) { qs += q; list.getOrNull(i)?.itemKey?.let { map[q.id] = "r:$it" } } }
            }
            if (qs.isEmpty()) throw NoQuestionsException("No fresh questions could be prepared for this revision.")
            val order = qs.shuffled(rnd)
            val kind = if (monsterIds != null) SetKinds.MONSTER else SetKinds.REVISION
            val nm = map.values.count { it.startsWith("m:") }
            val title = if (monsterIds != null) "Error Monster hunt ($nm)" else "Revision Set — Day $day"
            createPractice(kind, day, if (nm > 0 && map.isNotEmpty()) "MIXED" else slots.first().subjectId, slots.first().topicId, title, order.map { it.id }, map,
                "New questions on the same concepts: $nm from Error Monster, ${order.size - nm} due reviews/fresh topic questions")
        }

    suspend fun practiceView(sessionId: String): PracticeView? {
        val e = sessions.practice(sessionId) ?: return null
        val st = e.toState()
        val qs = content.questions(st.currentBatchIds)
        return PracticeView(e, st, st.currentBatchIds.mapNotNull { qs[it]?.toStudentView() })
    }

    suspend fun saveDraft(sessionId: String, state: PracticeState) {
        val e = sessions.practice(sessionId) ?: return
        if (e.status == "COMPLETED") return
        sessions.upsertPractice(e.copy(draftJson = ResponseCodec.encodeDraft(state)))
    }

    /** Grades the open batch of five, records attempts, mistakes, XP, mastery, quests and player progress. Idempotent. */
    suspend fun submitBatch(sessionId: String, current: PracticeState): BatchReview = db.withTransaction {
        val e = sessions.practice(sessionId) ?: error("Unknown session")
        val stored = e.toState()
        if (stored.isComplete) return@withTransaction reviewFor(sessionId, stored.batchCount - 1)!!
        // trust the in-memory answers for the open batch, but never accept state for a different batch
        val state = stored.copy(answers = stored.answers + current.answers.filterKeys { it in stored.currentBatchIds }, times = stored.times + current.times)
        val qs = content.questions(state.questionIds)
        val out = PracticeFlow.submitBatch(state, qs, settings.markingPolicy())
        val map = ResponseCodec.decodeStrings(e.mistakeMapJson)
        record(e.kind, sessionId, e.day, out.outcomes, map)
        val rules = settings.xpRules(); val epoch = clock.today().toEpochDay()
        progress.award(XpEngine.batch(rules, sessionId, out.batchIndex, out.correct), epoch)
        val ns = out.state
        var status = "IN_PROGRESS"; var completedAt: Long? = null
        if (ns.isComplete) {
            status = "COMPLETED"; completedAt = clock.millis()
            val correctTotal = ns.results.values.count { it.verdict == Verdict.CORRECT }
            when (e.kind) {
                SetKinds.PREP -> progress.award(XpEngine.prepSet(rules, sessionId), epoch)
                SetKinds.REVISION, SetKinds.MONSTER -> {
                    progress.award(XpEngine.revision(rules, sessionId, correctTotal), epoch)
                    map.values.filter { it.startsWith("r:") }.distinct().forEach { mistakes.markRevisionDone(it.removePrefix("r:"), epoch) }
                }
            }
        }
        sessions.upsertPractice(e.copy(submittedBatches = ns.submittedBatches, draftJson = ResponseCodec.encodeDraft(ns), status = status, completedAt = completedAt))
        progress.recomputeDay(e.day); progress.recomputePlayer()
        reviewFor(sessionId, out.batchIndex)!!
    }

    suspend fun reviewFor(sessionId: String, batchIndex: Int): BatchReview? {
        val e = sessions.practice(sessionId) ?: return null
        val st = e.toState()
        val ids = st.questionIds.drop(batchIndex * st.batchSize).take(st.batchSize)
        val qs = content.questions(ids)
        val graded = ids.mapNotNull { id ->
            val q = qs[id] ?: return@mapNotNull null
            val r = st.answers[id]
            Graded(q, r, st.results[id] ?: AnswerResult(Verdict.UNANSWERED, 0.0), ExplanationBuilder.local(q, r), st.times[id] ?: 0)
        }
        return BatchReview(batchIndex, graded, graded.count { it.result.verdict == Verdict.CORRECT }, graded.sumOf { it.result.marks }, st.isComplete && batchIndex == st.batchCount - 1)
    }

    suspend fun practiceReport(sessionId: String): PracticeReport? {
        val e = sessions.practice(sessionId) ?: return null
        val st = e.toState(); if (!st.isComplete) return null
        val qs = content.questions(st.questionIds)
        val report = PracticeFlow.finalReport(st, qs, settings.markingPolicy())
        return PracticeReport(e, report, report.outcomes.associate { it.question.id to ExplanationBuilder.local(it.question, it.response) })
    }

    // ------------------------------------------------------------------ grading + Error Monster bookkeeping
    private suspend fun record(kind: String, sessionId: String, day: Int, outcomes: List<QuestionOutcome>, mistakeMap: Map<String, String>) {
        val today = clock.today(); val epoch = today.toEpochDay(); val now = clock.millis(); val rules = settings.xpRules()
        val touched = LinkedHashSet<String>()
        for (o in outcomes) {
            val q = o.question; val r = o.response; val verdict = o.result.verdict
            val fresh = sessions.insertAttempt(AttemptEntity(
                questionId = q.id, sessionId = sessionId, sessionKind = kind, topicId = q.topicId, subjectId = q.subjectId, day = day, epochDay = epoch,
                selectedJson = Json.stringify(r?.selected?.sorted() ?: emptyList<Int>()), natValue = r?.nat, verdict = verdict.name, marks = o.result.marks,
                timeSec = o.timeSpentSec, difficulty = q.difficulty.name, createdAt = now)) != -1L
            if (!fresh) continue
            touched += q.topicId
            val ref = mistakeMap[q.id]?.takeIf { it.startsWith("m:") }?.removePrefix("m:")
            val reviewKind = kind == SetKinds.REVISION || kind == SetKinds.MONSTER
            when (verdict) {
                Verdict.WRONG -> {
                    val m = if (reviewKind && ref != null) mistakes.byId(ref)?.toDomain() else null
                    if (m != null) {
                        mistakes.upsert(ErrorMonsterEngine.onRevision(m, q.id, false, today).toEntity())
                        mistakes.insertEvent(MistakeEventEntity(mistakeId = m.id, epochDay = epoch, questionId = q.id, kind = "REVISION_FAIL", userAnswer = ErrorMonsterEngine.userAnswerText(q, r)))
                    } else logWrong(q, r, o.timeSpentSec, epoch)
                }
                Verdict.CORRECT -> if (reviewKind && ref != null) {
                    val m = mistakes.byId(ref)?.toDomain()
                    if (m != null) {
                        val independent = q.id !in m.succeededQuestionIds && q.id !in m.questionIds
                        val upd = ErrorMonsterEngine.onRevision(m, q.id, true, today)
                        mistakes.upsert(upd.toEntity())
                        mistakes.insertEvent(MistakeEventEntity(mistakeId = m.id, epochDay = epoch, questionId = q.id, kind = if (independent) "REVISION_OK" else "REPEAT_NO_EVIDENCE", userAnswer = ""))
                        if (m.status != MistakeStatus.OVERCOME && upd.status == MistakeStatus.OVERCOME) {
                            progress.award(XpEngine.overcome(rules, m.id, upd.recurrenceAfterOvercome), epoch)
                            player.insertEvent(ProgressEventEntity(kind = "MASTERY", title = "Error Monster overcome: ${m.concept}", reason = "3 independent successes on new questions across separate days", createdAt = now, seen = false))
                        }
                    }
                }
                Verdict.UNANSWERED -> Unit
            }
        }
        touched.forEach { progress.recomputeTopic(it) }
    }

    private suspend fun logWrong(q: Question, r: Response?, secs: Int, epoch: Long) {
        val today = LocalDate.ofEpochDay(epoch)
        val key = ErrorMonsterEngine.dedupeKey(q)
        val existing = mistakes.byKey(key)?.toDomain()
        val expl = ExplanationBuilder.local(q, r)
        val text = (expl.whyWrong + " " + q.solution).trim().take(700)
        val rec = ErrorMonsterEngine.onWrong(existing, q, r, MistakeClassifier.suggest(q, r, secs), text, today) { "m-" + QuestionValidator.sha256(key).take(12) }
        mistakes.upsert(rec.toEntity())
        mistakes.insertEvent(MistakeEventEntity(mistakeId = rec.id, epochDay = epoch, questionId = q.id, kind = "WRONG", userAnswer = ErrorMonsterEngine.userAnswerText(q, r)))
    }

    suspend fun setMistakeCategory(id: String, c: MistakeCategory) { mistakes.byId(id)?.let { mistakes.upsert(it.copy(category = c.name)) } }
    suspend fun setMistakeNotes(id: String, notes: String) = mistakes.setNotes(id, notes.take(1000))

    // ------------------------------------------------------------------ exam sets and mocks
    private fun examIdFor(kind: String, day: Int) = "ex-$kind-d$day-${clock.millis()}"

    /** Builds questions for (subject, marks, count) slots in parallel (max 5 AI requests at once). AI GATE-level first. */
    private suspend fun gather(slots: List<Triple<String, Int, Int>>, mode: String, seed: Long, role: BankRole): Pair<List<Question>, List<String>> = coroutineScope {
        val gate = Semaphore(5); val topics = planRepo.topicRefs
        val exclude = content.excludeSet()
        val jobs = slots.mapIndexed { i, (subj, marks, n) ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val titles = topics.filter { it.subjectId == subj }.map { it.title }
                    provider.provideGate(subj, marks, n, mode, seed + i * 977L, titles, exclude, emptyList(), role)
                }
            }
        }
        val res = jobs.awaitAll()
        val used = HashSet<String>(); val out = ArrayList<Question>()
        res.forEach { p -> p.questions.forEach { q -> if (used.add(q.id)) out += q } }
        out to res.map { it.note }.distinct()
    }

    private fun mockNote(notes: List<String>) = notes.firstOrNull { it.contains("not configured") || it.contains("stored bank") } ?: notes.firstOrNull().orEmpty()

    /** Exam set: ~40% today's subject, ~40% earlier subjects, ~20% GA, ~40% two-mark questions, all GATE level. */
    suspend fun startExam(day: Int, count: Int = 30): String = withContext(Dispatchers.Default) {
        sessions.examForDay(day, ExamKinds.EXAM)?.takeIf { it.status == "IN_PROGRESS" }?.let { return@withContext it.id }
        val seed = clock.millis(); val pd = planRepo.plan.day(day) ?: planRepo.focusDay()
        val focusSubj = QuestBuilder.mockSubject(pd)
        val earlier = planRepo.earlierLearnTopics(day).map { it.subjectId }.filter { it != "GA" && it != "MIXED" && it != focusSubj }.distinct().shuffled(java.util.Random(seed)).take(3)
        val nFocus = Math.round(count * 0.4).toInt(); val nGa = Math.round(count * 0.2).toInt()
        val per = LinkedHashMap<String, Int>()
        per[focusSubj] = nFocus; per["GA"] = nGa
        val others = earlier.ifEmpty { listOf("DSA", "OS", "DBMS").filter { it != focusSubj } }
        var rem = count - nFocus - nGa; var i = 0
        while (rem > 0) { val k = others[i++ % others.size]; per[k] = (per[k] ?: 0) + 1; rem-- }
        val slots = ArrayList<Triple<String, Int, Int>>()
        for ((subj, n) in per) { val two = Math.round(n * 0.4).toInt(); if (n - two > 0) slots += Triple(subj, 1, n - two); if (two > 0) slots += Triple(subj, 2, two) }
        val (qs, notes) = gather(slots, "exam", seed, BankRole.EXAM)
        if (qs.isEmpty()) throw NoQuestionsException("Could not assemble an exam set. ${mockNote(notes)}")
        createExam(ExamKinds.EXAM, "MIXED", day, "Exam Set — Day $day (${qs.size} Q) · ${mockNote(notes)}".take(120), qs.shuffled(java.util.Random(seed + 3)))
    }

    /** Full 65-question / 100-mark / 180-minute mock in the official pattern, GATE-level AI questions. */
    suspend fun startFullMock(day: Int): String = withContext(Dispatchers.Default) {
        sessions.examForDay(day, ExamKinds.MOCK)?.takeIf { it.status == "IN_PROGRESS" }?.let { return@withContext it.id }
        val seed = clock.millis()
        val groups = MockBlueprint.fullGateCs.groupingBy { it.subjectId to it.marks }.eachCount()
        val (out, notes) = gather(groups.map { (k, n) -> Triple(k.first, k.second, n) }, "mock", seed, BankRole.MOCK)
        val ordered = MockBlueprint.fullGateCs.map { it.subjectId to it.marks }.distinct().flatMap { s -> out.filter { it.subjectId == s.first && it.marks == s.second } }
        if (ordered.size < 50) throw NoQuestionsException("Could only build ${ordered.size} of 65 questions. ${mockNote(notes)}")
        createExam(ExamKinds.MOCK, "FULL", day, "Full Mock — Day $day (${ordered.size} Q / ${ordered.sumOf { it.marks }} marks) · ${mockNote(notes)}".take(130), ordered, MockBlueprint.FULL_DURATION_SEC)
    }

    /** One subject only: 25 GATE-level questions (15 x 1 mark, 10 x 2 marks). */
    suspend fun startSubjectMock(subjectId: String, day: Int): String = withContext(Dispatchers.Default) {
        val seed = clock.millis()
        val (out, notes) = gather(listOf(Triple(subjectId, 1, 15), Triple(subjectId, 2, 10)), "mock", seed, BankRole.MOCK)
        if (out.isEmpty()) throw NoQuestionsException("No questions for ${Subjects.get(subjectId).name}. ${mockNote(notes)}")
        createExam(ExamKinds.SUBJECT_MOCK, "SUBJECT:$subjectId", day, "${Subjects.get(subjectId).name} Mock (${out.size} Q) · ${mockNote(notes)}".take(130), out.shuffled(java.util.Random(seed)))
    }

    private suspend fun createExam(kind: String, mockType: String, day: Int, title: String, qs: List<Question>, durationSec: Int = ExamPaper.durationSec(qs)): String {
        val id = examIdFor(kind, day); val now = clock.millis()
        sessions.upsertExam(ExamSessionEntity(id, kind, mockType, day, clock.today().toEpochDay(), title, ResponseCodec.encodeList(qs.map { it.id }), durationSec, durationSec,
            "{}", "[]", "[]", "{}", "IN_PROGRESS", now, null, null, null, null, 0))
        return id
    }

    /** The exam clock keeps running in real time (like the real exam), so the remaining time is derived from the start time. */
    suspend fun examView(id: String): ExamView? {
        val e = sessions.exam(id) ?: return null
        var st = e.toState()
        if (!st.submitted) st = st.copy(remainingSec = maxOf(0, e.durationSec - ((clock.millis() - e.startedAt) / 1000).toInt()))
        val qs = content.questions(st.questionIds)
        return ExamView(e, st, st.questionIds.mapNotNull { qs[it]?.toStudentView() })
    }

    suspend fun saveExam(id: String, st: ExamState) {
        val e = sessions.exam(id) ?: return
        if (e.status != "IN_PROGRESS") return
        sessions.upsertExam(e.copy(remainingSec = st.remainingSec, responsesJson = ResponseCodec.encodeMap(st.responses), markedJson = ResponseCodec.encodeList(st.marked),
            visitedJson = ResponseCodec.encodeList(st.visited), timesJson = ResponseCodec.encodeInts(st.timeSpent)))
    }

    /** Freezes the attempt, scores with the stored keys and records everything. The AI never touches the score. */
    suspend fun submitExam(id: String, current: ExamState): ExamReview? {
        db.withTransaction {
            val e = sessions.exam(id) ?: return@withTransaction
            if (e.status == "SUBMITTED") return@withTransaction
            val qs = content.questions(current.questionIds)
            val (ns, report) = ExamFlow.submit(current, qs, settings.markingPolicy())
            val attemptKind = if (e.kind == ExamKinds.EXAM) "EXAM" else "MOCK"
            record(attemptKind, id, e.day, report.outcomes, emptyMap())
            val rules = settings.xpRules(); val epoch = clock.today().toEpochDay()
            val xp = when (e.kind) {
                ExamKinds.MOCK -> XpEngine.fullMock(rules, id, report.netMarks, report.maxMarks)
                ExamKinds.SUBJECT_MOCK -> XpEngine.subjectMock(rules, id, report.netMarks, report.maxMarks)
                else -> XpEngine.examByMarks(rules, id, report.netMarks, report.maxMarks)
            }
            progress.award(xp, epoch)
            val summary = "net ${report.netMarks}/${report.maxMarks}; ${report.correct} correct, ${report.incorrect} wrong, ${report.unanswered} unanswered"
            sessions.upsertExam(e.copy(remainingSec = ns.remainingSec, responsesJson = ResponseCodec.encodeMap(ns.responses), markedJson = ResponseCodec.encodeList(ns.marked),
                visitedJson = ResponseCodec.encodeList(ns.visited), timesJson = ResponseCodec.encodeInts(ns.timeSpent), status = "SUBMITTED", submittedAt = clock.millis(),
                netMarks = report.netMarks, maxMarks = report.maxMarks, reportJson = summary, xpAwarded = xp.amount))
            if (e.kind == ExamKinds.MOCK) {
                player.insertMock(MockResultEntity(id, e.day, e.mockType, report.percent, report.netMarks, report.maxMarks, report.accuracy, report.attempted, report.total,
                    e.durationSec - ns.remainingSec, e.durationSec, epoch, Json.stringify(report.byTopic.map { linkedMapOf("t" to it.key, "a" to it.attempted, "c" to it.correct, "n" to it.total) })))
            }
            progress.recomputeDay(e.day); progress.recomputePlayer()
        }
        return examReview(id)
    }

    suspend fun examReview(id: String): ExamReview? {
        val e = sessions.exam(id) ?: return null
        if (e.status != "SUBMITTED") return null
        val st = e.toState()
        val all = content.questions(st.questionIds)
        val flagged = content.flaggedAmong(st.questionIds)
        val counted = st.questionIds.mapNotNull { all[it] }.filter { it.id !in flagged }
        val report = SetScorer.evaluate(counted, st.responses, st.timeSpent, st.marked, settings.markingPolicy())
        val expl = report.outcomes.filter { it.result.verdict != Verdict.CORRECT }.associate { it.question.id to ExplanationBuilder.local(it.question, it.response) }
        return ExamReview(e, report, st, expl, ExamFlow.summary(st, all), MockAnalytics.timeManagement(report, e.durationSec), MockAnalytics.weakTopics(report), flagged.size)
    }

    /** Marks a question as ambiguous: it is excluded from reports instead of penalising the learner. */
    suspend fun reportAmbiguous(questionId: String) = content.setFlagged(questionId, true)

    // ------------------------------------------------------------------ manual quests
    suspend fun setManualQuest(questId: String, done: Boolean) = db.withTransaction {
        val q = player.quest(questId) ?: return@withTransaction
        if (QuestKind.valueOf(q.kind) == QuestKind.WORKOUT) return@withTransaction // workout only through setWorkout
        player.updateQuest(q.copy(manualDone = done, manualDoneAt = if (done) clock.millis() else null))
        progress.recomputeDay(q.day); progress.recomputePlayer()
    }

    /** Workout is never completed automatically: only an explicit user choice. status == null clears it. */
    suspend fun setWorkout(day: Int, status: WorkoutStatus?) = db.withTransaction {
        val pd = planRepo.plan.day(day) ?: return@withTransaction
        val epoch = planRepo.epochOf(day)
        if (status == null) player.clearWorkout(epoch) else {
            player.upsertWorkout(WorkoutEntity(epoch, day, pd.workout, status.name, "", clock.millis()))
            val rules = settings.xpRules()
            if (status != WorkoutStatus.SKIPPED) progress.award(XpEngine.workout(rules, epoch, false), epoch)
            if (status == WorkoutStatus.DONE) progress.award(XpEngine.workout(rules, epoch, true), epoch)
        }
        progress.recomputeDay(day); progress.recomputePlayer()
    }

    suspend fun refreshAll(day: Int) = db.withTransaction { progress.recomputeDay(day); progress.recomputePlayer() }
}
