@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import app.personal.clock.widget.LayoutCaps
import app.personal.clock.widget.WidgetTemplates
import app.personal.clock.widget.WidgetTypefaces
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
import app.personal.clock.core.WorldEntry
import app.personal.clock.data.Store
import app.personal.clock.data.WidgetConfig
import app.personal.clock.widget.WidgetRenderer
import app.personal.clock.widget.WidgetTicker
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
        val (wDp, hDp) = WidgetRenderer.primarySizeDp(this, widgetId)

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
                    initial = initial, widthDp = wDp, heightDp = hDp, startOnTemplates = !store.hasWidgetConfig(widgetId),
                    onSave = { cfg ->
                        store.saveWidgetConfig(widgetId, cfg)
                        awm.updateAppWidget(widgetId, WidgetRenderer.build(this, widgetId))
                        WidgetTicker.schedule(this) // starts/stops the minute refresh used by custom-font widgets
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
private val ACCENT_SWATCHES = listOf(0xFF9FC2FF, 0xFFFFD54F, 0xFFFF8A65, 0xFFF48FB1, 0xFFA5D6A7, 0xFF80DEEA, 0xFFFFFFFF, 0xFF000000)
private val TEXT_SWATCHES = listOf(0xFFFFFFFF, 0xFF000000, 0xFFD0D4DC, 0xFFFFD54F, 0xFF80DEEA, 0xFFF48FB1, 0xFFA5D6A7, 0xFFFF8A65)

private val SECTIONS = listOf("Template", "Text", "Layout", "Background", "Lines")

@Composable
private fun ConfigScreen(
    initial: WidgetConfig,
    widthDp: Int,
    heightDp: Int,
    startOnTemplates: Boolean,
    onSave: (WidgetConfig) -> Unit,
    onSaveDefault: (WidgetConfig) -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableIntStateOf(if (startOnTemplates) 0 else 1) }
    val store = remember { Store.get(ctx) }
    val worldZones = remember { store.world() }
    val homeId = remember { store.settings().zone().id }
    val worldFallback = remember { worldZones.firstOrNull { it != homeId } ?: "Asia/Tokyo" }
    val custom = cfg.font != 0 || cfg.layout != WidgetConfig.LAYOUT_STACK
    val caps = LayoutCaps.of(cfg.layout)

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
                        // ------------------------------------------------ templates
                        Hint("Pick a starting point; you can still change everything. The shape follows the size you give the widget on the home screen.")
                        val current = WidgetTemplates.guess(cfg)
                        WidgetTemplates.all.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { t ->
                                    val preview = WidgetTemplates.apply(cfg, t.id, worldFallback)
                                    val selected = current == t.id
                                    Card(
                                        onClick = { cfg = WidgetTemplates.apply(cfg, t.id, worldFallback) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        ),
                                        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    ) {
                                        Column(Modifier.padding(8.dp)) {
                                            WidgetThumb(preview, t.nominalW, t.nominalH, 104.dp)
                                            Text(t.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                                            Hint("${t.sizeLabel} \u00B7 ${t.blurb}", Modifier.padding(start = 4.dp, bottom = 4.dp))
                                        }
                                    }
                                }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                    1 -> {
                        // ------------------------------------------------ text
                        SectionTitle("Font")
                        ChipRow {
                            WidgetTypefaces.names.forEachIndexed { i, n ->
                                FilterChip(selected = cfg.font == i, onClick = { cfg = cfg.copy(font = i) }, label = { Text(n) })
                            }
                        }
                        if (custom) {
                            Hint("This look is drawn as a picture that is refreshed every minute while the phone is awake. The Classic template with the System font is a live clock driven by Android itself, the most reliable choice.")
                        }
                        LabeledRow("Text shadow (readability on busy wallpapers)") {
                            Switch(checked = cfg.shadow, onCheckedChange = { cfg = cfg.copy(shadow = it) })
                        }
                        if (cfg.shadow) {
                            ChipRow {
                                listOf(
                                    Triple("Soft glow", 38, 9),
                                    Triple("Normal", WidgetConfig.DEFAULT_SHADOW_STRENGTH, WidgetConfig.DEFAULT_SHADOW_BLUR),
                                    Triple("Strong", 90, 3),
                                ).forEach { (name, strength, blur) ->
                                    FilterChip(
                                        selected = cfg.shadowStrength == strength && cfg.shadowBlur == blur,
                                        onClick = { cfg = cfg.copy(shadowStrength = strength, shadowBlur = blur) },
                                        label = { Text(name) },
                                    )
                                }
                            }
                            SliderRow("Shadow darkness: ${cfg.shadowStrength}%", cfg.shadowStrength.toFloat(), 0f..100f) { cfg = cfg.copy(shadowStrength = it.roundToInt()) }
                            SliderRow("Shadow softness: ${cfg.shadowBlur} dp", cfg.shadowBlur.toFloat(), 0f..20f) { cfg = cfg.copy(shadowBlur = it.roundToInt()) }
                            Hint("\"Soft glow\" gives the faint, wide shadow used by many health and weather widgets. Changing the shadow draws the widget as a picture.")
                        }
                        if (caps.accent) {
                            SectionTitle("Accent colour")
                            ColorRow("Minute hand", cfg.accentColor, ACCENT_SWATCHES) { cfg = cfg.copy(accentColor = it) }
                        }
                        SectionTitle("Time zone")
                        ChipRow {
                            listOf("Home zone (Bangkok)", "Phone's zone").forEachIndexed { i, n ->
                                FilterChip(selected = cfg.zoneMode == i, onClick = { cfg = cfg.copy(zoneMode = i) }, label = { Text(n) })
                            }
                        }
                    }
                    2 -> {
                        // ------------------------------------------------ layout
                        if (cfg.layout != WidgetConfig.LAYOUT_STACK) {
                            Hint("The ${caps.name} layout has its own arrangement, so settings that it does not use are hidden. Choose the Classic template to get them back.")
                        }
                        if (caps.datePosition) {
                            SectionTitle("Date position")
                            ChipRow {
                                listOf("Above the time", "Below the time").forEachIndexed { i, n ->
                                    FilterChip(selected = (i == 0) == cfg.dateAbove, onClick = { cfg = cfg.copy(dateAbove = i == 0) }, label = { Text(n) })
                                }
                            }
                            if (!cfg.showDate) Hint("The date is currently hidden. Turn it on in the Lines tab.")
                        }
                        if (caps.hAlign) {
                            SectionTitle("Horizontal alignment")
                            ChipRow {
                                listOf("Left", "Center", "Right").forEachIndexed { i, n ->
                                    FilterChip(selected = cfg.hAlign == i, onClick = { cfg = cfg.copy(hAlign = i) }, label = { Text(n) })
                                }
                            }
                        }
                        if (caps.vAlign) {
                            SectionTitle("Vertical alignment")
                            ChipRow {
                                listOf("Top", "Middle", "Bottom").forEachIndexed { i, n ->
                                    FilterChip(selected = cfg.vAlign == i, onClick = { cfg = cfg.copy(vAlign = i) }, label = { Text(n) })
                                }
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
                    3 -> {
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
                        // ------------------------------------------------ one card per line: its own switch, size and colour
                        Hint(
                            if (cfg.layout == WidgetConfig.LAYOUT_STACK) "Each line has its own size and colour. Tap a card to open it."
                            else "Showing only what the ${caps.name} layout uses. Lines it does not draw are greyed out.",
                        )
                        LineCard("Time", if (caps.timeSize) "${cfg.sizeSp} sp" else "colour only", null, {}, startOpen = false) {
                            if (caps.timeSize) {
                                SliderRow("Size: ${cfg.sizeSp} sp", cfg.sizeSp.toFloat(), 24f..120f) { cfg = cfg.copy(sizeSp = it.roundToInt()) }
                            }
                            ColorRow(if (caps.timeSize) "Colour" else "Colour (dial and hour hand)", cfg.textColor, TEXT_SWATCHES) { cfg = cfg.copy(textColor = it) }
                            if (caps.timeFormat) {
                                LabeledRow("24-hour clock") {
                                    Switch(checked = cfg.use24h, onCheckedChange = { cfg = cfg.copy(use24h = it, leadingZero = it) })
                                }
                                if (!cfg.use24h) {
                                    LabeledRow("Show AM/PM") { Switch(checked = cfg.showAmPm, onCheckedChange = { cfg = cfg.copy(showAmPm = it) }) }
                                }
                                LabeledRow("Leading zero (${if (cfg.use24h) "07:30" else "07:30 vs 7:30"})") {
                                    Switch(checked = cfg.leadingZero, onCheckedChange = { cfg = cfg.copy(leadingZero = it) })
                                }
                            }
                            Hint("Seconds are never shown on this widget.")
                        }
                        LineCard(
                            "Date", "${cfg.dateSizeSp} sp", if (caps.dateSwitch) cfg.showDate else null, { cfg = cfg.copy(showDate = it) },
                            startOpen = true, active = caps.dateLine, inactiveNote = "Not used by this layout.",
                        ) {
                            if (caps.dateNote.isNotEmpty()) Hint(caps.dateNote)
                            if (caps.datePosition) {
                                SectionTitle("Position")
                                ChipRow {
                                    listOf("Above the time", "Below the time").forEachIndexed { i, n ->
                                        FilterChip(selected = (i == 0) == cfg.dateAbove, onClick = { cfg = cfg.copy(dateAbove = i == 0) }, label = { Text(n) })
                                    }
                                }
                            }
                            SliderRow("Size: ${cfg.dateSizeSp} sp", cfg.dateSizeSp.toFloat(), 10f..40f) { cfg = cfg.copy(dateSizeSp = it.roundToInt()) }
                            ColorRow("Colour", cfg.dateColor, TEXT_SWATCHES) { cfg = cfg.copy(dateColor = it) }
                            if (caps.dateFormat) {
                                SectionTitle("Format")
                                ChipRow {
                                    WidgetConfig.DATE_FORMATS.forEachIndexed { i, f ->
                                        val sample = java.time.LocalDate.of(2026, 11, 4)
                                            .format(java.time.format.DateTimeFormatter.ofPattern(f, java.util.Locale.getDefault()))
                                        FilterChip(selected = cfg.dateFormat == i, onClick = { cfg = cfg.copy(dateFormat = i) }, label = { Text(sample) })
                                    }
                                }
                            }
                        }
                        LineCard(
                            "Next alarm", "${cfg.alarmSizeSp} sp", if (caps.alarmSwitch) cfg.showNextAlarm else null, { cfg = cfg.copy(showNextAlarm = it) },
                            startOpen = true, active = caps.alarmLine, inactiveNote = "Not shown by this layout.",
                        ) {
                            if (!caps.alarmSwitch) Hint("This layout always shows the next alarm.")
                            SliderRow("Size: ${cfg.alarmSizeSp} sp", cfg.alarmSizeSp.toFloat(), 10f..32f) { cfg = cfg.copy(alarmSizeSp = it.roundToInt()) }
                            ColorRow("Colour", cfg.alarmColor, TEXT_SWATCHES) { cfg = cfg.copy(alarmColor = it) }
                        }
                        LineCard(
                            "Second clock",
                            if (cfg.showWorld || !caps.worldSwitch) WorldEntry.label(cfg.worldZone.ifBlank { worldFallback }) else "Off",
                            if (caps.worldSwitch) cfg.showWorld else null,
                            { on ->
                                val pick = cfg.worldZone.ifBlank { worldFallback }
                                cfg = cfg.copy(showWorld = on, worldZone = pick)
                            },
                            startOpen = !caps.worldSwitch, active = caps.worldLine, inactiveNote = "Not shown by this layout.",
                        ) {
                            if (worldZones.size <= 1) Hint("Add more cities in the World tab to choose from.")
                            ChipRow {
                                (worldZones + worldFallback).distinct().forEach { z ->
                                    FilterChip(selected = cfg.worldZone == z, onClick = { cfg = cfg.copy(worldZone = z) }, label = { Text(WorldEntry.label(z)) })
                                }
                            }
                            if (caps.worldStyle) {
                                SliderRow("Size: ${cfg.worldSizeSp} sp", cfg.worldSizeSp.toFloat(), 10f..32f) { cfg = cfg.copy(worldSizeSp = it.roundToInt()) }
                                ColorRow("Label colour", cfg.worldColor, TEXT_SWATCHES) { cfg = cfg.copy(worldColor = it) }
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

/** A collapsible card for one line of the widget. [enabled] = null means the line is always shown (no switch). */
@Composable
private fun LineCard(
    title: String,
    summary: String,
    enabled: Boolean?,
    onEnabled: (Boolean) -> Unit,
    startOpen: Boolean,
    active: Boolean = true,
    inactiveNote: String = "",
    content: @Composable () -> Unit,
) {
    if (!active) {
        // This layout does not draw the line: show it greyed out instead of offering settings that do nothing.
        Card(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), modifier = Modifier.weight(1f))
                Hint(inactiveNote)
            }
        }
    } else {
    var open by remember { mutableStateOf(startOpen) }
    Card(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Hint(summary)
                if (enabled != null) Switch(checked = enabled, onCheckedChange = onEnabled)
                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (open) "Collapse" else "Expand")
            }
            if (open) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
            }
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
