package et.tibeb.examprep.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import et.tibeb.examprep.R
import et.tibeb.examprep.data.MAX_PAGES
import et.tibeb.examprep.ui.Busy
import et.tibeb.examprep.ui.EmptyState
import et.tibeb.examprep.ui.ErrorText
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.util.compressedJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds a chapter from photos of its pages. Pages are compressed as they arrive (~300 KB each)
 * and kept in app-private storage. State survives the camera app pushing Tibeb out of memory,
 * which is common on low-RAM phones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(nav: NavHostController, initialPages: List<String> = emptyList()) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = rememberSaveable { System.currentTimeMillis().toString() }
    val dir = remember { File(ctx.filesDir, "staging/$session").apply { mkdirs() } }
    var pages by rememberSaveable { mutableStateOf(initialPages) }     // absolute paths, in reading order
    var capture by rememberSaveable { mutableStateOf<String?>(null) }      // file the camera is writing to
    var busy by remember { mutableStateOf(false) }
    var limitHit by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun addPages(sources: List<Uri>, afterwards: () -> Unit = {}) {
        val room = MAX_PAGES - pages.size
        limitHit = sources.size > room
        if (room <= 0) { afterwards(); return }
        busy = true
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                sources.take(room).mapNotNull { uri ->
                    runCatching {
                        File(dir, "p_${System.nanoTime()}.jpg").apply { writeBytes(ctx.compressedJpeg(uri, maxSide = 1600)) }.absolutePath
                    }.getOrNull()
                }
            }
            pages = pages + added
            busy = false
            afterwards()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val raw = capture?.let(::File)
        capture = null
        if (ok && raw != null && raw.length() > 0) {
            addPages(listOf(Uri.fromFile(raw))) { raw.delete() }  // raw camera file removed once compressed
        } else raw?.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PAGES)) { uris -> addPages(uris) }

    fun takePhoto() {
        if (pages.size >= MAX_PAGES) { limitHit = true; return }
        val raw = File(dir, "raw_${System.nanoTime()}.jpg")
        capture = raw.absolutePath
        camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", raw))
    }

    fun discard() { dir.deleteRecursively(); nav.popBackStack() }
    BackHandler { if (pages.isEmpty()) discard() else confirmDiscard = true }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.pages_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.pages_count, pages.size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                IconButton(onClick = { if (pages.isEmpty()) discard() else confirmDiscard = true }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Text(stringResource(R.string.pages_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        if (limitHit) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { ErrorText(stringResource(R.string.pages_limit)) }

        Box(Modifier.weight(1f)) {
            if (pages.isEmpty() && !busy) EmptyState(stringResource(R.string.pages_title), stringResource(R.string.pages_empty))
            LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(pages, key = { _, p -> p }) { i, path ->
                    PageTile(path, i + 1,
                        onEarlier = if (i > 0) ({ pages = pages.toMutableList().apply { add(i - 1, removeAt(i)) } }) else null,
                        onLater = if (i < pages.lastIndex) ({ pages = pages.toMutableList().apply { add(i + 1, removeAt(i)) } }) else null,
                        onRemove = { File(path).delete(); pages = pages - path; limitHit = false })
                }
            }
        }
        if (busy) Box(Modifier.padding(horizontal = 16.dp)) { Busy("…") }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = ::takePhoto, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(stringResource(R.string.ex_take_photo))
            }
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.pages_gallery)) }
        }
        Button(onClick = { naming = true }, enabled = pages.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).heightIn(min = 56.dp)) {
            Text(stringResource(R.string.pages_upload), style = MaterialTheme.typography.titleMedium)
        }
    }

    if (naming) ImportDialog(defaultTitle = "", defaultGrade = app.prefs.settings.value.grade, onDismiss = { naming = false }) { title, subject, grade, type ->
        naming = false
        val files = pages.map(::File)
        app.repo.run("import:pages:$session", title) {
            app.repo.queuePages(files, title, subject, grade, type).also { dir.deleteRecursively() }
        }
        nav.popBackStack()
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.pages_discard_title)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; discard() }) { Text(stringResource(R.string.pages_discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PageTile(path: String, number: Int, onEarlier: (() -> Unit)?, onLater: (() -> Unit)?, onRemove: () -> Unit) {
    val bmp = remember(path) {
        runCatching {
            // Decode at ~240 px wide: sharp on screen, tiny in memory (30 tiles stay under ~10 MB).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 240) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f)) {
                bmp?.let { Image(it, contentDescription = stringResource(R.string.page_n, number), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(6.dp)) {
                    Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Row {
                IconButton(onClick = { onEarlier?.invoke() }, enabled = onEarlier != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.page_earlier, number))
                }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.page_remove, number)) }
                IconButton(onClick = { onLater?.invoke() }, enabled = onLater != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.page_later, number))
                }
            }
        }
    }
}
