package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*

object GenDl {
    val templates: List<TemplateDef> = listOf(
        t("dl-base", "DL", "Number systems", "Base conversion between decimal, binary, octal and hexadecimal", "number system", "conversion", "binary", "arithmetic") { g ->
            val n = g.between(40, 4000)
            val toHex = g.rng.nextBoolean()
            val ok = if (toHex) Integer.toHexString(n).uppercase() else Integer.toOctalString(n)
            val bin = Integer.toBinaryString(n)
            val ws = if (toHex) listOf(Wrong(Integer.toHexString(n + 16).uppercase(), "off by 16 (one higher in the second digit)"), Wrong(Integer.toHexString(n - 1).uppercase(), "off by one"),
                Wrong(Integer.toOctalString(n), "that is the octal value, not hex"), Wrong(Integer.toHexString(n + 256).uppercase(), "off by 256"), Wrong(ok.reversed(), "digits reversed"))
            else listOf(Wrong(Integer.toOctalString(n + 8), "off by 8"), Wrong(Integer.toOctalString(n - 1), "off by one"), Wrong(Integer.toHexString(n).uppercase(), "that is hexadecimal"),
                Wrong(Integer.toOctalString(n + 64), "off by 64"), Wrong(ok.reversed(), "digits reversed"))
            g.mcq("The decimal number $n is written in ${if (toHex) "hexadecimal" else "octal"} as:", ok, ws,
                "Binary of $n is $bin. Group bits in ${if (toHex) "4s" else "3s"} from the right to get $ok.", Difficulty.EASY)
        },
        t("dl-twos", "DL", "Number systems", "Two's complement range and value", "number system", "binary arithmetic", "complement", "arithmetic") { g ->
            if (g.rng.nextBoolean()) {
                val n = g.between(5, 12)
                g.nat("What is the smallest (most negative) decimal integer representable in $n-bit two's complement?", -Math.pow(2.0, (n - 1).toDouble()), "Range is −2^(n−1) … 2^(n−1)−1; for n=$n the minimum is −${1 shl (n - 1)}.", Difficulty.EASY)
            } else {
                val v = g.between(128, 255); val bits = Integer.toBinaryString(v)
                g.nat("The 8-bit two's complement pattern $bits represents which decimal value?", (v - 256).toDouble(), "Leading bit is 1 so the value is negative: $v − 256 = ${v - 256}.", Difficulty.EASY)
            }
        },
        t("dl-minterms", "DL", "Boolean algebra", "Counting minterms of a Boolean expression", "boolean", "k-map", "minimi", "logic gate", "combinational") { g ->
            val forms = listOf<Pair<String, (Boolean, Boolean, Boolean, Boolean) -> Boolean>>(
                "AB + CD" to { a, b, c, d -> (a && b) || (c && d) },
                "(A + B)(C + D)" to { a, b, c, d -> (a || b) && (c || d) },
                "A ⊕ B ⊕ C ⊕ D" to { a, b, c, d -> a xor b xor c xor d },
                "AB + A'C + BC" to { a, b, c, _ -> (a && b) || (!a && c) || (b && c) },
                "(A ⊕ B)·(C + D)" to { a, b, c, d -> (a xor b) && (c || d) },
                "A'B' + CD'" to { a, b, c, d -> (!a && !b) || (c && !d) })
            val (txt, f) = g.pick(forms)
            var cnt = 0; val ms = ArrayList<Int>()
            for (m in 0..15) if (f(m and 8 != 0, m and 4 != 0, m and 2 != 0, m and 1 != 0)) { cnt++; ms += m }
            g.nat("The function F(A,B,C,D) = $txt has how many minterms (input combinations out of 16 for which F = 1)?", cnt.toDouble(), "Evaluating all 16 rows gives F=1 for minterms ${ms.joinToString(", ")} → $cnt.", Difficulty.MEDIUM)
        },
        t("dl-counter", "DL", "Sequential circuits", "Flip-flop count for counters and shift registers", "counter", "register", "flip", "sequential", "state") { g ->
            when (g.between(0, 2)) {
                0 -> { val n = g.between(5, 100); val ff = log2Ceil(n.toLong())
                    g.nat("What is the minimum number of flip-flops required to build a modulo-$n counter?", ff.toDouble(), "Need 2^k ≥ $n, so k = ⌈log2 $n⌉ = $ff.", Difficulty.EASY) }
                1 -> { val k = g.between(3, 9)
                    g.nat("How many distinct states does a $k-bit Johnson (twisted-ring) counter cycle through?", (2 * k).toDouble(), "A Johnson counter with k flip-flops has 2k states.", Difficulty.EASY) }
                else -> { val k = g.between(3, 9)
                    g.nat("How many distinct states are there in the sequence of a $k-bit ring counter that starts with a single 1?", k.toDouble(), "A ring counter with k flip-flops cycles through k states.", Difficulty.EASY) }
            }
        },
        t("dl-mux", "DL", "Combinational circuits", "Building large muxes/decoders from smaller ones", "combinational", "mux", "adder", "decoder", "multiplexer") { g ->
            val k = g.pick(listOf(2, 4)); val m = g.between(2, if (k == 2) 5 else 3); val nIn = Math.pow(k.toDouble(), m.toDouble()).toInt()
            val cnt = (nIn - 1) / (k - 1)
            g.nat("How many $k:1 multiplexers are needed to construct a single $nIn:1 multiplexer?", cnt.toDouble(), "Each level reduces the input count by a factor of $k: ${nIn}/$k + … + 1 = ($nIn − 1)/($k − 1) = $cnt.", Difficulty.MEDIUM)
        }
    )
}

