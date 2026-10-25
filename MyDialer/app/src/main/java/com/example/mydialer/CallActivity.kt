package com.example.mydialer

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CallScreen { finish() } } }
    }
}

@Composable
fun CallScreen(onEnd: () -> Unit) {
    val info by CallManager.info.collectAsState()
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var hadCall by remember { mutableStateOf(false) }

    LaunchedEffect(info) {
        if (info != null) hadCall = true else if (hadCall) onEnd()
    }
    val c = info ?: return

    val stateText = when (c.state) {
        Call.STATE_RINGING -> "수신 중"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "발신 중…"
        Call.STATE_ACTIVE -> "통화 중"
        Call.STATE_HOLDING -> "대기 중"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "종료 중…"
        else -> ""
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(c.number, fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text(stateText, fontSize = 18.sp)
        }

        if (c.state == Call.STATE_ACTIVE) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                FilledTonalIconToggleButton(muted, {
                    muted = MyInCallService.instance?.toggleMute() ?: muted
                }) { Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, "음소거") }
                FilledTonalIconToggleButton(speaker, {
                    speaker = MyInCallService.instance?.toggleSpeaker() ?: speaker
                }) { Icon(Icons.Default.VolumeUp, "스피커") }
            }
        } else Spacer(Modifier)

        Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (c.state == Call.STATE_RINGING) {
                FloatingActionButton({ CallManager.reject() }, containerColor = Color(0xFFD32F2F)) {
                    Icon(Icons.Default.CallEnd, "거절", tint = Color.White)
                }
                FloatingActionButton({ CallManager.answer() }, containerColor = Color(0xFF2E7D32)) {
                    Icon(Icons.Default.Call, "수락", tint = Color.White)
                }
            } else {
                FloatingActionButton({ CallManager.hangup() }, containerColor = Color(0xFFD32F2F)) {
                    Icon(Icons.Default.CallEnd, "종료", tint = Color.White)
                }
            }
        }
    }
}
