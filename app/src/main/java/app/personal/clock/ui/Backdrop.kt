@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.personal.clock.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

/** What the editor previews the widget on. Only affects the editor, never the real widget. */
sealed class Backdrop {
    object Gradient : Backdrop()
    class Solid(val argb: Int) : Backdrop()
    class Photo(val image: ImageBitmap) : Backdrop()
}

/** Remembers the chosen backdrop. A photo is stored as a small private copy, so no storage permission is needed. */
object BackdropStore {
    private const val PREFS = "ui"
    private const val KEY = "preview_bg"
    private const val FILE = "preview_bg.jpg"

    fun load(ctx: Context): Backdrop {
        val v = ctx.getSharedPreferences(PREFS, 0).getString(KEY, "gradient") ?: "gradient"
        return when {
            v == "photo" -> loadPhoto(ctx) ?: Backdrop.Gradient
            v.startsWith("color:") -> runCatching { Backdrop.Solid(v.removePrefix("color:").toLong(16).toInt()) }.getOrDefault(Backdrop.Gradient)
            else -> Backdrop.Gradient
        }
    }

    fun saveGradient(ctx: Context) = put(ctx, "gradient")

    fun saveSolid(ctx: Context, argb: Int) = put(ctx, "color:%08X".format(argb))

    /** Copies a downsized version of the picked image into the app's private storage. Returns false if it can't be read. */
    fun savePhoto(ctx: Context, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 2800) sample *= 2
        val bmp: Bitmap = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        FileOutputStream(File(ctx.filesDir, FILE)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        put(ctx, "photo")
        true
    }.getOrDefault(false)

    private fun loadPhoto(ctx: Context): Backdrop? =
        runCatching { BitmapFactory.decodeFile(File(ctx.filesDir, FILE).path)?.asImageBitmap()?.let { Backdrop.Photo(it) } }.getOrNull()

    private fun put(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY, value).apply()
    }
}

/** A rounded box showing the chosen backdrop behind [content]. */
@Composable
fun BackdropBox(backdrop: Backdrop, modifier: Modifier, corner: Dp = 16.dp, content: @Composable BoxScope.() -> Unit) {
    var m = modifier.clip(RoundedCornerShape(corner))
    m = when (backdrop) {
        is Backdrop.Solid -> m.background(Color(backdrop.argb or (0xFF shl 24)))
        is Backdrop.Gradient -> m.background(PreviewWallpaper)
        is Backdrop.Photo -> m
    }
    Box(m, contentAlignment = Alignment.Center) {
        if (backdrop is Backdrop.Photo) {
            Image(backdrop.image, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        content()
    }
}

private val BACKDROP_COLOURS = listOf(
    "Cream" to 0xFFE6DFC8,
    "Sand" to 0xFFD9C79A,
    "White" to 0xFFFFFFFF,
    "Grey" to 0xFFB8BCC4,
    "Blue" to 0xFF5C86C9,
    "Dark" to 0xFF202124,
)

/** Lets the user choose what the preview sits on: a preset, any colour, or a screenshot of their own home screen. */
@Composable
fun BackdropDialog(onChanged: (Backdrop) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var custom by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (BackdropStore.savePhoto(ctx, uri)) {
                onChanged(BackdropStore.load(ctx))
                onDismiss()
            } else failed = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview background") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint("Only changes this preview, not your widget. Pick something close to your wallpaper, or choose a screenshot of your home screen to see the widget on it.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(PreviewWallpaper).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { BackdropStore.saveGradient(ctx); onChanged(Backdrop.Gradient); onDismiss() },
                    )
                    BACKDROP_COLOURS.forEach { (name, argb) ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Color(argb)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { BackdropStore.saveSolid(ctx, argb.toInt()); onChanged(Backdrop.Solid(argb.toInt())); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {}
                    }
                }
                Hint("Left to right: sample gradient, " + BACKDROP_COLOURS.joinToString(", ") { it.first.lowercase() } + ".")
                Button(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) { Text("Pick any colour") }
                Button(
                    onClick = { failed = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Use a screenshot or photo") }
                if (failed) Hint("That image could not be read. Try another one.")
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (custom) {
        ColorPickerDialog(
            initial = 0xFFE6DFC8.toInt(),
            onDismiss = { custom = false },
            onPick = { argb -> custom = false; BackdropStore.saveSolid(ctx, argb); onChanged(Backdrop.Solid(argb)); onDismiss() },
        )
    }
}
