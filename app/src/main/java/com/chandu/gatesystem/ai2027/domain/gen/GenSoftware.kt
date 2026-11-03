package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*
import java.util.PriorityQueue

// ------------------------------------------------------------------ DSA ------------------------------------------------------------------
private class BstNode(val k: Int) { var l: BstNode? = null; var r: BstNode? = null }
private fun bstInsert(root: BstNode?, k: Int): BstNode { if (root == null) return BstNode(k); if (k < root.k) root.l = bstInsert(root.l, k) else root.r = bstInsert(root.r, k); return root }
private fun pre(n: BstNode?, o: MutableList<Int>) { if (n == null) return; o += n.k; pre(n.l, o); pre(n.r, o) }
private fun ino(n: BstNode?, o: MutableList<Int>) { if (n == null) return; ino(n.l, o); o += n.k; ino(n.r, o) }
private fun post(n: BstNode?, o: MutableList<Int>) { if (n == null) return; post(n.l, o); post(n.r, o); o += n.k }
private fun height(n: BstNode?): Int = if (n == null) -1 else 1 + maxOf(height(n.l), height(n.r))

object GenDsa {
    val templates: List<TemplateDef> = listOf(
        t("dsa-bst", "DSA", "Binary search trees", "BST insertion and traversals", "tree", "bst", "binary search", "traversal") { g ->
            val keys = (1..60).shuffled(g.rng).take(g.between(6, 8))
            var root: BstNode? = null; keys.forEach { root = bstInsert(root, it) }
            val p = ArrayList<Int>().also { pre(root, it) }; val i = ArrayList<Int>().also { ino(root, it) }; val po = ArrayList<Int>().also { post(root, it) }
            if (g.rng.nextBoolean()) {
                val bfs = ArrayList<Int>(); val q = ArrayDeque<BstNode>(); q.add(root!!); while (q.isNotEmpty()) { val n = q.removeFirst(); bfs += n.k; n.l?.let { q.add(it) }; n.r?.let { q.add(it) } }
                g.mcq("Keys ${keys.joinToString(", ")} are inserted in this order into an initially empty binary search tree. What is the post-order traversal of the resulting tree?", po.joinToString(" "),
                    listOf(Wrong(p.joinToString(" "), "that is the pre-order (root, left, right)"), Wrong(i.joinToString(" "), "that is the in-order, which is the sorted order"), Wrong(bfs.joinToString(" "), "that is the level-order")),
                    "Build the BST by inserting keys in order. Post-order visits left, right, then root: ${po.joinToString(" ")}.", Difficulty.MEDIUM, 2)
            } else {
                g.nat("Keys ${keys.joinToString(", ")} are inserted in this order into an initially empty BST. What is the height of the tree? (A single node has height 0.)", height(root).toDouble(),
                    "Insert each key following BST rules; the longest root-to-leaf path has ${height(root)} edges.", Difficulty.EASY)
            }
        },
        t("dsa-heap", "DSA", "Heaps", "Min-heap insertion vs build-heap", "heap", "priority") { g ->
            val keys = (1..40).shuffled(g.rng).take(g.between(6, 7))
            fun sift(a: MutableList<Int>, i0: Int, n: Int) { var i = i0; while (true) { var s = i; val l = 2 * i + 1; val r = l + 1; if (l < n && a[l] < a[s]) s = l; if (r < n && a[r] < a[s]) s = r; if (s == i) return; val t0 = a[i]; a[i] = a[s]; a[s] = t0; i = s } }
            val ins = ArrayList<Int>(); for (k in keys) { ins += k; var i = ins.size - 1; while (i > 0 && ins[(i - 1) / 2] > ins[i]) { val p = (i - 1) / 2; val t0 = ins[p]; ins[p] = ins[i]; ins[i] = t0; i = p } }
            val floyd = keys.toMutableList(); for (i in floyd.size / 2 - 1 downTo 0) sift(floyd, i, floyd.size)
            val maxHeap = ArrayList<Int>(); for (k in keys) { maxHeap += k; var i = maxHeap.size - 1; while (i > 0 && maxHeap[(i - 1) / 2] < maxHeap[i]) { val p = (i - 1) / 2; val t0 = maxHeap[p]; maxHeap[p] = maxHeap[i]; maxHeap[i] = t0; i = p } }
            g.mcq("Keys ${keys.joinToString(", ")} are inserted one at a time (in this order) into an initially empty MIN-heap stored in an array. What is the array after all insertions?", ins.joinToString(" "),
                listOf(Wrong(floyd.joinToString(" "), "that is the array produced by Floyd's bottom-up build-heap on the same sequence, not repeated insertion"), Wrong(maxHeap.joinToString(" "), "that is a max-heap"), Wrong(keys.sorted().joinToString(" "), "a sorted array is a valid heap but not what insertion produces"), Wrong(keys.joinToString(" "), "that is the unmodified input order")),
                "Insert each key at the end and sift it up while it is smaller than its parent. Final array: ${ins.joinToString(" ")}.", Difficulty.MEDIUM, 2)
        },
        t("dsa-stack", "DSA", "Stacks and queues", "Stack permutations and circular queue occupancy", "stack", "queue", "deque", "circular") { g ->
            if (g.rng.nextBoolean()) {
                val n = g.between(4, 7); val cat = choose(2 * n, n) / (n + 1)
                g.nat("The numbers 1, 2, …, $n are pushed onto an initially empty stack in this order, with pops allowed at any time. How many different output (pop) sequences are possible?", cat.toDouble(), "Stack-sortable permutations are counted by the Catalan number C_$n = C(${2 * n},$n)/(${n + 1}) = $cat.", Difficulty.MEDIUM, 2, tags = listOf("formula"))
            } else {
                val size = g.between(8, 12); val front = g.between(0, size - 1); val cnt = g.between(2, size - 2); val rear = (front + cnt - 1) % size
                g.nat("A circular queue is implemented in an array of size $size with indices 0…${size - 1}. FRONT = $front points to the first element and REAR = $rear to the last element, and the queue is not empty. How many elements does it hold? (An element count of $size means full.)", cnt.toDouble(),
                    "Count = (REAR − FRONT + SIZE) mod SIZE + 1 = ($rear − $front + $size) mod $size + 1 = $cnt.", Difficulty.EASY)
            }
        },
        t("dsa-hash", "DSA", "Hashing", "Linear probing", "hash") { g ->
            val m = g.pick(listOf(7, 10, 11)); val keys = (10..99).shuffled(g.rng).take(g.between(5, m - 2))
            val table = IntArray(m) { -1 }; var lastIdx = -1; var probes = 0
            for (k in keys) { var i = k % m; var c = 1; while (table[i] != -1) { i = (i + 1) % m; c++ }; table[i] = k; lastIdx = i; probes = c }
            g.nat("A hash table of size $m uses h(k) = k mod $m with linear probing. Keys ${keys.joinToString(", ")} are inserted in this order. At which index (0-based) is the last key ${keys.last()} stored?", lastIdx.toDouble(),
                "Simulate insertions: final table = [${table.joinToString(", ") { if (it < 0) "-" else "$it" }}]. The last key lands at index $lastIdx after $probes probe(s).", Difficulty.MEDIUM, 2)
        },
        t("dsa-tree-count", "DSA", "Trees", "Node counts of AVL, full and complete binary trees", "avl", "tree", "height", "linked list", "array") { g ->
            when (g.between(0, 2)) {
                0 -> { val h = g.between(3, 9); val a = LongArray(h + 1); a[0] = 1; if (h >= 1) a[1] = 2; for (i in 2..h) a[i] = a[i - 1] + a[i - 2] + 1
                    g.nat("What is the minimum number of nodes in an AVL tree of height $h? (A tree with a single node has height 0.)", a[h].toDouble(), "N(0)=1, N(1)=2, N(h)=N(h−1)+N(h−2)+1. So N($h) = ${a[h]}.", Difficulty.MEDIUM, 2) }
                1 -> { val i = g.between(5, 40)
                    g.nat("A full binary tree (every node has 0 or 2 children) has $i internal nodes. How many leaves does it have?", (i + 1).toDouble(), "In a full binary tree, leaves = internal nodes + 1 = ${i + 1}.", Difficulty.EASY) }
                else -> { val n = g.between(20, 500)
                    g.nat("What is the height of a complete binary tree with $n nodes? (Single node has height 0.)", (31 - Integer.numberOfLeadingZeros(n)).toDouble(), "Height = ⌊log2 $n⌋ = ${31 - Integer.numberOfLeadingZeros(n)}.", Difficulty.EASY) }
            }
        }
    )
}

