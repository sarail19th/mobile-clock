@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.core.AlarmItem
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlarmScreen() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val is24 = DateFormat.is24HourFormat(ctx)
    var alarms by remember { mutableStateOf(store.alarms()) }
    var editing by remember { mutableStateOf<AlarmItem?>(null) }
    val tick by rememberTicker(60_000L)
    var version by remember { mutableIntStateOf(0) }

    fun refresh() {
        alarms = store.alarms()
        AlarmScheduler.rescheduleAll(ctx)
        version++
    }

    val next = remember(tick, version) { AlarmScheduler.nextOverall(ctx) }
    val zone = remember(version) { store.settings().zone() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 8.dp)) {
                    if (next != null) {
                        val untilMs = next.at.toInstant().toEpochMilli() - tick
                        Text("Next alarm in ${Fmt.until(untilMs)}", style = MaterialTheme.typography.headlineSmall)
                        val whenText = next.at.format(
                            DateTimeFormatter.ofPattern(if (is24) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a", Locale.getDefault()),
                        )
                        Hint("$whenText · ${next.alarm.label.ifBlank { "Alarm" }}")
                        val skipped = next.skipped.firstOrNull()
                        if (skipped != null) {
                            val more = if (next.skipped.size > 1) " (+${next.skipped.size - 1} more)" else ""
                            Hint("Skipping ${skipped.name} on ${skipped.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))}$more")
                        }
                    } else {
                        Text("No alarm set", style = MaterialTheme.typography.headlineSmall)
                    }
                    Hint(
                        "Alarms ring on ${Fmt.cityName(zone.id)} time (${Fmt.utcOffset(zone.rules.getOffset(java.time.Instant.ofEpochMilli(tick)).totalSeconds)})" +
                            if (store.settings().followPhoneZone) ", following the phone" else "",
                        Modifier.padding(top = 2.dp),
                    )
                }
            }
            items(alarms, key = { it.id }) { a ->
                AlarmRow(
                    a, is24,
                    onToggle = { on ->
                        store.upsertAlarm(a.copy(enabled = on))
                        refresh()
                    },
                    onClick = { editing = a },
                )
            }
        }
        FloatingActionButton(
            onClick = {
                val s = store.settings()
                editing = AlarmItem(
                    id = 0, label = "", hour = 7, minute = 0, days = AlarmItem.WEEKDAYS,
                    snoozeMinutes = s.defaultSnoozeMinutes, skipHolidays = true,
                )
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Add alarm") }
    }

    val e = editing
    if (e != null) {
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AlarmEditor(
                alarm = e,
                onSave = { item ->
                    store.upsertAlarm(item)
                    editing = null
                    refresh()
                },
                onDelete = {
                    AlarmScheduler.cancel(ctx, e.id)
                    store.deleteAlarm(e.id)
                    editing = null
                    refresh()
                },
                onCancel = { editing = null },
            )
        }
    }
}

@Composable
private fun AlarmRow(a: AlarmItem, is24: Boolean, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val alpha = if (a.enabled) 1f else 0.5f
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatHm(a.hour, a.minute, is24),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                Text(
                    listOf(a.label.ifBlank { "Alarm" }, daysSummary(a.days)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                if (a.skipHolidays && !a.isOneShot) {
                    Hint("Skips Thai public holidays")
                }
            }
            Switch(checked = a.enabled, onCheckedChange = onToggle)
        }
    }
}

// ------------------------------------------------------------------ editor

@Composable
private fun AlarmEditor(
    alarm: AlarmItem,
    onSave: (AlarmItem) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    var label by remember { mutableStateOf(alarm.label) }
    val timeState = rememberTimePickerState(initialHour = alarm.hour, initialMinute = alarm.minute, is24Hour = true)
    var days by remember { mutableIntStateOf(alarm.days) }
    var vibrate by remember { mutableStateOf(alarm.vibrate) }
    var skip by remember { mutableStateOf(alarm.skipHolidays) }
    var snooze by remember { mutableIntStateOf(alarm.snoozeMinutes) }
    var ringtone by remember { mutableStateOf(alarm.ringtoneUri) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val picked: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                res.data!!.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data!!.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            ringtone = when {
                picked == null -> ""                                                   // "Silent"
                picked == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) -> null // "Default"
                else -> picked.toString()
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (alarm.id == 0) "New alarm" else "Edit alarm", style = MaterialTheme.typography.titleLarge)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) }

        OutlinedTextField(
            value = label, onValueChange = { label = it.take(40) },
            label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Text("Repeat", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, ch ->
                val on = days and (1 shl i) != 0
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { days = days xor (1 shl i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ch,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Hint(if (days == 0) "No day selected: rings once at the next ${"%02d:%02d".format(timeState.hour, timeState.minute)}." else daysSummary(days))

        LabeledRow("Skip Thai public holidays") { Switch(checked = skip, onCheckedChange = { skip = it }, enabled = days != 0) }
        LabeledRow("Vibrate") { Switch(checked = vibrate, onCheckedChange = { vibrate = it }) }

        LabeledRow("Snooze: $snooze min") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { snooze = (snooze - 1).coerceAtLeast(1) }) { Text("−") }
                Box(Modifier.size(8.dp))
                OutlinedButton(onClick = { snooze = (snooze + 1).coerceAtMost(60) }) { Text("+") }
            }
        }

        LabeledRow("Sound") {
            OutlinedButton(onClick = {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    val current: Uri? = when (ringtone) {
                        null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        "" -> null
                        else -> Uri.parse(ringtone)
                    }
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                }
                picker.launch(i)
            }) { Text("Choose") }
        }
        Hint(ringtoneTitle(ctx, ringtone))

        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (alarm.id != 0) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = {
                onSave(
                    alarm.copy(
                        label = label.trim(),
                        hour = timeState.hour,
                        minute = timeState.minute,
                        days = days,
                        enabled = true,
                        ringtoneUri = ringtone,
                        vibrate = vibrate,
                        snoozeMinutes = snooze,
                        skipHolidays = skip,
                    ),
                )
            }) { Text("Save") }
        }
    }
}
