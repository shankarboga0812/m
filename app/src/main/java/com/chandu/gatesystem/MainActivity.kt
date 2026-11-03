package com.chandu.gatesystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Bg = Color(0xFF080B16)
private val Panel = Color(0xFF12182B)
private val Cyan = Color(0xFF51E5FF)
private val Purple = Color(0xFF9B8CFF)
private val Muted = Color(0xFF9AA8C7)

private data class PlanDay(
    val day: Int, val dateLabel: String, val learning: String, val revision: String,
    val ga: String, val due: List<String>, val p: Int, val r: Int, val e: Int,
    val workout: String, val life: String, val mock: Boolean, val weekly: Boolean
)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_QUEST = "open_quest"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val days = readPlan()
        val prefs = getSharedPreferences("player_state_2027", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Cyan, secondary = Purple, background = Bg, surface = Panel,
                onPrimary = Bg, onBackground = Color.White, onSurface = Color.White
            )) {
                GateDashboard(days, prefs)
            }
        }
    }

    private fun readPlan(): List<PlanDay> {
        val json = assets.open("114_day_plan.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val arr = root.getJSONArray("days")
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val sets = d.getJSONObject("sets")
            val dueArr = d.getJSONArray("rnr_due")
            val due = (0 until dueArr.length()).map { j ->
                val item = dueArr.getJSONObject(j)
                "Day ${item.getInt("source_day")}: ${item.getString("topic")}"
            }
            PlanDay(
                day = d.getInt("day"), dateLabel = d.getString("date_label"),
                learning = d.optString("learning"), revision = d.optString("revision_subject"),
                ga = d.optString("ga_mini_set"), due = due,
                p = sets.optInt("P", 0), r = sets.optInt("R", 0), e = sets.optInt("E", 0),
                workout = d.optString("workout"), life = d.optString("life_quest"),
                mock = d.optBoolean("full_mock", false), weekly = d.optBoolean("weekly_review", false)
            )
        }
    }
}

