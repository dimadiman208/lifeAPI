package com.lifeapi.app.ui.screens.grow.learn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Material
import com.lifeapi.app.data.grow3.MaterialKinds
import com.lifeapi.app.data.grow3.learn.LearnSort
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.EmptyCard
import com.lifeapi.app.ui.components.Ic
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.components.MonoLabel
import com.lifeapi.app.ui.screens.ScreenList
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.learn.LearnVM

/**
 * Grow → Learn: personal library of PDFs, docs, articles, videos and repos.
 * Search + status/type/tag filters + sort; "Continue learning" and "Recently added" when nothing is filtered.
 */
@Composable
fun LearnTab(header: LazyListScope.(subtitle: String) -> Unit) {
    val vm: LearnVM = viewModel()
    val st by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var draft by remember { mutableStateOf<Material?>(null) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri) { draft = it }
    }
    fun addLink() { draft = Material(title = "", kind = MaterialKinds.LINK, status = "To Learn") }
    fun addPdf() { pickPdf.launch(arrayOf("application/pdf")) }

    ScreenList(glow = C.Grow) {
        val s = st
        val total = s?.all?.count { it.status != "Archived" } ?: 0
        header(if (total == 0) "Library — PDFs, docs, articles, videos to study" else "$total in your library")
        if (s == null) return@ScreenList
        val f = s.filter

        if (s.all.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    "LEARN", C.Grow, "Your study library",
                    "Save docs, articles, YouTube talks, GitHub repos and PDFs you want to learn from. Read PDFs right here, keep notes, and turn what you learned into Growth entries.",
                    "Add a link", ::addLink, "Add a PDF", ::addPdf,
                )
            }
            return@ScreenList
        }

        item(key = "search") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LifeField(f.query, { vm.setFilter(f.copy(query = it)) }, "Search", placeholder = "Title, tag, site…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LearnIconAction("Link", Ic.Plus, Modifier.weight(1f), ::addLink)
                    LearnIconAction("PDF", Ic.Plus, Modifier.weight(1f), ::addPdf)
                }
            }
        }
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(MaterialKinds.STATUSES, f.status) { vm.setFilter(f.copy(status = it)) }
                ChipRow(MaterialKinds.ALL.filter { k -> s.all.any { it.kind == k } }, f.kind, allLabel = "Any type") { vm.setFilter(f.copy(kind = it)) }
                if (s.tags.isNotEmpty()) ChipRow(s.tags.map { "#$it" }, f.tag?.let { "#$it" }, allLabel = null) { vm.setFilter(f.copy(tag = it?.removePrefix("#"))) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallFilterChip("★ Favorites", f.favoritesOnly, C.Fuel) { vm.setFilter(f.copy(favoritesOnly = !f.favoritesOnly)) }
                    SmallFilterChip("Newest", f.sort == LearnSort.ADDED) { vm.setFilter(f.copy(sort = LearnSort.ADDED)) }
                    SmallFilterChip("Last opened", f.sort == LearnSort.OPENED) { vm.setFilter(f.copy(sort = LearnSort.OPENED)) }
                    SmallFilterChip("A–Z", f.sort == LearnSort.TITLE) { vm.setFilter(f.copy(sort = LearnSort.TITLE)) }
                }
            }
        }

        val open: (Material) -> Unit = { nav.navigate(LearnRoutes.material(it.id)) }
        if (!f.active) {
            if (s.continueLearning.isNotEmpty()) {
                item(key = "cont-h") { MonoLabel("CONTINUE LEARNING", C.Grow) }
                items(s.continueLearning, key = { "c${it.id}" }) { m -> MaterialCard(m, { open(m) }, { vm.toggleFavorite(m) }) }
            }
            if (s.recent.isNotEmpty()) {
                item(key = "rec-h") { MonoLabel("RECENTLY ADDED") }
                items(s.recent, key = { "r${it.id}" }) { m -> MaterialCard(m, { open(m) }, { vm.toggleFavorite(m) }) }
            }
            val shown = (s.continueLearning + s.recent).map { it.id }.toSet()
            val rest = s.results.filter { it.id !in shown }
            if (rest.isNotEmpty()) {
                item(key = "all-h") { MonoLabel("LIBRARY · ${s.results.size}") }
                items(rest, key = { "a${it.id}" }) { m -> MaterialCard(m, { open(m) }, { vm.toggleFavorite(m) }) }
            }
            val archived = s.all.count { it.status == "Archived" }
            if (archived > 0) item(key = "arch") {
                Text("$archived archived — filter by Archived to see them", style = T.caption, color = C.InkFaint, modifier = Modifier.padding(start = 4.dp))
            }
        } else {
            item(key = "res-h") { MonoLabel("${s.results.size} RESULT${if (s.results.size == 1) "" else "S"}") }
            if (s.results.isEmpty()) item(key = "none") {
                Text("Nothing matches. Clear a filter or search for something else.", style = T.small, color = C.InkMuted, modifier = Modifier.fillMaxWidth().padding(4.dp))
            }
            items(s.results, key = { "f${it.id}" }) { m -> MaterialCard(m, { open(m) }, { vm.toggleFavorite(m) }) }
        }
    }

    draft?.let { d ->
        val s = st
        MaterialSheet(d, s?.skills.orEmpty(), s?.projects.orEmpty(), onDismiss = { draft = null }) { m ->
            vm.save(m) { id, dup -> if (dup) nav.navigate(LearnRoutes.material(id)) }
        }
    }
}
