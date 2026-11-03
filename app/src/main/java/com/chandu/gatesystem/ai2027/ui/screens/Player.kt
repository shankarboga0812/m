package com.chandu.gatesystem.ai2027.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.ui.LocalContainer
import com.chandu.gatesystem.ai2027.ui.components.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter
import com.chandu.gatesystem.ai2027.ui.vm.HomeVm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun stats(p: com.chandu.gatesystem.ai2027.data.db.PlayerEntity) = PlayerStats(p.totalXp, p.questionsAnswered, p.correctAnswers, p.topicsStrong, p.topicsMastered,
    p.mocksCompleted, p.bestMockPercent, p.avgRecentMockPercent, p.mistakesOvercome, p.currentStreak, p.longestStreak, p.questsCompleted, p.revisionsCompleted, p.prepSetsCompleted)

/** Screen 13: player status. */
@Composable
fun PlayerStatusScreen(vm: HomeVm, onBack: () -> Unit) {
    val c = LocalContainer.current
    val p by vm.player.collectAsState(); val ach by vm.achievements.collectAsState()
    val acc by c.db.sessions().topicAccuracy().collectAsState(emptyList())
    HunterScaffold("Player status", onBack, animate = !c.settings.reduceMotion) { pad ->
        val pl = p ?: run { Box(Modifier.padding(pad)) { Loading() }; return@HunterScaffold }
        val rank = runCatching { Rank.valueOf(pl.rank) }.getOrDefault(Rank.E); val lv = Levels.progress(pl.totalXp)
        ScreenColumn(pad) {
            SystemPanel("Hunter") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GateEmblem(110.dp, animate = !c.settings.reduceMotion)
                    Column { Text(pl.name, style = MaterialTheme.typography.headlineMedium, color = Hunter.Text); Text("${rank.label} · ${rank.title}", color = Hunter.Gold, style = MaterialTheme.typography.labelMedium); Dim("Level ${lv.level}") }
                }
            }
            SystemPanel("Stats") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Total XP", "${pl.totalXp}", Modifier.weight(1f), Hunter.Violet); StatTile("Streak", "${pl.currentStreak}d", Modifier.weight(1f), Hunter.Orange); StatTile("Best streak", "${pl.longestStreak}d", Modifier.weight(1f), Hunter.Gold)
                }
                KeyValue("Questions answered", "${pl.questionsAnswered}"); KeyValue("Correct", "${pl.correctAnswers}")
                KeyValue("Accuracy", if (pl.questionsAnswered == 0) "—" else pct(pl.correctAnswers.toDouble() / pl.questionsAnswered))
                KeyValue("Topics Strong / Mastered", "${pl.topicsStrong} / ${pl.topicsMastered}"); KeyValue("Preparation sets finished", "${pl.prepSetsCompleted}")
                KeyValue("Revision sets finished", "${pl.revisionsCompleted}"); KeyValue("Error Monsters overcome", "${pl.mistakesOvercome}")
                KeyValue("Quests cleared", "${pl.questsCompleted}"); KeyValue("Full mocks", "${pl.mocksCompleted}")
                if (pl.mocksCompleted > 0) { KeyValue("Best mock", "%.1f%%".format(pl.bestMockPercent)); KeyValue("Recent mock average", "%.1f%%".format(pl.avgRecentMockPercent)) }
            }
            val weakest = acc.filter { it.attempted >= 5 }.sortedBy { it.correct.toDouble() / it.attempted }.take(5)
            if (weakest.isNotEmpty()) SystemPanel("Weakest topics (5+ attempts)") { weakest.forEach { KeyValue(it.topicId.substringAfter('/').replace('-', ' ').take(40), pct(it.correct.toDouble() / it.attempted), Hunter.Orange) } }
            SystemPanel("Achievements (${ach.size}/${Achievements.all.size})", accent = Hunter.Gold) {
                Achievements.all.forEach { a -> val got = ach.any { it.id == a.id }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (got) "★" else "☆", color = if (got) Hunter.Gold else Hunter.TextDim); Column { Body(a.title, color = if (got) Hunter.Text else Hunter.TextDim); Dim(a.description) } } }
            }
        }
    }
}

