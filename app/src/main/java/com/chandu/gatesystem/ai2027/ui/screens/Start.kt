package com.chandu.gatesystem.ai2027.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chandu.gatesystem.ai2027.AppContainer
import com.chandu.gatesystem.ai2027.data.repo.PlanPhase
import com.chandu.gatesystem.ai2027.ui.LocalContainer
import com.chandu.gatesystem.ai2027.ui.components.*
import com.chandu.gatesystem.ai2027.ui.theme.Hunter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Screen 1: system initialisation. Seeds the database and installs the bundled 3000-question bank on first run. */
@Composable
fun SystemInitScreen(onDone: (setupDone: Boolean) -> Unit) {
    val c = LocalContainer.current
    val init by c.init.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { c.initialize() }
    LaunchedEffect(init) { if (init is AppContainer.Init.Ready) { delay(600); onDone(c.settings.setupDone) } }
    ParticleBackground(animate = !c.settings.reduceMotion) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GateEmblem(200.dp, animate = !c.settings.reduceMotion)
            Spacer(Modifier.height(20.dp))
            Text("GATE SOLO LEVELING", style = MaterialTheme.typography.headlineMedium, color = Hunter.Cyan)
            Text("AI SYSTEM · 2027", style = MaterialTheme.typography.labelLarge, color = Hunter.Violet)
            Spacer(Modifier.height(24.dp))
            when (val s = init) {
                is AppContainer.Init.Failed -> {
                    Note("System initialisation failed: ${s.message}. Your saved data was not changed.", NoteKind.ERROR)
                    Spacer(Modifier.height(10.dp)); HunterButton("Retry", { attempt++ })
                }
                is AppContainer.Init.Working -> {
                    Text(s.step.uppercase() + "…", style = MaterialTheme.typography.labelMedium, color = Hunter.TextDim)
                    Spacer(Modifier.height(8.dp))
                    GlowBar(if (s.total <= 0) 0f else s.done.toFloat() / s.total, Modifier.width(240.dp))
                    if (s.step.startsWith("Installing")) Dim("First launch only: ${s.done} / ${s.total}")
                }
                AppContainer.Init.Ready -> Text("SYSTEM ONLINE", style = MaterialTheme.typography.labelLarge, color = Hunter.Green)
                AppContainer.Init.Pending -> Text("BOOTING…", style = MaterialTheme.typography.labelMedium, color = Hunter.TextDim)
            }
        }
    }
}

/** Screen 2: player detection / first-run profile. */
@Composable
fun PlayerDetectionScreen(onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(c.settings.playerName) }
    var step by remember { mutableIntStateOf(0) }
    val lines = listOf("Scanning for a player…", "Candidate found: GATE CS 2027 aspirant", "Target: 85 / 100", "Training window: 114 days")
    LaunchedEffect(Unit) { while (step < lines.size) { delay(650); step++ } }
    val phase = c.plan.phase()
    HunterScaffold("Player detected", animate = !c.settings.reduceMotion) { pad ->
        ScreenColumn(pad) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GateEmblem(150.dp, animate = !c.settings.reduceMotion) }
            SystemPanel("System") { lines.take(step).forEach { Body("> $it", color = Hunter.Cyan) } }
            if (step >= lines.size) {
                SystemPanel("Plan") {
                    KeyValue("Plan window", "${c.plan.start} → ${c.plan.end}"); KeyValue("Days", "${c.plan.plan.size}")
                    KeyValue("Today", when (phase) { PlanPhase.BEFORE -> "Plan starts in ${c.plan.daysUntilStart()} day(s)"; PlanPhase.ACTIVE -> "Day ${c.plan.focusDay().day}"; PlanPhase.AFTER -> "Plan window finished" })
                    Dim("Source of truth: your Word document. Quests, topics and reminder times come only from it.")
                }
                SystemPanel("Name your hunter") {
                    OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text("Player name") }, modifier = Modifier.fillMaxWidth())
                    HunterButton("AWAKEN", {
                        scope.launch {
                            c.settings.playerName = name; c.settings.setupDone = true
                            c.db.player().player()?.let { c.db.player().upsertPlayer(it.copy(name = c.settings.playerName)) }
                            onDone()
                        }
                    }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
