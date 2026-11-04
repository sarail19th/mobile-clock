@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.personal.clock.core.Fmt
import app.personal.clock.data.Store
import app.personal.clock.data.WidgetConfig
import app.personal.clock.widget.WidgetRenderer
import kotlin.math.roundToInt

/** Per-widget settings. Opened when a widget is placed and again from the launcher's "reconfigure". */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED) // backing out must not add a half-configured widget

        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val store = Store.get(this)
        store.ensureSeeded()

        val awm = AppWidgetManager.getInstance(this)
        val opts = awm.getAppWidgetOptions(widgetId)
        val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: WidgetRenderer.DEFAULT_WIDTH_DP
        val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: WidgetRenderer.DEFAULT_HEIGHT_DP

        val initial = if (store.hasWidgetConfig(widgetId)) {
            store.widgetConfig(widgetId)
        } else {
            // Brand-new widget: start from the saved default, seeding 12/24h from the phone on the very first one.
            store.widgetDefault().let { d ->
                if (store.hasWidgetDefaultSaved()) d else WidgetConfig.default(DateFormat.is24HourFormat(this))
            }
        }

        setContent {
            ClockTheme {
                ConfigScreen(
                    initial = initial, widthDp = wDp, heightDp = hDp,
                    onSave = { cfg ->
                        store.saveWidgetConfig(widgetId, cfg)
                        awm.updateAppWidget(widgetId, WidgetRenderer.build(this, widgetId))
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    },
                    onSaveDefault = { cfg -> store.saveWidgetDefault(cfg) },
                    onCancel = { finish() },
                )
            }
        }
    }
}

private val BG_SWATCHES = listOf(0xFF1B1F2A, 0xFF000000, 0xFFFFFFFF, 0xFF0B3D91, 0xFF00695C, 0xFF4A148C, 0xFFB71C1C, 0xFFFFB300)
private val TEXT_SWATCHES = listOf(0xFFFFFFFF, 0xFF000000, 0xFFD0D4DC, 0xFFFFD54F, 0xFF80DEEA, 0xFFF48FB1, 0xFFA5D6A7, 0xFFFF8A65)

private val SECTIONS = listOf("Text", "Layout", "Background", "Lines")

