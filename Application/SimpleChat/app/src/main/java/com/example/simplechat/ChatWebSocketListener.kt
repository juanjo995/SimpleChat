package com.example.simplechat

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File

class ChatWebSocketListener() : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        val fileName = "profile.txt"
        val file = File(GlobalVars.context.filesDir, fileName)
        if (file.exists()) {
            val lines = file.readLines()
            if (lines.size != 0) {
                GlobalVars.userName = lines.getOrNull(0)
                GlobalVars.alias = lines.getOrNull(1)
            }
        }

        if (GlobalVars.userName == null) {
            webSocket.send("asociateUserToConexion;" + "null-" + Utils.generateRandomString(10))
        } else {
            webSocket.send("asociateUserToConexion;" + GlobalVars.userName)
        }

        webSocket.send("messagesForMe?")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {

        GlobalVars.lastMessageReceived = text

        GlobalVars.resumeSignal?.complete("OK")

        if (GlobalVars.lastMessageReceived != "pong") {

            if (GlobalVars.lastMessageReceived.toString().split(";")[0] == "ping") {
                Utils.webSocket.send(
                    "pong;" + GlobalVars.lastMessageReceived.toString().split(";")[1]
                )
            }

            if (GlobalVars.lastMessageReceived.toString().split(";")[0] == "newMessage") {
                Utils.receiveNewMessage()
            }
            if (GlobalVars.lastMessageReceived.toString().split(";")[0] == "deliveredMessage") {
                Utils.receiveDeliverMessage()
            }
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        println("Closing: $code / $reason")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
        println("Closing: $code / $reason")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        println("Error: ${t.message}")
    }
}