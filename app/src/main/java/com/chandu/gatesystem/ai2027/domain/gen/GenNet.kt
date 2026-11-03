package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*

object DbSim {
    fun closure(attrs: Set<Char>, fds: List<Pair<Set<Char>, Set<Char>>>): Set<Char> {
        val c = attrs.toMutableSet(); var ch = true
        while (ch) { ch = false; for ((l, r) in fds) if (c.containsAll(l) && !c.containsAll(r)) { c += r; ch = true } }
        return c
    }
    fun subsets(all: List<Char>): List<Set<Char>> = (1 until (1 shl all.size)).map { m -> all.filterIndexed { i, _ -> m and (1 shl i) != 0 }.toSet() }
    fun candidateKeys(all: List<Char>, fds: List<Pair<Set<Char>, Set<Char>>>): List<Set<Char>> {
        val supers = subsets(all).filter { closure(it, fds).containsAll(all) }
        return supers.filter { s -> supers.none { it != s && s.containsAll(it) } }
    }
    /** 1 = 1NF only, 2 = 2NF, 3 = 3NF, 4 = BCNF (assuming atomic attributes). */
    fun normalForm(all: List<Char>, fds: List<Pair<Set<Char>, Set<Char>>>): Int {
        val keys = candidateKeys(all, fds); val prime = keys.flatten().toSet(); val nonPrime = all.toSet() - prime
        val nontrivial = fds.filter { !it.first.containsAll(it.second) }
        val isSuper = { x: Set<Char> -> closure(x, fds).containsAll(all) }
        if (nontrivial.all { isSuper(it.first) }) return 4
        if (nontrivial.all { isSuper(it.first) || (it.second - it.first).all { a -> a in prime } }) return 3
        // 2NF: no non-prime attribute depends on a proper subset of a candidate key
        for (k in keys) for (s in subsets(k.toList())) if (s != k) { val c = closure(s, fds); if (c.any { it in nonPrime && it !in s }) return 1 }
        return 2
    }
    fun joinCount(r: List<Pair<Int, Int>>, s: List<Pair<Int, Int>>) = r.sumOf { a -> s.count { it.first == a.second } }
    fun precedenceEdges(schedule: List<Triple<Int, Char, String>>): Set<Pair<Int, Int>> {
        val e = HashSet<Pair<Int, Int>>()
        for (i in schedule.indices) for (j in i + 1 until schedule.size) {
            val a = schedule[i]; val b = schedule[j]
            if (a.first != b.first && a.third == b.third && (a.second == 'W' || b.second == 'W')) e += a.first to b.first
        }
        return e
    }
}

