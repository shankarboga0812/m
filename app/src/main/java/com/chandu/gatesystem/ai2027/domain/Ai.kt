package com.chandu.gatesystem.ai2027.domain

import com.chandu.gatesystem.ai2027.domain.gen.QuestionFactory
import java.io.IOException

/** User-supplied connection settings. The AI provider key lives ONLY on the backend. */
data class AiConfig(val baseUrl: String, val token: String) {
    val hasUrl get() = baseUrl.isNotBlank()
    val hasToken get() = token.isNotBlank()
    val configured get() = hasUrl && hasToken && (baseUrl.startsWith("https://") || baseUrl.startsWith("http://"))
    fun normalizedBase() = baseUrl.trim().trimEnd('/')
}

data class TransportResponse(val code: Int, val body: String)

/** Network seam: the Android implementation uses HttpURLConnection; tests supply fakes. Throws IOException when offline. */
interface AiTransport {
    @Throws(IOException::class)
    fun post(url: String, bearerToken: String, jsonBody: String): TransportResponse
    @Throws(IOException::class)
    fun get(url: String, bearerToken: String?): TransportResponse
}

data class GenerationRequest(
    val subject: String, val topic: String, val subtopic: String, val learningObjective: String,
    val difficulty: Difficulty, val questionType: QType, val count: Int, val marks: Int?,
    val previousMistakes: List<String> = emptyList(), val coveredConcepts: List<String> = emptyList(),
    val explanationFormat: String = "step-by-step with a reason for every option",
    val mode: String? = null
) {
    fun toJson(): String = Json.stringify(linkedMapOf(
        "subject" to subject, "topic" to topic, "subtopic" to subtopic, "learningObjective" to learningObjective,
        "difficulty" to difficulty.name, "questionType" to questionType.name, "count" to count, "marks" to marks,
        "previousMistakes" to previousMistakes.take(10), "coveredConcepts" to coveredConcepts.take(20), "explanationFormat" to explanationFormat, "mode" to mode))
}

sealed class AiOutcome {
    data class Success(val batch: ParsedBatch, val provider: String?) : AiOutcome()
    object NotConfigured : AiOutcome()
    object Offline : AiOutcome()
    object Unauthorized : AiOutcome()
    data class RateLimited(val retryAfterSec: Int?) : AiOutcome()
    data class ServerError(val code: Int, val message: String) : AiOutcome()
    data class Malformed(val message: String) : AiOutcome()
    val isSuccess get() = this is Success
    fun describe(): String = when (this) {
        is Success -> "AI questions received (${batch.accepted.size} accepted, ${batch.flagged.size} flagged, ${batch.rejected.size} rejected)"
        NotConfigured -> "AI Teacher is not configured (set backend URL and app token in AI Teacher settings)"
        Offline -> "No internet or the backend is unreachable — using stored/offline questions"
        Unauthorized -> "Backend rejected the app token (HTTP 401/403)"
        is RateLimited -> "Backend rate limit reached${retryAfterSec?.let { " — retry in ${it}s" } ?: ""}"
        is ServerError -> "Backend error $code: $message"
        is Malformed -> "Backend returned an unusable response: $message"
    }
}

data class ConnectionStatus(val configured: Boolean, val reachable: Boolean?, val authorised: Boolean?, val aiAvailable: Boolean?, val detail: String)

class AiService(private val transport: AiTransport, private val config: AiConfig) {
    fun generate(req: GenerationRequest, existing: Collection<Question> = emptyList()): AiOutcome {
        if (!config.configured) return AiOutcome.NotConfigured
        val resp = try { transport.post(config.normalizedBase() + "/v1/generate-questions", config.token, req.toJson()) }
        catch (e: IOException) { return AiOutcome.Offline }
        catch (e: Exception) { return AiOutcome.ServerError(-1, e.message ?: "transport failure") }
        return when (resp.code) {
            in 200..299 -> {
                val ctx = GenerationContext(Subjects.fromPlanLabel(req.subject), Topics.id(Subjects.fromPlanLabel(req.subject), req.topic), req.subtopic, req.difficulty, req.marks)
                val batch = AiQuestionParser.parse(resp.body, ctx, existing)
                if (batch.schemaError != null) AiOutcome.Malformed(batch.schemaError)
                else AiOutcome.Success(batch, runCatching { Json.parseObject(resp.body).str("provider") }.getOrNull())
            }
            401, 403 -> AiOutcome.Unauthorized
            429 -> AiOutcome.RateLimited(runCatching { Json.parseObject(resp.body).int("retryAfterSec") }.getOrNull())
            else -> AiOutcome.ServerError(resp.code, runCatching { Json.parseObject(resp.body).str("error") }.getOrNull() ?: resp.body.take(120))
        }
    }

