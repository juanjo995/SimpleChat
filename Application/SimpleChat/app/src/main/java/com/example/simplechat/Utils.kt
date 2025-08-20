package com.example.simplechat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object Utils {
    public lateinit var request: Request
    public lateinit var client: OkHttpClient
    public lateinit var webSocket: WebSocket
    public lateinit var monitor: NetworkMonitor

    interface MessageListener {
        fun onNewMessageReceived()
    }
    var messageListenerForChat: MessageListener? = null
    var messageListenerForChatMenu: MessageListener? = null

    private val mutex = Mutex()
    suspend fun connect() {

        mutex.withLock {
            if (isWebSocketConnected() == false) {
                client = OkHttpClient.Builder()
                    .build()
                request = Request.Builder().url(GlobalVars.url + "/app").build()
                webSocket = client.newWebSocket(request, ChatWebSocketListener())

            }
        }
    }

    suspend fun isWebSocketConnected(): Boolean {
        if(::webSocket.isInitialized == true) {
            webSocket.send("ping")
            GlobalVars.lastMessageReceived = ""
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 5000) {
                if (GlobalVars.lastMessageReceived == "pong") return true
                delay(100) // Wait a bit before checking again
            }
            return false
        } else {
            return false
        }
    }

    fun updateFCMtoken(token: String) {
        webSocket.send("newFCMtoken;" + token)
    }

    suspend fun waitForResponse() {
        GlobalVars.resumeSignal = CompletableDeferred()
        GlobalVars.resumeSignal?.await()
    }

    fun Base64ToPublicKey(publicKeyString: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA") // or "EC" if you're using EC keys
        return keyFactory.generatePublic(keySpec)
    }

    fun getPrivateKey(alias: String): PrivateKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw RuntimeException("Private key not found for alias: $alias")

        return privateKey
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun receiveNewMessage() {

        val sender = GlobalVars.lastMessageReceived.toString().split(";")[1]

        var file = File(GlobalVars.context.filesDir, "users.txt")
        if (file.exists()) {
            val users = file.readLines().toMutableList()
            var userExists = false
            for (line in users) {
                if (line.split(";")[0] == sender) {
                    userExists = true
                }
            }
            if (userExists == false) {
                return
            }
        } else {
            return
        }

        val encryptedMessageBase64 = GlobalVars.lastMessageReceived.toString().split(";")[3]
        val encryptedBytes = Base64.decode(encryptedMessageBase64, Base64.DEFAULT)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, getPrivateKey(GlobalVars.alias.toString()))
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        val decryptedMessage = String(decryptedBytes, Charsets.UTF_8)

        val chatFile = File(GlobalVars.context.filesDir, "app/" + sender + "/chat.txt")

        chatFile.appendText("you;" + GlobalVars.lastMessageReceived.toString().split(";")[2] + ";" + decryptedMessage + ";" + GlobalVars.lastMessageReceived.toString().split(";")[4] + "\n")

        // Send delivered signal
        val message = "deliveredMessage;" + sender + ";" + GlobalVars.lastMessageReceived.toString().split(";")[2]
        webSocket.send(message)

        // Set chat to be the first and green
        file = File(GlobalVars.context.filesDir, "users.txt")
        var users = file.readLines().toMutableList()
        for (line in users) {
            if (line.split(";")[0] == sender) {
                users.remove(line)
                break
            }
        }
        users.add(0, sender + ";green")
        file.writeText("")
        file.writeText(users.joinToString("\n"))

        if(isNotificationPermissionGranted(GlobalVars.context) && GlobalVars.isInForeground == false) {
            showNotification(GlobalVars.context)
        } else {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone: Ringtone = RingtoneManager.getRingtone(GlobalVars.context, notification)
            ringtone.play()
        }

        messageListenerForChat?.onNewMessageReceived()
        messageListenerForChatMenu?.onNewMessageReceived()
    }

    fun receiveDeliverMessage() {
        var sender = GlobalVars.lastMessageReceived.toString().split(";")[1]
        var messageID = GlobalVars.lastMessageReceived.toString().split(";")[2]
        val file = File(GlobalVars.context.filesDir, "app/" + sender + "/chat.txt")

        if (file.exists()) {
            var messages = file.readLines().toMutableList()

            for (i in messages.indices) {
                val line = messages[i]
                val parts = line.split(";")
                if (messageID == parts[1]) {
                    val newLine = parts[0] + ";" +
                            parts[1] + ";" +
                            parts[2] + ";" +
                            parts[3] + ";" +
                            "delivered"
                    messages[i] = newLine // ← Modify the list
                    break
                }
            }

            file.writeText("")
            for(line in messages) {
                file.appendText(line + "\n")
            }

            messageListenerForChat?.onNewMessageReceived()
            messageListenerForChatMenu?.onNewMessageReceived()

        } else {
            return
        }
    }

    fun generateRandomString(length: Int): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    fun setChatWhite(userName: String) {
        // If chat is green, back to white
        val file = File(GlobalVars.context.filesDir, "users.txt")
        var users = file.readLines().toMutableList()
        for (i in users.indices) {
            if(users[i].split(";")[0] == userName) {
                if(users[i].split(";")[1] == "green") {
                    users[i] = userName + ";white"
                }
            }
        }
        file.writeText("")
        file.writeText(users.joinToString("\n"))
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showNotification(context: Context) {
        val channelId = "SimpleChatChannelID" // Same as above

        val intent = Intent(context, ChatMenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Replace with your own icon
            .setContentTitle("Message received")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)
        // Show the notification (ID can be used to update/cancel later)
        notificationManager.notify(1, builder.build())
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "SimpleChatChannelID"
            val channelName = "SimpleChat"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Channel for SimpleChat"
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permissions are automatically granted on Android 12 and below
            true
        }
    }
}