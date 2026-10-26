package com.example.mydialer

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow

data class CallInfo(val number: String, val name: String?, val state: Int, val connectTime: Long)

object CallManager {
    var call: Call? = null
    private var name: String? = null
    val info = MutableStateFlow<CallInfo?>(null)
    val muted = MutableStateFlow(false)
    val speaker = MutableStateFlow(false)

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish(call)
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish(call)
    }

    fun add(c: Call, contactName: String?) {
        call = c; name = contactName
        c.registerCallback(callback); publish(c)
    }

    fun remove(c: Call) {
        c.unregisterCallback(callback)
        if (call == c) { call = null; info.value = null }
    }

    private fun publish(c: Call) {
        info.value = CallInfo(
            c.details.handle?.schemeSpecificPart ?: "알 수 없음",
            name, c.state, c.details.connectTimeMillis
        )
    }

    fun answer() = call?.answer(0)
    fun reject() = call?.reject(false, null)
    fun hangup() = call?.disconnect()
    fun toggleHold() { call?.let { if (it.state == Call.STATE_HOLDING) it.unhold() else it.hold() } }
    fun dtmfStart(ch: Char) { call?.playDtmfTone(ch) }
    fun dtmfStop() { call?.stopDtmfTone() }
}
