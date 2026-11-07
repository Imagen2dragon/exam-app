package et.tibeb.examprep.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import et.tibeb.examprep.R
import et.tibeb.examprep.data.Mastery
import et.tibeb.examprep.data.Mastery.Level
import et.tibeb.examprep.data.streamSubjects
import et.tibeb.examprep.ui.EmptyState
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.SubjectDot
import et.tibeb.examprep.ui.TCard
import et.tibeb.examprep.ui.subjectName

/** Every topic as a tile coloured by mastery, with the level written out (never colour alone). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MasteryScreen(nav: NavHostController) {
    val app = LocalApp.current
    val settings by app.prefs.settings.collectAsState()
    val stats by app.repo.dao.topicStats().collectAsState(emptyList())
    val books by app.repo.dao.allBookTopics().collectAsState(emptyList())
    val tiles = Mastery.tiles(books.map { it.subject to it.topic }, stats)
    val order = streamSubjects(settings.stream)
    val bySubject = tiles.groupBy { it.subject }.toList().sortedBy { (s, _) -> order.indexOf(s).let { if (it < 0) 99 else it } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.mastery_title)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.mastery_intro), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                Legend()
            }
            if (tiles.isEmpty()) item { EmptyState(stringResource(R.string.mastery_title), stringResource(R.string.mastery_empty)) }
            bySubject.forEach { (subject, list) ->
                item(key = subject) {
                    TCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubjectDot(subject); Spacer(Modifier.width(10.dp))
                            Text(subjectName(subject), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.mastery_subject, Mastery.subjectPercent(list)),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(progress = { Mastery.subjectPercent(list) / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { t ->
                                TopicTile(t) { nav.navigate("newexam?subject=${Uri.encode(t.subject)}&topic=${Uri.encode(t.topic)}") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class LevelStyle(val container: Color, val content: Color, val label: Int)

@Composable
private fun style(level: Level): LevelStyle {
    val c = MaterialTheme.colorScheme
    return when (level) {
        Level.NOT_STARTED -> LevelStyle(c.surfaceContainer, c.onSurfaceVariant, R.string.lvl_new)
        Level.WEAK -> LevelStyle(c.errorContainer, c.onErrorContainer, R.string.lvl_weak)
        Level.DEVELOPING -> LevelStyle(c.secondaryContainer, c.onSecondaryContainer, R.string.lvl_dev)
        Level.STRONG -> LevelStyle(c.primaryContainer, c.onPrimaryContainer, R.string.lvl_strong)
    }
}

@Composable
private fun TopicTile(t: Mastery.Tile, onClick: () -> Unit) {
    val s = style(t.level)
    val levelText = stringResource(s.label)
    val pct = (t.accuracy * 100).toInt()
    val desc = stringResource(R.string.mastery_tile_desc, t.topic, levelText, pct)
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = s.container,
        border = if (t.level == Level.NOT_STARTED) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.widthIn(min = 96.dp, max = 170.dp).heightIn(min = 48.dp)
            .clearAndSetSemantics { contentDescription = desc; role = Role.Button }) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(t.topic, style = MaterialTheme.typography.labelLarge, color = s.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (t.level == Level.NOT_STARTED) levelText else "$levelText · $pct%", style = MaterialTheme.typography.labelSmall,
                color = s.content.copy(alpha = 0.85f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() = FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    listOf(Level.NOT_STARTED, Level.WEAK, Level.DEVELOPING, Level.STRONG).forEach { l ->
        val s = style(l)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = s.container, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(14.dp)) {}
            Spacer(Modifier.width(6.dp))
            Text(stringResource(s.label), style = MaterialTheme.typography.labelMedium)
        }
    }
}
