@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import app.personal.clock.core.CustomDay
import app.personal.clock.core.Fmt
import app.personal.clock.core.WorldEntry
import app.personal.clock.data.AppSettings
import app.personal.clock.data.Store
import app.personal.clock.holiday.HolidayChecker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Time zone & holidays": one switch (the alarm time zone) decides the holiday country; everything else refines it. */
@Composable
fun TimeZoneAndHolidays(settings: AppSettings, refresh: Int, onSave: (AppSettings) -> Unit, onGrant: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var showZone by remember { mutableStateOf(false) }
    var showCountry by remember { mutableStateOf(false) }
    var showCalendars by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showTestPicker by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var customVersion by remember { mutableIntStateOf(0) }
    var names by remember(settings.calendarNames) { mutableStateOf(settings.calendarNames) }

    val zone = settings.zone()
    val region = remember(refresh, settings) { HolidayChecker.region(ctx) }
    val country = HolidayChecker.countryName(region.code)
    val builtinAvailable = remember(refresh, region.code) { HolidayChecker.builtinAvailable(ctx, region.code) }
    val builtinYears = remember(refresh, region.code) { HolidayChecker.builtinYears(ctx, region.code) }
    val hasPerm = HolidayChecker.hasCalendarPermission(ctx)
    val customCount = remember(customVersion, refresh) { store.customDays().size }
    val source = remember(refresh, settings) { HolidayChecker.sourceFor(ctx, LocalDate.now(zone).year) }

    // ---- time zone
    LabeledRow("Home time zone") {
        OutlinedButton(onClick = { showZone = true }, enabled = !settings.followPhoneZone) {
            Text(Fmt.cityName(zone.id), maxLines = 1)
        }
    }
    LabeledRow("Follow phone time zone") {
        Switch(checked = settings.followPhoneZone, onCheckedChange = { onSave(settings.copy(followPhoneZone = it)) })
    }
    Hint(explain(ctx, settings, zone, region.code, country, builtinAvailable, hasPerm))

    // ---- holiday sources
    SectionTitle("Public holidays")
    LabeledRow("Holiday country") {
        OutlinedButton(onClick = { showCountry = true }) { Text(if (region.fromZone) "$country (auto)" else country, maxLines = 1) }
    }
    LabeledRow("Use built-in holiday list") {
        Switch(
            checked = builtinAvailable && settings.useBuiltinHolidays,
            onCheckedChange = { onSave(settings.copy(useBuiltinHolidays = it)) },
            enabled = builtinAvailable,
        )
    }
    Hint(
        if (builtinAvailable) "Built in for $country: ${builtinYears.joinToString(", ")}. Check it against your government's list."
        else "This app has no built-in list for $country. A phone holiday calendar or your own days cover it.",
    )
    LabeledRow("Phone calendars") {
        OutlinedButton(onClick = { if (hasPerm) showCalendars = true else onGrant() }) {
            Text(if (!hasPerm) "Allow access" else if (settings.calendarIds.isEmpty()) "Automatic" else "${settings.calendarIds.size} selected", maxLines = 1)
        }
    }
    Hint(
        if (!hasPerm) "Calendar access is off. Allow it to read holiday calendars already synced on this phone (read-only, stays on the phone)."
        else "Reads the holiday calendars synced on this phone (Google Calendar's \"Holidays in $country\", for example). Automatic picks the one that matches $country.",
    )

    // ---- custom days
    SectionTitle("Your own days off")
    LabeledRow("Custom skip days") {
        OutlinedButton(onClick = { showCustom = true }) { Text(if (customCount == 0) "Add" else "$customCount saved", maxLines = 1) }
    }
    Hint("Single dates or ranges (leave, trips, birthdays), optionally every year. Each alarm chooses whether to skip them.")

    // ---- test tool
    SectionTitle("Check a date")
    Text(
        "Deciding source for ${LocalDate.now(zone).year}: ${sourceLabel(source, country)}",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = { showTestPicker = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Test holiday check for date…") }
    testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }

    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced") }
    if (advanced) {
        OutlinedTextField(
            value = names,
            onValueChange = { names = it },
            label = { Text("Also use calendars whose name contains (separate with |)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (names != settings.calendarNames) {
            TextButton(onClick = { onSave(settings.copy(calendarNames = names.trim())) }) { Text("Save names") }
        }
    }

    // ---- dialogs and sheets
    if (showZone) {
        ZonePickerDialog(
            title = "Home time zone", existing = emptyList(), now = Instant.now(),
            onDismiss = { showZone = false },
            onPick = { entry -> showZone = false; onSave(settings.copy(homeZoneId = WorldEntry.zone(entry), followPhoneZone = false)) },
        )
    }
    if (showCountry) {
        CountryPickerDialog(
            current = settings.holidayRegion,
            autoLabel = "Automatic (from the time zone)",
            onDismiss = { showCountry = false },
            onPick = { code -> showCountry = false; onSave(settings.copy(holidayRegion = code)) },
        )
    }
    if (showCalendars) {
        CalendarsSheet(
            settings = settings, country = country,
            onDismiss = { showCalendars = false },
            onSave = { ids -> showCalendars = false; onSave(settings.copy(calendarIds = ids)) },
        )
    }
    if (showCustom) {
        CustomDaysSheet(store = store, onChanged = { customVersion++; onSave(settings) }, onDismiss = { showCustom = false })
    }
    if (showTestPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showTestPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis
                    if (ms != null) {
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        testResult = describeDate(ctx, store, d, zone, country)
                    }
                    showTestPicker = false
                }) { Text("Check") }
            },
            dismissButton = { TextButton(onClick = { showTestPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

private fun sourceLabel(source: HolidayChecker.Source, country: String): String = when (source) {
    HolidayChecker.Source.CALENDAR -> "Phone calendar"
    HolidayChecker.Source.BUILTIN -> "Built-in list ($country)"
    HolidayChecker.Source.NONE -> "No data"
}

/** One plain sentence: which zone the alarms use and which holidays apply. */
private fun explain(
    ctx: android.content.Context, settings: AppSettings, zone: ZoneId, region: String?, country: String,
    builtinAvailable: Boolean, hasPerm: Boolean,
): String {
    val sb = StringBuilder("Alarms use ${zone.id} time. ")
    if (region == null) {
        sb.append("No country applies to this zone, so choose a holiday country below or use your own days.")
        return sb.toString()
    }
    val sources = ArrayList<String>()
    if (hasPerm) sources += "phone calendar"
    if (builtinAvailable && settings.useBuiltinHolidays) sources += "built-in list"
    sb.append("Holidays: $country")
    sb.append(if (sources.isEmpty()) " (no source yet: allow calendar access or add your own days)." else " (${sources.joinToString(" + ")}).")
    if (settings.followPhoneZone && region != "TH" && HolidayChecker.builtinAvailable(ctx, "TH")) {
        sb.append(" The built-in Thai list is not used here.")
    }
    return sb.toString()
}

private fun describeDate(ctx: android.content.Context, store: Store, d: LocalDate, zone: ZoneId, country: String): String {
    val h = HolidayChecker.check(ctx, d, fresh = true)
    val day = d.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))
    val where = sourceLabel(h.source, country)
    val custom = store.customDays().firstOrNull { it.matches(d) }
    val main = if (h.name != null) "public holiday – ${h.name} [source: $where]" else "not a public holiday [source: $where]"
    return "$day (${zone.id}): $main" + (custom?.let { "; your custom day – ${it.label.ifBlank { "Custom day" }}" } ?: "")
}

// ------------------------------------------------------------------ country picker

@Composable
private fun CountryPickerDialog(current: String, autoLabel: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val all = remember {
        Locale.getISOCountries().map { it to Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH).ifBlank { it } }
            .sortedBy { it.second }
    }
    var query by remember { mutableStateOf("") }
    val shown = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { it.second.lowercase().contains(q) || it.first.lowercase() == q }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Holiday country") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text("Search country") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)) {
                    item {
                        Text(
                            autoLabel + if (current.isEmpty()) "  ✓" else "",
                            Modifier.fillMaxWidth().clickable { onPick("") }.padding(vertical = 12.dp),
                            fontWeight = FontWeight.Bold,
                        )
                        HorizontalDivider()
                    }
                    items(shown, key = { it.first }) { (code, name) ->
                        Text(
                            name + if (code.equals(current, true)) "  ✓" else "",
                            Modifier.fillMaxWidth().clickable { onPick(code) }.padding(vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ------------------------------------------------------------------ phone calendars

@Composable
private fun CalendarsSheet(settings: AppSettings, country: String, onDismiss: () -> Unit, onSave: (List<Long>) -> Unit) {
    val ctx = LocalContext.current
    val calendars = remember { HolidayChecker.listCalendars(ctx) }
    var selected by remember {
        mutableStateOf(if (settings.calendarIds.isNotEmpty()) settings.calendarIds.toSet() else calendars.filter { it.suggested }.map { it.id }.toSet())
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Holiday calendars", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Hint("Tick the calendars that list public holidays. The one for $country is pre-ticked when found. Only all-day events are used, and nothing leaves the phone.")
            if (calendars.isEmpty()) {
                Hint("No calendars found on this phone.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(calendars, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (c.id in selected) selected - c.id else selected + c.id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = c.id in selected, onCheckedChange = { on -> selected = if (on) selected + c.id else selected - c.id })
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                if (c.account.isNotBlank()) Hint(c.account)
                            }
                            if (c.suggested) Pill("Suggested", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        HorizontalDivider()
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSave(emptyList()) }) { Text("Automatic") }
                Row(Modifier.weight(1f)) {}
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(selected.toList()) }) { Text("Done") }
            }
        }
    }
}

// ------------------------------------------------------------------ custom skip days

private fun dayText(c: CustomDay): String {
    val pattern = if (c.yearly) "d MMM" else "d MMM yyyy"
    val f = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    val to = c.to
    return if (to == null || to == c.from) c.from.format(f) else c.from.format(f) + " – " + to.format(f)
}

@Composable
private fun CustomDaysSheet(store: Store, onChanged: () -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(store.customDays()) }
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Custom skip days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Hint("Alarms that have \"Skip my custom days\" on will not ring on these dates, judged in the alarm's own time zone.")
            if (days.isEmpty()) Hint("Nothing yet.")
            LazyColumn(Modifier.fillMaxWidth().height(280.dp)) {
                items(days, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.label.ifBlank { "Custom day" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Hint(dayText(c))
                        }
                        Pill(if (c.yearly) "Yearly" else "One-off", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        IconButton(onClick = {
                            store.deleteCustomDay(c.id)
                            days = store.customDays()
                            onChanged()
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${c.label}") }
                    }
                    HorizontalDivider()
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }) { Text("Add skip day") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
    if (adding) {
        AddCustomDayDialog(
            onDismiss = { adding = false },
            onSave = { c ->
                adding = false
                store.upsertCustomDay(c)
                days = store.customDays()
                onChanged()
            },
        )
    }
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun AddCustomDayDialog(onDismiss: () -> Unit, onSave: (CustomDay) -> Unit) {
    var label by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(LocalDate.now()) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var yearly by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) } // "from" | "to"
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add skip day") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(40) }, label = { Text("Label (e.g. Annual leave)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                LabeledRow("From") { OutlinedButton(onClick = { picking = "from" }) { Text(from.format(fmt), maxLines = 1) } }
                LabeledRow("To (optional)") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { picking = "to" }) { Text(to?.format(fmt) ?: "Single day", maxLines = 1) }
                        if (to != null) TextButton(onClick = { to = null }) { Text("Clear") }
                    }
                }
                LabeledRow("Repeat every year") { Switch(checked = yearly, onCheckedChange = { yearly = it }) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(CustomDay(0, label.trim(), from, to, yearly)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    picking?.let { which ->
        val start = if (which == "from") from else (to ?: from)
        val state = rememberDatePickerState(initialSelectedDateMillis = start.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "from") from = d else to = d
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}
