package com.lifeapi.app.ui.screens.body.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

/** Display order of meal slots in Nutrition (stored names match MEAL_SLOTS). */
val SLOTS = listOf("Breakfast", "Lunch", "Dinner", "Snack")

object NIc {
    private fun icon(name: String, w: Float, vararg paths: String, fill: Boolean = false): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            if (fill) b.addPath(addPathNodes(it), fill = SolidColor(Color.Black))
            else b.addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = w, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        return b.build()
    }

    private const val STAR = "M12 3.8l2.5 5.1 5.6.8-4 3.9.9 5.6-5-2.6-5 2.6.9-5.6-4-3.9 5.6-.8z"
    val Star = icon("star", 1.75f, STAR)
    val StarFilled = icon("star-f", 0f, STAR, fill = true)
    val Open = icon("open", 1.75f, "M14 4.5h5.5V10", "M19.5 4.5L11 13", "M18 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10")
    val Sync = icon("sync", 1.75f, "M20 11a8 8 0 0 0-14.6-4.5L4 8", "M4 4v4h4", "M4 13a8 8 0 0 0 14.6 4.5L20 16", "M20 20v-4h-4")
    val Copy = icon("copy", 1.75f, "M9 9h10.5v10.5H9z", "M15 9V4.5H4.5V15H9")
}
