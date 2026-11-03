package com.chandu.gatesystem.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DailyQuest(
    val day: Int,
    val phase: String,
    val title: String,
    val primarySubject: String,
    val primaryTopic: String,
    val secondarySubject: String,
    val secondaryTopic: String,
    val difficulty: String,
    val sets: Int,
    val primaryQuestions: Int,
    val secondaryQuestions: Int,
    val gaQuestions: Int,
    val rnr: String,
    val pyq: String,
    val ga: GaSet
)

/**
 * General Aptitude combines 4 chapters — Verbal, Quantitative, Analytical & Logical
 * Reasoning, and Spatial Aptitude — every single day. Instead of a flat 40-question
 * label, the full GA syllabus is split into 3 balanced slots (slot 1/2/3) and those
 * 3 slots repeat on a 3-day cycle for the whole 150-day plan: Day 1, 4, 7, 10… all
 * get slot 1; Day 2, 5, 8, 11… get slot 2; Day 3, 6, 9, 12… get slot 3. That
 * repeating 3-day block is the "147 rule" applied to GA — the same slot resurfaces
 * every 3rd day (1st, 4th, 7th…) so every chapter gets revisited on a short cycle
 * across all 150 days instead of being studied once and forgotten.
 *
 * IMPORTANT: GA is its OWN single objective (see gaQuestions below). The
 * "secondary" slot in DailyQuest is a completely separate thing — Core Recall of
 * DSA / Digital Logic / DBMS / COA / Operating Systems (see coreRecallFor) — so GA
 * never appears twice in the same day the way it did in older versions of this plan.
 */
data class GaSet(val cycleSlot: Int, val verbal: String, val quantitative: String, val analytical: String, val spatial: String)

object GatePlan {
    const val START_DATE_TEXT = "2026-09-05"
    val START_DATE: LocalDate = LocalDate.parse(START_DATE_TEXT)

    // ── V7.1 "Day 21" baseline ──────────────────────────────────────────────
    // Days 1–20 are already completed and are never re-shown as "current" on a
    // fresh install. A fresh install (or a full RESET) starts the player at
    // Day 21 with the stats that day 20 of progress would have produced:
    // Level 9 / Rank C, 4000 XP (next level at 4500), 2000 Gold, Gate Power 550
    // (= 4000/10 + 6 objectives × 25), with today's quest already cleared.
    const val BASELINE_DAY = 21
    const val BASELINE_XP = 4000
    const val BASELINE_GOLD = 2000
    const val BASELINE_DONE = 6

    /** Baseline objectives-done count for [day]; only Day 21 gets the seeded value. */
    fun baselineDoneFor(day: Int): Int = if (day == BASELINE_DAY) BASELINE_DONE else 0

    /** Baseline "quest cleared" flag for [day]; only Day 21 starts pre-cleared. */
    fun baselineClearedFor(day: Int): Boolean = day == BASELINE_DAY

    // ── DAYS 1–20 "AWAKENING" — unchanged, already completed, never touched ──
    private data class MixedDay(val primary: String, val primaryTopic: String, val secondary: String, val secondaryTopic: String)

