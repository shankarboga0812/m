package com.chandu.gatesystem.ai2027.ui.screens
import com.chandu.gatesystem.ai2027.data.toState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chandu.gatesystem.ai2027.data.db.PracticeSessionEntity
import com.chandu.gatesystem.ai2027.data.repo.*
import com.chandu.gatesystem.ai2027.data.toDomain
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.ui.LocalContainer
import com.chandu.gatesystem.ai2027.ui.Routes
import com.chandu.gatesystem.ai2027.ui.components.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter
import com.chandu.gatesystem.ai2027.ui.vm.*
import kotlinx.coroutines.launch

@Composable
private fun AiToggle(use: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalContainer.current
    val cfg = c.settings.aiConfig()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = use && cfg.configured, onCheckedChange = onChange, enabled = cfg.configured)
        Spacer(Modifier.width(8.dp))
        Column { Body("Include AI-generated questions"); Dim(if (cfg.configured) "Used for ~40% of the set; falls back to the offline bank if the backend is unreachable." else "Off — set the backend URL and token in AI Teacher settings. Offline bank is used.") }
    }
}

@Composable
private fun ResumeCard(s: PracticeSessionEntity, onResume: () -> Unit, onReport: () -> Unit) {
    val st = s.toState()
    SystemPanel(if (st.isComplete) "Completed set" else "Unfinished set", accent = if (st.isComplete) Hunter.Green else Hunter.Orange) {
        Body(s.title); Dim("Batches submitted: ${st.submittedBatches} / ${st.batchCount}. Answers in the open batch are saved.")
        if (st.isComplete) HunterButton("VIEW REPORT", onReport, outlined = true) else HunterButton("RESUME", onResume)
    }
}

