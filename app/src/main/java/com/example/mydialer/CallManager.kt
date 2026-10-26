package com.example.mydialer

import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow

/** Immutable snapshot consumed by the in-call UI. */
data class CallInfo(
    val id: Int,
    val number: String,
    val name: String?,
    val state: Int,
    val connectTime: Long
)

/**
 * Tracks each Telecom Call independently. The ringing call gets UI focus first;
 * once ringing ends, the user-selected call is preferred. Call switching waits
 * for the current active call to become held before unholding the selected one.
 */
object CallManager {
    private data class ManagedCall(
        val id: Int,
        val call: Call,
        val name: String?,
        val callback: Call.Callback
    )

    private val calls = LinkedHashMap<Call, ManagedCall>()
    private var nextId = 1
    private var pendingSwitchId: Int? = null

    val info = MutableStateFlow<CallInfo?>(null)
    val allCalls = MutableStateFlow<List<CallInfo>>(emptyList())
    val selectedCallId = MutableStateFlow<Int?>(null)
    val muted = MutableStateFlow(false)
    val speaker = MutableStateFlow(false)
    val audioRoute = MutableStateFlow(CallAudioState.ROUTE_EARPIECE)
    val supportedAudioRoutes = MutableStateFlow(0)
    val lastError = MutableStateFlow<String?>(null)

    /** The call currently receiving UI actions. */
    val call: Call?
        get() = synchronized(this) { preferredCallLocked()?.call }

