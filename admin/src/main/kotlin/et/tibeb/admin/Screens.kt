package et.tibeb.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val zone = ZoneId.systemDefault()
fun fmtDate(ms: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy").format(Instant.ofEpochMilli(ms).atZone(zone))
fun fmtDay(day: String): String = runCatching { DateTimeFormatter.ofPattern("d MMM").format(LocalDate.parse(day)) }.getOrDefault(day)
/** The phone's cloud-backup state for the header; `late` = no backup for more than 3 days. */
fun backupStatus(p: Progress, nowMs: Long): Pair<String, Boolean> {
    val last = p.last_backup ?: return "Cloud backup: not turned on in the phone's Settings" to false
    val late = nowMs - last > 3 * 86_400_000L
    return ("Cloud backup: ${ago(last / 1000.0)}" + if (late) " - check the phone is online and signed in" else "") to late
}

fun ago(secs: Double?): String {
    if (secs == null) return "never"
    val m = (System.currentTimeMillis() / 1000.0 - secs) / 60
    return when { m < 2 -> "just now"; m < 60 -> "${m.toInt()} min ago"; m < 48 * 60 -> "${(m / 60).toInt()} h ago"; else -> "${(m / 1440).toInt()} days ago" }
}

// ---------------- Connect ----------------

@Composable
fun ConnectScreen(error: String?, busy: Boolean, onConnect: (String, String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    var key by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tibeb Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Follow your student's exam preparation and AI use. Enter your server address and the admin key " +
                    "(ADMIN_TOKEN) from the server's settings.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(url, { url = it.trim() }, label = { Text("Server address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it.trim() }, label = { Text("Admin key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { onConnect(url, key) }, enabled = !busy && url.length > 8 && key.length >= 32,
                    modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Connect")
                }
                Text("The key is stored encrypted for your Windows account only.", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------- Dashboard ----------------

enum class Tab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Dashboard), TOPICS("Topics", Icons.Filled.GridView), EXAMS("Exams", Icons.Filled.AutoGraph),
    ACTIVITY("Study days", Icons.Filled.CalendarMonth), AI("AI use", Icons.Filled.Payments), BOOKS("Books", Icons.Filled.MenuBook),
}

data class Loaded(val devices: Devices, val selected: Device?, val history: History?, val usage: Usage?, val at: Long)

@Composable
fun Dashboard(data: Loaded, error: String?, busy: Boolean, onSelect: (Device) -> Unit, onRefresh: () -> Unit,
              onRename: (Device, String) -> Unit, onSignOut: () -> Unit, initialTab: Tab = Tab.OVERVIEW) {
    var tab by remember { mutableStateOf(initialTab) }
    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Spacer(Modifier.height(8.dp))
            Tab.entries.forEach { t ->
                NavigationRailItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
            }
            Spacer(Modifier.weight(1f))
            NavigationRailItem(selected = false, onClick = onSignOut, icon = { Icon(Icons.Filled.Logout, null) }, label = { Text("Sign out") })
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Header(data, busy, onSelect, onRefresh, onRename)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
            val latest = data.history?.latest ?: data.selected?.latest
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (data.selected == null) Empty("No student phones yet", "Phones appear here after they connect to the server and go online.")
                else when (tab) {
                    Tab.OVERVIEW -> if (latest == null) NoProgress() else Overview(latest, data.history, data.selected)
                    Tab.TOPICS -> if (latest == null) NoProgress() else Topics(latest)
                    Tab.EXAMS -> if (latest == null) NoProgress() else Exams(latest)
                    Tab.ACTIVITY -> if (latest == null) NoProgress() else Activity(latest)
                    Tab.AI -> AiUse(data.usage, data.devices.budget_usd, data.selected)
                    Tab.BOOKS -> if (latest == null) NoProgress() else Books(latest)
                }
            }
        }
    }
}

