package com.chandu.gatesystem.training

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private val Bg = Color(0xFF03070D)
private val Panel = Color(0xFF071521)
private val Cyan = Color(0xFF63D8FF)
private val Green = Color(0xFF68F5B0)
private val Red = Color(0xFFFF6B7A)
private val Muted = Color(0xFF91A1AF)

internal enum class TrainingSet { PRACTICE, REVISION, EXAM }
internal enum class QType { MCQ, MSQ, NAT }
internal data class TrainingQuestion(
    val id: String,
    val subject: String,
    val topic: String,
    val difficulty: String,
    val type: QType,
    val prompt: String,
    val options: List<String> = emptyList(),
    val answers: Set<String>,
    val explanation: String,
    val marks: Int = 1
)

/** Small, explicitly labeled starter bank. Expand with verified PYQs and reviewed original questions. */
private val starterQuestions = listOf(
    TrainingQuestion("dl_kmap_01", "Digital Logic", "Boolean Algebra", "Moderate", QType.MCQ,
        "For Boolean variables A and B, which expression is equivalent to A + AB?",
        listOf("A", "B", "AB", "A + B"), setOf("A"),
        "Absorption law: A + AB = A(1 + B) = A. The term AB is already covered whenever A = 1."),
    TrainingQuestion("ds_stack_01", "Data Structures & Algorithms", "Stacks", "Moderate", QType.MCQ,
        "A stack initially empty receives push(4), push(7), push(9), pop(), push(2), pop(). What is the next value popped?",
        listOf("4", "7", "9", "2"), setOf("7"),
        "The first pop removes 9. Pushing 2 then popping removes 2. The remaining top is 7."),
    TrainingQuestion("os_rr_01", "Operating Systems", "CPU Scheduling", "Hard", QType.NAT,
        "Three processes arrive at time 0 with CPU bursts P1=5, P2=3, P3=1. Round Robin quantum is 2. Ignoring context-switch overhead, what is the completion time of P2? Enter an integer.",
        answers = setOf("8"), explanation = "Timeline: P1 runs 0–2 (remaining 3), P2 runs 2–4 (remaining 1), P3 runs 4–5 and completes, P1 runs 5–7 (remaining 1), P2 runs 7–8 and completes. Therefore P2 completion time is 8."),
    TrainingQuestion("dbms_keys_01", "DBMS", "Functional Dependencies", "Moderate", QType.MCQ,
        "Relation R(A,B,C) has functional dependencies A → B and B → C. Which statement must be true?",
        listOf("A → C", "C → A", "B → A", "A and C are independent"), setOf("A → C"),
        "By transitivity of functional dependencies, A → B and B → C imply A → C."),
    TrainingQuestion("toc_dfa_01", "Theory of Computation", "Finite Automata", "Hard", QType.MCQ,
        "Which language over {0,1} is regular?",
        listOf("{0ⁿ1ⁿ | n ≥ 0}", "{ww | w ∈ {0,1}*}", "All strings containing an even number of 1s", "{0ⁿ1ⁿ2ⁿ | n ≥ 0}"),
        setOf("All strings containing an even number of 1s"),
        "A DFA can track the parity of the number of 1s using two states. The other listed languages are non-regular."),
    TrainingQuestion("math_sets_01", "Discrete Mathematics", "Sets and Counting", "Moderate", QType.NAT,
        "A set has 5 elements. How many subsets does it have, including the empty set? Enter an integer.",
        answers = setOf("32"), explanation = "Each of the 5 elements is either included or excluded, giving 2⁵ = 32 subsets."),
    TrainingQuestion("math_prob_01", "Probability and Statistics", "Conditional Probability", "Hard", QType.NAT,
        "A fair die is rolled once. Given that the outcome is even, what is the probability that it is greater than 3? Enter a decimal such as 0.6667.",
        answers = setOf("0.6667"), explanation = "Given an even result, the possible outcomes are {2,4,6}. Outcomes greater than 3 are {4,6}, so the probability is 2/3 ≈ 0.6667. Numerical tolerance is 0.001."),
    TrainingQuestion("math_la_01", "Linear Algebra", "Matrices", "Moderate", QType.MCQ,
        "A square matrix A is invertible if and only if which condition holds?",
        listOf("det(A) = 0", "det(A) ≠ 0", "Every entry is nonzero", "A is symmetric"), setOf("det(A) ≠ 0"),
        "For a square matrix, nonzero determinant is equivalent to invertibility."),
    TrainingQuestion("ga_logic_01", "General Aptitude", "Logical Reasoning", "Moderate", QType.MCQ,
        "All coders are problem-solvers. Some students are coders. Which conclusion necessarily follows?",
        listOf("All students are problem-solvers", "Some students are problem-solvers", "No problem-solvers are students", "All problem-solvers are coders"),
        setOf("Some students are problem-solvers"),
        "The students who are coders must be problem-solvers, so at least some students are problem-solvers."),
    TrainingQuestion("cn_ip_01", "Computer Networks", "IPv4", "Hard", QType.NAT,
        "A /27 IPv4 subnet contains how many usable host addresses under conventional IPv4 subnetting? Enter an integer.",
        answers = setOf("30"), explanation = "A /27 leaves 32−27 = 5 host bits, giving 2⁵ = 32 addresses. Subtract network and broadcast addresses: 30 usable hosts."),
    TrainingQuestion("coa_cache_01", "Computer Organization & Architecture", "Cache Memory", "Hard", QType.MCQ,
        "A cache has a hit time of 1 ns, miss rate 5%, and miss penalty of 40 ns (additional time after a miss). What is the average access time?",
        listOf("1 ns", "2 ns", "3 ns", "41 ns"), setOf("3 ns"),
        "AMAT = hit time + miss rate × miss penalty = 1 + 0.05×40 = 3 ns."),
    TrainingQuestion("compiler_first_01", "Compiler Design", "Parsing", "Moderate", QType.MCQ,
        "In a predictive LL(1) parser, what does the first L represent?",
        listOf("Left-to-right input scan", "Rightmost derivation", "Leftmost derivation", "One-symbol lookahead"),
        setOf("Left-to-right input scan"),
        "LL(1): scan input Left-to-right, construct a Leftmost derivation, with 1 lookahead symbol.")
)

