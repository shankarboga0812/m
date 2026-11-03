package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*

/** Additional compiler-design generators with randomised inputs; every answer is computed by running the algorithm. */
object GenCd2 {
    private val vars = listOf("a", "b", "c", "d", "e", "f")

    // ----- expression trees -----
    private sealed class N
    private class L(val v: String) : N()
    private class B(val op: Char, val l: N, val r: N) : N()
    private fun tree(g: QGen, leaves: Int): N {
        if (leaves == 1) return L(g.pick(vars))
        val left = g.between(1, leaves - 1)
        return B(g.pick(listOf('+', '-', '*', '/')), tree(g, left), tree(g, leaves - left))
    }
    private fun infix(n: N): String = if (n is B) "(${infix(n.l)} ${n.op} ${infix(n.r)})" else (n as L).v
    private fun postfix(n: N): List<String> = if (n is B) postfix(n.l) + postfix(n.r) + n.op.toString() else listOf((n as L).v)
    /** Sethi-Ullman number: minimum registers for a tree evaluated without spilling. */
    private fun su(n: N, isLeft: Boolean = true): Int = if (n is L) (if (isLeft) 1 else 0) else {
        n as B; val a = su(n.l, true); val b = su(n.r, false); if (a == b) a + 1 else maxOf(a, b)
    }

    private fun maxStack(tokens: List<String>): Int { var d = 0; var m = 0; for (t in tokens) { if (t.length == 1 && t[0] in "+-*/") d-- else d++; m = maxOf(m, d) }; return m }

    // ----- plain arithmetic with precedence for SDT -----
    private fun evalPrec(tokens: List<String>): Int {
        var i = 0
        fun num(): Int = tokens[i++].toInt()
        fun term(): Int { var v = num(); while (i < tokens.size && tokens[i] == "*") { i++; v *= num() }; return v }
        var v = term()
        while (i < tokens.size) { val op = tokens[i++]; val r = term(); v = if (op == "+") v + r else v - r }
        return v
    }

    private fun fibCalls(n: Int): Long = if (n <= 1) 1 else 1 + fibCalls(n - 1) + fibCalls(n - 2)

