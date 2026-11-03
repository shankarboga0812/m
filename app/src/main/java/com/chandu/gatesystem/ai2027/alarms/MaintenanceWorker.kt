package com.chandu.gatesystem.ai2027.alarms

import android.content.Context
import androidx.work.*
import com.chandu.gatesystem.ai2027.GateApp
import java.util.concurrent.TimeUnit

/**
 * Safety net: some phones clear alarms. Every ~6 hours (WorkManager minimum is 15 min; this is not a precise timer)
 * the alarms are re-registered and today's quest status is refreshed.
 */
class MaintenanceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as GateApp).container
        return try {
            c.plan.seedIfNeeded()
            AlarmScheduler.rescheduleAll(applicationContext, c.db, c.plan)
            c.study.refreshAll(c.plan.focusDay().day)
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }

    companion object {
        private const val NAME = "gate_maintenance"
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<MaintenanceWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
