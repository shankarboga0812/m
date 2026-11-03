package com.chandu.gatesystem.ai2027.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.data.repo.PlanPhase
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.ui.LocalContainer
import com.chandu.gatesystem.ai2027.ui.Routes
import com.chandu.gatesystem.ai2027.ui.components.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter
import com.chandu.gatesystem.ai2027.ui.vm.HomeVm
import com.chandu.gatesystem.ai2027.ui.vm.QuestRow
import java.time.LocalDate

fun questRoute(r: QuestRow): String? = when (r.spec.kind) {
    QuestKind.SUBJECT_MOCK -> Routes.MOCKS
    QuestKind.PREP_SET -> Routes.setup("PREP", r.spec.day)
    QuestKind.REVISION_SET -> Routes.revision(r.spec.day)
    QuestKind.EXAM_SET -> Routes.examSetup(r.spec.day)
    QuestKind.MOCK_TEST -> Routes.MOCKS
    QuestKind.GENERAL_APTITUDE -> Routes.setup("GA", r.spec.day)
    QuestKind.ERROR_MONSTER -> Routes.MONSTER
    QuestKind.WORKOUT -> Routes.WORKOUT
    else -> null
}

fun statusColor(s: QuestStatus): Color = when (s) {
    QuestStatus.COMPLETED -> Hunter.Green; QuestStatus.IN_PROGRESS -> Hunter.Cyan; QuestStatus.PENDING -> Hunter.TextDim
    QuestStatus.SKIPPED -> Hunter.Orange; QuestStatus.MISSED -> Hunter.Red
}

