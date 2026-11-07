package et.tibeb.examprep.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import et.tibeb.examprep.R
import et.tibeb.examprep.data.Question
import et.tibeb.examprep.data.Repository.Turn
import et.tibeb.examprep.ui.Busy
import et.tibeb.examprep.ui.ErrorText
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.Markdown
import et.tibeb.examprep.ui.TCard
import et.tibeb.examprep.ui.math
import et.tibeb.examprep.util.Lang
import kotlinx.coroutines.launch

private const val MAX_TURNS = 19 // server allows 20 alternating turns ending with the student

/** Short tutor chat about one question. Online only; nothing is stored on the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainScreen(nav: NavHostController, questionId: Long, initialTurns: List<Turn> = emptyList()) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val language = if (Lang.isAmharic(ctx)) "am" else "en"
    val question by produceState<Question?>(null, questionId) { value = app.repo.dao.question(questionId) }
    // Saved as two parallel lists so the chat survives rotation.
    var texts by rememberSaveable { mutableStateOf(initialTurns.map { it.text }) }
    var fromStudent by rememberSaveable { mutableStateOf(initialTurns.map { it.fromStudent }) }
    var draft by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val turns = texts.zip(fromStudent) { t, s -> Turn(s, t) }
    val full = turns.size >= MAX_TURNS

    fun ask(text: String? = null) {
        val msg = text?.trim()
        val history = if (msg.isNullOrEmpty()) turns else turns + Turn(true, msg)
        if (history.isEmpty() || !history.last().fromStudent || busy) return
        texts = history.map { it.text }; fromStudent = history.map { it.fromStudent }; draft = ""
        busy = true; error = null
        scope.launch {
            runCatching { app.repo.explain(questionId, history, language) }
                .onSuccess { reply -> texts = texts + reply; fromStudent = fromStudent + false }
                .onFailure { e -> error = e.message }
            busy = false
        }
    }

    LaunchedEffect(turns.size, busy) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.size) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(title = { Text(stringResource(R.string.explain_title)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                question?.let { q ->
                    TCard(container = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Text(math(q.stem), style = MaterialTheme.typography.bodyLarge)
                        Text(math(stringResource(R.string.res_correct, q.answer)), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Text(stringResource(R.string.explain_note), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            itemsIndexed(turns) { _, t -> Bubble(t) }
            item {
                Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (busy) Busy(stringResource(R.string.explain_thinking))
                    error?.let { e ->
                        Column {
                            ErrorText(e)
                            TextButton(onClick = { ask() }) { Text(stringResource(R.string.explain_retry)) }
                        }
                    }
                }
            }
        }
        if (full) Text(stringResource(R.string.explain_limit), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        else {
            if (turns.isEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.explain_q_why, R.string.explain_q_mine, R.string.explain_q_steps, R.string.explain_q_similar).forEach { id ->
                    val label = stringResource(id)
                    AssistChip(onClick = { ask(label) }, label = { Text(label) }, enabled = !busy)
                }
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it.take(2000) }, placeholder = { Text(stringResource(R.string.explain_hint)) },
                    modifier = Modifier.weight(1f), maxLines = 4)
                FilledIconButton(onClick = { ask(draft) }, enabled = draft.isNotBlank() && !busy, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.explain_send))
                }
            }
        }
    }
}

@Composable
private fun Bubble(t: Turn) {
    val mine = t.fromStudent
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(shape = MaterialTheme.shapes.large,
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.widthIn(max = 320.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(if (mine) R.string.explain_you else R.string.app_name), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (mine) Text(t.text, style = MaterialTheme.typography.bodyLarge) else Markdown(t.text)
            }
        }
    }
}
