package com.chandu.gatesystem.ai2027.domain.gen

import com.chandu.gatesystem.ai2027.domain.*

/**
 * Small hand-authored bank of ORIGINAL practice questions (verbal aptitude and a few conceptual items that
 * are not computable by a generator). They are NOT official GATE questions and carry source = BANK_ORIGINAL.
 * No previous-year (PYQ) questions are bundled: add verified PYQs through the import path with source + year.
 */
object StaticBank {
    private fun mcq(id: String, subject: String, topic: String, sub: String, stmt: String, opts: List<String>, key: Int, why: String, concept: String,
                    diff: Difficulty = Difficulty.EASY, marks: Int = 1, optWhy: List<String>? = null) = Question(
        id = "bank-$id", subjectId = subject, topicId = topic, subtopic = sub, statement = stmt, type = QType.MCQ, options = opts,
        correctOptions = setOf(key), nat = null, marks = marks, negativeMarks = Scorer.defaultNegative(QType.MCQ, marks), solution = why,
        optionExplanations = optWhy ?: opts.mapIndexed { i, _ -> if (i == key) "Correct. $why" else "Incorrect." }, concept = concept,
        difficulty = diff, source = QSource.BANK_ORIGINAL, validation = ValidationStatus.VALIDATED_SCHEMA)

    val questions: List<Question> = listOf(
        mcq("ga-sv1", "GA", "GA/verbal", "Subject–verb agreement", "Choose the grammatically correct sentence.",
            listOf("The list of items are on the desk.", "The list of items is on the desk.", "The lists of item is on the desk.", "The list of items were on the desk."), 1,
            "The subject is the singular noun 'list'; 'of items' is a prepositional phrase, so the verb is singular: 'is'.", "Subject–verb agreement"),
        mcq("ga-sv2", "GA", "GA/verbal", "Subject–verb agreement", "Choose the correct option: 'Neither the teacher nor the students ____ present.'",
            listOf("was", "were", "is", "has been"), 1, "With 'neither…nor' the verb agrees with the nearer subject, 'students' (plural) → 'were'.", "Subject–verb agreement"),
        mcq("ga-art1", "GA", "GA/verbal", "Articles", "Fill in the blank: 'She is ____ honest woman who works at ____ university.'",
            listOf("an, an", "a, a", "an, a", "the, an"), 0, "'honest' begins with a vowel sound (silent h) → 'an'; 'university' begins with a /j/ consonant sound → 'a'. The correct pair is 'an … a'.", "Articles", Difficulty.MEDIUM,
            optWhy = listOf("Incorrect: 'university' starts with a consonant sound, so it takes 'a'.", "Incorrect: 'honest' has a silent h, so it takes 'an'.", "Correct: 'an honest … a university'.", "Incorrect: no definite article is needed here.")),
        mcq("ga-tense1", "GA", "GA/verbal", "Tenses", "Choose the correct sentence.",
            listOf("He has went to the market yesterday.", "He went to the market yesterday.", "He has gone to the market yesterday.", "He is going to the market yesterday."), 1,
            "A definite past time word ('yesterday') requires the simple past: 'went'.", "Tenses"),
        mcq("ga-prep1", "GA", "GA/verbal", "Prepositions", "Fill in the blank: 'The committee is responsible ____ the final report.'",
            listOf("to", "for", "with", "at"), 1, "'Responsible for' is the standard collocation.", "Prepositions"),
        mcq("ga-syn1", "GA", "GA/verbal", "Vocabulary", "Choose the word closest in meaning to 'ephemeral'.",
            listOf("lasting", "short-lived", "ancient", "harmful"), 1, "'Ephemeral' means lasting for a very short time.", "Vocabulary"),
        mcq("ga-ant1", "GA", "GA/verbal", "Vocabulary", "Choose the antonym of 'candid'.",
            listOf("frank", "evasive", "honest", "open"), 1, "'Candid' means frank/open; the opposite is 'evasive'.", "Vocabulary"),
        mcq("ga-idiom1", "GA", "GA/verbal", "Idioms", "'To burn the midnight oil' means:",
            listOf("to waste resources", "to work late into the night", "to start a fire", "to sleep early"), 1, "The idiom means working or studying late at night.", "Idioms"),
        mcq("ga-conj1", "GA", "GA/verbal", "Conjunctions", "Fill in the blank: 'He worked hard, ____ he failed the test.'",
            listOf("so", "because", "yet", "or"), 2, "The two clauses contrast (hard work vs failure), so the contrasting conjunction 'yet' fits.", "Conjunctions"),
        mcq("ga-order1", "GA", "GA/verbal", "Sentence ordering", "Choose the most logical order of sentences: P. She opened the door. Q. The bell rang. R. A courier stood outside. S. She walked to the entrance.",
            listOf("QSPR", "PQRS", "QPSR", "SQPR"), 0, "The bell rings first (Q); she walks to the entrance (S); she opens the door (P); then she sees the courier (R).", "Sentence ordering", Difficulty.MEDIUM),
        mcq("toc-dec1", "TOC", "TOC/decidability", "Decidability", "Which of the following problems is undecidable?",
            listOf("Whether a DFA accepts a given string", "Whether two DFAs accept the same language", "Whether a Turing machine halts on a given input", "Whether a CFG generates the empty language"), 2,
            "The halting problem for Turing machines is undecidable; the others are decidable for DFAs/CFGs.", "Decidability", Difficulty.EASY),
        mcq("os-deadlock-cond", "OS", "OS/deadlock", "Deadlock conditions", "Which of the following is NOT one of the four necessary (Coffman) conditions for deadlock?",
            listOf("Mutual exclusion", "Hold and wait", "Preemption", "Circular wait"), 2, "The necessary condition is NO preemption, not preemption. Preemption would break deadlock.", "Coffman conditions", Difficulty.EASY),
        mcq("dbms-acid", "DBMS", "DBMS/transactions", "ACID", "Which ACID property ensures that once a transaction commits its effects survive system failure?",
            listOf("Atomicity", "Consistency", "Isolation", "Durability"), 3, "Durability guarantees committed changes persist, typically via logging and recovery.", "ACID properties", Difficulty.EASY),
        mcq("cn-osi", "CN", "CN/layering", "Layering", "In the TCP/IP model, which layer is responsible for end-to-end delivery between processes?",
            listOf("Link", "Internet", "Transport", "Application"), 2, "The transport layer (TCP/UDP) provides process-to-process communication.", "Layering principles", Difficulty.EASY),
        mcq("cd-phases", "CD", "CD/architecture", "Compiler phases", "Which phase of a compiler checks that an identifier is declared before use?",
            listOf("Lexical analysis", "Semantic analysis", "Code generation", "Peephole optimisation"), 1, "Declaration checks need symbol tables and are done during semantic analysis.", "Compiler front end", Difficulty.EASY)
    )
}