private fun normalizeNat(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
private fun checkAnswer(q: TrainingQuestion, user: Set<String>): Boolean {
    if (q.type == QType.NAT) {
        val actual = user.firstOrNull()?.let(::normalizeNat) ?: return false
        val expected = q.answers.firstOrNull()?.let(::normalizeNat) ?: return false
        val tolerance = if (q.answers.firstOrNull() == "0.6667") 0.001 else 1e-6
        return abs(actual - expected) <= tolerance
    }
    return user == q.answers
}

@Composable
fun TrainingScreen(prefs: SharedPreferences) {
    var selectedSet by remember { mutableStateOf(TrainingSet.PRACTICE) }
    var selectedSubject by remember { mutableStateOf("All subjects") }
    var questionIndex by remember { mutableIntStateOf(0) }
    var selectedOptions by remember { mutableStateOf(setOf<String>()) }
    var natAnswer by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var lastCorrect by remember { mutableStateOf<Boolean?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val wrongIds = remember(refresh) { runCatching { JSONArray(prefs.getString("training_wrong_ids", "[]")) }.getOrDefault(JSONArray()).let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } }
    val allSubjects = remember { listOf("All subjects") + starterQuestions.map { it.subject }.distinct() }
    val pool = remember(selectedSet, selectedSubject, refresh) {
        val base = when (selectedSet) {
            TrainingSet.PRACTICE -> starterQuestions
            TrainingSet.REVISION -> starterQuestions.filter { it.id in wrongIds }
            TrainingSet.EXAM -> starterQuestions.filter { it.difficulty == "Hard" }
        }
        base.filter { selectedSubject == "All subjects" || it.subject == selectedSubject }
    }
    val safeIndex = if (pool.isEmpty()) 0 else questionIndex.coerceIn(0, pool.lastIndex)
    val q = pool.getOrNull(safeIndex)
    val attempts = prefs.getInt("training_attempts", 0)
    val correctCount = prefs.getInt("training_correct", 0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TRAINING ENGINE", color = Cyan, fontSize = 21.sp, fontWeight = FontWeight.Black)
        Text("OFFLINE QUESTION PRACTICE • STARTER BANK", color = Muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            SetButton("PRACTICE", selectedSet == TrainingSet.PRACTICE, Modifier.weight(1f)) { selectedSet = TrainingSet.PRACTICE; questionIndex = 0; resetAnswer { selectedOptions = it; natAnswer = ""; submitted = false; lastCorrect = null } }
            SetButton("REVISION", selectedSet == TrainingSet.REVISION, Modifier.weight(1f)) { selectedSet = TrainingSet.REVISION; questionIndex = 0; resetAnswer { selectedOptions = it; natAnswer = ""; submitted = false; lastCorrect = null } }
            SetButton("EXAM", selectedSet == TrainingSet.EXAM, Modifier.weight(1f)) { selectedSet = TrainingSet.EXAM; questionIndex = 0; resetAnswer { selectedOptions = it; natAnswer = ""; submitted = false; lastCorrect = null } }
        }
        Text("Subject filter", color = Muted, fontSize = 12.sp)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedSubject, color = Color.White); Spacer(Modifier.weight(1f)); Text("▾", color = Cyan) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                allSubjects.forEach { subject -> DropdownMenuItem(text = { Text(subject) }, onClick = { selectedSubject = subject; questionIndex = 0; selectedOptions = emptySet(); natAnswer = ""; submitted = false; lastCorrect = null; expanded = false }) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniStat("ATTEMPTS", attempts.toString())
            MiniStat("CORRECT", correctCount.toString())
            MiniStat("ACCURACY", if (attempts == 0) "—" else "${(100 * correctCount / attempts)}%")
        }
        if (q == null) {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (selectedSet == TrainingSet.REVISION) "REVISION QUEUE CLEAR" else "NO QUESTIONS IN THIS FILTER", color = Green, fontWeight = FontWeight.Bold)
                Text(if (selectedSet == TrainingSet.REVISION) "No incorrect questions are currently saved. Attempt Practice Set questions; incorrect answers will be added here." else "Choose another subject or set. This is a small starter bank, not the complete GATE question bank.", color = Muted)
            }
        } else {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${safeIndex + 1} / ${pool.size}   •   ${q.subject}", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${q.topic}  |  ${q.difficulty}  |  ${q.type.name}  |  ${q.marks} mark${if (q.marks > 1) "s" else ""}", color = Muted, fontSize = 11.sp)
                Text(q.prompt, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (q.type == QType.NAT) {
                    OutlinedTextField(value = natAnswer, onValueChange = { if (!submitted) natAnswer = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Your numerical answer") }, enabled = !submitted, singleLine = true)
                } else {
                    q.options.forEachIndexed { index, option ->
                        val key = option
                        val checked = key in selectedOptions
                        Row(Modifier.fillMaxWidth().background(if (checked) Cyan.copy(alpha = .12f) else Bg, RoundedCornerShape(8.dp)).clickable(enabled = !submitted) {
                            selectedOptions = if (q.type == QType.MCQ) setOf(key) else if (checked) selectedOptions - key else selectedOptions + key
                        }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (q.type == QType.MCQ) RadioButton(selected = checked, onClick = { if (!submitted) selectedOptions = setOf(key) }, enabled = !submitted) else Checkbox(checked = checked, onCheckedChange = { selectedOptions = if (it) selectedOptions + key else selectedOptions - key }, enabled = !submitted)
                            Text("${('A'.code + index).toChar()}. $option", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                if (!submitted) {
                    Button(onClick = {
                        val user = if (q.type == QType.NAT) setOf(natAnswer.trim()) else selectedOptions
                        if (user.isNotEmpty() && user.any { it.isNotBlank() }) {
                            val correct = checkAnswer(q, user)
                            prefs.edit().putInt("training_attempts", prefs.getInt("training_attempts", 0) + 1).putInt("training_correct", prefs.getInt("training_correct", 0) + if (correct) 1 else 0).apply()
                            val currentWrong = runCatching { JSONArray(prefs.getString("training_wrong_ids", "[]")) }.getOrDefault(JSONArray())
                            val ids = (0 until currentWrong.length()).map { currentWrong.optString(it) }.toMutableSet()
                            if (correct) ids.remove(q.id) else ids.add(q.id)
                            prefs.edit().putString("training_wrong_ids", JSONArray(ids.toList()).toString()).apply()
                            lastCorrect = correct
                            submitted = true
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("SUBMIT ANSWER") }
                } else {
                    Text(if (lastCorrect == true) "✓ CORRECT" else "✗ NOT QUITE — ADDED TO REVISION", color = if (lastCorrect == true) Green else Red, fontWeight = FontWeight.Black)
                    Text("Correct answer: ${q.answers.joinToString(" / ")}", color = Cyan, fontSize = 13.sp)
                    Text(q.explanation, color = Color.White, fontSize = 13.sp)
                    Button(onClick = {
                        if (pool.isNotEmpty()) questionIndex = (safeIndex + 1) % pool.size
                        selectedOptions = emptySet(); natAnswer = ""; submitted = false; lastCorrect = null; refresh++
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (safeIndex == pool.lastIndex) "RESTART SET" else "NEXT QUESTION →") }
                }
            }
        }
        Text("Starter content only: original demonstration questions, not official PYQs. The Exam tab currently filters harder starter questions; timed exams, GATE scoring and the expanded verified question bank are later milestones.", color = Muted, fontSize = 10.sp)
    }
}

@Composable private fun SetButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = if (selected) Cyan.copy(alpha = .22f) else Panel)) {
        Text(label, color = if (selected) Cyan else Muted, fontSize = 10.sp)
    }
}
@Composable private fun MiniStat(label: String, value: String) {
    Column(Modifier.background(Panel, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Cyan, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, color = Muted, fontSize = 9.sp)
    }
}
private inline fun resetAnswer(reset: (Set<String>) -> Unit) { reset(emptySet()) }
