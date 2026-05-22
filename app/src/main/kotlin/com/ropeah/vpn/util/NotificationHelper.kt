package com.ropeah.vpn.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ropeah.vpn.MainActivity
import com.ropeah.vpn.R
import com.ropeah.vpn.vpn.RopeahVpnService
import com.ropeah.vpn.vpn.TrafficSample

object NotificationHelper {
    const val NOTIF_ID = 1001
    private const val CHANNEL_ID = "ropeah_vpn_status"
    private const val DISCONNECT_REQUEST_CODE = 100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_vpn),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_vpn_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun buildOngoing(
        context: Context,
        serverName: String,
        elapsedMs: Long? = null,
        latencyMs: Long? = null,
    ): Notification {
        ensureChannel(context)

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            context, DISCONNECT_REQUEST_CODE,
            Intent(context, RopeahVpnService::class.java).apply {
                action = RopeahVpnService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val contentText = if (elapsedMs == null) {
            context.getString(R.string.notif_text_connected, serverName)
        } else {
            context.getString(
                R.string.notif_text_connected_detail,
                serverName,
                formatElapsed(elapsedMs),
                formatLatency(context, latencyMs),
            )
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_vpn)
            .setContentTitle(context.getString(R.string.notif_title_connected))
            .setContentText(contentText)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notif_vpn,
                    context.getString(R.string.notif_action_disconnect),
                    disconnectIntent,
                ).build()
            )
            .build()
    }

    fun updateOngoing(
        context: Context,
        serverName: String,
        elapsedMs: Long? = null,
        latencyMs: Long? = null,
    ) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notify(NOTIF_ID, buildOngoing(context, serverName, elapsedMs, latencyMs))
    }

    private fun formatElapsed(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    private fun formatLatency(context: Context, latencyMs: Long?): String = when {
        latencyMs == null || latencyMs == TrafficSample.LATENCY_UNKNOWN || latencyMs < 0 ->
            context.getString(R.string.notif_latency_unknown)
        else -> "${latencyMs} ms"
    }
}
