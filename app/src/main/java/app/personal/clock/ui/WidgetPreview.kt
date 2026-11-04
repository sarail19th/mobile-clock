@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)

package app.personal.clock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.personal.clock.R
import app.personal.clock.core.Fmt
import app.personal.clock.core.WidgetFitter
import app.personal.clock.data.Store
import app.personal.clock.data.WidgetConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WidgetFonts {
    val names = listOf("Sans", "Rounded", "Mono", "Serif", "Condensed", "Thin")

    private fun variable(res: Int, weight: Int) = FontFamily(
        Font(
            res, FontWeight(weight), FontStyle.Normal,
            FontVariation.Settings(FontWeight(weight), FontStyle.Normal, FontVariation.weight(weight)),
        ),
    )

    // Same pinned weights as the 'fontVariationSettings' in the generated widget layouts.
    private val families: List<FontFamily> by lazy {
        listOf(
            variable(R.font.dm_sans, 500),
            variable(R.font.nunito, 600),
            variable(R.font.jetbrains_mono, 500),
            variable(R.font.playfair_display, 500),
            FontFamily(Font(R.font.barlow_condensed)),
            FontFamily(Font(R.font.poppins_thin)),
        )
    }

    fun family(index: Int): FontFamily = families[index.coerceIn(0, families.size - 1)]
}

/**
 * What the widget will look like, drawn at the widget's real size (dp) with the same fitting rules
 * as the real renderer. Approximate for the background: "frosted" is a tint + edge, never a real blur.
 */
@Composable
fun WidgetPreview(cfg: WidgetConfig, widthDp: Int, heightDp: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val settings = remember { store.settings() }
    val tick by rememberTicker(1_000L)
    val zone = if (cfg.zoneMode == 1) ZoneId.systemDefault() else settings.zone()
    val now = Instant.ofEpochMilli(tick).atZone(zone)
    val sys24 = DateFormat.is24HourFormat(ctx)

    val showAmPm = !cfg.use24h && cfg.showAmPm
    val worldOk = cfg.showWorld && cfg.worldZone.isNotBlank() && runCatching { ZoneId.of(cfg.worldZone) }.isSuccess
    val fit = WidgetFitter.fit(
        cfg.sizeSp, cfg.paddingDp, cfg.font, showAmPm, cfg.showDate, cfg.showNextAlarm, worldOk, widthDp, heightDp,
    )

    val timePattern = if (cfg.use24h) (if (cfg.leadingZero) "HH:mm" else "H:mm") else (if (cfg.leadingZero) "hh:mm" else "h:mm")
    val time = now.format(DateTimeFormatter.ofPattern(timePattern, Locale.getDefault()))
    val ampm = now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    val date = now.format(DateTimeFormatter.ofPattern(WidgetConfig.DATE_FORMATS[cfg.dateFormat], Locale.getDefault()))
    val nextAlarm = "Next alarm  " + now.format(DateTimeFormatter.ofPattern(if (cfg.use24h) "EEE" else "EEE", Locale.getDefault())) +
        if (cfg.use24h) " 07:30" else " 7:30 AM"
    val worldLine = if (worldOk) {
        val wz = ZoneId.of(cfg.worldZone)
        val wt = Instant.ofEpochMilli(tick).atZone(wz)
        "${Fmt.cityName(cfg.worldZone)} · ${Fmt.utcOffset(wt.offset.totalSeconds)}  " +
            wt.format(DateTimeFormatter.ofPattern(if (cfg.use24h) "HH:mm" else "h:mm a", Locale.getDefault()))
    } else ""

    val family = WidgetFonts.family(cfg.font)
    val textColor = Color(cfg.textColor)
    val dateColor = Color(cfg.dateColor)
    val shadow = if (cfg.shadow) Shadow(Color(0xB3000000), Offset(0f, 2f), 6f) else null
    val shape = RoundedCornerShape((cfg.cornerStep * 4).dp)

    fun style(sp: Float, color: Color) = TextStyle(
        fontFamily = family, fontSize = sp.sp, color = color, shadow = shadow,
        lineHeight = (sp * 1.1f).sp,
    )

    val hAlign = when (cfg.hAlign) {
        WidgetConfig.ALIGN_START -> Alignment.Start
        WidgetConfig.ALIGN_END -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val textAlign = when (cfg.hAlign) {
        WidgetConfig.ALIGN_START -> TextAlign.Start
        WidgetConfig.ALIGN_END -> TextAlign.End
        else -> TextAlign.Center
    }
    val box = when (cfg.vAlign) {
        0 -> when (cfg.hAlign) { 0 -> Alignment.TopStart; 2 -> Alignment.TopEnd; else -> Alignment.TopCenter }
        2 -> when (cfg.hAlign) { 0 -> Alignment.BottomStart; 2 -> Alignment.BottomEnd; else -> Alignment.BottomCenter }
        else -> when (cfg.hAlign) { 0 -> Alignment.CenterStart; 2 -> Alignment.CenterEnd; else -> Alignment.Center }
    }

    // A busy "wallpaper" behind the widget so transparency and blur-less frosting are visible.
    Box(
        modifier.fillMaxWidth().height((heightDp + 40).dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF355C7D), Color(0xFF6C5B7B), Color(0xFFC06C84), Color(0xFFF8B195)))),
        contentAlignment = Alignment.Center,
    ) {
        var inner = Modifier.size(widthDp.dp, heightDp.dp).clip(shape)
        if (cfg.bgMode != WidgetConfig.BG_TRANSPARENT) {
            inner = inner.background(Color(cfg.bgColor).copy(alpha = cfg.bgOpacity / 100f))
        }
        if (cfg.bgMode == WidgetConfig.BG_FROSTED) {
            inner = inner.border(1.dp, Color.White.copy(alpha = 0.35f), shape)
        }
        Box(inner.padding(cfg.paddingDp.dp), contentAlignment = box) {
            Column(horizontalAlignment = hAlign) {
                Text(
                    if (showAmPm) "$time $ampm" else time,
                    style = style(fit.sizeSp, textColor),
                    maxLines = 1, softWrap = false, textAlign = textAlign,
                )
                if (fit.showDate) Text(date, style = style(fit.subSp, dateColor), maxLines = 1, textAlign = textAlign)
                if (fit.showAlarm) Text(nextAlarm, style = style(fit.subSp * 0.9f, dateColor), maxLines = 1, textAlign = textAlign)
                if (fit.showWorld) Text(worldLine, style = style(fit.subSp * 0.9f, dateColor), maxLines = 1, textAlign = textAlign)
            }
        }
    }
}