    private val mixed = listOf(
        MixedDay("Data Structures & Algorithms", "Arrays, Linked Lists, Stacks & Queues", "DBMS", "ER Model & Relational Model"),
        MixedDay("Data Structures & Algorithms", "Trees, BST, Heaps & Traversals", "Operating Systems", "Processes, Threads & Context Switch"),
        MixedDay("Digital Logic", "Boolean Algebra, Gates & K-Maps", "COA", "DLX Architecture & Instruction Set"),
        MixedDay("COA", "Pipelining, Hazards & Cache", "Data Structures & Algorithms", "Sorting, Searching & Complexity"),
        MixedDay("DBMS", "Normalization, Functional Dependencies & Design", "Operating Systems", "CPU Scheduling"),
        MixedDay("Operating Systems", "Concurrency, Semaphores & Deadlock", "Digital Logic", "Combinational Circuits"),
        MixedDay("Data Structures & Algorithms", "Graphs, BFS/DFS & Shortest Path", "DBMS", "SQL, Set Operations & Indexing"),
        MixedDay("DBMS", "Transactions & Concurrency Control", "COA", "Memory Hierarchy & Cache"),
        MixedDay("COA", "I/O, DMA, Interrupts & Synchronization", "Operating Systems", "Paging, TLB & Page Replacement"),
        MixedDay("Operating Systems", "Virtual Memory, EAT & Thrashing", "Data Structures & Algorithms", "Hashing, Trees & B-Trees"),
        MixedDay("Digital Logic", "Sequential Circuits, Flip-Flops & Counters", "COA", "ALU, CPU Control & Functional Units"),
        MixedDay("Data Structures & Algorithms", "Divide & Conquer, Sorting & Searching", "DBMS", "File Organization, B-Trees & Indexing"),
        MixedDay("DBMS", "ER Model, Relational Algebra & SQL", "Digital Logic", "Number Systems & Floating Point"),
        MixedDay("COA", "Pipelining, Hazards, Stalls & Memory", "Operating Systems", "File Systems & Disk Scheduling"),
        MixedDay("Operating Systems", "Processes, IPC & CPU Scheduling", "Data Structures & Algorithms", "Graph Traversal & Spanning Trees"),
        MixedDay("Data Structures & Algorithms", "Algorithm Analysis: O, Ω, Θ & Cases", "COA", "Memory System & Cache"),
        MixedDay("DBMS", "Normalization & Transactions", "Operating Systems", "Deadlock & Memory Management"),
        MixedDay("Digital Logic", "K-Map Minimization & FSM Design", "Data Structures & Algorithms", "BST, Heaps & Priority Queues"),
        MixedDay("COA", "DLX Data Types, Instructions & Implementation", "DBMS", "SQL & Relational Model"),
        MixedDay("Data Structures & Algorithms", "Full Mixed DSA Practice", "Digital Logic", "Full Mixed Digital Logic Practice")
    )

    // ── DAYS 21–40 "C + MATH" — C Programming (real learning subject, not just
    // "DSA practice") interleaved with Engineering Mathematics ────────────────
    private val cTopics = listOf(
        "C-1 Basics — Structure of a C program, Variables, Constants, Data Types, Operators, Type Conversion, printf/scanf",
        "C-2 Control Flow — if / if-else / nested conditions, switch, for, while, do-while, break, continue",
        "C-3 Functions — Declaration, Definition, Parameters, Return Values, Call by Value, Recursion, Scope, Storage Classes",
        "C-4 Arrays & Strings — 1D/2D/Multidimensional Arrays, Character Arrays, String Functions, Array/String Problems",
        "C-5 Pointers — Address & Dereferencing, Pointer Arithmetic, Pointer+Arrays, Pointer+Strings, Pointer+Functions, Pointer-to-Pointer",
        "C-6 Structures & Unions — struct, nested structures, arrays of structures, pointers to structures, union, enum, typedef",
        "C-7 Dynamic Memory — malloc, calloc, realloc, free, Dynamic Arrays, Dynamic Structures",
        "C-8 GATE C Concepts — Operator Precedence & Associativity, Evaluation Order, Side Effects, ++/--, Bitwise Operators, sizeof, Function Pointers",
        "C-9 GATE C Problem Solving — Output Prediction, Pointer Tracing, Recursion Tracing, Array/String Tracing, Mixed C PYQs",
        "C — Full Mixed Revision + Timed PYQ Set"
    )

