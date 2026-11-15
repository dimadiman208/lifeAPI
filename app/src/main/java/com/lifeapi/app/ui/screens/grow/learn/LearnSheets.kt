package com.lifeapi.app.ui.screens.grow.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lifeapi.app.data.Material
import com.lifeapi.app.data.MaterialNote
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.Skill
import com.lifeapi.app.data.grow3.MaterialKinds
import com.lifeapi.app.ui.components.ChipPicker
import com.lifeapi.app.ui.components.LifeField
import com.lifeapi.app.ui.screens.body.EntrySheet
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T

private const val NONE = "None"

/**
 * Add / edit a material. For a new link the type is detected from the URL (and can be changed); the title is
 * optional — a readable guess is made from the URL. PDFs arrive here prefilled from the file picker.
 */
@Composable
fun MaterialSheet(
    initial: Material,
    skills: List<Skill>,
    projects: List<Project>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (Material) -> Unit,
) {
    val isFile = initial.fileUri != null
    val isNew = initial.id == 0L
    var url by remember { mutableStateOf(initial.url.orEmpty()) }
    var title by remember { mutableStateOf(initial.title) }
    var kind by remember { mutableStateOf(initial.kind) }
    var kindTouched by remember { mutableStateOf(!isNew) }
    var desc by remember { mutableStateOf(initial.description) }
    var tags by remember { mutableStateOf(MaterialKinds.tagList(initial.tags).joinToString(", ")) }
    var status by remember { mutableStateOf(initial.status) }
    var fav by remember { mutableStateOf(initial.favorite) }
    var skillId by remember { mutableStateOf(initial.skillId) }
    var projectId by remember { mutableStateOf(initial.projectId) }

    val normUrl = if (isFile) null else MaterialKinds.normalizeUrl(url)
    if (!isFile && !kindTouched && normUrl != null) kind = MaterialKinds.detect(normUrl)
    val guess = normUrl?.let { MaterialKinds.titleFromUrl(it) }.orEmpty()
    val can = if (isFile) title.isNotBlank() else normUrl != null

    EntrySheet(
        if (isNew) (if (isFile) "Add PDF" else "Add link") else "Edit material", onDismiss,
        confirm = if (isNew) "Add to library" else "Save", hue = C.Grow, canConfirm = can,
        onDelete = onDelete,
        subtitle = if (isFile) initial.fileName + (if (initial.pageCount > 0) " · ${initial.pageCount} pages" else "") else null,
        onConfirm = {
            onSave(
                initial.copy(
                    url = normUrl ?: initial.url.takeIf { isFile },
                    title = title.trim().ifBlank { guess.ifBlank { initial.fileName } },
                    kind = if (isFile) MaterialKinds.PDF else kind,
                    description = desc.trim(),
                    tags = MaterialKinds.parseTags(tags),
                    status = status,
                    favorite = fav,
                    skillId = skillId,
                    projectId = projectId,
                ),
            )
            onDismiss()
        },
    ) {
        if (!isFile) {
            LifeField(url, { url = it }, "Link", keyboard = KeyboardType.Uri, placeholder = "https://…")
            if (url.isNotBlank() && normUrl == null) Text("That doesn't look like a web address", style = T.caption, color = C.Negative)
        }
        LifeField(title, { title = it }, "Title", placeholder = guess.ifBlank { "Optional — guessed from the link" })
        if (!isFile) {
            Text("Type", style = T.caption, color = C.InkMuted)
            ChipPicker(MaterialKinds.ALL.filter { it != MaterialKinds.PDF || kind == MaterialKinds.PDF }, kind, C.Grow) { kind = it; kindTouched = true }
        }
        LifeField(desc, { desc = it }, "Short description (optional)", singleLine = false)
        LifeField(tags, { tags = it }, "Tags", placeholder = "postgres, transactions")
        Text("Status", style = T.caption, color = C.InkMuted)
        ChipPicker(MaterialKinds.STATUSES, status, C.Grow) { status = it }
        if (skills.isNotEmpty()) {
            Text("Growth direction (optional)", style = T.caption, color = C.InkMuted)
            ChipPicker(listOf(NONE) + skills.map { it.name }, skills.firstOrNull { it.id == skillId }?.name ?: NONE, C.Grow) { pick ->
                skillId = skills.firstOrNull { it.name == pick }?.id
            }
        }
        if (projects.isNotEmpty()) {
            Text("Project (optional)", style = T.caption, color = C.InkMuted)
            ChipPicker(listOf(NONE) + projects.filter { it.status != "Archived" || it.id == projectId }.map { it.name },
                projects.firstOrNull { it.id == projectId }?.name ?: NONE, C.Grow) { pick ->
                projectId = projects.firstOrNull { it.name == pick }?.id
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("★ Favorite", style = T.body, color = C.Ink, modifier = Modifier.weight(1f))
            Switch(fav, { fav = it }, colors = SwitchDefaults.colors(checkedThumbColor = C.Graphite0, checkedTrackColor = C.Grow))
        }
    }
}

/** Add / edit a note. PDFs can pin a note to a page. */
@Composable
fun NoteSheet(
    materialId: Long,
    initial: MaterialNote?,
    pageCount: Int,
    defaultPage: Int?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (MaterialNote) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: "Idea") }
    var page by remember { mutableStateOf((initial?.page ?: defaultPage)?.let { (it + 1).toString() }.orEmpty()) }
    EntrySheet(
        if (initial == null) "New note" else "Edit note", onDismiss, hue = C.Grow, canConfirm = text.isNotBlank(), onDelete = onDelete,
        onConfirm = {
            val p = page.trim().toIntOrNull()?.takeIf { pageCount <= 0 || it in 1..pageCount }?.minus(1)
            onSave((initial ?: MaterialNote(materialId = materialId, ts = 0, text = "")).copy(text = text.trim(), kind = kind, page = p))
            onDismiss()
        },
    ) {
        ChipPicker(MaterialKinds.NOTE_KINDS, kind, C.Grow) { kind = it }
        LifeField(text, { text = it }, when (kind) {
            "Idea" -> "Main idea"
            "Example" -> "Useful example"
            "Learned" -> "What I learned"
            "Try" -> "What to try"
            else -> "My conclusion"
        }, singleLine = false)
        if (pageCount > 0) LifeField(page, { page = it.filter(Char::isDigit) }, "Page (optional)", keyboard = KeyboardType.Number, placeholder = "1–$pageCount")
    }
}