@Composable
private fun GateDashboard(days: List<PlanDay>, prefs: android.content.SharedPreferences) {
    val today = LocalDate.now()
    val start = LocalDate.parse("2026-10-11")
    val calculated = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, 114)
    var selectedDay by remember { mutableIntStateOf(prefs.getInt("selected_day", calculated)) }
    var tab by remember { mutableStateOf("TODAY") }
    val day = days.getOrNull(selectedDay - 1) ?: days.first()
    val tasks = listOf("Learn topic", "Preparation set (${day.p})", "Revision set (${day.r})", if (day.mock) "Full mock · 65 questions" else "Exam set (${day.e})", "GA mini-set", "Workout", "Life / career quest", "End-of-day review")
    val checks = tasks.mapIndexed { i, _ -> prefs.getBoolean("day_${day.day}_task_$i", false) }
    val completed = checks.count { it }
    val xp = completed * 25
    val allCompleted = days.sumOf { d ->
        val count = 8
        (0 until count).count { prefs.getBoolean("day_${d.day}_task_$it", false) }
    }
    val totalXp = allCompleted * 25
    val level = totalXp / 500 + 1
    val rank = when { level >= 30 -> "S"; level >= 22 -> "A"; level >= 15 -> "B"; level >= 8 -> "C"; else -> "E" }
    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("SYSTEM INITIALIZING  /  PLAYER DETECTED", color = Cyan, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Text("GATE SOLO LEVELING", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("AI STUDY SYSTEM · GATE CS 2027", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("LEVEL", level.toString(), "RANK $rank", Modifier.weight(1f))
            StatCard("TOTAL XP", totalXp.toString(), "$allCompleted quests", Modifier.weight(1f))
            StatCard("PLAN", "$selectedDay/114", "85/100 target", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TODAY", "CALENDAR", "ERROR LOG").forEach { item ->
                FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            "CALENDAR" -> {
                PanelCard {
                    Text("114-DAY QUEST MAP", color = Cyan, fontWeight = FontWeight.Bold)
                    Text("11 Oct 2026 — 1 Feb 2027", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    for (d in days) {
                        TextButton(onClick = { selectedDay = d.day; prefs.edit().putInt("selected_day", d.day).apply(); tab = "TODAY" }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("DAY ${d.day.toString().padStart(3, '0')}  ·  ${d.dateLabel}", color = if (d.day == selectedDay) Cyan else Color.White, fontWeight = FontWeight.SemiBold)
                                Text(d.learning, color = Muted, fontSize = 11.sp, maxLines = 2)
                                if (d.mock) Text("FULL MOCK DAY", color = Purple, fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF28314A))
                    }
                }
            }
            "ERROR LOG" -> ErrorLogPanel(prefs)
            else -> {
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("DAILY QUEST", color = Cyan, fontWeight = FontWeight.Bold)
                        Text("${completed}/8 · +${xp} XP", color = Purple, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("DAY ${day.day.toString().padStart(3, '0')}", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Row {
                            TextButton(enabled = selectedDay > 1, onClick = { selectedDay--; prefs.edit().putInt("selected_day", selectedDay).apply() }) { Text("‹ PREV") }
                            TextButton(enabled = selectedDay < 114, onClick = { selectedDay++; prefs.edit().putInt("selected_day", selectedDay).apply() }) { Text("NEXT ›") }
                        }
                    }
                    Text(day.dateLabel, color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    InfoBlock("LEARN / CONSOLIDATE", day.learning)
                    InfoBlock("REVISION SUBJECT", day.revision)
                    InfoBlock("GA MINI-SET", day.ga)
                    if (day.due.isNotEmpty()) InfoBlock("1–4–7 DUE TOPICS", day.due.joinToString("\n"))
                    InfoBlock("WORKOUT · 05:15–05:50", day.workout)
                    InfoBlock("LIFE / CAREER QUEST", day.life)
                    if (day.mock) InfoBlock("BOSS RAID · FULL MOCK", "65 questions · 100 marks · 180 minutes\nReserve 45–60 minutes for analysis.")
                    if (day.weekly) InfoBlock("WEEKLY REVIEW", "Record focused hours, accuracy, mock trend, five weakest topics and next week's priorities.")
                    Spacer(Modifier.height(8.dp))
                    Text("QUEST CHECKLIST", color = Cyan, fontWeight = FontWeight.Bold)
                    tasks.forEachIndexed { i, label ->
                        var checked by remember(day.day, i) { mutableStateOf(prefs.getBoolean("day_${day.day}_task_$i", false)) }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = checked, onCheckedChange = { value ->
                                checked = value
                                prefs.edit().putBoolean("day_${day.day}_task_$i", value).apply()
                            })
                            Text(label, color = if (checked) Muted else Color.White, modifier = Modifier.weight(1f))
                            if (checked) Text("+25 XP", color = Cyan, fontSize = 11.sp)
                        }
                    }
                    Text("Progress is stored locally on this device. Workout completion is always manual.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PanelCard {
            Text("ALARM PLAN", color = Cyan, fontWeight = FontWeight.Bold)
            Text("05:00 wake · 05:15 workout · 20:30 prepare tomorrow · 21:00 sleep", color = Color.White, fontSize = 13.sp)
            Text("Alarm scheduling and Android permission handling are not yet enabled in this build.", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable private fun StatCard(title: String, value: String, note: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28314A))) {
        Column(Modifier.padding(10.dp)) {
            Text(title, color = Muted, fontSize = 10.sp)
            Text(value, color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(note, color = Purple, fontSize = 10.sp)
        }
    }
}

@Composable private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28314A))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

@Composable private fun InfoBlock(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(3.dp))
        Text(body.ifBlank { "No entry in source plan." }, color = Color.White, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ErrorLogPanel(prefs: android.content.SharedPreferences) {
    var topic by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Concept gap") }
    var correction by remember { mutableStateOf("") }
    var log by remember { mutableStateOf(prefs.getString("error_log", "") ?: "") }
    PanelCard {
        Text("ERROR MONSTER", color = Cyan, fontWeight = FontWeight.Bold)
        Text("Record mistakes, why they happened, and the corrected reasoning. Review weak topics within 48 hours.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(topic, { topic = it }, label = { Text("Topic / question") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(reason, { reason = it }, readOnly = true, label = { Text("Mistake type") }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("Concept gap", "Formula", "Calculation", "Misread", "Time management", "Guessing").forEach { item ->
                    DropdownMenuItem(text = { Text(item) }, onClick = { reason = item; expanded = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(correction, { correction = it }, label = { Text("Correct reasoning / next action") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = {
            if (topic.isNotBlank() && correction.isNotBlank()) {
                log = ("• ${LocalDate.now()} | $topic | $reason\n  $correction\n\n" + log).take(12000)
                prefs.edit().putString("error_log", log).apply(); topic = ""; correction = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("SAVE MISTAKE") }
        Spacer(Modifier.height(10.dp))
        Text(if (log.isBlank()) "No mistakes recorded yet." else log, color = Color.White, fontSize = 12.sp)
    }
}