@Composable
fun QuestCard(r: QuestRow, onOpen: (String) -> Unit, onToggle: (String, Boolean) -> Unit) {
    val p = r.progress; val route = questRoute(r); val s = r.spec
    SystemPanel(accent = statusColor(p.status)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, color = Hunter.Text, modifier = Modifier.weight(1f))
            Chip(p.status.name.replace('_', ' '), statusColor(p.status))
        }
        if (s.detail.isNotBlank()) Dim(s.detail.take(220))
        Dim(s.requirement)
        if (s.required > 1) { GlowBar(p.fraction, color = statusColor(p.status)); Dim("${p.done} / ${p.required}") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val mins = if (s.plannedMinMinutes != null) "${s.plannedMinMinutes}${if (s.plannedMaxMinutes != s.plannedMinMinutes) "–${s.plannedMaxMinutes}" else ""} min" else null
            mins?.let { Chip(it, Hunter.Violet) }
            Chip("+${s.xp} XP", Hunter.Gold)
            Spacer(Modifier.weight(1f))
            if (route != null && p.status != QuestStatus.COMPLETED) HunterButton(if (p.done > 0) "RESUME" else "OPEN", { onOpen(route) })
            else if (route != null) HunterButton("VIEW", { onOpen(route) }, outlined = true)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = p.status == QuestStatus.COMPLETED, onCheckedChange = { onToggle(r.entity.id, it) })
                Text("Done", color = Hunter.Text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Screen 3: dashboard. */
@Composable
fun DashboardScreen(vm: HomeVm, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val player by vm.player.collectAsState(); val ds by vm.dayState.collectAsState(); val events by vm.events.collectAsState()
    val phase = c.plan.phase()
    HunterScaffold("System", animate = !c.settings.reduceMotion, actions = {
        TextButton({ nav(Routes.SETTINGS) }) { Text("SETTINGS", color = Hunter.Cyan, style = MaterialTheme.typography.labelMedium) }
    }) { pad ->
        val p = player
        if (p == null || ds == null) { Box(Modifier.padding(pad)) { Loading() }; return@HunterScaffold }
        val st = ds!!; val lv = Levels.progress(p.totalXp); val rank = runCatching { Rank.valueOf(p.rank) }.getOrDefault(Rank.E)
        ScreenColumn(pad) {
            SystemPanel("Hunter") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RankBadge(rank, 76.dp)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, color = Hunter.Text)
                        Text("${rank.label} · ${rank.title}", style = MaterialTheme.typography.labelMedium, color = Hunter.Gold)
                        Dim("Level ${lv.level} · ${p.totalXp} XP total")
                        GlowBar(lv.fraction, color = Hunter.Violet); Dim("${lv.xpIntoLevel} / ${lv.xpForNext} XP to level ${lv.level + 1}")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Streak", "${p.currentStreak}d", Modifier.weight(1f), Hunter.Orange)
                    StatTile("Accuracy", if (p.questionsAnswered == 0) "—" else "${(p.correctAnswers * 100 / p.questionsAnswered)}%", Modifier.weight(1f), Hunter.Green)
                    StatTile("Answered", "${p.questionsAnswered}", Modifier.weight(1f))
                }
            }
            when (phase) {
                PlanPhase.BEFORE -> Note("Your plan starts on ${c.plan.start} (in ${c.plan.daysUntilStart()} day(s)). Showing Day 1 so you can preview it.", NoteKind.INFO)
                PlanPhase.AFTER -> Note("The 114-day window has ended. You can keep practising; showing the last plan day.", NoteKind.WARN)
                PlanPhase.ACTIVE -> Unit
            }
            events.filter { !it.seen }.take(3).forEach { e ->
                SystemPanel(e.kind.replace('_', ' '), accent = Hunter.Gold) { Text(e.title, style = MaterialTheme.typography.titleMedium, color = Hunter.Gold); Dim("Why: ${e.reason}") }
            }
            if (events.any { !it.seen }) HunterButton("Dismiss notices", { vm.markEventsSeen() }, outlined = true)
            SystemPanel("Day ${st.day.day} of ${c.plan.plan.size} · ${st.day.date}", onClick = { nav(Routes.QUESTS) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RingProgress(st.percent / 100f, 84.dp, if (st.percent >= Streaks.QUALIFY_PERCENT) Hunter.Green else Hunter.Cyan) { Text("${st.percent}%", color = Hunter.Text, style = MaterialTheme.typography.titleMedium) }
                    Column(Modifier.weight(1f)) {
                        Body("Subject mock: ${Subjects.get(QuestBuilder.mockSubject(st.day)).name}"); Dim("${st.day.weekday}${if (QuestBuilder.isFullMockDay(st.day.day)) " · FULL MOCK DAY" else ""} · XP comes from your marks")
                        Dim("${st.rows.count { it.progress.status == QuestStatus.COMPLETED }} of ${st.rows.size} quests cleared · ${Streaks.QUALIFY_PERCENT}% keeps your streak")
                    }
                }
            }
            val next = st.rows.firstOrNull { it.progress.status != QuestStatus.COMPLETED && it.progress.status != QuestStatus.SKIPPED && questRoute(it) != null }
            if (next != null) HunterButton("CONTINUE: ${next.spec.name}".take(48), { nav(questRoute(next)!!) }, Modifier.fillMaxWidth())
            if (st.dueToday.isNotEmpty() || p.mistakesOvercome >= 0) SystemPanel("1-4-7 reviews due", onClick = { nav(Routes.revision(st.day.day)) }) {
                if (st.dueToday.isEmpty()) Dim("Nothing due today. Mistakes from mocks are scheduled here for revision.")
                st.dueToday.take(4).forEach { Body("• ${it.label} (day +${RevisionScheduler.OFFSETS[it.stage - 1]}${if (it.overdueDays(c.clock.today()) > 0) ", ${it.overdueDays(c.clock.today())}d overdue" else ""})") }
                if (st.dueToday.size > 4) Dim("+${st.dueToday.size - 4} more")
            }
            if (st.missed.isNotEmpty()) SystemPanel("Missed quests — resume, do not double up", accent = Hunter.Red) {
                st.missed.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Body(m.row.spec.name); Dim("Day ${m.day} · ${m.date}") }
                        questRoute(m.row)?.let { r -> HunterButton("RESUME", { nav(r) }, outlined = true, color = Hunter.Red) }
                    }
                }
            }
            SystemPanel("Navigate") {
                val items = listOf("Daily quests" to Routes.QUESTS, "Calendar" to Routes.CALENDAR, "Revision" to Routes.revision(st.day.day),
                    "Exam Set" to Routes.examSetup(st.day.day), "Error Monster" to Routes.MONSTER, "Mocks" to Routes.MOCKS, "Player status" to Routes.PLAYER,
                    "XP & rank" to Routes.XP, "Workout" to Routes.WORKOUT, "Alarms" to Routes.ALARMS, "AI Teacher" to Routes.AI, "Question bank" to Routes.BANK, "Backup" to Routes.BACKUP)
                items.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (t, r) -> HunterButton(t.uppercase(), { nav(r) }, Modifier.weight(1f), outlined = true) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Screen 4: daily quests for any plan day. */
@Composable
fun DailyQuestsScreen(vm: HomeVm, onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val ds by vm.dayState.collectAsState(); val sel by vm.selectedDay.collectAsState()
    HunterScaffold("Daily quests", onBack, animate = !c.settings.reduceMotion) { pad ->
        val st = ds
        if (st == null) { Box(Modifier.padding(pad)) { Loading() }; return@HunterScaffold }
        ScreenColumn(pad) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                HunterButton("◀", { vm.select(sel - 1) }, enabled = sel > 1, outlined = true)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DAY ${st.day.day}", style = MaterialTheme.typography.titleLarge, color = Hunter.Cyan)
                    Dim("${st.day.date} · ${st.day.weekday}${if (st.isToday) " · TODAY" else ""}")
                }
                HunterButton("▶", { vm.select(sel + 1) }, enabled = sel < c.plan.plan.size, outlined = true)
            }
            if (!st.isToday) HunterButton("JUMP TO TODAY", { vm.select(c.plan.focusDay().day) }, Modifier.fillMaxWidth(), outlined = true)
            SystemPanel("Completion") {
                GlowBar(st.percent / 100f, color = if (st.percent >= Streaks.QUALIFY_PERCENT) Hunter.Green else Hunter.Cyan)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Body("${st.percent}% complete"); Dim(if (st.percent >= Streaks.QUALIFY_PERCENT) "Streak day ✔" else "${Streaks.QUALIFY_PERCENT}% needed for the streak")
                }
                st.day.dueNote?.let { Dim(it) }
                if (st.day.due.isNotEmpty()) { Dim("Plan says due today (147):"); st.day.due.forEach { Dim("• Day ${it.fromDay}: ${it.topic}") } }
                st.day.mockPhase?.let { Dim("Mock: $it") }
                if (st.day.weeklyReview) st.day.weeklyReviewText?.let { Dim("Weekly review: $it") }
            }
            if (st.rows.isEmpty()) Note("No quests were generated for this day.", NoteKind.WARN)
            st.rows.forEach { r -> QuestCard(r, nav, vm::toggleManual) }
            Dim("Unfinished quests stay open: resume them later. Nothing is doubled onto tomorrow.")
        }
    }
}

