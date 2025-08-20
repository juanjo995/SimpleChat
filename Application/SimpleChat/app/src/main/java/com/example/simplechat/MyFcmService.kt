package com.example.simplechat

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // TODO: Send this token to your server via your API
        sendTokenToServer(token)
        println("ON NEW TOKEN INVOKED")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Handle incoming FCM message here
        GlobalVars.context = applicationContext
        val intent = Intent(GlobalVars.context, KeepAliveService::class.java)
        ContextCompat.startForegroundService(GlobalVars.context, intent)

    }

    private fun sendTokenToServer(token: String) {
        val intent = Intent(GlobalVars.context, KeepAliveService::class.java)
        ContextCompat.startForegroundService(GlobalVars.context, intent)

        Utils.updateFCMtoken(token)
    }
}