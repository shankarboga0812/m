package com.chandu.gatesystem.ai2027.domain

import java.io.File
import java.time.LocalDate

object TestSupport {
    fun asset(name: String): String {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            val f = File(d, "app/src/main/assets/$name"); if (f.exists()) return f.readText()
            val g = File(d, "src/main/assets/$name"); if (g.exists()) return g.readText()
            d = d.parentFile
        }
        error("asset $name not found from ${System.getProperty("user.dir")}")
    }
    val plan: Plan by lazy { Plan.fromJson(asset("plan_114.json")) }
    fun d(s: String) = LocalDate.parse(s)

    fun mcq(id: String = "q1", marks: Int = 1, correct: Int = 0, concept: String = "Concept A", subject: String = "OS", topic: String = "OS/t") = Question(
        id, subject, topic, "sub", "What is $id?", QType.MCQ, listOf("one", "two", "three", "four"), setOf(correct), null, marks,
        Scorer.defaultNegative(QType.MCQ, marks), "Because.", listOf("a", "b", "c", "d"), concept, Difficulty.MEDIUM, QSource.BANK_ORIGINAL, null, null, ValidationStatus.VALIDATED_SCHEMA)
    fun msq(id: String = "m1", marks: Int = 2, correct: Set<Int> = setOf(0, 2)) = Question(
        id, "OS", "OS/t", "sub", "Select all for $id", QType.MSQ, listOf("one", "two", "three", "four"), correct, null, marks, 0.0, "Because.",
        emptyList(), "Concept M", Difficulty.MEDIUM, QSource.BANK_ORIGINAL, null, null, ValidationStatus.VALIDATED_SCHEMA)
    fun nat(id: String = "n1", marks: Int = 1, v: Double = 10.0, tol: Double = 0.0) = Question(
        id, "OS", "OS/t", "sub", "Compute $id", QType.NAT, emptyList(), emptySet(), NatAnswer.exact(v, tol), marks, 0.0, "Because.",
        emptyList(), "Concept N", Difficulty.MEDIUM, QSource.BANK_ORIGINAL, null, null, ValidationStatus.VALIDATED_SCHEMA)
}
