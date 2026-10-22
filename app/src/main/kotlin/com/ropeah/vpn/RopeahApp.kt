package com.ropeah.vpn

import android.app.Application
import com.ropeah.vpn.util.NotificationHelper
import com.ropeah.vpn.work.SessionMaintenanceWorker

class RopeahApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        SessionMaintenanceWorker.enqueue(this)
    }
}
