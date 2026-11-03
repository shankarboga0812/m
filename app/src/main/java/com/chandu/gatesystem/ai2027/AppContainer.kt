package com.chandu.gatesystem.ai2027

import android.app.Application
import com.chandu.gatesystem.ai2027.data.*
import com.chandu.gatesystem.ai2027.data.db.AppDatabase
import com.chandu.gatesystem.ai2027.data.repo.*
import com.chandu.gatesystem.ai2027.domain.AiTransport
import com.chandu.gatesystem.ai2027.net.HttpUrlTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Manual dependency container (no DI framework): one database, one set of repositories for the whole process. */
class AppContainer(val app: Application) {
    val settings = SettingsStore(app)
    val clock: Clock = SystemClock
    val db: AppDatabase by lazy { AppDatabase.build(app) }
    val transport: AiTransport = HttpUrlTransport()

    val plan by lazy { PlanRepository(app, db, clock, settings) }
    val content by lazy { ContentRepository(db, clock) }
    val progress by lazy { ProgressEngine(db, plan, clock, settings) }
    val provider by lazy { QuestionProvider(content, settings, transport) }
    val study by lazy { StudyRepository(db, content, plan, progress, provider, settings, clock) }
    val ai by lazy { AiRepository(settings, transport, content) }
    val bank by lazy { BankInstaller(app, db, clock) }
    val backup by lazy { BackupManager(app, db) }

    sealed class Init {
        object Pending : Init()
        data class Working(val step: String, val done: Int, val total: Int) : Init()
        object Ready : Init()
        data class Failed(val message: String) : Init()
    }
    private val _init = MutableStateFlow<Init>(Init.Pending)
    val init: StateFlow<Init> get() = _init

    /** First-run seeding (idempotent; safe on every launch). Player data is never reset. */
    suspend fun initialize() {
        if (_init.value is Init.Working || _init.value is Init.Ready) return
        try {
            _init.value = Init.Working("Reading the 114-day plan", 0, 1)
            plan.seedIfNeeded()
            bank.installIfNeeded { d, t -> _init.value = Init.Working("Installing question bank", d, t) }
            _init.value = Init.Working("Updating hunter status", 1, 1)
            study.refreshAll(plan.focusDay().day)
            _init.value = Init.Ready
        } catch (e: Exception) {
            _init.value = Init.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