    private val mathTopics = listOf(
        "Linear Algebra — Vector spaces, subspaces & spanning sets",
        "Linear Algebra — Matrices, determinants, minors & cofactors",
        "Linear Algebra — System of linear equations, Rank, Cramer's rule & matrix properties",
        "Linear Algebra — Eigenvalues, eigenvectors & Cayley-Hamilton theorem",
        "Linear Algebra — LU Decomposition",
        "Calculus — Functions, limits, continuity & differentiability",
        "Calculus — Derivatives, partial derivatives, Maxima/Minima & mean value theorems",
        "Calculus — Taylor series & standard integrations",
        "Calculus — Definite, multiple & triple integrals",
        "Calculus — Change of order, differentials & applications",
        "Calculus — Line, surface, volume integrals & Fourier series",
        "Probability — Basic probability, sampling & conditional probability",
        "Probability — Discrete random variables & dependent events",
        "Probability — Total probability & Bayes' theorem",
        "Probability & Statistics — Central tendency, dispersion & random variables",
        "Distributions — Bernoulli, Binomial, Poisson, Uniform, Normal & Exponential distributions",
        "Statistics — Correlation & regression",
        "Discrete Math — Propositional & first-order logic, statements, connectives, WFF",
        "Discrete Math — Set Theory: laws, Venn diagrams, Cartesian products & relations",
        "Discrete Math — Functions, relations & partial orders / lattices",
        "Discrete Math — Algebra: binary operations, Monoids, semigroups & Groups",
        "Discrete Math — Residue classes & Boolean algebra",
        "Discrete Math — Graph Theory: connectivity, matching & colouring",
        "Discrete Math — Combinatorics: counting, recurrence relations & generating functions",
        "Engineering Mathematics — Full Mixed Revision + Timed PYQ Set"
    )
    private val mathChunks = chunkTopics(mathTopics, 10)

    // ── DAYS 41–60 "ALGORITHMS + TOC" — Algorithms gets its own identity,
    // split out from DSA, because it's the weaker area; TOC starts here ──────
    private val algoTopics = listOf(
        "A-1 Algorithm Analysis — Time/Space Complexity, Big-O/Ω/Θ, Best/Avg/Worst Case, Recurrence Relations & Solving",
        "A-2 Searching — Linear Search, Binary Search, Recursive Binary Search & Complexity",
        "A-3 Sorting — Bubble, Selection, Insertion, Merge, Quick, Heap & Counting Sort; Stability & In-Place Sorting",
        "A-4 Divide & Conquer — Basic concept, Merge Sort, Quick Sort, Binary Search, Recurrence Analysis",
        "A-5 Greedy Algorithms — Activity Selection, Fractional Knapsack, Job Sequencing, Huffman Coding, MST intro",
        "A-6 Graph Algorithms — BFS, DFS, Connected Components, Cycle Detection, Topological Sort, Dijkstra, Bellman-Ford, Floyd-Warshall, Prim, Kruskal",
        "A-7 Dynamic Programming — Overlapping Subproblems, Optimal Substructure, 0/1 Knapsack, LCS, Matrix-Chain Multiplication, Coin Change, LIS",
        "A-8 Complexity / Advanced — P, NP, NP-Hard, NP-Complete, Reductions, Decision vs Optimization Problems",
        "A-9 GATE Algorithm Practice — Dry Runs, Complexity Questions, Recurrence Questions, Graph/Sorting/DP Problems",
        "Algorithms — Full Mixed Revision + Timed PYQ Set"
    )

