package com.lifeapi.app.data.grow3.learn

import com.lifeapi.app.data.Material
import com.lifeapi.app.data.grow3.MaterialKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnLogicTest {
    private fun m(id: Long, title: String, kind: String = "Link", status: String = "To Learn", tags: String = "", fav: Boolean = false,
                  added: Long = id, opened: Long = 0, url: String? = null, file: String = "", page: Int = 0, pages: Int = 0) =
        Material(id = id, title = title, kind = kind, url = url, fileName = file, status = status, tags = tags, favorite = fav,
            addedTs = added, lastOpenedTs = opened, progressPage = page, pageCount = pages, fileUri = if (file.isNotEmpty()) "content://x/$id" else null)

    private val lib = listOf(
        m(1, "PostgreSQL MVCC", "Docs", "In Progress", "postgres,db", url = "https://www.postgresql.org/docs/current/mvcc.html", opened = 50),
        m(2, "Kotlin Flow guide", "Article", tags = "kotlin", fav = true, url = "https://medium.com/x/flow"),
        m(3, "Old talk", "YouTube", "Archived", "kotlin", url = "https://youtu.be/abc"),
        m(4, "DDIA", "PDF", "In Progress", "db", file = "ddia.pdf", opened = 90, page = 41, pages = 600),
        m(5, "Compose samples", "GitHub", "Completed", "kotlin,compose", url = "https://github.com/android/compose-samples"),
    )

    @Test fun archivedHiddenUnlessAsked() {
        assertFalse(LearnLogic.apply(lib, LibraryFilter()).any { it.id == 3L })
        assertEquals(listOf(3L), LearnLogic.apply(lib, LibraryFilter(status = "Archived")).map { it.id })
    }

    @Test fun searchAllWordsAcrossFields() {
        assertEquals(listOf(1L), LearnLogic.apply(lib, LibraryFilter(query = "postgresql mvcc")).map { it.id })
        assertEquals(listOf(4L), LearnLogic.apply(lib, LibraryFilter(query = "ddia.pdf")).map { it.id })
        assertEquals(setOf(2L, 5L), LearnLogic.apply(lib, LibraryFilter(query = "#kotlin")).map { it.id }.toSet())
    }

    @Test fun filtersCombine() {
        assertEquals(listOf(2L), LearnLogic.apply(lib, LibraryFilter(tag = "kotlin", favoritesOnly = true)).map { it.id })
        assertEquals(listOf(4L), LearnLogic.apply(lib, LibraryFilter(kind = "PDF")).map { it.id })
    }

    @Test fun sortOrders() {
        assertEquals(listOf(5L, 4L, 2L, 1L), LearnLogic.apply(lib, LibraryFilter()).map { it.id })
        assertEquals(listOf(4L, 1L), LearnLogic.apply(lib, LibraryFilter(sort = LearnSort.OPENED)).map { it.id }.take(2))
        assertEquals("Compose samples", LearnLogic.apply(lib, LibraryFilter(sort = LearnSort.TITLE)).first().title)
    }

    @Test fun sections() {
        assertEquals(listOf(4L, 1L), LearnLogic.continueLearning(lib).map { it.id })
        assertEquals(listOf(5L, 2L), LearnLogic.recentlyAdded(lib, exclude = setOf(4L, 1L)).map { it.id })
        assertEquals("kotlin", LearnLogic.allTags(lib).first())
    }

    @Test fun labels() {
        assertEquals("postgresql.org", LearnLogic.domainOf("https://www.postgresql.org/docs/current/mvcc.html"))
        assertNull(LearnLogic.domainOf(""))
        assertEquals("ddia.pdf", LearnLogic.source(lib[3]))
        assertEquals("p. 42 / 600", LearnLogic.progressLabel(lib[3]))
        assertNull(LearnLogic.progressLabel(lib[0]))
        assertEquals("Designing data intensive", LearnLogic.titleFromFileName("Designing_data-intensive.pdf"))
    }

    @Test fun kindDetectionAndTitles() {
        assertEquals("YouTube", MaterialKinds.detect("https://www.youtube.com/watch?v=1"))
        assertEquals("GitHub", MaterialKinds.detect("https://github.com/a/b"))
        assertEquals("Docs", MaterialKinds.detect("https://www.postgresql.org/docs/current/mvcc.html"))
        assertEquals("PDF", MaterialKinds.detect("https://x.org/paper.pdf"))
        assertEquals("Article", MaterialKinds.detect("https://medium.com/@a/post"))
        assertEquals("https://kotlinlang.org", MaterialKinds.normalizeUrl("kotlinlang.org"))
        assertTrue(MaterialKinds.titleFromUrl("https://www.postgresql.org/docs/current/mvcc-intro.html").startsWith("Mvcc intro"))
    }
}
