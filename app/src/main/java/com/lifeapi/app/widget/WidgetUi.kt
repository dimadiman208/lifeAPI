package com.lifeapi.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lifeapi.app.R
import com.lifeapi.app.ui.theme.C

internal object W {
    val ink = ColorProvider(C.Ink)
    val muted = ColorProvider(C.InkMuted)
    val subtle = ColorProvider(C.InkSubtle)
    val onHue = ColorProvider(C.Graphite0)
    fun c(color: Color) = ColorProvider(color)
}

/** Graphite 1 surface + 1 px hairline; radius follows the launcher on Android 12+. */
@Composable
internal fun WidgetSurface(
    onClick: Action? = null,
    padding: Dp = 14.dp,
    vertical: Alignment.Vertical = Alignment.Top,
    horizontal: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .then(if (onClick != null) GlanceModifier.clickable(onClick) else GlanceModifier)
            .padding(padding),
        verticalAlignment = vertical,
        horizontalAlignment = horizontal,
        content = content,
    )
}

@Composable
internal fun Cap(text: String, color: Color) {
    Text(
        text,
        style = TextStyle(color = W.c(color), fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace),
        maxLines = 1,
    )
}

@Composable
internal fun T(
    text: String,
    size: TextUnit = 13.sp,
    color: ColorProvider = W.ink,
    weight: FontWeight = FontWeight.Normal,
    strike: Boolean = false,
    maxLines: Int = 1,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textDecoration = if (strike) TextDecoration.LineThrough else null),
        maxLines = maxLines,
    )
}

/** Solid pill button from a shape drawable (corners work on every API level). */
@Composable
internal fun PillBtn(text: String, bg: Int, fg: Color, action: Action, height: Dp = 36.dp, bold: Boolean = true, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier.height(height).background(ImageProvider(bg)).clickable(action).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 12.sp, W.c(fg), if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
internal fun Bar(progress: Float, color: Color, track: Color, modifier: GlanceModifier = GlanceModifier) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
        color = W.c(color),
        backgroundColor = W.c(track),
    )
}

@Composable
internal fun Check(done: Boolean, onRes: Int, modifier: GlanceModifier = GlanceModifier) {
    Image(ImageProvider(if (done) onRes else R.drawable.widget_ck_off), if (done) "Done" else "Not done", modifier)
}

/** Three concentric progress rings (body, fuel, grow) with the score in the middle, as in the design. */
internal fun ringsBitmap(body: Float, fuel: Float, grow: Float, sizePx: Int = 264): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val k = sizePx / 132f
    val stroke = 10f * k
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    fun ring(r: Float, v: Float, color: Color, track: Color) {
        val rect = RectF(66 * k - r * k, 66 * k - r * k, 66 * k + r * k, 66 * k + r * k)
        p.color = track.toArgb(); cv.drawArc(rect, 0f, 360f, false, p)
        if (v > 0.005f) { p.color = color.toArgb(); cv.drawArc(rect, -90f, 360f * v.coerceAtMost(1f), false, p) }
    }
    ring(58f, body, C.Health, Color(0xFF1E2A2A))
    ring(44f, fuel, C.Fuel, C.FuelTint)
    ring(30f, grow, C.Grow, C.GrowTint)
    return bmp
}