    private val tocTopics = listOf(
        "Language concepts, sets, relations, functions & language operations",
        "Regular languages and regular expressions",
        "DFA construction and accepted languages",
        "NFA construction and accepted languages",
        "ε-NFA and ε-closure",
        "DFA/NFA conversions and equivalence",
        "Regular language properties and mixed problems",
        "Finite automata problem solving",
        "Pumping-style reasoning for regular languages",
        "CFL concepts and context-free grammars",
        "Grammar derivations, ambiguity and parse trees",
        "Pushdown automata — design and accepted languages",
        "PDA and CFG relationships",
        "CFL mixed practice",
        "Turing machine model and grammars",
        "Combining Turing machines",
        "Multitape Turing machines",
        "Nondeterministic Turing machines",
        "Universal Turing machines",
        "FSM with output — Moore and Mealy concepts",
        "Undecidability concepts and reductions",
        "TOC mixed practice and timed sets",
        "TOC PYQ-focused practice",
        "TOC checkpoint test and error review"
    )
    // TOC is split across two phases: first half here (41–60, paired with Algorithms),
    // second half in the 61–78 phase (paired with Compiler Design).
    private val tocChunk1 = chunkTopics(tocTopics.subList(0, 12), 10)
    private val tocChunk2 = chunkTopics(tocTopics.subList(12, 24), 9)

    // ── DAYS 61–78 "TOC + COMPILER" ───────────────────────────────────────────
    private val compilerTopics = listOf(
        "Compiler structure and phases",
        "Lexical analysis and tokens",
        "Regular expressions in lexical analysis",
        "Syntax analysis and context-free grammars",
        "Ambiguity and parse trees",
        "Semantic analysis",
        "Parsing methods and top-down parsing",
        "LR parsing and LR items",
        "SLR parsing and conflicts",
        "Parse tables and grammar restrictions",
        "Syntax-directed definitions",
        "Syntax trees and translation",
        "L-attributed definitions",
        "Intermediate code and instruction selection",
        "Target machine, optimization, data flow, liveness & common subexpressions"
    )
    private val compilerChunk = chunkTopics(compilerTopics, 9)

    // ── DAYS 79–100 "CN + ALGORITHMS" — Computer Networks aligned to the
    // official GATE 2027 wording (Principles of Layering; performance metrics;
    // distance vector & link state routing; IPv4 fragmentation/CIDR/NAT; TCP
    // flow & congestion control; socket API; DNS; HTTP). ARP, DHCP, ICMP, UDP,
    // SMTP/FTP/email and plain OSI-layer questions are de-emphasized for 2027. ──
    private val cnTopics = listOf(
        "Principles of Layering — why layered protocol stacks exist, encapsulation",
        "Switching — circuit, packet & virtual-circuit switching + performance metrics",
        "LAN, Ethernet and WLAN basics",
        "Data link layer — error detection (parity, CRC, checksums)",
        "Medium Access Control (MAC) protocols",
        "Distance vector routing",
        "Link state routing",
        "IPv4 — Fragmentation",
        "IPv4 — CIDR Notation",
        "Network Address Translation (NAT)",
        "TCP — flow control",
        "TCP — congestion control",
        "Socket API — call sequence & usage",
        "DNS — Domain Name System",
        "HTTP — request/response model",
        "CN mixed practice — layering, switching & routing",
        "CN mixed practice — IPv4, NAT & TCP",
        "CN PYQ-focused practice",
        "CN checkpoint test and error review"
    )
    private val cnChunk = chunkTopics(cnTopics, 11)

    // ── DAYS 101–115 "ENGINEERING MATHEMATICS MASTER" — revision + PYQs over
    // everything learned in the C+Math phase ──────────────────────────────────
    private val mathMasterTopics = listOf(
        "Linear Algebra master revision + PYQs",
        "Calculus master revision + PYQs",
        "Probability master revision + PYQs",
        "Statistics master revision + PYQs",
        "Discrete Logic master revision + PYQs",
        "Sets, Relations & Functions master revision",
        "Algebra, Lattices & Boolean Algebra master revision",
        "Graph Theory & Combinatorics master revision",
        "Engineering Mathematics mixed timed set",
        "Engineering Mathematics checkpoint test + analysis"
    )

