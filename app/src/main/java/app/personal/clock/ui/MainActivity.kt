@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.personal.clock.R
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

private data class TabItem(val label: String, val icon: Int)

// Five tabs; Settings is the gear in each screen's header (it frees room so labels never truncate).
private val TAB_ITEMS = listOf(
    TabItem("Clock", R.drawable.ic_nav_clock),
    TabItem("Alarm", R.drawable.ic_nav_alarm),
    TabItem("Timer", R.drawable.ic_nav_timer),
    TabItem("Stopwatch", R.drawable.ic_nav_stopwatch),
    TabItem("World", R.drawable.ic_nav_world),
)

@Composable
fun MainScreen(tab: Int, onTab: (Int) -> Unit) {
    val ctx = LocalContext.current
    val labelSize = with(LocalDensity.current) { 11.dp.toSp() }

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

    BackHandler(enabled = tab == Tabs.SETTINGS) { onTab(Tabs.CLOCK) }

    if (tab == Tabs.SETTINGS) {
        Scaffold { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) { SettingsScreen(onBack = { onTab(Tabs.CLOCK) }) }
        }
    } else {
        val openSettings = { onTab(Tabs.SETTINGS) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    TAB_ITEMS.forEachIndexed { i, item ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { onTab(i) },
                            icon = { Icon(painterResource(item.icon), contentDescription = item.label) },
                            // Fixed size (not scaled by the system font size) so every label always fits on one line.
                            label = { Text(item.label, fontSize = labelSize, maxLines = 1, softWrap = false) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tabs.CLOCK -> ClockScreen(openSettings)
                    Tabs.ALARM -> AlarmScreen(openSettings)
                    Tabs.TIMER -> TimerScreen(openSettings)
                    Tabs.STOPWATCH -> StopwatchScreen(openSettings)
                    else -> WorldScreen(openSettings)
                }
            }
        }
    }
}
