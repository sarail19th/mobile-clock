@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.alarm.Notifications
import app.personal.clock.alarm.RingService
import app.personal.clock.core.Fmt
import app.personal.clock.data.AppSettings
import app.personal.clock.data.Backup
import app.personal.clock.data.Store
import app.personal.clock.holiday.HolidayChecker
import app.personal.clock.sys
import app.personal.clock.widget.ClockWidgetProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Health(
    val title: String,
    val ok: Boolean,
    val optional: Boolean,
    val detail: String,
    val action: String,
    val onFix: () -> Unit,
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var settings by remember { mutableStateOf(store.settings()) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(-1) } // -1 = the short list; 0.. = one sub-page
    BackHandler(enabled = page >= 0) { page = -1 }
    OnResume { refresh++ }

    fun save(s: AppSettings) {
        settings = s
        store.saveSettings(s)
        HolidayChecker.clearCache()
        val app = ctx.applicationContext
        Thread { AlarmScheduler.rescheduleAll(app) }.start()
        refresh++
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        HolidayChecker.clearCache()
        refresh++
    }
    // Recomputed whenever `refresh` changes, i.e. after returning from a system settings page.
    val healthList = remember(refresh) { healthItems(ctx, onRequest = { permLauncher.launch(it) }) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            message = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(ctx).toByteArray(Charsets.UTF_8)) }
                "Backup saved."
            }.getOrElse { "Could not save the backup: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            message = runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                val err = Backup.restore(ctx, text)
                if (err == null) {
                    settings = store.settings()
                    HolidayChecker.clearCache()
                    val app = ctx.applicationContext
                    Thread { AlarmScheduler.rescheduleAll(app) }.start()
                    refresh++
                    "Backup restored. Existing widgets keep their own look; new ones use the restored default."
                } else err
            }.getOrElse { "Could not read the backup: ${it.message}" }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalIconButton(onClick = { if (page >= 0) page = -1 else onBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text(
                if (page >= 0) PAGE_TITLES[page] else "Settings",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            )
        }
        if (page < 0) HealthSummary(healthList)
        message?.let {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { message = null }) { Text("OK") }
                }
            }
        }

        if (page < 0) {
            val failing = healthList.count { !it.ok && !it.optional }
            SettingsRow(PAGE_TITLES[0], "Home time zone: ${settings.zone().id}") { page = 0 }
            SettingsRow(PAGE_TITLES[1], if (settings.showSecondsInApp) "Seconds shown in the app" else "Seconds hidden") { page = 1 }
            SettingsRow(PAGE_TITLES[2], "Snooze ${settings.defaultSnoozeMinutes} min \u00B7 auto-stop ${settings.autoStopMinutes} min") { page = 2 }
            SettingsRow(PAGE_TITLES[3], if (failing == 0) "All set" else "$failing to fix") { page = 3 }
            SettingsRow(PAGE_TITLES[4], "Add a widget and how it works") { page = 4 }
            SettingsRow(PAGE_TITLES[5], "Export or restore your data") { page = 5 }
            SettingsRow(PAGE_TITLES[6], "Version and licences") { page = 6 }
        }

        if (page == 0) SettingsCard(null) {
            TimeZoneAndHolidays(settings, refresh, onSave = ::save, onGrant = { permLauncher.launch(Manifest.permission.READ_CALENDAR) })
        }

        if (page == 1) SettingsCard(null) {
            LabeledRow("Show seconds in the app's Clock tab") {
                Switch(checked = settings.showSecondsInApp, onCheckedChange = { save(settings.copy(showSecondsInApp = it)) })
            }
            Hint("The home-screen widget never shows seconds.")
        }

        if (page == 2) SettingsCard(null) {
            Stepper("Default snooze for new alarms", settings.defaultSnoozeMinutes, "min", 1, 60) {
                save(settings.copy(defaultSnoozeMinutes = it))
            }
            Stepper("Stop ringing automatically after", settings.autoStopMinutes, "min", 1, 60) {
                save(settings.copy(autoStopMinutes = it))
            }
            LabeledRow("Notify when an alarm is skipped") {
                Switch(checked = settings.notifyOnSkip, onCheckedChange = { save(settings.copy(notifyOnSkip = it)) })
            }
            OutlinedButton(onClick = { RingService.start(ctx, RingService.KIND_ALARM, -1) }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Test ringing now")
            }
            Hint("Rings with the alarm volume, so check that volume too. Dismiss it from the ring screen.")
        }

        if (page == 3) SettingsCard(null) {
            val items = healthList
            items.forEachIndexed { i, h ->
                HealthRow(h)
                if (i < items.lastIndex) HorizontalDivider()
            }
            Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Samsung / One UI tips", style = MaterialTheme.typography.titleSmall)
                    Hint("• Settings > Apps > Clock > Battery > Unrestricted, and remove the app from \"Sleeping apps\" / \"Deep sleeping apps\".")
                    Hint("• Never use Force stop on this app: Android cancels every alarm until the app is opened again.")
                    Hint("• Do Not Disturb and Modes/Routines can silence alarms. Test with DND on before relying on it.")
                    Hint("• Before trusting a real wake-up, test with the screen off for 15+ minutes and after a reboot.")
                }
            }
        }

        if (page == 4) SettingsCard(null) {
            Hint("Add \"Clock (no seconds)\" from your launcher's widget picker. Each widget has its own look; long-press a placed widget and choose Settings/Reconfigure to change it. In the editor, the \u22EF menu under the preview has \"Use this look for new widgets\".")
            val awm = remember { AppWidgetManager.getInstance(ctx) }
            if (awm.isRequestPinAppWidgetSupported) {
                Button(onClick = { awm.requestPinAppWidget(ComponentName(ctx, ClockWidgetProvider::class.java), null, null) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Add widget to home screen")
                }
            }
        }

        if (page == 5) SettingsCard(null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("personal-clock-backup.json") }, modifier = Modifier.weight(1f)) {
                    Text("Export", maxLines = 1)
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Text("Restore", maxLines = 1)
                }
            }
            Hint("Saves alarms, world clocks, settings and the default widget look as a JSON file you choose where to keep.")
        }

        if (page == 6) SettingsCard(null) {
            @Suppress("DEPRECATION")
            val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
            Hint("Version $version · fully offline: this app has no network permission, account or analytics.")
            Hint("Widget fonts: Clean (DM Sans), Rounded (Nunito), Cute (Fredoka), Soft (Quicksand), Mono (JetBrains Mono), Serif (Playfair Display), Condensed (Barlow Condensed), Thin (Poppins), all under the SIL Open Font License 1.1 (see /licenses in the source). \"System\" uses your phone's own font.")
        }
        Column(Modifier.padding(bottom = 24.dp)) {}
    }
}

