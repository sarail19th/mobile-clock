@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.alarm.TimerController
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import app.personal.clock.data.TimerItem

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val tick by rememberTicker(250L)
    var version by remember { mutableIntStateOf(0) }
    var showCustom by remember { mutableStateOf(false) }
    // Re-read each tick so a timer that just finished (state changed by the receiver) shows correctly.
    val timers = remember(tick, version) { store.timers() }
    val nowE = SystemClock.elapsedRealtime()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New timer", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 15).forEach { m ->
                    AssistChip(
                        onClick = { TimerController.add(ctx, m * 60_000L, ""); version++ },
                        label = { Text("$m min") },
                    )
                }
                AssistChip(onClick = { showCustom = true }, label = { Text("Custom…") })
            }
        }
        if (timers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Hint("Tap a preset to start a timer. Several can run at once.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(timers, key = { it.id }) { t ->
                    TimerCard(t, nowE, onChange = { version++ })
                }
            }
        }
    }

    if (showCustom) {
        CustomTimerDialog(
            onDismiss = { showCustom = false },
            onCreate = { ms, label ->
                showCustom = false
                TimerController.add(ctx, ms, label)
                version++
            },
        )
    }
}

@Composable
private fun TimerCard(t: TimerItem, nowE: Long, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val left = t.remaining(nowE)
    val running = t.state == TimerItem.RUNNING
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Fmt.clockHms(left),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    t.label.ifBlank { Fmt.clockHms(t.durationMs) + " timer" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LinearProgressIndicator(
                progress = { if (t.durationMs > 0) (left.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    Button(onClick = { TimerController.pause(ctx, t.id); onChange() }) { Text("Pause") }
                } else {
                    Button(onClick = { TimerController.start(ctx, t.id); onChange() }) {
                        Text(if (t.state == TimerItem.PAUSED) "Resume" else "Start")
                    }
                }
                OutlinedButton(onClick = { TimerController.reset(ctx, t.id); onChange() }) { Text("Reset") }
                Box(Modifier.weight(1f))
                TextButton(onClick = { TimerController.delete(ctx, t.id); onChange() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CustomTimerDialog(onDismiss: () -> Unit, onCreate: (Long, String) -> Unit) {
    var h by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val total = ((h.toLongOrNull() ?: 0L) * 3600 + (m.toLongOrNull() ?: 0L) * 60 + (s.toLongOrNull() ?: 0L)) * 1000L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("h", h, { v: String -> h = v.filter(Char::isDigit).take(2) }),
                        Triple("min", m, { v: String -> m = v.filter(Char::isDigit).take(3) }),
                        Triple("sec", s, { v: String -> s = v.filter(Char::isDigit).take(3) })).forEach { (name, value, set) ->
                        OutlinedTextField(
                            value = value, onValueChange = set, label = { Text(name) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(30) }, label = { Text("Label (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = total > 0, onClick = { onCreate(total, label.trim()) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