// ------------------------------------------------------------------ Algorithms ------------------------------------------------------------------
data class Edge(val u: Int, val v: Int, val w: Int)
fun randomGraph(g: QGen, n: Int): List<Edge> {
    val e = LinkedHashMap<Pair<Int, Int>, Edge>()
    for (i in 1 until n) { val j = g.between(0, i - 1); e[j to i] = Edge(j, i, g.between(1, 9)) }
    var extra = g.between(n - 1, n + 2)
    var guard = 0
    while (extra > 0 && guard++ < 100) { val a = g.between(0, n - 1); val b = g.between(0, n - 1); if (a == b) continue; val k = minOf(a, b) to maxOf(a, b); if (k in e) continue; e[k] = Edge(k.first, k.second, g.between(1, 9)); extra-- }
    return e.values.toList()
}
fun edgesText(es: List<Edge>) = es.joinToString(", ") { "(${it.u}–${it.v}, w=${it.w})" }
fun dijkstra(n: Int, es: List<Edge>, s: Int): IntArray {
    val d = IntArray(n) { Int.MAX_VALUE }; d[s] = 0
    val pq = PriorityQueue<Pair<Int, Int>>(compareBy { it.first }); pq.add(0 to s)
    while (pq.isNotEmpty()) { val (du, u) = pq.poll(); if (du > d[u]) continue
        for (e in es) { val v = if (e.u == u) e.v else if (e.v == u) e.u else continue; if (du + e.w < d[v]) { d[v] = du + e.w; pq.add(d[v] to v) } } }
    return d
}

