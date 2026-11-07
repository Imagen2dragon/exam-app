package et.tibeb.examprep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import et.tibeb.examprep.R
import et.tibeb.examprep.data.Question
import et.tibeb.examprep.net.json
import et.tibeb.examprep.ui.EmptyState
import et.tibeb.examprep.ui.ErrorText
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.SubjectDot
import et.tibeb.examprep.ui.TCard
import et.tibeb.examprep.ui.math
import et.tibeb.examprep.ui.subjectName

/** Mistake notebook: wrong answers grouped by subject, each subject retryable as an offline exam. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakesScreen(nav: NavHostController) {
    val app = LocalApp.current
    val mistakes by app.repo.dao.mistakes().collectAsState(null)
    val job = app.repo.jobs.collectAsState().value["mistakes"]
    val bySubject = mistakes.orEmpty().groupBy { it.subject }

    LaunchedEffect(job?.resultId) {
        job?.resultId?.let { app.repo.clearJob("mistakes"); nav.navigate("exam/$it") }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.mistakes_title)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(stringResource(R.string.mistakes_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                job?.error?.let { ErrorText(it) }
            }
            if (mistakes != null && bySubject.isEmpty()) item {
                EmptyState(stringResource(R.string.mistakes_empty_title), stringResource(R.string.mistakes_empty_body))
            }
            bySubject.forEach { (subject, qs) ->
                item(key = "h-$subject") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        SubjectDot(subject); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(subjectName(subject), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.mistakes_to_fix, qs.size), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(enabled = job?.running != true, onClick = {
                            app.repo.run("mistakes", subject) { app.repo.createMistakeExam(subject) }
                        }) { Text(stringResource(R.string.mistakes_retry, minOf(qs.size, 30))) }
                    }
                }
                items(qs, key = { it.id }) { MistakeItem(it) { nav.navigate("explain/${it.id}") } }
            }
        }
    }
}

@Composable
private fun MistakeItem(q: Question, onExplain: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    TCard(onClick = { open = !open }) {
        Text(q.topic, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(math(q.stem), style = MaterialTheme.typography.bodyLarge, maxLines = if (open) 20 else 3)
        if (open) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            runCatching { json.decodeFromString<List<String>>(q.optionsJson) }.getOrDefault(emptyList())
                .forEachIndexed { i, o -> Text(math("${'A' + i}. $o"), style = MaterialTheme.typography.bodyMedium) }
            Text(math(stringResource(R.string.res_correct, q.answer)), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            if (q.explanation.isNotBlank()) Text(math(q.explanation), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.OutlinedButton(onClick = onExplain) { Text(stringResource(R.string.explain_button)) }
        }
    }
}
