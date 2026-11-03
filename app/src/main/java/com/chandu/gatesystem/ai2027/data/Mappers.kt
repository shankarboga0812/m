package com.chandu.gatesystem.ai2027.data

import com.chandu.gatesystem.ai2027.data.db.*
import com.chandu.gatesystem.ai2027.domain.*
import java.time.LocalDate

// ---------- Question <-> rows ----------

fun Question.toRows(now: Long, flagged: Boolean = false): Pair<QuestionEntity, List<AnswerOptionEntity>> {
    val e = QuestionEntity(
        id = id, subjectId = subjectId, topicId = topicId, subtopic = subtopic, statement = statement, type = type.name,
        correctJson = Json.stringify(correctOptions.sorted()), natLow = nat?.low, natHigh = nat?.high, marks = marks,
        negativeMarks = negativeMarks, solution = solution, concept = concept, difficulty = difficulty.name, source = source.name,
        sourceYear = sourceYear, sourceNote = sourceNote, validation = validation.name, tagsJson = Json.stringify(tags),
        fingerprint = QuestionValidator.fingerprint(this), flagged = flagged || validation == ValidationStatus.FLAGGED, createdAt = now
    )
    val opts = options.mapIndexed { i, t -> AnswerOptionEntity(id, i, t, i in correctOptions, optionExplanations.getOrElse(i) { "" }) }
    return e to opts
}

fun QuestionEntity.toDomain(options: List<AnswerOptionEntity>): Question {
    val opts = options.sortedBy { it.idx }
    val correct = (runCatching { Json.parse(correctJson) as? List<*> }.getOrNull() ?: emptyList<Any?>()).mapNotNull { (it as? Number)?.toInt() }.toSet()
    val tags = (runCatching { Json.parse(tagsJson) as? List<*> }.getOrNull() ?: emptyList<Any?>()).mapNotNull { it as? String }
    return Question(
        id = id, subjectId = subjectId, topicId = topicId, subtopic = subtopic, statement = statement,
        type = QType.valueOf(type), options = opts.map { it.text }, correctOptions = correct,
        nat = if (natLow != null && natHigh != null) NatAnswer(natLow, natHigh) else null,
        marks = marks, negativeMarks = negativeMarks, solution = solution, optionExplanations = opts.map { it.explanation },
        concept = concept, difficulty = Difficulty.valueOf(difficulty), source = QSource.valueOf(source), sourceYear = sourceYear,
        sourceNote = sourceNote, validation = ValidationStatus.valueOf(validation), tags = tags
    )
}

// ---------- Responses / drafts ----------

object ResponseCodec {
    fun toMap(r: Response): Map<String, Any?> = linkedMapOf("sel" to r.selected.sorted(), "nat" to r.nat)
    fun fromMap(m: Map<String, Any?>): Response =
        Response((m.list("sel") ?: emptyList()).mapNotNull { (it as? Number)?.toInt() }.toSet(), m.dbl("nat"))

    fun encodeMap(responses: Map<String, Response>): String = Json.stringify(responses.mapValues { toMap(it.value) })
    @Suppress("UNCHECKED_CAST")
    fun decodeMap(json: String): Map<String, Response> {
        val m = runCatching { Json.parseObject(json) }.getOrNull() ?: return emptyMap()
        return m.mapNotNull { (k, v) -> (v as? Map<String, Any?>)?.let { k to fromMap(it) } }.toMap()
    }
    fun encodeInts(m: Map<String, Int>): String = Json.stringify(m)
    fun decodeInts(json: String): Map<String, Int> {
        val m = runCatching { Json.parseObject(json) }.getOrNull() ?: return emptyMap()
        return m.mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toInt() } }.toMap()
    }
    fun encodeStrings(m: Map<String, String>): String = Json.stringify(m)
    fun decodeStrings(json: String): Map<String, String> {
        val m = runCatching { Json.parseObject(json) }.getOrNull() ?: return emptyMap()
        return m.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    }
    fun encodeList(l: Collection<String>): String = Json.stringify(l.toList())
    fun decodeList(json: String): List<String> = (runCatching { Json.parse(json) as? List<*> }.getOrNull() ?: emptyList<Any?>()).mapNotNull { it as? String }

    /** Practice draft layout: {"answers":{qid:{sel,nat}},"times":{qid:sec},"results":{qid:"VERDICT|marks"}} */
    fun encodeDraft(s: PracticeState): String = Json.stringify(linkedMapOf(
        "answers" to s.answers.mapValues { toMap(it.value) }, "times" to s.times,
        "results" to s.results.mapValues { "${it.value.verdict.name}|${it.value.marks}" }))

    @Suppress("UNCHECKED_CAST")
    fun decodeDraft(json: String): Triple<Map<String, Response>, Map<String, Int>, Map<String, AnswerResult>> {
        val m = runCatching { Json.parseObject(json) }.getOrNull() ?: return Triple(emptyMap(), emptyMap(), emptyMap())
        val answers = (m.obj("answers") ?: emptyMap()).mapNotNull { (k, v) -> (v as? Map<String, Any?>)?.let { k to fromMap(it) } }.toMap()
        val times = (m.obj("times") ?: emptyMap()).mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toInt() } }.toMap()
        val results = (m.obj("results") ?: emptyMap()).mapNotNull { (k, v) ->
            val p = (v as? String)?.split('|') ?: return@mapNotNull null
            val verdict = runCatching { Verdict.valueOf(p[0]) }.getOrNull() ?: return@mapNotNull null
            k to AnswerResult(verdict, p.getOrNull(1)?.toDoubleOrNull() ?: 0.0)
        }.toMap()
        return Triple(answers, times, results)
    }
}

