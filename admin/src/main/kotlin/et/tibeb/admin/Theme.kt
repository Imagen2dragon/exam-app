package et.tibeb.admin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Same brand as the phone app (highland green + meskel gold), so the two feel like one product.
private val Light = lightColorScheme(
    primary = Color(0xFF0E6B4E), onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EBD3), onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF7A5900), secondaryContainer = Color(0xFFFFDF9E), onSecondaryContainer = Color(0xFF261A00),
    tertiaryContainer = Color(0xFFBEEAF7), onTertiaryContainer = Color(0xFF001F26),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF4F3EE), surface = Color(0xFFF4F3EE), onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFDCE5DD), onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFEEE8), surfaceContainer = Color(0xFFE9E8E2),
    outlineVariant = Color(0xFFC0C9C1),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8BD6B6), onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A), onPrimaryContainer = Color(0xFFB7EBD3),
    secondary = Color(0xFFF2C14E), secondaryContainer = Color(0xFF5C4300), onSecondaryContainer = Color(0xFFFFDF9E),
    tertiaryContainer = Color(0xFF214C57), onTertiaryContainer = Color(0xFFBEEAF7),
    error = Color(0xFFF2B8B5), errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0F1512), surface = Color(0xFF0F1512), onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF1B211E), surfaceContainerLow = Color(0xFF171D1A), surfaceContainer = Color(0xFF1F2522),
    outlineVariant = Color(0xFF404943),
)

/**
 * Chart tokens, validated with the data-viz palette validator against the card surfaces
 * (#FFFFFF light / #1B211E dark): the series blue clears 3:1 in both modes. Reference lines are
 * neutral and dashed with a text label - never a second hue.
 */
@Immutable
data class ChartColors(
    val series: Color, val grid: Color, val axis: Color, val muted: Color, val reference: Color,
    val good: Color, val warning: Color, val critical: Color, val notStarted: Color, val onWarning: Color,
)

private val LightChart = ChartColors(
    series = Color(0xFF2A78D6), grid = Color(0xFFE1E0D9), axis = Color(0xFFC3C2B7), muted = Color(0xFF6B6A65),
    reference = Color(0xFF52514E), good = Color(0xFF0CA30C), warning = Color(0xFFFAB219), critical = Color(0xFFD03B3B),
    notStarted = Color(0xFFE9E8E2), onWarning = Color(0xFF0B0B0B),
)
private val DarkChart = ChartColors(
    series = Color(0xFF3987E5), grid = Color(0xFF2C2C2A), axis = Color(0xFF383835), muted = Color(0xFF9A9992),
    reference = Color(0xFFC3C2B7), good = Color(0xFF0CA30C), warning = Color(0xFFFAB219), critical = Color(0xFFD03B3B),
    notStarted = Color(0xFF2A302D), onWarning = Color(0xFF0B0B0B),
)

val LocalChart = staticCompositionLocalOf { LightChart }

@Composable
fun AdminTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalChart provides if (dark) DarkChart else LightChart) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)),
            content = content,
        )
    }
}