@Composable
private fun HealthSummary(items: List<Health>) {
    val failing = items.filter { !it.ok && !it.optional }
    val okCount = items.count { it.ok }
    val good = failing.isEmpty()
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (good) Color(0xFFE0F2E6) else Color(0xFFFFF1D6)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (good) Icons.Default.Check else Icons.Default.Warning, contentDescription = null,
                    tint = if (good) Color(0xFF17683A) else Color(0xFF7A4A00),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (good) "All set: alarms should ring" else "Alarms may not ring: fix ${failing.size}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B),
                    )
                    Text("$okCount of ${items.size} checks are OK", fontSize = 13.sp, color = Color(0xFF3A3A3A))
                }
            }
            failing.take(3).forEach { h ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(Color.White).padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(h.title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1B1B1B))
                    OutlinedButton(onClick = h.onFix) { Text(h.action) }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String?, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

private val PAGE_TITLES = listOf(
    "Time zone & holidays", "Clock display", "Alarm behaviour", "Permission health", "Widget", "Backup", "About",
)

/** One row of the short Settings list: title, one-line summary, and an arrow. */
@Composable
private fun SettingsRow(title: String, summary: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 10.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Hint(summary)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, unit: String, min: Int, max: Int, onChange: (Int) -> Unit) {
    LabeledRow("$label: $value $unit") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−") }
            OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
        }
    }
}

// ------------------------------------------------------------------ permission health

@Composable
private fun HealthRow(h: Health) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val (icon, color) = when {
            h.ok -> Icons.Default.Check to Color(0xFF2E9E5B)
            h.optional -> Icons.Default.Warning to Color(0xFFE0A100)
            else -> Icons.Default.Close to Color(0xFFD64545)
        }
        Icon(icon, contentDescription = if (h.ok) "OK" else if (h.optional) "Optional" else "Needs attention", tint = color, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title + if (h.optional) " (optional)" else "", style = MaterialTheme.typography.bodyLarge)
            Hint(h.detail)
        }
        if (!h.ok) OutlinedButton(onClick = h.onFix, modifier = Modifier.padding(start = 8.dp)) { Text(h.action) }
    }
}

private fun open(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun healthItems(ctx: Context, onRequest: (String) -> Unit): List<Health> {
    val pkg = Uri.parse("package:${ctx.packageName}")
    val nm = ctx.sys<NotificationManager>()
    val pm = ctx.sys<PowerManager>()
    val list = ArrayList<Health>()

    list += Health(
        "Exact alarms", AlarmScheduler.canScheduleExact(ctx), false,
        "Lets alarms and timers fire at the exact minute, even in Doze.", "Fix",
    ) { open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)) }

    val notifGranted = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val channelOk = (nm.getNotificationChannel(Notifications.CH_RING)?.importance ?: NotificationManager.IMPORTANCE_HIGH) != NotificationManager.IMPORTANCE_NONE
    list += Health(
        "Notifications", notifGranted && nm.areNotificationsEnabled() && channelOk, false,
        "Needed for the ringing screen, timer countdowns and \"skipped\" notices.", "Fix",
    ) {
        if (!notifGranted) onRequest(Manifest.permission.POST_NOTIFICATIONS)
        else open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
    }

    val fsi = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
    list += Health(
        "Full-screen alarm over the lock screen", fsi, false,
        "Without it the alarm only shows as a notification when the phone is locked.", "Fix",
    ) {
        if (Build.VERSION.SDK_INT >= 34) open(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
    }

    list += Health(
        "Battery: Unrestricted", pm.isIgnoringBatteryOptimizations(ctx.packageName), false,
        "Samsung otherwise puts the app to sleep and can delay or kill alarms.", "Fix",
    ) {
        open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
    }

    list += Health(
        "Calendar access", HolidayChecker.hasCalendarPermission(ctx), true,
        "Lets the holiday check read the holiday calendars already synced on this phone (read-only). Without it only the built-in list and your own days are used.", "Allow",
    ) { onRequest(Manifest.permission.READ_CALENDAR) }

    list += Health(
        "Music files access", ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED, true,
        "Only needed to ring with a song from your own files. Built-in alarm sounds work without it.", "Allow",
    ) { onRequest(Manifest.permission.READ_MEDIA_AUDIO) }

    return list
}
