package com.chandu.gatesystem.ai2027.data.repo

import com.chandu.gatesystem.ai2027.data.SettingsStore
import com.chandu.gatesystem.ai2027.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>()
    data class Fail(val message: String) : AiResult<Nothing>()
}

data class AiExplanation(val whyWrong: String, val misconception: String, val steps: String, val shortcut: String, val practiceNext: String)

/** On-demand AI features (connection test, explanations, lessons). The AI never changes a score. */
class AiRepository(private val settings: SettingsStore, private val transport: AiTransport, private val content: ContentRepository) {

    suspend fun testConnection(): ConnectionStatus = withContext(Dispatchers.IO) {
        val st = AiService(transport, settings.aiConfig()).testConnection()
        settings.lastConnectionNote = st.detail
        st
    }

    private fun failFor(code: Int, body: String): String = when (code) {
        401, 403 -> "Backend rejected the app token"
        429 -> "Backend rate limit reached; try again shortly"
        503 -> "Backend has no AI provider configured"
        else -> "Backend error $code: ${runCatching { Json.parseObject(body).str("error") }.getOrNull() ?: body.take(100)}"
    }

    private suspend fun post(path: String, body: Map<String, Any?>): AiResult<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val cfg = settings.aiConfig()
        if (!cfg.configured) return@withContext AiResult.Fail(AiOutcome.NotConfigured.describe())
        try {
            val r = transport.post(cfg.normalizedBase() + path, cfg.token, Json.stringify(body))
            if (r.code !in 200..299) return@withContext AiResult.Fail(failFor(r.code, r.body))
            val m = runCatching { Json.parseObject(r.body) }.getOrNull() ?: return@withContext AiResult.Fail("Backend returned invalid JSON")
            AiResult.Ok(m)
        } catch (e: IOException) { AiResult.Fail(AiOutcome.Offline.describe()) }
        catch (e: Exception) { AiResult.Fail("Request failed: ${e.message}") }
    }

    suspend fun explain(q: Question, r: Response?): AiResult<AiExplanation> {
        val res = post("/v1/explain", linkedMapOf(
            "subject" to Subjects.get(q.subjectId).name, "concept" to q.concept, "statement" to q.statement, "options" to q.options,
            "correctAnswer" to q.correctAnswerText(), "studentAnswer" to ErrorMonsterEngine.userAnswerText(q, r), "officialSolution" to q.solution))
        return when (res) {
            is AiResult.Fail -> res
            is AiResult.Ok -> {
                val e = res.value.obj("explanation") ?: return AiResult.Fail("Backend returned no explanation")
                AiResult.Ok(AiExplanation(e.str("whyWrong") ?: "", e.str("misconception") ?: "", e.str("steps") ?: "", e.str("shortcut") ?: "", e.str("practiceNext") ?: ""))
            }
        }
    }

    suspend fun fetchLesson(topicId: String, title: String, subjectId: String): AiResult<Lesson> {
        val res = post("/v1/lesson", linkedMapOf("subject" to Subjects.get(subjectId).name, "topic" to title, "exam" to "GATE CS 2027"))
        return when (res) {
            is AiResult.Fail -> res
            is AiResult.Ok -> {
                val body = res.value.obj("lesson") ?: return AiResult.Fail("Backend returned no lesson")
                val lesson = LessonCodec.fromJson(Json.stringify(body), topicId, title, LessonSource.AI) ?: return AiResult.Fail("Lesson was empty")
                content.saveAiLesson(lesson)
                AiResult.Ok(lesson)
            }
        }
    }

    /** Optional Telegram relay: the bot token lives only on the server. */
    suspend fun relayTelegram(text: String): AiResult<Unit> {
        if (!settings.telegramRelay) return AiResult.Fail("Telegram relay is switched off")
        return when (val r = post("/v1/telegram/notify", linkedMapOf("text" to text.take(1500)))) { is AiResult.Fail -> r; is AiResult.Ok -> AiResult.Ok(Unit) }
    }
}