/** Screen 5: 114-day calendar with completion colouring and mock markers. */
@Composable
fun CalendarScreen(vm: HomeVm, onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current
    val sums by vm.summaries.collectAsState(); val sel by vm.selectedDay.collectAsState()
    val byDay = sums.associateBy { it.day }
    val today = c.clock.today()
    val offset = LocalDate.parse(c.plan.plan.days.first().date).dayOfWeek.value % 7 // Sunday = 0
    val cells: List<PlanDay?> = List(offset) { null } + c.plan.plan.days
    HunterScaffold("Calendar", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("${c.plan.start} → ${c.plan.end}") {
                Row(Modifier.fillMaxWidth()) { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Hunter.TextDim, style = MaterialTheme.typography.labelMedium) } }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { d ->
                            if (d == null) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                                val s = byDay[d.day]; val date = LocalDate.parse(d.date)
                                val col = when {
                                    s != null && s.qualifies -> Hunter.Green
                                    s != null && s.percent > 0 -> Hunter.Orange
                                    date.isBefore(today) -> Hunter.Red
                                    date == today -> Hunter.Cyan
                                    else -> Hunter.Line
                                }
                                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = if (date.isAfter(today)) 0.10f else 0.28f))
                                    .border(if (d.day == sel) 2.dp else 1.dp, if (d.day == sel) Hunter.Gold else col.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .clickable { vm.select(d.day) }, contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${d.day}", color = Hunter.Text, style = MaterialTheme.typography.labelMedium)
                                        if (d.fullMock) Text("M", color = Hunter.Gold, style = MaterialTheme.typography.labelMedium)
                                        else if (s != null) Text("${s.percent}", color = col, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip("≥70% ✔", Hunter.Green); Chip("partial", Hunter.Orange); Chip("missed", Hunter.Red); Chip("M = full mock", Hunter.Gold) }
            }
            val pd = c.plan.plan.day(sel)
            if (pd != null) SystemPanel("Day ${pd.day} · ${pd.date}", onClick = { nav(Routes.QUESTS) }) {
                Body("Subject mock: ${Subjects.get(QuestBuilder.mockSubject(pd)).name}")
                Dim("Revision subject: ${pd.revisionSubjectLabel} — ${pd.revisionTopic}")
                Dim("GA: ${pd.gaTopic}")
                Dim("Workout: ${pd.workout}")
                if (QuestBuilder.isFullMockDay(pd.day)) Dim("Full mock: 65 Q · 100 marks · 180 min")
                byDay[pd.day]?.let { Dim("Completion recorded: ${it.percent}%") }
                Dim("Tap to open this day's quests")
            }
        }
    }
}
