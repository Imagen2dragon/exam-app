package et.tibeb.examprep.ui.screens

import androidx.compose.ui.res.stringResource
import et.tibeb.examprep.R
import et.tibeb.examprep.BuildConfig
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import et.tibeb.examprep.ui.streamName
import et.tibeb.examprep.util.Lang
import et.tibeb.examprep.util.findActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import et.tibeb.examprep.net.Api
import et.tibeb.examprep.ui.ChoiceChips
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.SectionTitle
import et.tibeb.examprep.ui.TCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val app = LocalApp.current
    val s by app.prefs.settings.collectAsState()
    var url by remember { mutableStateOf(s.serverUrl) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val urlOk = url.isBlank() || Api.isAllowedUrl(url.trim())
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    var uiLang by remember { mutableStateOf(Lang.saved(ctx)) }
    val strChecking = stringResource(R.string.set_checking)
    val strConnected = stringResource(R.string.set_connected)
    val strNoAnswer = stringResource(R.string.set_no_answer)
    val strRemoved = stringResource(R.string.set_token_removed)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.set_about_you))
            TCard {
                Text(stringResource(R.string.set_stream), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf("natural", "social"), setOf(s.stream), { streamName(it) }) {
                    app.prefs.save(s.copy(stream = it))
                }
                Text(stringResource(R.string.label_grade), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(listOf(9, 10, 11, 12), setOf(s.grade), { stringResource(R.string.grade_long, it) }) { app.prefs.save(s.copy(grade = it)) }
                Text(stringResource(R.string.set_app_lang), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(Lang.OPTIONS, setOf(uiLang), { langLabel(it) }) { tag ->
                    uiLang = tag
                    app.prefs.setUiLang(tag)
                    activity?.recreate() // reload every screen in the new language
                }
                Text(stringResource(R.string.set_notes_lang), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(listOf("en", "am"), setOf(s.language), { if (it == "en") "English" else "አማርኛ" }) { app.prefs.save(s.copy(language = it)) }
            }
            SectionTitle(stringResource(R.string.set_ai))
            TCard {
                OutlinedTextField(url, { url = it.trim() }, label = { Text(stringResource(R.string.set_server)) }, placeholder = { Text("https://tibeb.example.com") },
                    singleLine = true, isError = !urlOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { if (!urlOk) Text(stringResource(R.string.set_https)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it.trim() }, label = { Text(stringResource(if (s.hasToken) R.string.set_token_saved else R.string.set_token)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                Button(enabled = urlOk, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp), onClick = {
                    app.prefs.save(s.copy(serverUrl = url))
                    if (token.isNotBlank()) { app.prefs.setToken(token); token = "" }
                    status = strChecking
                    scope.launch { status = if (ping(app)) strConnected else strNoAnswer }
                }) { Text(stringResource(R.string.set_save_test)) }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            }
            SectionTitle(stringResource(R.string.set_privacy))
            TCard {
                Text(stringResource(R.string.set_privacy_body),
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { app.prefs.setToken(""); status = strRemoved }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.set_remove_token)) }
            }
            TCard {
                var checkUpdates by remember { mutableStateOf(app.updates.enabled) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_updates), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.set_updates_body), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checkUpdates, { checkUpdates = it; app.updates.enabled = it })
                }
                Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private suspend fun ping(app: et.tibeb.examprep.TibebApp): Boolean =
    Api({ app.prefs.settings.value.serverUrl }, { app.prefs.token() }, app::str).ping()

/** Each language is shown in its own script so it can be found whichever language is active. */
@Composable
private fun langLabel(tag: String): String = when (tag) {
    "en" -> "English"
    "am" -> "አማርኛ"
    else -> stringResource(R.string.lang_system)
}