object GenAlgo {
    val templates: List<TemplateDef> = listOf(
        t("algo-master", "ALGO", "Recurrences", "Master theorem", "recurrence", "divide", "master", "asymptotic", "analysis", "o, omega") { g ->
            val a = g.between(1, 9); val b = g.between(2, 4); val d = g.between(0, 3)
            val bd = Math.pow(b.toDouble(), d.toDouble()).toInt()
            val critical = "n^(log_$b $a)"
            val (ans, why) = when {
                a > bd -> "Θ($critical)" to "log_$b $a > $d, so the leaves dominate (case 1)."
                a == bd -> "Θ(n^$d log n)" to "log_$b $a = $d, so every level contributes equally (case 2)."
                else -> "Θ(n^$d)" to "log_$b $a < $d, so the root work dominates (case 3; regularity holds for polynomial f)."
            }
            val all = listOf("Θ($critical)", "Θ(n^$d log n)", "Θ(n^$d)", "Θ(n^${d + 1})", "Θ($critical log n)")
            g.mcq("Solve the recurrence T(n) = $a·T(n/$b) + n^$d (with T(1) = 1) using the Master theorem.", ans,
                all.filter { it != ans }.map { Wrong(it, "does not match the comparison of n^(log_$b $a) with n^$d") },
                "Compare n^(log_$b $a) with f(n) = n^$d. $why Therefore T(n) = $ans.", Difficulty.MEDIUM, 2, tags = listOf("formula"))
        },
        t("algo-dijkstra", "ALGO", "Shortest paths", "Dijkstra's algorithm", "shortest", "dijkstra", "graph", "bellman") { g ->
            val n = g.between(5, 6); val es = randomGraph(g, n); val d = dijkstra(n, es, 0)
            g.nat("An undirected weighted graph on vertices 0…${n - 1} has edges: ${edgesText(es)}. What is the length of the shortest path from vertex 0 to vertex ${n - 1}?", d[n - 1].toDouble(),
                "Run Dijkstra from 0. Final distances = [${d.joinToString(", ")}]; the distance to ${n - 1} is ${d[n - 1]}.", Difficulty.MEDIUM, 2)
        },
        t("algo-mst", "ALGO", "Greedy algorithms", "Minimum spanning tree weight (Kruskal)", "spanning", "greedy", "mst", "kruskal", "prim") { g ->
            val n = g.between(5, 6); val es = randomGraph(g, n); val par = IntArray(n) { it }
            fun find(x: Int): Int { var y = x; while (par[y] != y) y = par[y]; return y }
            var w = 0; val chosen = ArrayList<Edge>()
            for (e in es.sortedBy { it.w }) { val a = find(e.u); val b = find(e.v); if (a != b) { par[a] = b; w += e.w; chosen += e } }
            g.nat("A connected undirected graph on vertices 0…${n - 1} has edges: ${edgesText(es)}. What is the total weight of a minimum spanning tree?", w.toDouble(),
                "Kruskal picks edges in increasing weight avoiding cycles: ${chosen.joinToString(", ") { "(${it.u}–${it.v}:${it.w})" }} → total $w.", Difficulty.MEDIUM, 2)
        },
        t("algo-lcs", "ALGO", "Dynamic programming", "Longest common subsequence", "dynamic", "lcs", "dp", "memoization") { g ->
            val s1 = String(CharArray(g.between(6, 8)) { "ABCD"[g.between(0, 3)] }); val s2 = String(CharArray(g.between(6, 8)) { "ABCD"[g.between(0, 3)] })
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 1..s1.length) for (j in 1..s2.length) dp[i][j] = if (s1[i - 1] == s2[j - 1]) dp[i - 1][j - 1] + 1 else maxOf(dp[i - 1][j], dp[i][j - 1])
            g.nat("What is the length of the longest common subsequence of $s1 and $s2?", dp[s1.length][s2.length].toDouble(),
                "Fill the DP table: L[i][j] = L[i−1][j−1]+1 if the characters match, else max(L[i−1][j], L[i][j−1]). L[${s1.length}][${s2.length}] = ${dp[s1.length][s2.length]}.", Difficulty.MEDIUM, 2)
        },
        t("algo-knapsack", "ALGO", "Dynamic programming", "0/1 knapsack", "dynamic", "knapsack", "dp", "greedy") { g ->
            val n = 4; val w = List(n) { g.between(1, 6) }; val v = List(n) { g.between(5, 30) }; val cap = g.between(7, 12)
            val dp = IntArray(cap + 1); for (i in 0 until n) for (c in cap downTo w[i]) dp[c] = maxOf(dp[c], dp[c - w[i]] + v[i])
            g.nat("0/1 knapsack: item weights (${w.joinToString(", ")}), item values (${v.joinToString(", ")}), capacity $cap. What is the maximum total value?", dp[cap].toDouble(),
                "dp[c] = max(dp[c], dp[c−w]+v) over items. dp[$cap] = ${dp[cap]}.", Difficulty.MEDIUM, 2)
        },
        t("algo-sort", "ALGO", "Sorting", "Inversions and bubble-sort swaps", "sort", "inversion", "compar") { g ->
            val a = (1..30).shuffled(g.rng).take(g.between(6, 8)).toMutableList(); val orig = a.toList(); var swaps = 0
            for (i in a.indices) for (j in 0 until a.size - 1 - i) if (a[j] > a[j + 1]) { val t0 = a[j]; a[j] = a[j + 1]; a[j + 1] = t0; swaps++ }
            g.nat("Bubble sort (swap adjacent out-of-order elements, repeated passes) sorts ${orig.joinToString(", ")} in ascending order. How many swaps are performed in total?", swaps.toDouble(),
                "The number of swaps equals the number of inversions (pairs i<j with a[i]>a[j]) = $swaps.", Difficulty.MEDIUM, 2)
        },
        t("algo-loops", "ALGO", "Complexity analysis", "Counting basic operations of loops and binary search", "asymptotic", "analysis", "complexity", "search", "loop") { g ->
            if (g.rng.nextBoolean()) {
                val n = g.pick(listOf(16, 32, 64, 128, 256)); var cnt = 0L; var i = 1; while (i < n) { for (j in 0 until i) cnt++; i *= 2 }
                g.nat("How many times is op() called?\n\nfor (i = 1; i < $n; i = i * 2)\n  for (j = 0; j < i; j++)\n    op();", cnt.toDouble(), "i takes 1, 2, 4, …, ${n / 2}; the inner loop runs i times each: sum = ${n - 1}.", Difficulty.MEDIUM, 2)
            } else {
                val n = g.pick(listOf(100, 1000, 5000, 100000, 1000000)); val w = 32 - Integer.numberOfLeadingZeros(n)
                g.nat("What is the maximum number of comparisons binary search makes (counting one comparison per probe) on a sorted array of $n elements?", w.toDouble(), "Worst case probes = ⌊log2 $n⌋ + 1 = $w.", Difficulty.EASY, tags = listOf("formula"))
            }
        }
    )
}

