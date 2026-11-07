package et.tibeb.examprep.ui.screens

import androidx.compose.ui.res.stringResource
import et.tibeb.examprep.R
import et.tibeb.examprep.ui.DOC_TYPE
import et.tibeb.examprep.ui.gradeShort
import et.tibeb.examprep.ui.label
import et.tibeb.examprep.ui.subjectName
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
                Text(stringResource(R.string.tab_library), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
                Text(stringResource(R.string.lib_intro),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Only preparation errors (e.g. file too large) show here; upload progress lives on each book.
                jobs.filterKeys { it.startsWith("import") }.values.mapNotNull { it.error }.forEach { err ->
                    Spacer(Modifier.height(8.dp)); ErrorText(err)
                }
            }
            if (docs.isEmpty()) item { EmptyState(stringResource(R.string.lib_empty_title), stringResource(R.string.lib_empty_body)) }
            items(docs, key = { it.id }) { d ->
                val chunks by app.repo.dao.chunkCount(d.id).collectAsState(0)
                val questions by app.repo.dao.questionCount(d.id).collectAsState(0)
                TCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(d.subject)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text("${subjectName(d.subject)} · ${stringResource(R.string.grade_long, d.grade)} · ${label(DOC_TYPE, d.docType)}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { confirmDelete = d.id }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.lib_delete_desc, d.title)) }
                    }
                    when (d.status) {
                        "queued" -> Column(Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.lib_queued), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                            if (d.error.isNotBlank()) Text(stringResource(R.string.lib_last_error, d.error), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        "processing" -> Busy(stringResource(R.string.lib_reading_now))
                        "failed" -> Column(Modifier.padding(top = 8.dp)) {
                            ErrorText(d.error.ifBlank { stringResource(R.string.lib_failed) })
                            TextButton(onClick = { app.repo.retryImport(d.id) }) { Text(stringResource(R.string.lib_retry)) }
                        }
                        else -> Text(listOfNotNull(
                            stringResource(R.string.lib_sections, chunks).takeIf { chunks > 0 }, stringResource(R.string.lib_questions, questions).takeIf { questions > 0 },
                        ).joinToString(" · ").ifBlank { stringResource(R.string.lib_saved) } + ". ${d.summary}", style = MaterialTheme.typography.bodyMedium, maxLines = 4,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { pick.launch(arrayOf("application/pdf", "image/*")) },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.lib_upload)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    picked?.let { (uri, mime) ->
        ImportDialog(defaultTitle = remember(uri) { ctx.displayName(uri) }, defaultGrade = app.prefs.settings.value.grade,
            onDismiss = { picked = null }) { title, subject, grade, type ->
            picked = null
            app.repo.run("import:$uri", title) { app.repo.queueImport(uri, mime, title, subject, grade, type) }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.lib_delete_title)) },
            text = { Text(stringResource(R.string.lib_delete_body)) },
            confirmButton = { TextButton(onClick = { scope.launch { app.repo.deleteDocument(id) }; confirmDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } })
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
        title = { Text(stringResource(R.string.lib_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(200) }, label = { Text(stringResource(R.string.lib_field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.label_subject), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(SUBJECTS, setOf(subject), { subjectName(it) }) { subject = it }
                Text(stringResource(R.string.label_grade), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(9, 10, 11, 12), setOf(grade), { gradeShort(it) }) { grade = it }
                Text(stringResource(R.string.label_type), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(DOC_TYPE.keys.toList(), setOf(type), { label(DOC_TYPE, it) }) { type = it }
                Text(stringResource(R.string.lib_limit),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onImport(title, subject, grade, type) }) { Text(stringResource(R.string.lib_upload)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
