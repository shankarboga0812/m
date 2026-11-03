package com.chandu.gatesystem.ai2027.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chandu.gatesystem.ai2027.AppContainer
import com.chandu.gatesystem.ai2027.data.*
import com.chandu.gatesystem.ai2027.data.db.*
import com.chandu.gatesystem.ai2027.data.repo.*
import com.chandu.gatesystem.ai2027.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Suppress("UNCHECKED_CAST")
inline fun <reified T : ViewModel> factoryOf(crossinline create: () -> T) = object : ViewModelProvider.Factory {
    override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
}

data class QuestRow(val entity: DailyQuestEntity, val spec: QuestSpec, val progress: QuestEvaluator.Progress)
data class MissedItem(val day: Int, val date: String, val row: QuestRow)
data class DayState(val day: PlanDay, val rows: List<QuestRow>, val percent: Int, val facts: DayFacts, val missed: List<MissedItem>, val isToday: Boolean, val dueToday: List<RevisionEntry>)

/** Shared by Dashboard, Daily Quests, Calendar, Player Status and the other progress screens. */
class HomeVm(val c: AppContainer) : ViewModel() {
    val player: StateFlow<PlayerEntity?> = c.db.player().playerFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val selectedDay = MutableStateFlow(c.plan.focusDay().day)
    val summaries: StateFlow<List<DailySummaryEntity>> = c.db.player().summaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val events: StateFlow<List<ProgressEventEntity>> = c.db.player().events().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val achievements: StateFlow<List<AchievementEntity>> = c.db.player().achievements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentXp: StateFlow<List<XpTransactionEntity>> = c.db.player().recentXp().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val refresh = MutableStateFlow(0)

