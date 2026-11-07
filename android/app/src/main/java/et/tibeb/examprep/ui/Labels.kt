package et.tibeb.examprep.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import et.tibeb.examprep.R
import et.tibeb.examprep.util.formatDate

// Data is stored with stable English keys (subjects, types...) so the AI prompts and the database
// never change with the interface language; these map the keys to translated labels.

private val SUBJECT = mapOf(
    "Mathematics" to R.string.subj_math, "Physics" to R.string.subj_physics, "Chemistry" to R.string.subj_chemistry,
    "Biology" to R.string.subj_biology, "English" to R.string.subj_english, "Scholastic Aptitude" to R.string.subj_sat,
    "Geography" to R.string.subj_geography, "History" to R.string.subj_history, "Economics" to R.string.subj_economics,
)
val QTYPE = mapOf("mcq" to R.string.qt_mcq, "numeric" to R.string.qt_numeric, "short" to R.string.qt_short, "graph" to R.string.qt_graph)
val DOC_TYPE = mapOf(
    "textbook" to R.string.doc_textbook, "teacher_guide" to R.string.doc_teacher_guide, "past_exam" to R.string.doc_past_exam,
    "worksheet" to R.string.doc_worksheet, "notes" to R.string.doc_notes,
)
val NOTE_STYLE = mapOf("summary" to R.string.note_summary, "detailed" to R.string.note_detailed,
    "formula_sheet" to R.string.note_formula, "cheat_sheet" to R.string.note_cheat)
val DIFFICULTY = mapOf("standard" to R.string.diff_national, "hard" to R.string.diff_hard, "very_hard" to R.string.diff_very_hard)

@StringRes fun subjectRes(s: String): Int? = SUBJECT[s]

@Composable fun subjectName(s: String): String = SUBJECT[s]?.let { stringResource(it) } ?: s
@Composable fun streamName(s: String): String = stringResource(if (s == "social") R.string.stream_social else R.string.stream_natural)
@Composable fun label(map: Map<String, Int>, key: String): String = map[key]?.let { stringResource(it) } ?: key
@Composable fun gradeShort(g: Int) = stringResource(R.string.grade_short, g)
@Composable fun date(millis: Long) = LocalContext.current.formatDate(millis)
