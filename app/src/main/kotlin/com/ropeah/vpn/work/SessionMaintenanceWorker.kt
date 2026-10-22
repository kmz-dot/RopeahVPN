package com.ropeah.vpn.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ropeah.vpn.data.ApiClient
import com.ropeah.vpn.data.ApiException
import com.ropeah.vpn.data.ConfigSync
import com.ropeah.vpn.data.PingRequest
import com.ropeah.vpn.data.ServerRepository
import com.ropeah.vpn.data.SessionRepository
import java.util.concurrent.TimeUnit

/**
 * Background backstop (15-minute platform minimum). It revalidates the session
 * and refreshes the pushed config; it is deliberately NOT the expiry mechanism —
 * expiry is enforced locally by [com.ropeah.vpn.data.SessionGuard] and by the
 * running VPN service.
 */
class SessionMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionRepo = SessionRepository(applicationContext)
        val serverRepo = ServerRepository(applicationContext)
        val api = ApiClient()

        val session = sessionRepo.readOnce() ?: return Result.success()
        val deviceNow = System.currentTimeMillis()

        try {
            val response = api.ping(
                token = session.token,
                body = PingRequest(configVersion = session.configVersion, connected = false),
            )
            if (response.revoked) {
                sessionRepo.clear()
                return Result.success()
            }
            sessionRepo.updateFromServer(
                serverTime = response.serverTime ?: (deviceNow + session.serverOffsetMs),
                expiresAt = response.expiresAt,
                configVersion = response.configVersion,
                deviceNow = deviceNow,
            )
        } catch (e: ApiException.Unauthorized) {
            sessionRepo.clear()
            return Result.success()
        } catch (e: ApiException.Forbidden) {
            val serverTime = e.serverTime
            if (serverTime != null) {
                sessionRepo.updateFromServer(serverTime, e.expiresAt, null, deviceNow)
            }
            return Result.success()
        } catch (e: ApiException) {
            return Result.retry()
        }

        val refreshed = sessionRepo.readOnce() ?: return Result.success()
        ConfigSync(sessionRepo, serverRepo, api).sync(refreshed, force = false)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "v2eon-session-maintenance"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<SessionMaintenanceWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
