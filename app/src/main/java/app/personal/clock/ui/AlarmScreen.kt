@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.R
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.core.AlarmItem
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import app.personal.clock.holiday.HolidayChecker
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlarmScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val is24 = DateFormat.is24HourFormat(ctx)
    var alarms by remember { mutableStateOf(store.alarms()) }
    var editing by remember { mutableStateOf<AlarmItem?>(null) }
    val tick by rememberTicker(60_000L)
    var version by remember { mutableIntStateOf(0) }
    val uiPrefs = remember { ctx.getSharedPreferences("ui", 0) }
    var collapsed by remember { mutableStateOf(uiPrefs.getStringSet("collapsed_groups", emptySet()).orEmpty().toSet()) }
    fun toggleCollapsed(group: String) {
        collapsed = if (group in collapsed) collapsed - group else collapsed + group
        uiPrefs.edit().putStringSet("collapsed_groups", HashSet(collapsed)).apply()
    }

    fun refresh() {
        alarms = store.alarms()
        AlarmScheduler.rescheduleAll(ctx)
        version++
    }

    val next = remember(tick, version) { AlarmScheduler.nextOverall(ctx) }
    val skip = remember(version) { AlarmScheduler.nextSkip(ctx) }
    val zone = remember(version) { store.settings().zone() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Alarm", onSettings)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    // Summary card: when the next alarm rings, and the next holiday that will be skipped.
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                            if (next != null) {
                                val untilMs = next.at.toInstant().toEpochMilli() - tick
                                Text("Next alarm", fontSize = 13.sp)
                                Text("in ${Fmt.until(untilMs)}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    next.at.format(DateTimeFormatter.ofPattern(if (is24) "EEE d MMM · HH:mm" else "EEE d MMM · h:mm a", Locale.getDefault())) +
                                        " · " + next.alarm.label.ifBlank { "Alarm" },
                                    fontSize = 14.sp,
                                )
                            } else {
                                Text("No alarm set", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                Text("Turn an alarm on or add a new one.", fontSize = 14.sp)
                            }
                            skip?.let { (date, name) ->
                                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                                    Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                                    Text(
                                        "Next skip: " + date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) + " \u00B7 " + name,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    Hint(
                        "Alarms ring on ${Fmt.cityName(zone.id)} time (${Fmt.utcOffset(zone.rules.getOffset(java.time.Instant.ofEpochMilli(tick)).totalSeconds)})" +
                            if (store.settings().followPhoneZone) ", following the phone" else "",
                        Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                // Grouped alarms get a header with one switch for the whole group; ungrouped ones sit under "No group" (not "Other", so it never clashes with a group someone names Other).
                val grouped = alarms.groupBy { it.group.trim() }
                val anyGroup = grouped.keys.any { it.isNotEmpty() }
                grouped.forEach { (group, members) ->
                    val isCollapsed = group in collapsed
                    if (anyGroup) {
                        item(key = "group:$group") {
                            GroupHeader(
                                name = group.ifEmpty { "No group" },
                                members = members,
                                collapsed = isCollapsed,
                                onToggleCollapse = { toggleCollapsed(group) },
                                // One switch per section, including "No group" (the alarms with no group).
                                onToggleAll = { on ->
                                    members.forEach {
                                        store.upsertAlarm(it.copy(enabled = on))
                                        if (!on) AlarmScheduler.silence(ctx, it.id)
                                    }
                                    refresh()
                                },
                            )
                        }
                    }
                    if (!isCollapsed) {
                        items(members, key = { it.id }) { a ->
                            AlarmRow(
                                a, is24,
                                onToggle = { on ->
                                    store.upsertAlarm(a.copy(enabled = on))
                                    if (!on) AlarmScheduler.silence(ctx, a.id)
                                    refresh()
                                },
                                onClick = { editing = a },
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                val s = store.settings()
                editing = AlarmItem(
                    id = 0, label = "", hour = 7, minute = 0, days = AlarmItem.WEEKDAYS,
                    snoozeMinutes = s.defaultSnoozeMinutes, skipHolidays = true,
                )
            },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New alarm") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    val e = editing
    if (e != null) {
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AlarmEditor(
                alarm = e,
                groups = alarms.map { it.group.trim() }.filter { it.isNotEmpty() }.distinct(),
                onSave = { item ->
                    store.upsertAlarm(item)
                    editing = null
                    refresh()
                },
                onDelete = {
                    AlarmScheduler.cancel(ctx, e.id)
                    AlarmScheduler.silence(ctx, e.id)
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
private fun GroupHeader(
    name: String,
    members: List<AlarmItem>,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleAll: ((Boolean) -> Unit)?,
) {
    val on = members.count { it.enabled }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onToggleCollapse).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = if (collapsed) "Expand $name" else "Collapse $name",
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Hint(if (collapsed) "$on of ${members.size} on \u00B7 tap to show" else "$on of ${members.size} on")
        }
        if (onToggleAll != null) {
            Switch(checked = on > 0, onCheckedChange = onToggleAll)
        }
    }
}

@Composable
private fun AlarmRow(a: AlarmItem, is24: Boolean, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val alpha = if (a.enabled) 1f else 0.55f
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatHm(a.hour, a.minute, is24),
                    fontSize = 38.sp, fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                Text(
                    a.label.ifBlank { "Alarm" } + if (a.isOneShot) " · Once" else "",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                if (!a.isOneShot) DayDots(a.days)
                if ((a.skipHolidays || a.skipCustomDays) && !a.isOneShot) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "  " + when {
                                a.skipHolidays && a.skipCustomDays -> "Skips public holidays and my days"
                                a.skipHolidays -> "Skips public holidays"
                                else -> "Skips my custom days"
                            },
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Switch(checked = a.enabled, onCheckedChange = onToggle)
        }
    }
}

// ------------------------------------------------------------------ editor

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Hint(subtitle)
        }
        trailing()
    }
}

@Composable
private fun AlarmEditor(
    alarm: AlarmItem,
    groups: List<String>,
    onSave: (AlarmItem) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    var label by remember { mutableStateOf(alarm.label) }
    val timeState = rememberTimePickerState(initialHour = alarm.hour, initialMinute = alarm.minute, is24Hour = true)
    // Typing is the default; the owner can switch to the clock dial and the choice is remembered.
    val timePrefs = remember { ctx.getSharedPreferences("ui", 0) }
    var useDial by remember { mutableStateOf(timePrefs.getBoolean("alarm_time_dial", false)) }
    var days by remember { mutableIntStateOf(alarm.days) }
    var vibrate by remember { mutableStateOf(alarm.vibrate) }
    var skip by remember { mutableStateOf(alarm.skipHolidays) }
    var skipCustom by remember { mutableStateOf(alarm.skipCustomDays) }
    val customCount = remember { Store.get(ctx).customDays().size }
    val alarmZone = remember { Store.get(ctx).settings().zone() }
    val holidayRegion = remember { HolidayChecker.region(ctx).code }
    var snooze by remember { mutableIntStateOf(alarm.snoozeMinutes) }
    var ringtone by remember { mutableStateOf(alarm.ringtoneUri) }
    var group by remember { mutableStateOf(alarm.group) }

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

    fun launchPicker() {
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
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (alarm.id == 0) "New alarm" else "Edit alarm",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                useDial = !useDial
                timePrefs.edit().putBoolean("alarm_time_dial", useDial).apply()
            }) { Text(if (useDial) "Type time" else "Pick on dial") }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (useDial) TimePicker(state = timeState) else TimeInput(state = timeState)
        }
        Hint(
            if (days == 0) "Rings once, at the next ${"%02d:%02d".format(timeState.hour, timeState.minute)}."
            else "Rings at ${"%02d:%02d".format(timeState.hour, timeState.minute)} · ${daysSummary(days)}",
        )

        SectionTitle("Repeat", Modifier.padding(top = 0.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, ch ->
                val on = days and (1 shl i) != 0
                ChoiceChip(
                    selected = on,
                    onClick = { days = days xor (1 shl i) },
                    label = { Text(ch, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.size(width = 44.dp, height = 40.dp),
                    showCheck = false,
                )
            }
        }
        // Wraps onto a second line on large font sizes instead of breaking a word; the one matching the days is highlighted.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChoiceChip(selected = days == AlarmItem.WEEKDAYS, onClick = { days = AlarmItem.WEEKDAYS }, label = { Text("Weekdays", maxLines = 1) })
            ChoiceChip(selected = days == 0x7F, onClick = { days = 0x7F }, label = { Text("Daily", maxLines = 1) })
            ChoiceChip(selected = days == 0, onClick = { days = 0 }, label = { Text("Once", maxLines = 1) })
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = label, onValueChange = { label = it.take(40) },
                        label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = group, onValueChange = { group = it.take(24) },
                        label = { Text("Group (optional, e.g. Work)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("Work") + groups).distinct().forEach { g ->
                            AssistChip(onClick = { group = g }, label = { Text(g) })
                        }
                        if (group.isNotEmpty()) AssistChip(onClick = { group = "" }, label = { Text("No group") })
                    }
                    Hint("Alarms in a group can be switched on or off together from the list.")
                }
                HorizontalDivider()
                SettingRow(
                    "Skip public holidays",
                    if (holidayRegion == null) "No country for ${alarmZone.id}: set one in Settings" else "${HolidayChecker.countryName(holidayRegion)} \u00B7 phone calendar and built-in list",
                ) {
                    Switch(checked = skip, onCheckedChange = { skip = it }, enabled = days != 0)
                }
                HorizontalDivider()
                SettingRow("Skip my custom days", if (customCount == 0) "None saved yet (Settings > Time zone & holidays)" else "$customCount saved") {
                    Switch(checked = skipCustom, onCheckedChange = { skipCustom = it }, enabled = days != 0)
                }
                Hint(
                    "Dates are judged in ${alarmZone.id}, the zone this alarm rings in.",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                HorizontalDivider()
                SettingRow("Sound", ringtoneTitle(ctx, ringtone), onClick = ::launchPicker) {
                    OutlinedButton(onClick = ::launchPicker) { Text("Choose") }
                }
                HorizontalDivider()
                SettingRow("Vibrate") { Switch(checked = vibrate, onCheckedChange = { vibrate = it }) }
                HorizontalDivider()
                SettingRow("Snooze: $snooze min") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { snooze = (snooze - 1).coerceAtLeast(1) }) { Text("−") }
                        OutlinedButton(onClick = { snooze = (snooze + 1).coerceAtMost(60) }) { Text("+") }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        skipCustomDays = skipCustom,
                        group = group.trim(),
                    ),
                )
            }) { Text("Save") }
        }
    }
}
