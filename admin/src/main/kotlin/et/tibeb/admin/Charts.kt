package et.tibeb.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

data class Point(val label: String, val value: Double)

/** Rounds up to 1, 2, 2.5 or 5 x 10^k so four grid steps land on round numbers. */
fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val mag = Math.pow(10.0, Math.floor(Math.log10(v)))
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= v - 1e-9 }
}
data class Reference(val value: Double, val label: String)

private const val LEFT = 46f
private const val BOTTOM = 24f
private const val TOP = 12f

/**
 * One chart card: title, plot (single series, one y-axis), hover tooltip, and a "Table" toggle that
 * shows the same numbers as text (the accessible view). Reference lines are neutral, dashed, labelled.
 */
@Composable
fun ChartCard(
    title: String, points: List<Point>, yMax: Double, format: (Double) -> String,
    bars: Boolean = false, reference: Reference? = null, modifier: Modifier = Modifier, height: Int = 200,
) {
    var table by remember { mutableStateOf(false) }
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { table = !table }) { Text(if (table) "Chart" else "Table") }
            }
            when {
                points.isEmpty() -> Text("No data yet", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                table -> DataTable(listOf("Day", title), points.reversed().map { listOf(it.label, format(it.value)) })
                else -> Plot(points, yMax, format, bars, reference, height,
                    Modifier.semantics { contentDescription = "$title: latest ${format(points.last().value)}" })
            }
        }
    }
}

@Composable
private fun Plot(points: List<Point>, yMax: Double, format: (Double) -> String, bars: Boolean,
                 reference: Reference?, height: Int, modifier: Modifier) {
    val c = LocalChart.current
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val measurer = rememberTextMeasurer()
    var hover by remember { mutableStateOf<Offset?>(null) }
    val small = TextStyle(fontSize = 11.sp, color = c.muted)
    val dataMax = points.maxOf { it.value }
    // A natural scale (100%, 600) is used as-is when the data fits; otherwise round up to a "nice" value.
    val top = if (dataMax <= yMax && (reference?.value ?: 0.0) <= yMax) yMax else niceCeil(maxOf(dataMax, reference?.value ?: 0.0) * 1.05)

    Canvas(modifier.fillMaxWidth().height(height.dp).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                hover = if (e.type == PointerEventType.Exit) null else e.changes.first().position
            }
        }
    }) {
        val w = size.width - LEFT; val h = size.height - BOTTOM - TOP
        fun x(i: Int) = LEFT + if (bars) w * (i + 0.5f) / points.size else if (points.size == 1) w / 2 else w * i / (points.size - 1)
        fun y(v: Double) = TOP + h - (v / top * h).toFloat()

        // recessive grid + y labels (4 steps)
        for (k in 0..4) {
            val v = top / 4 * k
            drawLine(if (k == 0) c.axis else c.grid, Offset(LEFT, y(v)), Offset(size.width, y(v)), 1f)
            drawText(measurer, format(v), Offset(0f, y(v) - 8f), small)
        }
        // x labels: first, middle, last only (no collisions)
        listOf(0, points.size / 2, points.lastIndex).distinct().forEach { i ->
            val t = measurer.measure(points[i].label, small)
            drawText(t, topLeft = Offset((x(i) - t.size.width / 2).coerceIn(LEFT, size.width - t.size.width), size.height - BOTTOM + 6f))
        }
        reference?.let { r ->
            drawLine(c.reference, Offset(LEFT, y(r.value)), Offset(size.width, y(r.value)), 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
            val t = measurer.measure(r.label, small.copy(color = c.reference))
            drawText(t, topLeft = Offset(LEFT + 6f, y(r.value) - t.size.height - 2f))   // left end: never under the latest point
        }
        if (bars) {
            val slot = w / points.size
            val bw = (slot - 2f).coerceIn(2f, 28f)                       // 2px surface gap between bars
            points.forEachIndexed { i, p ->
                val bh = (TOP + h - y(p.value)).coerceAtLeast(if (p.value > 0) 2f else 0f)
                drawRoundRect(c.series, Offset(x(i) - bw / 2, TOP + h - bh), Size(bw, bh), CornerRadius(4f, 4f))
                drawRect(c.series, Offset(x(i) - bw / 2, TOP + h - minOf(bh, 4f)), Size(bw, minOf(bh, 4f)))  // square at baseline
            }
        } else {
            val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.value)) else lineTo(x(i), y(p.value)) } }
            drawPath(path, c.series, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            // last point marked + direct label (selective labelling)
            val last = points.lastIndex
            drawCircle(surface, 6f, Offset(x(last), y(points[last].value)))
            drawCircle(c.series, 4.5f, Offset(x(last), y(points[last].value)))
        }
        hover?.let { pos ->
            val i = points.indices.minByOrNull { abs(x(it) - pos.x) } ?: return@let
            if (!bars) {
                drawLine(c.axis, Offset(x(i), TOP), Offset(x(i), TOP + h), 1f)
                drawCircle(surface, 7f, Offset(x(i), y(points[i].value)))
                drawCircle(c.series, 5f, Offset(x(i), y(points[i].value)))
            }
            tooltip(measurer, "${points[i].label}  ·  ${format(points[i].value)}", Offset(x(i), y(points[i].value)), ink, surface)
        }
    }
}

private fun DrawScope.tooltip(measurer: androidx.compose.ui.text.TextMeasurer, text: String, at: Offset,
                              ink: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    val t = measurer.measure(text, TextStyle(fontSize = 12.sp, color = ink))
    val w = t.size.width + 16f; val h = t.size.height + 10f
    val left = (at.x - w / 2).coerceIn(0f, size.width - w); val topY = (at.y - h - 10f).coerceAtLeast(0f)
    drawRoundRect(ink.copy(alpha = 0.12f), Offset(left, topY + 1f), Size(w, h), CornerRadius(8f, 8f))
    drawRoundRect(bg, Offset(left, topY), Size(w, h), CornerRadius(8f, 8f))
    drawText(t, topLeft = Offset(left + 8f, topY + 5f))
}

/** Horizontal bars for per-subject averages: one hue, value written at the end of each bar. */
@Composable
fun SubjectBars(subjects: List<SubjectSummary>) {
    val c = LocalChart.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        subjects.sortedBy { it.avg_pct }.forEach { s ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(s.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(end = 8.dp).width(150.dp))
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    drawRoundRect(c.grid, size = size, cornerRadius = CornerRadius(4f, 4f))
                    drawRoundRect(c.series, size = Size(size.width * (s.avg_pct / 100).toFloat().coerceIn(0f, 1f), size.height),
                        cornerRadius = CornerRadius(4f, 4f))
                }
                Text("${s.avg_pct.toInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 10.dp).width(40.dp))
                Text("last ${s.last_pct.toInt()}% · ${s.exams} exams", style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(130.dp))
            }
        }
    }
}

@Composable
fun DataTable(headers: List<String>, rows: List<List<String>>) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(Modifier.padding(vertical = 6.dp)) {
            headers.forEach { Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)) }
        }
        rows.take(200).forEach { r ->
            Row(Modifier.padding(vertical = 4.dp)) {
                r.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) }
            }
        }
    }
}
