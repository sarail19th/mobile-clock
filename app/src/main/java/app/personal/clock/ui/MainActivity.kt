@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import app.personal.clock.Tabs
import app.personal.clock.alarm.AlarmScheduler
import app.personal.clock.alarm.Notifications
import app.personal.clock.data.Store

class MainActivity : ComponentActivity() {

    private var tab by mutableIntStateOf(Tabs.CLOCK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        Store.get(this).ensureSeeded()
        // Re-arm everything on every launch (cheap, idempotent) - off the main thread because the first
        // holiday lookup may read the calendar provider.
        val app = applicationContext
        Thread { AlarmScheduler.rescheduleAll(app) }.start()

        tab = savedInstanceState?.getInt(Tabs.EXTRA_TAB) ?: intent?.getIntExtra(Tabs.EXTRA_TAB, Tabs.CLOCK) ?: Tabs.CLOCK
        setContent {
            ClockTheme {
                MainScreen(tab = tab, onTab = { tab = it })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tab = intent.getIntExtra(Tabs.EXTRA_TAB, tab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(Tabs.EXTRA_TAB, tab)
    }
}

private data class TabItem(val label: String, val glyph: String)

private val TAB_ITEMS = listOf(
    TabItem("Clock", "🕐"),
    TabItem("Alarm", "⏰"),
    TabItem("Timer", "⏳"),
    TabItem("Stopwatch", "⏱️"),
    TabItem("World", "🌍"),
    TabItem("Settings", "⚙️"),
)

@Composable
fun MainScreen(tab: Int, onTab: (Int) -> Unit) {
    val ctx = LocalContext.current

    // Ask for notification permission once, at first launch (needed for timers and the ring notification).
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("ui", 0)
        val granted = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TAB_ITEMS.forEachIndexed { i, item ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = { Text(item.glyph, fontSize = 20.sp) },
                        label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tabs.CLOCK -> ClockScreen()
                Tabs.ALARM -> AlarmScreen()
                Tabs.TIMER -> TimerScreen()
                Tabs.STOPWATCH -> StopwatchScreen()
                Tabs.WORLD -> WorldScreen()
                else -> SettingsScreen()
            }
        }
    }
}
