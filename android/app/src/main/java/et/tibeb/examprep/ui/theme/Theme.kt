package et.tibeb.examprep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette: highland green, meskel-flower gold, and a warm clay red used only for errors/alerts.
// All text/background pairs below meet WCAG AA contrast (4.5:1).
private val Light = lightColorScheme(
    primary = Color(0xFF0E6B4E), onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EBD3), onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF7A5900), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDF9E), onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF3B6470), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBEEAF7), onTertiaryContainer = Color(0xFF001F26),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF8F6F0), onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFF8F6F0), onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFDCE5DD), onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F1EA),
    surfaceContainer = Color(0xFFECEBE4), surfaceContainerHigh = Color(0xFFE6E5DF),
    outline = Color(0xFF707973), outlineVariant = Color(0xFFC0C9C1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BD6B6), onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A), onPrimaryContainer = Color(0xFFB7EBD3),
    secondary = Color(0xFFF2C14E), onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300), onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFFA3CDDB), onTertiary = Color(0xFF033640),
    tertiaryContainer = Color(0xFF214C57), onTertiaryContainer = Color(0xFFBEEAF7),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0F1512), onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28),
    outline = Color(0xFF8A938C), outlineVariant = Color(0xFF404943),
)

private val Type = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    )
}

@Composable
fun TibebTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Type,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}

/** Stable per-subject accent so each subject is recognisable at a glance. */
fun subjectColor(subject: String, dark: Boolean): Color {
    val light = mapOf(
        "Mathematics" to 0xFF0E6B4E, "Physics" to 0xFF3B5BA9, "Chemistry" to 0xFF8E4585,
        "Biology" to 0xFF3E7D2C, "English" to 0xFFA0522D, "Scholastic Aptitude" to 0xFF6750A4,
        "Geography" to 0xFF00758F, "History" to 0xFF8A5A00, "Economics" to 0xFF5D6B00,
    )
    val c = Color(light[subject] ?: 0xFF5F6368)
    return if (dark) c.copy(alpha = 1f).let { Color(it.red * 0.5f + 0.5f, it.green * 0.5f + 0.5f, it.blue * 0.5f + 0.5f) } else c
}
