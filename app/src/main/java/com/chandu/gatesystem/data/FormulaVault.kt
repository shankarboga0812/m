package com.chandu.gatesystem.data

import android.content.Context

data class Formula(val id: String, val subject: String, val text: String)

object FormulaVault {
    val formulas = listOf(
        Formula("dl1", "Digital Logic", "K-Map: a group of 2^n adjacent 1s eliminates n variables from the term"),
        Formula("coa1", "COA", "Effective Access Time = hit_ratio × cache_time + (1 − hit_ratio) × memory_time"),
        Formula("coa2", "COA", "Speedup (pipelining) = (non-pipeline time) / (pipeline time), ideal speedup = k for k stages"),
        Formula("os1", "Operating Systems", "Average waiting time = Σ(waiting times) / n (compute per scheduling algorithm)"),
        Formula("os2", "Operating Systems", "Page fault rate & EAT = (1 − p) × ma + p × page_fault_time"),
        Formula("dbms1", "Database Management Systems", "BCNF: for every non-trivial FD X → Y, X must be a superkey"),
        Formula("dbms2", "Database Management Systems", "3NF: for every FD X → Y, either X is a superkey OR Y is a prime attribute"),
        Formula("toc1", "Theory of Computation", "Pumping Lemma: |w| ≥ p ⇒ w = xyz with |xy| ≤ p, |y| ≥ 1, and xyⁱz ∈ L for all i ≥ 0"),
        Formula("cn1", "Computer Networks", "Bandwidth-Delay Product = Bandwidth × RTT (max unacknowledged data in flight)"),
        Formula("cn2", "Computer Networks", "Transmission time = Packet size / Bandwidth; Total time = Transmission + Propagation"),
        Formula("em1", "Engineering Mathematics", "Bayes' Theorem: P(A|B) = P(B|A)·P(A) / P(B)"),
        Formula("em2", "Engineering Mathematics", "Cayley-Hamilton: every square matrix satisfies its own characteristic equation"),
        Formula("em3", "Engineering Mathematics", "AM ≥ GM ≥ HM for positive reals, equality iff all values equal"),
        Formula("dsa1", "Data Structures & Algorithms", "Master theorem: T(n) = aT(n/b) + f(n) — compare f(n) with n^(log_b a)"),
        Formula("cd1", "Compiler Design", "FIRST/FOLLOW sets drive LL(1) parse-table construction; conflicts mean the grammar isn't LL(1)")
    )

    private const val PREFS = "gate_progress"
    private const val FAV_KEY = "formula_favorites"
    private const val DIFFICULT_KEY = "formula_difficult"

    fun isFavorite(context: Context, id: String): Boolean =
        favorites(context).contains(id)

    fun isDifficult(context: Context, id: String): Boolean =
        difficult(context).contains(id)

    fun toggleFavorite(context: Context, id: String) = toggle(context, FAV_KEY, id)
    fun toggleDifficult(context: Context, id: String) = toggle(context, DIFFICULT_KEY, id)

    private fun favorites(context: Context) = prefs(context).getStringSet(FAV_KEY, emptySet()) ?: emptySet()
    private fun difficult(context: Context) = prefs(context).getStringSet(DIFFICULT_KEY, emptySet()) ?: emptySet()
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toggle(context: Context, key: String, id: String) {
        val p = prefs(context)
        val current = (p.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (!current.remove(id)) current.add(id)
        p.edit().putStringSet(key, current).apply()
    }
}