    // ── DAYS 116–125 "CORE RECALL DUNGEON" — DSA / OS / DBMS / Digital Logic /
    // COA come back strongly with a dedicated memory-revival block ───────────
    private val coreDungeonTopics = listOf(
        "DSA + Digital Logic — Mixed Recall",
        "Operating Systems + DBMS — Mixed Recall",
        "COA + DSA — Mixed Recall",
        "Digital Logic + Operating Systems — Mixed Recall",
        "DBMS + COA — Mixed Recall",
        "DSA + Operating Systems — Mixed Recall",
        "DBMS + Digital Logic — Mixed Recall",
        "COA + DSA — Mixed Recall",
        "Operating Systems + DBMS — Mixed Recall",
        "ALL FIVE — Mixed Core Test"
    )
    // Kept from earlier versions as a secondary/backup revision pool (unused by
    // topicFor directly, but still available if you want an alternate Core
    // Recall Dungeon rotation).
    private val coreMasterTopics = listOf(
        "Digital Logic master revision",
        "COA master revision",
        "DSA master revision",
        "Operating Systems master revision",
        "DBMS master revision",
        "TOC master revision",
        "Compiler master revision",
        "CN master revision",
        "Core mixed timed set",
        "Core checkpoint test + error analysis"
    )

    // ── DAYS 126–135 "PYQ DUNGEON" ────────────────────────────────────────────
    private val pyqDungeonTopics = listOf(
        "GATE PYQs — C + DSA",
        "GATE PYQs — Algorithms",
        "GATE PYQs — DBMS + Operating Systems",
        "GATE PYQs — COA + Digital Logic",
        "GATE PYQs — Theory of Computation",
        "GATE PYQs — Compiler Design",
        "GATE PYQs — Computer Networks",
        "GATE PYQs — Engineering Mathematics",
        "GATE PYQs — Mixed CS",
        "Full 65-Question Timed Mock Test"
    )

    // ── DAYS 136–150 "FINAL BOSS" ─────────────────────────────────────────────
    private val finalBossTopics = listOf(
        "Full CS Mock Test 1",
        "Mock Test 1 — Error Monster analysis",
        "Full CS Mock Test 2",
        "Mock Test 2 — Error Monster analysis",
        "Full CS Mock Test 3",
        "Mock Test 3 — Error Monster analysis",
        "Weak-area repair — C + Algorithms",
        "Weak-area repair — TOC + Compiler",
        "Weak-area repair — CN + Engineering Mathematics",
        "Full CS Mock Test 4",
        "Mock Test 4 — analysis",
        "Full CS Mock Test 5",
        "Final high-error PYQ revision",
        "Final 85+ readiness test",
        "FINAL BOSS — light revision + formula/concept recall"
    )

