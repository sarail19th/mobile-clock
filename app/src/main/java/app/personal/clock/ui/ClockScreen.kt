@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ClockScreen() {
    val ctx = LocalContext.current
    val settings = remember { Store.get(ctx).settings() }
    val seconds = settings.showSecondsInApp
    val tick by rememberTicker(if (seconds) 1_000L else 60_000L)
    val is24 = DateFormat.is24HourFormat(ctx)

    val zone = settings.zone()
    val now = Instant.ofEpochMilli(tick).atZone(zone)
    val timePattern = (if (is24) "HH:mm" else "h:mm") + (if (seconds) ":ss" else "")
    val time = now.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
    val ampm = if (is24) "" else now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))
    val offset = Fmt.utcOffset(now.offset.totalSeconds)

    val phoneZone = ZoneId.systemDefault()
    val phoneOffset = Instant.ofEpochMilli(tick).atZone(phoneZone).offset.totalSeconds
    val zoneDiffers = phoneZone.rules.getOffset(Instant.ofEpochMilli(tick)).totalSeconds != now.offset.totalSeconds

    val next = remember(tick) { AlarmScheduler.nextOverall(ctx) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            time,
            fontSize = if (seconds) 68.sp else 92.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
        if (ampm.isNotEmpty()) Text(ampm, style = MaterialTheme.typography.headlineSmall)
        Text(date, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            "$offset · ${zone.id}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (zoneDiffers) {
            Hint(
                "Phone is set to ${Fmt.cityName(phoneZone.id)} (${Fmt.utcOffset(phoneOffset)}); this app keeps ${Fmt.cityName(zone.id)} time.",
                Modifier.padding(top = 8.dp),
            )
        }
        if (next != null) {
            Card(Modifier.padding(top = 32.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Next alarm", style = MaterialTheme.typography.labelMedium)
                    val untilMs = next.at.toInstant().toEpochMilli() - tick
                    Text(
                        next.at.format(DateTimeFormatter.ofPattern(if (is24) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault())) +
                            "  ·  in ${Fmt.until(untilMs)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (next.alarm.label.isNotBlank()) Hint(next.alarm.label)
                }
            }
        }
    }
}
