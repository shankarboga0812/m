package com.chandu.gatesystem.ai2027.domain

/** Roles of the pre-installed question bank. Every subject ships 250 questions split across these pools. */
enum class BankRole(val tag: String, val perSubject: Int) {
    PRACTICE("pool:practice", 150), EXAM("pool:exam", 60), MOCK("pool:mock", 40)
}

object BankCodec {
    const val ASSET = "question_bank_v1.json"
    const val PER_SUBJECT = 250
    val subjects = listOf("EM", "DL", "COA", "C", "DSA", "ALGO", "TOC", "CD", "OS", "DBMS", "CN", "GA")

    fun toJson(qs: List<Question>): String = Json.stringify(linkedMapOf("version" to 1, "questions" to qs.map { q -> linkedMapOf(
        "id" to q.id, "s" to q.subjectId, "t" to q.topicId, "st" to q.subtopic, "q" to q.statement, "type" to q.type.name,
        "o" to q.options, "c" to q.correctOptions.sorted(), "nl" to q.nat?.low, "nh" to q.nat?.high, "m" to q.marks, "n" to q.negativeMarks,
        "sol" to q.solution, "oe" to q.optionExplanations, "con" to q.concept, "d" to q.difficulty.name, "src" to q.source.name,
        "v" to q.validation.name, "tags" to q.tags) }))

    fun fromJson(text: String): List<Question> {
        val root = Json.parseObject(text)
        @Suppress("UNCHECKED_CAST")
        return (root.list("questions") ?: emptyList()).mapNotNull { x ->
            val m = x as? Map<String, Any?> ?: return@mapNotNull null
            fun strs(k: String) = (m.list(k) ?: emptyList()).mapNotNull { it as? String }
            val nl = m.dbl("nl"); val nh = m.dbl("nh")
            Question(
                id = m.str("id")!!, subjectId = m.str("s")!!, topicId = m.str("t")!!, subtopic = m.str("st") ?: "", statement = m.str("q")!!,
                type = QType.valueOf(m.str("type")!!), options = strs("o"), correctOptions = (m.list("c") ?: emptyList()).mapNotNull { (it as? Number)?.toInt() }.toSet(),
                nat = if (nl != null && nh != null) NatAnswer(nl, nh) else null, marks = m.int("m")!!, negativeMarks = m.dbl("n") ?: 0.0,
                solution = m.str("sol") ?: "", optionExplanations = strs("oe"), concept = m.str("con") ?: "",
                difficulty = Difficulty.valueOf(m.str("d")!!), source = QSource.valueOf(m.str("src")!!),
                validation = ValidationStatus.valueOf(m.str("v")!!), tags = strs("tags")
            )
        }
    }

    fun role(q: Question): BankRole? = BankRole.values().firstOrNull { it.tag in q.tags }
}

/** Builds the bank deterministically from the computed-answer generators (used by tools/gen_bank.sh, not at runtime). */
object BankBuilder {
    data class SubjectBank(val subjectId: String, val questions: List<Question>)

    fun build(subjectId: String, topics: List<TopicRef>): SubjectBank {
        val out = ArrayList<Question>(); val used = HashSet<String>()
        fun add(list: List<Question>, role: BankRole) { for (q in list) { used += q.id; used += QuestionValidator.fingerprint(q); out += q.copy(tags = (q.tags + role.tag).distinct()) } }
        val base = 1000L + subjectId.hashCode().toLong().let { Math.abs(it % 100000) }
        add(PaperBuilder.draw(subjectId, null, BankRole.PRACTICE.perSubject, base, used, topics), BankRole.PRACTICE)
        add(PaperBuilder.draw(subjectId, 1, 36, base + 1_000_003, used, topics), BankRole.EXAM)
        add(PaperBuilder.draw(subjectId, 2, 24, base + 2_000_003, used, topics), BankRole.EXAM)
        add(PaperBuilder.draw(subjectId, 1, 24, base + 3_000_003, used, topics), BankRole.MOCK)
        add(PaperBuilder.draw(subjectId, 2, 16, base + 4_000_003, used, topics), BankRole.MOCK)
        return SubjectBank(subjectId, out.mapIndexed { i, q -> if (i % 2 == 0) natToMcq(q) else q })
    }

    /**
     * GATE mixes MCQ and NAT. An exact integer NAT question becomes an MCQ with computed distractors (same verified key),
     * so the bank is not almost entirely NAT.
     */
    fun natToMcq(q: Question): Question {
        val nat = q.nat ?: return q
        if (q.type != QType.NAT || nat.low != nat.high || nat.low != Math.floor(nat.low) || Math.abs(nat.low) > 1e9) return q
        val v = nat.low.toLong()
        val cands = LinkedHashSet<Long>()
        listOf(v * 2, v + 1, v - 1, v + 2, v - 2, if (v % 2 == 0L) v / 2 else v + 3, v + 10, v * 3, v - 3).forEach { if (it != v && (v < 0 || it >= 0)) cands += it }
        if (cands.size < 3) return q
        val rng = java.util.Random(QuestionValidator.sha256(q.id).take(8).toLong(16))
        val wrong = cands.toList().take(6).shuffled(rng).take(3)
        val opts = (listOf(v) + wrong).shuffled(rng)
        val text = opts.map { it.toString() }
        return q.copy(
            id = "bk-" + QuestionValidator.sha256(q.id + "|mcq").take(14), statement = q.statement + "\nChoose the correct option.",
            type = QType.MCQ, options = text, correctOptions = setOf(opts.indexOf(v)), nat = null, negativeMarks = Scorer.defaultNegative(QType.MCQ, q.marks),
            optionExplanations = opts.map { if (it == v) "Correct: matches the computed value." else "Incorrect: does not match the computed value; re-check the calculation." }
        )
    }
}