    // ── CORE RECALL — separate rotating recall of DSA / Digital Logic / DBMS /
    // COA / Operating Systems. This is the "secondary" objective for every day
    // from Day 21 onward (except the PYQ Dungeon and Core Recall Dungeon, which
    // already carry their own dedicated secondary text). It is intentionally
    // NOT General Aptitude, so GA is never shown twice in the same day. ──────
    private val coreRecallSubjects = listOf(
        "Data Structures & Algorithms",
        "Digital Logic",
        "Database Management Systems",
        "Computer Organization & Architecture",
        "Operating Systems"
    )
    private val dsaRecallTopics = listOf(
        "Arrays, Linked Lists, Stacks & Queues",
        "Trees, BST, Heaps & Traversals",
        "Sorting, Searching & Complexity",
        "Graphs, BFS/DFS & Shortest Path",
        "Divide & Conquer, Sorting & Searching",
        "Hashing, Trees & B-Trees",
        "Algorithm Analysis: O, Ω, Θ & Cases",
        "Graph Traversal & Spanning Trees",
        "BST, Heaps & Priority Queues",
        "Full Mixed DSA Practice"
    )
    private val dlRecallTopics = listOf(
        "Boolean Algebra, Gates & K-Maps",
        "Combinational Circuits",
        "Sequential Circuits, Flip-Flops & Counters",
        "Number Systems & Floating Point",
        "K-Map Minimization & FSM Design",
        "Tabular (Quine–McCluskey) Minimization Method",
        "Full Mixed Digital Logic Practice"
    )
    private val dbmsRecallTopics = listOf(
        "ER Model & Relational Model",
        "Normalization, Functional Dependencies & Design",
        "SQL, Set Operations & Indexing",
        "Transactions & Concurrency Control",
        "File Organization, B-Trees & Indexing",
        "ER Model, Relational Algebra & SQL",
        "Normalization & Transactions",
        "SQL & Relational Model"
    )
    private val coaRecallTopics = listOf(
        "Instruction Set & Addressing Modes",
        "Pipelining, Hazards & Cache",
        "Memory Hierarchy & Cache",
        "I/O, DMA, Interrupts & Synchronization",
        "Design of ALU",
        "Control Unit Design — Hardwired vs Microprogrammed",
        "Memory Interfacing & Cache Mapping Performance",
        "Pipelining, Hazards, Stalls & Memory",
        "DLX Data Types, Instructions & Implementation"
    )
    private val osRecallTopics = listOf(
        "Processes, Threads & Context Switch",
        "System Calls & Inter-Process Communication",
        "CPU Scheduling",
        "Concurrency, Semaphores & Deadlock",
        "Paging, TLB & Page Replacement",
        "Virtual Memory, EAT & Thrashing",
        "File Systems & Disk Scheduling",
        "Processes, IPC & CPU Scheduling",
        "Deadlock & Memory Management"
    )
    private val coreRecallPools = mapOf(
        "Data Structures & Algorithms" to dsaRecallTopics,
        "Digital Logic" to dlRecallTopics,
        "Database Management Systems" to dbmsRecallTopics,
        "Computer Organization & Architecture" to coaRecallTopics,
        "Operating Systems" to osRecallTopics
    )

