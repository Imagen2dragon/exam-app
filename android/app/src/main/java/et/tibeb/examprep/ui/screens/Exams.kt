package et.tibeb.examprep.ui.screens

import androidx.compose.ui.res.stringResource
import et.tibeb.examprep.R
import androidx.compose.ui.res.pluralStringResource
import et.tibeb.examprep.ui.DIFFICULTY
import et.tibeb.examprep.ui.QTYPE
import et.tibeb.examprep.ui.date
import et.tibeb.examprep.ui.gradeShort
import et.tibeb.examprep.ui.label
import et.tibeb.examprep.ui.streamName
import et.tibeb.examprep.ui.subjectName
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import et.tibeb.examprep.data.ExamConfig
import et.tibeb.examprep.data.ItemWithQuestion
import et.tibeb.examprep.data.SUBJECTS
import et.tibeb.examprep.data.percent
import et.tibeb.examprep.ui.SectionTitle
import et.tibeb.examprep.net.json
import et.tibeb.examprep.ui.Busy
import et.tibeb.examprep.ui.ChoiceChips
import et.tibeb.examprep.ui.EmptyState
import et.tibeb.examprep.ui.ErrorText
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.ScoreRing
import et.tibeb.examprep.ui.math
import et.tibeb.examprep.ui.SubjectDot
import et.tibeb.examprep.ui.TCard
import et.tibeb.examprep.util.answersDir
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val LETTERS = listOf("A", "B", "C", "D", "E")

