package com.lifeapi.app.data.grow3.learn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * PDFs in the Learn library.
 *
 * Storage rule: the material keeps the SAF `content://` uri (with a persisted read permission) as its identity —
 * never a raw file path. As a fallback we also keep a private copy in `filesDir/learn/<sha1(uri)>.pdf`, so the
 * document stays readable if the original file is moved/deleted or the permission is revoked. The copy's name is
 * derived from the uri, so no extra column is needed. Orphan copies are cleaned up by [cleanup].
 */
object PdfStore {
    data class Picked(val uri: String, val name: String, val pages: Int)

    sealed class OpenError(msg: String) : Exception(msg) {
        class NoAccess : OpenError("The file isn't accessible any more (moved, deleted or permission revoked).")
        class Locked : OpenError("This PDF is password-protected — LifeAPI can't open it.")
        class Broken : OpenError("This file can't be read as a PDF.")
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "learn").apply { mkdirs() }

    fun copyFileFor(ctx: Context, uri: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir(ctx), "$h.pdf")
    }

    /** Takes the persistable permission, copies the file privately, reads name + page count. */
    suspend fun import(ctx: Context, uri: Uri): Picked = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        val copy = copyFileFor(ctx, uri.toString())
        runCatching { cr.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } } }
        val pages = runCatching { open(ctx, uri.toString()).use { pfd -> android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount } } }.getOrDefault(0)
        Picked(uri.toString(), name, pages)
    }

    /** Opens the document: original uri first, private copy as fallback. */
    fun open(ctx: Context, uri: String): ParcelFileDescriptor {
        runCatching { ctx.contentResolver.openFileDescriptor(Uri.parse(uri), "r") }.getOrNull()?.let { return it }
        val copy = copyFileFor(ctx, uri)
        if (copy.exists() && copy.length() > 0) return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        throw OpenError.NoAccess()
    }

    /** Deletes private copies no material points to any more. */
    suspend fun cleanup(ctx: Context, liveUris: Collection<String>) = withContext(Dispatchers.IO) {
        val keep = liveUris.map { copyFileFor(ctx, it).name }.toSet()
        dir(ctx).listFiles()?.filter { it.name.endsWith(".pdf") && it.name !in keep }?.forEach { it.delete() }
    }
}
