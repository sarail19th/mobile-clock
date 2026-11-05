@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import app.personal.clock.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Current time in epoch millis, refreshed on [periodMs] boundaries while the screen is visible
 * (and not at all while the app is in the background).
 */
@Composable
fun rememberTicker(periodMs: Long): State<Long> {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(periodMs, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                state.longValue = now
                delay((periodMs - now % periodMs).coerceAtLeast(5L))
            }
        }
    }
    return state
}

/** Runs [block] every time the screen comes back to the foreground (e.g. after a Settings page). */
@Composable
fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) block() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun LabeledRow(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun Stack(modifier: Modifier = Modifier, gap: Int = 8, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.dp)) { content() }
}

// ---------------------------------------------------------------- formatting

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "Mon", "Tue–Fri", "Mon–Fri", "Every day", "Mon, Wed, Sat", "Once". */
fun daysSummary(mask: Int): String {
    if (mask == 0) return "Once"
    if (mask and 0x7F == 0x7F) return "Every day"
    val on = (0..6).filter { mask and (1 shl it) != 0 }
    val runs = ArrayList<List<Int>>()
    for (d in on) {
        if (runs.isNotEmpty() && runs.last().last() == d - 1) runs[runs.size - 1] = runs.last() + d else runs += listOf(d)
    }
    return runs.joinToString(", ") { r ->
        when {
            r.size >= 3 -> "${DAY_NAMES[r.first()]}–${DAY_NAMES[r.last()]}"
            else -> r.joinToString(", ") { DAY_NAMES[it] }
        }
    }
}

fun formatHm(hour: Int, minute: Int, is24: Boolean): String {
    val mm = minute.toString().padStart(2, '0')
    return if (is24) "${hour.toString().padStart(2, '0')}:$mm"
    else "${if (hour % 12 == 0) 12 else hour % 12}:$mm ${if (hour < 12) "AM" else "PM"}"
}

/** Title of an alarm's sound: null = default alarm tone, "" = silent. */
fun ringtoneTitle(ctx: android.content.Context, uri: String?): String = when {
    uri == null -> "Default alarm sound"
    uri.isEmpty() -> "Silent (vibrate only)"
    else -> runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(uri)).getTitle(ctx) }.getOrDefault("Custom sound")
}

// ---------------------------------------------------------------- shared building blocks

/** Title row used by every tab: big title, settings gear on the right. */
@Composable
fun ScreenHeader(title: String, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        FilledTonalIconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = "Settings")
        }
    }
}

/** A small rounded label. */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(container).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Seven small day dots (Mon..Sun): filled = the alarm repeats that day. */
@Composable
fun DayDots(mask: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, ch ->
            val on = mask and (1 shl i) != 0
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ch, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** True during local daytime (06:00-18:00): used for the sun/moon icon and the day/night strip. */
fun isDaytime(hour: Int): Boolean = hour in 6..17
