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
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class Kind { IN, OUT, MISSED, NONE }
data class Entry(val name: String, val number: String, val sub: String, val time: String = "", val kind: Kind = Kind.NONE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val initial = intent?.data?.schemeSpecificPart ?: ""
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { MainScreen(initial) } }
    }
}

private val CORE_PERMS = arrayOf(
    Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ANSWER_PHONE_CALLS
)

private fun hasCorePerms(ctx: Context) =
    CORE_PERMS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

fun placeCall(ctx: Context, number: String) {
    if (number.isBlank()) return
    try {
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (_: SecurityException) { }
}

// ───────────────────────── 메인 ─────────────────────────

@Composable
fun MainScreen(initialNumber: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val rm = remember { ctx.getSystemService(RoleManager::class.java) }
    var tab by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(hasCorePerms(ctx)) }
    var isDefault by remember { mutableStateOf(rm.isRoleHeld(RoleManager.ROLE_DIALER)) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasCorePerms(ctx)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            val all = if (Build.VERSION.SDK_INT >= 33) CORE_PERMS + Manifest.permission.POST_NOTIFICATIONS else CORE_PERMS
            permLauncher.launch(all)
        }
    }

    GlassBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (!isDefault) DefaultBanner { roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)) }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> DialPad(initialNumber)
                    1 -> RecentsScreen(granted)
                    else -> ContactsScreen(granted)
                }
            }
        }
        GlassTabBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun DefaultBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Call, null, tint = G.Green, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("기본 전화 앱으로 설정", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = G.TextSecondary)
    }
}

@Composable
fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf("키패드" to Icons.Default.Dialpad, "최근기록" to Icons.Default.AccessTime, "연락처" to Icons.Default.Person)
    Row(
        modifier.navigationBarsPadding().padding(bottom = 12.dp)
            .glass(RoundedCornerShape(36.dp), 1.1f).padding(6.dp)
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val sel = i == selected
            val tint = if (sel) Color.White else Color.White.copy(alpha = 0.55f)
            Column(
                Modifier.width(92.dp).background(if (sel) Color.White.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(30.dp))
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
                Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.Center) {
        Text(text, color = G.TextSecondary, fontSize = 16.sp)
    }
}

// ───────────────────────── 키패드 ─────────────────────────

private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to ""
)