    @Synchronized
    fun add(c: Call, contactName: String?) {
        if (calls.containsKey(c)) {
            refreshLocked()
            return
        }

        val id = nextId++
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = refresh()
            override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
        }
        calls[c] = ManagedCall(id, c, contactName, callback)
        if (c.state == Call.STATE_RINGING || selectedCallId.value == null) selectedCallId.value = id
        lastError.value = null
        try {
            c.registerCallback(callback)
        } catch (_: IllegalStateException) {
            // Telecom may already be tearing the call down; removal remains safe.
        }
        refreshLocked()
    }

    @Synchronized
    fun remove(c: Call) {
        val managed = calls.remove(c)
        if (managed != null) {
            try {
                c.unregisterCallback(managed.callback)
            } catch (_: IllegalStateException) {
                // Framework teardown may have removed the callback already.
            } catch (_: IllegalArgumentException) {
                // Ignore callbacks already detached by Telecom.
            }
            if (selectedCallId.value == managed.id) selectedCallId.value = null
            if (pendingSwitchId == managed.id) pendingSwitchId = null
        }
        refreshLocked()
    }

    @Synchronized
    fun hasRingingCall(): Boolean = calls.keys.any { it.state == Call.STATE_RINGING }

    /** Selects a call, safely holding the active call first when switching to a held call. */
    @Synchronized
    fun switchToCall(id: Int) {
        val target = calls.values.firstOrNull { it.id == id }
        if (target == null || target.call.state == Call.STATE_DISCONNECTED) {
            lastError.value = "해당 통화는 더 이상 사용할 수 없습니다."
            refreshLocked()
            return
        }
        if (target.call.state == Call.STATE_RINGING) {
            selectedCallId.value = target.id
            lastError.value = null
            refreshLocked()
            return
        }
        if (target.call.state != Call.STATE_HOLDING) {
            selectedCallId.value = target.id
            lastError.value = null
            refreshLocked()
            return
        }

        val active = calls.values.firstOrNull {
            it.id != target.id && it.call.state == Call.STATE_ACTIVE
        }
        if (active == null) {
            try {
                target.call.unhold()
                selectedCallId.value = target.id
                pendingSwitchId = null
                lastError.value = null
            } catch (_: RuntimeException) {
                lastError.value = "대기 중인 통화를 재개하지 못했습니다."
            }
            refreshLocked()
            return
        }

        pendingSwitchId = target.id
        lastError.value = null
        try {
            active.call.hold()
        } catch (_: RuntimeException) {
            pendingSwitchId = null
            lastError.value = "현재 통화를 보류할 수 없어 통화를 전환하지 못했습니다."
        }
        refreshLocked()
    }

    private fun preferredCallLocked(): ManagedCall? {
        val live = calls.values.filter { it.call.state != Call.STATE_DISCONNECTED }
        // A ringing call must take priority so accept/reject never targets the wrong call.
        live.firstOrNull { it.call.state == Call.STATE_RINGING }?.let { return it }
        selectedCallId.value?.let { id -> live.firstOrNull { it.id == id }?.let { return it } }
        return live.maxByOrNull { priority(it.call.state) }
    }

    private fun priority(state: Int): Int = when (state) {
        Call.STATE_ACTIVE -> 6
        Call.STATE_DIALING -> 5
        Call.STATE_CONNECTING -> 4
        Call.STATE_HOLDING -> 3
        Call.STATE_NEW -> 2
        Call.STATE_RINGING -> 7
        else -> 0
    }

    private fun refresh() {
        synchronized(this) { refreshLocked() }
    }

    private fun refreshLocked() {
        completePendingSwitchLocked()
        val live = calls.values
            .filter { it.call.state != Call.STATE_DISCONNECTED }
            .sortedByDescending { priority(it.call.state) }
            .map { managed ->
                val current = managed.call
                CallInfo(
                    id = managed.id,
                    number = current.details.handle?.schemeSpecificPart ?: "알 수 없음",
                    name = managed.name,
                    state = current.state,
                    connectTime = current.details.connectTimeMillis
                )
            }
        allCalls.value = live
        val managed = preferredCallLocked()
        info.value = managed?.let { selected -> live.firstOrNull { it.id == selected.id } }
        if (selectedCallId.value != null && live.none { it.id == selectedCallId.value }) {
            selectedCallId.value = live.firstOrNull()?.id
        }
    }

    /** Called on state callbacks after hold() completes. */
    private fun completePendingSwitchLocked() {
        val targetId = pendingSwitchId ?: return
        val target = calls.values.firstOrNull { it.id == targetId }
        if (target == null || target.call.state == Call.STATE_DISCONNECTED) {
            pendingSwitchId = null
            return
        }
        val anotherActive = calls.values.any {
            it.id != targetId && it.call.state in setOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING)
        }
        if (!anotherActive) {
            pendingSwitchId = null
            try {
                target.call.unhold()
                selectedCallId.value = target.id
                lastError.value = null
            } catch (_: RuntimeException) {
                lastError.value = "대기 중인 통화를 재개하지 못했습니다."
            }
        }
    }

    private fun perform(action: (Call) -> Unit, noCallMessage: String = "현재 통화가 없습니다.") {
        val target = call
        if (target == null) {
            lastError.value = noCallMessage
            return
        }
        try {
            lastError.value = null
            action(target)
        } catch (_: SecurityException) {
            lastError.value = "통화 제어 권한이 없어 작업을 수행하지 못했습니다."
        } catch (_: IllegalStateException) {
            lastError.value = "현재 통화 상태에서는 이 작업을 수행할 수 없습니다."
        } catch (_: IllegalArgumentException) {
            lastError.value = "통화 요청이 올바르지 않습니다."
        } catch (_: RuntimeException) {
            lastError.value = "통화 제어 중 문제가 발생했습니다."
        }
    }

    fun answer() = perform({ it.answer(0) })
    fun reject() = perform({ it.reject(false, null) })
    fun hangup() = perform({ it.disconnect() })

    fun toggleHold() = perform({ current ->
        when (current.state) {
            Call.STATE_HOLDING -> current.unhold()
            Call.STATE_ACTIVE -> current.hold()
            else -> lastError.value = "현재 통화 상태에서는 보류를 사용할 수 없습니다."
        }
    })

    fun dtmfStart(ch: Char) = perform({ it.playDtmfTone(ch) }, "DTMF를 보낼 통화가 없습니다.")
    fun dtmfStop() = perform({ it.stopDtmfTone() }, "DTMF를 보낼 통화가 없습니다.")
}
