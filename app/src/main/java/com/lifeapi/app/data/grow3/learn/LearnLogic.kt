package com.lifeapi.app.data.grow3.learn

import com.lifeapi.app.data.Material
import com.lifeapi.app.data.grow3.MaterialKinds

/** Library sort order. */
enum class LearnSort { ADDED, OPENED, TITLE }

/** What the Learn tab is currently showing. Empty values = no filter. */
data class LibraryFilter(
    val query: String = "",
    val status: String? = null,
    val kind: String? = null,
    val tag: String? = null,
    val favoritesOnly: Boolean = false,
    val sort: LearnSort = LearnSort.ADDED,
) {
    /** True when anything narrows the list (then the "Continue / Recently added" sections are hidden). */
    val active: Boolean get() = query.isNotBlank() || status != null || kind != null || tag != null || favoritesOnly
}

/** Pure library logic — filtering, sections, labels. No Android dependencies (unit-tested). */
object LearnLogic {

    /** Search is case-insensitive over title, description, tags, url/domain and file name; every word must match. */
    fun matches(m: Material, query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = listOf(m.title, m.description, m.tags, m.url.orEmpty(), m.fileName, m.kind).joinToString(" ").lowercase()
        return words.all { w -> hay.contains(w.removePrefix("#")) }
    }

    /**
     * Applies [f] to [all]. Archived items are hidden unless the status filter asks for them explicitly
     * (they are "put away", not deleted).
     */
    fun apply(all: List<Material>, f: LibraryFilter): List<Material> {
        val out = all.filter { m ->
            (f.status?.let { m.status == it } ?: (m.status != "Archived")) &&
                (f.kind == null || m.kind == f.kind) &&
                (f.tag == null || f.tag in MaterialKinds.tagList(m.tags)) &&
                (!f.favoritesOnly || m.favorite) &&
                matches(m, f.query)
        }
        return when (f.sort) {
            LearnSort.ADDED -> out.sortedWith(compareByDescending<Material> { it.addedTs }.thenByDescending { it.id })
            LearnSort.OPENED -> out.sortedWith(compareByDescending<Material> { it.lastOpenedTs }.thenByDescending { it.addedTs })
            LearnSort.TITLE -> out.sortedBy { it.title.lowercase() }
        }
    }

    /** "Continue learning": in progress, most recently opened first. */
    fun continueLearning(all: List<Material>, limit: Int = 3): List<Material> =
        all.filter { it.status == "In Progress" }.sortedByDescending { maxOf(it.lastOpenedTs, it.addedTs) }.take(limit)

    /** "Recently added": newest non-archived items not already shown in [exclude]. */
    fun recentlyAdded(all: List<Material>, exclude: Set<Long> = emptySet(), limit: Int = 5): List<Material> =
        all.filter { it.status != "Archived" && it.id !in exclude }.sortedByDescending { it.addedTs }.take(limit)

    /** Tags by how often they're used (most used first). */
    fun allTags(all: List<Material>): List<String> =
        all.flatMap { MaterialKinds.tagList(it.tags) }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }

    /** Host without "www." — "https://www.postgresql.org/docs/x" → "postgresql.org". */
    fun domainOf(url: String?): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        val host = u.substringAfter("://", u).substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        return host.removePrefix("www.").lowercase().ifBlank { null }
    }

    /** Where it lives, for the card: domain for links, file name for PDFs. */
    fun source(m: Material): String = when {
        m.fileUri != null -> m.fileName.ifBlank { "PDF file" }
        else -> domainOf(m.url) ?: ""
    }

    /** "p. 12 / 240" for PDFs that have been opened; null otherwise. */
    fun progressLabel(m: Material): String? =
        if (m.pageCount > 0) "p. ${(m.progressPage + 1).coerceAtMost(m.pageCount)} / ${m.pageCount}" else null

    /** 0..1 reading progress for PDFs; null when unknown. */
    fun progressFraction(m: Material): Float? =
        if (m.pageCount > 0) ((m.progressPage + 1).toFloat() / m.pageCount).coerceIn(0f, 1f) else null

    /** Title for a newly picked PDF: file name without extension, separators → spaces. */
    fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ").trim()
            .ifBlank { "PDF document" }
}

/**
 * Extension point for future AI features (summary, questions over a PDF, key ideas, review cards).
 * Intentionally not implemented and not shown in the UI — the library works fully offline without it.
 */
interface MaterialInsights {
    suspend fun summarize(material: Material, text: String): String
    suspend fun keyIdeas(material: Material, text: String): List<String>
    suspend fun reviewCards(material: Material, text: String): List<Pair<String, String>>
}
