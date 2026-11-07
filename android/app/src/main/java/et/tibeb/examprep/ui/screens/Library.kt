package et.tibeb.examprep.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import et.tibeb.examprep.data.SUBJECTS
import et.tibeb.examprep.ui.Busy
import et.tibeb.examprep.ui.ChoiceChips
import et.tibeb.examprep.ui.EmptyState
import et.tibeb.examprep.ui.ErrorText
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.SubjectDot
import et.tibeb.examprep.ui.TCard
import et.tibeb.examprep.util.displayName
import kotlinx.coroutines.launch

private val DOC_TYPES = listOf("textbook" to "Textbook", "teacher_guide" to "Teacher guide", "past_exam" to "Past national exam",
    "worksheet" to "Test / worksheet", "notes" to "Class notes")

@Composable
fun LibraryScreen() {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val docs by app.repo.dao.documents().collectAsState(emptyList())
    val jobs by app.repo.jobs.collectAsState()
    var picked by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picked = uri to (ctx.contentResolver.getType(uri) ?: "application/pdf")
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
                Text("Upload textbooks, past national exams and tests. Tibeb reads them once, keeps only the text, and studies from them offline.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                jobs.filterKeys { it.startsWith("import") }.values.forEach { j ->
                    Spacer(Modifier.height(8.dp))
                    if (j.running) Busy("Reading “${j.label}”… big books can take a few minutes")
                    j.error?.let { ErrorText(it) }
                }
            }
            if (docs.isEmpty()) item { EmptyState("Nothing here yet", "Tap Upload to add your first textbook or past exam (PDF or photo).") }
            items(docs, key = { it.id }) { d ->
                val chunks by app.repo.dao.chunkCount(d.id).collectAsState(0)
                TCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(d.subject)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text("${d.subject} · Grade ${d.grade} · ${DOC_TYPES.firstOrNull { it.first == d.docType }?.second ?: d.docType}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { confirmDelete = d.id }) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${d.title}") }
                    }
                    when (d.status) {
                        "processing" -> Busy("Processing…")
                        "failed" -> ErrorText(d.error.ifBlank { "Failed" })
                        else -> Text("$chunks study sections saved. ${d.summary}", style = MaterialTheme.typography.bodyMedium, maxLines = 4,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { pick.launch(arrayOf("application/pdf", "image/*")) },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Upload") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    picked?.let { (uri, mime) ->
        ImportDialog(defaultTitle = remember(uri) { ctx.displayName(uri) }, defaultGrade = app.prefs.settings.value.grade,
            onDismiss = { picked = null }) { title, subject, grade, type ->
            picked = null
            app.repo.run("import:$uri", title) { app.repo.importDocument(uri, mime, title, subject, grade, type) }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this book?") },
            text = { Text("Its study sections and saved past-exam questions are removed. Notes, flashcards and exam history stay.") },
            confirmButton = { TextButton(onClick = { scope.launch { app.repo.dao.deleteDocumentFully(id) }; confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ImportDialog(defaultTitle: String, defaultGrade: Int, onDismiss: () -> Unit, onImport: (String, String, Int, String) -> Unit) {
    var title by remember { mutableStateOf(defaultTitle) }
    var subject by remember { mutableStateOf("Mathematics") }
    var grade by remember { mutableIntStateOf(defaultGrade) }
    var type by remember { mutableStateOf("textbook") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to library") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(200) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Subject", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(SUBJECTS, setOf(subject), { it }) { subject = it }
                Text("Grade", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(9, 10, 11, 12), setOf(grade), { "G$it" }) { grade = it }
                Text("Type", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(DOC_TYPES.map { it.first }, setOf(type), { k -> DOC_TYPES.first { it.first == k }.second }) { type = it }
                Text("Max 20 MB. For a full textbook, upload one unit at a time for best results.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onImport(title, subject, grade, type) }) { Text("Upload") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
