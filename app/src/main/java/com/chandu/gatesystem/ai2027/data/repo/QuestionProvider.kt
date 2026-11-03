package com.chandu.gatesystem.ai2027.data.repo

import com.chandu.gatesystem.ai2027.data.SettingsStore
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.domain.gen.QuestionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Provided(val questions: List<Question>, val usedAi: Boolean, val note: String)

/**
 * Question supply, in this order:
 *  1. (optional) AI-generated questions, a share of the set, validated and labelled AI-generated;
 *  2. UNSEEN questions from the pre-installed 250-per-subject bank (topic first, then the whole subject);
 *  3. the offline generator (computed answers), which never runs dry.
 * Nothing the learner has already attempted is served again.
 */
class QuestionProvider(private val content: ContentRepository, private val settings: SettingsStore, private val transport: AiTransport) {

    suspend fun provide(subjectId: String, topicId: String, topicTitle: String, count: Int, wantAi: Boolean, seed: Long,
                        exclude: Set<String>, previousMistakes: List<String> = emptyList(), role: BankRole = BankRole.PRACTICE,
                        avoidIds: Collection<String> = emptyList(), aiMode: String? = null): Provided = withContext(Dispatchers.IO) {
        val notes = ArrayList<String>(); val got = ArrayList<Question>(); var usedAi = false
        fun add(qs: List<Question>) { for (q in qs) if (got.none { it.id == q.id } && q.id !in avoidIds) got += q }
        val cfg = settings.aiConfig()
        if (wantAi && subjectId != "MIXED") {
            if (!cfg.configured) notes += AiOutcome.NotConfigured.describe()
            else {
                val ai = AiService(transport, cfg)
                val existing = content.recentForTopic(topicId, 60)
                val aiCount = if (aiMode != null) count else minOf(count, maxOf(3, (count * 4 + 9) / 10))
                val plan = if (aiCount >= 6) listOf(QType.MCQ to (aiCount * 7 / 10), QType.NAT to (aiCount - aiCount * 7 / 10)) else listOf(QType.MCQ to aiCount)
                var failed = false
                for ((type, n) in plan) {
                    var left = n
                    while (left > 0 && !failed) {
                        val take = minOf(left, 10); left -= take
                        val req = GenerationRequest(Subjects.get(subjectId).name, topicTitle, topicTitle, "GATE CS 2027 practice on $topicTitle",
                            if (aiMode != null) Difficulty.HARD else Difficulty.MEDIUM, type, take, null, previousMistakes, existing.map { it.concept }.distinct().take(20), mode = aiMode)
                        val out = ai.generate(req, existing + got)
                        notes += out.describe()
                        if (out is AiOutcome.Success) {
                            content.ensureAll(out.batch.flagged, flagged = true)
                            val stored = content.ensureAll(out.batch.accepted)
                            add(stored); usedAi = usedAi || stored.isNotEmpty()
                        } else failed = true
                    }
                }
            }
        }
        val salt = ((seed % 9973).toInt() + 7).coerceAtLeast(1)
        if (got.size < count) {
            val bank = content.unseen(subjectId, topicId, null, null, role, got.map { it.id } + avoidIds, salt, count - got.size)
            add(bank)
            if (got.size < count) add(content.unseen(subjectId, null, null, null, role, got.map { it.id } + avoidIds, salt, count - got.size))
            if (got.size < count && role != BankRole.PRACTICE) add(content.unseen(subjectId, null, null, null, null, got.map { it.id } + avoidIds, salt, count - got.size))
            if (got.isNotEmpty()) notes += "Pre-installed question bank supplied ${got.count { it.source != QSource.AI_GENERATED }}"
        }
        if (got.size < count) {
            val ids = HashSet<String>(exclude + avoidIds).also { s -> got.forEach { s += it.id; s += QuestionValidator.fingerprint(it) } }
            var local = QuestionFactory.generate(subjectId, topicId, topicTitle, count - got.size, seed, ids)
            if (local.size < count - got.size) local = local + QuestionFactory.staticFor(subjectId).filter { it.id !in ids && local.none { l -> l.id == it.id } }.take(count - got.size - local.size)
            val stored = content.ensureAll(local)
            add(stored)
            if (stored.isNotEmpty()) notes += "Offline generator supplied ${stored.size} fresh questions (bank exhausted for this topic)"
        }
        Provided(got.take(count), usedAi, notes.joinToString("; ").ifBlank { "Pre-installed question bank" })
    }

    /**
     * GATE-level questions for exam sets / mocks. AI first (HARD, real GATE style, several parallel requests of up to 10),
     * the stored bank / offline generator only tops up what the AI could not supply.
     */
    suspend fun provideGate(subjectId: String, marks: Int, count: Int, mode: String, seed: Long, topicTitles: List<String>,
                            exclude: Set<String>, avoidIds: Collection<String>, role: BankRole): Provided = withContext(Dispatchers.IO) {
        val got = ArrayList<Question>(); val notes = ArrayList<String>()
        val cfg = settings.aiConfig()
        if (!cfg.configured) notes += "AI backend not configured: easier stored questions used. Set backend URL and token in AI Teacher settings."
        else {
            val ai = AiService(transport, cfg)
            val subjName = Subjects.get(subjectId).name
            val pool = topicTitles.shuffled(java.util.Random(seed)).distinct()
            val existing = content.recentForTopic(Topics.id(subjectId, subjName), 60)
            var left = count; var round = 0; var failed = false
            while (left > 0 && !failed && round < 4) {
                val take = minOf(left, 10)
                val focus = if (pool.isEmpty()) subjName else pool.drop((round * 3) % pool.size).take(3).ifEmpty { pool.take(3) }.joinToString("; ")
                val req = GenerationRequest(subjName, subjName, focus,
                    "Real GATE CS 2027 level $marks-mark questions for $subjName. Cover different subtopics. No easy questions.",
                    Difficulty.HARD, QType.MCQ, take, marks, emptyList(), existing.map { it.concept }.distinct().take(20), mode = mode)
                val out = ai.generate(req, existing + got)
                round++
                if (out is AiOutcome.Success) {
                    content.ensureAll(out.batch.flagged, flagged = true)
                    val stored = content.ensureAll(out.batch.accepted).filter { s -> s.id !in avoidIds && got.none { it.id == s.id } }
                    got += stored; left = count - got.size
                } else { notes += out.describe(); failed = true }
            }
        }
        val aiGot = got.size
        if (got.size < count) {
            val salt = ((seed % 9973).toInt() + 7).coerceAtLeast(1)
            val skip = got.map { it.id } + avoidIds
            for (s in content.unseen(subjectId, null, null, marks, role, skip, salt, count - got.size)) if (got.none { it.id == s.id }) got += s
            if (got.size < count) for (s in content.unseen(subjectId, null, null, marks, null, got.map { it.id } + avoidIds, salt + 1, count - got.size)) if (got.none { it.id == s.id }) got += s
            if (got.size < count) {
                val ids = HashSet<String>(exclude + avoidIds).also { h -> got.forEach { h += it.id } }
                got += content.ensureAll(PaperBuilder.draw(subjectId, marks, count - got.size, seed, ids, emptyList()))
            }
            if (got.size > aiGot) notes += "${got.size - aiGot} questions came from the stored bank"
        }
        Provided(got.take(count), aiGot > 0, "AI GATE-level: $aiGot of ${minOf(count, got.size)}" + if (notes.isNotEmpty()) "; " + notes.distinct().joinToString("; ") else "")
    }
}
