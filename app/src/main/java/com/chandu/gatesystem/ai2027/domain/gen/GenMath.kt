package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*

private fun imp(a: Boolean, b: Boolean) = !a || b
private fun b2(x: Boolean) = if (x) "T" else "F"

object GenMath {
    val templates: List<TemplateDef> = listOf(
        t("em-truth", "EM", "Propositional logic", "Counting satisfying rows of a propositional formula", "logic", "proposition", "truth", "tautolog", "connective", "contradiction") { g ->
            val forms = listOf<Pair<String, (Boolean, Boolean, Boolean) -> Boolean>>(
                "(p → q) ∧ (q → r)" to { p, q, r -> imp(p, q) && imp(q, r) },
                "(p ∨ q) → r" to { p, q, r -> imp(p || q, r) },
                "p → (q ∨ r)" to { p, q, r -> imp(p, q || r) },
                "(p ∧ q) ↔ r" to { p, q, r -> (p && q) == r },
                "¬(p ∧ q) ∨ r" to { p, q, r -> !(p && q) || r },
                "(p → q) → r" to { p, q, r -> imp(imp(p, q), r) },
                "p ⊕ q ⊕ r" to { p, q, r -> p xor q xor r },
                "(p ∨ q) ∧ (¬p ∨ r)" to { p, q, r -> (p || q) && (!p || r) })
            val (txt, f) = g.pick(forms)
            val rows = ArrayList<String>(); var count = 0
            for (m in 0..7) { val p = m and 4 != 0; val q = m and 2 != 0; val r = m and 1 != 0; val v = f(p, q, r); if (v) count++; rows += "${b2(p)}${b2(q)}${b2(r)}→${b2(v)}" }
            g.nat("For how many of the 8 truth assignments to (p, q, r) is the formula  $txt  true?", count.toDouble(),
                "Evaluate all 8 rows (pqr→value): ${rows.joinToString(", ")}. Rows with value T: $count.", Difficulty.EASY)
        },
        t("em-relations", "EM", "Relations", "Counting relations with given properties on an n-element set", "relation", "set", "cartesian") { g ->
            val n = g.between(3, 4)
            val kinds = listOf(
                Triple("reflexive", Math.pow(2.0, (n * n - n).toDouble()).toLong(), "A reflexive relation must contain all $n diagonal pairs; each of the other n²−n = ${n * n - n} pairs is free: 2^${n * n - n}."),
                Triple("symmetric", Math.pow(2.0, (n * (n + 1) / 2).toDouble()).toLong(), "Choose independently for each of n(n+1)/2 = ${n * (n + 1) / 2} unordered pairs/diagonal elements: 2^${n * (n + 1) / 2}."),
                Triple("antisymmetric", (Math.pow(2.0, n.toDouble()) * Math.pow(3.0, (n * (n - 1) / 2).toDouble())).toLong(), "Each diagonal pair is free (2^$n); each of the ${n * (n - 1) / 2} unordered off-diagonal pairs has 3 choices (none, one direction, other direction): 2^$n·3^${n * (n - 1) / 2}."),
                Triple("reflexive and symmetric", Math.pow(2.0, (n * (n - 1) / 2).toDouble()).toLong(), "Diagonal fixed; each of ${n * (n - 1) / 2} unordered off-diagonal pairs is in or out: 2^${n * (n - 1) / 2}."))
            val (name, ans, sol) = g.pick(kinds)
            g.nat("How many $name relations are there on a set with $n elements?", ans.toDouble(), sol, Difficulty.MEDIUM, 2)
        },
        t("em-arrangements", "EM", "Counting", "Permutations of a multiset (words with repeated letters)", "combinat", "permut", "counting", "arrangement") { g ->
            val word = g.pick(listOf("BANANA", "LEVEL", "MISSISSIPPI", "COMMITTEE", "ENGINEER", "STATISTICS", "ASSESSMENT", "PEPPER", "BOOKKEEPER", "TOMORROW", "SUCCESS", "CALCULUS", "ALGORITHM", "ADDRESS", "BALLOON", "COFFEE", "DIGITAL", "NETWORK"))
            val counts = word.groupingBy { it }.eachCount()
            var den = 1L; counts.values.forEach { den *= factorial(it) }
            val ans = factorial(word.length) / den
            g.nat("How many distinct arrangements of all the letters of the word $word are possible?", ans.toDouble(),
                "${word.length}! divided by the factorial of each repeated letter's count: ${counts.entries.joinToString(" ") { "${it.key}×${it.value}" }} → ${word.length}!/(${counts.values.joinToString("·") { "$it!" }}) = $ans.", Difficulty.MEDIUM)
        },
        t("em-dice", "EM", "Probability", "Probability from equally likely outcomes (two dice)", "probab", "random variable", "distribution") { g ->
            val s = g.between(3, 11)
            val ways = (1..6).count { a -> s - a in 1..6 }
            val correct = frac(ways.toLong(), 36)
            val w = listOf(Wrong(frac(ways.toLong() + 1, 36), "counts one outcome too many"), Wrong(frac(maxOf(1, ways - 1).toLong(), 36), "misses an outcome"),
                Wrong(frac(ways.toLong(), 12), "uses 12 outcomes instead of 36 ordered pairs"), Wrong(frac(ways.toLong(), 6), "uses 6 outcomes"),
                Wrong(frac((ways * 2).toLong(), 36), "double counts ordered pairs"))
            g.mcq("Two fair six-sided dice are rolled. What is the probability that the sum of the numbers is $s?", correct, w,
                "Ordered pairs (a,b) with a+b=$s: $ways out of 36, so the probability is $correct.", Difficulty.EASY)
        },
        t("em-det", "EM", "Linear algebra", "Determinant of a 3×3 matrix", "matrix", "determinant", "linear algebra", "rank", "system of linear") { g ->
            val m = Array(3) { IntArray(3) { g.between(-2, 4) } }
            val d = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
            val txt = m.joinToString("; ") { it.joinToString(" ") }
            g.nat("Find the determinant of the 3×3 matrix with rows:  [$txt]", d.toDouble(),
                "Expand along row 1: ${m[0][0]}·(${m[1][1]}·${m[2][2]}−${m[1][2]}·${m[2][1]}) − (${m[0][1]})·(${m[1][0]}·${m[2][2]}−${m[1][2]}·${m[2][0]}) + (${m[0][2]})·(${m[1][0]}·${m[2][1]}−${m[1][1]}·${m[2][0]}) = $d.", Difficulty.MEDIUM, 1)
        },
        t("em-eigen", "EM", "Linear algebra", "Eigenvalues via trace and determinant", "eigen", "matrix") { g ->
            val a = g.between(1, 6); val b = g.between(0, 4); val c = g.between(0, 4); val d = g.between(1, 6)
            val askTrace = g.rng.nextBoolean()
            val ans = if (askTrace) a + d else a * d - b * c
            g.nat("The 2×2 matrix A = [[$a, $b],[$c, $d]] has eigenvalues λ1, λ2. Find " + (if (askTrace) "λ1 + λ2." else "λ1 · λ2."), ans.toDouble(),
                if (askTrace) "Sum of eigenvalues = trace = $a + $d = $ans." else "Product of eigenvalues = det = $a·$d − $b·$c = $ans.", Difficulty.EASY, 1, tags = listOf("formula"))
        },
        t("em-calculus", "EM", "Calculus", "Derivative and definite integral of polynomials", "calculus", "derivative", "integral", "limit", "continuity", "maxima") { g ->
            if (g.rng.nextBoolean()) {
                val a = g.between(1, 4); val b = g.between(-3, 4); val c = g.between(-5, 5); val x0 = g.between(1, 4)
                val ans = 3 * a * x0 * x0 + 2 * b * x0 + c
                g.nat("Let f(x) = ${a}x³ ${if (b >= 0) "+" else "−"} ${Math.abs(b)}x² ${if (c >= 0) "+" else "−"} ${Math.abs(c)}x + 7. Find f′($x0).", ans.toDouble(),
                    "f′(x) = ${3 * a}x² + ${2 * b}x + $c; at x=$x0 this is $ans.", Difficulty.EASY)
            } else {
                val a = g.between(1, 3); val b = g.between(1, 3); val c = g.between(1, 4); val k = g.between(1, 3)
                val ans = a * k * k * k + b * k * k + c * k
                g.nat("Evaluate ∫ from 0 to $k of (${3 * a}x² + ${2 * b}x + $c) dx.", ans.toDouble(),
                    "Antiderivative: ${a}x³ + ${b}x² + ${c}x. At $k minus at 0 gives $ans.", Difficulty.MEDIUM)
            }
        },
        t("em-graph", "EM", "Graph theory", "Handshake lemma, Cayley's formula and chromatic number of cycles", "graph") { g ->
            when (g.between(0, 2)) {
                0 -> { val n = g.pick(listOf(6, 8, 10, 12)); val d = g.pick(listOf(3, 4, 5)); val e = n * d / 2
                    g.nat("A simple graph has $n vertices and every vertex has degree $d. How many edges does it have?", e.toDouble(), "Handshake lemma: sum of degrees = 2e, so e = $n·$d/2 = $e.", Difficulty.EASY) }
                1 -> { val n = g.between(4, 7); val ans = Math.pow(n.toDouble(), (n - 2).toDouble()).toLong()
                    g.nat("How many distinct labelled spanning trees does the complete graph K$n have?", ans.toDouble(), "Cayley's formula: n^(n−2) = $n^${n - 2} = $ans.", Difficulty.MEDIUM) }
                else -> { val n = g.between(5, 12); val chi = if (n % 2 == 0) 2 else 3
                    g.nat("What is the chromatic number of the cycle graph C$n?", chi.toDouble(), "An even cycle is bipartite (2 colours). An odd cycle needs 3. C$n is ${if (n % 2 == 0) "even" else "odd"} → $chi.", Difficulty.EASY) }
            }
        },
        t("em-group", "EM", "Algebraic structures", "Order of an element and generators in Z_n", "group", "algebraic", "monoid", "binary operation") { g ->
            val n = g.pick(listOf(12, 15, 18, 20, 24, 30, 36)); 
            if (g.rng.nextBoolean()) {
                val a = (1 until n).filter { gcd(it, n) > 1 && it != n }.let { g.pick(it) }
                g.nat("In the group (Z_$n, +), what is the order of the element $a?", (n / gcd(a, n)).toDouble(), "ord(a) = n/gcd(a,n) = $n/${gcd(a, n)} = ${n / gcd(a, n)}.", Difficulty.MEDIUM, tags = listOf("formula"))
            } else {
                val phi = (1..n).count { gcd(it, n) == 1 }
                g.nat("How many generators does the cyclic group (Z_$n, +) have?", phi.toDouble(), "Generators are elements coprime to $n; their number is φ($n) = $phi.", Difficulty.MEDIUM)
            }
        },
        t("em-lattice", "EM", "Partial orders", "Divisor lattices and Hasse diagrams", "lattice", "poset", "partial order", "hasse") { g ->
            val n = g.pick(listOf(12, 18, 30, 36, 60, 72, 90, 120, 180, 360))
            val divs = (1..n).count { n % it == 0 }
            g.nat("Consider the poset (D_$n, |) of positive divisors of $n ordered by divisibility. How many elements does it contain?", divs.toDouble(),
                "Count divisors of $n: ${(1..n).filter { n % it == 0 }.joinToString(", ")} → $divs.", Difficulty.EASY)
        },
        t("em-modular", "EM", "Number theory", "Modular exponentiation", "modular", "number theory", "gcd", "congruence") { g ->
            val b = g.between(2, 9); val e = g.between(10, 60); val m = g.pick(listOf(7, 11, 13, 17, 19, 23))
            val ans = modPow(b.toLong(), e.toLong(), m.toLong())
            g.nat("Compute $b^$e mod $m.", ans.toDouble(), "Use repeated squaring (or Fermat: b^(m−1) ≡ 1 mod m for prime m, so reduce the exponent mod ${m - 1}). Result: $ans.", Difficulty.MEDIUM, 2)
        }
    )
}