object GenDbms {
    private fun fdText(f: Pair<Set<Char>, Set<Char>>) = f.first.sorted().joinToString("") + " → " + f.second.sorted().joinToString("")
    private fun randomFds(g: QGen, attrs: List<Char>): List<Pair<Set<Char>, Set<Char>>> {
        val out = LinkedHashSet<Pair<Set<Char>, Set<Char>>>()
        repeat(g.between(3, 5)) {
            val l = attrs.shuffled(g.rng).take(g.between(1, 2)).toSet(); val r = attrs.filter { it !in l }.shuffled(g.rng).take(1).toSet(); out += l to r }
        return out.toList()
    }
    val templates: List<TemplateDef> = listOf(
        t("dbms-closure", "DBMS", "Functional dependencies", "Attribute closure", "functional", "dependency", "closure", "lossless", "armstrong") { g ->
            val attrs = "ABCDE".toList(); val fds = randomFds(g, attrs); val x = attrs.shuffled(g.rng).take(g.between(1, 2)).toSet()
            val c = DbSim.closure(x, fds); fun s(z: Set<Char>) = z.sorted().joinToString("")
            val wrongs = LinkedHashSet<String>(); for (a in attrs) { if (a !in c) wrongs += s(c + a) else if (a !in x) wrongs += s(c - a) }; wrongs += s(x); wrongs += "ABCDE"
            g.mcq("Relation R(A,B,C,D,E) has functional dependencies: ${fds.joinToString(", ") { fdText(it) }}. What is the attribute closure (${s(x)})⁺?", s(c),
                wrongs.filter { it != s(c) }.take(5).map { Wrong(it, "does not equal the fixpoint of repeatedly applying the FDs to ${s(x)}") },
                "Start with ${s(x)} and repeatedly add the right side of any FD whose left side is contained: result ${s(c)}.", Difficulty.MEDIUM, 2, tags = listOf("formula"))
        },
        t("dbms-keys", "DBMS", "Functional dependencies", "Counting candidate keys", "key", "functional", "dependency", "candidate", "normal") { g ->
            val attrs = "ABCDE".toList(); val fds = randomFds(g, attrs); val keys = DbSim.candidateKeys(attrs, fds)
            g.nat("Relation R(A,B,C,D,E) has functional dependencies: ${fds.joinToString(", ") { fdText(it) }}. How many candidate keys does R have?", keys.size.toDouble(),
                "A candidate key is a minimal set whose closure is ABCDE. Keys found: ${keys.joinToString(", ") { it.sorted().joinToString("") }} → ${keys.size}.", Difficulty.MEDIUM, 2)
        },
        t("dbms-nf", "DBMS", "Normalisation", "Highest normal form of a relation", "normal", "bcnf", "3nf", "2nf", "normalis", "normaliz") { g ->
            val attrs = "ABCDE".toList(); val fds = randomFds(g, attrs); val nf = DbSim.normalForm(attrs, fds)
            val names = listOf("", "1NF but not 2NF", "2NF but not 3NF", "3NF but not BCNF", "BCNF")
            val keys = DbSim.candidateKeys(attrs, fds)
            g.mcq("Relation R(A,B,C,D,E) with atomic attributes has functional dependencies: ${fds.joinToString(", ") { fdText(it) }}. What is the highest normal form that R satisfies?", names[nf],
                (1..4).filter { it != nf }.map { Wrong(names[it], "check the key/prime-attribute conditions for each normal form") },
                "Candidate keys: ${keys.joinToString(", ") { it.sorted().joinToString("") }}. Check BCNF (every FD determinant is a superkey), then 3NF (or RHS prime), then 2NF (no partial dependency). Result: ${names[nf]}.", Difficulty.HARD, 2)
        },
        t("dbms-join", "DBMS", "Relational algebra", "Size of a natural join", "relational algebra", "join", "sql", "calculus", "select") { g ->
            val r = List(g.between(4, 6)) { g.between(1, 9) to g.between(1, 3) }; val s = List(g.between(4, 6)) { g.between(1, 3) to g.between(1, 9) }
            val n = DbSim.joinCount(r, s)
            g.nat("R(A,B) = {${r.joinToString(", ") { "(${it.first},${it.second})" }}} and S(B,C) = {${s.joinToString(", ") { "(${it.first},${it.second})" }}} (duplicates are distinct tuples). How many tuples are in the natural join R ⋈ S?", n.toDouble(),
                "Match R.B = S.B: for each R tuple count S tuples with the same B. Total = $n.", Difficulty.MEDIUM, 2)
        },
        t("dbms-bplus", "DBMS", "Indexing", "B+ tree order and capacity", "b+", "index", "tree", "file organi", "hashing") { g ->
            val block = g.pick(listOf(512, 1024, 2048, 4096)); val key = g.pick(listOf(4, 8, 10)); val ptr = g.pick(listOf(6, 8))
            val order = (block + key) / (ptr + key)
            if (g.rng.nextBoolean()) g.nat("In a B+ tree, an internal node occupies one $block-byte block and holds p tree pointers and p−1 search keys. Each key is $key bytes and each pointer is $ptr bytes. What is the maximum value of p (the order)?", order.toDouble(),
                "p·$ptr + (p−1)·$key ≤ $block → p ≤ ($block + $key)/($ptr + $key) = ${(block + key).toDouble() / (ptr + key)} → p = $order.", Difficulty.MEDIUM, 2, tags = listOf("formula"))
            else { val cap = order.toLong() * order * (order - 1)
                g.nat("A B+ tree has order p = $order (internal nodes hold up to p pointers; leaves hold up to p−1 keys). What is the maximum number of keys that a tree with exactly 3 levels (root, one internal level, leaves) can index?", cap.toDouble(),
                    "Leaves reachable = p·p = ${order.toLong() * order}; each holds up to p−1 = ${order - 1} keys → $cap.", Difficulty.MEDIUM, 2) }
        },
        t("dbms-serial", "DBMS", "Transactions", "Conflict serializability via the precedence graph", "transaction", "serializab", "concurrency", "schedule", "lock", "recovery") { g ->
            val items = listOf("X", "Y", "Z"); val ops = ArrayList<Triple<Int, Char, String>>()
            for (t0 in 1..3) repeat(g.between(2, 3)) { ops += Triple(t0, if (g.rng.nextBoolean()) 'R' else 'W', g.pick(items)) }
            // preserve each transaction's internal order while interleaving randomly
            ops.shuffle(g.rng)
            val e = DbSim.precedenceEdges(ops)
            val sched = ops.joinToString(" ") { "${it.second}${it.first}(${it.third})" }
            g.nat("Consider the schedule: $sched. How many edges does its precedence (conflict) graph have? (Two operations conflict if they are from different transactions on the same item and at least one is a write; count each ordered pair Ti→Tj once.)", e.size.toDouble(),
                "Scan every pair of conflicting operations in schedule order and add Ti→Tj. Edges: ${if (e.isEmpty()) "none" else e.sortedBy { it.first * 10 + it.second }.joinToString(", ") { "T${it.first}→T${it.second}" }} → ${e.size}.", Difficulty.MEDIUM, 2)
        }
    )
}

