package com.chandu.gatesystem.ai2027.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.BuildConfig
import com.chandu.gatesystem.ai2027.alarms.AlarmScheduler
import com.chandu.gatesystem.ai2027.alarms.NotificationHelper
import com.chandu.gatesystem.ai2027.data.repo.AiResult
import com.chandu.gatesystem.ai2027.data.toConfig
import com.chandu.gatesystem.ai2027.data.toEntity
import com.chandu.gatesystem.ai2027.domain.*
import com.chandu.gatesystem.ai2027.ui.LocalContainer
import com.chandu.gatesystem.ai2027.ui.components.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Screen 17: alarm settings. Times come from the plan document; everything else is off until YOU pick a time. */
@Composable
fun AlarmSettingsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val rows by c.db.player().alarms().collectAsState(emptyList())
    var cap by remember { mutableStateOf(AlarmScheduler.capability(ctx)) }
    var info by remember { mutableStateOf<String?>(null) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cap = AlarmScheduler.capability(ctx) }
    fun save(cfg: AlarmConfig) = scope.launch {
        c.db.player().upsertAlarm(cfg.toEntity())
        val planned = AlarmScheduler.rescheduleAll(ctx, c.db, c.plan)
        info = "Saved. ${planned.size} alarm(s) registered; next: ${planned.firstOrNull()?.let { "${it.id.label} at ${it.at}" } ?: "none"}"
        cap = AlarmScheduler.capability(ctx)
    }
    HunterScaffold("Alarms", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("What Android allows right now", accent = if (cap.exactAllowed && cap.notificationsAllowed) Hunter.Green else Hunter.Orange) {
                Body(cap.summary)
                Dim("Android 12+ requires you to grant 'Alarms & reminders'. Android 13+ requires notification permission. Some phone makers (Xiaomi, Oppo, Vivo, Samsung 'sleeping apps') may still stop background alarms: exempt this app from battery optimisation and allow auto-start. Alarms are re-registered after reboot and about every 6 hours.")
                if (!cap.notificationsAllowed) HunterButton("ALLOW NOTIFICATIONS", {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else ctx.startActivity(AlarmScheduler.notificationSettingsIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                if (!cap.exactAllowed) HunterButton("ALLOW EXACT ALARMS", { ctx.startActivity(AlarmScheduler.exactAlarmSettingsIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, outlined = true)
                if (!cap.ignoringBatteryOptimisation) HunterButton("BATTERY OPTIMISATION SETTINGS", { ctx.startActivity(AlarmScheduler.batterySettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, outlined = true)
                HunterButton("REFRESH STATUS", { cap = AlarmScheduler.capability(ctx) }, outlined = true)
            }
            info?.let { Note(it, NoteKind.OK) }
            Note("Only the times written in your document are pre-set: 5:00 wake, 5:15 workout, 8:30 PM prepare-tomorrow, 9:00 PM sleep. The document gives NO time for the follow-up, due-revision, same-subject or mock reminders, so those are off until you choose a time.", NoteKind.INFO)
            rows.map { it.toConfig() }.sortedBy { it.id.ordinal }.forEach { cfg ->
                SystemPanel(cfg.id.label, accent = if (cfg.schedulable) Hunter.Cyan else Hunter.Line) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = cfg.enabled, onCheckedChange = { on -> if (!on || cfg.time != null) save(cfg.copy(enabled = on)) else info = "Pick a time first — the plan does not specify one." }, enabled = cfg.time != null || cfg.enabled)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Body(cfg.time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "No time set"); Dim(if (cfg.fromPlan) "Time from your plan document" else "Not in the plan — your choice")
                        }
                        OutlinedButton({
                            val t = cfg.time ?: LocalTime.of(20, 0)
                            TimePickerDialog(ctx, { _, h, m -> save(cfg.copy(time = LocalTime.of(h, m), fromPlan = false)) }, t.hour, t.minute, true).show()
                        }) { Text(if (cfg.time == null) "SET TIME" else "CHANGE") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("Sound", Hunter.Cyan, onClick = { save(cfg.copy(sound = !cfg.sound)) }, selected = cfg.sound)
                        Chip("Vibrate", Hunter.Cyan, onClick = { save(cfg.copy(vibrate = !cfg.vibrate)) }, selected = cfg.vibrate)
                        listOf(5, 10, 15).forEach { m -> Chip("Snooze ${m}m", Hunter.Violet, onClick = { save(cfg.copy(snoozeMinutes = m)) }, selected = cfg.snoozeMinutes == m) }
                    }
                    HunterButton("SEND A TEST NOTIFICATION", {
                        scope.launch {
                            val (t, x) = com.chandu.gatesystem.ai2027.alarms.AlarmContent.build(c, cfg.id, c.clock.today())
                            NotificationHelper.showAlarm(ctx, cfg.id, "TEST · $t", x, cfg.snoozeMinutes, cfg.sound, cfg.vibrate)
                            info = if (cap.notificationsAllowed) "Test sent. If you see nothing, check the notification channel 'Study alarms' in system settings." else "Notifications are blocked, so nothing can be shown."
                        }
                    }, outlined = true)
                }
            }
        }
    }
}

/** Screen 18: AI Teacher settings with an honest, real connection test. */
@Composable
fun AiTeacherScreen(onBack: () -> Unit) {
    val c = LocalContainer.current; val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(c.settings.backendUrl) }; var token by remember { mutableStateOf(c.settings.appToken) }
    var useAi by remember { mutableStateOf(c.settings.useAiWhenOnline) }; var tg by remember { mutableStateOf(c.settings.telegramRelay) }
    var status by remember { mutableStateOf<ConnectionStatus?>(null) }; var busy by remember { mutableStateOf(false) }; var saved by remember { mutableStateOf(false) }
    HunterScaffold("AI Teacher", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("How it works") {
                Body("The app talks only to YOUR backend (the Node server in /backend). The AI provider key and the Telegram bot token live on that server and are never stored in this app.")
                Dim("Without a backend the app is fully usable offline: it uses the pre-installed 3000-question bank and the offline generator. AI can add questions, explanations and lessons, but never changes a score.")
            }
            SystemPanel("Backend connection") {
                OutlinedTextField(url, { url = it; saved = false }, label = { Text("Backend URL (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it; saved = false }, label = { Text("App token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Dim("The app token is a shared secret between this app and your backend (APP_TOKEN). It is stored privately on this phone only.")
                HunterButton("SAVE", { c.settings.backendUrl = url; c.settings.appToken = token; saved = true; status = null }, Modifier.fillMaxWidth(), outlined = true)
                if (saved) Note("Saved.", NoteKind.OK)
                HunterButton(if (busy) "TESTING…" else "TEST CONNECTION", {
                    c.settings.backendUrl = url; c.settings.appToken = token
                    scope.launch { busy = true; status = c.ai.testConnection(); busy = false }
                }, Modifier.fillMaxWidth(), enabled = !busy)
                status?.let { s ->
                    fun mark(b: Boolean?) = when (b) { true -> "✔ yes"; false -> "✘ no"; null -> "— not checked" }
                    KeyValue("Configured", mark(s.configured)); KeyValue("Backend reachable", mark(s.reachable)); KeyValue("Token accepted", mark(s.authorised)); KeyValue("AI provider ready", mark(s.aiAvailable))
                    Note(s.detail, if (s.aiAvailable == true) NoteKind.OK else NoteKind.WARN)
                } ?: if (c.settings.lastConnectionNote.isNotBlank()) Dim("Last test: ${c.settings.lastConnectionNote}") else Dim("Not tested yet.")
            }
            SystemPanel("Behaviour") {
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(useAi, { useAi = it; c.settings.useAiWhenOnline = it }); Spacer(Modifier.width(8.dp)); Column { Body("Use AI questions when online"); Dim("Falls back to the offline bank automatically.") } }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(tg, { tg = it; c.settings.telegramRelay = it }); Spacer(Modifier.width(8.dp)); Column { Body("Telegram relay (optional)"); Dim("Sends short set/exam summaries through your backend. Needs TELEGRAM_* configured on the server.") } }
            }
            SystemPanel("AI rules in this app") {
                listOf("AI-generated questions are labelled 'AI-generated' and are never presented as official GATE questions.", "Each AI question is schema-checked, de-duplicated and, where possible, verified by code. Anything ambiguous is flagged and excluded, never used to penalise you.",
                    "AI cannot change your score, XP or rank.", "Verified previous-year questions are kept separate; none are bundled in this version.").forEach { Dim("• $it") }
            }
        }
    }
}

/** Screen 19: question-bank status. */
@Composable
fun QuestionBankScreen(onBack: () -> Unit) {
    val c = LocalContainer.current; val scope = rememberCoroutineScope()
    val total by c.content.questionCount.collectAsState(0); val bySource by c.content.countBySource.collectAsState(emptyList())
    val flagged by c.content.flaggedCount.collectAsState(0); val flaggedList by c.content.flaggedQuestions.collectAsState(emptyList())
    val lessons by c.content.authoredLessons.collectAsState(0)
    val table by produceState<List<Triple<String, List<Int>, List<Int>>>>(emptyList(), total) {
        value = BankCodec.subjects.map { s -> Triple(s, BankRole.values().map { c.content.bankRoleCount(s, it) }, BankRole.values().map { c.content.unseenCount(s, it) }) }
    }
    HunterScaffold("Question bank", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("Totals") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Stored", "$total", Modifier.weight(1f)); StatTile("Flagged", "$flagged", Modifier.weight(1f), Hunter.Orange); StatTile("Lessons", "$lessons", Modifier.weight(1f), Hunter.Violet)
                }
                bySource.forEach { s -> KeyValue(runCatching { QSource.valueOf(s.source).label }.getOrDefault(s.source), "${s.n}") }
                Dim("Pre-installed: 250 questions × 12 subjects = ${BankCodec.PER_SUBJECT * BankCodec.subjects.size}. Answers were computed by code, not typed by hand; many are value-variations of the same concept, so expect repeated patterns with different numbers.")
                Dim("Verified previous-year questions: 0 bundled. They would be listed separately and labelled with year and source.")
                Dim("Authored lessons: $lessons. Other topics show a study checklist, or an AI lesson if you fetch one.")
            }
            SystemPanel("Left to attempt (unseen / total)") {
                Row(Modifier.fillMaxWidth()) { Text("Subject", Modifier.weight(1.2f), color = Hunter.TextDim, style = MaterialTheme.typography.labelMedium); BankRole.values().forEach { Text(it.name.lowercase(), Modifier.weight(1f), color = Hunter.TextDim, style = MaterialTheme.typography.labelMedium) } }
                table.forEach { (s, tot, left) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(Subjects.get(s).short, Modifier.weight(1.2f), color = Hunter.Text, style = MaterialTheme.typography.bodySmall)
                        tot.indices.forEach { i -> Text("${left[i]}/${tot[i]}", Modifier.weight(1f), color = if (left[i] == 0) Hunter.Orange else Hunter.Green, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Dim("When a pool runs out, fresh questions with new values are generated offline, so practice never stops.")
            }
            SystemPanel("Flagged (ambiguous or invalid)", accent = Hunter.Orange) {
                if (flaggedList.isEmpty()) Dim("None. AI questions that fail checks, or that you report as ambiguous, appear here and are never used for scoring.")
                flaggedList.forEach { q -> Body(q.statement.take(120)); Dim("${Subjects.get(q.subjectId).short} · ${q.source}"); HunterButton("RESTORE TO BANK", { scope.launch { c.content.setFlagged(q.id, false) } }, outlined = true) }
            }
        }
    }
}

/** Screen 20: backup / restore through the system file picker. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val c = LocalContainer.current; val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<Pair<String, NoteKind>?>(null) }; var restart by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { msg = try { "Backup saved (${c.backup.export(uri) / 1024} KB)." to NoteKind.OK } catch (e: Exception) { "Backup failed: ${e.message}" to NoteKind.ERROR } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { val r = c.backup.restore(uri); msg = r.message to (if (r.ok) NoteKind.OK else NoteKind.ERROR); restart = r.ok }
    }
    HunterScaffold("Backup & restore", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("Backup") {
                Body("Saves a copy of ALL your progress, mistakes, attempts and questions to a file you choose (Drive, Downloads, a USB drive…).")
                Dim("Not included: the AI backend URL and token (secrets stay on this phone). Keep backup files private.")
                HunterButton("CREATE BACKUP", { export.launch("gate_solo_leveling_backup_${c.clock.today()}.db") }, Modifier.fillMaxWidth())
            }
            SystemPanel("Restore", accent = Hunter.Orange) {
                Body("Replaces the data on this phone with a backup. The current data is kept once as a safety copy inside the app.")
                Dim("The file is checked first (valid database, matching tables, not from a newer version). The app closes after a restore; reopen it.")
                HunterButton("CHOOSE BACKUP TO RESTORE", { import.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), outlined = true, color = Hunter.Orange)
            }
            msg?.let { Note(it.first, it.second) }
            Note("Updating the app never wipes your data: the database uses migrations and never falls back to a destructive reset.", NoteKind.INFO)
        }
        if (restart) AlertDialog(onDismissRequest = {}, title = { Text("Restore complete") }, text = { Text("The app must close now. Open it again to see the restored data.") },
            confirmButton = { TextButton({ android.os.Process.killProcess(android.os.Process.myPid()) }) { Text("Close app") } })
    }
}

/** Screen 21: app settings. */
@Composable
fun AppSettingsScreen(onBack: () -> Unit, nav: (String) -> Unit) {
    val c = LocalContainer.current; val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(c.settings.playerName) }; var partial by remember { mutableStateOf(c.settings.msqPartialCredit) }; var reduce by remember { mutableStateOf(c.settings.reduceMotion) }
    HunterScaffold("Settings", onBack, animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            SystemPanel("Player") {
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HunterButton("SAVE NAME", { c.settings.playerName = name; scope.launch { c.db.player().player()?.let { c.db.player().upsertPlayer(it.copy(name = c.settings.playerName)) } } }, outlined = true)
            }
            SystemPanel("Marking") {
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(partial, { partial = it; c.settings.msqPartialCredit = it }); Spacer(Modifier.width(8.dp)); Column { Body("MSQ partial credit"); Dim("Off by default (GATE-style all-or-nothing). Turn on only for practice; affects new gradings.") } }
                Dim("MCQ negative marking is fixed at marks ÷ 3. MSQ and NAT have none.")
            }
            SystemPanel("Display") {
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(reduce, { reduce = it; c.settings.reduceMotion = it }); Spacer(Modifier.width(8.dp)); Column { Body("Reduce motion"); Dim("Stops particle and gate animations (applies on the next screen).") } }
            }
            SystemPanel("Go to") {
                listOf("Alarms" to com.chandu.gatesystem.ai2027.ui.Routes.ALARMS, "AI Teacher" to com.chandu.gatesystem.ai2027.ui.Routes.AI, "Question bank" to com.chandu.gatesystem.ai2027.ui.Routes.BANK, "Backup & restore" to com.chandu.gatesystem.ai2027.ui.Routes.BACKUP)
                    .forEach { (t, r) -> HunterButton(t.uppercase(), { nav(r) }, Modifier.fillMaxWidth(), outlined = true) }
            }
            SystemPanel("About") {
                KeyValue("Version", BuildConfig.VERSION_NAME); KeyValue("App id", "com.chandu.gatesystem.ai2027"); KeyValue("Plan", "${c.plan.start} → ${c.plan.end} (${c.plan.plan.size} days)")
                Dim("A standalone app: it does not read or change your older GATE Solo Leveling app or its data.")
                Dim("Known limits: see FEATURE_STATUS.md in the project. Notably, no previous-year questions are bundled, most topics have a study checklist instead of an authored lesson, and alarms can be limited by your phone maker.")
            }
        }
    }
}
