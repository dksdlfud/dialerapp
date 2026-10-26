package com.example.mydialer

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Call as CallIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { CallScreen { finish() } } }
    }
}

fun fmtDuration(sec: Long): String =
    if (sec >= 3600) String.format("%d:%02d:%02d", sec / 3600, sec % 3600 / 60, sec % 60)
    else String.format("%02d:%02d", sec / 60, sec % 60)

@Composable
fun CallScreen(onEnd: () -> Unit) {
    val info by CallManager.info.collectAsState()
    val muted by CallManager.muted.collectAsState()
    val speaker by CallManager.speaker.collectAsState()
    var showKeypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var seen by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(info) {
        if (info != null) seen = true
        else {
            delay(if (seen) 0L else 400L)
            if (CallManager.info.value == null) onEnd()
        }
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    GlassBackground {
        val c = info
        if (c != null) {
            val ringing = c.state == Call.STATE_RINGING
            val active = c.state == Call.STATE_ACTIVE || c.state == Call.STATE_HOLDING
            val holding = c.state == Call.STATE_HOLDING
            val status = when (c.state) {
                Call.STATE_RINGING -> "수신 전화"
                Call.STATE_DIALING, Call.STATE_CONNECTING -> "발신 중…"
                Call.STATE_ACTIVE ->
                    if (c.connectTime > 0) fmtDuration(((now - c.connectTime) / 1000).coerceAtLeast(0)) else "통화 중"
                Call.STATE_HOLDING -> "보류 중"
                else -> "통화 종료"
            }

            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                if (!showKeypad) { Avatar(c.name ?: "", 104.dp); Spacer(Modifier.height(20.dp)) }
                Text(
                    c.name ?: c.number, color = Color.White, fontSize = 32.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (c.name != null) Text(c.number, color = G.TextSecondary, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(status, color = G.TextSecondary, fontSize = 18.sp)

                Spacer(Modifier.weight(1f))

                if (!ringing) {
                    if (showKeypad) KeypadPanel(typed, { typed += it }) { showKeypad = false }
                    else Column(verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                            CtrlButton(if (muted) Icons.Default.MicOff else Icons.Default.Mic, "음소거", muted) {
                                MyInCallService.instance?.toggleMute()
                            }
                            CtrlButton(Icons.Default.Dialpad, "키패드", false, active) { showKeypad = true }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                            CtrlButton(Icons.Default.VolumeUp, "스피커", speaker) {
                                MyInCallService.instance?.toggleSpeaker()
                            }
                            CtrlButton(if (holding) Icons.Default.PlayArrow else Icons.Default.Pause, "보류", holding, active) {
                                CallManager.toggleHold()
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (ringing) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionButton(G.Red, Icons.Default.CallEnd, "거절") { CallManager.reject() }
                        ActionButton(G.Green, Icons.Default.CallIcon, "수락") { CallManager.answer() }
                    }
                } else {
                    ActionButton(G.Red, Icons.Default.CallEnd, null) { CallManager.hangup() }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(76.dp, solid = on, enabled = enabled, onClick = onClick) {
            Icon(icon, label, tint = if (on) Color(0xFF111827) else Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun ActionButton(color: Color, icon: ImageVector, label: String?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(76.dp, tint = color, onClick = onClick) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        if (label != null) {
            Spacer(Modifier.height(8.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun KeypadPanel(typed: String, onKey: (String) -> Unit, onHide: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(typed.takeLast(14), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.height(40.dp))
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { DtmfKey(it, 68.dp, onKey) }
            }
        }
        TextButton(onHide) { Text("숨기기", color = Color.White, fontSize = 16.sp) }
    }
}

@Composable
private fun DtmfKey(k: Char, size: Dp, onKey: (String) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.size(size).glass(CircleShape)
            .pointerInput(k) {
                detectTapGestures(onPress = {
                    pressed = true; onKey(k.toString()); CallManager.dtmfStart(k)
                    tryAwaitRelease()
                    CallManager.dtmfStop(); pressed = false
                })
            },
        contentAlignment = Alignment.Center
    ) { Text(k.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light) }
}