object GenCn {
    private fun ip(a: Long) = "${(a shr 24) and 255}.${(a shr 16) and 255}.${(a shr 8) and 255}.${a and 255}"
    fun crcRemainder(data: String, gen: String): String {
        val r = gen.length - 1; val bits = (data + "0".repeat(r)).toCharArray()
        for (i in 0..data.length - 1) if (bits[i] == '1') for (j in gen.indices) bits[i + j] = if (bits[i + j] == gen[j]) '0' else '1'
        return String(bits.copyOfRange(bits.size - r, bits.size))
    }
    val templates: List<TemplateDef> = listOf(
        t("cn-subnet", "CN", "IP addressing", "Hosts and subnets from CIDR prefixes", "ip", "cidr", "subnet", "ipv4", "nat", "layering") { g ->
            if (g.rng.nextBoolean()) { val p = g.between(20, 29); val h = Math.pow(2.0, (32 - p).toDouble()).toLong() - 2
                g.nat("How many usable host addresses are there in each subnet of a /$p IPv4 network?", h.toDouble(), "Host bits = 32 − $p = ${32 - p}; usable = 2^${32 - p} − 2 = $h (network and broadcast addresses excluded).", Difficulty.EASY, tags = listOf("formula")) }
            else { val a = g.between(16, 22); val b = a + g.between(2, 5); val n = 1L shl (b - a)
                g.nat("A /$a block is divided into equal /$b subnets. How many subnets are obtained?", n.toDouble(), "Extra prefix bits = $b − $a = ${b - a}; subnets = 2^${b - a} = $n.", Difficulty.EASY) }
        },
        t("cn-broadcast", "CN", "IP addressing", "Network and broadcast address of a host", "ip", "cidr", "subnet", "ipv4") { g ->
            val p = g.between(24, 29); val host = (192L shl 24) or (168L shl 16) or (g.between(0, 255).toLong() shl 8) or g.between(1, 254).toLong()
            val mask = (0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL; val net = host and mask; val bc = net or (mask.inv() and 0xFFFFFFFFL)
            g.mcq("A host has the IPv4 address ${ip(host)}/$p. What is the broadcast address of its subnet?", ip(bc),
                listOf(Wrong(ip(net), "that is the network address (host bits all 0)"), Wrong(ip(bc + 1), "that is the first address of the NEXT subnet"), Wrong(ip(bc - 1), "that is the last usable host, not the broadcast address"), Wrong(ip(host or 255), "assumes a /24 boundary")),
                "Mask = /$p; network = ${ip(net)}; broadcast sets all ${32 - p} host bits to 1 = ${ip(bc)}.", Difficulty.MEDIUM, 2)
        },
        t("cn-flow", "CN", "Flow control", "Stop-and-wait utilisation and sliding-window size", "bandwidth", "delay", "throughput", "performance", "flow", "sliding", "link") { g ->
            val rateMbps = g.pick(listOf(1, 2, 10)); val frame = g.pick(listOf(1000, 2000, 5000)); val km = g.pick(listOf(100, 200, 400, 600))
            val tt = frame.toDouble() / (rateMbps * 1e6) * 1000; val tp = km * 1000.0 / 2e8 * 1000 // ms
            if (g.rng.nextBoolean()) { val u = tt / (tt + 2 * tp) * 100
                g.nat("A stop-and-wait link has bandwidth $rateMbps Mbps, frame size $frame bits, distance $km km and propagation speed 2×10⁸ m/s (ACK transmission time is negligible). What is the link utilisation in percent? (2 decimals)", u,
                    "Tt = $frame/${rateMbps}e6 = ${fmtNum(tt)} ms; Tp = $km km / 2e8 = ${fmtNum(tp)} ms; U = Tt/(Tt+2Tp) = ${"%.2f".format(u)}%.", Difficulty.MEDIUM, 2, 0.05, tags = listOf("formula")) }
            else { val w = Math.ceil(1 + 2 * tp / tt).toInt()
                g.nat("A sliding-window protocol runs over a $rateMbps Mbps link with $frame-bit frames, distance $km km and propagation speed 2×10⁸ m/s. What is the minimum window size (in frames) needed to keep the link 100% utilised?", w.toDouble(),
                    "a = Tp/Tt = ${fmtNum(tp / tt)}; window = ⌈1 + 2a⌉ = $w.", Difficulty.MEDIUM, 2, tags = listOf("formula")) }
        },
        t("cn-tcp", "CN", "Transport layer", "TCP slow start and congestion avoidance", "tcp", "congestion", "socket", "transport", "udp") { g ->
            val ss = g.pick(listOf(8, 16, 32)); val k = g.between(4, 9); var cwnd = 1
            repeat(k) { cwnd = if (cwnd < ss) minOf(cwnd * 2, ss) else cwnd + 1 }
            g.nat("A TCP sender starts with cwnd = 1 MSS and ssthresh = $ss MSS. No losses occur. Assuming cwnd doubles each RTT in slow start (capped at ssthresh) and then grows by 1 MSS per RTT in congestion avoidance, what is cwnd (in MSS) after $k RTTs?", cwnd.toDouble(),
                "Slow start: 1,2,4,… up to $ss, then +1 per RTT. After $k RTTs cwnd = $cwnd.", Difficulty.MEDIUM, 2)
        },
        t("cn-csma", "CN", "Data link layer", "Minimum frame size in CSMA/CD", "ethernet", "mac", "medium access", "csma", "data link", "lan") { g ->
            val b = g.pick(listOf(10, 100)); val d = g.pick(listOf(1000, 2000, 2500)); val bits = 2.0 * d / 2e8 * b * 1e6
            g.nat("A CSMA/CD network runs at $b Mbps with a maximum span of $d m and signal speed 2×10⁸ m/s. What is the minimum frame size (in bits) so that a sender can detect collisions?", bits, "Frame transmission time ≥ 2·Tp = 2·$d/2e8 = ${fmtNum(2.0 * d / 2e8 * 1e6)} µs; bits = 2Tp × $b Mbps = ${fmtNum(bits)}.", Difficulty.MEDIUM, 2, tags = listOf("formula"))
        },
        t("cn-routing", "CN", "Routing", "Least-cost path (link-state routing)", "routing", "distance vector", "link state", "bellman", "ospf", "rip") { g ->
            val n = 5; val es = randomGraph(g, n); val d = dijkstra(n, es, 0)
            g.nat("A network has routers R0…R${n - 1} with links (cost): ${es.joinToString(", ") { "R${it.u}–R${it.v} (${it.w})" }}. Using link-state routing, what is the least cost from R0 to R${n - 1}?", d[n - 1].toDouble(),
                "Each router runs Dijkstra on the full topology; distances from R0 = [${d.joinToString(", ")}]; to R${n - 1}: ${d[n - 1]}.", Difficulty.MEDIUM, 2)
        },
        t("cn-errors", "CN", "Error detection", "CRC remainder and Hamming check bits", "error", "crc", "hamming", "framing", "checksum") { g ->
            if (g.rng.nextBoolean()) {
                val gen = g.pick(listOf("1011", "1101", "10011")); val data = String(CharArray(g.between(7, 10)) { if (g.rng.nextBoolean()) '1' else '0' }).let { "1" + it }
                val rem = crcRemainder(data, gen); val r = gen.length - 1
                val others = (0 until (1 shl r)).map { Integer.toBinaryString(it).padStart(r, '0') }.filter { it != rem }.shuffled(g.rng).take(3)
                g.mcq("A sender uses CRC with generator polynomial bits $gen on the data word $data. What are the $r CRC bits appended to the data?", rem, others.map { Wrong(it, "does not match the remainder of the modulo-2 division of data·2^$r by $gen") },
                    "Append $r zeros: $data${"0".repeat(r)}, then XOR-divide by $gen. The remainder is $rem.", Difficulty.MEDIUM, 2)
            } else {
                val m = g.between(4, 64); var r = 1; while ((1 shl r) < m + r + 1) r++
                g.nat("How many check bits are needed in a single-error-correcting Hamming code for $m data bits?", r.toDouble(), "Smallest r with 2^r ≥ m + r + 1: for m=$m, r = $r.", Difficulty.EASY, tags = listOf("formula"))
            }
        },
        t("cn-switching", "CN", "Switching", "Store-and-forward packet switching delay", "switching", "packet", "circuit", "virtual", "layering", "principles") { g ->
            val hops = g.between(2, 5); val pkt = g.pick(listOf(1000, 2000)); val npk = g.between(4, 12); val rate = g.pick(listOf(1, 2, 10))
            val ms = (hops + npk - 1) * (pkt / (rate * 1e6)) * 1000
            g.nat("A file of ${npk * pkt} bits is sent in $npk packets of $pkt bits over a path with $hops links, each of rate $rate Mbps. Using store-and-forward packet switching and ignoring propagation, queuing and header overhead, how long (ms) does it take for the entire file to reach the destination? (2 decimals)", ms,
                "Transmission time per packet per link = $pkt/${rate}e6 = ${fmtNum(pkt / (rate * 1e6) * 1000)} ms. Total = (hops + packets − 1) × that = ($hops + $npk − 1) × ... = ${"%.2f".format(ms)} ms.", Difficulty.MEDIUM, 2, 0.01)
        },
        t("cn-fragment", "CN", "Network layer", "IPv4 fragmentation", "ipv4", "fragment", "nat", "network layer") { g ->
            val total = g.between(1500, 8000); val mtu = g.pick(listOf(576, 1000, 1500)); val payloadPer = ((mtu - 20) / 8) * 8; val data = total - 20
            val frags = (data + payloadPer - 1) / payloadPer
            g.nat("An IPv4 datagram of total length $total bytes (20-byte header, no options) must cross a link with MTU $mtu bytes. How many fragments are produced?", frags.toDouble(),
                "Data = $total − 20 = $data bytes. Fragment payload must be a multiple of 8: ⌊($mtu−20)/8⌋×8 = $payloadPer. Fragments = ⌈$data/$payloadPer⌉ = $frags.", Difficulty.MEDIUM, 2)
        }
    )
}
