@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import app.personal.clock.R
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
fun TimerScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val tick by rememberTicker(250L)
    var version by remember { mutableIntStateOf(0) }
    var showCustom by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimerItem?>(null) }
    var reorder by remember { mutableStateOf(false) }
    // Re-read each tick so a timer that just finished (state changed by the receiver) shows correctly.
    val timers = remember(tick, version) { store.timers() }
    val nowE = SystemClock.elapsedRealtime()
    val inReorder = reorder && timers.size > 1

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Timer", onSettings)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Quick start", Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 5, 10, 15).forEach { m ->
                    Card(
                        onClick = { TimerController.add(ctx, m * 60_000L, ""); version++ },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$m", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Hint("min")
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showCustom = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Custom timer", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Your timers", Modifier.weight(1f))
                if (timers.size > 1) {
                    TextButton(onClick = { reorder = !reorder }) { Text(if (inReorder) "Done" else "Reorder") }
                }
            }
        }
        if (timers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Hint("Tap a preset to start a timer. Several can run at once.", Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(timers, key = { _, t -> t.id }) { i, t ->
                    TimerCard(
                        t, nowE, onChange = { version++ },
                        reorder = inReorder, canUp = i > 0, canDown = i < timers.size - 1,
                        onMove = { d -> store.moveTimer(t.id, d); version++ },
                        onEdit = { editing = t },
                    )
                }
            }
        }
    }

    if (showCustom) {
        TimerDialog(
            initial = null,
            onDismiss = { showCustom = false },
            onConfirm = { ms, label ->
                showCustom = false
                TimerController.add(ctx, ms, label)
                version++
            },
        )
    }
    editing?.let { e ->
        TimerDialog(
            initial = e,
            onDismiss = { editing = null },
            onConfirm = { ms, label ->
                editing = null
                TimerController.update(ctx, e.id, ms, label)
                version++
            },
        )
    }
}

@Composable
private fun TimerCard(
    t: TimerItem, nowE: Long, onChange: () -> Unit,
    reorder: Boolean, canUp: Boolean, canDown: Boolean, onMove: (Int) -> Unit, onEdit: () -> Unit,
) {
    val ctx = LocalContext.current
    val left = t.remaining(nowE)
    val running = t.state == TimerItem.RUNNING
    val fraction = if (t.durationMs > 0) (left.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The ring is the play / pause button: tap it to start, pause or resume (disabled while reordering).
            val statusText = if (running) "${(fraction * 100).toInt()}%" else if (t.state == TimerItem.PAUSED) "Paused" else "Ready"
            val toggleLabel = if (running) "Pause" else if (t.state == TimerItem.PAUSED) "Resume" else "Start"
            Box(
                Modifier.size(76.dp).clip(CircleShape).clickable(enabled = !reorder) {
                    if (running) TimerController.pause(ctx, t.id) else TimerController.start(ctx, t.id)
                    onChange()
                },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(76.dp),
                    strokeWidth = 8.dp,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Icon(
                    painterResource(if (running) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = toggleLabel,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (reorder) 0.4f else 1f),
                )
            }
            // Time and label get the full middle width (they used to wrap when the buttons sat beside them).
            Column(Modifier.weight(1f)) {
                Text(Fmt.clockHms(left), fontSize = 32.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
                Hint(statusText + " \u00B7 " + t.label.ifBlank { Fmt.clockHms(t.durationMs) + " timer" })
                if (reorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoveButton("\u25B2", "Move up", canUp) { onMove(-1) }
                        MoveButton("\u25BC", "Move down", canDown) { onMove(1) }
                    }
                } else {
                    Row {
                        IconButton(onClick = { TimerController.reset(ctx, t.id); onChange() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { TimerController.delete(ctx, t.id); onChange() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/** Create ([initial] = null) or edit a saved timer: length in h / min / sec and an optional label. */
@Composable
private fun TimerDialog(initial: TimerItem?, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    val secs0 = (initial?.durationMs ?: 0L) / 1000L
    var h by remember { mutableStateOf(if (secs0 >= 3600) (secs0 / 3600).toString() else "") }
    var m by remember { mutableStateOf(if (secs0 >= 60) ((secs0 % 3600) / 60).toString() else "") }
    var s by remember { mutableStateOf(if (secs0 % 60 > 0) (secs0 % 60).toString() else "") }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    val total = ((h.toLongOrNull() ?: 0L) * 3600 + (m.toLongOrNull() ?: 0L) * 60 + (s.toLongOrNull() ?: 0L)) * 1000L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Custom timer" else "Edit timer") },
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
                if (initial != null && initial.state != TimerItem.IDLE) Hint("Saving resets this timer to Ready.")
            }
        },
        confirmButton = {
            TextButton(enabled = total > 0, onClick = { onConfirm(total, label.trim()) }) { Text(if (initial == null) "Start" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