// ------------------------------------------------------------------ TOC ------------------------------------------------------------------
object GenToc {
    private data class Lang(val text: String, val cls: Int)
    private val cats = listOf("Regular", "Context-free but not regular", "Context-sensitive but not context-free", "Not recursively enumerable")
    private val langs = listOf(
        Lang("L = { aⁿbⁿ : n ≥ 0 }", 1), Lang("L = { aⁿbⁿcⁿ : n ≥ 1 }", 2), Lang("L = { ww : w ∈ {a,b}* }", 2), Lang("L = { wwᴿ : w ∈ {a,b}* }", 1),
        Lang("L = { aᵖ : p is a prime number }", 2), Lang("L = { aⁱbʲ : i ≠ j }", 1), Lang("L = { w ∈ {a,b}* : w ends with abb }", 0), Lang("L = { aⁿbᵐ : n, m ≥ 0 }", 0),
        Lang("L = { a^(2ⁿ) : n ≥ 0 }", 2), Lang("L = { w ∈ {a,b}* : number of a's equals number of b's }", 1))
    val templates: List<TemplateDef> = listOf(
        t("toc-dfa-count", "TOC", "Finite automata", "Counting strings accepted by a DFA", "dfa", "regular", "finite automata", "language", "nfa") { g ->
            val states = 3; val delta = Array(states) { IntArray(2) { g.between(0, 2) } }; val acc = (0 until states).filter { g.rng.nextBoolean() }.ifEmpty { listOf(g.between(0, 2)) }
            val len = g.between(4, 7); var cur = LongArray(states); cur[0] = 1
            repeat(len) { val nx = LongArray(states); for (s in 0 until states) for (c in 0..1) nx[delta[s][c]] += cur[s]; cur = nx }
            val ans = acc.sumOf { cur[it] }
            val tbl = (0 until states).joinToString("; ") { "q$it: 0→q${delta[it][0]}, 1→q${delta[it][1]}" }
            g.nat("A DFA over {0,1} has start state q0, accepting states {${acc.joinToString(", ") { "q$it" }}} and transitions: $tbl. How many binary strings of length $len does it accept?", ans.toDouble(),
                "Do a DP over states: count[s] = number of strings of the current length that end in s. After $len steps the accepting states hold ${acc.joinToString(" + ") { "${cur[it]}" }} = $ans strings.", Difficulty.MEDIUM, 2)
        },
        t("toc-count-strings", "TOC", "Regular languages", "Counting strings of a given form (verified by enumeration)", "language", "string", "regular", "alphabet") { g ->
            val n = g.between(5, 9)
            val preds = listOf<Pair<String, (String) -> Boolean>>(
                "contain no two consecutive 1s" to { s -> !s.contains("11") }, "contain the substring 00" to { s -> s.contains("00") },
                "start and end with the same symbol" to { s -> s.first() == s.last() }, "have an even number of 0s" to { s -> s.count { it == '0' } % 2 == 0 },
                "do not contain the substring 010" to { s -> !s.contains("010") })
            val (txt, p) = g.pick(preds)
            var cnt = 0; for (m in 0 until (1 shl n)) { val s = Integer.toBinaryString(m).padStart(n, '0'); if (p(s)) cnt++ }
            g.nat("How many binary strings of length $n $txt?", cnt.toDouble(), "Counting over all 2^$n = ${1 shl n} strings (verified by exhaustive enumeration) gives $cnt. A recurrence/DP argument yields the same value.", Difficulty.MEDIUM, 2)
        },
        t("toc-classify", "TOC", "Language classes", "Chomsky hierarchy classification", "context-free", "grammar", "pumping", "regular", "decidab", "turing", "pda") { g ->
            val l = g.pick(langs)
            val why = listOf("Regular (finite automaton suffices)", "A PDA recognises it, but the pumping lemma for regular languages rules out a DFA", "Needs a linear-bounded automaton; the CFL pumping lemma fails",
                "Not recursively enumerable")
            g.mcq("The language ${l.text} is best classified as:", cats[l.cls], cats.withIndex().filter { it.index != l.cls }.map { Wrong(it.value, "this is not the tightest class for the language") },
                "Correct class: ${cats[l.cls]}. (${why[l.cls]}.)", Difficulty.MEDIUM, 2)
        },
        t("toc-minstates", "TOC", "Minimal automata", "Minimal DFA state counts", "minimi", "dfa", "nfa", "conversion", "equivalen") { g ->
            when (g.between(0, 2)) {
                0 -> { val k = g.between(3, 12); g.nat("What is the minimum number of states in a DFA over {a} that accepts exactly the strings whose length is divisible by $k?", k.toDouble(), "The DFA must remember length mod $k and all $k residues are distinguishable → $k states.", Difficulty.EASY) }
                1 -> { val k = g.pick(listOf(3, 5, 7, 9, 11)); g.nat("What is the minimum number of states of a DFA over {0,1} accepting exactly the binary representations (read MSB first) of numbers divisible by $k?", k.toDouble(), "State = value mod $k; for odd $k all residues are reachable and pairwise distinguishable → $k states.", Difficulty.MEDIUM, 2) }
                else -> { val n = g.between(3, 8); g.nat("What is the maximum number of states in the DFA obtained by subset construction from an NFA with $n states (without ε-moves, counting the empty set if reachable)?", Math.pow(2.0, n.toDouble()), "Each DFA state is a subset of the NFA states: at most 2^$n = ${1 shl n}.", Difficulty.EASY) }
            }
        }
    )
}