/** Screen 14: XP, level and rank — every change comes with its reason. */
@Composable
fun XpRankScreen(vm: HomeVm, onBack: () -> Unit) {
    val c = LocalContainer.current
    val p by vm.player.collectAsState(); val events by vm.events.collectAsState(); val xp by vm.recentXp.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
    HunterScaffold("XP & rank", onBack, animate = !c.settings.reduceMotion) { pad ->
        val pl = p ?: run { Box(Modifier.padding(pad)) { Loading() }; return@HunterScaffold }
        val st = stats(pl); val rank = runCatching { Rank.valueOf(pl.rank) }.getOrDefault(Rank.E); val lv = Levels.progress(pl.totalXp)
        ScreenColumn(pad) {
            SystemPanel("Level ${lv.level}") {
                GlowBar(lv.fraction, color = Hunter.Violet); Body("${lv.xpIntoLevel} / ${lv.xpForNext} XP into this level · ${pl.totalXp} total")
                Dim("Level L needs 50×(L−1)×L total XP. XP is never granted twice for the same lesson, batch, set or quest.")
            }
            SystemPanel("Ranks (never lowered; never from XP alone)") {
                Rank.values().forEach { r ->
                    val reqs = RankEvaluator.requirements(r, st); val reached = r.ordinal <= rank.ordinal
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RankBadge(r, 48.dp, locked = !reached)
                        Column(Modifier.weight(1f)) {
                            Text("${r.label} · ${r.title}", color = if (reached) Hunter.Gold else Hunter.TextDim, style = MaterialTheme.typography.titleMedium)
                            if (r == Rank.E) Dim("Starting rank") else if (reached) Dim("Reached") else reqs.forEach { Dim("${if (it.met) "✔" else "○"} ${it.description}") }
                        }
                    }
                }
            }
            SystemPanel("Why things changed") {
                if (events.isEmpty()) Dim("Level-ups, rank-ups, achievements and mastery changes appear here with their reason.")
                events.take(30).forEach { e -> Text(e.title, color = Hunter.Gold, style = MaterialTheme.typography.bodyMedium); Dim("${fmt.format(Date(e.createdAt))} — ${e.reason}") }
            }
            SystemPanel("XP log") {
                if (xp.isEmpty()) Dim("No XP yet. Take today\'s subject mock or finish your workout.")
                xp.take(40).forEach { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Dim(it.reason); Text("+${it.amount}", color = Hunter.Green, style = MaterialTheme.typography.bodySmall) } }
            }
            SystemPanel("XP rules (adjustable)") {
                val rules = c.settings.xpRules()
                listOf("subMockBase" to ("Subject mock: base" to rules.subjectMockBase), "subMockMark" to ("Subject mock: XP per net mark" to rules.subjectMockPerMark),
                    "mock" to ("Full mock: base" to rules.mockComplete), "mockMark" to ("Full mock: XP per net mark" to rules.mockPerNetMark.toInt()),
                    "examMark" to ("Exam set: XP per net mark" to rules.examPerMark), "bonus60" to ("Bonus for 60%+ in a mock" to rules.bonus60), "bonus75" to ("Bonus for 75%+ in a mock" to rules.bonus75),
                    "workoutDone" to ("Workout done" to rules.workoutDone), "workoutPartial" to ("Workout partial" to rules.workoutPartial),
                    "rev" to ("Revision set" to rules.revisionSessionComplete), "overcome" to ("Error Monster overcome" to rules.mistakeOvercome)
                ).forEach { (k, v) ->
                    var value by remember { mutableIntStateOf(v.second) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Body(v.first, Modifier.weight(1f))
                        OutlinedButton({ value = (value - 5).coerceAtLeast(0); c.settings.setXpRule(k, value) }) { Text("−5") }
                        Text("$value", Modifier.width(48.dp), color = Hunter.Gold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        OutlinedButton({ value += 5; c.settings.setXpRule(k, value) }) { Text("+5") }
                    }
                }
                HunterButton("RESET TO DEFAULTS", { c.settings.resetXpRules() }, outlined = true)
                Dim("Changes apply to future XP only; past XP is never rewritten.")
            }
        }
    }
}

/** Screen 16: workout tracker — manual only. Nothing here is ever ticked automatically. */
@Composable
fun WorkoutScreen(vm: HomeVm, onBack: () -> Unit) {
    val c = LocalContainer.current
    val ds by vm.dayState.collectAsState()
    val ws by c.db.player().workouts().collectAsState(emptyList())
    HunterScaffold("Workout", onBack, animate = !c.settings.reduceMotion) { pad ->
        val st = ds ?: run { Box(Modifier.padding(pad)) { Loading() }; return@HunterScaffold }
        val cur = st.facts.workout
        ScreenColumn(pad) {
            SystemPanel("Day ${st.day.day} · ${st.day.date}") {
                Body(st.day.workout); Dim("Window 5:15–5:50 AM (35 min). Mark it yourself: this app never completes a workout for you.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutStatus.values().forEach { s -> Chip(s.name.lowercase().replaceFirstChar { it.uppercase() }, when (s) { WorkoutStatus.DONE -> Hunter.Green; WorkoutStatus.PARTIAL -> Hunter.Orange; WorkoutStatus.SKIPPED -> Hunter.Red },
                        onClick = { vm.setWorkout(st.day.day, if (cur == s) null else s) }, selected = cur == s) }
                }
                Dim(when (cur) { WorkoutStatus.DONE -> "Done — counts fully toward today."; WorkoutStatus.PARTIAL -> "Partial — counts half."; WorkoutStatus.SKIPPED -> "Skipped — recorded honestly, no credit."; null -> "Not recorded yet. Tap the chip again to clear a choice." })
                if (st.day.isRestDay) Dim("The plan marks this as a rest day.")
            }
            SystemPanel("History") {
                if (ws.isEmpty()) Dim("No workouts recorded yet.")
                ws.take(40).forEach { w -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Body("Day ${w.day} · ${java.time.LocalDate.ofEpochDay(w.epochDay)}"); Dim(w.planned.take(60)) }
                    Text(w.status, color = when (w.status) { "DONE" -> Hunter.Green; "PARTIAL" -> Hunter.Orange; else -> Hunter.Red }, style = MaterialTheme.typography.labelMedium) } }
                if (ws.isNotEmpty()) { val d = ws.count { it.status == "DONE" }; Dim("$d done · ${ws.count { it.status == "PARTIAL" }} partial · ${ws.count { it.status == "SKIPPED" }} skipped") }
            }
            Note("Document note: the calendar's workout cycle (7-day rotation) does not match the weekday split table in the same document. The app follows the calendar days exactly as listed.", NoteKind.INFO)
        }
    }
}
