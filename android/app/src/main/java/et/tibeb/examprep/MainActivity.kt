package et.tibeb.examprep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.screens.ExamScreen
import et.tibeb.examprep.ui.screens.ExamSetupScreen
import et.tibeb.examprep.ui.screens.ExamsScreen
import et.tibeb.examprep.ui.screens.HomeScreen
import et.tibeb.examprep.ui.screens.LibraryScreen
import et.tibeb.examprep.ui.screens.NoteScreen
import et.tibeb.examprep.ui.screens.ResultScreen
import et.tibeb.examprep.ui.screens.ReviewScreen
import et.tibeb.examprep.ui.screens.SettingsScreen
import et.tibeb.examprep.ui.screens.StudyScreen
import et.tibeb.examprep.ui.theme.TibebTheme

class MainActivity : ComponentActivity() {
    /** Exposed only so screenshot tests can jump to deep screens. */
    internal var navForTest: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as TibebApp
        setContent {
            TibebTheme { CompositionLocalProvider(LocalApp provides app) { AppNav { navForTest = it } } }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("library", "Library", Icons.AutoMirrored.Filled.List),
    Tab("study", "Study", Icons.Filled.Create),
    Tab("exams", "Exams", Icons.Filled.Star),
)

@Composable
private fun AppNav(onNav: (NavHostController) -> Unit) {
    val nav = rememberNavController()
    onNav(nav)
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route in tabs.map { it.route }) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.tab(t.route) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(nav) }
            composable("library") { LibraryScreen() }
            composable("study") { StudyScreen(nav) }
            composable("exams") { ExamsScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("review") { ReviewScreen(nav) }
            composable("newexam?subject={subject}", listOf(navArgument("subject") { defaultValue = "" })) {
                ExamSetupScreen(nav, it.arguments?.getString("subject").orEmpty())
            }
            composable("note/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                NoteScreen(nav, it.arguments!!.getLong("id"))
            }
            composable("exam/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                ExamScreen(nav, it.arguments!!.getLong("id"))
            }
            composable("result/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                ResultScreen(nav, it.arguments!!.getLong("id"))
            }
        }
    }
}

fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
