package com.studylauncher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.studylauncher.AppState
import com.studylauncher.Clock
import com.studylauncher.Enforcer
import com.studylauncher.Mode
import com.studylauncher.ModeLogic
import com.studylauncher.Policy
import com.studylauncher.Stats
import com.studylauncher.Store
import com.studylauncher.Tasks
import com.studylauncher.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppEntry(val pkg: String, val label: String)

private fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private fun launch(ctx: Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
        runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Quick links to the few Settings pages students legitimately need (Wi-Fi etc.). */
private fun quickSetting(ctx: Context, action: String) {
    Enforcer.openFixWindow(60)
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    bmp?.let { Image(it, null, Modifier.size(size)) } ?: Spacer(Modifier.size(size))
}

@Composable
fun MainScaffold() {
    val ctx = LocalContext.current
    val tab by AppState.tab.collectAsState()
    val cfg by AppState.config.collectAsState()
    val now by rememberNow()
    val ms = remember(now, cfg) { ModeLogic.current(cfg) }

    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val tabs = listOf("🏠" to "Home", "📱" to "Apps", "⏱" to "Focus", "✅" to "Tasks", "📊" to "Stats")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { AppState.tab.value = i },
                        icon = { Text(icon, fontSize = 20.sp) }, label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> HomeTab(now, ms.mode, ms.msLeft, ms.forced, ms.trusted)
                1 -> AppsTab(ms.mode)
                2 -> FocusTab()
                3 -> TasksTab()
                else -> StatsTab()
            }
        }
    }
}

@Composable
private fun HomeTab(now: Long, mode: Mode, left: Long, forced: Boolean, trusted: Boolean) {
    val ctx = LocalContext.current
    val cfg by AppState.config.collectAsState()
    val tasks by Tasks.list.collectAsState()
    val days by Stats.days.collectAsState()
    var showAdmin by remember { mutableStateOf(false) }
    val accent = if (mode == Mode.STUDY) StudyRed else BreakGreen
    val apps by produceState(emptyList<AppEntry>(), now / 60_000) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }
    val visible = apps.filter { mode == Mode.BREAK || Policy.isAllowedInStudy(ctx, it.pkg) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), fontSize = 56.sp, fontWeight = FontWeight.Light)
                Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("🔒", fontSize = 26.sp, modifier = Modifier.clickable { showAdmin = true }.padding(12.dp))
        }

        Panel {
            Text(if (mode == Mode.STUDY) "STUDY TIME" else "BREAK", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(if (left >= 0) formatDuration(left) else "—", fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    !trusted -> "Time can't be verified offline. Connect to the internet to unlock your break."
                    forced -> "Set by your admin."
                    mode == Mode.STUDY -> "Only your allowed apps are available. Hi ${cfg?.studentName.orEmpty()}, stay with it."
                    else -> "Enjoy your break. Study mode returns automatically."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
            )
        }

        cfg?.message?.takeIf { it.isNotBlank() }?.let { m ->
            Panel {
                Text("MESSAGE FROM YOUR ADMIN", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(m)
            }
        }

        val key = Stats.key(now)
        val today = days[key]
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Focus today", "${today?.focusMin ?: 0} min", Modifier.weight(1f))
            StatTile("Tasks done", "${today?.tasks ?: 0}", Modifier.weight(1f))
            StatTile("Streak", "${Stats.streak()} d", Modifier.weight(1f))
        }

        val pending = tasks.filter { !it.done }.sortedByDescending { it.priority }.take(3)
        if (pending.isNotEmpty()) Panel {
            Text("Up next", fontWeight = FontWeight.SemiBold)
            pending.forEach { Text("• ${it.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }

        Text(if (mode == Mode.STUDY) "Allowed apps" else "Apps", fontWeight = FontWeight.SemiBold)
        if (visible.isEmpty()) Text("No allowed apps installed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppGrid(visible.take(8), Modifier.fillMaxWidth().height(if (visible.size > 4) 220.dp else 110.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { quickSetting(ctx, Settings.ACTION_WIFI_SETTINGS) }, label = { Text("Wi-Fi") })
            AssistChip(onClick = { quickSetting(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) }, label = { Text("Bluetooth") })
            AssistChip(onClick = { quickSetting(ctx, Settings.ACTION_SOUND_SETTINGS) }, label = { Text("Sound") })
        }
    }
    if (showAdmin) AdminDialog { showAdmin = false }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Panel(modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(4), modifier = modifier,
        contentPadding = PaddingValues(0.dp), userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { gridItems(apps, ctx) }
}

@Composable
private fun AppsTab(mode: Mode) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val apps by produceState(emptyList<AppEntry>(), now / 60_000) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }
    var q by remember { mutableStateOf("") }
    val visible = apps.filter { (mode == Mode.BREAK || Policy.isAllowedInStudy(ctx, it.pkg)) && it.label.contains(q, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(if (mode == Mode.STUDY) "Allowed apps" else "All apps", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth())
        LazyVerticalGrid(
            columns = GridCells.Fixed(4), modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) { gridItems(visible, ctx) }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.gridItems(apps: List<AppEntry>, ctx: Context) {
    items(apps, key = { it.pkg }) { a ->
        Column(
            Modifier.clickable { launch(ctx, a.pkg) }.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(a.pkg, 52.dp)
            Text(a.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
