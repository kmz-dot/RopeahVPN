package com.ropeah.vpn

import android.app.Application
import com.ropeah.vpn.util.NotificationHelper

class RopeahApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }
}
