@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import app.personal.clock.data.StopwatchState

@Composable
fun StopwatchScreen() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var sw by remember { mutableStateOf(StopwatchState.load(store)) }
    val tick by rememberTicker(if (sw.running) 33L else 1_000L)

    fun update(next: StopwatchState) {
        sw = next
        StopwatchState.save(store, next)
    }

    // tick is read so the readout recomposes while running; the value itself comes from elapsedRealtime.
    val elapsed = remember(tick, sw) { sw.elapsed() }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text(
                Fmt.stopwatch(elapsed),
                fontSize = 60.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sw.running) {
                Button(onClick = { update(sw.stop()) }) { Text("Stop") }
                OutlinedButton(onClick = { update(sw.lap()) }) { Text("Lap") }
            } else {
                Button(onClick = { update(sw.start()) }) { Text(if (sw.elapsed() > 0) "Resume" else "Start") }
                OutlinedButton(onClick = { update(sw.reset()) }, enabled = sw.elapsed() > 0 || sw.laps.isNotEmpty()) { Text("Reset") }
            }
        }
        if (sw.laps.isNotEmpty()) {
            Text("Laps", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                // newest first
                itemsIndexed(sw.laps.reversed()) { idx, total ->
                    val n = sw.laps.size - idx
                    val prev = if (n >= 2) sw.laps[n - 2] else 0L
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lap $n", style = MaterialTheme.typography.bodyLarge)
                        Text("+${Fmt.stopwatch(total - prev)}", fontFamily = FontFamily.Monospace)
                        Text(Fmt.stopwatch(total), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
