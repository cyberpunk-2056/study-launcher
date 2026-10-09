package com.studylauncher.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.studylauncher.AdminAuth
import com.studylauncher.Protection
import com.studylauncher.Recovery
import com.studylauncher.Verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Stage { PASSWORD, FORGOT, CODE, OPTIONS, CONFIRM_REMOVE }

/** Password-gated area: temporary Settings access, or permanently removing the launcher's protection. */
@Composable
fun AdminDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(Stage.PASSWORD) }
    var pw by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    fun <T> work(job: () -> T, done: (T) -> Unit) {
        busy = true; err = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { job() }
            busy = false
            done(r)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                when (stage) {
                    Stage.PASSWORD -> "Admin password"
                    Stage.FORGOT -> "Forgot password"
                    Stage.CODE -> "Enter reset code"
                    Stage.OPTIONS -> "Admin options"
                    Stage.CONFIRM_REMOVE -> "Remove protection?"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (stage) {
                    Stage.PASSWORD -> {
                        Text("Enter the secret password, or the one-time code your admin gave you.", color = dim)
                        OutlinedTextField(
                            value = pw, onValueChange = { pw = it }, singleLine = true, enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(enabled = !busy, onClick = { stage = Stage.FORGOT; err = ""; info = "" }) { Text("Forgot password?") }
                    }
                    Stage.FORGOT -> {
                        Text(
                            "A reset code will be e-mailed to the parent / guardian e-mail your admin approved. " +
                                "They decide whether to share it with you.",
                            color = dim,
                        )
                    }
                    Stage.CODE -> {
                        Text(info, color = dim)
                        OutlinedTextField(
                            value = code, onValueChange = { code = it.filter(Char::isDigit).take(8) }, singleLine = true, enabled = !busy,
                            label = { Text("8-digit code") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newPw, onValueChange = { newPw = it }, singleLine = true, enabled = !busy,
                            label = { Text("New password (min 6)") }, visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Stage.OPTIONS -> {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                AdminAuth.unlockSettings()
                                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                onDismiss()
                            },
                        ) { Text("Open Settings (10 minutes)") }
                        Button(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), onClick = { stage = Stage.CONFIRM_REMOVE }) {
                            Text("Remove launcher protection…")
                        }
                    }
                    Stage.CONFIRM_REMOVE -> Text(
                        "This switches off device-admin protection and the study restrictions on this phone. " +
                            "You will then be able to uninstall the launcher. Continue?"
                    )
                }
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            when (stage) {
                Stage.PASSWORD -> Button(enabled = !busy && pw.isNotBlank(), onClick = {
                    work({ AdminAuth.verify(pw) }) { r ->
                        pw = ""
                        when (r) {
                            Verify.Ok -> stage = Stage.OPTIONS
                            is Verify.Fail -> err = r.msg
                        }
                    }
                }) { Text(if (busy) "Checking…" else "Unlock") }

                Stage.FORGOT -> Button(enabled = !busy, onClick = {
                    work({ Recovery.requestCode() }) { (ok, text) ->
                        if (ok) {
                            info = "A code was sent to $text. It expires in 15 minutes."
                            stage = Stage.CODE
                        } else err = text
                    }
                }) { Text(if (busy) "Sending…" else "E-mail a code") }

                Stage.CODE -> Button(enabled = !busy && code.length == 8 && newPw.length >= 6, onClick = {
                    work({ Recovery.resetPassword(code, newPw) }) { (ok, text) ->
                        if (ok) {
                            Toast.makeText(ctx, "Password changed. Use the new password now.", Toast.LENGTH_LONG).show()
                            code = ""; newPw = ""; stage = Stage.PASSWORD
                        } else err = text
                    }
                }) { Text(if (busy) "Saving…" else "Change password") }

                Stage.CONFIRM_REMOVE -> Button(onClick = {
                    Protection.release(ctx, "removed with admin password")
                    Toast.makeText(ctx, "Protection removed. You can now uninstall the launcher.", Toast.LENGTH_LONG).show()
                    onDismiss()
                }) { Text("Yes, remove") }

                Stage.OPTIONS -> {}
            }
        },
        dismissButton = {
            val back = stage == Stage.FORGOT || stage == Stage.CODE
            TextButton(enabled = !busy, onClick = { if (back) { stage = Stage.PASSWORD; err = "" } else onDismiss() }) {
                Text(if (back) "Back" else "Close")
            }
        },
    )
}
