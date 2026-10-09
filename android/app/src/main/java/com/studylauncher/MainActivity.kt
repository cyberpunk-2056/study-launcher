package com.studylauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.studylauncher.ui.MainScaffold
import com.studylauncher.ui.PairScreen
import com.studylauncher.ui.SetupScreen
import com.studylauncher.ui.StudyTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StudyTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        GuardService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while already open: go back to the first tab.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) AppState.tab.value = 0
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    var paired by remember { mutableStateOf(Store.token != null) }
    var missing by remember { mutableStateOf(Protection.missing(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            missing = Protection.missing(ctx)
            delay(1000)
        }
    }
    // A launcher has nowhere to go "back" to.
    BackHandler(true) { AppState.tab.value = 0 }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !paired -> PairScreen { paired = true }
            missing.isNotEmpty() -> SetupScreen()
            else -> MainScaffold()
        }
    }
}
