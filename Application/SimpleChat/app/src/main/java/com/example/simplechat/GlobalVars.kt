package com.example.simplechat

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import java.security.PublicKey

@SuppressLint("StaticFieldLeak")
object GlobalVars {
    lateinit var context: Context
    var userName: String? = null
    var alias: String? = null
    var FCMtoken: String? = null
    var lastMessageReceived: String? = null
    var resumeSignal: CompletableDeferred<String>? = null
    var activeChatUser:  String? = null
    lateinit var activeUserPublicKey: PublicKey
    var isInForeground = true
    var connectedToInternet: Boolean = false
    var url: String = "ws://192.168.1.41:8000"
}