@Composable
private fun Header(data: Loaded, busy: Boolean, onSelect: (Device) -> Unit, onRefresh: () -> Unit, onRename: (Device, String) -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var name by remember(data.selected?.device) { mutableStateOf(data.selected?.label.orEmpty()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (renaming) Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.width(260.dp))
                TextButton(onClick = { data.selected?.let { onRename(it, name) }; renaming = false }) { Text("Save") }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.selected?.name ?: "Tibeb Admin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (data.selected != null) TextButton(onClick = { renaming = true }) { Text("Rename") }
            }
            data.selected?.let { d ->
                val p = d.latest
                Text("Last update from the phone: ${ago(d.last_seen)}" + (p?.let { " · app ${it.app_version} · ${it.stream} science, grade ${it.grade}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                p?.let {
                    val (text, late) = backupStatus(it, System.currentTimeMillis())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (late) Icon(Icons.Filled.Warning, null, Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.error)
                        Text(text, style = MaterialTheme.typography.bodyMedium,
                            color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (data.devices.devices.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            data.devices.devices.forEach { d ->
                TextButton(onClick = { onSelect(d) }, enabled = d.device != data.selected?.device) { Text(d.name) }
            }
        }
        Text("Updated ${ago(data.at / 1000.0)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onRefresh, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Refresh, "Refresh")
        }
    }
}

@Composable
private fun Tile(value: String, label: String, note: String? = null, modifier: Modifier = Modifier) =
    Surface(modifier.height(112.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            note?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

@Composable
private fun Card(title: String, content: @Composable () -> Unit) =
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium); content()
        }
    }

@Composable
private fun Empty(title: String, body: String) = Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NoProgress() = Empty("No progress received yet",
    "The phone sends a summary about every 6 hours and after each finished exam, whenever it is online.")

@Composable
private fun Overview(p: Progress, h: History?, d: Device) {
    val t = p.totals
    val days = p.exam_date?.let { ChronoUnit.DAYS.between(LocalDate.now(zone), Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(days?.let { if (it >= 0) "$it" else "–" } ?: "–", "Days to the exam", p.exam_date?.let { fmtDate(it) } ?: "Date not set", Modifier.weight(1f))
        Tile("${t.avg_score_pct.toInt()}%", "Average score", "last 20 exams", Modifier.weight(1f))
        Tile(if (t.mocks_done > 0) "${t.best_mock_total.toInt()}" else "–", "Best mock / 600",
            if (t.mocks_done > 0) (if (t.best_mock_total >= 300) "pass line: 300 ✓" else "pass line: 300") else "no full mock yet", Modifier.weight(1f))
        Tile("${p.streak}", "Day streak", null, Modifier.weight(1f))
        Tile("${t.mistakes_open}", "Mistakes to fix", null, Modifier.weight(1f))
        Tile("${t.exams_done}", "Exams finished", "${t.cards_due} flashcards due", Modifier.weight(1f))
    }
    val hist = h?.history.orEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChartCard("Average score (%)", hist.map { Point(fmtDay(it.day), it.avg_score_pct) }, 100.0, { "${it.toInt()}%" }, modifier = Modifier.weight(1f))
        ChartCard("Best mock (out of 600)", hist.filter { it.best_mock_total > 0 }.map { Point(fmtDay(it.day), it.best_mock_total) }, 600.0,
            { "${it.toInt()}" }, reference = Reference(300.0, "Pass line 300"), modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ChartCard("Mistakes to fix", hist.map { Point(fmtDay(it.day), it.mistakes_open.toDouble()) }, 10.0, { "${it.toInt()}" }, modifier = Modifier.weight(1f))
        Box(Modifier.weight(1f)) {
            Card("Subjects (average score)") { if (p.subjects.isEmpty()) Text("No finished exams yet") else SubjectBars(p.subjects) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Topics(p: Progress) {
    val c = LocalChart.current
    Card("Topic mastery") {
        Text("Weak < 50% · Developing · Strong ≥ 75% over at least 3 questions", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    p.topics.groupBy { it.subject }.forEach { (subject, topics) ->
        Card(subject) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.sortedBy { it.accuracy }.forEach { t ->
                    val (bg, fg, level) = when {
                        t.accuracy < 0.5 -> Triple(c.critical, Color.White, "Weak")
                        t.accuracy >= 0.75 && t.attempted >= 3 -> Triple(c.good, Color.White, "Strong")
                        else -> Triple(c.warning, c.onWarning, "Developing")
                    }
                    Surface(color = bg, shape = MaterialTheme.shapes.medium, modifier = Modifier.semantics {
                        contentDescription = "${t.topic}: $level, ${(t.accuracy * 100).toInt()}% over ${t.attempted} questions" }) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(t.topic, color = fg, style = MaterialTheme.typography.labelLarge)
                            Text("$level · ${(t.accuracy * 100).toInt()}% · ${t.attempted} q", color = fg, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    if (p.topics.isEmpty()) Empty("No topics practised yet", "Topics appear once exams on them are finished.")
}

@Composable
private fun Exams(p: Progress) {
    Card("National mocks (out of 600)") {
        if (p.mocks.isEmpty()) Text("No national mock started yet")
        else DataTable(listOf("Started", "Papers done", "Total"), p.mocks.reversed().map {
            listOf(fmtDate(it.created_at), "${it.papers_done} / 6", "${it.total.toInt()}" + if (it.papers_done == 6) (if (it.total >= 300) "  ✓ pass" else "  ✗ below 300") else "  (in progress)")
        })
    }
    Card("Recent results") {
        if (p.recent_exams.isEmpty()) Text("No finished exams yet")
        else DataTable(listOf("Date", "Exam", "Subject", "Score"), p.recent_exams.map {
            val pct = if (it.max_score > 0) (it.score / it.max_score * 100).toInt() else 0
            listOf(fmtDate(it.finished_at), (if (it.mock) "Mock · " else "") + it.title, it.subject, "${"%.1f".format(it.score)} / ${it.max_score.toInt()}  ($pct%)")
        })
    }
}

@Composable
private fun Activity(p: Progress) {
    val c = LocalChart.current
    val studied = p.activity_days.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
    val today = LocalDate.now(zone)
    val weeks = 18
    val start = today.minusDays((weeks * 7 - 1).toLong()).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
    val last30 = (0 until 30).count { today.minusDays(it.toLong()) in studied }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile("${p.streak}", "Current streak (days)", null, Modifier.weight(1f))
        Tile("$last30 / 30", "Days studied in the last 30", null, Modifier.weight(1f))
        Tile("${studied.size}", "Study days recorded", null, Modifier.weight(1f))
    }
    Card("Study days (last $weeks weeks)") {
        Text("Filled square = studied that day (flashcard reviews or a finished exam). Columns are weeks, Monday at the top.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = "Studied $last30 of the last 30 days" }) {
            val cell = minOf(size.width / (weeks + 1), size.height / 7) - 3f
            var d = start
            while (!d.isAfter(today)) {
                val col = ChronoUnit.WEEKS.between(start, d).toInt(); val row = d.dayOfWeek.value - 1
                drawRoundRect(if (d in studied) c.series else c.notStarted, Offset(col * (cell + 3f), row * (cell + 3f)), Size(cell, cell), CornerRadius(3f, 3f))
                d = d.plusDays(1)
            }
        }
    }
}

@Composable
private fun AiUse(u: Usage?, budget: Double, d: Device) {
    if (u == null) { Empty("No AI use recorded yet", "Costs appear after the phone uses an AI feature."); return }
    val days30 = u.days.takeLast(30)
    val cached = days30.sumOf { (it.input_tokens ?: 0) * it.cached_share } / maxOf(1.0, days30.sumOf { (it.input_tokens ?: 0).toDouble() })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile("$${"%.2f".format(d.today_usd)}", "Today", if (budget > 0) "daily budget $${"%.2f".format(budget)}" else "no daily limit", Modifier.weight(1f))
        Tile("$${"%.2f".format(u.total_usd)}", "Last 30 days", "${days30.sumOf { it.requests }} AI requests", Modifier.weight(1f))
        Tile("$${"%.2f".format(u.total_usd / maxOf(1, days30.size))}", "Average per active day", null, Modifier.weight(1f))
        Tile("${(cached * 100).toInt()}%", "Input served from cache", "cheaper repeated text", Modifier.weight(1f))
    }
    ChartCard("AI cost per day (USD)", days30.map { Point(fmtDay(it.day), it.usd) }, budget, { "$" + "%.2f".format(it) }, bars = true,
        reference = if (budget > 0) Reference(budget, "Daily budget") else null, height = 220)
    Card("Cost by feature (last 30 days)") {
        DataTable(listOf("Feature", "Model", "Requests", "Cost"), u.by_feature.map {
            listOf(featureName(it.endpoint), it.model.removePrefix("claude-"), "${it.requests}", "$" + "%.3f".format(it.usd))
        })
    }
}

@Composable
private fun Books(p: Progress) = Card("Books and past papers on the phone (${p.books.size})") {
    if (p.books.isEmpty()) Text("No books uploaded yet")
    else DataTable(listOf("Title", "Subject", "Status"), p.books.map { listOf(it.title, it.subject, it.status) })
}
