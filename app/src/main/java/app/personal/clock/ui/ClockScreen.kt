@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.R
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ClockScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val settings = remember { store.settings() }
    val seconds = settings.showSecondsInApp
    val tick by rememberTicker(if (seconds) 1_000L else 60_000L)
    val is24 = DateFormat.is24HourFormat(ctx)

    val zone = settings.zone()
    val nowInstant = Instant.ofEpochMilli(tick)
    val now = nowInstant.atZone(zone)
    val timePattern = (if (is24) "HH:mm" else "h:mm") + (if (seconds) ":ss" else "")
    val time = now.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
    val ampm = if (is24) "" else now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))

    val phoneZone = ZoneId.systemDefault()
    val zoneDiffers = phoneZone.rules.getOffset(nowInstant).totalSeconds != now.offset.totalSeconds
    val next = remember(tick) { AlarmScheduler.nextOverall(ctx) }
    val others = remember { store.world().filter { it != zone.id }.take(2) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Clock", onSettings)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    time + if (ampm.isNotEmpty()) " $ampm" else "",
                    fontSize = if (seconds) 68.sp else 96.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                )
                Text(date, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${Fmt.utcOffset(now.offset.totalSeconds)} · ${Fmt.cityName(zone.id)}",
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (zoneDiffers) {
                    Hint(
                        "Phone is set to ${Fmt.cityName(phoneZone.id)}; this app keeps ${Fmt.cityName(zone.id)} time.",
                        Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (next != null) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_nav_alarm), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Hint("Next alarm · in ${Fmt.until(next.at.toInstant().toEpochMilli() - tick)}")
                            Text(
                                next.at.format(DateTimeFormatter.ofPattern(if (is24) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault())),
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            )
                            if (next.alarm.label.isNotBlank()) Hint(next.alarm.label)
                        }
                    }
                }
            }

            if (others.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    others.forEach { id ->
                        val z = runCatching { ZoneId.of(id) }.getOrNull()
                        if (z != null) {
                            val local = nowInstant.atZone(z)
                            val day = isDaytime(local.hour)
                            Card(
                                Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(Fmt.cityName(id), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                                        Icon(
                                            painterResource(if (day) R.drawable.ic_sun else R.drawable.ic_moon),
                                            contentDescription = if (day) "Day" else "Night",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Text(
                                        local.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())),
                                        fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 4.dp),
                                    )
                                    Hint(Fmt.hoursDiff(local.offset.totalSeconds - now.offset.totalSeconds))
                                }
                            }
                        }
                    }
                }
            } else {
                Hint("Add cities in the World tab to see them here.")
            }
        }
    }
}
