package et.tibeb.examprep.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import et.tibeb.examprep.R
import et.tibeb.examprep.backup.BackupError
import et.tibeb.examprep.backup.BackupState
import et.tibeb.examprep.ui.LocalApp
import et.tibeb.examprep.ui.SectionTitle
import et.tibeb.examprep.ui.TCard
import et.tibeb.examprep.ui.date
import et.tibeb.examprep.util.findActivity
import kotlinx.coroutines.launch

/** Settings → Cloud backup: sign in, write down the recovery code, restore, back up now. */
@Composable
fun BackupSection() {
    val app = LocalApp.current
    val backup = app.backup
    val state by backup.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { backup.config() }    // picks up a server that was set up since last time

    fun run(done: String? = null, block: suspend () -> Unit) {
        busy = true; message = null
        scope.launch {
            message = try { block(); done } catch (e: BackupError) { ctx.getString(errorText(e.reason)) }
            busy = false
        }
    }

    SectionTitle(stringResource(R.string.bk_title))
    TCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                BackupState.Unavailable -> Text(stringResource(R.string.bk_unavailable), style = MaterialTheme.typography.bodyMedium)
                BackupState.SignedOut -> SignIn(busy) { email, pw -> run { backup.signIn(email, pw) } }
                is BackupState.NewCode -> {
                    Text(stringResource(R.string.bk_code_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.bk_code_body), style = MaterialTheme.typography.bodyMedium)
                    CodeBox(s.code)
                    Button(enabled = !busy, onClick = { run(ctx.getString(R.string.bk_done)) { backup.confirmCode() } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.bk_wrote_it)) }
                }
                is BackupState.Found -> Found(s, busy, onRestore = { code -> run { backup.restore(code); restored = true } },
                    onStartNew = { run { backup.startNew() } })
                is BackupState.Active -> Active(s, busy, onNow = { run(ctx.getString(R.string.bk_done)) { backup.backupNow(force = true) } },
                    onSignIn = { email, pw -> run { backup.signIn(email, pw) } },
                    onWifiOnly = backup::setWifiOnly, onSignOut = { backup.signOut(); message = null }, code = backup::recoveryCode)
            }
            if (busy) Text(stringResource(R.string.bk_working), style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { contentDescription = it }) }
        }
    }
    if (restored) AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.bk_restored_title)) },
        text = { Text(stringResource(R.string.bk_restored_body)) },
        confirmButton = {
            TextButton(onClick = {
                // The restored database is swapped in at the next start, before anything opens it.
                ctx.findActivity()?.finishAffinity()
                Runtime.getRuntime().exit(0)
            }) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
private fun SignIn(busy: Boolean, intro: Boolean = true, onSignIn: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    if (intro) Text(stringResource(R.string.bk_intro), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(email, { email = it.trim() }, label = { Text(stringResource(R.string.bk_email)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.bk_password)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && "@" in email && password.isNotEmpty(), onClick = { onSignIn(email, password); password = "" },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.bk_sign_in)) }
}

/** The code in large monospace, selectable, read out group by group by screen readers. */
@Composable
private fun CodeBox(code: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(code.split("-").chunked(3).joinToString("\n") { it.joinToString("-") }, fontFamily = FontFamily.Monospace, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp).semantics { contentDescription = code.toCharArray().joinToString(" ").replace("-", ",") })
        }
    }
}

@Composable
private fun Found(s: BackupState.Found, busy: Boolean, onRestore: (String) -> Unit, onStartNew: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var confirmNew by remember { mutableStateOf(false) }
    Text(stringResource(R.string.bk_found, date(s.createdAt)), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.bk_found_body), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(code, { code = it.uppercase().take(40) }, label = { Text(stringResource(R.string.bk_code_field)) }, singleLine = true,
        placeholder = { Text("XXXXX-XXXXX-XXXXX-XXXXX-XXXXX") },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && code.count { it.isLetterOrDigit() } >= 25, onClick = { onRestore(code) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.bk_restore)) }
    TextButton(enabled = !busy, onClick = { confirmNew = true }) { Text(stringResource(R.string.bk_start_new)) }
    if (confirmNew) AlertDialog(
        onDismissRequest = { confirmNew = false },
        text = { Text(stringResource(R.string.bk_start_new_confirm)) },
        confirmButton = { TextButton(onClick = { confirmNew = false; onStartNew() }) { Text(stringResource(R.string.bk_continue)) } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Active(s: BackupState.Active, busy: Boolean, onNow: () -> Unit, onSignIn: (String, String) -> Unit,
                   onWifiOnly: (Boolean) -> Unit, onSignOut: () -> Unit, code: () -> String?) {
    var showCode by remember { mutableStateOf(false) }
    Text(if (s.lastBackup > 0) stringResource(R.string.bk_last, date(s.lastBackup), sizeText(s.lastSize)) else stringResource(R.string.bk_never),
        style = MaterialTheme.typography.titleSmall)
    if (s.needsSignIn) {
        Text(stringResource(R.string.bk_needs_login), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        SignIn(busy, intro = false, onSignIn = onSignIn)
    } else {
        Button(enabled = !busy, onClick = onNow, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.bk_now)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.bk_wifi_only), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.bk_wifi_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(s.wifiOnly, onWifiOnly)
    }
    if (showCode) code()?.let { CodeBox(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showCode = !showCode }) { Text(stringResource(if (showCode) R.string.bk_hide_code else R.string.bk_show_code)) }
        TextButton(enabled = !busy, onClick = onSignOut) { Text(stringResource(R.string.bk_sign_out)) }
    }
}

private fun sizeText(bytes: Int): String =
    if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000.0) else "${(bytes / 1000).coerceAtLeast(1)} KB"

private fun errorText(r: BackupError.Reason): Int = when (r) {
    BackupError.Reason.NOT_CONFIGURED -> R.string.bk_unavailable
    BackupError.Reason.BAD_LOGIN -> R.string.bk_err_bad_login
    BackupError.Reason.SIGNED_OUT -> R.string.bk_err_signed_out
    BackupError.Reason.OFFLINE -> R.string.bk_err_offline
    BackupError.Reason.TOO_BIG -> R.string.bk_err_too_big
    BackupError.Reason.SERVER -> R.string.bk_err_server
    BackupError.Reason.BAD_CODE -> R.string.bk_err_bad_code
    BackupError.Reason.WRONG_CODE -> R.string.bk_err_wrong_code
    BackupError.Reason.NO_BACKUP -> R.string.bk_err_no_backup
    BackupError.Reason.NEWER_APP -> R.string.bk_err_newer_app
    BackupError.Reason.DAMAGED -> R.string.bk_err_damaged
}
