package com.chandu.gatesystem.ai2027

import android.app.Application
import com.chandu.gatesystem.ai2027.alarms.NotificationHelper
import com.chandu.gatesystem.ai2027.alarms.MaintenanceWorker

class GateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
        MaintenanceWorker.schedule(this)
    }
}
