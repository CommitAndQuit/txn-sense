package `in`.financeministry.app.feature

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import `in`.financeministry.app.data.*
import `in`.financeministry.app.transactionTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun OriginalSmsAction(repository: TransactionRepository, row: TransactionEntity, source: HistoricalSmsSource? = null) {
    if (row.sourceType != "SMS" || row.sourceFingerprint?.size != 32) return
    val erased by repository.eraseGeneration.collectAsState()
    key(repository, row.id, row.updatedAt, erased) { OriginalSmsContent(repository, row.id, source) }
}

/** Raw SMS lives only in this unsaved composition while its secure dialog is visible. */
@Composable
private fun OriginalSmsContent(repository: TransactionRepository, id: String, source: HistoricalSmsSource?) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf(false) }
    var disclosure by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OriginalSmsResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun clear() { job?.cancel(); job = null; result = null; viewing = false; disclosure = false }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) clear() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); clear() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // A permission prompt backgrounds the activity. Never reopen a raw SMS automatically.
        clear()
        notice = if (granted) "Inbox access enabled. Tap View original SMS to view it."
            else "Inbox access wasn't granted. You can enable SMS permission in Android app settings."
    }
    TextButton(onClick = {
        notice = null
        if (!repository.historyPermissionGranted()) disclosure = true
        else {
            viewing = true; result = null
            job = scope.launch { result = repository.originalSms(id, source) }
        }
    }) { Text("View original SMS") }
    notice?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
        if (!repository.historyPermissionGranted()) TextButton(onClick = {
            clear()
            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
            catch (_: Exception) { notice = "Open Android Settings → Apps → Finance Ministry → Permissions → SMS." }
        }) { Text("Open SMS settings") }
    }
    if (disclosure) AlertDialog(onDismissRequest = ::clear,
        title = { Text("Read original SMS?") },
        text = { Text("Android grants access to the whole SMS inbox, including personal messages and OTPs. Finance Ministry will look for this transaction's original received or sent timestamp and display only an exact fingerprint match. The message and sender are read temporarily, never saved, logged, backed up or uploaded. This is optional and does not enable new SMS capture or import transactions.") },
        confirmButton = { TextButton(onClick = { clear(); permission.launch(Manifest.permission.READ_SMS) }) { Text("Allow inbox access") } },
        dismissButton = { TextButton(onClick = ::clear) { Text("Not now") } })
    if (viewing) AlertDialog(onDismissRequest = ::clear,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Original SMS") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val current = result) {
                    null -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Looking for the original message…") }
                    is OriginalSmsResult.Found -> {
                        Text(current.message.sender, style = MaterialTheme.typography.titleSmall)
                        Text(transactionTime(current.message.date), style = MaterialTheme.typography.bodySmall)
                        Text(current.message.body, color = MaterialTheme.colorScheme.onSurface)
                        Text("Read directly from your phone. Not saved by Finance Ministry. Closes when you leave the app.", style = MaterialTheme.typography.bodySmall)
                    }
                    OriginalSmsResult.Unavailable -> {
                        Text("Original message unavailable")
                        Text("It may have been deleted, moved or restored from another installation, or could not be matched uniquely. Your transaction is unchanged.")
                    }
                    OriginalSmsResult.PermissionRequired -> {
                        Text("SMS inbox access required")
                        Text("Android did not allow inbox access. Close this view and check SMS permission in Android app settings. Your transaction is unchanged.")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::clear) { Text("Close") } })
}
