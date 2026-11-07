package et.tibeb.examprep.ui.screens

import androidx.compose.ui.res.stringResource
import et.tibeb.examprep.R
import et.tibeb.examprep.ui.streamName
import et.tibeb.examprep.ui.subjectName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import et.tibeb.examprep.data.Exam
import et.tibeb.examprep.data.paperMinutes
import et.tibeb.examprep.data.percent
import et.tibeb.examprep.data.streamSubjects
import et.tibeb.examprep.ui.Busy
import et.tibeb.examprep.ui.ChoiceChips
import et.tibeb.examprep.ui.ErrorText
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.ScoreRing
import et.tibeb.examprep.ui.SectionTitle
import et.tibeb.examprep.ui.SubjectDot
import et.tibeb.examprep.ui.TCard
import kotlinx.coroutines.launch

private val LENGTHS = listOf(30 to R.string.len_quick, 60 to R.string.len_standard, 100 to R.string.len_full)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockSetupScreen(nav: NavHostController) {
    val app = LocalApp.current
    val settings by app.prefs.settings.collectAsState()
    var stream by remember { mutableStateOf(settings.stream) }
    var perPaper by remember { mutableIntStateOf(60) }
    var useAi by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val useAiDesc = stringResource(R.string.mock_use_ai_desc)
    val subjects = streamSubjects(stream)
    val totalMin = subjects.sumOf { paperMinutes(it, perPaper) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.mock_setup_title)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.mock_setup_intro),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TCard {
                Text(stringResource(R.string.set_stream), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf("natural", "social"), setOf(stream), { streamName(it) }) { stream = it }
                Text(stringResource(R.string.mock_qpp), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LENGTHS.forEachIndexed { i, (n, l) ->
                        SegmentedButton(selected = perPaper == n, onClick = { perPaper = n }, shape = SegmentedButtonDefaults.itemShape(i, LENGTHS.size)) { Text(stringResource(l) + " · $n") }
                    }
                }
            }
            TCard {
                Text(stringResource(R.string.mock_papers), style = MaterialTheme.typography.titleSmall)
                subjects.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(s); Spacer(Modifier.width(10.dp))
                        Text(subjectName(s), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.mock_q_min, perPaper, paperMinutes(s, perPaper)), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.mock_total, totalMin / 60, totalMin % 60),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.mock_estimates), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (useAi) R.string.mock_ai_title else R.string.mock_bank_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (useAi) R.string.mock_ai_body else R.string.mock_bank_body),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(useAi, { useAi = it }, modifier = Modifier.semantics { contentDescription = useAiDesc })
                }
            }
            Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = {
                scope.launch {
                    val id = app.repo.createMock(stream, perPaper, useAi)
                    nav.navigate("mock/$id") { popUpTo("mocksetup") { inclusive = true } }
                }
            }) { Text(stringResource(R.string.mock_create), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockScreen(nav: NavHostController, mockId: Long) {
    val app = LocalApp.current
    val mock = app.repo.dao.mock(mockId).collectAsState(null).value ?: return
    val papers by app.repo.dao.mockPapers(mockId).collectAsState(emptyList())
    val jobs by app.repo.jobs.collectAsState()
    val subjects = streamSubjects(mock.stream)
    val bySubject: Map<String, Exam> = papers.associateBy { it.subject }
    val done = subjects.mapNotNull { bySubject[it]?.takeIf { e -> e.finishedAt > 0 } }
    val total = done.sumOf { it.percent() }
    val projected = if (done.isEmpty()) null else total / done.size * subjects.size
    val prepare = jobs["prepare:$mockId"]

    // Open a paper as soon as its generation job finishes.
    subjects.forEach { s ->
        val j = jobs["paper:$mockId:$s"]
        LaunchedEffect(j?.resultId) { j?.resultId?.let { app.repo.clearJob("paper:$mockId:$s"); nav.navigate("exam/$it") } }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.mock_title)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ring colour = performance on papers done so far (not progress), so a strong start isn't shown red.
                    ScoreRing(if (done.isEmpty()) 0f else (total / (done.size * 100)).toFloat(), "${total.toInt()}", size = 110.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("${total.toInt()} / 600", style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.mock_done_count, done.size, subjects.size), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(mockVerdict(done.size == subjects.size, total, projected)), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                PassBar(total, projected)
            }
            SectionTitle(stringResource(R.string.mock_papers))
            subjects.forEach { s ->
                val e = bySubject[s]
                val job = jobs["paper:$mockId:$s"]
                TCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(s); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(subjectName(s), style = MaterialTheme.typography.titleMedium)
                            Text(when {
                                e == null -> stringResource(R.string.mock_q_min_long, mock.questionsPerPaper, paperMinutes(s, mock.questionsPerPaper))
                                e.finishedAt > 0 -> stringResource(R.string.mock_score, e.percent().toInt())
                                e.startedAt > 0 -> stringResource(R.string.mock_in_progress)
                                else -> stringResource(R.string.mock_ready, e.minutes)
                            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                            job?.running == true -> Busy(stringResource(R.string.mock_writing))
                            e != null && e.finishedAt > 0 -> OutlinedButton(onClick = { nav.navigate("result/${e.id}") }) { Text(stringResource(R.string.mock_review)) }
                            e != null -> Button(onClick = { nav.navigate("exam/${e.id}") }) { Text(stringResource(if (e.startedAt > 0) R.string.ex_continue else R.string.mock_start)) }
                            else -> Button(enabled = prepare?.running != true, onClick = {
                                app.repo.run("paper:$mockId:$s", s) { app.repo.mockPaper(mockId, s) }
                            }) { Text(stringResource(R.string.mock_start)) }
                        }
                    }
                    if (e != null && e.finishedAt > 0) LinearProgressIndicator(progress = { (e.percent() / 100).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    job?.error?.let { Spacer(Modifier.height(6.dp)); ErrorText(it) }
                }
            }
            if (mock.useAi && papers.size < subjects.size) {
                if (prepare?.running == true) Busy(stringResource(R.string.mock_preparing))
                else FilledTonalButton(onClick = { app.repo.run("prepare:$mockId", "mock") { app.repo.prepareMock(mockId) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.mock_prepare)) }
                prepare?.error?.let { ErrorText(it) }
            }
            if (done.isNotEmpty()) {
                val weakest = done.minBy { it.percent() }
                TCard(onClick = { nav.navigate("newexam?subject=${weakest.subject}") }, container = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(stringResource(R.string.mock_weakest, subjectName(weakest.subject), weakest.percent().toInt()), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.mock_weakest_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PassBar(total: Double, projected: Double?) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        LinearProgressIndicator(progress = { (total / 600).toFloat() }, trackColor = track, modifier = Modifier.fillMaxWidth().height(10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("0", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.mock_pass), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("600", style = MaterialTheme.typography.labelSmall)
        }
        projected?.let { Text(stringResource(R.string.mock_on_track, it.toInt()), style = MaterialTheme.typography.bodySmall) }
    }
}

private fun mockVerdict(complete: Boolean, total: Double, projected: Double?): Int = when {
    projected == null -> R.string.mv_start
    !complete -> if (projected >= 300) R.string.mv_proj_pass else R.string.mv_proj_fail
    total >= 450 -> R.string.mv_excellent
    total >= 300 -> R.string.mv_pass
    else -> R.string.mv_fail
}
