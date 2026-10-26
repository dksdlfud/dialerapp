package com.example.mydialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class MyInCallService : InCallService() {
    companion object {
        @Volatile
        var instance: MyInCallService? = null

        private const val CHANNEL_ID = "incoming_call"
        private const val NOTIFICATION_ID = 1001
    }

    private val ringCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_RINGING) {
                cancelIncomingIfNoRingingCall()
            } else {
                showIncoming(CallManager.info.value?.name ?: call.details.handle?.schemeSpecificPart ?: "알 수 없음")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val number = call.details.handle?.schemeSpecificPart
        CallManager.add(call, number?.let(::lookupName))
        callAudioState?.let { state ->
            CallManager.muted.value = state.isMuted
            CallManager.audioRoute.value = state.route
            CallManager.supportedAudioRoutes.value = state.supportedRouteMask
            CallManager.speaker.value = state.route == CallAudioState.ROUTE_SPEAKER
        }
        if (call.state == Call.STATE_RINGING) {
            try {
                call.registerCallback(ringCallback)
            } catch (_: IllegalStateException) {
                // The call could be transitioning away from ringing already.
            }
            showIncoming(CallManager.info.value?.name ?: number ?: "알 수 없음")
        }

        // The full-screen notification is the fallback for devices that block
        // activity launches from a background InCallService.
        try {
            startActivity(
                Intent(this, CallActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            )
        } catch (_: Exception) {
            // The incoming-call notification remains available to open the UI.
        }
    }

    override fun onCallRemoved(call: Call) {
        try {
            call.unregisterCallback(ringCallback)
        } catch (_: IllegalStateException) {
            // This callback is only registered while ringing.
        } catch (_: IllegalArgumentException) {
            // Some framework versions report an already-removed callback this way.
        }
        CallManager.remove(call)
        cancelIncomingIfNoRingingCall()
        super.onCallRemoved(call)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(state: CallAudioState) {
        super.onCallAudioStateChanged(state)
        CallManager.muted.value = state.isMuted
        CallManager.audioRoute.value = state.route
        CallManager.supportedAudioRoutes.value = state.supportedRouteMask
        CallManager.speaker.value = state.route == CallAudioState.ROUTE_SPEAKER
    }

    /** Set an explicitly selected route only when Telecom reports it as supported. */
    @Suppress("DEPRECATION")
    fun setAudioRouteFromUi(route: Int) {
        try {
            val audio = callAudioState ?: run {
                CallManager.lastError.value = "통화 오디오가 아직 준비되지 않았습니다."
                return
            }
            if ((audio.supportedRouteMask and route) == 0) {
                CallManager.lastError.value = "현재 기기에서 이 오디오 경로를 사용할 수 없습니다."
                return
            }
            setAudioRoute(route)
            CallManager.lastError.value = null
        } catch (_: SecurityException) {
            CallManager.lastError.value = "오디오 경로를 변경할 권한이 없습니다."
        } catch (_: IllegalStateException) {
            CallManager.lastError.value = "통화 오디오가 아직 준비되지 않았습니다."
        } catch (_: RuntimeException) {
            CallManager.lastError.value = "오디오 경로를 변경하지 못했습니다."
        }
    }

    @Suppress("DEPRECATION")
    fun toggleMute() {
        try {
            val current = callAudioState?.isMuted ?: false
            setMuted(!current)
            CallManager.lastError.value = null
        } catch (_: SecurityException) {
            CallManager.lastError.value = "음소거를 변경할 수 없습니다."
        } catch (_: IllegalStateException) {
            CallManager.lastError.value = "통화 오디오가 아직 준비되지 않았습니다."
        } catch (_: RuntimeException) {
            CallManager.lastError.value = "음소거 설정을 변경하는 중 문제가 발생했습니다."
        }
    }

    /** Prefer the previous private route when leaving speakerphone. */
    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        try {
            val audio = callAudioState ?: run {
                CallManager.lastError.value = "통화 오디오가 아직 준비되지 않았습니다."
                return
            }
            val supported = audio.supportedRouteMask
            val targetRoute = if (audio.route == CallAudioState.ROUTE_SPEAKER) {
                when {
                    (supported and CallAudioState.ROUTE_BLUETOOTH) != 0 -> CallAudioState.ROUTE_BLUETOOTH
                    (supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0 -> CallAudioState.ROUTE_WIRED_HEADSET
                    (supported and CallAudioState.ROUTE_EARPIECE) != 0 -> CallAudioState.ROUTE_EARPIECE
                    else -> audio.route
                }
            } else if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) {
                CallAudioState.ROUTE_SPEAKER
            } else {
                CallManager.lastError.value = "이 통화에서 스피커를 사용할 수 없습니다."
                return
            }
            setAudioRoute(targetRoute)
            CallManager.lastError.value = null
        } catch (_: SecurityException) {
            CallManager.lastError.value = "스피커 설정을 변경할 수 없습니다."
        } catch (_: IllegalStateException) {
            CallManager.lastError.value = "통화 오디오가 아직 준비되지 않았습니다."
        } catch (_: RuntimeException) {
            CallManager.lastError.value = "오디오 경로를 변경하는 중 문제가 발생했습니다."
        }
    }

    private fun lookupName(number: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun showIncoming(title: String) {
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "수신 전화", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "수신 전화 알림과 통화 화면 열기"
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                )
            }
            val intent = Intent(this, CallActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            val pendingIntent = PendingIntent.getActivity(
                this,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }
            val notification = builder
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(title.ifBlank { "알 수 없는 번호" })
                .setContentText("수신 전화")
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission or full-screen-intent access may be disabled.
        } catch (_: RuntimeException) {
            // Never let a notification failure terminate Telecom callback handling.
        }
    }

    private fun cancelIncomingIfNoRingingCall() {
        if (CallManager.hasRingingCall()) return
        try {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (_: RuntimeException) {
            // Best-effort cleanup.
        }
    }
}
