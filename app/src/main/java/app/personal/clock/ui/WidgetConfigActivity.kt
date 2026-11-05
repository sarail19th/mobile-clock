@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.personal.clock.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import app.personal.clock.widget.LayoutCaps
import app.personal.clock.widget.WidgetTemplates
import app.personal.clock.widget.WidgetTypefaces
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDefault by remember { mutableStateOf(false) }
    var openLine by remember { mutableIntStateOf(-1) } // which line card is open (-1 = all closed)
    var section by remember { mutableIntStateOf(if (startOnTemplates) 0 else 1) }
    var backdrop by remember { mutableStateOf(BackdropStore.load(ctx)) }
    var showBackdrop by remember { mutableStateOf(false) }
    val store = remember { Store.get(ctx) }
    val worldZones = remember { store.world() }
    val homeId = remember { store.settings().zone().id }
    val worldFallback = remember { worldZones.firstOrNull { it != homeId } ?: "Asia/Tokyo" }
    val custom = cfg.font != 0 || cfg.layout != WidgetConfig.LAYOUT_STACK || !WidgetRenderer.liveOrder(cfg)
    fun move(line: Char, delta: Int) {
        // Classic reorders all four lines; Analog + digital has no second clock, so only the first three move.
        val movable = if (cfg.layout == WidgetConfig.LAYOUT_STACK) 4 else 3
        val base = if (cfg.layout == WidgetConfig.LAYOUT_STACK) cfg.safeOrder else cfg.safeOrder.filter { it != 'W' } + "W"
        val a = base.toCharArray()
        val i = a.indexOf(line)
        val j = i + delta
        if (i >= 0 && i < movable && j >= 0 && j < movable) {
            val t = a[i]; a[i] = a[j]; a[j] = t
            cfg = cfg.copy(order = a.concatToString())
        }
    }
    val caps = LayoutCaps.of(cfg.layout)

    if (showBackdrop) BackdropDialog(onChanged = { backdrop = it }, onDismiss = { showBackdrop = false })
    if (confirmDefault) {
        AlertDialog(
            onDismissRequest = { confirmDefault = false },
            title = { Text("Use this look for new widgets?") },
            text = { Text("Every widget you add from now on will start with this look. Widgets already on your home screen and the Templates are not changed.") },
            confirmButton = { TextButton(onClick = { onSaveDefault(cfg); confirmDefault = false }) { Text("Use for new widgets") } },
            dismissButton = { TextButton(onClick = { confirmDefault = false }) { Text("Cancel") } },
        )
    }

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
                WidgetPreview(cfg, widthDp, heightDp, backdrop)
            }
            // Under the picture: what the preview sits on (editor only), and the menu for "new widgets" defaults.
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showBackdrop = true }, modifier = Modifier.weight(1f)) {
                    Text("Tap to change preview background", maxLines = 1, fontSize = 13.sp)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Use this look for new widgets") },
                            onClick = { menuOpen = false; confirmDefault = true },
                        )
                    }
                }
            }
            // Section switcher: all sections visible at once, never scrolls sideways.
            SectionTabs(section) { section = it }
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
                                            WidgetThumb(preview, t.nominalW, t.nominalH, 104.dp, backdrop)
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
                                ChoiceChip(selected = cfg.font == i, onClick = { cfg = cfg.copy(font = i) }, label = { Text(n) })
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
                                    Triple("Soft", 30, 2),
                                    Triple("Normal", WidgetConfig.DEFAULT_SHADOW_STRENGTH, WidgetConfig.DEFAULT_SHADOW_BLUR),
                                    Triple("Strong", 90, 3),
                                ).forEach { (name, strength, blur) ->
                                    ChoiceChip(
                                        selected = cfg.shadowStrength == strength && cfg.shadowBlur == blur,
                                        onClick = { cfg = cfg.copy(shadowStrength = strength, shadowBlur = blur) },
                                        label = { Text(name) },
                                    )
                                }
                            }
                            SliderRow("Shadow darkness: ${cfg.shadowStrength}%", cfg.shadowStrength.toFloat(), 0f..100f) { cfg = cfg.copy(shadowStrength = it.roundToInt()) }
                            SliderRow("Shadow softness: ${cfg.shadowBlur} dp", cfg.shadowBlur.toFloat(), 0f..20f) { cfg = cfg.copy(shadowBlur = it.roundToInt()) }
                            Hint("\"Soft\" is a faint, tight shadow: about a third as dark as Normal and only a couple of pixels wide. Changing the shadow draws the widget as a picture.")
                        }
                        if (caps.accent) {
                            SectionTitle("Accent colour")
                            ColorRow("Minute hand (tap Custom for any colour)", cfg.accentColor, ACCENT_SWATCHES) { cfg = cfg.copy(accentColor = it) }
                        }
                        SectionTitle("Time zone")
                        ChipRow {
                            listOf("Home zone (${Fmt.cityName(homeId)})", "Phone's zone").forEachIndexed { i, n ->
                                ChoiceChip(selected = cfg.zoneMode == i, onClick = { cfg = cfg.copy(zoneMode = i) }, label = { Text(n) })
                            }
                        }
                    }
                    2 -> {
                        // ------------------------------------------------ layout
                        if (cfg.layout != WidgetConfig.LAYOUT_STACK) {
                            Hint("The ${caps.name} layout has its own arrangement, so settings that it does not use are hidden. Choose the Classic template to get them back.")
                        }
                        if (caps.datePosition) {
                            Hint("The order of the lines (time, date, next alarm, second clock) is set with the \u25B2 \u25BC buttons on the Lines tab.")
                        }
                        if (caps.hAlign) {
                            SectionTitle("Horizontal alignment")
                            ChipRow {
                                listOf("Left", "Center", "Right").forEachIndexed { i, n ->
                                    ChoiceChip(selected = cfg.hAlign == i, onClick = { cfg = cfg.copy(hAlign = i) }, label = { Text(n) })
                                }
                            }
                        }
                        if (caps.vAlign) {
                            SectionTitle("Vertical alignment")
                            ChipRow {
                                listOf("Top", "Middle", "Bottom").forEachIndexed { i, n ->
                                    ChoiceChip(selected = cfg.vAlign == i, onClick = { cfg = cfg.copy(vAlign = i) }, label = { Text(n) })
                                }
                            }
                        }
                        SliderRow("Padding: ${cfg.paddingDp} dp", cfg.paddingDp.toFloat(), 0f..40f) { cfg = cfg.copy(paddingDp = it.roundToInt()) }
                        SectionTitle("When tapped")
                        ChipRow {
                            listOf("Open app", "Open Alarm tab", "Do nothing").forEachIndexed { i, n ->
                                ChoiceChip(selected = cfg.tapAction == i, onClick = { cfg = cfg.copy(tapAction = i) }, label = { Text(n) })
                            }
                        }
                    }
                    3 -> {
                        // ------------------------------------------------ background
                        SectionTitle("Style")
                        ChipRow {
                            listOf("Solid", "Transparent", "Frosted").forEachIndexed { i, n ->
                                ChoiceChip(selected = cfg.bgMode == i, onClick = { cfg = cfg.copy(bgMode = i) }, label = { Text(n) })
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
                            if (caps.reorder) "Each line has its own size and colour. Tap a card to open it, and use \u25B2 \u25BC to change the order of the lines."
                            else "Showing only what the ${caps.name} layout uses. Lines it does not draw are greyed out.",
                        )
                        val canReorder = caps.reorder
                        val movableCount = if (cfg.layout == WidgetConfig.LAYOUT_STACK) 4 else 3
                        val lineOrder = when {
                            !canReorder -> WidgetConfig.DEFAULT_ORDER
                            cfg.layout == WidgetConfig.LAYOUT_STACK -> cfg.safeOrder
                            else -> cfg.safeOrder.filter { it != 'W' } + "W"
                        }
                        for (line in lineOrder) {
                            key(line) {
                                when (line) {
                                    'T' -> {
                                LineCard("Time", if (caps.timeSize) "${cfg.sizeSp} sp" else "colour only", null, {}, open = openLine == 0, onToggle = { openLine = if (openLine == 0) -1 else 0 }, movable = canReorder, canUp = lineOrder.indexOf('T') > 0, canDown = lineOrder.indexOf('T') < movableCount - 1, onMove = { move('T', it) }) {
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
                                    }
                                    'D' -> {
                                LineCard(
                                    "Date", "${cfg.dateSizeSp} sp", if (caps.dateSwitch) cfg.showDate else null, { cfg = cfg.copy(showDate = it) },
                                    open = openLine == 1, onToggle = { openLine = if (openLine == 1) -1 else 1 }, movable = canReorder, canUp = lineOrder.indexOf('D') > 0, canDown = lineOrder.indexOf('D') < movableCount - 1, onMove = { move('D', it) }, active = caps.dateLine, inactiveNote = "Not used by this layout.",
                                ) {
                                    if (caps.dateNote.isNotEmpty()) Hint(caps.dateNote)
                                    SliderRow("Size: ${cfg.dateSizeSp} sp", cfg.dateSizeSp.toFloat(), 10f..40f) { cfg = cfg.copy(dateSizeSp = it.roundToInt()) }
                                    ColorRow("Colour", cfg.dateColor, TEXT_SWATCHES) { cfg = cfg.copy(dateColor = it) }
                                    if (caps.dateFormat) {
                                        SectionTitle("Format")
                                        ChipRow {
                                            WidgetConfig.DATE_FORMATS.forEachIndexed { i, f ->
                                                val sample = java.time.LocalDate.of(2026, 11, 4)
                                                    .format(java.time.format.DateTimeFormatter.ofPattern(f, java.util.Locale.getDefault()))
                                                ChoiceChip(selected = cfg.dateFormat == i, onClick = { cfg = cfg.copy(dateFormat = i) }, label = { Text(sample) })
                                            }
                                        }
                                    }
                                }
                                    }
                                    'A' -> {
                                LineCard(
                                    "Next alarm", "${cfg.alarmSizeSp} sp", if (caps.alarmSwitch) cfg.showNextAlarm else null, { cfg = cfg.copy(showNextAlarm = it) },
                                    open = openLine == 2, onToggle = { openLine = if (openLine == 2) -1 else 2 }, movable = canReorder, canUp = lineOrder.indexOf('A') > 0, canDown = lineOrder.indexOf('A') < movableCount - 1, onMove = { move('A', it) }, active = caps.alarmLine, inactiveNote = "Not shown by this layout.",
                                ) {
                                    if (!caps.alarmSwitch) Hint("This layout always shows the next alarm.")
                                    SliderRow("Size: ${cfg.alarmSizeSp} sp", cfg.alarmSizeSp.toFloat(), 10f..32f) { cfg = cfg.copy(alarmSizeSp = it.roundToInt()) }
                                    ColorRow("Colour", cfg.alarmColor, TEXT_SWATCHES) { cfg = cfg.copy(alarmColor = it) }
                                }
                                    }
                                    'W' -> {
                                LineCard(
                                    "Second clock",
                                    if (cfg.showWorld || !caps.worldSwitch) WorldEntry.label(cfg.worldZone.ifBlank { worldFallback }) else "Off",
                                    if (caps.worldSwitch) cfg.showWorld else null,
                                    { on ->
                                        val pick = cfg.worldZone.ifBlank { worldFallback }
                                        cfg = cfg.copy(showWorld = on, worldZone = pick)
                                    },
                                    open = openLine == 3, onToggle = { openLine = if (openLine == 3) -1 else 3 }, movable = canReorder, canUp = lineOrder.indexOf('W') > 0, canDown = lineOrder.indexOf('W') < movableCount - 1, onMove = { move('W', it) }, active = caps.worldLine, inactiveNote = "Not shown by this layout.",
                                ) {
                                    if (worldZones.size <= 1) Hint("Add more cities in the World tab to choose from.")
                                    ChipRow {
                                        (worldZones + worldFallback).distinct().forEach { z ->
                                            ChoiceChip(selected = cfg.worldZone == z, onClick = { cfg = cfg.copy(worldZone = z) }, label = { Text(WorldEntry.label(z)) })
                                        }
                                    }
                                    if (caps.worldStyle) {
                                        SliderRow("Size: ${cfg.worldSizeSp} sp", cfg.worldSizeSp.toFloat(), 10f..32f) { cfg = cfg.copy(worldSizeSp = it.roundToInt()) }
                                        ColorRow("Label colour", cfg.worldColor, TEXT_SWATCHES) { cfg = cfg.copy(worldColor = it) }
                                    }
                                }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
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
    open: Boolean,
    onToggle: () -> Unit,
    movable: Boolean = false,
    canUp: Boolean = true,
    canDown: Boolean = true,
    onMove: (Int) -> Unit = {},
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
    Card(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Hint(summary)
                }
                if (movable) {
                    MoveButton("\u25B2", "Move up", canUp) { onMove(-1) }
                    MoveButton("\u25BC", "Move down", canDown) { onMove(1) }
                }
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

/** A small up/down arrow used to reorder the lines; greyed out at the end of the list. */
@Composable
private fun MoveButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph, fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.25f),
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    // Wraps onto more lines instead of scrolling sideways.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

/**
 * The five editor sections as a text tab bar with an underline under the current one. It is navigation, so it looks
 * different from the option chips below it. Labels ignore the phone's font-size setting (like a navigation bar) so
 * all five always fit on one line.
 */
@Composable
private fun SectionTabs(selected: Int, onSelect: (Int) -> Unit) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(0.5f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            SECTIONS.forEachIndexed { i, n ->
                val on = selected == i
                Column(Modifier.weight(maxOf(1f, n.length / 7f)).clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        n, maxLines = 1, softWrap = false, fontSize = (12f / fontScale).sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(3.dp).background(
                            if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                        ),
                    )
                }
            }
        }
        HorizontalDivider()
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
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = swatches.none { (it.toInt() and 0xFFFFFF) == (current and 0xFFFFFF) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        // All swatches are visible at once: they wrap onto a second line instead of scrolling sideways.
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
            // "Custom": the rainbow circle opens the full colour picker. When the colour is not a swatch it shows it in the middle.
            Row(
                Modifier.clip(RoundedCornerShape(17.dp))
                    .border(if (isCustom) 2.dp else 1.dp, if (isCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(17.dp))
                    .clickable { showPicker = true }.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(current or (0xFF shl 24))).border(2.dp, Color.White, CircleShape))
                }
                Text("Custom", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                val clean = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                hex = clean
                if (clean.length == 6) onPick((0xFF000000 or clean.toLong(16)).toInt())
            },
            label = { Text("Hex code") },
            singleLine = true,
            modifier = Modifier.width(150.dp),
        )
    }
    if (showPicker) {
        ColorPickerDialog(initial = current or (0xFF shl 24), onDismiss = { showPicker = false }, onPick = { onPick(it); showPicker = false })
    }
}

/** A slider drawn as a colour gradient; [value] is 0..1. Tap or drag anywhere on the bar. */
@Composable
private fun GradientBar(value: Float, colors: List<Color>, onChange: (Float) -> Unit) {
    var width by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current
    val latest by rememberUpdatedState(onChange)
    Box(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(Brush.horizontalGradient(colors))
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { latest((it.x / width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    latest((change.position.x / width).coerceIn(0f, 1f))
                }
            },
    ) {
        Box(
            Modifier.offset(x = with(density) { (value * width).toDp() } - 14.dp, y = 3.dp).size(28.dp).clip(CircleShape)
                .background(Color.White).border(3.dp, Color(0x80000000), CircleShape),
        )
    }
}

/** Full colour picker: hue, saturation and brightness bars with a live preview, plus a hex box. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initial, hsv)
        hsv
    }
    var h by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    fun argb(hh: Float, ss: Float, vv: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(hh.coerceIn(0f, 360f), ss.coerceIn(0f, 1f), vv.coerceIn(0f, 1f)))
    val chosen = argb(h, sat, v)
    var hexText by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    LaunchedEffect(chosen) { hexText = "%06X".format(chosen and 0xFFFFFF) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(chosen)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)))
                Text("Colour", style = MaterialTheme.typography.labelMedium)
                GradientBar(h / 360f, List(7) { Color(argb(it * 60f, 1f, 1f)) }) { h = it * 360f }
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                GradientBar(sat, listOf(Color(argb(h, 0f, v)), Color(argb(h, 1f, v)))) { sat = it }
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                GradientBar(v, listOf(Color.Black, Color(argb(h, sat, 1f)))) { v = it }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        val clean = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hexText = clean
                        if (clean.length == 6) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV((0xFF000000 or clean.toLong(16)).toInt(), hsv)
                            h = hsv[0]; sat = hsv[1]; v = hsv[2]
                        }
                    },
                    label = { Text("Hex") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen or (0xFF shl 24)) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
