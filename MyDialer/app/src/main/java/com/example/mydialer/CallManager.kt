package com.example.mydialer

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow

data class CallInfo(val number: String, val state: Int)

object CallManager {
    var call: Call? = null
    val info = MutableStateFlow<CallInfo?>(null)

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish(call)
    }

    fun add(c: Call) { call = c; c.registerCallback(callback); publish(c) }

    fun remove(c: Call) {
        c.unregisterCallback(callback)
        if (call == c) { call = null; info.value = null }
    }

    private fun publish(c: Call) {
        info.value = CallInfo(c.details.handle?.schemeSpecificPart ?: "알 수 없음", c.state)
    }

    fun answer() = call?.answer(0)
    fun reject() = call?.reject(false, null)
    fun hangup() = call?.disconnect()
}
