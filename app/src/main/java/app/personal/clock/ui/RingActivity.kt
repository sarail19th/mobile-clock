@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.alarm.RingInfo
import app.personal.clock.alarm.RingService
import app.personal.clock.alarm.RingState
import app.personal.clock.data.Store
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Full-screen "alarm ringing" page, shown over the lock screen. */
class RingActivity : ComponentActivity() {

    private var info by mutableStateOf<RingInfo?>(RingState.current)

    private val listener: (RingInfo?) -> Unit = { next ->
        if (next == null) finish() else info = next
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        if (RingState.current == null) {
            // Nothing is ringing (e.g. stale full-screen intent): don't leave a dead page on the lock screen.
            finish()
            return
        }
        RingState.addListener(listener)
        setContent {
            ClockTheme {
                val current = info
                if (current != null) {
                    RingScreen(
                        current,
                        onSnooze = { RingService.snooze(applicationContext) },
                        onDismiss = { RingService.dismiss(applicationContext) },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        RingState.removeListener(listener)
        super.onDestroy()
    }
}

@Composable
private fun RingScreen(info: RingInfo, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val tick by rememberTicker(1_000L)
    val zone = remember { Store.get(ctx).settings().zone() }
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = Instant.ofEpochMilli(tick).atZone(zone)
        .format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Locale.getDefault()))

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 80.dp)) {
                Text(time, fontSize = 96.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
                Text(
                    info.label,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (info.canSnooze) {
                    FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                        Text("Snooze ${info.snoozeMinutes} min", fontSize = 20.sp)
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text("Dismiss", fontSize = 20.sp)
                }
            }
        }
    }
}
