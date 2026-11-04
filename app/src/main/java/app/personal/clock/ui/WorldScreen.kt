@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val REGIONS = listOf(
    "Africa/", "America/", "Antarctica/", "Arctic/", "Asia/", "Atlantic/",
    "Australia/", "Europe/", "Indian/", "Pacific/",
)

/** Selectable zones: real "Region/City" ids only (hides legacy aliases like US/Pacific, Etc/GMT+5, SystemV/...). */
fun selectableZones(): List<String> =
    ZoneId.getAvailableZoneIds().filter { id -> REGIONS.any { id.startsWith(it) } }.sorted()

@Composable
fun WorldScreen() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val is24 = DateFormat.is24HourFormat(ctx)
    var zones by remember { mutableStateOf(store.world()) }
    var adding by remember { mutableStateOf(false) }
    val tick by rememberTicker(60_000L)
    val settings = remember { store.settings() }
    val homeZone = settings.zone()
    val now = Instant.ofEpochMilli(tick)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(zones, key = { it }) { id ->
                val zone = runCatching { ZoneId.of(id) }.getOrNull()
                if (zone != null) {
                    val zdt = now.atZone(zone)
                    val offsetSec = zdt.offset.totalSeconds
                    val dayDiff = ChronoUnit.DAYS.between(now.atZone(homeZone).toLocalDate(), zdt.toLocalDate())
                    val dayText = when (dayDiff) {
                        0L -> "Today"
                        1L -> "Tomorrow"
                        -1L -> "Yesterday"
                        else -> "${if (dayDiff > 0) "+" else ""}$dayDiff days"
                    }
                    val dst = zone.rules.isDaylightSavings(now)
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.cityName(id), style = MaterialTheme.typography.titleMedium)
                                Hint(
                                    "${Fmt.utcOffset(offsetSec)} · $dayText" + (if (dst) " · DST" else "") +
                                        (if (id == homeZone.id) " · home" else ""),
                                )
                                Hint(id)
                            }
                            Text(
                                zdt.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Light,
                            )
                            IconButton(onClick = {
                                zones = zones.filterNot { it == id }
                                store.saveWorld(zones)
                            }) { Icon(Icons.Default.Delete, contentDescription = "Remove ${Fmt.cityName(id)}") }
                        }
                    }
                }
            }
            item {
                Hint("Offsets are computed from the phone's built-in time-zone database, so daylight saving is handled automatically.")
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Add city") }
    }

    if (adding) {
        AddZoneDialog(
            existing = zones,
            now = now,
            onDismiss = { adding = false },
            onPick = { id ->
                zones = (zones + id).distinct()
                store.saveWorld(zones)
                adding = false
            },
        )
    }
}

@Composable
private fun AddZoneDialog(existing: List<String>, now: Instant, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val all = remember { selectableZones() }
    var query by remember { mutableStateOf("") }
    val matches = remember(query) {
        val q = query.trim().lowercase().replace(' ', '_')
        val list = if (q.isEmpty()) all else all.filter { id ->
            id.lowercase().contains(q) ||
                Fmt.utcOffset(ZoneId.of(id).rules.getOffset(now).totalSeconds).lowercase().replace(" ", "") == q.replace("_", "")
        }
        list.filterNot { it in existing }.take(80)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add city") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Search city, region or UTC+9") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)) {
                    items(matches, key = { it }) { id ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp)) {
                            Text(Fmt.cityName(id), style = MaterialTheme.typography.bodyLarge)
                            Hint("$id · ${Fmt.utcOffset(ZoneId.of(id).rules.getOffset(now).totalSeconds)}")
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