@Composable
private fun ConfigScreen(
    initial: WidgetConfig,
    widthDp: Int,
    heightDp: Int,
    onSave: (WidgetConfig) -> Unit,
    onSaveDefault: (WidgetConfig) -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableIntStateOf(0) }
    val worldZones = remember { Store.get(ctx).world() }

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel", maxLines = 1) }
                    Button(onClick = { onSave(cfg) }, modifier = Modifier.weight(1f)) { Text("Save", maxLines = 1) }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                WidgetPreview(cfg, widthDp, heightDp)
            }
            // Section switcher: keeps every control within one short scroll.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SECTIONS.forEachIndexed { i, n ->
                    FilterChip(selected = section == i, onClick = { section = i }, label = { Text(n, maxLines = 1) })
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (section) {
                    0 -> {
                        // ------------------------------------------------ text
                        SectionTitle("Font")
                        ChipRow {
                            WidgetFonts.names.forEachIndexed { i, n ->
                                FilterChip(selected = cfg.font == i, onClick = { cfg = cfg.copy(font = i) }, label = { Text(n) })
                            }
                        }
                        SliderRow("Time size: ${cfg.sizeSp} sp", cfg.sizeSp.toFloat(), 24f..120f) { cfg = cfg.copy(sizeSp = it.roundToInt()) }
                        LabeledRow("Text shadow (readability on busy wallpapers)") {
                            Switch(checked = cfg.shadow, onCheckedChange = { cfg = cfg.copy(shadow = it) })
                        }
                        SectionTitle("Colours")
                        ColorRow("Time", cfg.textColor, TEXT_SWATCHES) { cfg = cfg.copy(textColor = it) }
                        ColorRow("Date and other lines", cfg.dateColor, TEXT_SWATCHES) { cfg = cfg.copy(dateColor = it) }
                        SectionTitle("Time format")
                        LabeledRow("24-hour clock") {
                            Switch(checked = cfg.use24h, onCheckedChange = { cfg = cfg.copy(use24h = it, leadingZero = it) })
                        }
                        if (!cfg.use24h) {
                            LabeledRow("Show AM/PM") { Switch(checked = cfg.showAmPm, onCheckedChange = { cfg = cfg.copy(showAmPm = it) }) }
                        }
                        LabeledRow("Leading zero (${if (cfg.use24h) "07:30" else "07:30 vs 7:30"})") {
                            Switch(checked = cfg.leadingZero, onCheckedChange = { cfg = cfg.copy(leadingZero = it) })
                        }
                        Hint("Seconds are never shown on this widget.")
                        SectionTitle("Time zone")
                        ChipRow {
                            listOf("Home zone (Bangkok)", "Phone's zone").forEachIndexed { i, n ->
                                FilterChip(selected = cfg.zoneMode == i, onClick = { cfg = cfg.copy(zoneMode = i) }, label = { Text(n) })
                            }
                        }
                    }
                    1 -> {
                        // ------------------------------------------------ layout
                        SectionTitle("Horizontal alignment")
                        ChipRow {
                            listOf("Left", "Center", "Right").forEachIndexed { i, n ->
                                FilterChip(selected = cfg.hAlign == i, onClick = { cfg = cfg.copy(hAlign = i) }, label = { Text(n) })
                            }
                        }
                        SectionTitle("Vertical alignment")
                        ChipRow {
                            listOf("Top", "Middle", "Bottom").forEachIndexed { i, n ->
                                FilterChip(selected = cfg.vAlign == i, onClick = { cfg = cfg.copy(vAlign = i) }, label = { Text(n) })
                            }
                        }
                        SliderRow("Padding: ${cfg.paddingDp} dp", cfg.paddingDp.toFloat(), 0f..40f) { cfg = cfg.copy(paddingDp = it.roundToInt()) }
                        SectionTitle("When tapped")
                        ChipRow {
                            listOf("Open app", "Open Alarm tab", "Do nothing").forEachIndexed { i, n ->
                                FilterChip(selected = cfg.tapAction == i, onClick = { cfg = cfg.copy(tapAction = i) }, label = { Text(n) })
                            }
                        }
                    }
                    2 -> {
                        // ------------------------------------------------ background
                        SectionTitle("Style")
                        ChipRow {
                            listOf("Solid", "Transparent", "Frosted").forEachIndexed { i, n ->
                                FilterChip(selected = cfg.bgMode == i, onClick = { cfg = cfg.copy(bgMode = i) }, label = { Text(n) })
                            }
                        }
                        when (cfg.bgMode) {
                            WidgetConfig.BG_TRANSPARENT -> Hint("No panel at all: only the text sits on your wallpaper.")
                            WidgetConfig.BG_FROSTED -> Hint("Android widgets cannot blur what is behind them. \"Frosted\" is a tinted, see-through panel with a soft light edge.")
                            else -> Hint("A solid panel. Lower the opacity to make it translucent.")
                        }
                        if (cfg.bgMode != WidgetConfig.BG_TRANSPARENT) {
                            ColorRow("Panel colour", cfg.bgColor, BG_SWATCHES) { cfg = cfg.copy(bgColor = it) }
                            SliderRow("Opacity: ${cfg.bgOpacity}%  (0 = invisible, 100 = solid)", cfg.bgOpacity.toFloat(), 0f..100f) {
                                cfg = cfg.copy(bgOpacity = it.roundToInt())
                            }
                        }
                        SliderRow(
                            "Corner radius: ${cfg.cornerStep * 4} dp", cfg.cornerStep.toFloat(),
                            0f..WidgetConfig.MAX_CORNER_STEP.toFloat(), steps = WidgetConfig.MAX_CORNER_STEP - 1,
                        ) { cfg = cfg.copy(cornerStep = it.roundToInt()) }
                    }
                    else -> {
                        // ------------------------------------------------ extra lines
                        LabeledRow("Date") { Switch(checked = cfg.showDate, onCheckedChange = { cfg = cfg.copy(showDate = it) }) }
                        if (cfg.showDate) {
                            SectionTitle("Date position")
                            ChipRow {
                                listOf("Above the time", "Below the time").forEachIndexed { i, n ->
                                    FilterChip(selected = (i == 0) == cfg.dateAbove, onClick = { cfg = cfg.copy(dateAbove = i == 0) }, label = { Text(n) })
                                }
                            }
                            SectionTitle("Date format")
                            ChipRow {
                                WidgetConfig.DATE_FORMATS.forEachIndexed { i, f ->
                                    val sample = java.time.LocalDate.of(2026, 11, 4)
                                        .format(java.time.format.DateTimeFormatter.ofPattern(f, java.util.Locale.getDefault()))
                                    FilterChip(selected = cfg.dateFormat == i, onClick = { cfg = cfg.copy(dateFormat = i) }, label = { Text(sample) })
                                }
                            }
                        }
                        LabeledRow("Next alarm (from this app)") {
                            Switch(checked = cfg.showNextAlarm, onCheckedChange = { cfg = cfg.copy(showNextAlarm = it) })
                        }
                        LabeledRow("Second clock (world city)") {
                            Switch(
                                checked = cfg.showWorld,
                                onCheckedChange = { on ->
                                    val pick = cfg.worldZone.ifBlank { worldZones.firstOrNull { it != "Asia/Bangkok" } ?: worldZones.firstOrNull().orEmpty() }
                                    cfg = cfg.copy(showWorld = on, worldZone = pick)
                                },
                            )
                        }
                        if (cfg.showWorld) {
                            if (worldZones.size <= 1) Hint("Add cities in the World tab first.")
                            ChipRow {
                                worldZones.forEach { z ->
                                    FilterChip(selected = cfg.worldZone == z, onClick = { cfg = cfg.copy(worldZone = z) }, label = { Text(Fmt.cityName(z)) })
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onSaveDefault(cfg); note = "Saved as the default look for new widgets." },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text("Use this look as default for new widgets") }
                note?.let { Hint(it) }
                Box(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun ColorRow(label: String, current: Int, swatches: List<Long>, onPick: (Int) -> Unit) {
    var hex by remember(current) { mutableStateOf("%06X".format(current and 0xFFFFFF)) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            swatches.forEach { argb ->
                val c = argb.toInt()
                val selected = (c and 0xFFFFFF) == (current and 0xFFFFFF)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onPick(c) },
                )
            }
            OutlinedTextField(
                value = hex,
                onValueChange = { v ->
                    val clean = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                    hex = clean
                    if (clean.length == 6) onPick((0xFF000000 or clean.toLong(16)).toInt())
                },
                label = { Text("Hex") },
                singleLine = true,
                modifier = Modifier.size(width = 120.dp, height = 64.dp),
            )
        }
    }
}
