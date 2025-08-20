package com.example.simplechat

import java.util.Calendar

class Message {
    public lateinit var ID: String
    public lateinit var payload: String
    public lateinit var time: String

    fun getCSVLine(): String {
        return ID + ";" + payload + ";" + time
    }

    fun generateID() {
        ID = Utils.generateRandomString(20)
    }

    fun setTime() {
        time = ""
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)  // 0-23
        val minute = calendar.get(Calendar.MINUTE)
        if(hour < 10) {
            time += "0" + hour.toString() + ":"
        } else {
            time += hour.toString() + ":"
        }
        if(minute < 10) {
            time += "0" + minute.toString()
        } else {
            time += minute.toString()
        }
    }

}