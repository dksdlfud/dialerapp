package com.example.mydialer

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class MyInCallService : InCallService() {
    companion object { var instance: MyInCallService? = null }

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }

    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onCallRemoved(call: Call) = CallManager.remove(call)

    fun toggleMute(): Boolean { val m = !(callAudioState?.isMuted ?: false); setMuted(m); return m }

    fun toggleSpeaker(): Boolean {
        val on = callAudioState?.route == CallAudioState.ROUTE_SPEAKER
        setAudioRoute(if (on) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
        return !on
    }
}