@Composable
fun ExamsScreen(nav: NavHostController) {
    val app = LocalApp.current
    val exams by app.repo.dao.exams().collectAsState(emptyList())
    val mocks by app.repo.dao.mocks().collectAsState(emptyList())
    val allExams by app.repo.dao.allExams().collectAsState(emptyList())
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(stringResource(R.string.tab_exams), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
                Spacer(Modifier.height(12.dp))
                TCard(onClick = { nav.navigate("mocksetup") }, container = MaterialTheme.colorScheme.primary) {
                    Text(stringResource(R.string.ex_mock_card_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.ex_mock_card_body),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                }
            }
            items(mocks, key = { "m${it.id}" }) { m ->
                val papers = allExams.filter { it.mockId == m.id }
                val done = papers.filter { it.finishedAt > 0 }
                TCard(onClick = { nav.navigate("mock/${m.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ex_mock_item, streamName(m.stream)), style = MaterialTheme.typography.titleMedium)
                            Text(date(m.createdAt) + " · " + stringResource(R.string.ex_papers_done, done.size),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${done.sumOf { it.percent() }.toInt()}/600", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (exams.isNotEmpty()) item { SectionTitle(stringResource(R.string.ex_practice)) }
            if (exams.isEmpty() && mocks.isEmpty()) item { EmptyState(stringResource(R.string.ex_empty_title), stringResource(R.string.ex_empty_body)) }
            items(exams, key = { it.id }) { e ->
                TCard(onClick = { nav.navigate(if (e.finishedAt > 0) "result/${e.id}" else "exam/${e.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(e.subject)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text(date(e.createdAt) + " · " + stringResource(R.string.minutes_short, e.minutes),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (e.finishedAt > 0) "${pct(e.score, e.maxScore)}%" else stringResource(R.string.ex_continue),
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { nav.navigate("newexam") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.ex_new)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSetupScreen(nav: NavHostController, initialSubject: String) {
    val app = LocalApp.current
    val settings by app.prefs.settings.collectAsState()
    var subject by remember { mutableStateOf(initialSubject.ifBlank { "Mathematics" }) }
    var topic by remember { mutableStateOf("") }
    var grade by remember { mutableIntStateOf(settings.grade) }
    var count by remember { mutableFloatStateOf(20f) }
    var difficulty by remember { mutableStateOf("hard") }
    var types by remember { mutableStateOf(setOf("mcq")) }
    var useAi by remember { mutableStateOf(true) }
    val useAiDesc = stringResource(R.string.ex_use_ai_desc)
    val topics by app.repo.dao.topics(subject).collectAsState(emptyList())
    val pastCount by app.repo.dao.pastCount(subject).collectAsState(0)
    val job = app.repo.jobs.collectAsState().value["exam"]

    LaunchedEffect(job?.resultId) {
        job?.resultId?.let { id -> app.repo.clearJob("exam"); nav.navigate("exam/$id") { popUpTo("newexam?subject={subject}") { inclusive = true } } }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.ex_new)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TCard {
                Text(stringResource(R.string.label_subject), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(SUBJECTS, setOf(subject), { subjectName(it) }) { subject = it; topic = "" }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(topic, { topic = it.take(200) }, label = { Text(stringResource(R.string.ex_topics_hint)) }, modifier = Modifier.fillMaxWidth())
                if (topics.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        topics.take(15).forEach { t -> OutlinedButton(onClick = { topic = if (topic.isBlank()) t else "$topic, $t" }) { Text(t, maxLines = 1) } }
                    }
                }
                Text(stringResource(R.string.label_grade), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(listOf(9, 10, 11, 12), setOf(grade), { gradeShort(it) }) { grade = it }
            }
            TCard {
                Text(stringResource(R.string.ex_qtypes), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(QTYPE.keys.toList(), types, { label(QTYPE, it) }) { t ->
                    types = if (t in types && types.size > 1) types - t else types + t
                }
                Text(stringResource(R.string.ex_difficulty), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                val diffs = DIFFICULTY.keys.map { it to label(DIFFICULTY, it) }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    diffs.forEachIndexed { i, (k, l) ->
                        SegmentedButton(selected = difficulty == k, onClick = { difficulty = k }, shape = SegmentedButtonDefaults.itemShape(i, diffs.size)) { Text(l) }
                    }
                }
                Text(stringResource(R.string.ex_count_time, count.toInt(), (count * 1.4).toInt()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Slider(count, { count = it }, valueRange = 5f..60f, steps = 10)
            }
            TCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (useAi) R.string.ex_ai_title else R.string.ex_bank_title), style = MaterialTheme.typography.titleSmall)
                        Text(if (useAi) stringResource(R.string.ex_ai_body)
                        else stringResource(R.string.ex_bank_body, pastCount, subjectName(subject)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(useAi, { useAi = it }, modifier = Modifier.semantics { contentDescription = useAiDesc })
                }
            }
            job?.error?.let { ErrorText(it) }
            if (job?.running == true) Busy(stringResource(R.string.ex_writing))
            else Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = {
                app.repo.run("exam", subject) {
                    app.repo.createExam(ExamConfig(subject, topic, grade, count.toInt(), difficulty, types.toList(), useAi))
                }
            }) { Text(stringResource(R.string.ex_create), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(nav: NavHostController, examId: Long) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val exam by app.repo.dao.exam(examId).collectAsState(null)
    val items by app.repo.dao.items(examId).collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var idx by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val drafts = remember { mutableStateMapOf<Int, String>() }
    val submit = app.repo.jobs.collectAsState().value["submit:$examId"]
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var photoVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(examId) { app.repo.start(examId) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(submit?.running, submit?.resultId, submit?.error) {
        if (submit != null && !submit.running) nav.navigate("result/$examId") { popUpTo("exams") }
    }
    BackHandler { confirm = true }

    val e = exam ?: return
    val left = if (e.startedAt == 0L) e.minutes * 60_000L else e.startedAt + e.minutes * 60_000L - now
    LaunchedEffect(left <= 0) { if (left <= 0 && submit == null && items.isNotEmpty()) app.repo.run("submit:$examId", "submit") { app.repo.submit(examId) } }
    if (items.isEmpty()) return
    val q = items[idx.coerceIn(items.indices)]
    val answered = items.count { (drafts[it.idx] ?: it.response).isNotBlank() || it.imagePath.isNotBlank() }

    val tmpFile = remember { File(ctx.answersDir(), "capture.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { app.repo.attachPhoto(examId, q.idx, Uri.fromFile(tmpFile)); photoVersion++ }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { app.repo.attachPhoto(examId, q.idx, uri); photoVersion++ }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.ex_q_of, idx + 1, items.size), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ex_answered, answered), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = { confirm = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ex_leave)) } },
            actions = {
                val m = (left.coerceAtLeast(0) / 60000).toInt(); val s = (left.coerceAtLeast(0) / 1000 % 60).toInt()
                val leftDesc = stringResource(R.string.ex_min_left, m)
                Surface(shape = CircleShape, color = if (left < 5 * 60_000) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = leftDesc }) {
                    Text("%02d:%02d".format(m, s), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LinearProgressIndicator(progress = { (idx + 1f) / items.size }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${label(QTYPE, q.type)} · ${pluralStringResource(R.plurals.marks, q.marks, q.marks)} · ${q.topic}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(math(q.stem), style = MaterialTheme.typography.titleLarge)
            val current = drafts[q.idx] ?: q.response
            val save = { v: String -> drafts[q.idx] = v; scope.launch { app.repo.answer(examId, q.idx, v) }; Unit }
            when (q.type) {
                "mcq" -> options(q).forEachIndexed { i, opt ->
                    val letter = LETTERS[i]; val sel = current == letter
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(sel, role = Role.RadioButton) { save(letter) },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(letter, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(math(opt), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                "numeric" -> {
                    OutlinedTextField(current, { save(it.take(40)) }, label = { Text(if (q.unit.isNotBlank()) stringResource(R.string.ex_final_answer_unit, q.unit) else stringResource(R.string.ex_final_answer)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ex_numeric_tip),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    OutlinedTextField(current, { save(it.take(3000)) }, label = { Text(stringResource(if (q.type == "graph") R.string.ex_sketch_notes else R.string.ex_your_answer)) },
                        minLines = if (q.type == "graph") 2 else 4, modifier = Modifier.fillMaxWidth())
                    if (q.type == "graph" || q.type == "short") {
                        Text(stringResource(if (q.type == "graph") R.string.ex_graph_hint else R.string.ex_photo_hint), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", tmpFile)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ex_take_photo)) }
                            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ex_from_gallery)) }
                        }
                        if (q.imagePath.isNotBlank()) Thumbnail(q.imagePath, photoVersion)
                    }
                }
            }
        }
        // Question palette + navigation
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { it ->
                val done = (drafts[it.idx] ?: it.response).isNotBlank() || it.imagePath.isNotBlank()
                val qDesc = if (done) stringResource(R.string.ex_q_desc_answered, it.idx + 1) else stringResource(R.string.ex_q_desc, it.idx + 1)
                Surface(shape = CircleShape, onClick = { idx = it.idx },
                    color = when { it.idx == idx -> MaterialTheme.colorScheme.primary; done -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant },
                    modifier = Modifier.size(40.dp).semantics { contentDescription = qDesc }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${it.idx + 1}", style = MaterialTheme.typography.labelMedium,
                            color = if (it.idx == idx) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { idx-- }, enabled = idx > 0, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.ex_prev)) }
            if (idx < items.lastIndex) Button(onClick = { idx++ }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.ex_next)) }
            else Button(onClick = { confirm = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.ex_finish)) }
        }
    }
    if (submit?.running == true) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text(stringResource(R.string.ex_grading)) },
        text = { Busy(stringResource(R.string.ex_grading_body)) })
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.ex_finish_title)) },
        text = { Text(stringResource(R.string.ex_finish_body, answered, items.size)) },
        confirmButton = { TextButton(onClick = { confirm = false; app.repo.run("submit:$examId", "submit") { app.repo.submit(examId) } }) { Text(stringResource(R.string.ex_submit)) } },
        dismissButton = { TextButton(onClick = { confirm = false; nav.popBackStack() }) { Text(stringResource(R.string.ex_later)) } },
    )
}

@Composable
private fun Thumbnail(path: String, version: Int) {
    val bmp = remember(path, version) {
        runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull()
    }
    bmp?.let { Image(it, contentDescription = stringResource(R.string.ex_photo_desc), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(nav: NavHostController, examId: Long) {
    val app = LocalApp.current
    val exam by app.repo.dao.exam(examId).collectAsState(null)
    val items by app.repo.dao.items(examId).collectAsState(emptyList())
    val job = app.repo.jobs.collectAsState().value["submit:$examId"]
    val e = exam ?: return
    val ungraded = items.count { !it.graded }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopAppBar(title = { Text(stringResource(R.string.res_title)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            TCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing((e.score / e.maxScore.coerceAtLeast(1.0)).toFloat(), "${pct(e.score, e.maxScore)}%")
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (e.mockId != null) stringResource(R.string.paper_title, subjectName(e.subject)) else e.title, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.res_marks, "%.1f".format(e.score), "%.0f".format(e.maxScore)), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(verdict(e.score / e.maxScore.coerceAtLeast(1.0))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (job?.running == true) Busy(stringResource(R.string.res_grading_written))
            if (ungraded > 0 && job?.running != true) {
                ErrorText(stringResource(R.string.res_ungraded, ungraded))
                Button(onClick = { app.repo.run("submit:$examId", "submit") { app.repo.retryGrading(examId) } }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.res_retry)) }
            }
            val mockId = e.mockId
            if (mockId != null) Button(onClick = { nav.navigate("mock/$mockId") { popUpTo("exams") } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp)) { Text(stringResource(R.string.res_back_mock)) }
            else Button(onClick = { nav.navigate("newexam?subject=${e.subject}") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp)) {
                Text(stringResource(R.string.res_focus))
            }
        }
        items(items, key = { it.idx }) { ReviewItem(it) }
    }
}

@Composable
private fun ReviewItem(it: ItemWithQuestion) {
    var open by remember { mutableStateOf(it.earned < it.marks) }
    val full = it.earned >= it.marks
    val tone = when { !it.graded -> MaterialTheme.colorScheme.surfaceVariant; full -> MaterialTheme.colorScheme.primaryContainer
        it.earned > 0 -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.errorContainer }
    TCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = tone, modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("${it.idx + 1}", style = MaterialTheme.typography.labelMedium) }
            }
            Spacer(Modifier.width(10.dp))
            Text(math(it.stem), style = MaterialTheme.typography.bodyLarge, maxLines = if (open) 20 else 2, modifier = Modifier.weight(1f))
            Text("%.1f/%d".format(it.earned, it.marks), style = MaterialTheme.typography.labelLarge)
        }
        if (open) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (it.type == "mcq") options(it).forEachIndexed { i, o -> Text(math("${LETTERS[i]}. $o"), style = MaterialTheme.typography.bodyMedium) }
            Text(math(stringResource(R.string.res_your_answer, it.response.ifBlank { if (it.imagePath.isNotBlank()) stringResource(R.string.res_photo) else "—" })), style = MaterialTheme.typography.bodyMedium)
            Text(math(stringResource(R.string.res_correct, it.answer + if (it.unit.isNotBlank() && it.type == "numeric") " ${it.unit}" else "")), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            if (it.feedback.isNotBlank()) Text(math(it.feedback), style = MaterialTheme.typography.bodyMedium)
            if (it.explanation.isNotBlank()) Text(math(it.explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun options(q: ItemWithQuestion): List<String> =
    runCatching { json.decodeFromString<List<String>>(q.optionsJson) }.getOrDefault(emptyList()).take(5)

private fun pct(score: Double, max: Double) = if (max <= 0) 0 else (score / max * 100).toInt()

private fun verdict(f: Double) = when {
    f >= 0.85 -> R.string.verdict_excellent
    f >= 0.65 -> R.string.verdict_good
    f >= 0.5 -> R.string.verdict_pass
    else -> R.string.verdict_fail
}
