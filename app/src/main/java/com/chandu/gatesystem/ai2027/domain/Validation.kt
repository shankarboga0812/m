package com.chandu.gatesystem.ai2027.domain

import java.security.MessageDigest
import kotlin.math.abs

data class ValidationReport(val issues: List<String>) {
    val ok get() = issues.isEmpty()
}

/** Structural validation applied before ANY question is accepted into practice, exam or mock sets. */
object QuestionValidator {
    fun normalize(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()

    fun validate(q: Question): ValidationReport {
        val e = ArrayList<String>()
        if (q.id.isBlank()) e += "Missing question id"
        if (q.subjectId.isBlank() || q.topicId.isBlank()) e += "Missing subject/topic id"
        if (q.statement.isBlank()) e += "Empty question statement"
        if (q.marks !in 1..4) e += "Marks must be between 1 and 4 (was ${q.marks})"
        if (q.solution.isBlank()) e += "Missing step-by-step solution"
        if (q.concept.isBlank()) e += "Missing tested concept"
        when (q.type) {
            QType.MCQ, QType.MSQ -> {
                if (q.options.size < 2) e += "Needs at least 2 options"
                if (q.options.any { it.isBlank() }) e += "Blank option text"
                val norm = q.options.map { normalize(it) }
                if (norm.toSet().size != norm.size) e += "Duplicate options"
                if (q.correctOptions.isEmpty()) e += "Missing answer key"
                if (q.correctOptions.any { it !in q.options.indices }) e += "Answer key refers to a non-existent option"
                if (q.type == QType.MCQ && q.correctOptions.size != 1) e += "MCQ must have exactly one correct option"
                if (q.type == QType.MSQ && q.correctOptions.size > q.options.size) e += "MSQ key larger than option list"
                if (q.type == QType.MCQ && q.options.size != 4) e += "MCQ should have 4 options"
                if (q.nat != null) e += "Option-type question must not carry a numeric key"
            }
            QType.NAT -> {
                if (q.options.isNotEmpty()) e += "NAT question must not have options"
                if (q.nat == null) e += "Missing numeric answer key"
                else if (q.nat.low.isNaN() || q.nat.high.isNaN() || q.nat.low > q.nat.high) e += "Invalid numeric range"
                if (q.correctOptions.isNotEmpty()) e += "NAT question must not have option keys"
            }
        }
        // Negative marking rules: MCQ only; MSQ and NAT never.
        if (q.type != QType.MCQ && q.negativeMarks != 0.0) e += "Only MCQs may carry negative marks"
        if (q.type == QType.MCQ && (q.negativeMarks < 0 || q.negativeMarks > q.marks)) e += "Invalid MCQ negative marks"
        if (q.optionExplanations.isNotEmpty() && q.optionExplanations.size != q.options.size) e += "Option explanations count differs from option count"
        if (q.source == QSource.VERIFIED_PYQ && q.sourceYear == null) e += "Verified PYQ must record its year"
        if (q.source == QSource.AI_GENERATED && q.validation == ValidationStatus.VERIFIED_DETERMINISTIC)
            e += "AI question cannot claim deterministic verification without a checker result"
        return ValidationReport(e)
    }

    /** Fingerprint used for duplicate detection (statement + options, order-insensitive). */
    fun fingerprint(q: Question): String {
        val body = normalize(q.statement) + "|" + q.type + "|" + q.options.map { normalize(it) }.sorted().joinToString("¦")
        return sha256(body).take(24)
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

class DuplicateDetector(existing: Collection<Question> = emptyList()) {
    private val seen = HashSet<String>().apply { existing.forEach { add(QuestionValidator.fingerprint(it)) } }
    fun isDuplicate(q: Question) = QuestionValidator.fingerprint(q) in seen
    /** Returns true if added (not a duplicate). */
    fun register(q: Question): Boolean = seen.add(QuestionValidator.fingerprint(q))
}

/** Safe arithmetic evaluator used to double-check numeric AI answers deterministically. */
object ExpressionEvaluator {
    class EvalException(m: String) : Exception(m)
    fun eval(expr: String): Double {
        val p = P(expr.replace(" ", "")); val v = p.expr()
        if (p.i != p.s.length) throw EvalException("Unexpected '${p.s[p.i]}'")
        return v
    }
    private class P(val s: String) {
        var i = 0
        fun peek() = if (i < s.length) s[i] else '\u0000'
        fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') { val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = power()
            while (peek() == '*' || peek() == '/' || peek() == '%') {
                val op = s[i++]; val r = power()
                v = when (op) { '*' -> v * r; '/' -> { if (r == 0.0) throw EvalException("division by zero"); v / r }; else -> { if (r == 0.0) throw EvalException("mod by zero"); v % r } }
            }
            return v
        }
        fun power(): Double {
            val b = unary()
            if (peek() == '^') { i++; return Math.pow(b, power()) }
            return b
        }
        fun unary(): Double {
            if (peek() == '-') { i++; return -unary() }
            if (peek() == '+') { i++; return unary() }
            return atom()
        }
        fun atom(): Double {
            if (peek() == '(') { i++; val v = expr(); if (peek() != ')') throw EvalException("missing )"); i++; return v }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw EvalException("number expected at $i")
            return s.substring(st, i).toDouble()
        }
    }
}

data class Rejection(val index: Int, val reasons: List<String>)
data class ParsedBatch(val accepted: List<Question>, val flagged: List<Question>, val rejected: List<Rejection>, val schemaError: String? = null)

/** Context the question was requested for (not trusted from the AI response). */
data class GenerationContext(val subjectId: String, val topicId: String, val subtopic: String, val defaultDifficulty: Difficulty, val marks: Int?)

/**
 * Parses and validates AI backend output. The AI never decides scoring parameters:
 * negative marks and source/validation labels are assigned here, locally.
 */
object AiQuestionParser {
    fun parse(json: String, ctx: GenerationContext, existing: Collection<Question> = emptyList()): ParsedBatch {
        val root = try { Json.parseObject(json) } catch (e: Exception) {
            return ParsedBatch(emptyList(), emptyList(), emptyList(), "Malformed JSON from backend: ${e.message}")
        }
        val arr = root.list("questions") ?: return ParsedBatch(emptyList(), emptyList(), emptyList(), "Response has no 'questions' array")
        val dup = DuplicateDetector(existing)
        val ok = ArrayList<Question>(); val flagged = ArrayList<Question>(); val rej = ArrayList<Rejection>()
        arr.forEachIndexed { idx, raw ->
            @Suppress("UNCHECKED_CAST") val m = raw as? Map<String, Any?>
            if (m == null) { rej += Rejection(idx, listOf("Question is not an object")); return@forEachIndexed }
            val reasons = ArrayList<String>()
            val type = m.str("type")?.uppercase()?.let { t -> QType.values().firstOrNull { it.name == t } }
            if (type == null) reasons += "Unknown or missing question type"
            val statement = m.str("statement")?.trim().orEmpty()
            val options = m.list("options")?.map { it?.toString()?.trim().orEmpty() } ?: emptyList()
            val marks = m.int("marks") ?: ctx.marks ?: 1
            val keyList = m.list("correct")?.mapNotNull { (it as? Number)?.toInt() }
            var nat: NatAnswer? = null
            if (type == QType.NAT) {
                val ans = m["answer"]
                nat = when (ans) {
                    is Number -> NatAnswer.exact(ans.toDouble(), (m.dbl("tolerance") ?: 0.0).coerceAtLeast(0.0))
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST") val am = ans as Map<String, Any?>
                        val lo = am.dbl("low"); val hi = am.dbl("high")
                        if (lo != null && hi != null) NatAnswer(lo, hi) else null
                    }
                    else -> null
                }
                if (nat == null) reasons += "NAT question without numeric answer key"
            }
            val diff = m.str("difficulty")?.uppercase()?.let { d -> Difficulty.values().firstOrNull { it.name == d } } ?: ctx.defaultDifficulty
            val q = if (type == null) null else Question(
                id = "ai-" + QuestionValidator.sha256(QuestionValidator.normalize(statement) + options.joinToString("|")).take(16),
                subjectId = ctx.subjectId, topicId = ctx.topicId,
                subtopic = m.str("subtopic") ?: ctx.subtopic,
                statement = statement, type = type, options = if (type == QType.NAT) emptyList() else options,
                correctOptions = if (type == QType.NAT) emptySet() else (keyList ?: emptyList()).toSet(),
                nat = nat, marks = marks, negativeMarks = Scorer.defaultNegative(type, marks),
                solution = m.str("solution").orEmpty(),
                optionExplanations = m.list("optionExplanations")?.map { it?.toString().orEmpty() } ?: emptyList(),
                concept = m.str("concept").orEmpty(), difficulty = diff,
                source = QSource.AI_GENERATED, validation = ValidationStatus.UNVERIFIED_AI
            )
            if (q != null) reasons += QuestionValidator.validate(q).issues
            if (reasons.isNotEmpty() || q == null) { rej += Rejection(idx, reasons.distinct()); return@forEachIndexed }
            if (dup.isDuplicate(q)) { rej += Rejection(idx, listOf("Duplicate of an existing question")); return@forEachIndexed }
            dup.register(q)
            // Deterministic cross-check where the backend supplied a machine-checkable expression.
            val check = m.obj("check")
            val verdict = if (check != null) mechanicalCheck(q, check) else null
            when (verdict) {
                null -> ok += q
                true -> ok += q.copy(validation = ValidationStatus.VERIFIED_DETERMINISTIC)
                false -> flagged += q.copy(validation = ValidationStatus.FLAGGED)
            }
        }
        return ParsedBatch(ok, flagged, rej)
    }

    /** true = verified, false = contradicts key (flag), null = not checkable. */
    fun mechanicalCheck(q: Question, check: Map<String, Any?>): Boolean? {
        if (check.str("kind") != "arith") return null
        val expr = check.str("expr") ?: return null
        val v = try { ExpressionEvaluator.eval(expr) } catch (e: Exception) { return false }
        return when (q.type) {
            QType.NAT -> q.nat!!.let { v >= it.low - 1e-6 * maxOf(1.0, abs(it.low)) && v <= it.high + 1e-6 * maxOf(1.0, abs(it.high)) }
            QType.MCQ -> q.options[q.correctOptions.first()].trim().toDoubleOrNull()?.let { abs(it - v) <= 1e-6 * maxOf(1.0, abs(v)) }
            else -> null
        }
    }
}
