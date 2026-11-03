package com.chandu.gatesystem.ai2027.domain

enum class LessonSource(val label: String) {
    CURATED("Authored offline lesson"), AI("AI-generated lesson (verify against a textbook)"), SKELETON("Study checklist only — no authored lesson text yet")
}

data class Formula(val name: String, val formula: String, val meaning: String)
data class Example(val problem: String, val steps: List<String>)

/** Structured lesson content shown by the Topic Learning screen. */
data class Lesson(
    val topicId: String, val title: String, val definition: String, val intuition: String,
    val concepts: List<String> = emptyList(), val formulas: List<Formula> = emptyList(), val examples: List<Example> = emptyList(),
    val traps: List<String> = emptyList(), val methods: List<String> = emptyList(), val complexity: String = "",
    val shortcuts: List<String> = emptyList(), val practice: List<String> = emptyList(), val related: List<String> = emptyList(),
    val source: LessonSource = LessonSource.CURATED
)

object LessonCodec {
    fun toJson(l: Lesson): String = Json.stringify(linkedMapOf(
        "definition" to l.definition, "intuition" to l.intuition, "concepts" to l.concepts,
        "formulas" to l.formulas.map { linkedMapOf("name" to it.name, "formula" to it.formula, "meaning" to it.meaning) },
        "examples" to l.examples.map { linkedMapOf("problem" to it.problem, "steps" to it.steps) },
        "traps" to l.traps, "methods" to l.methods, "complexity" to l.complexity, "shortcuts" to l.shortcuts,
        "practice" to l.practice, "related" to l.related))

    private fun strings(m: Map<String, Any?>, k: String) = (m.list(k) ?: emptyList()).mapNotNull { it as? String }

    /** Tolerant parser: also accepts the backend/AI shape. Returns null when there is no usable text. */
    fun fromJson(json: String, topicId: String, title: String, source: LessonSource): Lesson? {
        val m = runCatching { Json.parseObject(json) }.getOrNull() ?: return null
        val def = m.str("definition")?.trim().orEmpty()
        val intuition = m.str("intuition")?.trim().orEmpty()
        if (def.isEmpty() && intuition.isEmpty()) return null
        @Suppress("UNCHECKED_CAST")
        fun maps(k: String) = (m.list(k) ?: emptyList()).mapNotNull { it as? Map<String, Any?> }
        return Lesson(
            topicId, title, def, intuition, strings(m, "concepts"),
            maps("formulas").map { Formula(it.str("name") ?: "", it.str("formula") ?: "", it.str("meaning") ?: "") },
            maps("examples").map { Example(it.str("problem") ?: "", strings(it, "steps")) },
            strings(m, "traps"), strings(m, "methods"), m.str("complexity") ?: "", strings(m, "shortcuts"),
            strings(m, "practice"), strings(m, "related"), source
        )
    }

    fun skeleton(topicId: String, title: String, subjectName: String) = Lesson(
        topicId, title,
        definition = "No offline lesson text has been authored for this topic yet. This is a study checklist for \"$title\" ($subjectName).",
        intuition = "Use your standard GATE text/notes for the theory, or tap 'Fetch AI lesson' (needs the backend) for a generated outline — AI lessons must be checked against a trusted source.",
        concepts = listOf("List every definition used in \"$title\" and write each in your own words.", "Identify the 3 standard question patterns GATE asks on this topic."),
        traps = listOf("Do not rely on memorised results without knowing their conditions.", "Re-check units, boundary cases and off-by-one counts."),
        methods = listOf("Work 2 small examples by hand before attempting the Preparation Set."),
        practice = listOf("Open the Preparation Set for this topic: questions are generated offline with computed answers."),
        source = LessonSource.SKELETON
    )
}

/** Lessons written offline. Everything else gets a SKELETON (honestly labelled) or an on-demand AI lesson. */
object LessonBank {
    private fun L(subject: String, title: String, lesson: Lesson) = Topics.id(subject, title) to lesson.copy(topicId = Topics.id(subject, title), title = title)

