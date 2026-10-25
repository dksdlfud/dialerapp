package com.example.mydialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

data class Entry(val name: String, val number: String, val sub: String = "")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent?.data?.schemeSpecificPart ?: ""
        setContent { MaterialTheme { MainScreen(initial) } }
    }
}

fun placeCall(ctx: Context, number: String) {
    if (number.isBlank()) return
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
    val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    tm.placeCall(Uri.fromParts("tel", number, null), android.os.Bundle())
}

@Composable
fun MainScreen(initialNumber: String) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(false) }

    val perms = arrayOf(
        Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
    )
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it.values.all { v -> v }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    LaunchedEffect(Unit) {
        granted = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) permLauncher.launch(perms)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Dialpad, null) }, label = { Text("키패드") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.History, null) }, label = { Text("최근기록") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Contacts, null) }, label = { Text("연락처") })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (!rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                Button(
                    { roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)) },
                    Modifier.fillMaxWidth().padding(8.dp)
                ) { Text("기본 전화 앱으로 설정") }
            }
            when (tab) {
                0 -> DialPad(initialNumber)
                1 -> EntryList(granted, ::loadRecents)
                else -> EntryList(granted, ::loadContacts)
            }
        }
    }
}

@Composable
fun DialPad(initial: String) {
    val ctx = LocalContext.current
    var number by remember { mutableStateOf(initial) }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number, fontSize = 32.sp, modifier = Modifier.padding(vertical = 24.dp).heightIn(min = 40.dp))
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { k ->
                    TextButton({ number += k }, Modifier.size(80.dp)) { Text(k, fontSize = 28.sp) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            FloatingActionButton({ placeCall(ctx, number) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Call, "전화 걸기")
            }
            IconButton({ number = number.dropLast(1) }, Modifier.size(48.dp)) { Icon(Icons.Default.Backspace, "지우기") }
        }
    }
}

@Composable
fun EntryList(granted: Boolean, loader: (Context) -> List<Entry>) {
    val ctx = LocalContext.current
    if (!granted) { Text("권한이 필요합니다.", Modifier.padding(16.dp)); return }
    val items = remember(granted) { loader(ctx) }
    LazyColumn {
        items(items) { e ->
            ListItem(
                headlineContent = { Text(e.name) },
                supportingContent = { Text(listOf(e.number, e.sub).filter { it.isNotBlank() }.joinToString(" · ")) },
                trailingContent = { Icon(Icons.Default.Call, null) },
                modifier = Modifier.clickable { placeCall(ctx, e.number) }
            )
            HorizontalDivider()
        }
    }
}

fun loadContacts(ctx: Context): List<Entry> {
    val out = mutableListOf<Entry>()
    ctx.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )?.use { c -> while (c.moveToNext()) out += Entry(c.getString(0) ?: "", c.getString(1) ?: "") }
    return out
}

fun loadRecents(ctx: Context): List<Entry> {
    val out = mutableListOf<Entry>()
    val fmt = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
        null, null, CallLog.Calls.DATE + " DESC"
    )?.use { c ->
        while (c.moveToNext() && out.size < 200) {
            val type = when (c.getInt(2)) {
                CallLog.Calls.INCOMING_TYPE -> "수신"
                CallLog.Calls.OUTGOING_TYPE -> "발신"
                CallLog.Calls.MISSED_TYPE -> "부재중"
                else -> ""
            }
            val num = c.getString(1) ?: ""
            out += Entry(c.getString(0) ?: num, num, "$type ${fmt.format(Date(c.getLong(3)))}")
        }
    }
    return out
}
