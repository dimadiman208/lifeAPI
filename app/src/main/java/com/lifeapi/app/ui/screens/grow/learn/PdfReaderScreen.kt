package com.lifeapi.app.ui.screens.grow.learn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifeapi.app.data.Material
import com.lifeapi.app.data.grow3.learn.PdfStore
import com.lifeapi.app.ui.LocalNav
import com.lifeapi.app.ui.components.LifeCard
import com.lifeapi.app.ui.components.PillButton
import com.lifeapi.app.ui.components.press
import com.lifeapi.app.ui.screens.today.DetailTop
import com.lifeapi.app.ui.theme.C
import com.lifeapi.app.ui.theme.T
import com.lifeapi.app.vm.grow3.learn.PdfReaderVM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Minimal PDF reader on Android's PdfRenderer: one renderer per document, page access serialised by a mutex
 * (PdfRenderer allows one open page at a time), rendered bitmaps kept in a small LRU cache.
 */
private class PdfDoc(private val pfd: ParcelFileDescriptor) {
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    val count = renderer.pageCount
    /** width/height ratio per page (filled on open). */
    val ratios = FloatArray(count) { 0.7071f }
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    @Volatile var closed = false

    suspend fun measure() = lock.withLock {
        for (i in 0 until count) {
            if (closed) return@withLock
            renderer.openPage(i).use { p -> if (p.height > 0) ratios[i] = p.width.toFloat() / p.height }
        }
    }

    suspend fun render(page: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed || page !in 0 until count || widthPx <= 0) return@withLock null
        val key = "$page@$widthPx"
        cache.get(key)?.let { return@withLock it }
        renderer.openPage(page).use { p ->
            val w = widthPx.coerceAtMost(2600)
            val h = (w * p.height.toFloat() / p.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AColor.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache.put(key, bmp)
            bmp
        }
    }

    fun close() {
        closed = true
        runCatching { renderer.close() }; runCatching { pfd.close() }; cache.evictAll()
    }
}

private sealed class Load {
    data object Loading : Load()
    data class Ok(val doc: PdfDoc) : Load()
    data class Err(val msg: String, val canRepick: Boolean) : Load()
}

private suspend fun openDoc(ctx: Context, m: Material): Load = withContext(Dispatchers.IO) {
    val uri = m.fileUri ?: return@withContext Load.Err("This material has no file.", true)
    val pfd = try { PdfStore.open(ctx, uri) } catch (e: Exception) { return@withContext Load.Err(PdfStore.OpenError.NoAccess().message!!, true) }
    try {
        val doc = PdfDoc(pfd)
        doc.measure()
        Load.Ok(doc)
    } catch (e: SecurityException) {
        runCatching { pfd.close() }; Load.Err(PdfStore.OpenError.Locked().message!!, false)
    } catch (e: Exception) {
        runCatching { pfd.close() }; Load.Err(PdfStore.OpenError.Broken().message!!, true)
    }
}

@OptIn(FlowPreview::class)
@Composable
fun PdfReaderScreen(id: Long, startPage: Int) {
    val vm: PdfReaderVM = viewModel(key = "pdf-$id")
    val m by vm.material(id).collectAsStateWithLifecycle(null)
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val mat = m
    var reloadKey by remember { mutableStateOf(0) }
    val load by produceState<Load>(Load.Loading, mat?.fileUri, reloadKey) {
        value = Load.Loading
        if (mat != null) value = openDoc(ctx, mat)
    }
    DisposableEffect(load) {
        val l = load
        onDispose { if (l is Load.Ok) l.doc.close() }
    }
    val repick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && mat != null) vm.replaceFile(mat, uri) { reloadKey++ }
    }
    var noteFor by remember { mutableStateOf<Int?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().background(C.Graphite0)) {
        val l = load
        // a fresh list state per opened document, so the saved page is restored once the file is ready
        val listState = remember(l) {
            androidx.compose.foundation.lazy.LazyListState(
                if (l is Load.Ok) (if (startPage >= 0) startPage else mat?.progressPage ?: 0).coerceIn(0, (l.doc.count - 1).coerceAtLeast(0)) else 0,
            )
        }
        val current = listState.firstVisibleItemIndex
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = top + 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailTop(mat?.title ?: "PDF", onBack = { nav.popBackStack() }) {
                if (l is Load.Ok) Text("${current + 1} / ${l.doc.count}", style = T.mono, color = C.InkMuted)
            }
            if (l is Load.Ok) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(1f to "Fit", 1.5f to "1.5×", 2f to "2×").forEach { (z, label) -> SmallFilterChip(label, zoom == z) { zoom = z } }
                Box(Modifier.weight(1f))
                SmallFilterChip("+ Note on p. ${current + 1}", false) { noteFor = current }
            }
        }
        when (l) {
            Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Opening…", style = T.small, color = C.InkSubtle) }
            is Load.Err -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                LifeCard(Modifier.fillMaxWidth()) {
                    Text("Can't open this PDF", style = T.section, color = C.Ink)
                    Text(l.msg, style = T.small, color = C.InkMuted)
                    if (l.canRepick) PillButton("Pick the file again", C.Grow, C.Graphite0, Modifier.fillMaxWidth(), height = 44.dp, radius = 14.dp) {
                        repick.launch(arrayOf("application/pdf"))
                    }
                }
            }
            is Load.Ok -> {
                val doc = l.doc
                LaunchedEffect(doc) {
                    vm.opened(id)
                    snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().debounce(700)
                        .collect { vm.progress(id, it, doc.count) }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val pageW = maxWidth * zoom
                    val pagePx = with(LocalDensity.current) { pageW.roundToPx() }
                    Box(Modifier.fillMaxSize().then(if (zoom > 1f) Modifier.horizontalScroll(rememberScrollState()) else Modifier)) {
                        LazyColumn(
                            Modifier.requiredWidth(pageW).fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(bottom = bottom + 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(doc.count, key = { it }) { i -> PdfPage(doc, i, pagePx) }
                        }
                    }
                }
            }
        }
    }

    val ok = load as? Load.Ok
    if (noteFor != null && mat != null) {
        NoteSheet(mat.id, null, ok?.doc?.count ?: mat.pageCount, noteFor, onDismiss = { noteFor = null }) { vm.saveNote(it) }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int) {
    val bmp by produceState<Bitmap?>(null, doc, index, widthPx) {
        value = withContext(Dispatchers.IO) { runCatching { doc.render(index, widthPx) }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(doc.ratios[index]).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth)
        else Text("${index + 1}", style = T.small, color = C.InkFaint)
    }
}