object GenCoa {
    val templates: List<TemplateDef> = listOf(
        t("coa-cache", "COA", "Cache memory", "Tag, index and offset bits of a set-associative cache", "cache", "memory", "hierarchy", "mapping") { g ->
            val addr = g.pick(listOf(32, 36, 40)); val cacheKb = g.pick(listOf(16, 32, 64, 128, 256)); val block = g.pick(listOf(16, 32, 64, 128)); val ways = g.pick(listOf(1, 2, 4, 8))
            val sets = cacheKb * 1024 / (block * ways); val idx = log2Ceil(sets.toLong()); val off = log2Ceil(block.toLong()); val tag = addr - idx - off
            g.nat("A byte-addressable machine has $addr-bit addresses and a $cacheKb KB ${if (ways == 1) "direct-mapped" else "$ways-way set-associative"} cache with $block-byte blocks. How many tag bits does each cache line need?", tag.toDouble(),
                "Offset = log2 $block = $off. Sets = ${cacheKb * 1024}/($block×$ways) = $sets → index = $idx bits. Tag = $addr − $idx − $off = $tag.", Difficulty.MEDIUM, 2, tags = listOf("formula"))
        },
        t("coa-amat", "COA", "Cache memory", "Average memory access time with two cache levels", "memory interfacing", "cache", "hierarchy", "memory") { g ->
            val t1 = g.between(1, 3); val m1 = g.pick(listOf(5, 10, 20)); val t2 = g.pick(listOf(8, 10, 12)); val m2 = g.pick(listOf(10, 20, 25)); val tm = g.pick(listOf(80, 100, 120))
            val amat = t1 + m1 / 100.0 * (t2 + m2 / 100.0 * tm)
            g.nat("L1 hit time is $t1 ns with miss rate $m1%; L2 hit time is $t2 ns with local miss rate $m2%; main memory access takes $tm ns. What is the average memory access time (ns)?", amat,
                "AMAT = t1 + m1·(t2 + m2·tm) = $t1 + ${m1 / 100.0}·($t2 + ${m2 / 100.0}·$tm) = ${fmtNum(amat)} ns.", Difficulty.MEDIUM, 2, 0.01, tags = listOf("formula"))
        },
        t("coa-pipeline", "COA", "Pipelining", "Pipeline execution time and speedup", "pipeline", "instruction", "hazard") { g ->
            val k = g.between(4, 6); val n = g.pick(listOf(50, 100, 200, 500)); val stalls = g.between(0, 10)
            if (g.rng.nextBoolean()) {
                val total = k + n - 1 + stalls
                g.nat("A $k-stage pipeline executes $n instructions with a total of $stalls stall cycles inserted. How many clock cycles are needed in all?", total.toDouble(), "Cycles = k + n − 1 + stalls = $k + $n − 1 + $stalls = $total.", Difficulty.EASY)
            } else {
                val sp = n.toDouble() * k / (k + n - 1)
                g.nat("A non-pipelined processor needs $k cycles per instruction. An ideal $k-stage pipeline (no stalls) with the same clock executes $n instructions. What is the speedup? (2 decimals)", sp, "Speedup = n·k/(k+n−1) = ${n * k}/${k + n - 1} = ${fmtNum(sp)}.", Difficulty.MEDIUM, 2, 0.01)
            }
        },
        t("coa-cpi", "COA", "Performance", "Average CPI and execution time", "performance", "instruction", "cpi", "clock") { g ->
            val mix = listOf(g.pick(listOf(40, 50, 60)), 0, 0).toMutableList(); mix[1] = g.pick(listOf(20, 30)); mix[2] = 100 - mix[0] - mix[1]
            val cpi = listOf(1, g.between(2, 3), g.between(3, 6)); val freq = g.pick(listOf(1, 2, 2.5, 4)).toDouble()
            val avg = (0..2).sumOf { mix[it] * cpi[it] } / 100.0
            val instr = g.pick(listOf(100, 200, 500)) // millions
            val timeMs = instr * 1e6 * avg / (freq * 1e9) * 1000
            g.nat("A program has ${mix[0]}% ALU instructions (CPI ${cpi[0]}), ${mix[1]}% load/store (CPI ${cpi[1]}) and ${mix[2]}% branches (CPI ${cpi[2]}). It runs $instr million instructions on a ${fmtNum(freq)} GHz processor. What is the execution time in milliseconds?", timeMs,
                "Average CPI = ${mix[0] / 100.0}·${cpi[0]} + ${mix[1] / 100.0}·${cpi[1]} + ${mix[2] / 100.0}·${cpi[2]} = ${fmtNum(avg)}. Time = instructions × CPI / frequency = ${fmtNum(timeMs)} ms.", Difficulty.MEDIUM, 2, 0.01, tags = listOf("formula"))
        },
        t("coa-ieee", "COA", "Computer arithmetic", "IEEE-754 single precision biased exponent", "ieee", "floating", "arithmetic") { g ->
            val ip = g.between(9, 250); val fr = g.pick(listOf(0.0, 0.25, 0.5, 0.75)); val v = ip + fr
            val e = 31 - Integer.numberOfLeadingZeros(ip); val biased = e + 127
            g.nat("In IEEE-754 single precision, the number ${fmtNum(v)} is stored with a biased exponent field. What is the decimal value of that exponent field?", biased.toDouble(),
                "${fmtNum(v)} = 1.xxx × 2^$e after normalising (2^$e ≤ ${fmtNum(v)} < 2^${e + 1}). Bias is 127, so the field = $e + 127 = $biased.", Difficulty.MEDIUM, 2, tags = listOf("formula"))
        },
        t("coa-instr", "COA", "Instruction set", "Instruction format: opcode bits from register fields", "instruction", "addressing", "machine", "format") { g ->
            val len = g.pick(listOf(16, 24, 32)); val regs = g.pick(listOf(16, 32, 64)); val rb = log2Ceil(regs.toLong()); val fields = g.between(2, 3)
            val opBits = len - fields * rb
            g.nat("A $len-bit instruction has $fields register operand fields, each selecting one of $regs registers, and no other operands. How many bits are available for the opcode?", opBits.toDouble(),
                "Each register field needs log2 $regs = $rb bits; $fields fields use ${fields * rb}; opcode = $len − ${fields * rb} = $opBits.", Difficulty.EASY)
        },
        t("coa-disk", "COA", "I/O and storage", "Disk access time", "i/o", "dma", "disk", "interrupt", "storage") { g ->
            val seek = g.pick(listOf(4, 6, 8)); val rpm = g.pick(listOf(6000, 7200, 10000, 15000)); val sectorKb = g.pick(listOf(1, 4)); val rateMBs = g.pick(listOf(50, 100, 200))
            val rot = 0.5 * 60000.0 / rpm; val tr = sectorKb / 1024.0 / rateMBs * 1000.0
            val total = seek + rot + tr
            g.nat("A disk has average seek time $seek ms, rotates at $rpm RPM and transfers data at $rateMBs MB/s. What is the average time (ms) to read one $sectorKb KB block? (Use 1 MB = 1024 KB; 2 decimals.)", total,
                "Rotational latency = ½·60000/$rpm = ${fmtNum(rot)} ms; transfer = $sectorKb/1024/$rateMBs s = ${"%.4f".format(tr)} ms; total ≈ ${"%.2f".format(total)} ms.", Difficulty.MEDIUM, 2, 0.02)
        }
    )
}

