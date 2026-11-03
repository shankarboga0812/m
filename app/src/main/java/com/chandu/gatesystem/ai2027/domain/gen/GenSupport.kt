package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*
import java.util.Random

/** A distractor together with the reason it is wrong (shown after submission). */
data class Wrong(val text: String, val why: String)

/**
 * Helper used by every generator. Generators COMPUTE the answer in code, so the answer key is
 * verified by construction (ValidationStatus.VERIFIED_DETERMINISTIC).
 */
class QGen(val rng: Random, val subjectId: String, val topicId: String, val subtopic: String, val concept: String) {
    private fun id(statement: String, extra: String) =
        "gen-" + QuestionValidator.sha256("$subjectId|$concept|${QuestionValidator.normalize(statement)}|$extra").take(14)

    fun mcq(stem: String, correct: String, wrong: List<Wrong>, solution: String, difficulty: Difficulty = Difficulty.MEDIUM,
            marks: Int = 1, tags: List<String> = emptyList(), correctWhy: String = "Correct."): Question {
        val seen = HashSet<String>(); seen += QuestionValidator.normalize(correct)
        val uniq = wrong.filter { seen.add(QuestionValidator.normalize(it.text)) }.take(3)
        check(uniq.size == 3) { "Need 3 distinct distractors for: $stem (have ${uniq.size})" }
        val all = (listOf(Triple(correct, correctWhy, true)) + uniq.map { Triple(it.text, "Incorrect: ${it.why}", false) }).shuffled(rng)
        return Question(
            id = id(stem, all.joinToString("|") { it.first }), subjectId = subjectId, topicId = topicId, subtopic = subtopic,
            statement = stem, type = QType.MCQ, options = all.map { it.first },
            correctOptions = setOf(all.indexOfFirst { it.third }), nat = null, marks = marks,
            negativeMarks = Scorer.defaultNegative(QType.MCQ, marks), solution = solution,
            optionExplanations = all.map { it.second }, concept = concept, difficulty = difficulty,
            source = QSource.TEMPLATE_GENERATED, validation = ValidationStatus.VERIFIED_DETERMINISTIC, tags = tags
        )
    }

    fun nat(stem: String, answer: Double, solution: String, difficulty: Difficulty = Difficulty.MEDIUM, marks: Int = 1,
            tolerance: Double = 0.0, tags: List<String> = emptyList()): Question = Question(
        id = id(stem, answer.toString()), subjectId = subjectId, topicId = topicId, subtopic = subtopic,
        statement = stem, type = QType.NAT, options = emptyList(), correctOptions = emptySet(),
        nat = NatAnswer.exact(answer, tolerance), marks = marks, negativeMarks = 0.0, solution = solution,
        optionExplanations = emptyList(), concept = concept, difficulty = difficulty,
        source = QSource.TEMPLATE_GENERATED, validation = ValidationStatus.VERIFIED_DETERMINISTIC, tags = tags
    )

    fun msq(stem: String, options: List<String>, correct: Set<Int>, explanations: List<String>, solution: String,
            difficulty: Difficulty = Difficulty.MEDIUM, marks: Int = 2, tags: List<String> = emptyList()): Question {
        // shuffle options while tracking the key
        val order = options.indices.shuffled(rng)
        val newOpts = order.map { options[it] }
        val newKey = order.withIndex().filter { it.value in correct }.map { it.index }.toSet()
        return Question(
            id = id(stem, newOpts.joinToString("|")), subjectId = subjectId, topicId = topicId, subtopic = subtopic,
            statement = stem, type = QType.MSQ, options = newOpts, correctOptions = newKey, nat = null, marks = marks,
            negativeMarks = 0.0, solution = solution, optionExplanations = order.map { explanations[it] },
            concept = concept, difficulty = difficulty, source = QSource.TEMPLATE_GENERATED,
            validation = ValidationStatus.VERIFIED_DETERMINISTIC, tags = tags
        )
    }

    fun between(lo: Int, hi: Int) = lo + rng.nextInt(hi - lo + 1)
    fun <T> pick(l: List<T>): T = l[rng.nextInt(l.size)]
}

fun gcd(a: Long, b: Long): Long = if (b == 0L) Math.abs(a) else gcd(b, a % b)
fun gcd(a: Int, b: Int): Int = gcd(a.toLong(), b.toLong()).toInt()
fun factorial(n: Int): Long { var r = 1L; for (i in 2..n) r *= i; return r }
fun choose(n: Int, k: Int): Long { if (k < 0 || k > n) return 0; var r = 1L; for (i in 1..k) r = r * (n - k + i) / i; return r }
fun frac(n: Long, d: Long): String { val g = gcd(n, d); val a = n / g; val b = d / g; return if (b == 1L) "$a" else "$a/$b" }
fun modPow(b: Long, e: Long, m: Long): Long { var r = 1L % m; var x = b % m; var k = e; while (k > 0) { if (k and 1L == 1L) r = r * x % m; x = x * x % m; k = k shr 1 }; return r }
fun log2Ceil(n: Long): Int { var k = 0; var v = 1L; while (v < n) { v *= 2; k++ }; return k }
fun isPow2(n: Long) = n > 0 && (n and (n - 1)) == 0L
fun fmtNum(d: Double) = Question.fmt(d)

/** Difference-based distractors: values near the right answer, deduplicated and positive-biased. */
fun nearInts(correct: Long, rng: Random, vararg extra: Long): List<Long> {
    val s = LinkedHashSet<Long>()
    for (e in extra) if (e != correct) s += e
    var step = 1L
    while (s.size < 6) { s += correct + step; if (correct - step > 0) s += correct - step; step++ }
    return s.filter { it != correct }.take(8)
}
