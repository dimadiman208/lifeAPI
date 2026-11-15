package com.lifeapi.app.ui.screens.grow.learn

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeapi.app.data.Material
import com.lifeapi.app.data.grow3.MaterialKinds
import com.lifeapi.app.data.grow3.learn.LearnLogic
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

object LearnRoutes {
    const val MATERIAL = "grow/learn/material/{id}"
    const val PDF = "grow/learn/pdf/{id}?page={page}"
    fun material(id: Long) = "grow/learn/material/$id"
    fun pdf(id: Long, page: Int = -1) = "grow/learn/pdf/$id?page=$page"
    /** Registered by the Growth agent. */
    fun addToGrowth(materialId: Long) = "grow/growth/add?material=$materialId"
    fun skill(id: Long) = "grow/growth/skill/$id"
    fun project(id: Long) = "grow/projects/project/$id"
}

/** Short badge label + colour per material type, so PDFs, docs, videos and repos read differently at a glance. */
data class KindStyle(val label: String, val color: Color)

fun kindStyle(kind: String): KindStyle = when (kind) {
    MaterialKinds.PDF -> KindStyle("PDF", C.Train)
    MaterialKinds.ARTICLE -> KindStyle("ART", C.Money)
    MaterialKinds.DOCS -> KindStyle("DOC", C.Health)
    MaterialKinds.GITHUB -> KindStyle("GH", C.InkSoft)
    MaterialKinds.YOUTUBE -> KindStyle("YT", C.Negative)
    else -> KindStyle("URL", C.Grow)
}

fun statusColor(status: String): Color = when (status) {
    "In Progress" -> C.Grow
    "Completed" -> C.Positive
    "Archived" -> C.InkFaint
    else -> C.InkSubtle
}

@Composable
fun KindBadge(kind: String, size: Dp = 40.dp) {
    val k = kindStyle(kind)
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(k.color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        Text(k.label, style = T.label.copy(fontSize = 10.sp, letterSpacing = 0.02.sp), color = k.color)
    }
}

/** Compact library row: type badge, title, source · status · progress, tags, favourite star. */
@Composable
fun MaterialCard(m: Material, onOpen: () -> Unit, onFavorite: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(C.Graphite1).border(1.dp, C.Hairline, shape)
            .press(onClick = onOpen).padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindBadge(m.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(m.title, style = T.body, color = if (m.status == "Archived") C.InkSubtle else C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(LearnLogic.source(m).ifBlank { null }, LearnLogic.progressLabel(m)).joinToString(" · ")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.status, style = T.caption.copy(fontWeight = FontWeight.SemiBold), color = statusColor(m.status), maxLines = 1)
                if (meta.isNotBlank()) Text("· $meta", style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val tags = MaterialKinds.tagList(m.tags)
            if (tags.isNotEmpty()) Text(tags.take(4).joinToString("  ") { "#$it" }, style = T.caption, color = C.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LearnLogic.progressFraction(m)?.takeIf { m.status == "In Progress" }?.let {
                LinearBar(it, C.Grow, C.GrowTint, 3.dp, Modifier.padding(top = 3.dp))
            }
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).press(onClick = onFavorite), contentAlignment = Alignment.Center) {
            Text(if (m.favorite) "★" else "☆", style = T.section.copy(fontSize = 18.sp), color = if (m.favorite) C.Fuel else C.InkFaint)
        }
    }
}

/** One-line horizontally scrolling chip row ("All" + options). */
@Composable
fun ChipRow(options: List<String>, selected: String?, hue: Color = C.Grow, allLabel: String? = "All", onPick: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allLabel != null) SmallFilterChip(allLabel, selected == null, hue) { onPick(null) }
        options.forEach { o -> SmallFilterChip(o, selected == o, hue) { onPick(if (selected == o) null else o) } }
    }
}

@Composable
fun SmallFilterChip(text: String, on: Boolean, hue: Color = C.Grow, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(32.dp).clip(shape).background(if (on) hue.copy(alpha = 0.16f) else C.Graphite1)
            .border(1.dp, if (on) hue.copy(alpha = 0.45f) else C.Hairline, shape)
            .press(onClick = onClick).padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.small.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = if (on) hue else C.InkMuted, maxLines = 1) }
}

@Composable
fun LearnIconAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(44.dp).clip(shape).background(C.GrowTint).press(haptic = true, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = C.Grow, modifier = Modifier.size(16.dp))
        Text(text, style = T.smallB, color = C.Grow, maxLines = 1)
    }
}

/** Opens a link in the user's browser / YouTube / GitHub app. */
fun openUrl(ctx: Context, url: String): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (e: ActivityNotFoundException) { false }