// ------------------------------------------------------------------ Compiler Design ------------------------------------------------------------------
private class Cfg(val name: String, val start: String, val prods: LinkedHashMap<String, List<List<String>>>) {
    val nts get() = prods.keys
    fun isNt(s: String) = s in prods
    fun text() = prods.entries.joinToString("\n") { (a, rs) -> "$a → " + rs.joinToString(" | ") { r -> if (r.isEmpty()) "ε" else r.joinToString(" ") } }
    fun first(): Map<String, Set<String>> {
        val f = HashMap<String, MutableSet<String>>(); nts.forEach { f[it] = HashSet() }
        var ch = true
        while (ch) { ch = false
            for ((a, rs) in prods) for (r in rs) { val add = firstOfSeq(r, f); if (f[a]!!.addAll(add)) ch = true } }
        return f
    }
    fun firstOfSeq(seq: List<String>, f: Map<String, Set<String>>): Set<String> {
        val out = HashSet<String>(); var allNullable = true
        for (s in seq) { if (isNt(s)) { out += f[s]!!.filter { it != "ε" }; if ("ε" !in f[s]!!) { allNullable = false; break } } else { out += s; allNullable = false; break } }
        if (allNullable) out += "ε"; return out
    }
    fun follow(f: Map<String, Set<String>>): Map<String, Set<String>> {
        val fo = HashMap<String, MutableSet<String>>(); nts.forEach { fo[it] = HashSet() }; fo[start]!!.add("$")
        var ch = true
        while (ch) { ch = false
            for ((a, rs) in prods) for (r in rs) for (i in r.indices) { val b = r[i]; if (!isNt(b)) continue
                val rest = firstOfSeq(r.drop(i + 1), f)
                if (fo[b]!!.addAll(rest.filter { it != "ε" })) ch = true
                if ("ε" in rest && fo[b]!!.addAll(fo[a]!!)) ch = true } }
        return fo
    }
}
private val grammars = listOf(
    Cfg("G1", "E", linkedMapOf("E" to listOf(listOf("T", "E'")), "E'" to listOf(listOf("+", "T", "E'"), emptyList()), "T" to listOf(listOf("F", "T'")),
        "T'" to listOf(listOf("*", "F", "T'"), emptyList()), "F" to listOf(listOf("(", "E", ")"), listOf("id")))),
    Cfg("G2", "S", linkedMapOf("S" to listOf(listOf("A", "B")), "A" to listOf(listOf("a", "A"), emptyList()), "B" to listOf(listOf("b", "B"), listOf("c")))),
    Cfg("G3", "S", linkedMapOf("S" to listOf(listOf("i", "E", "t", "S", "S'"), listOf("a")), "S'" to listOf(listOf("e", "S"), emptyList()), "E" to listOf(listOf("b")))),
    Cfg("G4", "S", linkedMapOf("S" to listOf(listOf("A", "S", "d"), listOf("B")), "A" to listOf(listOf("a"), emptyList()), "B" to listOf(listOf("b", "B"), listOf("c"))))
)