    val dayState: StateFlow<DayState?> = combine(selectedDay, player, refresh) { d, _, _ -> d }
        .map { load(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun select(day: Int) { selectedDay.value = day.coerceIn(1, c.plan.plan.size) }
    fun reload() { refresh.value++ }

    private suspend fun rowsFor(day: Int, today: LocalDate): Pair<List<QuestRow>, DayFacts> {
        val facts = c.progress.factsFor(day)
        return c.db.player().questsForDayOnce(day).map { e -> val s = e.toSpec(); QuestRow(e, s, QuestEvaluator.evaluate(s, facts, today)) } to facts
    }

    private suspend fun load(day: Int): DayState? {
        val pd = c.plan.plan.day(day) ?: return null
        val today = c.clock.today()
        val (rows, facts) = rowsFor(day, today)
        val specs = rows.map { it.spec }
        val focus = c.plan.focusDay(today).day
        val missed = ArrayList<MissedItem>()
        if (day == focus && c.plan.phase(today) == PlanPhase.ACTIVE) {
            for (d in maxOf(1, day - 7) until day) {
                val (r, _) = rowsFor(d, today)
                r.filter { it.progress.status == QuestStatus.MISSED }.forEach { missed += MissedItem(d, c.plan.plan.day(d)!!.date, it) }
            }
        }
        val due = c.db.mistakes().pendingRevisions().map { it.toDomain() }.filter { it.dueDate <= today }
        return DayState(pd, rows, QuestEvaluator.dayPercent(specs, facts, today), facts, missed.take(12), day == focus, due)
    }

    fun toggleManual(questId: String, done: Boolean) = viewModelScope.launch { c.study.setManualQuest(questId, done); reload() }
    fun setWorkout(day: Int, s: WorkoutStatus?) = viewModelScope.launch { c.study.setWorkout(day, s); reload() }
    fun markEventsSeen() = viewModelScope.launch { c.db.player().markEventsSeen() }
}

// ---------------------------------------------------------------------------------------------------------------

sealed class SetLoad { object Loading : SetLoad(); data class Error(val msg: String) : SetLoad(); object Ready : SetLoad() }

/** Preparation / Revision / GA / Error-Monster sets: five questions per batch, drafts saved on every answer. */
class PracticeVm(val c: AppContainer, val sessionId: String) : ViewModel() {
    val load = MutableStateFlow<SetLoad>(SetLoad.Loading)
    val view = MutableStateFlow<PracticeView?>(null)
    val state = MutableStateFlow<PracticeState?>(null)
    val natText = MutableStateFlow<Map<String, String>>(emptyMap())
    val review = MutableStateFlow<BatchReview?>(null)
    val report = MutableStateFlow<PracticeReport?>(null)
    val busy = MutableStateFlow(false)
    val aiText = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiBusy = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    private var lastTouch = System.currentTimeMillis()

    init { reloadSession() }

    fun reloadSession() = viewModelScope.launch {
        val v = c.study.practiceView(sessionId)
        if (v == null) { load.value = SetLoad.Error("This set no longer exists."); return@launch }
        view.value = v; state.value = v.state
        natText.value = v.state.answers.filter { it.value.nat != null }.mapValues { Question.fmt(it.value.nat!!) }
        if (v.state.isComplete) report.value = c.study.practiceReport(sessionId)
        load.value = SetLoad.Ready
    }

    private fun touch(qid: String) {
        val now = System.currentTimeMillis(); val dt = ((now - lastTouch) / 1000).toInt().coerceIn(0, 600); lastTouch = now
        state.value = state.value?.let { PracticeFlow.addTime(it, qid, dt) }
    }

    fun select(qid: String, sel: Set<Int>) {
        val s = state.value ?: return
        touch(qid)
        val st = PracticeFlow.answer(state.value!!, qid, Response(selected = sel))
        state.value = st; persist(st)
    }

    fun nat(qid: String, text: String) {
        val s = state.value ?: return
        natText.value = natText.value + (qid to text)
        touch(qid)
        val v = NatInput.parse(text)
        val st = if (v == null) s.copy(answers = s.answers - qid) else PracticeFlow.answer(state.value!!, qid, Response(nat = v))
        state.value = st; persist(st)
    }

    private fun persist(st: PracticeState) { viewModelScope.launch { c.study.saveDraft(sessionId, st) } }

    fun submit() {
        val st = state.value ?: return
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val r = c.study.submitBatch(sessionId, st)
                review.value = r
                val v = c.study.practiceView(sessionId); view.value = v; state.value = v?.state
                if (v?.state?.isComplete == true) report.value = c.study.practiceReport(sessionId)
                if (c.settings.telegramRelay && r.setComplete) c.ai.relayTelegram("Set completed: ${v?.session?.title} — ${report.value?.report?.correct ?: 0}/${report.value?.report?.total ?: 0} correct")
            } catch (e: Exception) { message.value = "Could not grade: ${e.message}" } finally { busy.value = false }
        }
    }

    fun nextBatch() { review.value = null; lastTouch = System.currentTimeMillis() }

    fun askAi(g: Graded) {
        aiBusy.value = g.question.id
        viewModelScope.launch {
            when (val r = c.ai.explain(g.question, g.response)) {
                is AiResult.Ok -> aiText.value = aiText.value + (g.question.id to listOf("Why wrong: ${r.value.whyWrong}", "Misconception: ${r.value.misconception}", "Steps: ${r.value.steps}", "Shortcut: ${r.value.shortcut}", "Next: ${r.value.practiceNext}").filter { it.substringAfter(": ").isNotBlank() }.joinToString("\n"))
                is AiResult.Fail -> aiText.value = aiText.value + (g.question.id to "AI Teacher unavailable: ${r.message}")
            }
            aiBusy.value = null
        }
    }
    fun reportAmbiguous(qid: String) = viewModelScope.launch { c.study.reportAmbiguous(qid); message.value = "Flagged as ambiguous. It will not be used again and is excluded from exam scoring." }
}

// ---------------------------------------------------------------------------------------------------------------

/** Exam Set and mocks: all questions on one screen, real-time countdown, autosave, frozen keys. */
class ExamVm(val c: AppContainer, val examId: String) : ViewModel() {
    val load = MutableStateFlow<SetLoad>(SetLoad.Loading)
    val session = MutableStateFlow<ExamSessionEntity?>(null)
    val views = MutableStateFlow<List<QuestionView>>(emptyList())
    val state = MutableStateFlow<ExamState?>(null)
    val natText = MutableStateFlow<Map<String, String>>(emptyMap())
    val current = MutableStateFlow<String?>(null)
    val review = MutableStateFlow<ExamReview?>(null)
    val submitting = MutableStateFlow(false)
    val aiText = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiBusy = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    private var timer: Job? = null

