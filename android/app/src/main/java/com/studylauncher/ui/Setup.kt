package com.studylauncher.ui

import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylauncher.Api
import com.studylauncher.Enforcer
import com.studylauncher.GuardService
import com.studylauncher.Protection
import com.studylauncher.Req
import com.studylauncher.Store
import com.studylauncher.Sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun PairScreen(onPaired: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var newStudent by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(Store.baseUrl) }
    var code by remember { mutableStateOf("") }
    var enrollCode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var guardian by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    val canGo = !busy && (if (newStudent) enrollCode.isNotBlank() && name.trim().length >= 2 else code.length >= 6)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Study Launcher", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!newStudent) Button(onClick = {}) { Text("I have a code") } else OutlinedButton(onClick = { newStudent = false; err = "" }) { Text("I have a code") }
            if (newStudent) Button(onClick = {}) { Text("New student") } else OutlinedButton(onClick = { newStudent = true; err = "" }) { Text("New student") }
        }
        if (!newStudent) {
            Text("Enter the 8-character pairing code from your admin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(8) }, label = { Text("Pairing code") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
        } else {
            Text("Set up this phone yourself. Your admin will see you in their panel and review your setup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = enrollCode, onValueChange = { enrollCode = it }, label = { Text("Enrollment code (from your admin)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = guardian, onValueChange = { guardian = it }, label = { Text("Parent / guardian e-mail") }, singleLine = true,
                supportingText = { Text("Password reset codes go here, after your admin confirms it.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = url, onValueChange = { url = it }, label = { Text("Server address") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
        Button(
            enabled = canGo, modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true; err = ""
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        Store.baseUrl = url
                        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
                        val r = if (newStudent) Api.post(
                            "/api/device/enroll",
                            JSONObject().put("enrollCode", enrollCode).put("name", name).put("guardianEmail", guardian).put("deviceName", device),
                            auth = false,
                        ) else Api.post("/api/device/pair", JSONObject().put("pairingCode", code).put("deviceName", device), auth = false)
                        when {
                            r == null -> { err = "Cannot reach the server. Check internet and the address."; false }
                            r.code != 200 || r.json == null -> { err = r.json?.optString("error")?.ifEmpty { null } ?: "Failed (${r.code})"; false }
                            else -> {
                                Store.token = r.json.getString("token")
                                Store.offlineSecret = r.json.getString("offlineSecret")
                                Sync.config(ctx)
                                true
                            }
                        }
                    }
                    busy = false
                    if (ok) { GuardService.start(ctx); onPaired() }
                }
            },
        ) { Text(if (busy) "Connecting…" else if (newStudent) "Create & continue" else "Connect") }
    }
}

private fun grant(ctx: Context, r: Req) {
    Enforcer.openFixWindow()
    val pkg = Uri.parse("package:${ctx.packageName}")
    val i = when (r) {
        Req.ADMIN -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Protection.adminComponent(ctx))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents the launcher from being uninstalled without the secret password.")
        Req.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Req.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        Req.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
        Req.NOTIF -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        Req.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        Req.HOME -> if (Build.VERSION.SDK_INT >= 29 && ctx.getSystemService(RoleManager::class.java).isRoleAvailable(RoleManager.ROLE_HOME))
            ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
        else Intent(Settings.ACTION_HOME_SETTINGS)
    }
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(Req.values().associateWith { Protection.granted(ctx, it) }) }
    LaunchedEffect(Unit) {
        while (true) {
            status = Req.values().associateWith { Protection.granted(ctx, it) }
            delay(700)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Finish setup", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Every item below is required. The launcher stays locked on this screen until all are on, " +
            "and it will return here if any is switched off later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Req.values().forEach { r ->
            val ok = status[r] == true
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (ok) "✓  " else "") + r.title, fontWeight = FontWeight.SemiBold)
                        Text(r.why, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!ok) Button(onClick = { grant(ctx, r) }) { Text("Grant") }
                }
            }
        }
        Text("Accessibility or Notification access greyed out? On Android 13+ open App info, tap ⋮ and choose " +
            "“Allow restricted settings”, then try again.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            Enforcer.openFixWindow()
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { Text("Open App info") }
    }
}
