package app.personal.clock.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import app.personal.clock.core.ThemePref
import app.personal.clock.data.Store

/** The app's light / dark choice as observable state, so changing it in Settings recolours every screen at once. */
object ThemeMode {
    private val state = mutableIntStateOf(ThemePref.SYSTEM)

    val mode: Int get() = state.intValue

    /** Reads the saved choice. Called from each activity's onCreate, before its content is composed. */
    fun load(ctx: Context) {
        state.intValue = ThemePref.sanitize(Store.get(ctx).settings().theme)
    }

    fun set(mode: Int) {
        state.intValue = ThemePref.sanitize(mode)
    }
}

/** Material You colours (minSdk 31 guarantees dynamic colour support), light or dark per the app's own setting. */
@Composable
fun ClockTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = ThemePref.isDark(ThemeMode.mode, isSystemInDarkTheme())
    val scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    val activity = remember(ctx) { ctx.findComponentActivity() }
    val surface = scheme.surface.toArgb()
    // Status / navigation bar icons and the window behind Compose follow the app's theme, not the phone's.
    DisposableEffect(activity, dark, surface) {
        activity?.applyBars(dark, surface)
        onDispose { }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Call from onCreate instead of enableEdgeToEdge(): loads the saved choice and sets the bars right from the first frame. */
fun ComponentActivity.setupThemeBars() {
    ThemeMode.load(this)
    val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = ThemePref.isDark(ThemeMode.mode, systemDark)
    applyBars(dark, if (dark) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())
}

private fun ComponentActivity.applyBars(dark: Boolean, windowBackground: Int) {
    val detect: (Resources) -> Boolean = { dark }
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, detect),
        navigationBarStyle = SystemBarStyle.auto(
            android.graphics.Color.argb(0xE6, 0xFF, 0xFF, 0xFF), android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B), detect,
        ),
    )
    window.setBackgroundDrawable(ColorDrawable(windowBackground))
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

/** Fixed status colours (green ok, amber attention, red problem) in a light and a dark variant, for the spots that used one fixed pale colour. */
class StatusColors(
    val okBg: Color, val okFg: Color,
    val warnBg: Color, val warnFg: Color,
    val badBg: Color, val badFg: Color,
    val text: Color, val subtext: Color, val rowBg: Color,
)

@Composable
fun statusColors(): StatusColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        StatusColors(
            okBg = Color(0xFF1E3A2A), okFg = Color(0xFF9BE2B3),
            warnBg = Color(0xFF4A3A12), warnFg = Color(0xFFFFD98A),
            badBg = Color(0xFF4A1F1B), badFg = Color(0xFFFFB4AB),
            text = Color(0xFFEDEDED), subtext = Color(0xFFC8C8C8), rowBg = Color(0xFF2A2A2A),
        )
    } else {
        StatusColors(
            okBg = Color(0xFFE0F2E6), okFg = Color(0xFF17683A),
            warnBg = Color(0xFFFFF1D6), warnFg = Color(0xFF7A4A00),
            badBg = Color(0xFFFBE9E7), badFg = Color(0xFF8C1D18),
            text = Color(0xFF1B1B1B), subtext = Color(0xFF3A3A3A), rowBg = Color.White,
        )
    }
}
