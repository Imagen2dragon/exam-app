package et.tibeb.examprep.ui.screens

import androidx.compose.ui.res.stringResource
import et.tibeb.examprep.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import et.tibeb.examprep.data.Planner
import et.tibeb.examprep.ui.date
import androidx.compose.ui.res.pluralStringResource
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
    val mistakes by dao.mistakes().collectAsState(emptyList())
    val subjects = if (settings.stream == "social") SOCIAL_STREAM else NATURAL_STREAM
    val finished = exams.filter { it.finishedAt > 0 && it.maxScore > 0 }
    val avg = if (finished.isEmpty()) null else finished.takeLast(10).map { it.score / it.maxScore }.average()

    val tick by app.repo.activityTick.collectAsState()
    val today = Planner.startOfDay(now)
    val examDate = settings.examDate.takeIf { it > 0 && Planner.daysUntil(it, now) >= 0 }
    val streak = remember(tick, exams) { Planner.streak(app.repo.activeDays(), System.currentTimeMillis()) }
    val plan = remember(tick, due, mistakes.size, exams, settings) {
        val accuracy = finished.groupBy { it.subject }.mapValues { (_, es) -> es.map { it.score / it.maxScore }.average() }
        Planner.plan(Planner.Input(
            now = System.currentTimeMillis(), examDate = examDate, dueCards = due, reviewedToday = app.repo.reviewsToday(),
            mistakes = mistakes.size, streamSubjects = subjects, subjectAccuracy = accuracy,
            examsFinishedToday = finished.filter { it.finishedAt >= today }.map { it.subject },
            lastMockFinished = finished.filter { it.mockId != null }.maxOfOrNull { it.finishedAt },
        ))
    }
    var pickDate by remember { mutableStateOf(false) }

    val update by app.updates.available.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { app.updates.check() } // at most once a day

    // One-time, plain-language notice that progress is shared with the admin (never hidden).
    var sharingNotice by remember { mutableStateOf(!app.getSharedPreferences("settings", 0).getBoolean("sharing_notice_seen", false)) }
    if (sharingNotice) androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.share_notice_title)) },
        text = { Text(stringResource(R.string.share_body)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                app.getSharedPreferences("settings", 0).edit().putBoolean("sharing_notice_seen", true).apply(); sharingNotice = false
            }) { Text(stringResource(R.string.ok_understood)) }
        },
    )

    if (pickDate) ExamDateDialog(settings.examDate.takeIf { it > 0 }, { pickDate = false }) { picked ->
        pickDate = false; app.prefs.save(settings.copy(examDate = picked ?: 0))
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) }
            }
            Spacer(Modifier.height(12.dp))
            Countdown(examDate, now) { pickDate = true }
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
                StatPill(if (streak > 0) "🔥 $streak" else "0", stringResource(R.string.home_streak), Modifier.weight(1f))
                StatPill("$due", stringResource(R.string.home_cards_due), Modifier.weight(1f))
            }
        }
        item {
            SectionTitle(stringResource(R.string.home_today))
            if (plan.isNotEmpty()) {
                PlanCard(plan) { item ->
                    when (item.kind) {
                        Planner.Kind.REVIEW_CARDS -> nav.navigate("review")
                        Planner.Kind.FIX_MISTAKES -> nav.navigate("mistakes")
                        Planner.Kind.PRACTICE_EXAM -> nav.navigate("newexam?subject=${android.net.Uri.encode(item.subject)}")
                        Planner.Kind.FULL_MOCK -> nav.navigate("mocksetup")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.home_needs_work), Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { nav.navigate("mastery") }) { Text(stringResource(R.string.home_all_topics)) }
            }
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
                    TCard(onClick = { nav.navigate("newexam?subject=${android.net.Uri.encode(s)}") }, container = subjectColor(s, dark).copy(alpha = 0.14f)) {
                        SubjectDot(s, 14.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(subjectName(s), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Countdown(examDate: Long?, now: Long, onPick: () -> Unit) {
    if (examDate == null) {
        TCard(onClick = onPick, container = MaterialTheme.colorScheme.primaryContainer) {
            Text(stringResource(R.string.countdown_set), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.countdown_set_body), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val days = Planner.daysUntil(examDate, now)
    TCard(onClick = onPick, container = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$days", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (days == 0) stringResource(R.string.countdown_today)
                    else pluralStringResource(R.plurals.countdown_days, days, days), style = MaterialTheme.typography.titleMedium)
                Text(date(examDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanCard(plan: List<Planner.Item>, onOpen: (Planner.Item) -> Unit) {
    TCard {
        plan.forEach { item ->
            val label = when (item.kind) {
                Planner.Kind.REVIEW_CARDS -> if (item.done) stringResource(R.string.plan_cards_done) else pluralStringResource(R.plurals.plan_cards, item.count, item.count)
                Planner.Kind.FIX_MISTAKES -> pluralStringResource(R.plurals.plan_mistakes, item.count, item.count)
                Planner.Kind.PRACTICE_EXAM -> stringResource(R.string.plan_exam, subjectName(item.subject))
                Planner.Kind.FULL_MOCK -> stringResource(R.string.plan_mock)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onOpen(item) },
                verticalAlignment = Alignment.CenterVertically) {
                if (item.done) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                else SubjectDot(item.subject.ifBlank { "-" }, 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (plan.all { it.done }) Text(stringResource(R.string.plan_all_done), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}
