package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.Difficulty
import com.chandu.gatesystem.ai2027.domain.Question
import java.util.Random

/**
 * A question template: a function that builds a fresh question from a random seed and COMPUTES the answer in code.
 * Different seeds give different values/scenarios of the same concept, which is what the Revision Set needs.
 */
class TemplateDef(
    val id: String, val subjectId: String, val subtopic: String, val concept: String,
    val keywords: List<String>, val body: (QGen) -> Question
) {
    fun generate(seed: Long, topicId: String): Question {
        // A seed can rarely yield too few distinct distractors; retry deterministically with the next seeds.
        var s = seed
        var last: Exception? = null
        repeat(40) {
            try { return body(QGen(Random(s), subjectId, topicId, subtopic, concept)) } catch (e: IllegalStateException) { last = e; s += 7919 }
        }
        throw IllegalStateException("Template $id could not generate a question: ${last?.message}")
    }
}

fun t(id: String, subject: String, subtopic: String, concept: String, vararg keywords: String, body: (QGen) -> Question) =
    TemplateDef(id, subject, subtopic, concept, keywords.toList(), body)
