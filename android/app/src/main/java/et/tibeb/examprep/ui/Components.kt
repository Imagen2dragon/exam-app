package et.tibeb.examprep.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import et.tibeb.examprep.util.parseMath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import et.tibeb.examprep.TibebApp
import et.tibeb.examprep.ui.theme.subjectColor

val LocalApp = compositionLocalOf<TibebApp> { error("no app") }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(
    text, style = MaterialTheme.typography.titleMedium,
    modifier = modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() },
)

@Composable
fun SubjectDot(subject: String, size: Dp = 12.dp) = Box(
    Modifier.size(size).then(Modifier.semantics { }),
) {
    val c = subjectColor(subject, isSystemInDarkTheme())
    Canvas(Modifier.size(size)) { drawCircle(c) }
}

/** A tappable card with consistent padding and a 48dp+ touch target. */
@Composable
fun TCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, container: Color? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = container ?: MaterialTheme.colorScheme.surfaceContainerLowest)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(16.dp)) { content() } }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, shape = MaterialTheme.shapes.large) { inner() }
    else Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = MaterialTheme.shapes.large) { inner() }
}

@Composable
fun ScoreRing(fraction: Float, label: String, size: Dp = 120.dp, stroke: Dp = 12.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val bar = when {
        fraction >= 0.75f -> MaterialTheme.colorScheme.primary
        fraction >= 0.5f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    Box(Modifier.size(size).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = Stroke(stroke.toPx(), cap = StrokeCap.Round)
            drawArc(track, -90f, 360f, false, style = s)
            drawArc(bar, -90f, 360f * fraction.coerceIn(0f, 1f), false, style = s)
        }
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun StatPill(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: Set<T>, label: @Composable (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o -> FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
fun Busy(text: String) = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    Spacer(Modifier.width(12.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ErrorText(text: String) = Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
    Text(text, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun EmptyState(title: String, body: String) = Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {}
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Minimal Markdown: "#" headings, "-" or "*" bullets, **bold**, `code`. No WebView, no library.
@Composable
fun Markdown(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        md.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") -> Text(inline(line.drop(4)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                line.startsWith("## ") -> Text(inline(line.drop(3)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                line.startsWith("# ") -> Text(inline(line.drop(2)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row {
                    val indent = (line.length - line.trimStart().length) / 2
                    Spacer(Modifier.width((indent * 16).dp))
                    Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(inline(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyLarge)
                }
                else -> Text(inline(line), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val buf = StringBuilder()
    fun flush() { appendMath(buf.toString()); buf.clear() }
    while (i < s.length) {
        when {
            s.startsWith("**", i) && s.indexOf("**", i + 2) > 0 -> {
                flush()
                val end = s.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendMath(s.substring(i + 2, end)) }
                i = end + 2
            }
            s[i] == '`' && s.indexOf('`', i + 1) > 0 -> {
                flush()
                val end = s.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { appendMath(s.substring(i + 1, end)) }
                i = end + 1
            }
            else -> { buf.append(s[i]); i++ }
        }
    }
    flush()
}

/** Appends text with simple LaTeX rendered as real superscripts/subscripts and symbols. */
fun AnnotatedString.Builder.appendMath(t: String) {
    parseMath(t).forEach { seg ->
        if (seg.script == 0) append(seg.text)
        else withStyle(SpanStyle(baselineShift = if (seg.script > 0) BaselineShift.Superscript else BaselineShift.Subscript, fontSize = 0.72.em)) {
            append(seg.text)
        }
    }
}

/** Plain string -> styled math text, for question stems, options, answers and flashcards. */
fun math(t: String): AnnotatedString = buildAnnotatedString { appendMath(t) }
