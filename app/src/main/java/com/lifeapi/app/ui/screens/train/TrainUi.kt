package com.lifeapi.app.ui.screens.train

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

/** Train-specific colors from TrainFlow.dc.html. */
object TC {
    val PrFg = Color(0xFFFFB29C)
    val WarmBorder = Color(0xFF3A2A24)
    val WarmBg = Color(0xFF1A1311)
    val PausedBg = Color(0xFF1A1513)
    val CreateBorder = Color(0xFF5A3A30)
    val RowDivider = Color(0xFF1A1B1F)
    val Chip = Color(0xFF2C2F36)
}

object TrainIc {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        return b.build()
    }
    val Pause = icon("pause", "M9 6v12M15 6v12")
    val List = icon("list", "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01")
}

@Composable
fun TrainCircleButton(icon: ImageVector, desc: String?, size: Dp = 40.dp, tint: Color = C.InkSoft, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(C.Track).press(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun TrainTopBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TrainCircleButton(Ic.ChevronLeft, "Back", onClick = onBack)
        Text(title, style = T.title.copy(fontSize = 18.sp), color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

/** The 3-up stat card: small label, number, sub line. */
@Composable
fun RowScope.MiniStat(label: String, value: String, sub: String?, subColor: Color = C.InkSubtle, center: Boolean = false) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(C.Graphite1).border(1.dp, C.Hairline, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(label, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = C.InkSubtle)
        Text(value, style = T.section.copy(fontSize = 18.sp), color = C.Ink, maxLines = 1)
        if (sub != null) Text(sub, style = T.tiny.copy(fontWeight = FontWeight.Normal), color = subColor, maxLines = 1)
    }
}

/** Outline chip; selected = Train tint. */
@Composable
fun TrainChip(text: String, selected: Boolean, height: Dp = 32.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        Modifier.height(height).clip(shape)
            .background(if (selected) C.TrainTint else Color.Transparent)
            .border(1.dp, if (selected) TC.CreateBorder else TC.Chip, shape)
            .press(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.caption.copy(fontWeight = FontWeight.Medium), color = if (selected) C.Train else C.InkMuted) }
}

@Composable
fun PrPill() {
    Box(Modifier.height(20.dp).clip(RoundedCornerShape(10.dp)).background(C.TrainTint).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text("PR", style = T.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = TC.PrFg)
    }
}

/** Card with hairline rows. */
@Composable
fun RowsCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Graphite1).border(1.dp, C.Hairline, RoundedCornerShape(20.dp))) { content() }
}

@Composable
fun DlRow(first: Boolean, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(TC.RowDivider))
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
