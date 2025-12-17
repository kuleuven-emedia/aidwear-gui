package com.emedia.zeromqcontroller

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class IpManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ZMQ_APP_PREFS", Context.MODE_PRIVATE)

    fun saveIpAddress(ip: String) {
        prefs.edit { putString("IP_ADDRESS", ip) }
    }

    fun getIpAddress(): String {
        // Return a default IP if none is saved
        return prefs.getString("IP_ADDRESS", "tcp://192.168.1.100:5555") ?: "tcp://192.168.1.100:5555"
    }
}
