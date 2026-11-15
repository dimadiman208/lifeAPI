package com.lifeapi.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons transcribed from the design's 24×24 SVGs. */
object Ic {
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} $y H${x + w - r} A$r $r 0 0 1 ${x + w} ${y + r} V${y + h - r} A$r $r 0 0 1 ${x + w - r} ${y + h} " +
            "H${x + r} A$r $r 0 0 1 $x ${y + h - r} V${y + r} A$r $r 0 0 1 ${x + r} $y Z"

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z"

    private fun icon(name: String, width: Float = 1.75f, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(
                pathData = addPathNodes(it),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun filled(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { b.addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        return b.build()
    }

    val Today = icon("today", 1.75f, rect(3.5f, 3.5f, 7f, 7f, 2f), rect(13.5f, 3.5f, 7f, 7f, 2f), rect(3.5f, 13.5f, 7f, 7f, 2f), rect(13.5f, 13.5f, 7f, 7f, 2f))
    val Body = icon("body", 1.75f, "M3 12h4l2.5-6 5 12 2.5-6H21")
    val Money = icon("money", 1.75f, rect(3f, 6f, 18f, 13f, 3f), "M3 10h18M16 15h2")
    val Train = icon("train", 1.75f, "M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11")
    val Grow = icon("grow", 1.75f, "M12 20v-8", "M12 12c0-4 3-6 7-6 0 4-3 6-7 6z", "M12 14c0-3-2-5-6-5 0 3 2 5 6 5z")

    val Bell = icon("bell", 1.75f, "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val Drop = icon("drop", 1.75f, "M12 3.5c3.5 4.2 6 7.6 6 10.5a6 6 0 0 1-12 0c0-2.9 2.5-6.3 6-10.5z")
    val Pill = icon("pill", 1.75f, "M10.2 4.2a4.6 4.6 0 0 1 6.5 6.5l-6 6a4.6 4.6 0 0 1-6.5-6.5z", "M7.2 7.2l6.5 6.5")
    val Fork = icon("fork", 1.75f, "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 21V3c-2 1.5-3 4-3 7h3")
    val Receipt = icon("receipt", 1.75f, "M6 3h12v18l-3-2-3 2-3-2-3 2z", "M9 8h6M9 12h6")
    val Moon = icon("moon", 1.75f, "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Check = icon("check", 2.25f, "M5 12.5l4.5 4.5L19 7.5")
    val Plus = icon("plus", 2f, "M12 5v14M5 12h14")
    val Close = icon("close", 2f, "M6 6l12 12M18 6L6 18")
    val ChevronRight = icon("chev-r", 1.75f, "M9.5 6l6 6-6 6")
    val ChevronLeft = icon("chev-l", 1.75f, "M14.5 6l-6 6 6 6")
    val ChevronDown = icon("chev-d", 1.75f, "M6 9.5l6 6 6-6")
    val Search = icon("search", 1.75f, circle(11f, 11f, 6.5f), "M16 16l4.5 4.5")
    val Eye = icon("eye", 1.75f, "M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z", circle(12f, 12f, 2.75f))
    val EyeOff = icon("eye-off", 1.75f, "M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z", "M4 4l16 16")
    val Flame = icon("flame", 1.75f, "M12 21a6 6 0 0 0 6-6c0-3.5-2.5-5.5-3.5-8.5-1 2-2 3-3.5 3.5C11 7 10 5 8.5 3.5 8 7 6 9.5 6 15a6 6 0 0 0 6 6z")
    val Sparkle = icon("sparkle", 1.75f, "M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z", "M18.5 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z")
    val More = icon("more", 2.5f, "M6 12h.01M12 12h.01M18 12h.01")
    val Play = icon("play", 1.75f, "M8 5.5v13l10.5-6.5z")
    val Trash = icon("trash", 1.75f, "M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13", "M10 11v5.5M14 11v5.5")
    val Edit = icon("edit", 1.75f, "M4 20h4l10.5-10.5a2.8 2.8 0 0 0-4-4L4 16z", "M13.5 6.5l4 4")
    val Spark = icon("trend", 1.75f, "M3 17l6-6 4 4 8-8", "M15 7h6v6")
    val Settings = icon("settings", 1.75f, circle(12f, 12f, 3f), "M12 2.5v3M12 18.5v3M21.5 12h-3M5.5 12h-3M18.7 5.3l-2.1 2.1M7.4 16.6l-2.1 2.1M18.7 18.7l-2.1-2.1M7.4 7.4L5.3 5.3")
    val Book = icon("book", 1.75f, "M4 5.5A1.5 1.5 0 0 1 5.5 4H11v16H5.5A1.5 1.5 0 0 1 4 18.5z", "M20 5.5A1.5 1.5 0 0 0 18.5 4H13v16h5.5a1.5 1.5 0 0 0 1.5-1.5z")
    val Dot = filled("dot", circle(12f, 12f, 4f))
}