fun PracticeSessionEntity.toState(): PracticeState {
    val (answers, times, results) = ResponseCodec.decodeDraft(draftJson)
    return PracticeState(ResponseCodec.decodeList(questionIdsJson), batchSize, submittedBatches, answers, times, results)
}

fun ExamSessionEntity.toState(): ExamState = ExamState(
    questionIds = ResponseCodec.decodeList(questionIdsJson), durationSec = durationSec, remainingSec = remainingSec,
    responses = ResponseCodec.decodeMap(responsesJson), marked = ResponseCodec.decodeList(markedJson).toSet(),
    visited = ResponseCodec.decodeList(visitedJson).toSet(), timeSpent = ResponseCodec.decodeInts(timesJson), submitted = status == "SUBMITTED"
)

// ---------- Mistakes ----------

fun MistakeRecord.toEntity() = MistakeEntity(
    id = id, dedupeKey = dedupeKey, subjectId = subjectId, topicId = topicId, concept = concept, createdEpochDay = createdDate.toEpochDay(),
    userAnswer = userAnswer, correctAnswer = correctAnswer, category = category.name, explanation = explanation, occurrences = occurrences,
    successfulRevisions = successfulRevisions, consecutiveSuccess = consecutiveSuccess, succeededIdsJson = ResponseCodec.encodeList(succeededQuestionIds),
    questionIdsJson = ResponseCodec.encodeList(questionIds), status = status.name, lastAttemptedEpochDay = lastAttempted?.toEpochDay(),
    lastSuccessEpochDay = lastSuccessDate?.toEpochDay(), nextRevisionEpochDay = nextRevision?.toEpochDay(),
    relatedJson = ResponseCodec.encodeList(relatedConcepts), source = source.name, notes = notes,
    overcomeEpochDay = overcomeDate?.toEpochDay(), recurrence = recurrenceAfterOvercome
)

fun MistakeEntity.toDomain() = MistakeRecord(
    id = id, dedupeKey = dedupeKey, questionIds = ResponseCodec.decodeList(questionIdsJson), subjectId = subjectId, topicId = topicId,
    concept = concept, createdDate = LocalDate.ofEpochDay(createdEpochDay), userAnswer = userAnswer, correctAnswer = correctAnswer,
    category = MistakeCategory.valueOf(category), explanation = explanation, occurrences = occurrences, successfulRevisions = successfulRevisions,
    consecutiveSuccess = consecutiveSuccess, succeededQuestionIds = ResponseCodec.decodeList(succeededIdsJson), status = MistakeStatus.valueOf(status),
    lastAttempted = lastAttemptedEpochDay?.let { LocalDate.ofEpochDay(it) }, lastSuccessDate = lastSuccessEpochDay?.let { LocalDate.ofEpochDay(it) },
    nextRevision = nextRevisionEpochDay?.let { LocalDate.ofEpochDay(it) }, relatedConcepts = ResponseCodec.decodeList(relatedJson),
    source = QSource.valueOf(source), notes = notes, overcomeDate = overcomeEpochDay?.let { LocalDate.ofEpochDay(it) }, recurrenceAfterOvercome = recurrence
)

fun RevisionItemEntity.toDomain() = RevisionEntry(
    itemKey, stage, LocalDate.ofEpochDay(dueEpochDay), doneEpochDay?.let { LocalDate.ofEpochDay(it) }, label, subjectId, topicId
)

fun RevisionEntry.toEntity(learnDay: Int) = RevisionItemEntity(uniqueKey, itemKey, stage, dueDate.toEpochDay(), doneDate?.toEpochDay(), label, subjectId, topicId, learnDay)

// ---------- Quests / alarms / mock ----------

fun DailyQuestEntity.toSpec() = QuestSpec(
    id, day, LocalDate.ofEpochDay(epochDay).toString(), QuestKind.valueOf(kind), name, detail, plannedMin, plannedMax, required, xp, requirement, topicId, subjectId
)

fun AlarmSettingEntity.toConfig(): AlarmConfig {
    val aid = AlarmId.valueOf(id)
    return AlarmConfig(aid, enabled, if (minuteOfDay < 0) null else java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60), sound, vibrate, snoozeMinutes,
        fromPlan = AlarmDefaults.all().first { it.id == aid }.fromPlan)
}

fun AlarmConfig.toEntity() = AlarmSettingEntity(id.name, enabled, time?.let { it.hour * 60 + it.minute } ?: -1, sound, vibrate, snoozeMinutes)

fun MockResultEntity.toDomain() = MockResult(id, day, type, percent, netMarks, maxMarks, accuracy, attempted, total, timeUsedSec, durationSec, epochDay)