object GenC {
    val templates: List<TemplateDef> = listOf(
        t("c-expr", "C", "Operators and precedence", "Operator precedence and integer division", "operator", "precedence", "expression", "data type") { g ->
            val a = g.between(10, 60); val b = g.between(2, 9); val c = g.between(2, 9); val d = g.between(3, 12)
            val ans = a / b * c % d
            val alt1 = a / (b * c) % d; val alt2 = a / b * (c % d); val alt3 = a * c / b % d
            g.mcq("What does the following C program print?\n\nint a = $a, b = $b, c = $c, d = $d;\nprintf(\"%d\", a / b * c % d);", "$ans",
                listOf(Wrong("$alt1", "treats b*c as a single divisor; / * % are left-associative: (((a/b)*c)%d)"), Wrong("$alt2", "applies % to c first; % has the same precedence as * and /, left to right"),
                    Wrong("$alt3", "multiplies before dividing, which changes the integer-division result"), Wrong("${a / b}", "ignores the later * and % operators"), Wrong("${a * c / b}", "forgets % d")),
                "Operators / * % share precedence and associate left to right: ((($a / $b) * $c) % $d). $a/$b = ${a / b} (integer division), ×$c = ${a / b * c}, % $d = $ans.", Difficulty.EASY)
        },
        t("c-loop", "C", "Control flow", "Counting loop iterations", "loop", "control", "conditional", "statement") { g ->
            val s = g.between(2, 5); val a = g.between(0, 6); val b = a + g.between(10, 40)
            val cnt = (a until b step s).count()
            g.nat("How many times is the body executed?\n\nint i, count = 0;\nfor (i = $a; i < $b; i += $s) { count++; }\n/* value of count at the end */", cnt.toDouble(),
                "i takes values $a, ${a + s}, ${a + 2 * s}, … while i < $b. Number of iterations = ⌈($b − $a)/$s⌉ = $cnt.", Difficulty.EASY)
        },
        t("c-pointer", "C", "Pointers and arrays", "Pointer arithmetic and sizeof", "pointer", "array", "string", "memory") { g ->
            val n = g.between(5, 9); val arr = List(n) { g.between(1, 50) }; val k = g.between(0, 2); val j = g.between(1, n - k - 1)
            if (g.rng.nextBoolean()) {
                g.nat("Assume the following is compiled and run.\n\nint arr[] = {${arr.joinToString(", ")}};\nint *p = arr + $k;\nprintf(\"%d\", *(p + $j));", arr[k + j].toDouble(),
                    "p points to arr[$k]; p + $j points to arr[${k + j}] = ${arr[k + j]}.", Difficulty.EASY)
            } else {
                g.nat("Assume sizeof(int) = 4 bytes. What does the following print?\n\nint arr[$n];\nprintf(\"%zu\", sizeof(arr));", (4 * n).toDouble(), "sizeof(arr) = $n × 4 = ${4 * n} bytes (arr is an array here, not a decayed pointer).", Difficulty.EASY)
            }
        },
        t("c-recursion", "C", "Functions and recursion", "Tracing recursive functions", "recurs", "function", "stack") { g ->
            when (g.between(0, 2)) {
                0 -> { val n = g.between(5, 10); fun f(x: Int): Int = if (x <= 1) x else f(x - 1) + f(x - 2)
                    g.nat("int f(int n) { if (n <= 1) return n; return f(n-1) + f(n-2); }\n\nWhat is the value returned by f($n)?", f(n).toDouble(), "This computes Fibonacci numbers: f($n) = ${f(n)}.", Difficulty.EASY) }
                1 -> { val n = g.between(4, 9); var calls = 0; fun f(x: Int): Int { calls++; return if (x <= 1) x else f(x - 1) + f(x - 2) }; f(n)
                    g.nat("int f(int n) { if (n <= 1) return n; return f(n-1) + f(n-2); }\n\nHow many times is f called in total (including the initial call) when evaluating f($n)?", calls.toDouble(), "Calls(n) = 1 + Calls(n−1) + Calls(n−2) with Calls(0)=Calls(1)=1; Calls($n) = $calls.", Difficulty.MEDIUM) }
                else -> { val a = g.between(20, 200); val b = g.between(5, 60); fun gc(x: Int, y: Int): Int = if (y == 0) x else gc(y, x % y)
                    g.nat("int h(int a, int b) { return b == 0 ? a : h(b, a % b); }\n\nWhat does h($a, $b) return?", gc(a, b).toDouble(), "This is Euclid's algorithm: h($a,$b) = gcd($a,$b) = ${gc(a, b)}.", Difficulty.EASY) }
            }
        },
        t("c-bits", "C", "Bitwise operators", "Bitwise AND/OR/XOR and shifts", "bit", "operator", "data type") { g ->
            val a = g.between(5, 63); val b = g.between(5, 63); val c = g.between(5, 63); val s = g.between(1, 3)
            val ans = (a and b) or ((c xor a) shl s)
            g.nat("int a = $a, b = $b, c = $c;\nprintf(\"%d\", (a & b) | ((c ^ a) << $s));\n\nWhat is printed?", ans.toDouble(),
                "a&b = ${a and b}; c^a = ${c xor a}; << $s gives ${(c xor a) shl s}; OR of ${a and b} and ${(c xor a) shl s} = $ans.", Difficulty.MEDIUM, 2)
        }
    )
}
