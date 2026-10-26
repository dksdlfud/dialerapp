package com.example.mydialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class MyInCallService : InCallService() {
    companion object {
        var instance: MyInCallService? = null
        private const val CH = "incoming_call"
        private const val NID = 1001
    }

    private val ringCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_RINGING) cancelIncoming()
        }
    }

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }

    override fun onCallAdded(call: Call) {
        val number = call.details.handle?.schemeSpecificPart
        CallManager.add(call, number?.let { lookupName(it) })
        if (call.state == Call.STATE_RINGING) {
            call.registerCallback(ringCallback)
            showIncoming(CallManager.info.value?.name ?: number ?: "알 수 없음")
        }
        try {
            startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { /* 잠금 화면 등: 전체 화면 알림이 대신 표시 */ }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(ringCallback)
        cancelIncoming()
        CallManager.remove(call)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(state: CallAudioState) {
        CallManager.muted.value = state.isMuted
        CallManager.speaker.value = state.route == CallAudioState.ROUTE_SPEAKER
    }

    @Suppress("DEPRECATION")
    fun toggleMute() { setMuted(!(callAudioState?.isMuted ?: false)) }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val on = callAudioState?.route == CallAudioState.ROUTE_SPEAKER
        setAudioRoute(if (on) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    private fun lookupName(number: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) { null }

    private fun showIncoming(title: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "수신 전화", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title).setContentText("수신 전화")
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true)
            .setContentIntent(pi).setFullScreenIntent(pi, true).build()
        nm.notify(NID, n)
    }

    private fun cancelIncoming() {
        getSystemService(NotificationManager::class.java).cancel(NID)
    }
}
