package com.example.mydialer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.Call
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Call as CallIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private enum class PendingCallAction { ANSWER, REJECT }

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = G.Accent,
                onPrimary = G.Background,
                secondary = G.Blue,
                background = G.Background,
                onBackground = G.TextPrimary,
                surface = G.Surface,
                onSurface = G.TextPrimary,
                error = G.Red
            )) { CallScreen { finish() } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallScreen(onEnd: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val info by CallManager.info.collectAsState()
    val allCalls by CallManager.allCalls.collectAsState()
    val muted by CallManager.muted.collectAsState()
    val speaker by CallManager.speaker.collectAsState()
    val audioRoute by CallManager.audioRoute.collectAsState()
    val supportedAudioRoutes by CallManager.supportedAudioRoutes.collectAsState()
    val actionError by CallManager.lastError.collectAsState()
    var showAudioRoutes by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingCallAction?>(null) }
    val answerPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) {
            when (action) {
                PendingCallAction.ANSWER -> CallManager.answer()
                PendingCallAction.REJECT -> CallManager.reject()
                null -> Unit
            }
        } else {
            Toast.makeText(context, "전화 수락 또는 거절 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }
    fun performIncomingAction(action: PendingCallAction) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            if (action == PendingCallAction.ANSWER) CallManager.answer() else CallManager.reject()
        } else {
            pendingAction = action
            answerPermissionLauncher.launch(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }

    LaunchedEffect(actionError) {
        actionError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            CallManager.lastError.value = null
        }
    }
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
    LaunchedEffect(info?.state) {
        if (info?.state != Call.STATE_ACTIVE) showKeypad = false
    }

    GlassBackground {
        val c = info
        if (c != null) {
            val ringing = c.state == Call.STATE_RINGING
            val active = c.state == Call.STATE_ACTIVE
            val holding = c.state == Call.STATE_HOLDING
            val status = when (c.state) {
                Call.STATE_RINGING -> "수신 전화"
                Call.STATE_DIALING, Call.STATE_CONNECTING -> "연결 중"
                Call.STATE_ACTIVE -> if (c.connectTime > 0) fmtDuration(((now - c.connectTime) / 1000).coerceAtLeast(0)) else "통화 중"
                Call.STATE_HOLDING -> "보류 중"
                else -> "통화 종료"
            }
            val statusLabel = when {
                ringing -> "INCOMING CALL"
                holding -> "CALL ON HOLD"
                active -> "CONNECTED"
                else -> "CALLING"
            }

            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(G.SurfaceElevated, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Call, null, tint = G.Accent, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MYDIALER", color = G.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(statusLabel, color = G.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (ringing) G.Red.copy(alpha = 0.12f) else G.Accent.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(status, color = if (ringing) G.Red else G.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(30.dp))
                if (!showKeypad) {
                    Box(
                        Modifier.size(132.dp).border(1.dp, G.Accent.copy(alpha = 0.25f), CircleShape).padding(9.dp),
                        contentAlignment = Alignment.Center
                    ) { Avatar(c.name ?: c.number, 112.dp) }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        c.name ?: c.number,
                        color = G.TextPrimary, fontSize = 29.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (c.name != null && c.number.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(c.number, color = G.TextSecondary, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (ringing) "전화를 받을 준비가 되었어요" else if (holding) "상대방이 대기 중입니다" else status,
                        color = G.TextSecondary, fontSize = 13.sp
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("키패드", color = G.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(c.name ?: c.number, color = G.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                if (allCalls.size > 1 && !ringing) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text("다른 통화", color = G.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allCalls.filter { it.id != c.id }.forEach { other ->
                                val otherState = when (other.state) {
                                    Call.STATE_ACTIVE -> "통화 중"
                                    Call.STATE_HOLDING -> "대기 중 · 전환"
                                    Call.STATE_DIALING, Call.STATE_CONNECTING -> "연결 중"
                                    Call.STATE_RINGING -> "수신 중"
                                    else -> "통화"
                                }
                                Column(
                                    Modifier.widthIn(min = 138.dp, max = 210.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(G.SurfaceElevated)
                                        .border(1.dp, G.Border, RoundedCornerShape(16.dp))
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { CallManager.switchToCall(other.id) }
                                        .padding(horizontal = 14.dp, vertical = 11.dp)
                                ) {
                                    Text(other.name ?: other.number, color = G.TextPrimary, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text(otherState, color = if (other.state == Call.STATE_HOLDING) G.Accent else G.TextSecondary,
                                        fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (!ringing) {
                    if (showKeypad) {
                        KeypadPanel(typed, { typed = (typed + it).takeLast(24) }) { showKeypad = false }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(horizontalArrangement = Arrangement.spacedBy(34.dp)) {
                                CtrlButton(if (muted) Icons.Default.MicOff else Icons.Default.Mic, "음소거", muted, active) {
                                    val service = MyInCallService.instance
                                    if (service != null) service.toggleMute()
                                    else CallManager.lastError.value = "통화 오디오 서비스를 사용할 수 없습니다."
                                }
                                CtrlButton(Icons.Default.Dialpad, "키패드", false, active) { showKeypad = true }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(34.dp)) {
                                val routeIcon = when (audioRoute) {
                                    android.telecom.CallAudioState.ROUTE_BLUETOOTH -> Icons.Default.Bluetooth
                                    android.telecom.CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Default.Headset
                                    android.telecom.CallAudioState.ROUTE_SPEAKER -> Icons.Default.VolumeUp
                                    else -> Icons.Default.Call
                                }
                                CtrlButton(routeIcon, "오디오 경로", speaker, active) { showAudioRoutes = true }
                                CtrlButton(if (holding) Icons.Default.PlayArrow else Icons.Default.Pause, if (holding) "통화 재개" else "보류", holding, active || holding) {
                                    CallManager.toggleHold()
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (ringing) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionButton(G.Red, Icons.Default.CallEnd, "거절") { performIncomingAction(PendingCallAction.REJECT) }
                        ActionButton(G.Accent, Icons.Default.Call, "수락") { performIncomingAction(PendingCallAction.ANSWER) }
                    }
                } else {
                    ActionButton(G.Red, Icons.Default.CallEnd, "종료") { CallManager.hangup() }
                }
                Spacer(Modifier.height(18.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(58.dp).background(G.SurfaceElevated, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Call, null, tint = G.Accent, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("통화 정보를 불러오는 중", color = G.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (showAudioRoutes && c != null) {
            AudioRouteDialog(
                currentRoute = audioRoute,
                supportedMask = supportedAudioRoutes,
                onSelect = { route ->
                    val service = MyInCallService.instance
                    if (service != null) service.setAudioRouteFromUi(route)
                    else CallManager.lastError.value = "통화 오디오 서비스를 사용할 수 없습니다."
                    showAudioRoutes = false
                },
                onDismiss = { showAudioRoutes = false }
            )
        }
    }
}

@Composable
private fun AudioRouteDialog(currentRoute: Int, supportedMask: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val routes = buildList {
        if ((supportedMask and android.telecom.CallAudioState.ROUTE_EARPIECE) != 0) add(android.telecom.CallAudioState.ROUTE_EARPIECE to ("수화기" to Icons.Default.Call))
        if ((supportedMask and android.telecom.CallAudioState.ROUTE_WIRED_HEADSET) != 0) add(android.telecom.CallAudioState.ROUTE_WIRED_HEADSET to ("유선 이어폰" to Icons.Default.Headset))
        if ((supportedMask and android.telecom.CallAudioState.ROUTE_BLUETOOTH) != 0) add(android.telecom.CallAudioState.ROUTE_BLUETOOTH to ("블루투스" to Icons.Default.Bluetooth))
        if ((supportedMask and android.telecom.CallAudioState.ROUTE_SPEAKER) != 0) add(android.telecom.CallAudioState.ROUTE_SPEAKER to ("스피커" to Icons.Default.VolumeUp))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.Surface,
        title = { Text("오디오 경로", color = G.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            if (routes.isEmpty()) {
                Text("사용 가능한 오디오 경로를 확인하는 중입니다. 잠시 후 다시 열어 주세요.", color = G.TextSecondary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    routes.forEach { (route, item) ->
                        val (label, icon) = item
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (currentRoute == route) G.SurfaceSelected else Color.Transparent)
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(route) }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, tint = if (currentRoute == route) G.Accent else G.TextSecondary, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, modifier = Modifier.weight(1f), color = G.TextPrimary, fontSize = 14.sp)
                            if (currentRoute == route) Icon(Icons.Default.CheckCircle, null, tint = G.Accent, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기", color = G.Accent) } }
    )
}

@Composable
private fun CtrlButton(icon: ImageVector, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(68.dp, solid = on, enabled = enabled, onClick = onClick) {
            Icon(icon, label, tint = if (on) G.Background else G.TextPrimary, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = if (enabled) G.TextSecondary else G.TextSecondary.copy(alpha = 0.45f), fontSize = 12.sp)
    }
}

@Composable
private fun ActionButton(color: Color, icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(74.dp, tint = color, onClick = onClick) {
            Icon(icon, label, tint = if (color == G.Accent) G.Background else Color.White, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = G.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KeypadPanel(typed: String, onKey: (String) -> Unit, onHide: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(typed.takeLast(18).ifEmpty { "번호 입력" }, color = if (typed.isEmpty()) G.TextSecondary else G.TextPrimary,
            fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.height(38.dp))
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(17.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                row.forEach { DtmfKey(it, 61.dp, onKey) }
            }
        }
        TextButton(onClick = onHide) { Text("키패드 숨기기", color = G.Accent, fontSize = 13.sp) }
    }
}

@Composable
private fun DtmfKey(k: Char, size: Dp, onKey: (String) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (pressed) G.SurfaceSelected else G.SurfaceElevated)
            .border(1.dp, G.Border, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = "키패드 숫자 $k"
                onClick(label = "숫자 $k 입력") {
                    onKey(k.toString())
                    CallManager.dtmfStart(k)
                    CallManager.dtmfStop()
                    true
                }
            }
            .pointerInput(k) {
                detectTapGestures(onPress = {
                    pressed = true
                    onKey(k.toString())
                    CallManager.dtmfStart(k)
                    try {
                        tryAwaitRelease()
                    } finally {
                        CallManager.dtmfStop()
                        pressed = false
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) { Text(k.toString(), color = G.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Medium) }
}
