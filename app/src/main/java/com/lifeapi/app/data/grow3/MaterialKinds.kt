package com.lifeapi.app.data.grow3

/** Learn library helpers: kind detection, tag parsing, statuses. */
object MaterialKinds {
    const val PDF = "PDF"; const val ARTICLE = "Article"; const val DOCS = "Docs"
    const val GITHUB = "GitHub"; const val YOUTUBE = "YouTube"; const val LINK = "Link"
    val ALL = listOf(PDF, ARTICLE, DOCS, GITHUB, YOUTUBE, LINK)

    val STATUSES = listOf("To Learn", "In Progress", "Completed", "Archived")
    val NOTE_KINDS = listOf("Idea", "Example", "Learned", "Try", "Conclusion")

    private val DOCS_HOSTS = Regex("""(^|\.)(docs\.|developer\.|developers\.|learn\.microsoft\.com|readthedocs\.io|postgresql\.org|kotlinlang\.org|android\.com|mozilla\.org|python\.org)""")

    /** Best guess from a URL (or a file name / mime type for local files). Users can still change it. */
    fun detect(url: String?, mime: String? = null, fileName: String? = null): String {
        if (mime == "application/pdf" || fileName?.lowercase()?.endsWith(".pdf") == true) return PDF
        val u = url?.trim()?.lowercase() ?: return LINK
        val host = Regex("""^[a-z]+://([^/:?#]+)""").find(u)?.groupValues?.get(1)?.removePrefix("www.") ?: u.substringBefore('/')
        val path = u.substringAfter(host, "").substringBefore('?')
        return when {
            host == "youtu.be" || host.endsWith("youtube.com") -> YOUTUBE
            host == "github.com" || host == "gist.github.com" -> GITHUB
            path.endsWith(".pdf") -> PDF
            DOCS_HOSTS.containsMatchIn(host) || path.contains("/docs/") || path.startsWith("/docs") || path.contains("/reference/") -> DOCS
            host.endsWith("medium.com") || host.endsWith("habr.com") || host.endsWith("dev.to") || host.endsWith("substack.com") || path.contains("/blog") || path.contains("/article") -> ARTICLE
            else -> LINK
        }
    }

    /** Adds https:// when missing; null if it doesn't look like a URL. */
    fun normalizeUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        val u = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) t else "https://$t"
        return u.takeIf { Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://[^/\s]+\.[^/\s]+""").containsMatchIn(it) }
    }

    /** "Kotlin, #coroutines , kotlin" → "kotlin,coroutines" */
    fun parseTags(raw: String): String =
        raw.split(',', ';', ' ').map { it.trim().removePrefix("#").lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    fun tagList(tags: String): List<String> = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Title guess for a URL when the user leaves it blank (no network fetch). */
    fun titleFromUrl(url: String): String {
        val noProto = url.substringAfter("://")
        val host = noProto.substringBefore('/').removePrefix("www.")
        val last = noProto.substringAfter('/', "").substringBefore('?').trimEnd('/').substringAfterLast('/')
        val pretty = last.replace('-', ' ').replace('_', ' ').removeSuffix(".pdf").removeSuffix(".html").trim()
        return if (pretty.isBlank()) host else pretty.replaceFirstChar { it.uppercase() } + " · " + host
    }
}
