@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.mydialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Kind { IN, OUT, MISSED, REJECTED, NONE }
data class Entry(val name: String, val number: String, val sub: String, val time: String = "", val kind: Kind = Kind.NONE, val durationSeconds: Long = 0L)
private enum class CallFilter { ALL, MISSED, INCOMING, OUTGOING }
private data class DialRequest(val number: String, val id: Int)

class MainActivity : ComponentActivity() {
    private var dialRequest by mutableStateOf(DialRequest("", 0))
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        dialRequest = DialRequest(intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty(), 0)
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
            )) {
                MainScreen(dialRequest.number, dialRequest.id, resumeTick)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val number = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        dialRequest = DialRequest(number, dialRequest.id + 1)
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

fun hasPermission(ctx: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

/** @return null on successful hand-off to Telecom; otherwise a localized error message. */
fun placeCall(ctx: Context, number: String): String? {
    val target = number.trim()
    validateDialNumber(target)?.let { return it }

    return try {
        val telecom = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return "전화 서비스를 사용할 수 없습니다."
        telecom.placeCall(Uri.fromParts("tel", target, null), Bundle())
        null
    } catch (_: SecurityException) {
        "전화 권한이 없습니다. 권한을 허용하거나 기본 전화 앱 설정을 확인해 주세요."
    } catch (_: IllegalArgumentException) {
        "전화번호 형식을 확인해 주세요."
    } catch (_: IllegalStateException) {
        "지금은 전화를 걸 수 없습니다. 잠시 후 다시 시도해 주세요."
    } catch (_: RuntimeException) {
        "전화 요청 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
    }
}

private fun showCallResult(ctx: Context, error: String?) {
    if (error != null) Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
}

// ───────────────────────── 메인 ─────────────────────────

@Composable
fun MainScreen(initialNumber: String, dialRequestId: Int = 0, resumeTick: Int = 0) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val roleManager = remember { ctx.getSystemService(RoleManager::class.java) }
    var tab by remember { mutableIntStateOf(0) }
    var contactsGranted by remember { mutableStateOf(hasPermission(ctx, Manifest.permission.READ_CONTACTS)) }
    var callLogGranted by remember { mutableStateOf(hasPermission(ctx, Manifest.permission.READ_CALL_LOG)) }
    var callPhoneGranted by remember { mutableStateOf(hasPermission(ctx, Manifest.permission.CALL_PHONE)) }
    var isDefault by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) }
    var pendingNumber by remember { mutableStateOf("") }

    LaunchedEffect(dialRequestId) { tab = 0 }

    fun refreshPermissions() {
        contactsGranted = hasPermission(ctx, Manifest.permission.READ_CONTACTS)
        callLogGranted = hasPermission(ctx, Manifest.permission.READ_CALL_LOG)
        callPhoneGranted = hasPermission(ctx, Manifest.permission.CALL_PHONE)
        isDefault = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsGranted = granted && hasPermission(ctx, Manifest.permission.READ_CONTACTS)
        if (!contactsGranted) Toast.makeText(ctx, "연락처를 보려면 연락처 권한이 필요합니다.", Toast.LENGTH_LONG).show()
    }
    val callLogPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callLogGranted = granted && hasPermission(ctx, Manifest.permission.READ_CALL_LOG)
        if (!callLogGranted) Toast.makeText(ctx, "최근 기록을 보려면 통화 기록 권한이 필요합니다.", Toast.LENGTH_LONG).show()
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callPhoneGranted = granted && hasPermission(ctx, Manifest.permission.CALL_PHONE)
        val number = pendingNumber
        pendingNumber = ""
        if (callPhoneGranted && number.isNotBlank()) showCallResult(ctx, placeCall(ctx, number))
        else if (!callPhoneGranted) Toast.makeText(ctx, "전화를 걸려면 전화 권한이 필요합니다.", Toast.LENGTH_LONG).show()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(ctx, "수신 전화 알림을 놓치지 않도록 알림 권한을 허용하는 것이 좋습니다.", Toast.LENGTH_LONG).show()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        if (!isDefault) {
            Toast.makeText(ctx, "기본 전화 앱으로 설정되지 않았습니다.", Toast.LENGTH_LONG).show()
        } else if (Build.VERSION.SDK_INT >= 33 && !hasPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val requestCall: (String) -> Unit = { rawNumber ->
        val number = rawNumber.trim()
        if (number.isEmpty()) {
            Toast.makeText(ctx, "전화번호를 입력해 주세요.", Toast.LENGTH_SHORT).show()
        } else if (!callPhoneGranted && !hasPermission(ctx, Manifest.permission.CALL_PHONE)) {
            pendingNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        } else {
            showCallResult(ctx, placeCall(ctx, number))
        }
    }

    LaunchedEffect(resumeTick) { refreshPermissions() }

    fun requestDefaultDialer() {
        try {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            } else {
                Toast.makeText(ctx, "이 기기에서는 기본 전화 앱 역할을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(ctx, "기본 전화 앱 설정 화면을 열 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    GlassBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (!isDefault) DefaultBanner(::requestDefaultDialer)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> DialPad(initialNumber, dialRequestId, requestCall)
                    1 -> RecentsScreen(
                        granted = callLogGranted,
                        isDefaultDialer = isDefault,
                        onRequestPermission = {
                            if (!isDefault) requestDefaultDialer()
                            else callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        },
                        onPlaceCall = requestCall,
                        refreshTick = resumeTick
                    )
                    else -> ContactsScreen(contactsGranted, { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }, requestCall, resumeTick)
                }
            }
        }
        GlassTabBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun DefaultBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(G.Accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Call, null, tint = G.Accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("기본 전화 앱으로 설정", color = G.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("수신 전화와 통화 기록을 한곳에서 관리하세요", color = G.TextSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = G.TextSecondary)
    }
}

@Composable
fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf("키패드" to Icons.Default.Dialpad, "최근 기록" to Icons.Default.AccessTime, "연락처" to Icons.Default.Person)
    Row(
        modifier.navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .glass(RoundedCornerShape(26.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val sel = i == selected
            val tint = if (sel) G.Accent else G.TextSecondary
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) G.SurfaceSelected else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, label, tint = tint, modifier = Modifier.size(21.dp))
                Spacer(Modifier.height(3.dp))
                Text(label, color = tint, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String, kicker: String = "MYDIALER", detail: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 14.dp)) {
        Text(kicker.uppercase(Locale.KOREA), color = G.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(5.dp))
        Text(text, color = G.TextPrimary, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(detail, color = G.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = 112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(66.dp).background(G.SurfaceElevated, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.History, null, tint = G.TextSecondary, modifier = Modifier.size(29.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(text, color = G.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text("새로운 기록이 생기면 여기에 표시됩니다.", color = G.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun PermissionPrompt(text: String, buttonText: String, onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp)
            .glass(RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(54.dp).background(G.SurfaceSelected, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Lock, null, tint = G.Accent, modifier = Modifier.size(25.dp))
        }
        Text(text, color = G.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = G.Accent, contentColor = G.Background),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
        ) { Text(buttonText, fontWeight = FontWeight.SemiBold) }
    }
}

// ───────────────────────── 키패드 ─────────────────────────

private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to ""
)

@Composable
fun DialPad(initial: String, requestId: Int = 0, onPlaceCall: (String) -> Unit) {
    var number by remember(requestId) { mutableStateOf(initial) }

    BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
        val s = minOf(70.dp, ((maxHeight - 250.dp) / 5).coerceAtLeast(47.dp))
        Column(
            Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MYDIALER", color = G.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("전화 걸기", color = G.TextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                }
                Box(Modifier.size(42.dp).background(G.SurfaceElevated, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Dialpad, null, tint = G.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().height(68.dp)
                    .clip(RoundedCornerShape(17.dp)).background(G.Surface.copy(alpha = 0.68f))
                    .border(1.dp, G.Border, RoundedCornerShape(17.dp)).padding(start = 16.dp, end = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    number.ifEmpty { "전화번호를 입력" },
                    modifier = Modifier.weight(1f),
                    color = if (number.isEmpty()) G.TextSecondary.copy(alpha = 0.75f) else G.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = if (number.length > 12) 23.sp else 29.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp
                )
                if (number.isNotEmpty()) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .combinedClickable(
                                role = Role.Button,
                                onClick = { number = number.dropLast(1) },
                                onLongClick = { number = "" }
                            ).semantics { contentDescription = "번호 지우기. 길게 누르면 전체 삭제" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, "번호 지우기", tint = G.TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            KEYS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { (k, sub) ->
                        GlassCircle(
                            size = s,
                            onLongClick = if (k == "0") ({ number += "+" }) else null,
                            onClick = { number += k }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(k, color = G.TextPrimary, fontSize = if (s < 55.dp) 25.sp else 28.sp, fontWeight = FontWeight.Medium)
                                if (sub.isNotEmpty()) {
                                    Spacer(Modifier.height(1.dp))
                                    Text(sub, color = G.TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(19.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(s))
                GlassCircle(s, tint = G.Accent, onClick = { onPlaceCall(number) }) {
                    Icon(Icons.Default.Call, "전화 걸기", tint = G.Background, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.size(s))
            }
            Spacer(Modifier.height(7.dp))
            Text("0을 길게 누르면 + 입력", color = G.TextSecondary, fontSize = 10.sp)
        }
    }
}

// ───────────────────────── 최근 기록 ─────────────────────────

@Composable
fun RecentsScreen(
    granted: Boolean,
    isDefaultDialer: Boolean,
    onRequestPermission: () -> Unit,
    onPlaceCall: (String) -> Unit,
    refreshTick: Int = 0
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var filter by remember { mutableStateOf(CallFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    val all by produceState(initialValue = emptyList<Entry>(), granted, refreshTick) {
        value = if (granted) withContext(Dispatchers.IO) { loadRecents(ctx) } else emptyList()
    }
    val list = remember(all, filter, query) {
        val q = query.trim().lowercase(Locale.KOREA)
        all.filter { entry ->
            val filterMatch = when (filter) {
                CallFilter.ALL -> true
                CallFilter.MISSED -> entry.kind == Kind.MISSED || entry.kind == Kind.REJECTED
                CallFilter.INCOMING -> entry.kind == Kind.IN
                CallFilter.OUTGOING -> entry.kind == Kind.OUT
            }
            filterMatch && matchesPhoneQuery(entry.name, entry.number, q, entry.sub)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("최근 통화", "CALL HISTORY", if (granted) "최근 통화 ${all.size}건" else "놓친 전화와 통화 내역을 확인하세요")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(16.dp)).background(G.SurfaceElevated)
                .border(1.dp, G.Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = G.TextSecondary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("이름 또는 번호 검색", color = G.TextSecondary, fontSize = 14.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = G.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(G.Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Box(Modifier.size(28.dp).clickable(role = Role.Button) { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "검색 지우기", tint = G.TextSecondary, modifier = Modifier.size(17.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf(
                CallFilter.ALL to "전체",
                CallFilter.MISSED to "부재중",
                CallFilter.INCOMING to "수신",
                CallFilter.OUTGOING to "발신"
            ).forEach { (value, label) ->
                val selected = filter == value
                Row(
                    Modifier.clip(RoundedCornerShape(13.dp))
                        .background(if (selected) G.SurfaceSelected else G.Surface)
                        .border(1.dp, if (selected) G.Accent.copy(alpha = 0.25f) else G.Border, RoundedCornerShape(13.dp))
                        .clickable(role = Role.Tab) { filter = value }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (value == CallFilter.MISSED) Icon(Icons.Default.CallMissed, null, tint = if (selected) G.Red else G.TextSecondary, modifier = Modifier.size(15.dp))
                    Text(label, color = if (selected) G.TextPrimary else G.TextSecondary, fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
        when {
            !granted && !isDefaultDialer -> PermissionPrompt(
                "최근 통화 기록을 사용하려면 기본 전화 앱으로 설정해야 합니다.",
                "기본 전화 앱으로 설정", onRequestPermission
            )
            !granted -> PermissionPrompt("최근 통화 기록을 표시하려면 권한이 필요합니다.", "통화 기록 권한 허용", onRequestPermission)
            list.isEmpty() -> EmptyHint(
                when {
                    query.isNotBlank() -> "검색 결과가 없습니다"
                    filter == CallFilter.MISSED -> "부재중 전화가 없습니다"
                    filter == CallFilter.INCOMING -> "수신 기록이 없습니다"
                    filter == CallFilter.OUTGOING -> "발신 기록이 없습니다"
                    else -> "아직 통화 기록이 없습니다"
                }
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 122.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(list) { entry ->
                    EntryRow(entry, onClick = { selectedEntry = entry }, onCall = { onPlaceCall(entry.number) })
                }
            }
        }
    }
    selectedEntry?.let { entry ->
        EntryDetailsDialog(entry = entry, onDismiss = { selectedEntry = null }, onCall = onPlaceCall)
    }
}

@Composable
fun EntryRow(e: Entry, onClick: () -> Unit, onCall: () -> Unit = onClick) {
    val kindColor = when (e.kind) {
        Kind.MISSED, Kind.REJECTED -> G.Red
        Kind.OUT -> G.Accent
        Kind.IN -> G.Blue
        else -> G.TextSecondary
    }
    val kindIcon = when (e.kind) {
        Kind.IN -> Icons.Default.CallReceived
        Kind.OUT -> Icons.Default.CallMade
        Kind.MISSED, Kind.REJECTED -> Icons.Default.CallMissed
        else -> Icons.Default.Call
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).background(G.SurfaceElevated, CircleShape), contentAlignment = Alignment.Center) {
            Icon(kindIcon, null, tint = kindColor, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.name, color = if (e.kind == Kind.MISSED || e.kind == Kind.REJECTED) G.Red else G.TextPrimary,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(e.sub.ifBlank { e.number }, color = G.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (e.time.isNotEmpty()) Text(e.time, color = G.TextSecondary, fontSize = 11.sp)
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(G.Accent.copy(alpha = 0.10f))
                    .clickable(role = Role.Button, onClick = onCall),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, "${e.name}에게 전화", tint = G.Accent, modifier = Modifier.size(16.dp))
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(G.Divider))
}

@Composable
private fun EntryDetailsDialog(entry: Entry, onDismiss: () -> Unit, onCall: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = G.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, G.Border)
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(entry.name, 58.dp)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = G.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (entry.number.isBlank()) "전화번호 없음" else entry.number, color = G.TextSecondary, fontSize = 14.sp)
                    }
                }
                Divider(color = G.Divider)
                if (entry.sub.isNotBlank() && entry.sub != entry.number) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("기록", color = G.TextSecondary, fontSize = 11.sp)
                        Text(entry.sub, color = G.TextPrimary, fontSize = 14.sp)
                    }
                }
                if (entry.time.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("통화 시간", color = G.TextSecondary, fontSize = 11.sp)
                        Text(entry.time, color = G.TextPrimary, fontSize = 14.sp)
                    }
                }
                if (entry.durationSeconds > 0L) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("통화 길이", color = G.TextSecondary, fontSize = 11.sp)
                        Text(formatCallDuration(entry.durationSeconds), color = G.TextPrimary, fontSize = 14.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (entry.number.isNotBlank()) {
                                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("전화번호", entry.number))
                                Toast.makeText(ctx, "전화번호를 복사했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f), enabled = entry.number.isNotBlank(),
                        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = G.TextPrimary)
                    ) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("복사") }
                    OutlinedButton(
                        onClick = {
                            if (entry.number.isNotBlank()) {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", entry.number, null)))
                                } catch (_: Exception) {
                                    Toast.makeText(ctx, "메시지 앱을 열 수 없습니다.", Toast.LENGTH_LONG).show()
                                }
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f), enabled = entry.number.isNotBlank(),
                        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = G.TextPrimary)
                    ) { Icon(Icons.Default.Message, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("메시지") }
                }
                Button(
                    onClick = { onCall(entry.number); onDismiss() },
                    modifier = Modifier.fillMaxWidth(), enabled = entry.number.isNotBlank(),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = G.Accent, contentColor = G.Background)
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("전화 걸기", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("닫기", color = G.TextSecondary)
                }
            }
        }
    }
}


// ───────────────────────── 연락처 ─────────────────────────

@Composable
fun ContactsScreen(granted: Boolean, onRequestPermission: () -> Unit, onPlaceCall: (String) -> Unit, refreshTick: Int = 0) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    val all by produceState(initialValue = emptyList<Entry>(), granted, refreshTick) {
        value = if (granted) withContext(Dispatchers.IO) { loadContacts(ctx) } else emptyList()
    }
    val filtered = remember(all, query) {
        val q = query.trim().lowercase(Locale.KOREA)
        all.filter { entry -> matchesPhoneQuery(entry.name, entry.number, q) }
    }
    val grouped = remember(filtered) { filtered.groupBy { groupKey(it.name) } }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("연락처", "PEOPLE", if (granted) "저장된 번호 ${all.size}개" else "이름과 번호를 빠르게 찾아보세요")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(16.dp)).background(G.SurfaceElevated)
                .border(1.dp, G.Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = G.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("이름 또는 전화번호 검색", color = G.TextSecondary, fontSize = 14.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = G.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(G.Accent), modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Box(Modifier.size(28.dp).clickable(role = Role.Button) { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "검색 지우기", tint = G.TextSecondary, modifier = Modifier.size(17.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        when {
            !granted -> PermissionPrompt("연락처를 검색하고 바로 전화하려면 연락처 접근 권한이 필요합니다.", "연락처 권한 허용", onRequestPermission)
            filtered.isEmpty() -> EmptyHint(if (query.isBlank()) "연락처가 없습니다" else "검색 결과가 없습니다")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 122.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                grouped.forEach { (header, entries) ->
                    item(key = "header-$header") {
                        Text(
                            header, color = G.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 5.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(entries, key = { it.name + it.number }) { entry ->
                        EntryRow(entry, onClick = { selectedEntry = entry }, onCall = { onPlaceCall(entry.number) })
                    }
                }
            }
        }
    }
    selectedEntry?.let { entry ->
        EntryDetailsDialog(entry = entry, onDismiss = { selectedEntry = null }, onCall = onPlaceCall)
    }
}


// ───────────────────────── 데이터 로딩 ─────────────────────────

fun loadContacts(ctx: Context): List<Entry> = try {
    val out = mutableListOf<Entry>()
    val phone = ContactsContract.CommonDataKinds.Phone
    ctx.contentResolver.query(
        phone.CONTENT_URI, arrayOf(phone.DISPLAY_NAME, phone.NUMBER), null, null, phone.DISPLAY_NAME + " ASC"
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val name = cursor.getString(0) ?: continue
            val number = cursor.getString(1) ?: continue
            out += Entry(name, number, number)
        }
    }
    out.distinctBy { it.name + it.number.filter(Char::isDigit) }
} catch (_: SecurityException) {
    emptyList()
} catch (_: RuntimeException) {
    emptyList()
}

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun fmtTime(ms: Long): String {
    val time = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(time, today) -> SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(ms))
        sameDay(time, yesterday) -> "어제"
        else -> SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(ms))
    }
}

fun loadRecents(ctx: Context): List<Entry> = try {
    val out = mutableListOf<Entry>()
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
        null, null, CallLog.Calls.DATE + " DESC"
    )?.use { cursor ->
        while (cursor.moveToNext() && out.size < 200) {
            val kind = when (cursor.getInt(2)) {
                CallLog.Calls.INCOMING_TYPE -> Kind.IN
                CallLog.Calls.OUTGOING_TYPE -> Kind.OUT
                CallLog.Calls.MISSED_TYPE -> Kind.MISSED
                CallLog.Calls.REJECTED_TYPE -> Kind.REJECTED
                else -> Kind.NONE
            }
            val label = when (kind) {
                Kind.IN -> "수신"
                Kind.OUT -> "발신"
                Kind.MISSED -> "부재중"
                Kind.REJECTED -> "거절됨"
                else -> ""
            }
            val number = cursor.getString(1).orEmpty()
            val cachedName = cursor.getString(0).orEmpty()
            val name = cachedName.ifEmpty { number.ifEmpty { "알 수 없음" } }
            val sub = listOf(label, if (cachedName.isNotEmpty()) number else "").filter { it.isNotEmpty() }.joinToString(" · ")
            out += Entry(name, number, sub, fmtTime(cursor.getLong(3)), kind, cursor.getLong(4))
        }
    }
    out
} catch (_: SecurityException) {
    emptyList()
} catch (_: RuntimeException) {
    emptyList()
}