    /** Real connection test: /health (no auth) then an authenticated /v1/status. Never returns a fake success. */
    fun testConnection(): ConnectionStatus {
        if (!config.configured) return ConnectionStatus(false, null, null, null, "Backend URL and/or token not set")
        val base = config.normalizedBase()
        val health = try { transport.get("$base/health", null) } catch (e: IOException) {
            return ConnectionStatus(true, false, null, null, "Cannot reach backend: ${e.message ?: "network error"}")
        }
        if (health.code !in 200..299) return ConnectionStatus(true, false, null, null, "Backend /health returned HTTP ${health.code}")
        val st = try { transport.get("$base/v1/status", config.token) } catch (e: IOException) {
            return ConnectionStatus(true, true, null, null, "Lost connection during auth check")
        }
        if (st.code == 401 || st.code == 403) return ConnectionStatus(true, true, false, null, "Backend reachable but the app token was rejected")
        if (st.code !in 200..299) return ConnectionStatus(true, true, null, null, "Status check failed with HTTP ${st.code}")
        val m = runCatching { Json.parseObject(st.body) }.getOrNull()
            ?: return ConnectionStatus(true, true, true, null, "Status response was not valid JSON")
        val ai = m.bool("aiConfigured") ?: false
        return ConnectionStatus(true, true, true, ai, if (ai) "Connected; provider ${m.str("provider") ?: "?"} is configured on the backend" else "Connected, but the backend has no AI provider key configured")
    }
}

/** Offline-first provider: tries AI when allowed, otherwise (or on any failure) serves local questions. */
data class QuestionSupply(val questions: List<Question>, val usedAi: Boolean, val note: String)

object QuestionSupplier {
    fun supply(ai: AiService?, request: GenerationRequest, subjectId: String, topicId: String, topicTitle: String, wantAi: Boolean,
               seed: Long, exclude: Set<String> = emptySet(), existing: Collection<Question> = emptyList()): QuestionSupply {
        if (wantAi && ai != null) {
            val out = ai.generate(request, existing)
            if (out is AiOutcome.Success && out.batch.accepted.isNotEmpty())
                return QuestionSupply(out.batch.accepted.take(request.count), true, out.describe())
            val local = QuestionFactory.generate(subjectId, topicId, topicTitle, request.count, seed, exclude)
            return QuestionSupply(local, false, out.describe() + (if (local.isNotEmpty()) "; served ${local.size} offline questions instead" else "; no offline questions for this topic"))
        }
        val local = QuestionFactory.generate(subjectId, topicId, topicTitle, request.count, seed, exclude)
        return QuestionSupply(local, false, if (local.isEmpty()) "No stored questions for this topic" else "Offline question generator")
    }
}

/** Evaluation explanation with the seven parts required for each wrong answer. */
data class Explanation(
    val selected: String, val correct: String, val whyWrong: String, val concept: String,
    val steps: String, val shortcut: String?, val practiceNext: String
)

object ExplanationBuilder {
    /** Built from stored data only, so it works offline. An AI explanation may replace the text but never the score. */
    fun local(q: Question, r: Response?): Explanation {
        val sel = ErrorMonsterEngine.userAnswerText(q, r)
        val why = if (r == null || !r.isAnswered(q.type)) "You did not answer this question."
        else if (q.type != QType.NAT && q.optionExplanations.isNotEmpty()) r.selected.sorted().joinToString(" ") { i -> "Option ${'A' + i}: ${q.optionExplanations.getOrElse(i) { "" }}" }
        else "Your value ${sel} does not satisfy the key (${q.correctAnswerText()}). Re-check the calculation steps below."
        val shortcut = when {
            q.tags.contains("formula") -> "Memorise the formula for this concept and apply it directly instead of deriving it under time pressure."
            q.type == QType.MCQ -> "Eliminate options that contradict the definition first; negative marking makes blind guessing costly."
            q.type == QType.NAT -> "Do a quick sanity check on magnitude/units before entering the number; NAT has no negative marking so always attempt."
            else -> null
        }
        return Explanation(sel, q.correctAnswerText(), why, q.concept, q.solution, shortcut, "Practise 3–5 more questions on \"${q.concept}\" (${Subjects.get(q.subjectId).name}).")
    }
}
