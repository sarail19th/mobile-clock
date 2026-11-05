@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.R
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import app.personal.clock.data.StopwatchState

@Composable
fun StopwatchScreen(onSettings: () -> Unit) {
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
    val lastLapTotal = sw.laps.lastOrNull() ?: 0L
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val arc = MaterialTheme.colorScheme.primary
    val frame = MaterialTheme.colorScheme.surfaceContainer

    // Lap deltas, oldest first, to mark the best and slowest lap.
    val deltas = sw.laps.mapIndexed { i, total -> total - (if (i == 0) 0L else sw.laps[i - 1]) }
    val best = if (deltas.size >= 2) deltas.minOrNull() else null
    val slowest = if (deltas.size >= 2) deltas.maxOrNull() else null

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Stopwatch", onSettings)
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2 + 6.dp.toPx()
                    drawCircle(color = frame, radius = size.minDimension / 2)
                    drawCircle(color = track, radius = size.minDimension / 2 - inset, style = Stroke(width = stroke))
                    val sweep = ((elapsed % 60_000L) / 60_000f) * 360f
                    drawArc(
                        color = arc, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                        topLeft = Offset(inset, inset), size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.stopwatch(elapsed), fontSize = 44.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
                    if (sw.laps.isNotEmpty()) Hint("Lap ${sw.laps.size + 1} · ${Fmt.stopwatch(elapsed - lastLapTotal)}")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (sw.running) {
                    RoundAction(R.drawable.ic_flag, "Lap", filled = false) { update(sw.lap()) }
                    RoundAction(R.drawable.ic_stop, "Stop", filled = true, danger = true) { update(sw.stop()) }
                } else {
                    RoundAction(R.drawable.ic_nav_stopwatch, "Reset", filled = false, enabled = sw.elapsed() > 0 || sw.laps.isNotEmpty()) { update(sw.reset()) }
                    RoundAction(R.drawable.ic_play, if (sw.elapsed() > 0) "Resume" else "Start", filled = true) { update(sw.start()) }
                }
            }

            if (sw.laps.isNotEmpty()) {
                SectionTitle("Laps", Modifier.align(Alignment.Start))
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(sw.laps.reversed()) { idx, total ->
                        val n = sw.laps.size - idx
                        val delta = deltas[n - 1]
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$n", Modifier.size(width = 40.dp, height = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("+${Fmt.stopwatch(delta)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            if (delta == best) Pill("Best", Color(0xFFE0F2E6), Color(0xFF17683A), Modifier.padding(start = 8.dp))
                            if (delta == slowest && delta != best) Pill("Slowest", Color(0xFFFBE9E7), Color(0xFF8C1D18), Modifier.padding(start = 8.dp))
                            Box(Modifier.weight(1f))
                            Text(Fmt.stopwatch(total), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: Int, label: String, filled: Boolean, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val container = when {
        !filled -> MaterialTheme.colorScheme.surface
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val content = when {
        !filled -> MaterialTheme.colorScheme.onSurface
        danger -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(container)
                .let { if (!filled) it.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else it }
                .let { if (enabled) it.clickable(onClick = onClick) else it },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = label, tint = if (enabled) content else content.copy(alpha = 0.4f))
        }
        Hint(label)
    }
}
