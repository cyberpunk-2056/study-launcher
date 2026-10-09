package com.studylauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylauncher.Phase
import com.studylauncher.Pomodoro
import com.studylauncher.Stats
import com.studylauncher.Tasks
import com.studylauncher.formatDuration

// ---------------------------------------------------------------- Focus (Pomodoro)

@Composable
fun FocusTab() {
    val s by Pomodoro.state.collectAsState()
    val now by rememberNow()
    val left = remember(now, s) { Pomodoro.remaining() }
    val accent = if (s.phase == Phase.FOCUS) StudyRed else BreakGreen
    var editing by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pomodoro", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(s.phase.label.uppercase(), color = accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(formatDuration(left), fontSize = 76.sp, fontWeight = FontWeight.Light)
        val total = s.lengthOf(s.phase).coerceAtLeast(1)
        Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth((1f - left.toFloat() / total).coerceIn(0f, 1f)).height(8.dp).background(accent, RoundedCornerShape(4.dp)))
        }
        Text("Sessions completed today cycle: ${s.doneFocus % 4}/4 · total ${s.doneFocus}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.running) Button(onClick = { Pomodoro.pause() }) { Text("Pause") }
            else Button(onClick = { Pomodoro.start() }) { Text("Start") }
            OutlinedButton(onClick = { Pomodoro.reset() }) { Text("Reset") }
            OutlinedButton(onClick = { Pomodoro.skip() }) { Text("Skip") }
        }
        TextButton(onClick = { editing = !editing }) { Text(if (editing) "Hide lengths" else "Change lengths") }
        if (editing) Panel {
            Stepper("Focus", s.focusMin) { Pomodoro.setLengths(it, s.shortMin, s.longMin) }
            Stepper("Short break", s.shortMin) { Pomodoro.setLengths(s.focusMin, it, s.longMin) }
            Stepper("Long break", s.longMin) { Pomodoro.setLengths(s.focusMin, s.shortMin, it) }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(value - 5) }) { Text("−5") }
        Text("$value min", Modifier.width(76.dp), textAlign = TextAlign.Center)
        OutlinedButton(onClick = { onChange(value + 5) }) { Text("+5") }
    }
}

// ---------------------------------------------------------------- Tasks

@Composable
fun TasksTab() {
    val list by Tasks.list.collectAsState()
    var text by remember { mutableStateOf("") }
    var pri by remember { mutableIntStateOf(1) }
    val sorted = list.sortedWith(compareBy({ it.done }, { -it.priority }, { it.id }))

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Tasks", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("Add a task, e.g. Physics ch. 4") }, modifier = Modifier.weight(1f))
            Button(enabled = text.isNotBlank(), onClick = { Tasks.add(text, pri); text = "" }) { Text("Add") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Low", 1 to "Normal", 2 to "High").forEach { (v, l) ->
                FilterChip(selected = pri == v, onClick = { pri = v }, label = { Text(l) })
            }
        }
        if (sorted.isEmpty()) Text("Nothing here yet. Add what you plan to finish this study block.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sorted, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = t.done, onCheckedChange = { Tasks.toggle(t.id) })
                    Column(Modifier.weight(1f)) {
                        Text(t.title, textDecoration = if (t.done) TextDecoration.LineThrough else null,
                            color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        if (!t.done && t.priority == 2) Text("High priority", fontSize = 11.sp, color = StudyRed)
                    }
                    TextButton(onClick = { Tasks.remove(t.id) }) { Text("✕") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Stats

@Composable
fun StatsTab() {
    val days by Stats.days.collectAsState()
    val week = remember(days) { Stats.lastDays(7) }
    val max = (week.maxOfOrNull { it.second.focusMin } ?: 0).coerceAtLeast(30)
    val total = week.sumOf { it.second.focusMin }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Progress", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)) { Text("${Stats.streak()} days", fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text("Focus streak", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Panel(Modifier.weight(1f)) { Text("${total / 60}h ${total % 60}m", fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text("Last 7 days", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Panel {
            Text("Focus minutes per day", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.forEach { (k, d) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text("${d.focusMin}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth().height((100f * d.focusMin / max).coerceAtLeast(2f).dp).background(Indigo, RoundedCornerShape(6.dp)))
                        Text(k.takeLast(2), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Panel {
            Text("Last 7 days", fontWeight = FontWeight.SemiBold)
            week.reversed().forEach { (k, d) ->
                Row { Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text("${d.focusMin} min · ${d.pomodoros} sessions · ${d.tasks} tasks", fontSize = 13.sp) }
            }
        }
    }
}