    val curated: Map<String, Lesson> = mapOf(
        L("EM", "Discrete logic: propositions, connectives, truth tables, tautology/contradiction", Lesson("", "",
            "A proposition is a declarative statement that is either true or false. Connectives (¬, ∧, ∨, →, ↔) build compound propositions whose truth value is fixed by a truth table.",
            "Think of each connective as a circuit gate: ∧ is AND, ∨ is OR. Implication p→q is false in exactly one row: p true, q false.",
            listOf("n variables give 2^n rows in a truth table.", "Tautology: true in every row. Contradiction: false in every row. Contingency: neither.", "p→q ≡ ¬p ∨ q ≡ ¬q → ¬p (contrapositive).", "p↔q is true when p and q have the same value."),
            listOf(Formula("Implication", "p → q ≡ ¬p ∨ q", "Rewrite → before simplifying"), Formula("De Morgan", "¬(p ∧ q) ≡ ¬p ∨ ¬q;  ¬(p ∨ q) ≡ ¬p ∧ ¬q", "Push negation inwards"), Formula("Biconditional", "p ↔ q ≡ (p→q) ∧ (q→p)", "")),
            listOf(Example("Is (p → q) ∨ (q → p) a tautology?", listOf("Rewrite: (¬p ∨ q) ∨ (¬q ∨ p).", "Group: (¬p ∨ p) ∨ (q ∨ ¬q) = T ∨ T = T.", "Yes, it is a tautology."))),
            listOf("p→q is TRUE when p is false (vacuous truth).", "The converse q→p is not equivalent to p→q; the contrapositive is."),
            listOf("Build the table only for ≤3 variables; otherwise simplify algebraically.", "To test a tautology, try to make the formula false: if impossible it is a tautology."),
            "", listOf("To falsify p→q you need exactly p=T, q=F: use this to test validity quickly."),
            listOf("Count rows where (p→q)∧(q→r) is true.", "Negate: 'for every x there is y' — what is the negation?"), listOf("Predicate logic", "Normal forms"))),
        L("TOC", "Alphabets, strings, languages and regular expressions", Lesson("", "",
            "An alphabet Σ is a finite non-empty set of symbols; a string is a finite sequence over Σ; a language is any set of strings (a subset of Σ*). Regular expressions describe exactly the regular languages.",
            "A regular expression is a compact recipe for building strings using union (+), concatenation and star (*).",
            listOf("|Σ^n| = |Σ|^n strings of length n; |Σ*| is countably infinite.", "ε is the empty string, length 0; ∅ is the empty language and differs from {ε}.", "Concatenation of languages: L1·L2 = {xy | x∈L1, y∈L2}.", "L* = ∪ L^k (k ≥ 0) always contains ε."),
            listOf(Formula("Identities", "(a+b)* = (a*b*)* = (a*+b*)*", "Different forms of the same language"), Formula("Empty set", "∅* = {ε},  ∅·L = ∅,  ε·L = L", "Frequent trick questions"), Formula("Kleene closure", "(r*)* = r*,  r*r* = r*", "")),
            listOf(Example("How many strings of length ≤ 3 are over {0,1}?", listOf("1 + 2 + 4 + 8 = 15 (include ε)."))),
            listOf("∅ ≠ {ε}: the language {ε} has one string, ∅ has none.", "L* always includes ε even if ε ∉ L."), listOf("To describe a language, find the pattern of the last/first symbols and build around it."),
            "", listOf("Strings ending in 01 over {0,1}: (0+1)*01."), listOf("Write a regex for strings with an even number of 0s.", "Is (ab)* = a*b*?"), listOf("DFA construction", "Pumping lemma"))),
        L("CN", "Layering principles; OSI and TCP/IP models", Lesson("", "",
            "Layering splits a network protocol suite into stacked functions; each layer uses the service of the layer below and provides a service to the layer above. OSI has 7 layers; TCP/IP has 4 (or 5 in teaching models).",
            "Each layer wraps the data from above with its own header (encapsulation) and unwraps at the receiver.",
            listOf("OSI (top to bottom): Application, Presentation, Session, Transport, Network, Data link, Physical.", "TCP/IP: Application, Transport, Internet, Link.", "Units: segment (transport), packet/datagram (network), frame (data link), bits (physical).", "Router works up to the network layer; switch/bridge to data link; hub at physical."),
            emptyList(), listOf(Example("Which layer provides process-to-process delivery?", listOf("Ports identify processes.", "Ports belong to the transport layer."))),
            listOf("Mixing up which device operates at which layer.", "The Internet layer of TCP/IP corresponds to OSI Network layer, not Transport."), listOf("Remember the PDU names in order from top: data → segment → packet → frame → bits."),
            "", emptyList(), listOf("Name the header added at each layer for a web request."), listOf("IP addressing", "Transport protocols"))),
        L("CD", "Compiler architecture: front end, middle end, back end", Lesson("", "",
            "A compiler translates a source program into target code in phases: lexical analysis, syntax analysis, semantic analysis, intermediate code generation, optimisation and code generation. The front end is source-language dependent; the back end is machine dependent.",
            "Front end understands the language; middle end improves the intermediate form; back end maps it to a machine.",
            listOf("Analysis (front end): lexer → parser → semantic analyser → intermediate code.", "Synthesis (back end): optimiser → code generator.", "A symbol table is used by all phases; the error handler too.", "A pass reads the program once; phases may be grouped into one pass."),
            emptyList(), listOf(Example("Which phase detects an undeclared variable?", listOf("It is a context-sensitive property.", "Detected in semantic analysis, not in parsing."))),
            listOf("Syntax (CFG) cannot express declaration-before-use; semantic analysis does."), emptyList(), "", emptyList(), emptyList(), listOf("Lexical analysis", "Parsing"))),
        L("C", "C data types, operators, precedence and expressions", Lesson("", "",
            "C has integer, floating and pointer types with implementation-defined sizes (commonly int = 4 bytes, char = 1). Expression value depends on operator precedence, associativity and usual arithmetic conversions.",
            "Precedence says who binds tighter; associativity says the direction among equals; evaluation order of operands is usually unspecified.",
            listOf("Integer division truncates toward zero: -7/2 = -3, -7%2 = -1.", "sizeof is a compile-time operator; i++ returns the old value.", "Relational and logical operators return 0 or 1.", "Unsigned arithmetic wraps modulo 2^n."),
            listOf(Formula("Precedence (high→low)", "() [] -> . ; ! ~ ++ -- unary* & sizeof ; * / % ; + - ; << >> ; < <= > >= ; == != ; & ; ^ ; | ; && ; || ; ?: ; = ; ,", "Memorise for tracing questions")),
            listOf(Example("int a=5; int b = a++ + ++a; (unspecified!)", listOf("Modifying a twice without a sequence point is undefined behaviour.", "GATE avoids such code unless it asks 'undefined'."))),
            listOf("= vs == in conditions.", "Integer promotion: char+char becomes int.", "Dividing two ints then assigning to float loses the fraction."), listOf("Rewrite with explicit parentheses before evaluating."), "", emptyList(), emptyList(), listOf("Pointers", "Storage classes"))),
        L("ALGO", "Asymptotic analysis: O, Omega, Theta; best/average/worst cases", Lesson("", "",
            "Asymptotic notation compares growth rates: f = O(g) means f grows no faster than g, Ω is a lower bound and Θ is a tight bound. Best/average/worst case describe input choices, not notations.",
            "Ignore constants and lower-order terms; compare the dominant term as n → ∞.",
            listOf("Order: 1 < log n < n < n log n < n² < n³ < 2ⁿ < n! < nⁿ.", "f=Θ(g) iff f=O(g) and f=Ω(g).", "log_a n = Θ(log_b n): the base does not matter.", "2^(n+1) = Θ(2ⁿ) but 2^(2n) ≠ O(2ⁿ)."),
            listOf(Formula("Limit test", "lim f(n)/g(n) = 0 ⇒ f=o(g);  = c>0 ⇒ f=Θ(g);  = ∞ ⇒ f=ω(g)", "")),
            listOf(Example("Is n log n = O(n²)?", listOf("n log n / n² = (log n)/n → 0.", "Yes, even o(n²)."))),
            listOf("Confusing worst case with big-O: both are independent ideas.", "log n! = Θ(n log n) (Stirling)."), emptyList(), "", emptyList(), emptyList(), listOf("Recurrences"))
        )
    )

    fun lessonFor(topicId: String, title: String, subjectName: String): Lesson = curated[topicId] ?: LessonCodec.skeleton(topicId, title, subjectName)
}
