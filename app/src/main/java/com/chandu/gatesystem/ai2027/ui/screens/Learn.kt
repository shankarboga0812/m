package com.chandu.gatesystem.ai2027.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.data.db.TopicProgressEntity
import com.chandu.gatesystem.ai2027.data.repo.AiResult
import com.chandu.gatesystem.ai2027.data.repo.SetKinds
import com.chandu.gatesystem.ai2027.data.toDomain
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.ui.LocalContainer
import com.chandu.gatesystem.ai2027.ui.Routes
import com.chandu.gatesystem.ai2027.ui.components.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter
import kotlinx.coroutines.launch

fun topicStatusColor(s: TopicStatus): Color = when (s) {
    TopicStatus.NOT_STARTED -> Hunter.TextDim; TopicStatus.LEARNING -> Hunter.Cyan; TopicStatus.PRACTISING -> Hunter.Blue
    TopicStatus.REVISION_REQUIRED -> Hunter.Orange; TopicStatus.STRONG -> Hunter.Green; TopicStatus.MASTERED -> Hunter.Gold
}

private fun statusOf(p: TopicProgressEntity?): TopicStatus = p?.let { runCatching { TopicStatus.valueOf(it.status) }.getOrNull() } ?: TopicStatus.NOT_STARTED

/** Screen 6: subject selection with per-subject mastery summary. */
@Composable
fun SubjectSelectionScreen(onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val topics by c.content.topics.collectAsState(emptyList()); val prog by c.content.progress.collectAsState(emptyList())
    val acc by c.db.sessions().topicAccuracy().collectAsState(emptyList())
    val pById = prog.associateBy { it.topicId }; val aById = acc.associateBy { it.topicId }
    HunterScaffold("Choose a subject", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            Dim("Every subject keeps its own topic tracker: Not started → Learning → Practising → Revision required → Strong → Mastered. Mastery comes from your answers, not from opening a lesson.")
            Subjects.all.filter { it.id != "MIXED" }.forEach { s ->
                val ts = topics.filter { it.subjectId == s.id }
                if (ts.isNotEmpty()) {
                    val strong = ts.count { statusOf(pById[it.topicId]) in setOf(TopicStatus.STRONG, TopicStatus.MASTERED) }
                    val attempted = ts.sumOf { aById[it.topicId]?.attempted ?: 0 }; val correct = ts.sumOf { aById[it.topicId]?.correct ?: 0 }
                    SystemPanel(s.name, onClick = { nav(Routes.subject(s.id)) }) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Body("${ts.size} topics"); Body("$strong strong/mastered", color = Hunter.Green)
                        }
                        GlowBar(if (ts.isEmpty()) 0f else strong.toFloat() / ts.size, color = Hunter.Green)
                        Dim(if (attempted == 0) "No questions answered yet" else "$correct / $attempted correct (${correct * 100 / attempted}%)")
                    }
                }
            }
        }
    }
}

/** Topic list of one subject (the chapter level of the plan: topics are listed in the document's order). */
@Composable
fun SubjectTopicsScreen(subjectId: String, onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val topics by c.content.topicsBySubject(subjectId).collectAsState(emptyList()); val prog by c.content.progress.collectAsState(emptyList())
    val acc by c.db.sessions().topicAccuracy().collectAsState(emptyList())
    val pById = prog.associateBy { it.topicId }; val aById = acc.associateBy { it.topicId }
    HunterScaffold(Subjects.get(subjectId).name, onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            val counts = TopicStatus.values().associateWith { st -> topics.count { statusOf(pById[it.topicId]) == st } }
            SystemPanel("Tracker") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    TopicStatus.values().take(3).forEach { Chip("${it.label} ${counts[it]}", topicStatusColor(it)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    TopicStatus.values().drop(3).forEach { Chip("${it.label} ${counts[it]}", topicStatusColor(it)) }
                }
            }
            if (topics.isEmpty()) Note("The plan lists no topics for this subject.", NoteKind.WARN)
            topics.forEach { t ->
                val p = pById[t.topicId]; val st = statusOf(p); val a = aById[t.topicId]
                SystemPanel(accent = topicStatusColor(st), onClick = { nav(Routes.lesson(t.topicId)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium, color = Hunter.Text, modifier = Modifier.weight(1f))
                        Chip(st.label, topicStatusColor(st))
                    }
                    Dim(buildString {
                        append(when (t.kind) { "LEARN" -> "Learn day ${t.firstDay}"; "REVISION" -> "Revision topic (first on day ${t.firstDay})"; else -> "General aptitude" })
                        if (p?.lessonCompleted == true) append(" · lesson done")
                        if (a != null) append(" · ${a.correct}/${a.attempted} correct")
                    })
                    if (p != null && st != TopicStatus.NOT_STARTED) { GlowBar(p.masteryScore / 100f, color = topicStatusColor(st)); Dim(p.statusReason) }
                }
            }
        }
    }
}