private sealed class Ex
private class Lf(val n: String) : Ex()
private class Op(val op: String, val l: Ex, val r: Ex) : Ex()
private fun show(e: Ex): String = when (e) { is Lf -> e.n; is Op -> "(" + show(e.l) + " " + e.op + " " + show(e.r) + ")" }
private fun dagNodes(e: Ex, table: MutableMap<String, Int>): Int {
    val key = when (e) { is Lf -> "L:${e.n}"; is Op -> { val l = dagNodes(e.l, table); val r = dagNodes(e.r, table); "O:${e.op}:$l:$r" } }
    return table.getOrPut(key) { table.size }
}

object GenCd {
    private fun setText(s: Set<String>) = "{" + s.sortedWith(compareBy({ it == "$" }, { it == "ε" }, { it })).joinToString(", ") + "}"
    private val exprs: List<Ex> = run {
        fun l(n: String) = Lf(n)
        fun o(op: String, a: Ex, b: Ex) = Op(op, a, b)
        listOf(
            o("+", o("+", l("a"), o("*", l("a"), o("-", l("b"), l("c")))), o("*", o("-", l("b"), l("c")), l("d"))),
            o("*", o("+", l("a"), l("b")), o("+", l("a"), l("b"))),
            o("+", o("*", l("x"), l("y")), o("-", o("*", l("x"), l("y")), l("z"))),
            o("/", o("+", o("*", l("p"), l("q")), o("*", l("p"), l("q"))), o("+", l("p"), l("q"))))
    }
    private val stmts = listOf("int x = a+b*10;", "if (i<=n && j!=0) k++;", "while(p->next!=NULL) p=p->next;", "total += arr[i]*2;", "x = y == z ? 1 : 0;", "return fib(n-1)+fib(n-2);")
    private fun lex(s: String): Int {
        var i = 0; var n = 0; val two = listOf("==", "!=", "<=", ">=", "++", "--", "&&", "||", "+=", "-=", "->")
        while (i < s.length) { val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isLetter() || c == '_' -> { while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++; n++ }
                c.isDigit() -> { while (i < s.length && s[i].isDigit()) i++; n++ }
                else -> { i += if (i + 1 < s.length && s.substring(i, i + 2) in two) 2 else 1; n++ } } }
        return n
    }
    val templates: List<TemplateDef> = listOf(
        t("cd-follow", "CD", "Parsing", "FIRST and FOLLOW sets", "first", "follow", "pars", "ll", "grammar", "top-down", "ambiguous") { g ->
            val cfg = g.pick(grammars); val f = cfg.first(); val fo = cfg.follow(f); val nt = g.pick(cfg.nts.toList().filter { it != cfg.start || fo[it]!!.size > 1 })
            val askFirst = g.rng.nextBoolean(); val corr = setText(if (askFirst) f[nt]!! else fo[nt]!!); val other = setText(if (askFirst) fo[nt]!! else f[nt]!!)
            val noDollar = setText((if (askFirst) f[nt]!! else fo[nt]!!).filter { it != "$" }.toSet())
            val extra = setText((if (askFirst) f[nt]!! else fo[nt]!!) + "$")
            val withEps = setText((if (askFirst) f[nt]!! else fo[nt]!!) + "ε")
            g.mcq("Consider the grammar (start symbol ${cfg.start}; ε is the empty string):\n${cfg.text()}\n\nWhat is ${if (askFirst) "FIRST" else "FOLLOW"}($nt)?", corr,
                listOf(Wrong(other, "that is the ${if (askFirst) "FOLLOW" else "FIRST"} set"), Wrong(noDollar, "check the end-marker $ rule"), Wrong(extra, "adds the end-marker where it does not belong"), Wrong(withEps, "ε is only in FIRST sets, and only when the symbol is nullable")),
                "Apply the FIRST/FOLLOW rules to a fixpoint. FIRST: ${cfg.nts.joinToString("; ") { "$it=${setText(f[it]!!)}" }}. FOLLOW: ${cfg.nts.joinToString("; ") { "$it=${setText(fo[it]!!)}" }}.", Difficulty.MEDIUM, 2)
        },
        t("cd-lr0-items", "CD", "Parsing", "Counting LR(0) items", "lr", "item", "grammar", "bottom-up", "shift") { g ->
            val cfg = g.pick(grammars); val items = cfg.prods.values.sumOf { rs -> rs.sumOf { it.size + 1 } } + 2 // augmented S' -> .S and S' -> S.
            g.nat("Consider the grammar:\n${cfg.text()}\n\nAugment it with S′ → ${cfg.start}. How many distinct LR(0) items does the augmented grammar have? (ε-productions give a single item A → ·.)", items.toDouble(),
                "A production with k right-hand-side symbols gives k+1 items (ε: 1 item). Sum over productions = ${items - 2}; the augmented production adds 2 → $items.", Difficulty.MEDIUM, 2)
        },
        t("cd-dag", "CD", "Intermediate code", "Common-subexpression elimination with a DAG", "intermediate", "code", "expression", "optimi", "dag", "three-address", "syntax") { g ->
            val e = g.pick(exprs); val tbl = HashMap<String, Int>(); dagNodes(e, tbl)
            val ops = tbl.keys.count { it.startsWith("O:") }
            g.nat("The expression ${show(e)} is converted to a DAG with common subexpressions shared. How many three-address instructions (one per operator node) are needed to compute it?", ops.toDouble(),
                "Build the DAG with value numbering: leaf nodes ${tbl.size - ops}, operator nodes $ops (identical operator+operand pairs are shared). Each operator node becomes one instruction → $ops.", Difficulty.MEDIUM, 2)
        },
        t("cd-tokens", "CD", "Lexical analysis", "Counting tokens (maximal munch)", "lexical", "token", "lexeme", "pattern", "regular expression", "front end") { g ->
            val s = g.pick(stmts)
            g.nat("A C lexical analyser uses the maximal-munch rule (e.g. == is one token). How many tokens does it produce for the statement:  $s", lex(s).toDouble(),
                "Split into keywords/identifiers/numbers/operators/punctuation, always taking the longest match. Count = ${lex(s)}.", Difficulty.EASY)
        }
    )
}
