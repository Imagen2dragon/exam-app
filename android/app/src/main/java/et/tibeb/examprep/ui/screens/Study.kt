package et.tibeb.examprep.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import et.tibeb.examprep.data.Card
import et.tibeb.examprep.data.SUBJECTS
import et.tibeb.examprep.ui.Busy
import et.tibeb.examprep.ui.ChoiceChips
import et.tibeb.examprep.ui.EmptyState
import et.tibeb.examprep.ui.ErrorText
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.Markdown
import et.tibeb.examprep.ui.SectionTitle
import et.tibeb.examprep.ui.SubjectDot
import et.tibeb.examprep.ui.TCard
import kotlinx.coroutines.launch

private val NOTE_STYLES = listOf("summary" to "Summary", "detailed" to "Detailed", "formula_sheet" to "Formula sheet", "cheat_sheet" to "Cheat sheet")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(nav: NavHostController) {
    val app = LocalApp.current
    val settings by app.prefs.settings.collectAsState()
    val notes by app.repo.dao.notes().collectAsState(emptyList())
    val cardCount by app.repo.dao.cardCount().collectAsState(0)
    val jobs by app.repo.jobs.collectAsState()
    var subject by remember { mutableStateOf("Mathematics") }
    var topic by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) } // 0 notes, 1 flashcards
    var style by remember { mutableStateOf("summary") }
    var count by remember { mutableFloatStateOf(15f) }
    val topics by app.repo.dao.topics(subject).collectAsState(emptyList())
    val notesJob = jobs["notes"]
    val cardsJob = jobs["cards"]

    LaunchedEffect(notesJob?.resultId) {
        notesJob?.resultId?.let { app.repo.clearJob("notes"); nav.navigate("note/$it") }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Study", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
            Spacer(Modifier.height(12.dp))
            TCard {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Notes", "Flashcards").forEachIndexed { i, l ->
                        SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ChoiceChips(SUBJECTS, setOf(subject), { it }) { subject = it; topic = "" }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(topic, { topic = it.take(200) }, label = { Text("Topic (e.g. Logarithmic functions)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (topics.isNotEmpty() && topic.isBlank()) {
                    Text("From your books", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    ChoiceChips(topics.take(10), emptySet(), { it }) { topic = it }
                }
                Spacer(Modifier.height(8.dp))
                if (mode == 0) {
                    ChoiceChips(NOTE_STYLES.map { it.first }, setOf(style), { k -> NOTE_STYLES.first { it.first == k }.second }) { style = it }
                    Spacer(Modifier.height(8.dp))
                    if (notesJob?.running == true) Busy("Writing your notes…")
                    else Button(enabled = topic.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                        app.repo.run("notes", topic) { app.repo.makeNotes(subject, topic, settings.grade, style, settings.language) }
                    }) { Text("Create notes") }
                    notesJob?.error?.let { ErrorText(it) }
                } else {
                    Text("${count.toInt()} cards", style = MaterialTheme.typography.labelLarge)
                    Slider(count, { count = it }, valueRange = 5f..40f, steps = 6)
                    if (cardsJob?.running == true) Busy("Making flashcards…")
                    else Button(enabled = topic.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                        app.repo.run("cards", topic) { app.repo.makeFlashcards(subject, topic, settings.grade, count.toInt()) }
                    }) { Text("Create flashcards") }
                    cardsJob?.error?.let { ErrorText(it) }
                    cardsJob?.resultId?.let { Text("Added $it cards to your deck.", color = MaterialTheme.colorScheme.primary) }
                }
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { nav.navigate("review") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Review flashcards ($cardCount in deck)")
            }
            SectionTitle("My notes")
        }
        if (notes.isEmpty()) item { EmptyState("No notes yet", "Pick a subject and topic above to generate notes from your books.") }
        items(notes, key = { it.id }) { n ->
            TCard(onClick = { nav.navigate("note/${n.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectDot(n.subject)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(n.title, style = MaterialTheme.typography.titleMedium)
                        Text("${n.subject} · ${n.topic}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(nav: NavHostController, id: Long) {
    val app = LocalApp.current
    val note by app.repo.dao.note(id).collectAsState(null)
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(note?.title ?: "", maxLines = 1) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { scope.launch { app.repo.dao.deleteNote(id); nav.popBackStack() } }) { Icon(Icons.Filled.Delete, "Delete note") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        note?.let { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { Markdown(it.markdown) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(nav: NavHostController) {
    val app = LocalApp.current
    val now = remember { System.currentTimeMillis() }
    val due by app.repo.dao.dueCards(now).collectAsState(null)
    val scope = rememberCoroutineScope()
    var flipped by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    val card: Card? = due?.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Review · $done done") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (due != null && card == null) {
            EmptyState("All caught up 🎉", "No cards are due. Make more flashcards in Study, or come back tomorrow.")
            return@Column
        }
        if (card == null) return@Column
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            TCard(Modifier.weight(1f), onClick = { flipped = !flipped }) {
                Text("${card.subject} · ${card.topic}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
                    AnimatedContent(flipped, label = "flip") { f ->
                        Text(if (f) card.back else card.front, style = if (f) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center)
                    }
                }
                if (!flipped) Text("Tap to show answer", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(16.dp))
            if (flipped) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Again", 3 to "Hard", 4 to "Good", 5 to "Easy").forEach { (g, l) ->
                    val action = { scope.launch { app.repo.review(card, g); flipped = false; done++ }; Unit }
                    if (g == 4) Button(onClick = action, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(l) }
                    else OutlinedButton(onClick = action, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(l) }
                }
            } else Button(onClick = { flipped = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Show answer") }
        }
    }
}
