package com.studylauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.studylauncher.Clock
import kotlinx.coroutines.delay

val StudyRed = Color(0xFFF2605C)
val BreakGreen = Color(0xFF3DCB93)
val Indigo = Color(0xFF8B87FF)

private val scheme = darkColorScheme(
    primary = Indigo,
    onPrimary = Color(0xFF14123A),
    background = Color(0xFF0D0F16),
    onBackground = Color(0xFFECEEF6),
    surface = Color(0xFF161923),
    onSurface = Color(0xFFECEEF6),
    surfaceVariant = Color(0xFF1F2330),
    onSurfaceVariant = Color(0xFF9AA0B8),
    error = StudyRed,
)

@Composable
fun StudyTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

/** Trusted time, ticking once a second. */
@Composable
fun rememberNow(): State<Long> = produceState(Clock.now()) {
    while (true) {
        value = Clock.now()
        delay(1000)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
