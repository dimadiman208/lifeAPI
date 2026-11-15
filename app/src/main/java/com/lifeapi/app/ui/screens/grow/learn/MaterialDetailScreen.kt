package com.lifeapi.app.ui.screens.grow.learn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.MaterialNote
import com.lifeapi.app.data.grow3.MaterialKinds
import com.lifeapi.app.data.grow3.learn.LearnLogic
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.GhostButton
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.LinearBar
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.RoundIconButton
import com.lifeapi.app.ui.components.SmallChip
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.today.DetailList
import com.lifeapi.app.ui.screens.today.DetailTop
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.learn.MaterialVM
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialDetailScreen(id: Long) {
    val vm: MaterialVM = viewModel(key = "material-$id")
    LaunchedEffect(id) { vm.bind(id) }
    val st by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<MaterialNote?>(null) }
    var newNote by remember { mutableStateOf(false) }
    val repick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val m = st?.m
        if (uri != null && m != null) vm.replaceFile(m, uri)
    }

    DetailList {
        item(key = "top") {
            DetailTop("Learn", onBack = { nav.popBackStack() }) {
                st?.m?.let { m ->
                    Box(Modifier.padding(end = 4.dp).clip(RoundedCornerShape(12.dp)).press { vm.toggleFavorite(m) }.padding(8.dp)) {
                        Text(if (m.favorite) "★" else "☆", style = T.section.copy(fontSize = 20.sp), color = if (m.favorite) C.Fuel else C.InkSubtle)
                    }
                    RoundIconButton(Ic.Edit, "Edit", size = 40.dp, bg = C.Track) { editing = true }
                }
            }
        }
        val s = st ?: return@DetailList
        val m = s.m

        item(key = "head") {
            LifeCard(Modifier.fillMaxWidth(), radius = 22.dp, gap = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(m.kind, 44.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.title, style = T.title.copy(fontSize = 19.sp), color = C.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(listOf(m.kind, LearnLogic.source(m)).filter { it.isNotBlank() }.joinToString(" · "), style = T.caption, color = C.InkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (m.description.isNotBlank()) Text(m.description, style = T.small, color = C.InkMuted)
                val tags = MaterialKinds.tagList(m.tags)
                if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { SmallChip("#$it", C.Graphite2, C.InkMuted) }
                }
                LearnLogic.progressFraction(m)?.let { fr ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearBar(fr, C.Grow, C.GrowTint, 5.dp)
                        Text(LearnLogic.progressLabel(m).orEmpty(), style = T.caption, color = C.InkSubtle)
                    }
                }
                val isPdf = m.fileUri != null
                PillButton(
                    when {
                        isPdf && m.progressPage > 0 -> "Continue reading · p. ${m.progressPage + 1}"
                        isPdf -> "Read PDF"
                        m.kind == MaterialKinds.YOUTUBE -> "Watch"
                        else -> "Open link"
                    },
                    C.Grow, C.Graphite0, Modifier.fillMaxWidth(), height = 48.dp, radius = 14.dp, icon = Ic.Play,
                ) {
                    if (isPdf) nav.navigate(LearnRoutes.pdf(m.id))
                    else m.url?.let { u ->
                        if (openUrl(ctx, u)) vm.markOpened(m) else com.lifeapi.app.ui.screens.grow.learn.toast(ctx, "No app can open this link")
                    }
                }
                if (isPdf) Text(
                    "File missing? Pick it again", style = T.caption, color = C.InkSubtle,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).press { repick.launch(arrayOf("application/pdf")) }.padding(4.dp),
                )
            }
        }

        item(key = "status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoLabel("STATUS")
                ChipRow(MaterialKinds.STATUSES, m.status, allLabel = null) { pick -> if (pick != null) vm.setStatus(m, pick) }
                m.completedTs?.takeIf { m.status == "Completed" }?.let {
                    val d = com.lifeapi.app.data.Days.date(com.lifeapi.app.data.Days.dayOf(it))
                    Text("Completed ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.year}", style = T.caption, color = C.Positive)
                }
            }
        }

        item(key = "links") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoLabel("CONNECTED TO")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.skill?.let { sk -> LinkChip("Growth · ${sk.name}") { safeNavigate(nav, ctx, LearnRoutes.skill(sk.id)) } }
                    s.project?.let { p -> LinkChip("Project · ${p.name}") { safeNavigate(nav, ctx, LearnRoutes.project(p.id)) } }
                    if (s.skill == null && s.project == null) Text("Not linked — Edit to connect it to a growth direction or a project.", style = T.caption, color = C.InkSubtle)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Add to Growth", Modifier.weight(1f), height = 42.dp, color = C.Grow) { safeNavigate(nav, ctx, LearnRoutes.addToGrowth(m.id)) }
                    if (m.status != "Completed") GhostButton("Mark completed", Modifier.weight(1f), height = 42.dp) { vm.setStatus(m, "Completed") }
                }
            }
        }

        item(key = "notes-h") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel("NOTES · ${s.notes.size}", modifier = Modifier.weight(1f))
                Text("+ Note", style = T.smallB, color = C.Grow, modifier = Modifier.clip(RoundedCornerShape(8.dp)).press { newNote = true }.padding(horizontal = 8.dp, vertical = 6.dp))
            }
        }
        if (s.notes.isEmpty()) item(key = "notes-empty") {
            Text("Write down the main ideas, useful examples, what you learned, what to try and your own conclusions.", style = T.small, color = C.InkSubtle)
        }
        items(s.notes, key = { "n${it.id}" }) { n -> NoteRow(n) { note = n } }

        item(key = "danger") {
            Text(
                "Delete from library", style = T.smallB, color = C.Negative,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).press { vm.delete(m); nav.popBackStack() }.padding(8.dp),
            )
        }
    }

    val s = st
    if (s != null && editing) MaterialSheet(s.m, s.skills, s.projects, onDismiss = { editing = false }) { vm.update(it) }
    if (s != null && (newNote || note != null)) {
        val n = note
        NoteSheet(
            s.m.id, n, s.m.pageCount, null, onDismiss = { newNote = false; note = null },
            onDelete = n?.let { { vm.deleteNote(it); note = null } },
        ) { vm.saveNote(it) }
    }
}

@Composable
private fun LinkChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.clip(shape).background(C.GrowTint).border(1.dp, C.Grow.copy(alpha = 0.35f), shape).press(onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
    ) { Text("$text ›", style = T.small.copy(fontWeight = FontWeight.SemiBold), color = C.Grow, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

private val NOTE_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@Composable
private fun NoteRow(n: MaterialNote, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(C.Graphite1).border(1.dp, C.Hairline, shape).press(onClick = onEdit).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val d = java.time.Instant.ofEpochMilli(n.ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        Text(
            listOfNotNull(n.kind.uppercase(), n.page?.let { "P. ${it + 1}" }, NOTE_DATE.format(d).uppercase()).joinToString(" · "),
            style = T.label, color = C.Grow,
        )
        Text(n.text, style = T.bodyR, color = C.Ink)
    }
}

fun toast(ctx: android.content.Context, text: String) = android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()

/** Cross-tab routes are registered by other Grow sections; never crash if one isn't there. */
fun safeNavigate(nav: androidx.navigation.NavHostController, ctx: android.content.Context, route: String) {
    try { nav.navigate(route) } catch (e: IllegalArgumentException) { toast(ctx, "Not available yet") }
}
