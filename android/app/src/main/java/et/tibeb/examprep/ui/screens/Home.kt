package et.tibeb.examprep.ui.screens

import androidx.compose.ui.res.stringResource
import et.tibeb.examprep.R
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import et.tibeb.examprep.util.Updates
import et.tibeb.examprep.ui.subjectName
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.tibeb.examprep.data.NATURAL_STREAM
import et.tibeb.examprep.data.SOCIAL_STREAM
import et.tibeb.examprep.tab
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.SectionTitle
import et.tibeb.examprep.ui.StatPill
import et.tibeb.examprep.ui.SubjectDot
import et.tibeb.examprep.ui.TCard
import et.tibeb.examprep.ui.theme.subjectColor
import androidx.navigation.NavHostController

@Composable
fun HomeScreen(nav: NavHostController) {
    val app = LocalApp.current
    val dao = app.repo.dao
    val settings by app.prefs.settings.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val due by dao.dueCount(now).collectAsState(0)
    val exams by dao.allExams().collectAsState(emptyList())
    val stats by dao.topicStats().collectAsState(emptyList())
    val subjects = if (settings.stream == "social") SOCIAL_STREAM else NATURAL_STREAM
    val finished = exams.filter { it.finishedAt > 0 && it.maxScore > 0 }
    val avg = if (finished.isEmpty()) null else finished.takeLast(10).map { it.score / it.maxScore }.average()

    val update by app.updates.available.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { app.updates.check() } // at most once a day

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) }
            }
            update?.let { v ->
                Spacer(Modifier.height(12.dp))
                TCard(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updates.DOWNLOAD_URL))) },
                    container = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(stringResource(R.string.update_title, v), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.update_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!settings.hasToken || settings.serverUrl.isBlank()) {
                Spacer(Modifier.height(12.dp))
                TCard(onClick = { nav.navigate("settings") }, container = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(stringResource(R.string.home_connect_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_connect_body),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(avg?.let { "${(it * 100).toInt()}%" } ?: "–", stringResource(R.string.home_avg), Modifier.weight(1f))
                StatPill("${finished.size}", stringResource(R.string.home_exams_done), Modifier.weight(1f))
                StatPill("$due", stringResource(R.string.home_cards_due), Modifier.weight(1f))
            }
        }
        item {
            SectionTitle(stringResource(R.string.home_today))
            TCard(onClick = { nav.navigate("newexam") }, container = MaterialTheme.colorScheme.primary) {
                Text(stringResource(R.string.home_create_exam), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                Text(stringResource(R.string.home_create_exam_body), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TCard(Modifier.weight(1f), onClick = { nav.navigate("review") }) {
                    Text(stringResource(R.string.home_review_cards), style = MaterialTheme.typography.titleMedium)
                    Text(if (due > 0) stringResource(R.string.home_waiting, due) else stringResource(R.string.home_caught_up), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TCard(Modifier.weight(1f), onClick = { nav.tab("study") }) {
                    Text(stringResource(R.string.home_make_notes), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_any_topic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val weak = stats.filter { it.attempted >= 2 }.sortedBy { it.accuracy }.take(4)
        if (weak.isNotEmpty()) item {
            SectionTitle(stringResource(R.string.home_needs_work))
            TCard {
                weak.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        SubjectDot(s.subject)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.topic, style = MaterialTheme.typography.bodyLarge)
                            LinearProgressIndicator(progress = { s.accuracy.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${(s.accuracy * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(if (settings.stream == "social") R.string.home_stream_social else R.string.home_stream_natural))
            val dark = isSystemInDarkTheme()
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.height(((subjects.size + 1) / 2 * 92).dp), userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(subjects) { s ->
                    TCard(onClick = { nav.navigate("newexam?subject=$s") }, container = subjectColor(s, dark).copy(alpha = 0.14f)) {
                        SubjectDot(s, 14.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(subjectName(s), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}
