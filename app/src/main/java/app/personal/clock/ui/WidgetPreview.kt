@file:OptIn(ExperimentalMaterial3Api::class)

package app.personal.clock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.personal.clock.data.WidgetConfig
import app.personal.clock.widget.WidgetScene

/** The "wallpaper" gradient shown behind previews so transparency and translucency are visible. */
val PreviewWallpaper: Brush = Brush.linearGradient(listOf(Color(0xFF355C7D), Color(0xFF6C5B7B), Color(0xFFC06C84), Color(0xFFF8B195)))

/**
 * One widget, drawn at its real dp size and then scaled by [scale]. The text is the very same bitmap the home-screen
 * widget shows ([WidgetScene]); only the panel behind it is drawn here (the widget draws it with image views).
 * "Frosted" is a tinted see-through panel with a soft edge, never a real blur.
 */
@Composable
fun WidgetPanel(cfg: WidgetConfig, widthDp: Int, heightDp: Int, scale: Float, minuteKey: Long = 0L) {
    val ctx = LocalContext.current
    val bmp = remember(cfg, widthDp, heightDp, minuteKey) { WidgetScene.render(ctx, cfg, widthDp, heightDp) }
    val shape = RoundedCornerShape((cfg.cornerStep * 4).dp)
    var panel = Modifier.requiredSize(widthDp.dp, heightDp.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
    if (cfg.bgMode != WidgetConfig.BG_TRANSPARENT) {
        panel = panel.background(Color(cfg.bgColor).copy(alpha = cfg.bgOpacity / 100f))
    }
    if (cfg.bgMode == WidgetConfig.BG_FROSTED) {
        panel = panel.border(1.dp, Color.White.copy(alpha = 0.35f), shape)
    }
    Box(Modifier.size((widthDp * scale).dp, (heightDp * scale).dp), contentAlignment = Alignment.Center) {
        Box(panel) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }
}

/** Big preview at the top of the editor, at the widget's current size, on the chosen backdrop. */
@Composable
fun WidgetPreview(cfg: WidgetConfig, widthDp: Int, heightDp: Int, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val tick by rememberTicker(60_000L)
    // Drawn at real size (so fitting is exact) then scaled down to leave room for the controls.
    val scale = (150f / heightDp).coerceIn(0.4f, 1f)
    BackdropBox(backdrop, modifier.fillMaxWidth().height(((heightDp + 24) * scale).dp)) {
        WidgetPanel(cfg, widthDp, heightDp, scale, tick / 60_000L)
    }
}

/** Fixed-size thumbnail of a template (rendered at the template's nominal widget size, scaled to fit the tile). */
@Composable
fun WidgetThumb(cfg: WidgetConfig, widthDp: Int, heightDp: Int, tileHeight: Dp, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val scale = (140f / widthDp).coerceAtMost((tileHeight.value - 16f) / heightDp).coerceIn(0.2f, 1f)
    BackdropBox(backdrop, modifier.fillMaxWidth().height(tileHeight), corner = 14.dp) {
        WidgetPanel(cfg, widthDp, heightDp, scale)
    }
}
