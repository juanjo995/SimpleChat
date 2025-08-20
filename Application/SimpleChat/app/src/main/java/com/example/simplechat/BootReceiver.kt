package com.example.simplechat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebSocketWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Start your WebSocket logic here

        GlobalVars.context = applicationContext

        GlobalVars.isInForeground = false

        Utils.createNotificationChannel(GlobalVars.context)

        withContext(Dispatchers.IO) {
            Utils.connect()
        }

        Utils.monitor = NetworkMonitor(applicationContext)
        Utils.monitor.start (
            // Trigger WebSocket reconnect or anything else
            onAvailable = {
                GlobalVars.connectedToInternet = true
                CoroutineScope(Dispatchers.IO).launch {
                    Utils.connect()
                }
                val intent = Intent(GlobalVars.context, KeepAliveService::class.java)
                ContextCompat.startForegroundService(GlobalVars.context, intent)
            },
            onLost = {
                GlobalVars.connectedToInternet = false
                GlobalVars.context.stopService(Intent(GlobalVars.context, KeepAliveService::class.java))
            }
        )

        val intent = Intent(GlobalVars.context, KeepAliveService::class.java)
        ContextCompat.startForegroundService(GlobalVars.context, intent)

        return Result.success()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val workRequest = OneTimeWorkRequestBuilder<WebSocketWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "WebSocketStartupWork",
                ExistingWorkPolicy.KEEP, // or KEEP depending on behavior you want
                workRequest
            )

        }
    }
}