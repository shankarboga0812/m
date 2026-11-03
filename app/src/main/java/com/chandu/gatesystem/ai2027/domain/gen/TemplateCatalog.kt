package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*

/** Registry of all generators plus selection by plan topic. */
object TemplateCatalog {
    val all: List<TemplateDef> by lazy {
        GenMath.templates + GenGa.templates + GenDl.templates + GenCoa.templates + GenC.templates + GenDsa.templates +
            GenAlgo.templates + GenToc.templates + GenCd.templates + GenCd2.templates + GenOs.templates + GenDbms.templates + GenCn.templates
    }
    fun forSubject(subjectId: String) = if (subjectId == "MIXED") all else all.filter { it.subjectId == subjectId }

    /** Templates whose keywords occur in the topic title; falls back to the whole subject so a pool is never empty. */
    fun forTopic(subjectId: String, topicTitle: String): List<TemplateDef> {
        val title = topicTitle.lowercase()
        val pool = forSubject(subjectId)
        val hit = pool.filter { d -> d.keywords.any { it in title } }
        return hit.ifEmpty { pool }
    }
    fun topicMatches(subjectId: String, topicTitle: String): Boolean {
        val title = topicTitle.lowercase()
        return forSubject(subjectId).any { d -> d.keywords.any { it in title } }
    }
}

/** Builds fresh, duplicate-free question lists from templates and the static bank. */
object QuestionFactory {
    /**
     * @param seedBase use a different value each time (e.g. epoch-millis) to get new values.
     * @param exclude fingerprints/ids that must not be returned (already in the bank/session).
     */
    fun generate(subjectId: String, topicId: String, topicTitle: String, count: Int, seedBase: Long,
                 exclude: Set<String> = emptySet(), difficulty: Difficulty? = null): List<Question> {
        val templates = TemplateCatalog.forTopic(subjectId, topicTitle)
        if (templates.isEmpty()) return emptyList()
        val out = ArrayList<Question>(); val dup = DuplicateDetector()
        var seed = seedBase; var i = 0; var guard = 0
        while (out.size < count && guard++ < count * 40) {
            val tmpl = templates[i++ % templates.size]
            val q = try { tmpl.generate(seed, topicId) } catch (e: IllegalStateException) { seed += 104729; continue }
            seed += 104729
            if (difficulty != null && q.difficulty != difficulty && guard < count * 20) continue
            if (q.id in exclude || QuestionValidator.fingerprint(q) in exclude || dup.isDuplicate(q)) continue
            if (!QuestionValidator.validate(q).ok) continue
            dup.register(q); out += q
        }
        return out
    }

    /** Static-bank questions for a subject (used mainly for verbal GA etc.). */
    fun staticFor(subjectId: String): List<Question> = StaticBank.questions.filter { it.subjectId == subjectId || subjectId == "MIXED" }
}