/** Screen 7: topic learning — lesson, status, schedule and practice entry. */
@Composable
fun LessonScreen(topicId: String, onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var lesson by remember { mutableStateOf<Lesson?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val prog by c.content.progressFor(topicId).collectAsState(null)
    val revs by c.db.mistakes().allRevisionsFlow().collectAsState(emptyList())
    val topic by produceState<com.chandu.gatesystem.ai2027.data.db.TopicEntity?>(null, topicId) { value = c.content.topic(topicId) }
    LaunchedEffect(topicId) { c.study.openLesson(topicId); lesson = c.content.lesson(topicId) }
    val st = statusOf(prog)
    HunterScaffold("Learn", onBack, animate = !c.settings.reduceMotion) { pad ->
        val l = lesson
        if (l == null) { Box(Modifier.padding(pad)) { Loading() }; return@HunterScaffold }
        ScreenColumn(pad) {
            Text(l.title, style = MaterialTheme.typography.headlineMedium, color = Hunter.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Subjects.get(topic?.subjectId ?: topicId.substringBefore('/')).short, Hunter.Violet)
                Chip(st.label, topicStatusColor(st))
                Chip(l.source.name, if (l.source == LessonSource.CURATED) Hunter.Green else if (l.source == LessonSource.AI) Hunter.Violet else Hunter.Orange)
            }
            if (l.source != LessonSource.CURATED) Note(l.source.label, if (l.source == LessonSource.AI) NoteKind.WARN else NoteKind.INFO)
            SystemPanel("Definition") { Body(l.definition); if (l.intuition.isNotBlank()) { Dim("Intuition"); Body(l.intuition) } }
            if (l.concepts.isNotEmpty()) SystemPanel("Key concepts") { l.concepts.forEach { Body("• $it") } }
            if (l.formulas.isNotEmpty()) SystemPanel("Formulas") { l.formulas.forEach { Body("${it.name}:  ${it.formula}"); if (it.meaning.isNotBlank()) Dim(it.meaning) } }
            if (l.methods.isNotEmpty()) SystemPanel("Methods") { l.methods.forEach { Body("• $it") } }
            l.examples.forEach { ex -> SystemPanel("Worked example") { Body(ex.problem); ex.steps.forEachIndexed { i, s -> Dim("${i + 1}. $s") } } }
            if (l.shortcuts.isNotEmpty()) SystemPanel("Shortcuts", accent = Hunter.Gold) { l.shortcuts.forEach { Body("• $it") } }
            if (l.traps.isNotEmpty()) SystemPanel("Common traps", accent = Hunter.Red) { l.traps.forEach { Body("• $it") } }
            if (l.complexity.isNotBlank()) SystemPanel("Complexity") { Body(l.complexity) }
            if (l.practice.isNotEmpty()) SystemPanel("Think about") { l.practice.forEach { Body("• $it") } }
            SystemPanel("Your progress", accent = topicStatusColor(st)) {
                prog?.let { KeyValue("Mastery score", "${it.masteryScore}/100"); Dim(it.statusReason) }
                Dim("Strong needs 10+ different questions at 80%+ over 2+ days. Mastered needs 20+ at 85%+ over 3+ days, harder questions, a correct recall 4+ days later, and no open mistakes.")
                val mine = revs.filter { it.itemKey == "topic:$topicId" }
                if (mine.isNotEmpty()) { Dim("1-4-7 reviews:"); mine.forEach { Body("• Day +${RevisionScheduler.OFFSETS[it.stage - 1]}: ${java.time.LocalDate.ofEpochDay(it.dueEpochDay)}${if (it.doneEpochDay != null) "  ✔ done" else ""}") } }
            }
            msg?.let { Note(it, NoteKind.WARN) }
            if (prog?.lessonCompleted != true) HunterButton("MARK LESSON COMPLETED", { scope.launch { c.study.completeLesson(topicId); msg = null } }, Modifier.fillMaxWidth(), color = Hunter.Green)
            else Note("Lesson completed on ${prog?.lessonCompletedEpochDay?.let { java.time.LocalDate.ofEpochDay(it) }}. Reviews are scheduled from that date.", NoteKind.OK)
            HunterButton(if (busy) "STARTING…" else "PRACTISE THIS TOPIC (10 Q)", {
                scope.launch {
                    busy = true
                    try {
                        val t = topic; val day = t?.firstDay ?: c.plan.focusDay().day
                        val id = c.study.startOrResumePractice(SetKinds.PREP, day, 10, t?.subjectId ?: topicId.substringBefore('/'), topicId, l.title, c.settings.useAiWhenOnline, forceNew = true)
                        nav(Routes.practice(id))
                    } catch (e: Exception) { msg = e.message } finally { busy = false }
                }
            }, Modifier.fillMaxWidth(), enabled = !busy)
            HunterButton(if (busy) "…" else "FETCH AI LESSON (needs backend)", {
                scope.launch {
                    busy = true
                    when (val r = c.ai.fetchLesson(topicId, l.title, topic?.subjectId ?: topicId.substringBefore('/'))) {
                        is AiResult.Ok -> { lesson = r.value; msg = null }
                        is AiResult.Fail -> msg = r.message
                    }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy, color = Hunter.Violet, outlined = true)
        }
    }
}
