@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Country names for time zones, from the phone's own ICU data (so "France" finds Europe/Paris). */
object ZoneCountries {
    class Info(val name: String, val code: String)

    private val byZone: Map<String, Info> by lazy {
        val m = HashMap<String, Info>()
        for (code in Locale.getISOCountries()) {
            val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
            val ids = runCatching { android.icu.util.TimeZone.getAvailableIDs(code) }.getOrNull() ?: continue
            for (id in ids) if (!m.containsKey(id)) m[id] = Info(name.ifBlank { code }, code)
        }
        m
    }

    /** Common short names people actually type. */
    private val aliases = mapOf(
        "usa" to "us", "america" to "us", "uk" to "gb", "britain" to "gb", "england" to "gb",
        "uae" to "ae", "emirates" to "ae", "korea" to "kr", "russia" to "ru", "holland" to "nl",
    )

    fun of(zoneId: String): Info? = byZone[zoneId]

    fun matches(zoneId: String, query: String): Boolean {
        val c = byZone[zoneId] ?: return false
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val alias = aliases[q]
        return c.name.lowercase().contains(q) || c.code.lowercase() == q || (alias != null && c.code.lowercase() == alias)
    }
}

private val NIGHT = Color(0xFF26304F)
private val DAY = Color(0xFFF3C969)

@Composable
fun WorldScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val is24 = DateFormat.is24HourFormat(ctx)
    var zones by remember { mutableStateOf(store.world()) }
    var adding by remember { mutableStateOf(false) }
    val tick by rememberTicker(60_000L)
    val settings = remember { store.settings() }
    val homeZone = settings.zone()
    val now = Instant.ofEpochMilli(tick)
    val homeNow = now.atZone(homeZone)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("World", onSettings)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(zones, key = { it }) { id ->
                    val zone = runCatching { ZoneId.of(id) }.getOrNull()
                    if (zone != null) {
                        val zdt = now.atZone(zone)
                        val dayDiff = ChronoUnit.DAYS.between(homeNow.toLocalDate(), zdt.toLocalDate())
                        val dayText = when (dayDiff) {
                            0L -> "Today"
                            1L -> "Tomorrow"
                            -1L -> "Yesterday"
                            else -> "${if (dayDiff > 0) "+" else ""}$dayDiff days"
                        }
                        val isHome = id == homeZone.id
                        val dst = zone.rules.isDaylightSavings(now)
                        val frac = (zdt.hour * 60 + zdt.minute) / 1440f
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isHome) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 14.dp, end = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(Fmt.cityName(id), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            if (isHome) Pill("Home", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, Modifier.padding(start = 8.dp))
                                            if (dst) Pill("DST", Color(0xFFFFF1D6), Color(0xFF7A4A00), Modifier.padding(start = 6.dp))
                                        }
                                        Hint(
                                            (ZoneCountries.of(id)?.let { it.name + " \u00B7 " } ?: "") + "${Fmt.utcOffset(zdt.offset.totalSeconds)} · $dayText" +
                                                if (isHome) "" else " · ${Fmt.hoursDiff(zdt.offset.totalSeconds - homeNow.offset.totalSeconds)}",
                                        )
                                    }
                                    Text(
                                        zdt.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())),
                                        fontSize = 32.sp, fontWeight = FontWeight.Light,
                                    )
                                    IconButton(onClick = {
                                        zones = zones.filterNot { it == id }
                                        store.saveWorld(zones)
                                    }) { Icon(Icons.Default.Delete, contentDescription = "Remove ${Fmt.cityName(id)}") }
                                }
                                // 24-hour strip in the city's own time: dark = night, gold = daytime, bar = now.
                                Box(Modifier.padding(top = 10.dp, end = 12.dp).fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                                    Box(
                                        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(
                                            Brush.horizontalGradient(
                                                0f to NIGHT, 0.25f to NIGHT, 0.30f to DAY, 0.70f to DAY, 0.75f to NIGHT, 1f to NIGHT,
                                            ),
                                        ),
                                    )
                                    Row(Modifier.fillMaxWidth()) {
                                        Spacer(Modifier.weight(frac.coerceIn(0.001f, 0.999f)))
                                        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface))
                                        Spacer(Modifier.weight((1f - frac).coerceIn(0.001f, 0.999f)))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Hint("Offsets come from the phone's built-in time-zone database, so daylight saving is handled automatically.")
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            text = { Text("Add city") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
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
                ZoneCountries.matches(id, query) ||
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
                    label = { Text("Search city, country or UTC+9") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)) {
                    items(matches, key = { it }) { id ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp)) {
                            Text(Fmt.cityName(id), style = MaterialTheme.typography.bodyLarge)
                            Hint((ZoneCountries.of(id)?.let { it.name + " \u00B7 " } ?: "") + "$id \u00B7 ${Fmt.utcOffset(ZoneId.of(id).rules.getOffset(now).totalSeconds)}")
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
