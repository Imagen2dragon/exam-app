package et.tibeb.examprep.ui.screens

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

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("About you")
            TCard {
                Text("Stream", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf("natural", "social"), setOf(s.stream), { if (it == "natural") "Natural science" else "Social science" }) {
                    app.prefs.save(s.copy(stream = it))
                }
                Text("Grade", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(listOf(9, 10, 11, 12), setOf(s.grade), { "Grade $it" }) { app.prefs.save(s.copy(grade = it)) }
                Text("Notes language", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(listOf("en", "am"), setOf(s.language), { if (it == "en") "English" else "አማርኛ" }) { app.prefs.save(s.copy(language = it)) }
            }
            SectionTitle("AI connection")
            TCard {
                OutlinedTextField(url, { url = it.trim() }, label = { Text("Server address") }, placeholder = { Text("https://tibeb.example.com") },
                    singleLine = true, isError = !urlOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = { if (!urlOk) Text("Must start with https://") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it.trim() }, label = { Text(if (s.hasToken) "Device token (saved – type to replace)" else "Device token") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                Button(enabled = urlOk, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp), onClick = {
                    app.prefs.save(s.copy(serverUrl = url))
                    if (token.isNotBlank()) { app.prefs.setToken(token); token = "" }
                    status = "Checking…"
                    scope.launch { status = if (ping(app)) "Connected ✓" else "Saved, but the server did not answer." }
                }) { Text("Save & test") }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            }
            SectionTitle("Privacy & security")
            TCard {
                Text("• Your books, notes and results are stored only on this phone and excluded from cloud backup.\n" +
                    "• The device token is encrypted with a key held in the phone's secure hardware (Android Keystore).\n" +
                    "• The AI key never lives in this app; requests go over HTTPS to your own server.\n" +
                    "• Uploaded files are sent once for reading, then only the extracted text is kept.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { app.prefs.setToken(""); status = "Token removed" }, modifier = Modifier.padding(top = 8.dp)) { Text("Remove device token") }
            }
        }
    }
}

private suspend fun ping(app: et.tibeb.examprep.TibebApp): Boolean =
    Api({ app.prefs.settings.value.serverUrl }, { app.prefs.token() }).ping()
