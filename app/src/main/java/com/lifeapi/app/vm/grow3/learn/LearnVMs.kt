package com.lifeapi.app.vm.grow3.learn

import android.app.Application
import android.net.Uri
import com.lifeapi.app.data.Days
import com.lifeapi.app.data.Material
import com.lifeapi.app.data.MaterialNote
import com.lifeapi.app.data.Project
import com.lifeapi.app.data.Skill
import com.lifeapi.app.data.grow3.learn.LearnLogic
import com.lifeapi.app.data.grow3.learn.LibraryFilter
import com.lifeapi.app.data.grow3.learn.PdfStore
import com.lifeapi.app.vm.BaseVM
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

data class LearnState(
    val all: List<Material>,
    val filter: LibraryFilter,
    val results: List<Material>,
    val continueLearning: List<Material>,
    val recent: List<Material>,
    val tags: List<String>,
    val skills: List<Skill>,
    val projects: List<Project>,
)

/** Shared: save with dedupe (same url or same file = same material). Returns the saved id. */
internal suspend fun saveMaterial(learn: com.lifeapi.app.data.LearnDao, m: Material): Pair<Long, Boolean> {
    if (m.id == 0L) {
        learn.findDuplicate(m.url, m.fileUri)?.let { return it.id to true }
    }
    val id = learn.upsertMaterial(m.copy(addedTs = if (m.addedTs == 0L) Days.now() else m.addedTs))
    return (if (m.id != 0L) m.id else id) to false
}

class LearnVM(app: Application) : BaseVM(app) {
    private val learn = repo.db.learn()
    private val growth = repo.db.growth()
    private val projectsDao = repo.db.projects()
    val filter = MutableStateFlow(LibraryFilter())

    val state = combine(learn.materials(), filter, growth.skills(), projectsDao.allProjects()) { all, f, skills, projects ->
        val cont = LearnLogic.continueLearning(all)
        LearnState(
            all = all, filter = f, results = LearnLogic.apply(all, f), continueLearning = cont,
            recent = LearnLogic.recentlyAdded(all, exclude = cont.map { it.id }.toSet()),
            tags = LearnLogic.allTags(all), skills = skills, projects = projects,
        )
    }.state()

    init {
        // remove private PDF copies of materials that no longer exist
        launch { PdfStore.cleanup(getApplication(), learn.materials().first().mapNotNull { it.fileUri }) }
    }

    fun setFilter(f: LibraryFilter) { filter.value = f }

    /** Saves (or finds the existing duplicate) and reports the id + whether it already existed. */
    fun save(m: Material, onDone: (Long, Boolean) -> Unit) = launch {
        val (id, dup) = saveMaterial(learn, m)
        c.snack(if (dup) "Already in your library — opened it" else "Added to library")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(id, dup) }
    }

    fun toggleFavorite(m: Material) = launch { learn.upsertMaterial(m.copy(favorite = !m.favorite)) }

    /** SAF pick → persisted permission + private copy + page count; [onPicked] gets a prefilled draft. */
    fun importPdf(uri: Uri, onPicked: (Material) -> Unit) = launch {
        val p = runCatching { PdfStore.import(getApplication(), uri) }.getOrElse {
            c.snack("Couldn't read that file"); return@launch
        }
        val draft = Material(
            title = LearnLogic.titleFromFileName(p.name), kind = "PDF", fileUri = p.uri, fileName = p.name, pageCount = p.pages,
        )
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onPicked(draft) }
    }
}

data class MaterialState(
    val m: Material,
    val notes: List<MaterialNote>,
    val skill: Skill?,
    val project: Project?,
    val skills: List<Skill>,
    val projects: List<Project>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MaterialVM(app: Application) : BaseVM(app) {
    private val learn = repo.db.learn()
    private val growth = repo.db.growth()
    private val projectsDao = repo.db.projects()
    private val id = MutableStateFlow<Long?>(null)

    fun bind(materialId: Long) { if (id.value != materialId) id.value = materialId }

    val state = id.filterNotNull().flatMapLatest { mid ->
        combine(learn.material(mid), learn.notesOf(mid), growth.skills(), projectsDao.allProjects()) { m, notes, skills, projects ->
            m?.let { MaterialState(it, notes, skills.firstOrNull { s -> s.id == it.skillId }, projects.firstOrNull { p -> p.id == it.projectId }, skills, projects) }
        }
    }.state()

    /** Emits once the material is gone (deleted) so the screen can close. */
    val exists = id.filterNotNull().flatMapLatest { learn.material(it) }

    fun update(m: Material) = launch { learn.upsertMaterial(m) }

    fun setStatus(m: Material, status: String) = act("Marked ${status.lowercase()}") {
        val before = m
        learn.upsertMaterial(m.copy(status = status, completedTs = if (status == "Completed") (m.completedTs ?: Days.now()) else m.completedTs))
        suspend { learn.upsertMaterial(before) }
    }

    fun toggleFavorite(m: Material) = launch { learn.upsertMaterial(m.copy(favorite = !m.favorite)) }

    fun markOpened(m: Material) = launch { learn.markOpened(m.id, Days.now()) }

    /** Deletes the material and its notes (links in Growth/Projects are cleared). Undo restores material + notes. */
    fun delete(m: Material) = act("“${m.title}” deleted") {
        val notes = learn.notesOf(m.id).first()
        learn.deleteMaterialCascade(m.id)
        suspend {
            learn.upsertMaterial(m)
            notes.forEach { learn.upsertNote(it) }
        }
    }

    fun saveNote(n: MaterialNote) = launch { learn.upsertNote(if (n.id == 0L) n.copy(ts = Days.now()) else n) }

    fun deleteNote(n: MaterialNote) = act("Note deleted") {
        learn.deleteNote(n)
        suspend { learn.upsertNote(n) }
    }

    /** The original file was lost: pick it again (same material, new uri). */
    fun replaceFile(m: Material, uri: Uri) = launch {
        val p = runCatching { PdfStore.import(getApplication(), uri) }.getOrElse { c.snack("Couldn't read that file"); return@launch }
        learn.upsertMaterial(m.copy(fileUri = p.uri, fileName = p.name, pageCount = if (p.pages > 0) p.pages else m.pageCount))
        c.snack("File re-linked")
    }

}

/** Reader: progress + open marking. */
class PdfReaderVM(app: Application) : BaseVM(app) {
    private val learn = repo.db.learn()
    fun material(id: Long) = learn.material(id)
    fun opened(id: Long) = launch { learn.markOpened(id, Days.now()) }
    fun progress(id: Long, page: Int, count: Int) = launch { learn.saveProgress(id, page, count, Days.now()) }
    fun saveNote(n: MaterialNote) = launch { learn.upsertNote(n.copy(ts = Days.now())) }
    fun replaceFile(m: Material, uri: Uri, done: () -> Unit) = launch {
        val p = runCatching { PdfStore.import(getApplication(), uri) }.getOrElse { c.snack("Couldn't read that file"); return@launch }
        learn.upsertMaterial(m.copy(fileUri = p.uri, fileName = p.name, pageCount = if (p.pages > 0) p.pages else m.pageCount))
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { done() }
    }
}
