package et.tibeb.examprep.ui.screens

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
import java.text.DateFormat
import java.util.Date

private val TYPE_LABEL = mapOf("mcq" to "Multiple choice", "numeric" to "Calculation", "short" to "Short answer", "graph" to "Draw graph / diagram")
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
                Text("Exams", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
                Spacer(Modifier.height(12.dp))
                TCard(onClick = { nav.navigate("mocksetup") }, container = MaterialTheme.colorScheme.primary) {
                    Text("Full national mock · 600", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text("All six papers of your stream, timed, scored out of 600 against the 300 pass line.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                }
            }
            items(mocks, key = { "m${it.id}" }) { m ->
                val papers = allExams.filter { it.mockId == m.id }
                val done = papers.filter { it.finishedAt > 0 }
                TCard(onClick = { nav.navigate("mock/${m.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("National mock · ${if (m.stream == "social") "Social" else "Natural"} stream", style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(m.createdAt)) + " · ${done.size}/6 papers",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${done.sumOf { it.percent() }.toInt()}/600", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (exams.isNotEmpty()) item { SectionTitle("Practice exams") }
            if (exams.isEmpty() && mocks.isEmpty()) item { EmptyState("No exams yet", "Tap New exam. Start with 10 questions to see how it works.") }
            items(exams, key = { it.id }) { e ->
                TCard(onClick = { nav.navigate(if (e.finishedAt > 0) "result/${e.id}" else "exam/${e.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubjectDot(e.subject)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.createdAt)) + " · ${e.minutes} min",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (e.finishedAt > 0) "${pct(e.score, e.maxScore)}%" else "Continue",
                            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { nav.navigate("newexam") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New exam") },
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
    val topics by app.repo.dao.topics(subject).collectAsState(emptyList())
    val pastCount by app.repo.dao.pastCount(subject).collectAsState(0)
    val job = app.repo.jobs.collectAsState().value["exam"]

    LaunchedEffect(job?.resultId) {
        job?.resultId?.let { id -> app.repo.clearJob("exam"); nav.navigate("exam/$id") { popUpTo("newexam?subject={subject}") { inclusive = true } } }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("New exam") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TCard {
                Text("Subject", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(SUBJECTS, setOf(subject), { it }) { subject = it; topic = "" }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(topic, { topic = it.take(200) }, label = { Text("Topics (blank = whole syllabus)") }, modifier = Modifier.fillMaxWidth())
                if (topics.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        topics.take(15).forEach { t -> OutlinedButton(onClick = { topic = if (topic.isBlank()) t else "$topic, $t" }) { Text(t, maxLines = 1) } }
                    }
                }
                Text("Grade", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(listOf(9, 10, 11, 12), setOf(grade), { "G$it" }) { grade = it }
            }
            TCard {
                Text("Question types", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(TYPE_LABEL.keys.toList(), types, { TYPE_LABEL.getValue(it) }) { t ->
                    types = if (t in types && types.size > 1) types - t else types + t
                }
                Text("Difficulty", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                val diffs = listOf("standard" to "National", "hard" to "Hard", "very_hard" to "Very hard")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    diffs.forEachIndexed { i, (k, l) ->
                        SegmentedButton(selected = difficulty == k, onClick = { difficulty = k }, shape = SegmentedButtonDefaults.itemShape(i, diffs.size)) { Text(l) }
                    }
                }
                Text("${count.toInt()} questions · about ${(count * 1.4).toInt()} min", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Slider(count, { count = it }, valueRange = 5f..60f, steps = 10)
            }
            TCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (useAi) "AI-generated (online)" else "From saved past exams (offline)", style = MaterialTheme.typography.titleSmall)
                        Text(if (useAi) "New questions written from your books, past papers and weak topics."
                        else "$pastCount saved $subject questions. No internet needed.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(useAi, { useAi = it }, modifier = Modifier.semantics { contentDescription = "Use AI to generate questions" })
                }
            }
            job?.error?.let { ErrorText(it) }
            if (job?.running == true) Busy("Writing your exam… hard exams take 1–3 minutes")
            else Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = {
                app.repo.run("exam", subject) {
                    app.repo.createExam(ExamConfig(subject, topic, grade, count.toInt(), difficulty, types.toList(), useAi))
                }
            }) { Text("Create exam", style = MaterialTheme.typography.titleMedium) }
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
    val tmpUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", tmpFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { app.repo.attachPhoto(examId, q.idx, tmpUri); photoVersion++ }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { app.repo.attachPhoto(examId, q.idx, uri); photoVersion++ }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Question ${idx + 1} of ${items.size}", style = MaterialTheme.typography.titleMedium)
                    Text("$answered answered", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = { confirm = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Leave exam") } },
            actions = {
                val m = (left.coerceAtLeast(0) / 60000).toInt(); val s = (left.coerceAtLeast(0) / 1000 % 60).toInt()
                Surface(shape = CircleShape, color = if (left < 5 * 60_000) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = "$m minutes left" }) {
                    Text("%02d:%02d".format(m, s), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LinearProgressIndicator(progress = { (idx + 1f) / items.size }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${TYPE_LABEL[q.type] ?: q.type} · ${q.marks} mark${if (q.marks > 1) "s" else ""} · ${q.topic}",
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
                    OutlinedTextField(current, { save(it.take(40)) }, label = { Text("Final answer" + if (q.unit.isNotBlank()) " (${q.unit})" else "") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Tip: work it out on paper. You can attach a photo of your working for feedback after the exam.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    OutlinedTextField(current, { save(it.take(3000)) }, label = { Text(if (q.type == "graph") "Notes about your sketch (optional)" else "Your answer") },
                        minLines = if (q.type == "graph") 2 else 4, modifier = Modifier.fillMaxWidth())
                    if (q.type == "graph" || q.type == "short") {
                        Text(if (q.type == "graph") "Draw the graph on paper, then photograph it. The AI checks shape, intercepts, asymptotes and labels."
                        else "Or photograph your handwritten answer.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { camera.launch(tmpUri) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Take photo") }
                            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("From gallery") }
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
                Surface(shape = CircleShape, onClick = { idx = it.idx },
                    color = when { it.idx == idx -> MaterialTheme.colorScheme.primary; done -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant },
                    modifier = Modifier.size(40.dp).semantics { contentDescription = "Question ${it.idx + 1}" + if (done) ", answered" else "" }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${it.idx + 1}", style = MaterialTheme.typography.labelMedium,
                            color = if (it.idx == idx) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { idx-- }, enabled = idx > 0, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Previous") }
            if (idx < items.lastIndex) Button(onClick = { idx++ }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Next") }
            else Button(onClick = { confirm = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Finish") }
        }
    }
    if (submit?.running == true) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Grading…") },
        text = { Busy("Checking answers. Written work and graphs are graded by the AI.") })
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Finish exam?") },
        text = { Text("You answered $answered of ${items.size}. You can also leave and continue later from Exams.") },
        confirmButton = { TextButton(onClick = { confirm = false; app.repo.run("submit:$examId", "submit") { app.repo.submit(examId) } }) { Text("Submit") } },
        dismissButton = { TextButton(onClick = { confirm = false; nav.popBackStack() }) { Text("Continue later") } },
    )
}

@Composable
private fun Thumbnail(path: String, version: Int) {
    val bmp = remember(path, version) {
        runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull()
    }
    bmp?.let { Image(it, contentDescription = "Photo of your answer", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp)) }
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
            TopAppBar(title = { Text("Result") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            TCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing((e.score / e.maxScore.coerceAtLeast(1.0)).toFloat(), "${pct(e.score, e.maxScore)}%")
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(e.title, style = MaterialTheme.typography.titleMedium)
                        Text("%.1f / %.0f marks".format(e.score, e.maxScore), style = MaterialTheme.typography.bodyLarge)
                        Text(verdict(e.score / e.maxScore.coerceAtLeast(1.0)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (job?.running == true) Busy("Grading written answers…")
            if (ungraded > 0 && job?.running != true) {
                ErrorText("$ungraded answer(s) still need AI grading (needs internet).")
                Button(onClick = { app.repo.run("submit:$examId", "submit") { app.repo.retryGrading(examId) } }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry grading") }
            }
            val mockId = e.mockId
            if (mockId != null) Button(onClick = { nav.navigate("mock/$mockId") { popUpTo("exams") } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp)) { Text("Back to national mock") }
            else Button(onClick = { nav.navigate("newexam?subject=${e.subject}") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp)) {
                Text("New exam – focus on my mistakes")
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
            Text(math("Your answer: " + it.response.ifBlank { if (it.imagePath.isNotBlank()) "(photo)" else "—" }), style = MaterialTheme.typography.bodyMedium)
            Text(math("Correct: ${it.answer}${if (it.unit.isNotBlank() && it.type == "numeric") " ${it.unit}" else ""}"), style = MaterialTheme.typography.titleSmall,
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
    f >= 0.85 -> "Excellent – university-ready on this material."
    f >= 0.65 -> "Good. Review the misses below, then go harder."
    f >= 0.5 -> "Passing level (50%). Push the weak topics."
    else -> "Below the 50% pass mark. Re-read the explanations and retry."
}