/** Screens 8/GA launcher: Preparation Set (and the 5-question GA set). Resumes after the app was closed. */
@Composable
fun SetLauncherScreen(kind: String, day: Int, onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val pd = c.plan.plan.day(day) ?: return
    val isGa = kind == SetKinds.GA
    val topicId = if (isGa) Topics.id("GA", pd.gaTopic) else pd.learnTopicId
    val subject = if (isGa) "GA" else pd.learnSubjectId
    val title = if (isGa) pd.gaTopic else pd.learnTopic
    val questSize = if (isGa) pd.gaQuestions else (pd.setP ?: 12).coerceAtLeast(5)
    var size by remember { mutableIntStateOf(questSize) }
    var useAi by remember { mutableStateOf(c.settings.useAiWhenOnline) }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val existing by produceState<PracticeSessionEntity?>(null, kind, day) { value = c.db.sessions().practiceForDay(day, kind) }
    HunterScaffold(if (isGa) "General aptitude" else "Preparation set", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("Day $day") { Body(title); Dim(Subjects.get(subject).name); Dim("Questions come five at a time. A batch is graded only when you submit it; answers are saved as you go.") }
            existing?.let { s -> ResumeCard(s, { nav(Routes.practice(s.id)) }, { nav(Routes.report(s.id)) }) }
            if (!isGa) SystemPanel("Set size") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Plan size: $questSize", Hunter.Cyan, onClick = { size = questSize }, selected = size == questSize)
                    Chip("Full set: 30 (6 batches)", Hunter.Violet, onClick = { size = 30 }, selected = size == 30)
                }
                Dim("The Word document sets $questSize questions for this day. The full 30-question set (6 batches of 5) is available any time.")
            }
            AiToggle(useAi) { useAi = it; c.settings.useAiWhenOnline = it }
            err?.let { Note(it, NoteKind.ERROR) }
            HunterButton(if (busy) "PREPARING QUESTIONS…" else if (existing != null) "START A NEW SET ($size Q)" else "START ($size Q)", {
                scope.launch {
                    busy = true; err = null
                    try { nav(Routes.practice(c.study.startOrResumePractice(kind, day, if (isGa) questSize else size, subject, topicId, title, useAi, forceNew = true))) }
                    catch (e: Exception) { err = e.message ?: "Could not prepare questions" } finally { busy = false }
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}

/** Screen 9: Revision Set set-up with the required filters (Error Monster + 1-4-7 queue). */
@Composable
fun RevisionLauncherScreen(day: Int, onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val pd = c.plan.plan.day(day) ?: return
    var filter by remember { mutableStateOf(RevisionFilter()) }
    var size by remember { mutableIntStateOf((pd.setR ?: 12).coerceAtLeast(5)) }
    var useAi by remember { mutableStateOf(c.settings.useAiWhenOnline) }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val existing by produceState<PracticeSessionEntity?>(null, day) { value = c.db.sessions().practiceForDay(day, SetKinds.REVISION) }
    val preview by produceState(Triple(emptyList<QueueItem>(), emptyList<QueueItem>(), 0), filter) {
        val today = c.clock.today()
        val ms = c.db.mistakes().allOnce().map { it.toDomain() }; val es = c.db.mistakes().pendingRevisions().map { it.toDomain() }
        val q = RevisionScheduler.buildQueue(today, es, ms, 500)
        val f = filter.apply(q.all, today, ms.associateBy { it.id })
        value = Triple(f, q.all, ms.count { it.status == MistakeStatus.ACTIVE })
    }
    val subjectsInQueue = preview.second.map { it.subjectId }.distinct()
    HunterScaffold("Revision set", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("How this works") {
                Dim("Every active Error Monster is retested with a NEW question on the same concept. Due 1-4-7 reviews get fresh questions on their topics. Set size follows the plan (${pd.setR}${pd.setRMax?.let { "–$it" } ?: ""} Q).")
            }
            existing?.let { s -> ResumeCard(s, { nav(Routes.practice(s.id)) }, { nav(Routes.report(s.id)) }) }
            SystemPanel("Due now") {
                Body("${preview.second.size} items due (${preview.second.count { it.kind == QueueKind.MISTAKE }} mistakes, ${preview.second.count { it.kind == QueueKind.TOPIC_REVISION }} topic reviews). ${preview.third} active Error Monsters.")
                preview.first.take(6).forEach { Dim("• ${it.label}${if (it.overdueDays > 0) "  (${it.overdueDays}d overdue)" else ""}") }
                if (preview.first.size > 6) Dim("+${preview.first.size - 6} more match the filters")
                if (preview.second.isEmpty()) Dim("Nothing is due, so the set will use today's revision topic and earlier topics (${pd.revisionTopic}).")
            }
            SystemPanel("Filters") {
                Dim("Subject")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Chip("All", Hunter.Cyan, onClick = { filter = filter.copy(subjectId = null) }, selected = filter.subjectId == null)
                    subjectsInQueue.take(4).forEach { s -> Chip(Subjects.get(s).short, Hunter.Cyan, onClick = { filter = filter.copy(subjectId = s) }, selected = filter.subjectId == s) }
                }
                if (subjectsInQueue.size > 4) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { subjectsInQueue.drop(4).take(5).forEach { s -> Chip(Subjects.get(s).short, Hunter.Cyan, onClick = { filter = filter.copy(subjectId = s) }, selected = filter.subjectId == s) } }
                Dim("Status")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Due today", Hunter.Gold, onClick = { filter = filter.copy(dueToday = !filter.dueToday) }, selected = filter.dueToday)
                    Chip("Overdue", Hunter.Red, onClick = { filter = filter.copy(overdue = !filter.overdue) }, selected = filter.overdue)
                    Chip("Repeated", Hunter.Orange, onClick = { filter = filter.copy(frequentOnly = !filter.frequentOnly) }, selected = filter.frequentOnly)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Mistakes only", Hunter.Violet, onClick = { filter = filter.copy(mistakesOnly = !filter.mistakesOnly) }, selected = filter.mistakesOnly)
                    listOf(1, 2, 3).forEach { st -> Chip("Review +${RevisionScheduler.OFFSETS[st - 1]}", Hunter.Blue, onClick = { filter = filter.copy(stage = if (filter.stage == st) null else st) }, selected = filter.stage == st) }
                }
                Dim("Mistake type")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(MistakeCategory.CALCULATION, MistakeCategory.CONCEPT, MistakeCategory.MISREAD).forEach { cat -> Chip(cat.label.substringBefore(' '), Hunter.Green, onClick = { filter = filter.copy(category = if (filter.category == cat) null else cat) }, selected = filter.category == cat) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(MistakeCategory.FORMULA, MistakeCategory.GUESS, MistakeCategory.TIME).forEach { cat -> Chip(cat.label.substringBefore(' '), Hunter.Green, onClick = { filter = filter.copy(category = if (filter.category == cat) null else cat) }, selected = filter.category == cat) }
                }
                if (!filter.isDefault) HunterButton("Clear filters", { filter = RevisionFilter() }, outlined = true)
            }
            SystemPanel("Set size") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val planSize = (pd.setR ?: 12).coerceAtLeast(5)
                    Chip("Plan: $planSize", Hunter.Cyan, onClick = { size = planSize }, selected = size == planSize)
                    Chip("Full set: 30", Hunter.Violet, onClick = { size = 30 }, selected = size == 30)
                }
            }
            AiToggle(useAi) { useAi = it; c.settings.useAiWhenOnline = it }
            err?.let { Note(it, NoteKind.ERROR) }
            HunterButton(if (busy) "BUILDING NEW QUESTIONS…" else "START REVISION ($size Q)", {
                scope.launch {
                    busy = true; err = null
                    try { nav(Routes.practice(c.study.startRevision(day, size, filter, useAi))) } catch (e: Exception) { err = e.message ?: "Could not build the set" } finally { busy = false }
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}

/** Practice / Revision / GA / Error-Monster runner: five questions per batch, saved drafts, graded on submit. */
@Composable
fun PracticeScreen(sessionId: String, onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val vm: PracticeVm = viewModel(key = "practice-$sessionId", factory = factoryOf { PracticeVm(c, sessionId) })
    val load by vm.load.collectAsState(); val view by vm.view.collectAsState(); val st by vm.state.collectAsState()
    val nat by vm.natText.collectAsState(); val review by vm.review.collectAsState(); val busy by vm.busy.collectAsState()
    val ai by vm.aiText.collectAsState(); val aiBusy by vm.aiBusy.collectAsState(); val msg by vm.message.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    HunterScaffold(view?.session?.title ?: "Set", onBack, animate = false) { pad ->
        when (val l = load) {
            SetLoad.Loading -> Box(Modifier.padding(pad)) { Loading() }
            is SetLoad.Error -> ScreenColumn(pad) { Note(l.msg, NoteKind.ERROR) }
            SetLoad.Ready -> {
                val v = view; val s = st
                if (v == null || s == null) return@HunterScaffold
                val rv = review
                if (rv != null) ScreenColumn(pad) {
                    SystemPanel("Batch ${rv.batchIndex + 1} of ${s.batchCount} graded", accent = Hunter.Green) {
                        Body("${rv.correct} of ${rv.graded.size} correct · ${Scorer.r(rv.marks)} marks")
                        Dim("XP is added once per batch; submitting again never doubles it.")
                    }
                    rv.graded.forEachIndexed { i, g -> GradedCard(rv.batchIndex * s.batchSize + i + 1, g, ai[g.question.id], aiBusy == g.question.id, { vm.askAi(g) }, { vm.reportAmbiguous(g.question.id) }) }
                    msg?.let { Note(it, NoteKind.INFO) }
                    if (rv.setComplete || s.isComplete) HunterButton("VIEW FINAL REPORT", { nav(Routes.report(sessionId)) }, Modifier.fillMaxWidth(), color = Hunter.Gold)
                    else HunterButton("NEXT BATCH (${s.submittedBatches + 1} of ${s.batchCount})", { vm.nextBatch() }, Modifier.fillMaxWidth())
                } else if (s.isComplete) ScreenColumn(pad) {
                    Note("This set is finished.", NoteKind.OK); HunterButton("VIEW FINAL REPORT", { nav(Routes.report(sessionId)) }, Modifier.fillMaxWidth(), color = Hunter.Gold)
                } else ScreenColumn(pad) {
                    SystemPanel("Batch ${s.submittedBatches + 1} of ${s.batchCount}") {
                        GlowBar(s.submittedBatches.toFloat() / s.batchCount)
                        Dim("Questions ${s.nextQuestionNumber}–${minOf(s.questionIds.size, s.nextQuestionNumber + s.batchSize - 1)} of ${s.questionIds.size}. Source: ${v.session.note}")
                    }
                    v.current.forEachIndexed { i, q ->
                        QuestionBlock(s.nextQuestionNumber + i, q, s.answers[q.id], nat[q.id] ?: "", { sel -> vm.select(q.id, sel) }, { t -> vm.nat(q.id, t) })
                    }
                    val unanswered = v.current.count { q -> s.answers[q.id]?.isAnswered(q.type) != true }
                    msg?.let { Note(it, NoteKind.WARN) }
                    if (unanswered > 0) Dim("$unanswered question(s) unanswered — unanswered MCQs score 0, not negative.")
                    HunterButton(if (busy) "GRADING…" else "SUBMIT BATCH", { if (unanswered > 0) confirm = true else vm.submit() }, Modifier.fillMaxWidth(), enabled = !busy)
                }
                if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Submit with unanswered questions?") },
                    text = { Text("Unanswered questions score 0 and cannot be answered after grading.") },
                    confirmButton = { TextButton({ confirm = false; vm.submit() }) { Text("Submit") } }, dismissButton = { TextButton({ confirm = false }) { Text("Keep answering") } })
            }
        }
    }
}

/** Final report of a finished set. */
@Composable
fun PracticeReportScreen(sessionId: String, onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val vm: PracticeVm = viewModel(key = "practice-$sessionId", factory = factoryOf { PracticeVm(c, sessionId) })
    val rep by vm.report.collectAsState(); val load by vm.load.collectAsState()
    val ai by vm.aiText.collectAsState(); val aiBusy by vm.aiBusy.collectAsState(); val msg by vm.message.collectAsState()
    val topics by c.content.topics.collectAsState(emptyList())
    val titles = remember(topics) { topics.associate { it.topicId to it.title } }
    HunterScaffold("Final report", onBack, animate = false) { pad ->
        val r = rep
        if (load == SetLoad.Loading) { Box(Modifier.padding(pad)) { Loading() }; return@HunterScaffold }
        if (r == null) { ScreenColumn(pad) { Note("This set is not finished yet.", NoteKind.WARN); HunterButton("RESUME", { nav(Routes.practice(sessionId)) }) }; return@HunterScaffold }
        ScreenColumn(pad) {
            Text(r.session.title, style = MaterialTheme.typography.titleLarge, color = Hunter.Text)
            msg?.let { Note(it, NoteKind.INFO) }
            ReportBody(r.report, r.explanations, ai, aiBusy, { vm.askAi(it) }, { vm.reportAmbiguous(it) }) { titles[it] ?: it.substringAfter('/') }
            HunterButton("ERROR MONSTER", { nav(Routes.MONSTER) }, Modifier.fillMaxWidth(), outlined = true)
            HunterButton("BACK TO DASHBOARD", { nav(Routes.DASH) }, Modifier.fillMaxWidth())
        }
    }
}