    init { open() }

    private fun open() = viewModelScope.launch {
        val v = c.study.examView(examId)
        if (v == null) { load.value = SetLoad.Error("This exam no longer exists."); return@launch }
        session.value = v.session; views.value = v.views; state.value = v.state
        natText.value = v.state.responses.filter { it.value.nat != null }.mapValues { Question.fmt(it.value.nat!!) }
        current.value = v.views.firstOrNull()?.id
        current.value?.let { id -> state.value = state.value?.let { ExamFlow.visit(it, id) } }
        if (v.session.status == "SUBMITTED") review.value = c.study.examReview(examId)
        else if (v.state.timeUp) submit()
        else startTimer()
        load.value = SetLoad.Ready
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            var n = 0
            while (true) {
                delay(1000)
                val s = state.value ?: break
                if (s.submitted) break
                val ns = ExamFlow.tick(s, 1, current.value)
                state.value = ns
                if (++n % 10 == 0) c.study.saveExam(examId, ns)
                if (ns.timeUp) { submit(); break }
            }
        }
    }

    fun focus(id: String) { current.value = id; state.value = state.value?.let { if (it.submitted) it else ExamFlow.visit(it, id) } }
    fun select(id: String, sel: Set<Int>) { val s = state.value ?: return; if (s.submitted) return; update(ExamFlow.answer(s, id, Response(selected = sel)), id) }
    fun nat(id: String, text: String) {
        val s = state.value ?: return; if (s.submitted) return
        natText.value = natText.value + (id to text)
        val v = NatInput.parse(text)
        update(if (v == null) ExamFlow.clear(s, id) else ExamFlow.answer(s, id, Response(nat = v)), id)
    }
    fun clear(id: String) { val s = state.value ?: return; if (s.submitted) return; natText.value = natText.value - id; update(ExamFlow.clear(s, id), id) }
    fun mark(id: String) { val s = state.value ?: return; if (s.submitted) return; update(ExamFlow.toggleMark(s, id), id) }
    private fun update(ns: ExamState, id: String) { current.value = id; state.value = ns; viewModelScope.launch { c.study.saveExam(examId, ns) } }

    fun saveNow() { val s = state.value ?: return; if (!s.submitted) viewModelScope.launch { c.study.saveExam(examId, s) } }

    fun submit() {
        val s = state.value ?: return
        if (submitting.value || s.submitted) return
        submitting.value = true; timer?.cancel()
        viewModelScope.launch {
            try { review.value = c.study.submitExam(examId, s); state.value = review.value?.state ?: s.copy(submitted = true)
                if (c.settings.telegramRelay) review.value?.let { c.ai.relayTelegram("${it.session.title}: ${it.report.netMarks}/${it.report.maxMarks} (${"%.1f".format(it.report.percent)}%)") }
            } catch (e: Exception) { message.value = "Could not submit: ${e.message}"; submitting.value = false; startTimer() }
        }
    }

    fun askAi(g: Graded) {
        aiBusy.value = g.question.id
        viewModelScope.launch {
            when (val r = c.ai.explain(g.question, g.response)) {
                is AiResult.Ok -> aiText.value = aiText.value + (g.question.id to listOf("Why wrong: ${r.value.whyWrong}", "Misconception: ${r.value.misconception}", "Steps: ${r.value.steps}", "Shortcut: ${r.value.shortcut}").filter { it.substringAfter(": ").isNotBlank() }.joinToString("\n"))
                is AiResult.Fail -> aiText.value = aiText.value + (g.question.id to "AI Teacher unavailable: ${r.message}")
            }
            aiBusy.value = null
        }
    }
    fun reportAmbiguous(qid: String) = viewModelScope.launch { c.study.reportAmbiguous(qid); message.value = "Flagged. It is excluded from this report next time it loads." }

    override fun onCleared() { saveNowBlocking(); super.onCleared() }
    private fun saveNowBlocking() { /* last autosave happens via update()/timer; nothing blocking is allowed here */ }
}
