package com.example.simplechat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.Context
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KeepAliveService : Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
    companion object {
        var isRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if(isRunning) return START_STICKY
        isRunning = true

        startForegroundServiceWithNotification()

        if (pingJob?.isActive != true) {
            pingJob = coroutineScope.launch {
                while (true) {
                    Utils.connect()
                    delay(120_000) // Wait 2 minutes
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "my_foreground_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, ChatMenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(GlobalVars.context, channelId)
            .setContentIntent(pendingIntent) // Attach the pending intent
            .setAutoCancel(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        coroutineScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}