object GenGa {
    val templates: List<TemplateDef> = listOf(
        t("ga-percent", "GA", "Percentages", "Successive percentage change", "percent", "ratio") { g ->
            val a = g.pick(listOf(10, 20, 25, 40, 50)); val b = g.pick(listOf(10, 20, 25, 40, 50))
            val net = ((100 + a) * (100 - b)) / 100.0 - 100.0
            g.nat("The price of an item is increased by $a% and then decreased by $b%. What is the net percentage change in price? (Use a negative value for a decrease.)", net,
                "Multiplier = (1+$a/100)(1−$b/100) = ${(100 + a) * (100 - b) / 10000.0}; net change = ${fmtNum(net)}%.", Difficulty.EASY, tolerance = 0.01)
        },
        t("ga-work", "GA", "Time and work", "Combined work rate", "work", "ratio") { g ->
            val (x, y) = g.pick(listOf(10 to 15, 12 to 6, 20 to 30, 15 to 30, 12 to 4, 18 to 9, 30 to 20, 24 to 8))
            val ans = x.toDouble() * y / (x + y)
            g.nat("A can finish a job in $x days and B can finish it in $y days. Working together, in how many days will they finish it?", ans,
                "Rate = 1/$x + 1/$y = ${x + y}/${x * y} per day, so time = ${x * y}/${x + y} = ${fmtNum(ans)} days.", Difficulty.EASY, tolerance = 0.01)
        },
        t("ga-train", "GA", "Speed and distance", "Relative length/time with unit conversion", "speed", "distance", "mensuration") { g ->
            val kmph = g.pick(listOf(36, 54, 72, 90, 108)); val l = g.pick(listOf(100, 150, 200, 250)); val p = g.pick(listOf(150, 200, 300, 350))
            val ans = (l + p) / (kmph * 5.0 / 18.0)
            g.nat("A train of length $l m runs at $kmph km/h. How many seconds does it take to completely cross a platform of length $p m?", ans,
                "Speed = $kmph×5/18 = ${fmtNum(kmph * 5.0 / 18.0)} m/s; distance = $l+$p = ${l + p} m; time = ${fmtNum(ans)} s.", Difficulty.MEDIUM, tolerance = 0.01)
        },
        t("ga-series", "GA", "Number series", "Pattern recognition in sequences", "series", "number series", "estimation") { g ->
            when (g.between(0, 3)) {
                0 -> { val a = g.between(2, 9); val d = g.between(3, 9); val s = (0..4).map { a + it * d }
                    g.nat("What is the next term of the series ${s.joinToString(", ")}, …?", (a + 5 * d).toDouble(), "Common difference $d, so next = ${s.last()} + $d = ${a + 5 * d}.", Difficulty.EASY) }
                1 -> { val a = g.between(2, 5); val r = g.between(2, 3); val s = (0..4).map { a * Math.pow(r.toDouble(), it.toDouble()).toLong() }
                    g.nat("What is the next term of the series ${s.joinToString(", ")}, …?", (s.last() * r).toDouble(), "Common ratio $r, so next = ${s.last()}×$r = ${s.last() * r}.", Difficulty.EASY) }
                2 -> { val k = g.between(2, 6); val s = (k..k + 4).map { it * it }
                    g.nat("What is the next term of the series ${s.joinToString(", ")}, …?", ((k + 5) * (k + 5)).toDouble(), "These are consecutive squares ${k}² … ${k + 4}²; next is ${k + 5}² = ${(k + 5) * (k + 5)}.", Difficulty.EASY) }
                else -> { val s = (1..5).map { it * (it + 1) / 2 }
                    g.nat("What is the next term of the series ${s.joinToString(", ")}, …? (Triangular numbers.)", 21.0, "Differences grow by 1: +2, +3, +4, +5, then +6 → 15+6 = 21.", Difficulty.EASY) }
            }
        },
        t("ga-adjacent", "GA", "Analytical reasoning", "Arrangement with an adjacency constraint (brute force verified)", "analytical", "permutation", "arrangement", "reasoning", "deduction") { g ->
            val n = g.between(4, 7)
            val adjacent = g.rng.nextBoolean()
            val names = listOf("Asha", "Bala", "Chitra", "Dev", "Esha", "Farid", "Gita", "Hari").shuffled(g.rng)
            var cnt = 0L
            fun perms(a: IntArray, k: Int) {
                if (k == a.size) {
                    var adj = false
                    for (i in 0 until a.size - 1) if ((a[i] == 0 && a[i + 1] == 1) || (a[i] == 1 && a[i + 1] == 0)) { adj = true; break }
                    if (adj == adjacent) cnt++
                    return
                }
                for (i in k until a.size) { var t0 = a[k]; a[k] = a[i]; a[i] = t0; perms(a, k + 1); t0 = a[k]; a[k] = a[i]; a[i] = t0 }
            }
            perms(IntArray(n) { it }, 0)
            val glued = 2 * factorial(n - 1)
            g.nat("In how many ways can $n different people be seated in a row if ${names[0]} and ${names[1]} must ${if (adjacent) "sit next to each other" else "NOT sit next to each other"}?", cnt.toDouble(),
                if (adjacent) "Glue the two into one block: (${n - 1})! orderings × 2 internal orders = $glued (confirmed by exhaustive enumeration: $cnt)."
                else "Total $n! = ${factorial(n)} minus the adjacent arrangements $glued = $cnt (confirmed by exhaustive enumeration).", Difficulty.MEDIUM)
        },
        t("ga-cube", "GA", "Spatial aptitude", "Painted cube cut into unit cubes", "spatial", "cube", "geometry", "mensuration") { g ->
            val n = g.between(3, 6)
            val kinds = listOf(Triple("exactly three faces", 8L, "Only the 8 corner cubes."), Triple("exactly two faces", 12L * (n - 2), "Edge cubes excluding corners: 12(n−2)."),
                Triple("exactly one face", 6L * (n - 2) * (n - 2), "Face interiors: 6(n−2)²."), Triple("no face", ((n - 2).toLong() * (n - 2) * (n - 2)), "Inner cube: (n−2)³."))
            val (what, ans, why) = g.pick(kinds)
            g.nat("A solid cube of side $n cm is painted on all faces and then cut into $n³ unit cubes of side 1 cm. How many unit cubes have $what painted?", ans.toDouble(), "$why With n=$n: $ans.", Difficulty.MEDIUM)
        },
        t("ga-square-circle", "GA", "Geometry", "Square inscribed in a circle", "geometry", "mensuration", "triangle") { g ->
            val r = g.between(2, 12)
            g.nat("A square is inscribed in a circle of radius $r cm. What is the area of the square in cm²?", 2.0 * r * r, "The diagonal equals the diameter 2r=${2 * r}; area = d²/2 = ${4 * r * r}/2 = ${2 * r * r}.", Difficulty.EASY)
        },
        t("ga-stats", "GA", "Statistics", "Mean and median", "statistic", "mean", "median", "probability") { g ->
            val n = g.pick(listOf(5, 7)); val xs = List(n) { g.between(2, 30) }.sorted()
            val askMean = g.rng.nextBoolean()
            val mean = xs.sum().toDouble() / n
            g.nat("For the data set ${xs.shuffled(g.rng).joinToString(", ")}, find the ${if (askMean) "arithmetic mean (give up to 2 decimals)" else "median"}.", if (askMean) mean else xs[n / 2].toDouble(),
                if (askMean) "Sum = ${xs.sum()}, n = $n, mean = ${fmtNum(mean)}." else "Sorted: ${xs.joinToString(", ")}; the middle (position ${n / 2 + 1}) is ${xs[n / 2]}.", Difficulty.EASY, tolerance = 0.01)
        }
    )
}
