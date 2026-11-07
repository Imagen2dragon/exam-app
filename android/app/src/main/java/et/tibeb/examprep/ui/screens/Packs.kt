package et.tibeb.examprep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import et.tibeb.examprep.R
import et.tibeb.examprep.data.ExamConfig
import et.tibeb.examprep.data.Pack
import et.tibeb.examprep.data.streamSubjects
import et.tibeb.examprep.ui.Busy
import et.tibeb.examprep.ui.ChoiceChips
import et.tibeb.examprep.ui.ErrorText
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.SectionTitle
import et.tibeb.examprep.ui.SubjectDot
import et.tibeb.examprep.ui.TCard
import et.tibeb.examprep.ui.date
import et.tibeb.examprep.ui.subjectName

private val SIZES = listOf(50, 100, 200)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacksScreen(nav: NavHostController) {
    val app = LocalApp.current
    val settings by app.prefs.settings.collectAsState()
    val packs by app.repo.dao.packs().collectAsState(emptyList())
    val jobs by app.repo.jobs.collectAsState()
    var subject by remember { mutableStateOf(streamSubjects(settings.stream).first { it != "English" }) }
    var size by remember { mutableIntStateOf(100) }
    var topics by remember { mutableStateOf(setOf<String>()) }
    val bookTopics by app.repo.dao.topics(subject).collectAsState(emptyList())
    val order = jobs["pack-order"]
    val practise = jobs["pack-exam"]

    LaunchedEffect(practise?.resultId) {
        practise?.resultId?.let { app.repo.clearJob("pack-exam"); nav.navigate("exam/$it") }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.packs_title)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(stringResource(R.string.packs_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                TCard {
                    Text(stringResource(R.string.label_subject), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(streamSubjects(settings.stream), setOf(subject), { subjectName(it) }) { subject = it; topics = emptySet() }
                    Text(stringResource(R.string.packs_size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SIZES.forEachIndexed { i, n ->
                            SegmentedButton(selected = size == n, onClick = { size = n }, shape = SegmentedButtonDefaults.itemShape(i, SIZES.size)) { Text("$n") }
                        }
                    }
                    if (bookTopics.isNotEmpty()) {
                        Text(stringResource(R.string.packs_topics), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                        ChoiceChips(bookTopics.take(20), topics, { it }) { t ->
                            topics = if (t in topics) topics - t else if (topics.size < 12) topics + t else topics
                        }
                    }
                    Spacer(Modifier.padding(4.dp))
                    order?.error?.let { ErrorText(it) }
                    if (order?.running == true) Busy(stringResource(R.string.packs_ordering))
                    else Button(onClick = { app.repo.run("pack-order", subject) { app.repo.orderPack(subject, size, topics.toList()) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.packs_order)) }
                }
                if (packs.isNotEmpty()) SectionTitle(stringResource(R.string.packs_title))
            }
            items(packs, key = { it.id }) { p -> PackItem(p,
                onCheck = { app.repo.checkPackNow(p.id) },
                onPractise = { app.repo.run("pack-exam", p.subject) {
                    app.repo.createExam(ExamConfig(p.subject, "", 12, 20, "standard", listOf("mcq"), useAi = false))
                } }) }
        }
    }
}

@Composable
private fun PackItem(p: Pack, onCheck: () -> Unit, onPractise: () -> Unit) {
    TCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubjectDot(p.subject); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pack_line, subjectName(p.subject), p.count), style = MaterialTheme.typography.titleMedium)
                Text(date(p.createdAt) + if (p.topics.isNotBlank()) " · ${p.topics}" else "", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        Spacer(Modifier.padding(4.dp))
        when (p.status) {
            "processing" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pack_processing), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.pack_check)) }
            }
            "imported" -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pack_imported, p.received), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Button(onClick = onPractise) { Text(stringResource(R.string.pack_practise)) }
            }
            else -> ErrorText(p.error.ifBlank { stringResource(R.string.pack_failed) })
        }
    }
}
