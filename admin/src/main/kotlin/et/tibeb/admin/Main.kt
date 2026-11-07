package et.tibeb.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Tibeb Admin",
        state = rememberWindowState(size = DpSize(1280.dp, 860.dp))) {
        AdminTheme { AdminApp() }
    }
}

/** Loads everything the dashboard shows for the selected device. */
fun load(api: AdminApi, selected: String?): Loaded {
    val devices = api.devices()
    val dev = devices.devices.firstOrNull { it.device == selected } ?: devices.devices.maxByOrNull { it.last_seen ?: 0.0 }
    val history = dev?.takeIf { it.latest != null }?.let { runCatching { api.history(it.device) }.getOrNull() }
    val usage = dev?.let { runCatching { api.usage(it.device) }.getOrNull() }
    return Loaded(devices, dev, history, usage, System.currentTimeMillis())
}

@Composable
fun AdminApp(store: Store = remember { Store() }, apiFor: (String, String) -> AdminApi = ::AdminApi) {
    val scope = rememberCoroutineScope()
    var api by remember { mutableStateOf<AdminApi?>(store.load()?.let { (u, k) -> runCatching { apiFor(u, k) }.getOrNull() }) }
    var data by remember { mutableStateOf<Loaded?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun refresh(selected: String? = data?.selected?.device) {
        val a = api ?: return
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { load(a, selected) } }
                .onSuccess { data = it; error = null }
                .onFailure { error = it.message }
            busy = false
        }
    }

    LaunchedEffect(api) {
        while (api != null) { refresh(); delay(5 * 60 * 1000L) }   // auto-refresh every 5 minutes
    }

    // A Surface (not a plain Box) so all text gets the theme's content colour - readable in dark mode too.
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val a = api; val d = data
        when {
            a == null || (d == null && error != null && !busy) -> ConnectScreen(error, busy) { url, key ->
                busy = true; error = null
                scope.launch {
                    runCatching {
                        val candidate = apiFor(url, key)
                        withContext(Dispatchers.IO) { load(candidate, null) } to candidate
                    }.onSuccess { (loaded, candidate) -> store.save(url, key); api = candidate; data = loaded }
                        .onFailure { error = it.message }
                    busy = false
                }
            }
            d == null -> ConnectScreen(null, true) { _, _ -> }
            else -> Dashboard(d, error, busy, onSelect = { refresh(it.device) }, onRefresh = { refresh() },
                onRename = { dev, name -> scope.launch { runCatching { withContext(Dispatchers.IO) { a.rename(dev.device, name) } }; refresh(dev.device) } },
                onSignOut = { store.forget(); api = null; data = null; error = null })
        }
    }
}