@Composable
fun DialPad(initial: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var number by remember { mutableStateOf(initial) }

    BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 92.dp)) {
        val s = minOf(78.dp, (maxHeight - 160.dp) / 5).coerceAtLeast(56.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.height(72.dp).fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    number, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = if (number.length > 12) 28.sp else 38.sp, fontWeight = FontWeight.Light
                )
            }
            Spacer(Modifier.height(12.dp))
            KEYS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { (k, sub) ->
                        GlassCircle(s, onLongClick = if (k == "0") ({ number += "+" }) else null, onClick = { number += k }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(k, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
                                if (sub.isNotEmpty()) Text(sub, color = G.TextSecondary, fontSize = 9.sp, letterSpacing = 1.5.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(s))
                GlassCircle(s, tint = G.Green, onClick = { placeCall(ctx, number) }) {
                    Icon(Icons.Default.Call, "전화 걸기", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Box(
                    Modifier.size(s).pointerInput(Unit) {
                        detectTapGestures(onTap = { number = number.dropLast(1) }, onLongPress = { number = "" })
                    },
                    contentAlignment = Alignment.Center
                ) {
                    if (number.isNotEmpty())
                        Icon(Icons.AutoMirrored.Filled.Backspace, "지우기", tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ───────────────────────── 최근 기록 ─────────────────────────

@Composable
fun RecentsScreen(granted: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var onlyMissed by remember { mutableStateOf(false) }
    val all by produceState(emptyList<Entry>(), granted) {
        if (granted) value = withContext(Dispatchers.IO) { loadRecents(ctx) }
    }
    val list = if (onlyMissed) all.filter { it.kind == Kind.MISSED } else all

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("최근 기록")
        Row(
            Modifier.padding(start = 20.dp, bottom = 12.dp).glass(RoundedCornerShape(20.dp)).padding(3.dp)
        ) {
            listOf("전체" to false, "부재중" to true).forEach { (label, missed) ->
                val sel = onlyMissed == missed
                Text(
                    label, color = if (sel) Color.White else G.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.background(if (sel) Color.White.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(17.dp))
                        .clickable { onlyMissed = missed }.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
        }
        when {
            !granted -> EmptyHint("권한을 허용해 주세요")
            list.isEmpty() -> EmptyHint("기록이 없습니다")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { items(list) { EntryRow(it) { placeCall(ctx, it.number) } } }
        }
    }
}

@Composable
fun EntryRow(e: Entry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(e.name, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.name, color = if (e.kind == Kind.MISSED) G.Red else Color.White, fontSize = 17.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(e.sub, color = G.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (e.time.isNotEmpty()) Text(e.time, color = G.TextSecondary, fontSize = 13.sp)
    }
}

// ───────────────────────── 연락처 ─────────────────────────

@Composable
fun ContactsScreen(granted: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    val all by produceState(emptyList<Entry>(), granted) {
        if (granted) value = withContext(Dispatchers.IO) { loadContacts(ctx) }
    }
    val grouped = remember(all, query) {
        all.filter { query.isBlank() || it.name.contains(query, true) || it.number.contains(query) }
            .groupBy { groupKey(it.name) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("연락처")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .glass(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = G.TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("검색", color = G.TextSecondary, fontSize = 16.sp)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty())
                Icon(Icons.Default.Close, "지우기", tint = G.TextSecondary,
                    modifier = Modifier.size(20.dp).clickable { query = "" })
        }
        when {
            !granted -> EmptyHint("권한을 허용해 주세요")
            grouped.isEmpty() -> EmptyHint("연락처가 없습니다")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (header, entries) ->
                    item {
                        Text(header, color = G.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp, top = 10.dp))
                    }
                    items(entries) { EntryRow(it) { placeCall(ctx, it.number) } }
                }
            }
        }
    }
}

fun groupKey(name: String): String {
    val c = name.trim().firstOrNull() ?: return "#"
    if (c in '\uAC00'..'\uD7A3') {
        val cho = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"[(c - '\uAC00') / 588]
        return when (cho) { 'ㄲ' -> "ㄱ"; 'ㄸ' -> "ㄷ"; 'ㅃ' -> "ㅂ"; 'ㅆ' -> "ㅅ"; 'ㅉ' -> "ㅈ"; else -> cho.toString() }
    }
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

// ───────────────────────── 데이터 로딩 ─────────────────────────

fun loadContacts(ctx: Context): List<Entry> {
    val out = mutableListOf<Entry>()

    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val nameColumn = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
    val numberColumn = ContactsContract.CommonDataKinds.Phone.NUMBER

    ctx.contentResolver.query(
        uri,
        arrayOf(nameColumn, numberColumn),
        null,
        null,
        "$nameColumn ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            val name = c.getString(0) ?: continue
            val num = c.getString(1) ?: continue
            out += Entry(name, num, num)
        }
    }

    return out.distinctBy {
        it.name + it.number.filter(Char::isDigit)
    }
}

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun fmtTime(ms: Long): String {
    val t = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(t, today) -> SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(ms))
        sameDay(t, yesterday) -> "어제"
        else -> SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(ms))
    }
}

fun loadRecents(ctx: Context): List<Entry> {
    val out = mutableListOf<Entry>()
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
        null, null, CallLog.Calls.DATE + " DESC"
    )?.use { c ->
        while (c.moveToNext() && out.size < 200) {
            val kind = when (c.getInt(2)) {
                CallLog.Calls.INCOMING_TYPE -> Kind.IN
                CallLog.Calls.OUTGOING_TYPE -> Kind.OUT
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Kind.MISSED
                else -> Kind.NONE
            }
            val label = when (kind) { Kind.IN -> "수신"; Kind.OUT -> "발신"; Kind.MISSED -> "부재중"; else -> "" }
            val num = c.getString(1).orEmpty()
            val cached = c.getString(0).orEmpty()
            val name = cached.ifEmpty { num.ifEmpty { "알 수 없음" } }
            val sub = listOf(label, if (cached.isNotEmpty()) num else "").filter { it.isNotEmpty() }.joinToString(" · ")
            out += Entry(name, num, sub, fmtTime(c.getLong(3)), kind)
        }
    }
    return out
}
