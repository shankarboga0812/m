package com.chandu.gatesystem.ai2027.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.data.toDomain
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.ui.LocalContainer
import com.chandu.gatesystem.ai2027.ui.Routes
import com.chandu.gatesystem.ai2027.ui.components.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter
import kotlinx.coroutines.launch

/** Screen 12: Error Monster — the central mistake database, filters, dashboard and re-attempt entry. */
@Composable
fun ErrorMonsterScreen(onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val all by c.db.mistakes().all().collectAsState(emptyList())
    val recs = remember(all) { all.map { it.toDomain() } }
    val today = c.clock.today()
    var filter by remember { mutableStateOf(ErrorMonsterEngine.Filter.ALL) }
    var open by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val dash = ErrorMonsterEngine.dashboard(recs, today)
    val shown = ErrorMonsterEngine.filter(recs, filter, today)
    val topics by c.content.topics.collectAsState(emptyList()); val titles = remember(topics) { topics.associate { it.topicId to it.title } }
    HunterScaffold("Error monster", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("Dashboard", accent = Hunter.Red) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Active", "${dash.active}", Modifier.weight(1f), Hunter.Red); StatTile("Due today", "${dash.dueToday}", Modifier.weight(1f), Hunter.Gold)
                    StatTile("Overdue", "${dash.overdue}", Modifier.weight(1f), Hunter.Orange)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Repeated", "${dash.repeated}", Modifier.weight(1f), Hunter.Violet); StatTile("Overcome (7d)", "${dash.recentlyOvercome}", Modifier.weight(1f), Hunter.Green)
                    StatTile("Resolved", pct(dash.resolutionRate), Modifier.weight(1f), Hunter.Green)
                }
                if (dash.topErrorTopics.isNotEmpty()) { Dim("Topics with the most errors"); dash.topErrorTopics.forEach { KeyValue(titles[it.first] ?: it.first.substringAfter('/'), "${it.second}", Hunter.Orange) } }
                Dim("A monster is overcome after 3 independent successes on NEW questions on separate days (+4 and +7 day gaps). A wrong answer resets the streak; the same question twice proves nothing.")
            }
            err?.let { Note(it, NoteKind.ERROR) }
            val due = recs.filter { it.status == MistakeStatus.ACTIVE && (it.nextRevision?.let { d -> !d.isAfter(today) } == true) }
            HunterButton(if (busy) "BUILDING NEW QUESTIONS…" else "RE-ATTEMPT 3 MISTAKES", {
                val pick = (due.ifEmpty { recs.filter { it.status == MistakeStatus.ACTIVE } }).sortedByDescending { it.occurrences }.take(3).map { it.id }
                scope.launch { busy = true; err = null; try { nav(Routes.practice(c.study.startRevision(c.plan.focusDay().day, 3, monsterIds = pick, wantAi = c.settings.useAiWhenOnline))) } catch (e: Exception) { err = e.message } finally { busy = false } }
            }, Modifier.fillMaxWidth(), enabled = !busy && recs.any { it.status == MistakeStatus.ACTIVE }, color = Hunter.Red)
            SystemPanel("Filter") {
                val names = mapOf(ErrorMonsterEngine.Filter.ALL to "All", ErrorMonsterEngine.Filter.DUE_TODAY to "Due today", ErrorMonsterEngine.Filter.OVERDUE to "Overdue",
                    ErrorMonsterEngine.Filter.FREQUENT to "Repeated", ErrorMonsterEngine.Filter.CALCULATION to "Calculation", ErrorMonsterEngine.Filter.CONCEPTUAL to "Conceptual",
                    ErrorMonsterEngine.Filter.MISREAD to "Misread", ErrorMonsterEngine.Filter.FORMULA to "Formula", ErrorMonsterEngine.Filter.GUESS to "Guess", ErrorMonsterEngine.Filter.OVERCOME to "Overcome")
                names.entries.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { (f, n) -> Chip(n, Hunter.Cyan, onClick = { filter = f }, selected = filter == f) } } }
            }
            if (shown.isEmpty()) Note(if (recs.isEmpty()) "No mistakes logged yet. Wrong answers in any set are saved here automatically (one record per underlying concept, no duplicates)." else "Nothing matches this filter.", NoteKind.INFO)
            shown.forEach { m ->
                val isOpen = open == m.id
                SystemPanel(accent = if (m.status == MistakeStatus.ACTIVE) Hunter.Red else Hunter.Green, onClick = { open = if (isOpen) null else m.id }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip(m.status.name, if (m.status == MistakeStatus.ACTIVE) Hunter.Red else Hunter.Green); Chip(m.category.label.substringBefore(' '), Hunter.Violet); if (m.occurrences >= 3) Chip("×${m.occurrences}", Hunter.Orange) }
                    Text(m.concept, style = MaterialTheme.typography.titleMedium, color = Hunter.Text)
                    Dim("${Subjects.get(m.subjectId).short} · ${titles[m.topicId] ?: m.topicId.substringAfter('/')}")
                    Dim(if (m.status == MistakeStatus.ACTIVE) "Streak ${m.consecutiveSuccess}/3 · next retest ${m.nextRevision ?: "—"}" else "Overcome ${m.overcomeDate}")
                    if (isOpen) {
                        HorizontalDivider(color = Hunter.Line)
                        KeyValue("Logged", "${m.createdDate}"); KeyValue("Your answer", m.userAnswer); KeyValue("Correct answer", m.correctAnswer, Hunter.Green)
                        KeyValue("Occurrences", "${m.occurrences}"); KeyValue("Independent successes", "${m.successfulRevisions}")
                        KeyValue("Last attempted", "${m.lastAttempted ?: "—"}"); KeyValue("Last success", "${m.lastSuccessDate ?: "—"}")
                        if (m.recurrenceAfterOvercome > 0) KeyValue("Came back after overcome", "${m.recurrenceAfterOvercome}×", Hunter.Red)
                        if (m.relatedConcepts.isNotEmpty()) KeyValue("Related concepts", m.relatedConcepts.joinToString(", "))
                        KeyValue("Source", m.source.label)
                        if (m.explanation.isNotBlank()) { Dim("Explanation"); Body(m.explanation) }
                        Dim("Change the mistake type (you know best):")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { MistakeCategory.values().take(3).forEach { cat -> Chip(cat.label.substringBefore(' '), Hunter.Green, onClick = { scope.launch { c.study.setMistakeCategory(m.id, cat) } }, selected = m.category == cat) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { MistakeCategory.values().drop(3).forEach { cat -> Chip(cat.label.substringBefore(' '), Hunter.Green, onClick = { scope.launch { c.study.setMistakeCategory(m.id, cat) } }, selected = m.category == cat) } }
                        var notes by remember(m.id) { mutableStateOf(m.notes) }
                        OutlinedTextField(notes, { notes = it; scope.launch { c.study.setMistakeNotes(m.id, it) } }, label = { Text("Your notes") }, modifier = Modifier.fillMaxWidth())
                        if (m.status == MistakeStatus.ACTIVE) HunterButton("HUNT THIS ONE", {
                            scope.launch { busy = true; err = null; try { nav(Routes.practice(c.study.startRevision(c.plan.focusDay().day, 1, monsterIds = listOf(m.id), wantAi = c.settings.useAiWhenOnline))) } catch (e: Exception) { err = e.message } finally { busy = false } }
                        }, enabled = !busy, color = Hunter.Red, outlined = true)
                    }
                }
            }
        }
    }
}
