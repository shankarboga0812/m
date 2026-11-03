package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*

private data class Proc(val id: Int, val arrival: Int, val burst: Int)

/** Simulators exposed for tests so the generators' answers can be cross-checked independently. */
object OsSim {
    fun fcfsWait(arr: IntArray, burst: IntArray): Double {
        val ord = arr.indices.sortedWith(compareBy({ arr[it] }, { it })); var t = 0; var w = 0
        for (i in ord) { t = maxOf(t, arr[i]); w += t - arr[i]; t += burst[i] }
        return w.toDouble() / arr.size
    }
    fun sjfWait(arr: IntArray, burst: IntArray): Double {
        val n = arr.size; val done = BooleanArray(n); var t = 0; var w = 0; var c = 0
        while (c < n) {
            val ready = (0 until n).filter { !done[it] && arr[it] <= t }
            if (ready.isEmpty()) { t = (0 until n).filter { !done[it] }.minOf { arr[it] }; continue }
            val i = ready.sortedWith(compareBy({ burst[it] }, { arr[it] }, { it })).first()
            w += t - arr[i]; t += burst[i]; done[i] = true; c++
        }
        return w.toDouble() / n
    }
    /** Round robin; a process arriving exactly when a quantum expires is queued BEFORE the pre-empted process. */
    fun rrWait(arr: IntArray, burst: IntArray, q: Int): Double {
        val n = arr.size; val rem = burst.copyOf(); val finish = IntArray(n); val queue = ArrayDeque<Int>(); val queued = BooleanArray(n)
        var t = 0; var done = 0
        val order = arr.indices.sortedWith(compareBy({ arr[it] }, { it }))
        fun admit() { for (i in order) if (!queued[i] && arr[i] <= t) { queue.addLast(i); queued[i] = true } }
        admit()
        while (done < n) {
            if (queue.isEmpty()) { t = order.filter { !queued[it] }.minOf { arr[it] }; admit(); continue }
            val i = queue.removeFirst(); val run = minOf(q, rem[i]); t += run; rem[i] -= run; admit()
            if (rem[i] > 0) queue.addLast(i) else { finish[i] = t; done++ }
        }
        return (0 until n).sumOf { finish[it] - arr[it] - burst[it] }.toDouble() / n
    }
    fun srtfWait(arr: IntArray, burst: IntArray): Double {
        val n = arr.size; val rem = burst.copyOf(); val finish = IntArray(n); var t = 0; var done = 0
        while (done < n) {
            val ready = (0 until n).filter { rem[it] > 0 && arr[it] <= t }
            if (ready.isEmpty()) { t++; continue }
            val i = ready.sortedWith(compareBy({ rem[it] }, { arr[it] }, { it })).first(); rem[i]--; t++
            if (rem[i] == 0) { finish[i] = t; done++ }
        }
        return (0 until n).sumOf { finish[it] - arr[it] - burst[it] }.toDouble() / n
    }
    fun pageFaults(ref: List<Int>, frames: Int, policy: String): Int {
        val mem = ArrayList<Int>(); var faults = 0
        for ((idx, p) in ref.withIndex()) {
            if (p in mem) { if (policy == "LRU") { mem.remove(p); mem.add(p) }; continue }
            faults++
            if (mem.size < frames) { mem.add(p); continue }
            when (policy) {
                "FIFO", "LRU" -> mem.removeAt(0)
                else -> { // OPT: evict page whose next use is farthest (or never)
                    val victim = mem.maxByOrNull { m -> val nx = ref.drop(idx + 1).indexOf(m); if (nx < 0) Int.MAX_VALUE else nx }!!; mem.remove(victim) }
            }
            mem.add(p)
        }
        return faults
    }
    fun diskMovement(reqs: List<Int>, head: Int, policy: String, maxCyl: Int): Int {
        when (policy) {
            "FCFS" -> { var c = head; var m = 0; for (r in reqs) { m += Math.abs(r - c); c = r }; return m }
            "SSTF" -> { val left = reqs.toMutableList(); var c = head; var m = 0
                while (left.isNotEmpty()) { val r = left.minWith(compareBy({ Math.abs(it - c) }, { it })); m += Math.abs(r - c); c = r; left.remove(r) }; return m }
            else -> { // SCAN moving toward higher cylinders first, going to the disk end, then reversing
                val up = reqs.filter { it >= head }; val down = reqs.filter { it < head }
                var m = 0; var c = head
                if (up.isNotEmpty() || down.isNotEmpty()) { m += (maxCyl - c); c = maxCyl; if (down.isNotEmpty()) m += (c - down.min()) }
                return m }
        }
    }
    fun safeSequence(alloc: Array<IntArray>, need: Array<IntArray>, avail: IntArray): List<Int>? {
        val work = avail.copyOf(); val fin = BooleanArray(alloc.size); val seq = ArrayList<Int>()
        var progress = true
        while (progress) { progress = false
            for (i in alloc.indices) if (!fin[i] && need[i].indices.all { need[i][it] <= work[it] }) { for (j in work.indices) work[j] += alloc[i][j]; fin[i] = true; seq += i; progress = true } }
        return if (fin.all { it }) seq else null
    }
    fun isValidSafeSequence(seq: List<Int>, alloc: Array<IntArray>, need: Array<IntArray>, avail: IntArray): Boolean {
        val work = avail.copyOf()
        for (i in seq) { if (need[i].indices.any { need[i][it] > work[it] }) return false; for (j in work.indices) work[j] += alloc[i][j] }
        return true
    }
}

