package dev.schoolgrades.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.schoolgrades.PdfExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data class Ready(val file: File) : PdfLoad
    data class Failed(val message: String) : PdfLoad
}

/**
 * Downloads the report with the app's own session (OkHttp), then renders it with Android's
 * built-in PdfRenderer. A WebView can't show PDFs, which is why the old viewer stayed blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentId: Long,
    filename: String,
    download: suspend (Long) -> File,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf<PdfLoad>(PdfLoad.Loading) }

    LaunchedEffect(documentId, attempt) {
        load = PdfLoad.Loading
        load = try {
            PdfLoad.Ready(download(documentId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PdfLoad.Failed(e.message ?: "Downloaden mislukt")
        }
    }

    BackHandler(onBack = onDismiss)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(filename, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Sluiten")
                    }
                },
                actions = {
                    val ready = load as? PdfLoad.Ready
                    if (ready != null) {
                        TextButton(onClick = {
                            if (PdfExport.saveToDownloads(context, ready.file, filename)) {
                                Toast.makeText(context, "Opgeslagen in Downloads", Toast.LENGTH_SHORT).show()
                            } else {
                                // Android 9 and older (or a failed save): let the user pick where it goes.
                                PdfExport.share(context, ready.file, filename)
                            }
                        }) { Text("Opslaan") }
                        TextButton(onClick = { PdfExport.share(context, ready.file, filename) }) { Text("Delen") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = load) {
                PdfLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is PdfLoad.Failed -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { attempt++ }) { Text("Opnieuw proberen") }
                }
                is PdfLoad.Ready -> PdfPages(state.file)
            }
        }
    }
}

@Composable
private fun PdfPages(file: File) {
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    if (renderer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Kan deze PDF niet openen.", color = MaterialTheme.colorScheme.error)
        }
        return
    }
    // PdfRenderer only allows one open page at a time, so renders are serialised with a mutex.
    val mutex = remember(renderer) { Mutex() }
    DisposableEffect(renderer) {
        onDispose { runBlocking { mutex.withLock { renderer.close() } } }
    }
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val targetWidth = (widthPx * 1.5f).toInt().coerceIn(600, 2000)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = renderer.pageCount) { index -> PdfPage(renderer, mutex, index, targetWidth) }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, mutex: Mutex, index: Int, targetWidth: Int) {
    val bitmap by produceState<Bitmap?>(null, renderer, index, targetWidth) {
        value = withContext(Dispatchers.IO) {
            mutex.withLock {
                val page = renderer.openPage(index)
                try {
                    val height = (targetWidth * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    page.close()
                }
            }
        }
    }
    val bmp = bitmap
    if (bmp == null) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.707f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Pagina ${index + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}