    val templates: List<TemplateDef> = listOf(
        t("cd2-leaders", "CD", "Basic blocks", "Counting basic blocks using leaders", "basic block", "control-flow", "flow graph", "intermediate") { g ->
            val n = g.between(10, 15); val kind = Array(n + 1) { "" }; val target = IntArray(n + 1)
            for (i in 1..n) {
                val r = g.rng.nextInt(10)
                if (i < n && r < 2) { kind[i] = "goto"; target[i] = g.between(1, n) }
                else if (i < n && r < 4) { kind[i] = "if"; target[i] = g.between(1, n) }
            }
            val leaders = sortedSetOf(1)
            for (i in 1..n) if (kind[i] != "") { leaders += target[i]; if (i + 1 <= n) leaders += i + 1 }
            val lines = (1..n).joinToString("\n") { i ->
                "$i: " + when (kind[i]) { "goto" -> "goto ${target[i]}"; "if" -> "if ${g.pick(vars)} < ${g.pick(vars)} goto ${target[i]}"; else -> "${g.pick(vars)} = ${g.pick(vars)} ${g.pick(listOf("+", "-", "*"))} ${g.between(1, 9)}" } }
            g.nat("Consider this three-address code:\n$lines\n\nHow many basic blocks does it contain?", leaders.size.toDouble(),
                "Leaders: instruction 1, every jump target, and every instruction following a jump. Leaders = $leaders → ${leaders.size} basic blocks.", Difficulty.MEDIUM, 2)
        },
        t("cd2-liveness", "CD", "Data-flow analysis", "Live-variable analysis on straight-line code", "liveness", "live variable", "data-flow", "constant propagation") { g ->
            val n = g.between(5, 7)
            val defs = ArrayList<String>(); val uses = ArrayList<List<String>>()
            repeat(n) { defs += g.pick(vars); uses += listOf(g.pick(vars), g.pick(vars)).distinct() }
            val liveOut = vars.shuffled(g.rng).take(g.between(1, 3)).toSet()
            val k = g.between(0, n - 1)
            var live = liveOut
            for (i in n - 1 downTo k) live = (live - defs[i]) + uses[i]
            val code = (0 until n).joinToString("\n") { i -> "S${i + 1}: ${defs[i]} = ${uses[i].joinToString(" + ")}" }
            g.nat("A straight-line block is:\n$code\nThe variables live on exit from the block are {${liveOut.sorted().joinToString(", ")}}. How many variables are live immediately BEFORE S${k + 1}?",
                live.size.toDouble(), "Work backwards: live_in(S) = (live_out − def) ∪ use. Live before S${k + 1} = {${live.sorted().joinToString(", ")}} → ${live.size}.", Difficulty.MEDIUM, 2)
        },
        t("cd2-postfix-depth", "CD", "Intermediate code", "Stack depth when evaluating postfix code", "intermediate", "three-address", "expression", "postfix") { g ->
            val e = tree(g, g.between(4, 7)); val pf = postfix(e)
            g.nat("The expression ${infix(e)} is converted to postfix form (${pf.joinToString(" ")}) and evaluated with an operand stack. What is the maximum stack depth reached?",
                maxStack(pf).toDouble(), "Scan left to right: push operands (+1), each binary operator pops two and pushes one (−1). Peak depth = ${maxStack(pf)}.", Difficulty.EASY, 1)
        },
        t("cd2-sdt-value", "CD", "Syntax-directed translation", "Evaluating a synthesized attribute", "syntax-directed", "translation", "attribute", "parse tree") { g ->
            val k = g.between(3, 5); val toks = ArrayList<String>()
            repeat(k) { i -> toks += g.between(1, 9).toString(); if (i < k - 1) toks += g.pick(listOf("+", "*", "-")) }
            val v = evalPrec(toks)
            g.nat("In the SDD  E → E1 + T {E.val = E1.val + T.val} | E1 − T {E.val = E1.val − T.val} | T;  T → T1 * F {T.val = T1.val × F.val} | F;  F → digit {F.val = digit.lexval}, what is E.val for the input ${toks.joinToString(" ")}?",
                v.toDouble(), "The grammar gives * higher precedence and left associativity for + and −. Evaluate bottom-up: ${toks.joinToString(" ")} = $v.", Difficulty.EASY, 1)
        },
        t("cd2-registers", "CD", "Intermediate code", "Register allocation as graph colouring", "register", "intermediate", "data-flow", "optimi") { g ->
            val n = g.between(5, 7); val adj = Array(n) { BooleanArray(n) }; val edges = ArrayList<Pair<Int, Int>>()
            for (i in 0 until n) for (j in i + 1 until n) if (g.rng.nextInt(100) < 45) { adj[i][j] = true; adj[j][i] = true; edges += i to j }
            if (edges.isEmpty()) { adj[0][1] = true; adj[1][0] = true; edges += 0 to 1 }
            fun colourable(k: Int): Boolean { val c = IntArray(n) { -1 }
                fun go(v: Int): Boolean { if (v == n) return true
                    for (col in 0 until k) { if ((0 until v).all { !adj[v][it] || c[it] != col }) { c[v] = col; if (go(v + 1)) return true; c[v] = -1 } }
                    return false }
                return go(0) }
            val chi = (1..n).first { colourable(it) }
            g.nat("Variables v1..v$n interfere (cannot share a register) on these pairs: ${edges.joinToString(", ") { "(v${it.first + 1},v${it.second + 1})" }}. What is the minimum number of registers needed with no spilling?",
                chi.toDouble(), "Minimum registers = chromatic number of the interference graph, found by exhaustive search = $chi.", Difficulty.MEDIUM, 2)
        },
        t("cd2-parse-nodes", "CD", "Parsing", "Counting nodes in a parse tree", "parse tree", "parsing fundamentals", "derivation", "grammar") { g ->
            val terms = g.between(2, 4); val sizes = List(terms) { g.between(1, 3) }
            var nodes = 0
            nodes += terms            // E nodes: E → E + T (terms−1 times) and E → T once
            nodes += terms - 1        // '+' leaves
            for (f in sizes) { nodes += f /*T*/ + f /*F*/ + f /*id*/ + (f - 1) /* '*' leaves */ }
            val expr = sizes.joinToString(" + ") { f -> List(f) { "id" }.joinToString(" * ") }
            g.nat("For the grammar E → E + T | T,  T → T * F | F,  F → id, how many nodes (internal nodes and leaves, counting every terminal) are in the parse tree of the sentence  $expr ?",
                nodes.toDouble(), "Count E nodes ($terms), '+' leaves (${terms - 1}), and for each term with f factors: f T-nodes, f F-nodes, f id leaves and f−1 '*' leaves. Total = $nodes.", Difficulty.MEDIUM, 2)
        },
        t("cd2-sethi-ullman", "CD", "Intermediate code", "Minimum registers by Sethi–Ullman labelling", "register", "three-address", "intermediate", "expression", "local optimi") { g ->
            val e = tree(g, g.between(4, 7))
            g.nat("Using Sethi–Ullman labelling (left leaf label 1, right leaf label 0, equal child labels → label+1), how many registers are needed to evaluate ${infix(e)} without spilling?",
                su(e).toDouble(), "Label bottom-up: if both children have the same label L the node gets L+1, otherwise the maximum. The root label is ${su(e)}.", Difficulty.MEDIUM, 2)
        },
        t("cd2-activation", "CD", "Runtime environments", "Activation records for recursive calls", "runtime", "activation", "parameter passing", "stack") { g ->
            val n = g.between(4, 9)
            if (g.rng.nextBoolean()) g.nat("int f(int n) { if (n <= 1) return 1; return f(n-1) + f(n-2); }  How many activation records are created in total (calls to f) when f($n) is called?", fibCalls(n).toDouble(),
                "calls(n) = 1 + calls(n−1) + calls(n−2) with calls(0)=calls(1)=1 → ${fibCalls(n)}.", Difficulty.MEDIUM, 2)
            else g.nat("int f(int n) { if (n <= 1) return 1; return f(n-1) + f(n-2); }  What is the maximum number of activation records simultaneously on the stack (including main's call) when f($n) runs? (count f's records only)", n.toDouble(),
                "The deepest chain is f($n) → f(${n - 1}) → … → f(1): $n records.", Difficulty.EASY, 1)
        },
        t("cd2-token-count", "CD", "Lexical analysis", "Counting tokens with maximal munch", "lexical", "token", "lexeme", "scanner") { g ->
            val two = listOf("==", "!=", "<=", ">=", "++", "--", "&&", "||", "+=", "-=", "->")
            val ops = listOf("+", "-", "*", "/", "<", ">", "==", "!=", "<=", "&&", "||", "+=")
            val parts = ArrayList<String>(); val m = g.between(2, 4)
            parts += g.pick(vars) + g.pick(listOf("", "1", "2")); parts += g.pick(listOf("=", "+=", "-="))
            repeat(m) { i -> parts += if (g.rng.nextBoolean()) g.between(1, 99).toString() else g.pick(vars); if (i < m - 1) parts += g.pick(ops) }
            parts += ";"
            val s = parts.joinToString("")
            var i = 0; var cnt = 0
            while (i < s.length) { val c = s[i]
                when { c.isLetter() || c == '_' -> { while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++; cnt++ }
                    c.isDigit() -> { while (i < s.length && s[i].isDigit()) i++; cnt++ }
                    else -> { i += if (i + 1 < s.length && s.substring(i, i + 2) in two) 2 else 1; cnt++ } } }
            g.nat("A C scanner uses maximal munch (longest match). How many tokens does it produce for:  $s", cnt.toDouble(),
                "Scan left to right taking the longest lexeme at each point (identifiers/numbers, two-character operators before one-character ones). Token count = $cnt.", Difficulty.EASY, 1)
        }
    )
}