object GenOs {
    private fun permutations(n: Int): List<List<Int>> { val out = ArrayList<List<Int>>(); fun rec(cur: List<Int>, rest: List<Int>) { if (rest.isEmpty()) out += cur else for (x in rest) rec(cur + x, rest - x) }; rec(emptyList(), (0 until n).toList()); return out }

    val templates: List<TemplateDef> = listOf(
        t("os-sched", "OS", "CPU scheduling", "Average waiting time under FCFS/SJF/RR/SRTF", "scheduling", "process", "cpu", "thread") { g ->
            val n = 4; val arr = IntArray(n) { g.between(0, 6) }; val burst = IntArray(n) { g.between(1, 9) }
            val algo = g.pick(listOf("FCFS", "SJF", "RR", "SRTF")); val q = g.between(2, 4)
            val w = when (algo) { "FCFS" -> OsSim.fcfsWait(arr, burst); "SJF" -> OsSim.sjfWait(arr, burst); "RR" -> OsSim.rrWait(arr, burst, q); else -> OsSim.srtfWait(arr, burst) }
            val name = when (algo) { "FCFS" -> "First-Come-First-Served"; "SJF" -> "non-preemptive Shortest Job First (ties by arrival, then id)"; "RR" -> "Round Robin with time quantum $q"; else -> "pre-emptive Shortest Remaining Time First" }
            val tbl = (0 until n).joinToString("; ") { "P${it + 1}(arrival ${arr[it]}, burst ${burst[it]})" }
            g.nat("Processes: $tbl. Using $name scheduling, what is the average waiting time? (2 decimals)${if (algo == "RR") " A process that arrives exactly when a quantum expires joins the queue before the pre-empted process." else ""}", w,
                "Simulate the schedule, computing waiting time = turnaround − burst for each process, then average: ${"%.2f".format(w)}.", Difficulty.MEDIUM, 2, 0.01)
        },
        t("os-page", "OS", "Virtual memory", "Page-replacement faults (FIFO/LRU/OPT)", "page", "virtual memory", "replacement", "thrash", "demand") { g ->
            val ref = List(g.between(11, 14)) { g.between(1, 5) }; val frames = g.between(3, 4); val pol = g.pick(listOf("FIFO", "LRU", "OPT"))
            val f = OsSim.pageFaults(ref, frames, pol)
            g.nat("A process references pages ${ref.joinToString(" ")} in this order and is allocated $frames initially empty frames. How many page faults occur under the $pol policy?", f.toDouble(),
                "Simulate $pol with $frames frames; faults = $f (initial fills count as faults).", Difficulty.MEDIUM, 2)
        },
        t("os-disk", "OS", "Disk scheduling", "Total head movement", "disk", "i/o", "scheduling", "storage") { g ->
            val reqs = (0..199).shuffled(g.rng).take(6); val head = g.between(20, 150); val pol = g.pick(listOf("FCFS", "SSTF", "SCAN"))
            val m = OsSim.diskMovement(reqs, head, pol, 199)
            g.nat("A disk has cylinders 0–199 and the head is initially at $head. The request queue is ${reqs.joinToString(", ")}. Under $pol${if (pol == "SCAN") " (head first moves toward higher cylinders, travels to cylinder 199, then reverses)" else ""}, what is the total head movement in cylinders?", m.toDouble(),
                "Follow the $pol service order and sum |Δcylinder| = $m.", Difficulty.MEDIUM, 2)
        },
        t("os-deadlock-min", "OS", "Deadlocks", "Minimum resources for deadlock freedom", "deadlock", "banker", "resource") { g ->
            val n = g.between(3, 8); val k = g.between(2, 5); val r = n * (k - 1) + 1
            g.nat("A system has $n processes, each needing at most $k units of a single resource type to complete. What is the minimum number of units that guarantees deadlock freedom?", r.toDouble(),
                "Worst case each process holds k−1 units: n(k−1) = ${n * (k - 1)} units can still deadlock; one more guarantees progress: $r.", Difficulty.MEDIUM, 2, tags = listOf("formula"))
        },
        t("os-banker", "OS", "Deadlocks", "Banker's algorithm safe sequence", "deadlock", "banker", "safe") { g ->
            val n = 4; val m = 2
            var tries = 0
            var result: Question? = null
            while (result == null) {
                if (tries++ > 300) throw IllegalStateException("no banker state")
                val alloc = Array(n) { IntArray(m) { g.between(0, 3) } }; val need = Array(n) { IntArray(m) { g.between(0, 4) } }; val avail = IntArray(m) { g.between(0, 3) }
                val safe = OsSim.safeSequence(alloc, need, avail) ?: continue
                val invalid = permutations(n).filter { !OsSim.isValidSafeSequence(it, alloc, need, avail) }
                if (invalid.size < 3) continue
                val chosen = invalid.shuffled(g.rng).take(3)
                fun s(l: List<Int>) = l.joinToString(" → ") { "P${it}" }
                val tbl = (0 until n).joinToString("; ") { "P$it: Allocation(${alloc[it].joinToString(",")}) Need(${need[it].joinToString(",")})" }
                result = g.mcq("A system with 2 resource types has Available = (${avail.joinToString(",")}) and: $tbl. Which of the following is a valid safe sequence?", s(safe),
                    chosen.map { Wrong(s(it), "some process in this order cannot obtain its Need from the resources available at that point") },
                    "Banker's safety algorithm: repeatedly pick a process whose Need ≤ Work, add its Allocation back to Work. One safe order is ${s(safe)}.", Difficulty.HARD, 2)
            }
            result
        },
        t("os-semaphore", "OS", "Synchronisation", "Counting semaphore value and blocked processes", "semaphore", "mutex", "monitor", "synchron", "critical") { g ->
            val s0 = g.between(1, 5); val p = s0 + g.between(2, 8); val v = g.between(0, 2); val value = s0 - p + v
            g.nat("A counting semaphore is initialised to $s0. Then $p wait (P) operations and $v signal (V) operations are executed, in an order such that no operation is lost. How many processes are blocked on the semaphore at the end?", (-value).toDouble(),
                "Final value = $s0 − $p + $v = $value; a negative value's magnitude is the number of blocked processes = ${-value}.", Difficulty.EASY)
        },
        t("os-paging", "OS", "Memory management", "Page-table size and effective access time with a TLB", "paging", "tlb", "memory management", "segment", "address") { g ->
            if (g.rng.nextBoolean()) {
                val h = g.pick(listOf(80, 90, 95, 98)); val tlb = g.pick(listOf(5, 10, 20)); val mem = g.pick(listOf(80, 100, 200)); val e = h / 100.0 * (tlb + mem) + (1 - h / 100.0) * (tlb + 2 * mem)
                g.nat("A single-level paging system has a TLB lookup time of $tlb ns, memory access time $mem ns and TLB hit ratio $h%. What is the effective memory access time in ns? (2 decimals)", e,
                    "EMAT = h(t + m) + (1−h)(t + 2m) = ${h / 100.0}($tlb+$mem) + ${1 - h / 100.0}($tlb+${2 * mem}) = ${"%.2f".format(e)}.", Difficulty.MEDIUM, 2, 0.01, tags = listOf("formula"))
            } else {
                val la = g.between(30, 40); val pg = g.between(10, 14); val entries = 1L shl (la - pg)
                g.nat("A system has a $la-bit logical address space and ${1 shl pg}-byte pages. How many entries does the single-level page table have?", entries.toDouble(), "Page number bits = $la − $pg = ${la - pg}, so 2^${la - pg} = $entries entries.", Difficulty.EASY)
            }
        },
        t("os-inode", "OS", "File systems", "Maximum file size with direct and indirect blocks", "file", "inode", "directory") { g ->
            val b = g.pick(listOf(512, 1024, 2048)); val p = g.pick(listOf(4, 8)); val d = g.pick(listOf(8, 10, 12)); val ppb = b / p
            val blocks = d.toLong() + ppb + ppb.toLong() * ppb; val kb = blocks * b / 1024.0
            g.nat("A UNIX-style inode has $d direct pointers, one single-indirect and one double-indirect pointer. Block size is $b bytes and each block pointer is $p bytes. What is the maximum file size in KB?", kb,
                "Pointers per block = $b/$p = $ppb. Blocks = $d + $ppb + $ppb² = $blocks. Size = $blocks × $b bytes = ${fmtNum(kb)} KB.", Difficulty.MEDIUM, 2, 0.001)
        }
    )
}
