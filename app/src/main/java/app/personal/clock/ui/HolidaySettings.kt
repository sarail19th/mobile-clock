@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Time zone & holidays": one switch (the alarm time zone) decides the holiday country; everything else refines it.
 * Laid out as a summary card, then one card per topic, each with a header and a one-line subtitle, and rows that keep
 * their help text to one short grey line.
 */
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
    var testResult by remember { mutableStateOf<DateCheck?>(null) }
    var customVersion by remember { mutableIntStateOf(0) }
    var names by remember(settings.calendarNames) { mutableStateOf(settings.calendarNames) }

    val zone = settings.zone()
    val today = LocalDate.now(zone)
    val city = Fmt.cityName(zone.id)
    val offset = Fmt.utcOffset(zone.rules.getOffset(Instant.now()).totalSeconds)
    val region = remember(refresh, settings) { HolidayChecker.region(ctx) }
    val country = HolidayChecker.countryName(region.code)
    val builtinAvailable = remember(refresh, region.code) { HolidayChecker.builtinAvailable(ctx, region.code) }
    val builtinYears = remember(refresh, region.code) { HolidayChecker.builtinYears(ctx, region.code) }
    val hasPerm = HolidayChecker.hasCalendarPermission(ctx)
    val customDays = remember(customVersion, refresh) { store.customDays() }
    val nextCustom = remember(customDays, today) { nextCustomDay(customDays, today) }
    val source = remember(refresh, settings) { HolidayChecker.sourceFor(ctx, today.year) }
    val calendarNames = remember(refresh, settings, hasPerm) {
        if (!hasPerm) emptyList() else {
            val all = HolidayChecker.listCalendars(ctx)
            (if (settings.calendarIds.isEmpty()) all.filter { it.suggested } else all.filter { it.id in settings.calendarIds }).map { it.name }
        }
    }
    val thaiBuiltin = remember(refresh) { HolidayChecker.builtinAvailable(ctx, "TH") }
    val thaiNote = settings.followPhoneZone && region.code != "TH" && thaiBuiltin

    // ---- summary
    val sources = ArrayList<String>()
    if (hasPerm) sources += "phone calendar"
    if (builtinAvailable && settings.useBuiltinHolidays) sources += "built-in list"
    val warn = region.code == null || source == HolidayChecker.Source.NONE
    SummaryCard(
        warn = warn,
        zoneTitle = "Alarms ring on $city time",
        zoneSub = (if (settings.followPhoneZone) "Following your phone" else "Home time zone") + " · $offset",
        holidayTitle = when {
            region.code == null -> "Holidays: no country"
            warn -> "Holidays: $country · no source"
            else -> "Holidays: $country"
        },
        holidaySub = when {
            region.code == null -> "This time zone has no country. Choose one under Public holidays."
            warn && !hasPerm -> "Holiday skipping won't work until a source is on."
            warn -> "No holiday data for ${today.year}. Check Phone calendars below."
            else -> sources.joinToString(" + ").replaceFirstChar { it.uppercase() } + if (thaiNote) " · Thai list not used here" else ""
        },
        action = if (warn && !hasPerm && region.code != null) ({ onGrant() }) else null,
        actionLabel = "Allow calendar access",
    )

    // ---- alarm time zone
    SectionCard("Alarm time zone", "The clock your alarms follow") {
        SettingRow(
            "Follow phone time zone",
            if (settings.followPhoneZone) "Changes when you travel" else "Off · alarms stay on home time",
            onClick = { onSave(settings.copy(followPhoneZone = !settings.followPhoneZone)) },
        ) {
            Switch(checked = settings.followPhoneZone, onCheckedChange = { onSave(settings.copy(followPhoneZone = it)) })
        }
        SettingRow(
            "Home time zone",
            if (settings.followPhoneZone) "Used when not following the phone" else "${zone.id} · $offset",
            enabled = !settings.followPhoneZone,
            onClick = { showZone = true },
        ) {
            ValueLink(Fmt.cityName(settings.homeZoneId), enabled = !settings.followPhoneZone)
        }
    }

    // ---- public holidays
    SectionCard("Public holidays", "Alarms set to skip holidays use these") {
        SettingRow(
            "Country",
            when {
                region.code == null -> "No country for this time zone"
                region.fromZone -> "Automatic, from the time zone"
                else -> "Chosen by you"
            },
            onClick = { showCountry = true },
        ) {
            ValueLink(if (region.code == null) "Choose" else country)
        }
        if (!hasPerm) {
            SettingRow("Phone calendars", "Off · read-only, stays on the phone", onClick = onGrant) {
                Button(onClick = onGrant, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Allow") }
            }
        } else {
            SettingRow(
                "Phone calendars",
                when (calendarNames.size) {
                    0 -> "No holiday calendar found on this phone"
                    1 -> calendarNames[0]
                    else -> calendarNames[0] + " +${calendarNames.size - 1} more"
                },
                onClick = { showCalendars = true },
            ) {
                ValueLink(if (settings.calendarIds.isEmpty()) "Automatic" else "${settings.calendarIds.size} selected")
            }
        }
        SettingRow(
            "Built-in list",
            if (builtinAvailable) "$country ${yearsText(builtinYears)} · used when the calendar has no data"
            else "Not available for ${if (region.code == null) "this zone" else country}",
            enabled = builtinAvailable,
            onClick = { onSave(settings.copy(useBuiltinHolidays = !settings.useBuiltinHolidays)) },
        ) {
            Switch(
                checked = builtinAvailable && settings.useBuiltinHolidays,
                onCheckedChange = { onSave(settings.copy(useBuiltinHolidays = it)) },
                enabled = builtinAvailable,
            )
        }
    }

    // ---- your own days off
    SectionCard("Your own days off", "Leave, trips, birthdays — each alarm decides whether to skip them") {
        val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        SettingRow(
            "Custom skip days",
            if (customDays.isEmpty()) "None yet"
            else "${customDays.size} saved" + (nextCustom?.let { (c, d) -> " · next: ${c.label.ifBlank { "Custom day" }}, ${d.format(shortDate)}" } ?: ""),
            onClick = { showCustom = true },
        ) {
            ValueLink(if (customDays.isEmpty()) "Add" else "Manage")
        }
    }

    // ---- check a date
    SectionCard("Check a date", "See whether an alarm would skip a day") {
        OutlinedButton(onClick = { showTestPicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pick a date")
        }
        testResult?.let { DateCheckCard(it, city, country) }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Deciding source for ${today.year}: ${sourceLabel(source, country)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        TextButton(onClick = { advanced = !advanced }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Advanced")
            Icon(if (advanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        if (advanced) {
            OutlinedTextField(
                value = names,
                onValueChange = { names = it },
                label = { Text("Extra calendar names") },
                supportingText = { Text("Also use calendars whose name contains any of these. Separate with |") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (names != settings.calendarNames) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { onSave(settings.copy(calendarNames = names.trim())) }) { Text("Save") }
                }
            }
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
                        val h = HolidayChecker.check(ctx, d, fresh = true)
                        val custom = store.customDays().firstOrNull { it.matches(d) }
                        testResult = DateCheck(d, h.name, h.source, custom?.let { it.label.ifBlank { "Custom day" } })
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

/** "2026–2027" for consecutive years, else "2026, 2028". */
private fun yearsText(years: List<Int>): String {
    if (years.isEmpty()) return ""
    val sorted = years.sorted()
    val consecutive = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (sorted.size > 1 && consecutive) "${sorted.first()}–${sorted.last()}" else sorted.joinToString(", ")
}

/** The first custom skip day from [today] on (within a year), with the date it falls on. */
private fun nextCustomDay(days: List<CustomDay>, today: LocalDate): Pair<CustomDay, LocalDate>? {
    if (days.isEmpty()) return null
    for (i in 0..366) {
        val d = today.plusDays(i.toLong())
        days.firstOrNull { it.matches(d) }?.let { return it to d }
    }
    return null
}

/** The answer of the "Check a date" tool. */
private class DateCheck(val date: LocalDate, val holiday: String?, val source: HolidayChecker.Source, val custom: String?)

// ------------------------------------------------------------------ building blocks

/** Two short lines with icons: which time the alarms use, and where the holidays come from. Amber when holidays have no source. */
@Composable
private fun SummaryCard(
    warn: Boolean, zoneTitle: String, zoneSub: String, holidayTitle: String, holidaySub: String,
    action: (() -> Unit)?, actionLabel: String,
) {
    val sc = statusColors()
    val bg = if (warn) sc.warnBg else MaterialTheme.colorScheme.primaryContainer
    val fg = if (warn) sc.warnFg else MaterialTheme.colorScheme.onPrimaryContainer
    val iconTint = if (warn) sc.warnFg else MaterialTheme.colorScheme.primary
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryLine(zoneTitle, zoneSub, fg) { ClockGlyph(iconTint) }
            SummaryLine(holidayTitle, holidaySub, fg) {
                Icon(
                    if (warn) Icons.Default.Warning else Icons.Default.DateRange,
                    contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp),
                )
            }
            if (action != null) Button(onClick = action) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SummaryLine(title: String, sub: String, fg: Color, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = fg)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.8f))
        }
    }
}

/** A simple clock face (the core icon set has none). */
@Composable
private fun ClockGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = 2.dp.toPx()
        val r = size.minDimension / 2f - w
        drawCircle(color, radius = r, style = Stroke(width = w))
        drawLine(color, center, Offset(center.x, center.y - r * 0.55f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, center, Offset(center.x + r * 0.45f, center.y + r * 0.25f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

/** One topic: a bold header, a one-line subtitle, then its rows. */
@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            content()
        }
    }
}

/** A settings row: title and one short grey line on the left, the control on the right; the whole row is tappable. */
@Composable
private fun SettingRow(
    title: String, subtitle: String?, enabled: Boolean = true, onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    val base = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val subColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        (if (onClick != null && enabled) base.clickable(onClick = onClick) else base).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subColor, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing()
    }
}

/** A tappable value at the end of a row: blue text and a › arrow (greyed when the row is off). */
@Composable
private fun ValueLink(text: String, enabled: Boolean = true) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, color = color, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp),
        )
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

/** The "Check a date" answer: the date, a verdict badge (amber holiday, green normal day), the name, and the source. */
@Composable
private fun DateCheckCard(r: DateCheck, city: String, country: String) {
    val sc = statusColors()
    val day = r.date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("$day · $city", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            r.holiday != null -> {
                Pill("Public holiday", sc.warnBg, sc.warnFg)
                Text(r.holiday, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (r.custom != null) Hint("Also your day off: ${r.custom}")
            }
            r.custom != null -> {
                Pill("Your day off", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                Text(r.custom, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Hint("Not a public holiday")
            }
            else -> {
                Pill("Normal day", sc.okBg, sc.okFg)
                Text("Not a public holiday", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Source: " + when (r.source) {
                HolidayChecker.Source.CALENDAR -> "phone calendar"
                HolidayChecker.Source.BUILTIN -> "built-in list ($country)"
                HolidayChecker.Source.NONE -> "no holiday data for ${r.date.year}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