    /** Splits [topics] into exactly [groups] chunks (as equal as possible), joining
     * each chunk's items with " + " so every source topic is still covered even
     * when the phase has fewer days than the syllabus has fine-grained topics. */
    private fun chunkTopics(topics: List<String>, groups: Int): List<String> {
        if (groups <= 0 || topics.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val n = topics.size
        val base = n / groups
        val extra = n % groups
        var idx = 0
        for (g in 0 until groups) {
            val size = (base + if (g < extra) 1 else 0).coerceAtLeast(1)
            val end = (idx + size).coerceAtMost(n)
            result.add(topics.subList(idx, end).joinToString(" + "))
            idx = end
        }
        return result
    }

    /** Core Recall rotation used as the "secondary" objective for Day 21 onward.
     * A 5-day cycle picks the subject (DSA → Digital Logic → DBMS → COA → OS),
     * and a slower-moving index picks which topic of that subject to revise, so
     * the same subject shows different content each time it comes back around. */
    private fun coreRecallFor(day: Int): Pair<String, String> {
        val offset = (day - 21).coerceAtLeast(0)
        val subjectIdx = offset.mod(5)
        val subject = coreRecallSubjects[subjectIdx]
        val pool = coreRecallPools.getValue(subject)
        val variation = (offset / 5).mod(pool.size)
        return subject to "Core Recall — ${pool[variation]} (10–15 questions)"
    }

    // ── GENERAL APTITUDE — 147-rule / 3-day repeating cycle ──────────────────
    // Each list holds exactly 3 slots. Slot index = (day - 1) % 3.
    private val gaVerbalCycle = listOf(
        "Grammar — Articles ('A'/'An'/'The'), Nouns, Pronouns & Parts of Speech errors",
        "Grammar — Prepositions, Conjunctions, Subject-Verb Agreement, Tenses, Gerund & Infinitive, Adjectives/Adverbs + Sentence Completion strategies",
        "Synonyms, Antonyms & Vocabulary — Idioms & Phrases, Word Analogy, Odd Word Out, Reading Comprehension vocabulary"
    )
    private val gaQuantCycle = listOf(
        "Numbers, Algebra & Data Interpretation + Percentage, SI/CI, Profit & Loss, Partnership, Stocks & Shares",
        "Speed-Time-Work (Boats & Streams, Races, Pipes & Cisterns) + Ratio, Proportion, Variation & Mixtures/Alligations",
        "Permutations-Combinations, Elementary Statistics & Probability + Linear/Quadratic Equations, Geometry & Mensuration, Powers, Exponents & Logarithms"
    )
    private val gaAnalyticalCycle = listOf(
        "Verbal Reasoning, Analogy, Classification, Coding-Decoding, Blood Relations, Direction Sense, Logical Venn Diagrams",
        "Puzzle Test, Alphabetical Quibble, Number/Ranking/Time Sequence, Mathematical Operations, Logical Sequence of Words, Decision Making, Data Sufficiency",
        "Cubes, Arrangements, Clocks, Calendars, Deductions & mixed Logical Reasoning practice"
    )
    private val gaSpatialCycle = listOf(
        "Transformations & Geometrical Transformations",
        "Paper Folding and Cutting",
        "Patterns in 2D & 3D Dimensions + Shape Matching (2D & 3D)"
    )

    fun gaFor(day: Int): GaSet {
        val slot = (day - 1).mod(3)
        return GaSet(slot + 1, gaVerbalCycle[slot], gaQuantCycle[slot], gaAnalyticalCycle[slot], gaSpatialCycle[slot])
    }

    private fun topicFor(day: Int): Pair<String, String> {
        return when (day) {
            in 21..40 -> {
                val offset = day - 21
                if (offset % 2 == 0) "C Programming" to cTopics[offset / 2]
                else "Engineering Mathematics" to mathChunks[offset / 2]
            }
            in 41..60 -> {
                val offset = day - 41
                if (offset % 2 == 0) "Algorithms" to algoTopics[offset / 2]
                else "Theory of Computation" to tocChunk1[offset / 2]
            }
            in 61..78 -> {
                val offset = day - 61
                if (offset % 2 == 0) "Theory of Computation" to tocChunk2[offset / 2]
                else "Compiler Design" to compilerChunk[offset / 2]
            }
            in 79..100 -> {
                val offset = day - 79
                if (offset % 2 == 0) "Computer Networks" to cnChunk[offset / 2]
                else "Algorithms" to (algoTopics[(offset / 2) % algoTopics.size] + " (Recall)")
            }
            in 101..115 -> "Engineering Mathematics" to mathMasterTopics[(day - 101) % mathMasterTopics.size]
            in 116..125 -> "Core Recall Dungeon" to coreDungeonTopics[day - 116]
            in 126..135 -> "PYQ Dungeon" to pyqDungeonTopics[day - 126]
            else -> "Final Boss" to finalBossTopics[(day - 136).coerceIn(0, finalBossTopics.size - 1)]
        }
    }

    fun forDay(day: Int): DailyQuest {
        val d = day.coerceIn(1, 150)
        val phase: String
        val title: String
        val primary: String
        val primaryTopic: String
        val secondary: String
        val secondaryTopic: String
        val sets: Int
        val primaryQ: Int
        val secondaryQ: Int
        val gaQ: Int
        val difficulty: String

        if (d <= 20) {
            val t = mixed[d - 1]
            val s = when { d <= 7 -> 2; d <= 14 -> 3; else -> 4 }
            phase = "AWAKENING"
            title = "MIXED PRACTICE"
            primary = t.primary
            primaryTopic = t.primaryTopic
            secondary = t.secondary
            secondaryTopic = t.secondaryTopic
            sets = s
            primaryQ = 25 * s
            secondaryQ = 20 * s
            gaQ = 20 * s
            difficulty = when { d <= 7 -> "E"; d <= 14 -> "D"; else -> "C" }
        } else {
            val p = topicFor(d)
            phase = when (d) {
                in 21..40 -> "C + MATH"
                in 41..60 -> "ALGORITHMS + TOC"
                in 61..78 -> "TOC + COMPILER"
                in 79..100 -> "CN + ALGORITHMS"
                in 101..115 -> "MATH MASTER"
                in 116..125 -> "CORE RECALL DUNGEON"
                in 126..135 -> "PYQ DUNGEON"
                else -> "FINAL BOSS"
            }
            title = p.second
            primary = p.first
            primaryTopic = p.second

            if (d in 126..135) {
                secondary = "Error Monsters"
                secondaryTopic = "Re-solve 5 previously wrong questions from this day"
            } else {
                val core = coreRecallFor(d)
                secondary = core.first
                secondaryTopic = core.second
            }

            sets = 1
            primaryQ = if (d >= 136) 65 else 40
            secondaryQ = if (d in 126..135) 5 else 12
            gaQ = if (d in 126..135) 0 else 20
            difficulty = when {
                d >= 136 -> "S"
                d >= 126 -> "A"
                else -> "B"
            }
        }

        val prev = if (d == 1) "No previous day — create your first error notes" else "Day ${d - 1}"
        val rnr = when {
            d <= 20 -> "Recall ${prev} → Note mistakes/formulas → Re-solve 5 wrong/marked questions"
            d in 126..135 -> "Recall yesterday's PYQ set → Note weak points → Re-solve 5 marked questions"
            else -> {
                val recallA = (d - 3).coerceAtLeast(1)
                val recallB = (d - 6).coerceAtLeast(1)
                "147 Recall (C / Algorithms / TOC / Compiler / CN / Math only) → Recall Day $recallA & Day $recallB topics → Note weak points → Re-solve 5 marked questions"
            }
        }
        val pyq = when {
            d <= 20 -> "Mark at least 5 difficult questions for later PYQ/error review"
            d in 126..150 -> "PYQ / mock work is the main quest"
            else -> "Solve 10 GATE-style/PYQ questions after learning the topic"
        }
        return DailyQuest(d, phase, title, primary, primaryTopic, secondary, secondaryTopic, difficulty, sets, primaryQ, secondaryQ, gaQ, rnr, pyq, gaFor(d))
    }

    fun currentDay(today: LocalDate = LocalDate.now()): Int {
        return (ChronoUnit.DAYS.between(START_DATE, today) + 1).toInt().coerceIn(1, 150)
    }

    fun workoutFor(date: LocalDate = LocalDate.now()): String {
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY, DayOfWeek.THURSDAY -> "CHEST + SHOULDERS + TRICEPS"
            DayOfWeek.TUESDAY, DayOfWeek.FRIDAY -> "BACK + BICEPS + SHOULDERS + FOREARMS"
            DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY -> "LEGS + ABS"
            DayOfWeek.SUNDAY -> "REST / ACTIVE RECOVERY"
        }
    }

    fun workoutExercises(date: LocalDate = LocalDate.now()): List<String> {
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY, DayOfWeek.THURSDAY -> listOf("Push-ups — 3 × 10–15", "Dumbbell/Bench Press — 3 × 10", "Shoulder Press — 3 × 10", "Lateral Raises — 3 × 12", "Triceps Extensions — 3 × 12")
            DayOfWeek.TUESDAY, DayOfWeek.FRIDAY -> listOf("Rows — 3 × 10", "Lat Pulldown/Pull-ups — 3 × 8–12", "Biceps Curls — 3 × 12", "Hammer Curls — 3 × 12", "Forearm Curls — 3 × 15")
            DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY -> listOf("Squats — 3 × 12", "Lunges — 3 × 10 each leg", "Calf Raises — 3 × 15", "Leg Raises — 3 × 12", "Crunches — 3 × 15", "Plank — 3 × 30–45 sec")
            DayOfWeek.SUNDAY -> listOf("10–15 min easy walk", "Light stretching — 5–10 min", "No hard workout — recover")
        }
    }
}
